package com.lilincpp.github.libezftp.exceptions;

/**
 * if client or server is not init,will throw this exception
 *
 * @author lilin
 */
public class EZFtpNoInitException extends IllegalStateException {

    public EZFtpNoInitException() {
        super();
    }

    public EZFtpNoInitException(String s) {
        super(s);
    }
}
