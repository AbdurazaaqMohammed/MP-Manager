package com.lilincpp.github.libezftp.callback;

/**
 *
 * callback when client request
 *
 * @param <E>
 */
public interface OnEZFtpCallBack<E> {

    void onSuccess(E response);

    void onFail(int code, String msg);
}
