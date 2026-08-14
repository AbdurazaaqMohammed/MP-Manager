package com.lilincpp.github.libezftp;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;

import java.util.List;

/**
 * FTP 客户端操作接口
 *
 * FTP client interface
 *
 * @author lilin
 */
public interface IEZFtpClient {

    int SECURITY_NONE = 0;
    int SECURITY_FTPS_EXPLICIT = 1;
    int SECURITY_FTPS_IMPLICIT = 2;

    /**
     * Connect FTP Server
     *
     * @param serverIp ftp server ip
     * @param port     ftp server port
     * @param userName login username
     * @param password login password
     */
    void connect(@NonNull String serverIp,
                 @NonNull int port,
                 @NonNull String userName,
                 @NonNull String password);

    /**
     * Connect FTP Server
     *
     * @param serverIp ftp server ip
     * @param port     ftp server port
     * @param userName login username
     * @param password login password
     * @param callBack the async callback
     */
    void connect(@NonNull String serverIp,
                 @NonNull int port,
                 @NonNull String userName,
                 @NonNull String password,
                 @Nullable OnEZFtpCallBack<Void> callBack);

    /**
     * Connect FTP Server with the given security type
     *
     * @param serverIp     ftp server ip
     * @param port         ftp server port
     * @param userName     login username
     * @param password     login password
     * @param securityType one of {@link #SECURITY_NONE}, {@link #SECURITY_FTPS_EXPLICIT}, {@link #SECURITY_FTPS_IMPLICIT}
     * @param callBack     the async callback
     */
    void connect(@NonNull String serverIp,
                 @NonNull int port,
                 @NonNull String userName,
                 @NonNull String password,
                 int securityType,
                 @Nullable OnEZFtpCallBack<Void> callBack);

    /**
     * disconnect server
     */
    void disconnect();

    /**
     * disconnect server
     * @param callBack the async callback
     */
    void disconnect(@Nullable OnEZFtpCallBack<Void> callBack);

    ////////////////////////////////////////

    /**
     * get remote file list from cur dir
     * @param callBack the async callback
     */
    void getCurDirFileList(@Nullable OnEZFtpCallBack<List<EZFtpFile>> callBack);

    /**
     * get remote cur dir path
     * @param callBack the async callback
     */
    void getCurDirPath(@Nullable OnEZFtpCallBack<String> callBack);

    /**
     * change remote cur dir
     * @param path target dir name
     * @param callBack the async callback
     */
    void changeDirectory(@NonNull String path, @Nullable OnEZFtpCallBack<String> callBack);

    /**
     * Return to the previous level
     * @param callBack the async callback
     */
    void backup(@Nullable OnEZFtpCallBack<String> callBack);

    /**
     * download remote file from cur dir or absolute remote path
     * @param remoteFile remote file
     * @param localFilePath save path when downloading
     * @param callback the async callback
     */
    void downloadFile(@NonNull EZFtpFile remoteFile, @NonNull String localFilePath, @Nullable OnEZFtpDataTransferCallback callback);

    /**
     * upload local file to remote server
     * @param localFilePath local file path
     * @param callback the async callback
     */
    void uploadFile(@NonNull String localFilePath, @Nullable OnEZFtpDataTransferCallback callback);

    ////////////////////////////////////////

    /**
     * Is it connected to the server?
     * @return true is connected,false is disconnected
     */
    boolean isConnected();

    /**
     * Whether the server directory is the home directory
     * @return true it is,false it is not.
     */
    boolean curDirIsHomeDir();

    /**
     * change remote server cur dir to home dir
     * @param callBack the async callback
     */
    void backToHomeDir(OnEZFtpCallBack<String> callBack);

    ////////////////////////////////////////

    void deleteFile(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack);
    
    void deleteDirectory(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack);
    
    void rename(@NonNull String oldPath, @NonNull String newPath, @Nullable OnEZFtpCallBack<Void> callBack);
    
    void createDirectory(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack);

    ////////////////////////////////////////

    /**
     * release ftp client.This method will disconnect to ftp server.
     */
    void release();
}
