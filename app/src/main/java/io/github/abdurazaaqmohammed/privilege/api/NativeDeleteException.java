package io.github.abdurazaaqmohammed.privilege.api;

import java.io.IOException;

/** The native delete started and failed; never silently replay a destructive batch. */
public final class NativeDeleteException extends IOException {
    public NativeDeleteException(String message) { super(message); }
    public NativeDeleteException(String message, Throwable cause) { super(message, cause); }
}
