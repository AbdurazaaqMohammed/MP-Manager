package io.github.abdurazaaqmohammed.domain.remote;

import java.io.IOException;

/**
 * Remote-filesystem failure. Unchecked because callers are UI code paths that
 * already funnel every failure into an error dialog; a checked exception on
 * every list call would only add noise.
 *
 * <p>{@link #isAuthFailure()} lets the host prompt for credentials again instead
 * of showing a generic error.
 */
public class RemoteException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Backend-specific code, e.g. an S3 or FTP response number. */
    private final int code;
    private final boolean authFailure;

    public RemoteException(String message) {
        this(message, -1, false, null);
    }

    public RemoteException(String message, Throwable cause) {
        this(message, -1, false, cause);
    }

    public RemoteException(String message, int code, boolean authFailure, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.authFailure = authFailure;
    }

    public int code() {
        return code;
    }

    public boolean isAuthFailure() {
        return authFailure;
    }

    public static RemoteException auth(String message) {
        return new RemoteException(message, -1, true, null);
    }
}