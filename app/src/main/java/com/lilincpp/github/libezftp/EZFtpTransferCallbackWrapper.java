package com.lilincpp.github.libezftp;

import android.os.Handler;
import android.os.Looper;

import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;

/**
 * transfer data wrapper,it changed thread to ui thread
 */
final class EZFtpTransferCallbackWrapper implements OnEZFtpDataTransferCallback {

    private OnEZFtpDataTransferCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    public EZFtpTransferCallbackWrapper(OnEZFtpDataTransferCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onStateChanged(final int state) {
        synchronized (lock) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onStateChanged(state);
                    }
                }
            });
        }
    }

    @Override
    public void onTransferred(final long fileSize, final int transferredSize) {
        synchronized (lock) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onTransferred(fileSize,transferredSize);
                    }
                }
            });
        }
    }

    @Override
    public void onErr(final int code, final String msg) {
        synchronized (lock) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onErr(code, msg);
                    }
                }
            });
        }
    }
}
