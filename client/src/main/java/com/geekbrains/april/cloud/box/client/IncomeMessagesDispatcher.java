package com.geekbrains.april.cloud.box.client;

import com.geekbrains.april.cloud.box.common.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.function.Predicate;

public class IncomeMessagesDispatcher implements Runnable {

    private CloudBoxClient application;
    private MainController mainController;
    private int chunksize = 0;
    private String root_dir = "";

    private byte[] buffer;

    private HashMap<String, FileHelper.ChunksReceiver> chunksReceiverHashMap = new HashMap<>();
    private HashMap<String, FileHelper.ChunksSender> chunksSenderHashMap = new HashMap<>();
    private Predicate<String> receeivingInProgress = x -> chunksReceiverHashMap.containsKey(x);
    private Predicate<String> sendingInProgress = x -> chunksSenderHashMap.containsKey(x);


    public IncomeMessagesDispatcher(CloudBoxClient application) {
        this.application = application;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        mainController.setSenderInProgress(x -> sendingInProgress.test(x));
    }

    public void setChunksize(int chunksize) {
        this.chunksize = chunksize;
        buffer = new byte[chunksize];
    }

    public void setRoot_dir(String root_dir) {
        this.root_dir = root_dir;
    }


    @Override
    public void run() {
        Thread thread = Thread.currentThread();
        try {
            while (!thread.isInterrupted()) {
                AbstractMessage am = Network.readObject();

                if (am instanceof FileChunkMessage) {
                    FileChunkMessage fcm = (FileChunkMessage) am;
                    Files.write(Paths.get(root_dir + "/" + fcm.getFileInfo().fileName), fcm.getData(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    FXHelper.updateUI(() -> mainController.refreshLocalFilesList());
//todo rename file if download completed
                }

                if (am instanceof FileDownloadRequest) {
                    FileDownloadRequest fdr = (FileDownloadRequest) am;
                    FileInfo info = ((FileDownloadRequest) am).getFileInfo();
                    FileChunkMessage fileChunkMessage;
                    FileHelper.ChunksSender sender = chunksSenderHashMap.get(info.fileName);
                    if(info.position == info.fileLength){
                        if(sender != null) sender.close();
                        chunksSenderHashMap.remove(info.fileName);
                        fileChunkMessage = new FileChunkMessage(new byte[0], info);  // to close corresponding ChunksReceiver and pause loading process
                    } else {
                        if(sender == null) {
                            sender = new FileHelper.ChunksSender(root_dir + "/" + info.fileName, info.position);
                            chunksSenderHashMap.put(info.fileName, sender);
                        }
                        int bytes =  sender.read(buffer, info.position);
                        if(bytes !=  -1) {
                            fileChunkMessage = new FileChunkMessage(buffer, info); // regular chunk
                        } else {
                            sender.close();
                            chunksSenderHashMap.remove(info.fileName);
                            fileChunkMessage = new FileChunkMessage(new byte[0], info);  // to close corresponding ChunksReceiver and pause/finish loading process
                        }
//TODO: realize sending chunks that has size less than chunksize
                    }
                    Network.sendMsg(new FileListRequest());
                    Network.sendMsg(fileChunkMessage);
                }

                if (am instanceof FileListMessage) {
                    FileListMessage flm = (FileListMessage) am;
                    FXHelper.updateUI(() -> mainController.refreshRemoteFilesList(flm.getList(), receeivingInProgress));
                }

                if (am instanceof InfoMessage) {
                    InfoMessage im = (InfoMessage) am;
                    if (im.getCode() == InfoMessage.MessageCode.AUTHORIZATION_SUCCESSFUL) {
                        Platform.runLater(() -> application.onAuthorizationAnswer(true));
                    } else if (im.getCode() == InfoMessage.MessageCode.AUTHORIZATION_FAILED) {
                        Platform.runLater(() -> application.onAuthorizationAnswer(false));
                    } else if (im.getCode() == InfoMessage.MessageCode.FILE_CORRUPTED) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Uploaded file \"" + im.getInfo2() + "\" corrupted\nMD5: " + im.getInfo1(), ButtonType.OK);
                            alert.showAndWait();
                        });
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        } finally {
            Network.stop();
        }
    }
}
