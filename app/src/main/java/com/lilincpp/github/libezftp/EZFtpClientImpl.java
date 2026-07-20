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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import it.sauronsoftware.ftp4j.FTPListParseException;

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
            //create ftp client object
            ftpClient = new FTPClient();
            isInit = true;
        }
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
        connect(serverIp, port, userName, password, null);
    }

    @Override
    public void connect(@NonNull final String serverIp, @NonNull final int port, @NonNull final String userName, @NonNull final String password, @Nullable final OnEZFtpCallBack<Void> callBack) {
        checkInit();
        Log.d(TAG, "connect ftp server : serverIp = " + serverIp + ",port = " + port
                + ",user = " + userName + ",pw = " + password);
        taskHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ftpClient.connect(serverIp, port);
                    ftpClient.login(userName, password);
                    getCurDirPath(null);
                    callbackNormalSuccess(callBack, null);
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Read server response fail!");
                } catch (FTPException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (IllegalStateException e) {
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
                    ftpClient.disconnect(true);
                    callbackNormalSuccess(callBack, null);
                    release();
                } catch (IOException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Read server response fail!");
                } catch (FTPException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (IllegalStateException e) {
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
                    FTPFile[] ftpFiles = ftpClient.list();
                    List<EZFtpFile> ezFtpFiles = new ArrayList<>();
                    for (FTPFile ftpFile : ftpFiles) {
                        ezFtpFiles.add(
                                new EZFtpFile(
                                        ftpFile.getName(),
                                        curDirPath,
                                        ftpFile.getType(),
                                        ftpFile.getSize(),
                                        ftpFile.getModifiedDate()
                                ));
                    }
                    callbackNormalSuccess(callBack, ezFtpFiles);
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Read server response fail!");
                } catch (FTPException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (IllegalStateException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPDataTransferException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPAbortedException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPListParseException e) {
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
                    final String path = ftpClient.currentDirectory();
                    setCurDirPath(path);
                    callbackNormalSuccess(callBack, path);
                } catch (IOException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Read server response fail!");
                } catch (FTPException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (IllegalStateException e) {
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
                        ftpClient.changeDirectory(path);
                        setCurDirPath(path);
                        callbackNormalSuccess(callBack, path);
                    }
                } catch (IOException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_FAIL, "Read server response fail!");
                } catch (FTPException e) {
                    callbackNormalFail(callBack, EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (IllegalStateException e) {
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
                    ftpClient.download(remoteFile.getName(), localFile, new FTPDataTransferListener() {
                        @Override
                        public void started() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.START);
                        }

                        @Override
                        public void transferred(int i) {
                            callbackWrapper.onTransferred(remoteFile.getSize(), i);
                        }

                        @Override
                        public void completed() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.COMPLETED);
                        }

                        @Override
                        public void aborted() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ABORTED);
                        }

                        @Override
                        public void failed() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                            callbackWrapper.onErr(EZFtpResultCode.RESULT_FAIL, "Download file fail!");
                        }
                    });
                } catch (IOException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, "Read server response fail!");
                } catch (FTPException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPDataTransferException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPAbortedException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ABORTED);
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
                    ftpClient.upload(localFile, new FTPDataTransferListener() {
                        @Override
                        public void started() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.START);
                        }

                        @Override
                        public void transferred(int i) {
                            callbackWrapper.onTransferred(localFile.length(), i);
                        }

                        @Override
                        public void completed() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.COMPLETED);
                        }

                        @Override
                        public void aborted() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ABORTED);
                        }

                        @Override
                        public void failed() {
                            callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                            callbackWrapper.onErr(EZFtpResultCode.RESULT_FAIL, "Download file fail!");
                        }
                    });
                } catch (IOException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, "IOException");
                } catch (FTPIllegalReplyException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, "Read server response fail!");
                } catch (FTPException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPDataTransferException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ERROR);
                    callbackWrapper.onErr(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                } catch (FTPAbortedException e) {
                    callbackWrapper.onStateChanged(OnEZFtpDataTransferCallback.ABORTED);
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
                    ftpClient.deleteDirectory(path);
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
                    ftpClient.createDirectory(path);
                    callbackWrapper.onSuccess(null);
                } catch (Exception e) {
                    callbackWrapper.onFail(EZFtpResultCode.RESULT_EXCEPTION, e.getMessage());
                }
            }
        });
    }
}
