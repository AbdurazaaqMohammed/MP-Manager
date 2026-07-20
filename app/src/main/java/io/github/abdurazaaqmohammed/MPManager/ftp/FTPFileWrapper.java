package io.github.abdurazaaqmohammed.MPManager.ftp;

import androidx.annotation.NonNull;

import com.lilincpp.github.libezftp.EZFtpFile;

import java.io.File;

public class FTPFileWrapper extends File {

    private final EZFtpFile ftpFile;
    private final String currentPath;

    public FTPFileWrapper(String currentPath, EZFtpFile ftpFile) {
        super(currentPath, ftpFile.getName());
        this.currentPath = currentPath;
        this.ftpFile = ftpFile;
    }

    public EZFtpFile getFtpFile() {
        return ftpFile;
    }

    @NonNull
    @Override
    public String getName() {
        return ftpFile.getName();
    }

    @Override
    public boolean isDirectory() {
        return ftpFile.getType() == 1;
    }

    @Override
    public boolean isFile() {
        return ftpFile.getType() == 0;
    }

    @Override
    public long length() {
        return ftpFile.getSize();
    }

    @Override
    public long lastModified() {
        return 0; // EZFtpFile might not expose last modified time easily
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }
}
