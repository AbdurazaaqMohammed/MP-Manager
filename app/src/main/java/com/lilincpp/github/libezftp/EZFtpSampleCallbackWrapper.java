package com.lilincpp.github.libezftp;

import android.os.Handler;
import android.os.Looper;

import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;

/**
 * callback wrapper{@link OnEZFtpCallBack},it will change current thread to ui thread.
 *
 * @param <E>
 */
final class EZFtpSampleCallbackWrapper<E> implements OnEZFtpCallBack<E> {

    private final Object lock = new Object();
    private OnEZFtpCallBack<E> onEZFtpCallBack;
    private Handler handler = new Handler(Looper.getMainLooper());

    public EZFtpSampleCallbackWrapper(OnEZFtpCallBack<E> onEZFtpCallBack) {
        this.onEZFtpCallBack = onEZFtpCallBack;
    }

    @Override
    public void onSuccess(final E response) {
        synchronized (lock) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (onEZFtpCallBack != null) {
                        onEZFtpCallBack.onSuccess(response);
                    }
                }
            });
        }
    }

    @Override
    public void onFail(final int code, final String msg) {
        synchronized (lock) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (onEZFtpCallBack != null) {
                        onEZFtpCallBack.onFail(code, msg);
                    }
                }
            });
        }
    }
}
