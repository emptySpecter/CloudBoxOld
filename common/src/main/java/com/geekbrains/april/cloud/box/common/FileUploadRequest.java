package com.geekbrains.april.cloud.box.common;

public class FileUploadRequest implements AbstractMessage {
    private FileInfo fileInfo;

    public FileUploadRequest(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

}
