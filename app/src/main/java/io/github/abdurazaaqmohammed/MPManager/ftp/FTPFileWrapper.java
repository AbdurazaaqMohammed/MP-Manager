package io.github.abdurazaaqmohammed.MPManager.ftp;

import androidx.annotation.NonNull;

import com.lilincpp.github.libezftp.EZFtpFile;
import com.lilincpp.github.libezftp.IEZFtpClient;

import java.io.File;

public class FTPFileWrapper extends File {

    private final EZFtpFile ftpFile;
    private final String currentPath;
    public final IEZFtpClient client;
    private final String address;

    public FTPFileWrapper(String currentPath, EZFtpFile ftpFile) {
        this(currentPath, ftpFile, null, "ftp://");
    }

    public FTPFileWrapper(String currentPath, EZFtpFile ftpFile, IEZFtpClient client, String address) {
        super(client == null ? currentPath : "/ftp/" + Integer.toHexString(System.identityHashCode(client)) + currentPath, ftpFile.getName());
        this.currentPath = currentPath;
        this.ftpFile = ftpFile;
        this.client = client;
        this.address = address;
    }

    public static FTPFileWrapper folder(String path, IEZFtpClient client, String address) {
        String clean = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        int slash = clean.lastIndexOf('/');
        String parent = slash <= 0 ? "/" : clean.substring(0, slash);
        String name = clean.equals("/") ? "" : clean.substring(slash + 1);
        return new FTPFileWrapper(parent, new EZFtpFile(name, parent, EZFtpFile.TYPE_DIRECTORY, 0, null), client, address);
    }

    public String connectionAddress() { return address; }
    public String remoteParent() { return currentPath; }
    public String displayPath() {
        try { return address + new java.net.URI(null, null, remotePath(), null).toASCIIString(); }
        catch (Exception ignored) { return address + remotePath(); }
    }

    @Override public File getParentFile() {
        if (client == null) return super.getParentFile();
        return "/".equals(remotePath()) ? null : folder(currentPath, client, address);
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
        return ftpFile.getModifiedDate() == null ? 0 : ftpFile.getModifiedDate().getTime();
    }

    public String remotePath() {
        return currentPath + (currentPath.endsWith("/") ? "" : "/") + ftpFile.getName();
    }

    @Override public boolean isHidden() { return ftpFile.getName().startsWith("."); }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }
}
