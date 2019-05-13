package com.geekbrains.april.cloud.box.server;

import com.geekbrains.april.cloud.box.common.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class MainHandler extends ChannelInboundHandlerAdapter {
    int user_id = 0;
    int chunksize = 0;
    String root_dir = "";

    private byte[] buffer;

    private HashMap<String, FileHelper.ChunksReceiver> chunksReceiverHashMap = new HashMap<>();
    private HashMap<String, FileHelper.ChunksSender> chunksSenderHashMap = new HashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg == null) {
            return;
        }

        if (msg instanceof AuthMessage) {
            user_id = ctx.channel().attr(CloudBoxServer.user_id).get();
            chunksize = ctx.channel().attr(CloudBoxServer.chunksize).get();
            root_dir = ctx.channel().attr(CloudBoxServer.root_dir).get();
            buffer = new byte[chunksize];
        }

        if (msg instanceof FileListRequest) {
            FileListMessage fileListMessage = new FileListMessage(SQLHandler.getUserFilesList(user_id));
            ctx.writeAndFlush(fileListMessage);
        }

        if (msg instanceof FileChunkMessage) {
            FileChunkMessage fcm = (FileChunkMessage) msg;
            FileInfo fileInfo = fcm.getFileInfo();
            Path path = Paths.get(root_dir + "/" + fileInfo.MD5);
            byte[] data = fcm.getData();
            int dataSize = data.length;
            FileHelper.ChunksReceiver receiver = chunksReceiverHashMap.get(fileInfo.MD5);
            if(data.length == 0) {
                if(receiver != null) receiver.close();
                chunksReceiverHashMap.remove(fileInfo.MD5);
                if(Files.size(path) == fileInfo.fileLength){
                    String MD5 = FileHelper.calculateMD5(path);
                    if (!MD5.equals(fileInfo.MD5)) {
                        if (Files.exists(path)) Files.delete(path);
                        fileInfo.position = 0;
                        SQLHandler.insertOrUpdateWorkingFile(fileInfo, user_id);
                        ctx.writeAndFlush(new InfoMessage(InfoMessage.MessageCode.FILE_CORRUPTED, MD5, fileInfo.fileName));
                    } else {
                        FileListMessage fileListMessage = new FileListMessage(SQLHandler.getUserFilesList(user_id));
                        ctx.writeAndFlush(fileListMessage);
                    }
                } else {
                    FileListMessage fileListMessage = new FileListMessage(SQLHandler.getUserFilesList(user_id));
                    ctx.writeAndFlush(fileListMessage);
                }
            } else {
                if(receiver == null) {
                    receiver = new FileHelper.ChunksReceiver(path);
                    chunksReceiverHashMap.put(fileInfo.MD5, receiver);
                }
                long remain = fileInfo.fileLength - fileInfo.position;
                int len = (remain < dataSize) ? (int) remain : dataSize;
                fileInfo.position = receiver.append(fileInfo.position, data, len);
                SQLHandler.insertOrUpdateWorkingFile(fileInfo, user_id);
                FileDownloadRequest fileDownloadRequest = new FileDownloadRequest(fileInfo);
                ctx.writeAndFlush(fileDownloadRequest);
            }
        }

        if (msg instanceof FileDownloadRequest) {
            FileDownloadRequest fdr = (FileDownloadRequest) msg;
            FileInfo fileInfo = (FileInfo) fdr.getFileInfo().clone();
            Path path = Paths.get(root_dir + "/" + fileInfo.MD5);
            if (Files.exists(path)) {
                FileChunkMessage fm = new FileChunkMessage(path, fileInfo);
                ctx.writeAndFlush(fm);
            }
        }

        if (msg instanceof FileUploadRequest) {
            FileUploadRequest fur = (FileUploadRequest) msg;
            FileInfo fileInfo = (FileInfo) fur.getFileInfo().clone();
            FileInfo fileInfoDB = SQLHandler.getFileInfoDB(fileInfo, user_id);
            if(fileInfoDB == null){
                fileInfo.position = 0;
                SQLHandler.insertOrUpdateWorkingFile(fileInfo, user_id);
                ctx.writeAndFlush(new FileDownloadRequest(fileInfo)); // first DownloadRequest
            } else  {
                if(fileInfo.MD5.equals(fileInfoDB.MD5)){
                    if(chunksSenderHashMap.containsKey(fileInfo.MD5)){
                        fileInfo.position = fileInfo.fileLength;  // to close corresponding ChunksSender and ChunksReceiver and pause loading process
                    } else {
                        fileInfo.position = fileInfoDB.position;
                    }
                    ctx.writeAndFlush(new FileDownloadRequest(fileInfo));
                } else {
                    ctx.writeAndFlush(new InfoMessage(InfoMessage.MessageCode.FILE_CORRUPTED, fileInfoDB.MD5, fileInfo.fileName));
                }
            }
        }

        if (msg instanceof FileDeleteRequest) {
            FileDeleteRequest fdr = (FileDeleteRequest) msg;
            Path path = Paths.get(root_dir + "/" + fdr.getFileInfo().MD5);
            if (Files.exists(path)) {
                if (SQLHandler.deleteWorkingFile(fdr.getFileInfo(), user_id)) {
                    Files.delete(path);
                }
            }
            FileListMessage fileListMessage = new FileListMessage(SQLHandler.getUserFilesList(user_id));
            ctx.writeAndFlush(fileListMessage);
        }

        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
