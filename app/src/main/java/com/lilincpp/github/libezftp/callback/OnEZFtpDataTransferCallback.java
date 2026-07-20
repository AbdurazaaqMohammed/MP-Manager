package com.lilincpp.github.libezftp.callback;

/**
 * callback when transfer data
 */
public interface OnEZFtpDataTransferCallback {

    int START = 1;
    int TRANSFER = 2;
    int COMPLETED = 3;
    int ERROR = 4;
    int ABORTED = 5;

    void onStateChanged(int state);

    void onTransferred(long fileSize, int transferredSize);

    void onErr(int code, String msg);
}
