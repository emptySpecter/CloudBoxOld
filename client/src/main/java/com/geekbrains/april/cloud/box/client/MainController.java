package com.geekbrains.april.cloud.box.client;

import com.geekbrains.april.cloud.box.common.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.function.Predicate;


public class MainController implements Initializable {

    @FXML
    TableView<FXFileInfo> localfilesTable;

    @FXML
    TableView<FXFileInfo> remotefilesTable;

    @FXML
    Button btnDeleteRem, btnRefreshRem, btnDownloadRem;

    @FXML
    Button btnDeleteLoc, btnRefreshLoc, btnUploadLoc;

    @FXML
    Label titleRem;

    private String user = "";
    private String root_dir = "";

    private Callback<String, Boolean> senderInProgress;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        localfilesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String btnText = "Upload";
                if (newSelection.getPercent() < 0.999999) {
                    btnText = "Resume";
                    if (newSelection.inProgress) btnText = "Pause";
                }
                btnUploadLoc.setText(btnText);
            }
        });

        remotefilesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String btnText = "Download";
                if (newSelection.getPercent() < 0.999999) {
                    btnText = "Resume";
                    if (newSelection.inProgress) btnText = "Pause";
                }
                btnDownloadRem.setText(btnText);
            }
        });

        Network.sendMsg(new FileListRequest());

    }

    public void setUser(String user) {
        this.user = user;
        titleRem.setText("Remote: " + user);

    }

    public void setRoot_dir(String root_dir) {
        this.root_dir = root_dir;
    }

    public void setSenderInProgress(Callback<String, Boolean> senderInProgress) {
        this.senderInProgress = senderInProgress;
        refreshLocalFilesList();
    }

    public void pressBtnDeleteRem(ActionEvent actionEvent) {
        FileInfo info = remotefilesTable.getSelectionModel().getSelectedItem().getFileInfo();
        Network.sendMsg(new FileDeleteRequest(info));
    }

    public void pressBtnRefreshRem(ActionEvent actionEvent) {
        Network.sendMsg(new FileListRequest());
    }

    public void pressBtnDownloadRem(ActionEvent actionEvent) {
        FileInfo info = remotefilesTable.getSelectionModel().getSelectedItem().getFileInfo();
        Network.sendMsg(new FileDownloadRequest(info));
    }

    public void pressBtnDeleteLoc(ActionEvent actionEvent) throws IOException {
        FileInfo info = localfilesTable.getSelectionModel().getSelectedItem().getFileInfo();
        String longFileName = root_dir + "/" + info.fileName;
        Files.delete(Paths.get(longFileName));
        refreshLocalFilesList();
    }

    public void pressBtnRefreshLoc(ActionEvent actionEvent) {
        refreshLocalFilesList();
    }

    public void pressBtnUploadLoc(ActionEvent actionEvent) throws NoSuchAlgorithmException {
        FileInfo info = localfilesTable.getSelectionModel().getSelectedItem().getFileInfo();
        info.MD5 = FileHelper.calculateMD5(Paths.get("client_storage/" + info.fileName));
        Network.sendMsg(new FileUploadRequest(info));
    }

    FXFileInfo getFileInfoFX(Path p) {
        try {
            String fileName = p.getFileName().toString();
            long position = Files.size(p);
            FileInfo fileInfo = FXFileInfo.parseToFileInfo(fileName, position);
            FXFileInfo fxFileInfo = null;
            if(fileInfo.position == fileInfo.fileLength) fxFileInfo = new FXFileInfo(fileInfo);
            return  fxFileInfo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void refreshLocalFilesList() {
        localfilesTable.getItems().clear();
        try {
            Files.list(Paths.get("client_storage"))
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> getFileInfoFX(p))
                    .filter(o -> o != null)
                    .forEach(o -> {
                        if (senderInProgress.call(o.fileInfo.fileName)) o.setInProgress(true);
                        localfilesTable.getItems().add(o);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!localfilesTable.getItems().isEmpty()) localfilesTable.getSelectionModel().select(0);
    }

    public void refreshRemoteFilesList(ArrayList<FileInfo> list, Predicate<String> inProgress) {
        remotefilesTable.getItems().clear();
        for (FileInfo info : list) {
            FXFileInfo fxinfo = new FXFileInfo(info);
            fxinfo.setInProgress(inProgress.test(info.fileName));
            remotefilesTable.getItems().add(fxinfo);
        }
        if (!remotefilesTable.getItems().isEmpty()) remotefilesTable.getSelectionModel().select(0);

    }
}

