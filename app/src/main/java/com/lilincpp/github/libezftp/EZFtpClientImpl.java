package com.lilincpp.github.libezftp;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;
import com.lilincpp.github.libezftp.exceptions.EZFtpNoInitException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.X509TrustManager;

/**
 * implement {@link IEZFtpClient}
 *
 * @author lilin
 */
final class EZFtpClientImpl implements IEZFtpClient {

    private static final String TAG = "EZFtpClientImpl";
    private static final String HOME_DIR = "/";

    private FTPClient ftpClient;
    private HandlerThread taskThread = new HandlerThread("ftp-task");
    private Handler taskHandler;
    private final Object lock = new Object();
    private boolean isInit = false;
    private String curDirPath;
    private int securityType = IEZFtpClient.SECURITY_NONE;

    EZFtpClientImpl() {
        init();
    }

    private void setCurDirPath(String path) {
        synchronized (lock) {
            this.curDirPath = path;
        }
    }

    /**
     * init ftp client
     */
    private void init() {
        synchronized (lock) {
            //init work thread
            final HandlerThread temp = taskThread;
            if (!temp.isAlive()) {
                temp.start();
                taskHandler = new Handler(temp.getLooper());
            }
            isInit = true;
        }
    }

    /**
     * create the underlying ftp client based on the security type
     */
    private FTPClient createFtpClient(int securityType) {
        if (securityType == IEZFtpClient.SECURITY_FTPS_EXPLICIT || securityType == IEZFtpClient.SECURITY_FTPS_IMPLICIT) {
            FTPSClient ftpsClient = new FTPSClient(securityType == IEZFtpClient.SECURITY_FTPS_IMPLICIT);
            ftpsClient.setTrustManager(new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });
            ftpsClient.setHostnameVerifier((hostname, session) -> true);
            return ftpsClient;
        }
        return new FTPClient();
    }

    /**
     * release ftp client.
     */
    @Override
    public void release() {
        synchronized (lock) {
            //disconnect if it is currently connected
            if (ftpClient != null && isConnected()) {
                disconnect();
            }
            //release work thread
            final HandlerThread temp = taskThread;
            if (temp.isAlive()) {
                temp.quit();
            }
            //clear message queue
            if (taskHandler != null) {
                taskHandler.removeCallbacksAndMessages(null);
            }
            isInit = false;
        }
    }

    /**
     * check init status
     */
    private void checkInit() {
        if (!isInit) {
            throw new EZFtpNoInitException("EZFtpClient is not init or has been released！");
        }
    }

    /**
     * Get the upper level path
     *
     * @return the previous level path
     */
    private @Nullable
    String getBackUpPath() {
        if (TextUtils.isEmpty(curDirPath)) {
            return null;
        }

        //if cur path is home dir,return
        //Because it can't go back to the previous level
        if (TextUtils.equals(curDirPath, HOME_DIR)) {
            return HOME_DIR;
        }

        //get last index
        final int lastIndex = curDirPath.lastIndexOf("/");
        if (lastIndex == 0) {
            return HOME_DIR;
        }
        return curDirPath.substring(0, lastIndex);

    }

    @SuppressWarnings("unchecked")
    private void callbackNormalSuccess(@Nullable final OnEZFtpCallBack callBack, @Nullable final Object response) {
        EZFtpSampleCallbackWrapper wrapper = new EZFtpSampleCallbackWrapper(callBack);
        wrapper.onSuccess(response);
    }

    @SuppressWarnings("unchecked")
    private void callbackNormalFail(@Nullable final OnEZFtpCallBack callBack, final int code, final String msg) {
        EZFtpSampleCallbackWrapper wrapper = new EZFtpSampleCallbackWrapper(callBack);
        wrapper.onFail(code, msg);
    }


    @Override
    public void connect(@NonNull final String serverIp, @NonNull final int port, @NonNull final String userName, @NonNull final String password) {
        connect(serverIp, port, userName, password, IEZFtpClient.SECURITY_NONE, null);
    }

    @Override
    public void connect(@NonNull final String serverIp, @NonNull final int port, @NonNull final String userName, @NonNull final String password, @Nullable final OnEZFtpCallBack<Void> callBack) {
        connect(serverIp, port, userName, password, IEZFtpClient.SECURITY_NONE, callBack);
    }

    @Override
    public void connect(@NonNull final String serverIp, @NonNull final int port, @NonNull final String userName, @NonNull final String password, final int securityType, @Nullable final OnEZFtpCallBack<Void> callBack) {
        checkInit();
        this.securityType = securityType;
        Log.d(TAG, "connect ftp server : serverIp = " + serverIp + ",port = " + port
                + ",user = " + userName + ",pw = " + password + ",securityType = " + securityType);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient = createFtpClient(securityType);
                    ftpClient.connect(serverIp, port);
                    if (!ftpClient.login(userName, password)) {
                        callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Login failed!");
                        return;
                    }
                    if (securityType == IEZFtpClient.SECURITY_FTPS_EXPLICIT && ftpClient instanceof FTPSClient) {
                        ((FTPSClient) ftpClient).execPBSZ(0);
                        ((FTPSClient) ftpClient).execPROT("P");
                    }
                    getCurDirPath(null);
                    callbackNormalSuccess(callBack, null);
                } catch (SocketException e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage() != null ? e.getMessage() : "IOException");
                } catch (Exception e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void disconnect() {
        disconnect(null);
    }

    @Override
    public void disconnect(@Nullable final OnEZFtpCallBack<Void> callBack) {
        checkInit();
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ftpClient != null && ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                    callbackNormalSuccess(callBack, null);
                    release();
                } catch (IOException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (Exception e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean isConnected() {
        return ftpClient != null && ftpClient.isConnected();
    }

    @Override
    public void getCurDirFileList(@Nullable final OnEZFtpCallBack<List<EZFtpFile>> callBack) {
        checkInit();
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPFile[] ftpFiles = ftpClient.listFiles();
                    List<EZFtpFile> ezFtpFiles = new ArrayList<>();
                    for (FTPFile ftpFile : ftpFiles) {
                        Date modifiedDate = null;
                        if (ftpFile.getTimestamp() != null) {
                            modifiedDate = ftpFile.getTimestamp().getTime();
                        }
                        ezFtpFiles.add(
                                new EZFtpFile(
                                        ftpFile.getName(),
                                        curDirPath,
                                        ftpFile.getType(),
                                        ftpFile.getSize(),
                                        modifiedDate
                                ));
                    }
                    callbackNormalSuccess(callBack, ezFtpFiles);
                } catch (Exception e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void getCurDirPath(@Nullable final OnEZFtpCallBack<String> callBack) {
        checkInit();
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final String path = ftpClient.printWorkingDirectory();
                    setCurDirPath(path);
                    callbackNormalSuccess(callBack, path);
                } catch (Exception e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void changeDirectory(@Nullable final String path, @Nullable final OnEZFtpCallBack<String> callBack) {
        checkInit();
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (TextUtils.isEmpty(path)) {
                        callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "path is empty!");
                    } else {
                        if (ftpClient.changeWorkingDirectory(path)) {
                            setCurDirPath(path);
                            callbackNormalSuccess(callBack, path);
                        } else {
                            callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Change directory failed!");
                        }
                    }
                } catch (Exception e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void backup(@Nullable final OnEZFtpCallBack<String> callBack) {
        changeDirectory(getBackUpPath(), callBack);
    }

    @Override
    public void backToHomeDir(OnEZFtpCallBack<String> callBack) {
        changeDirectory(HOME_DIR, callBack);
    }

    @Override
    public void downloadFile(@NonNull final EZFtpFile remoteFile, @NonNull String localFilePath, @Nullable OnEZFtpDataTransferCallback callback) {
        checkInit();

        final File localFile = new File(localFilePath);
        final EZFtpTransferCallbackWrapper callbackWrapper
                = new EZFtpTransferCallbackWrapper(callback);

        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.START);
                    ftpClient.setCopyStreamListener(new CopyStreamListener() {
                        @Override
                        public void bytesTransferred(CopyStreamEvent event) {
                        }

                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            callbackWrapper.onTransferred(streamSize, (int) totalBytesTransferred);
                        }
                    });
                    boolean ok;
                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        ok = ftpClient.retrieveFile(remoteFile.getName(), fos);
                    }
                    if (ok) {
                        callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.COMPLETED);
                    } else {
                        callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                        callbackWrapper.onErr(EZFtpResultCode.RESULT_FAIL, "Download file fail!");
                    }
                } catch (Exception e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void uploadFile(@NonNull final String localFilePath, @Nullable OnEZFtpDataTransferCallback callback) {
        checkInit();

        final File localFile = new File(localFilePath);
        final EZFtpTransferCallbackWrapper callbackWrapper
                = new EZFtpTransferCallbackWrapper(callback);

        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.START);
                    ftpClient.setCopyStreamListener(new CopyStreamListener() {
                        @Override
                        public void bytesTransferred(CopyStreamEvent event) {
                        }

                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            callbackWrapper.onTransferred(streamSize, (int) totalBytesTransferred);
                        }
                    });
                    boolean ok;
                    try (FileInputStream fis = new FileInputStream(localFile)) {
                        ok = ftpClient.storeFile(localFile.getName(), fis);
                    }
                    if (ok) {
                        callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.COMPLETED);
                    } else {
                        callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                        callbackWrapper.onErr(EZFtpResultCode.RESULT_FAIL, "Upload file fail!");
                    }
                } catch (Exception e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean curDirIsHomeDir() {
        return TextUtils.equals(curDirPath, HOME_DIR);
    }

    @Override
    public void deleteFile(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack) {
        checkInit();
        final EZFtpSampleCallbackWrapper<Void> callbackWrapper = new EZFtpSampleCallbackWrapper<>(callBack);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.deleteFile(path);
                    callbackWrapper.onSuccess(null);
                } catch (Exception e) {
                    callbackWrapper.onFail(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void deleteDirectory(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack) {
        checkInit();
        final EZFtpSampleCallbackWrapper<Void> callbackWrapper = new EZFtpSampleCallbackWrapper<>(callBack);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.removeDirectory(path);
                    callbackWrapper.onSuccess(null);
                } catch (Exception e) {
                    callbackWrapper.onFail(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void rename(@NonNull String oldPath, @NonNull String newPath, @Nullable OnEZFtpCallBack<Void> callBack) {
        checkInit();
        final EZFtpSampleCallbackWrapper<Void> callbackWrapper = new EZFtpSampleCallbackWrapper<>(callBack);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.rename(oldPath, newPath);
                    callbackWrapper.onSuccess(null);
                } catch (Exception e) {
                    callbackWrapper.onFail(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }

    @Override
    public void createDirectory(@NonNull String path, @Nullable OnEZFtpCallBack<Void> callBack) {
        checkInit();
        final EZFtpSampleCallbackWrapper<Void> callbackWrapper = new EZFtpSampleCallbackWrapper<>(callBack);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.makeDirectory(path);
                    callbackWrapper.onSuccess(null);
                } catch (Exception e) {
                    callbackWrapper.onFail(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }
}
