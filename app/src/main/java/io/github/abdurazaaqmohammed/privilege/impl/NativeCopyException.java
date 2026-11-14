package io.github.abdurazaaqmohammed.privilege.impl;

import java.io.IOException;

/** A native copy started but did not complete; do not silently retry it. */
public final class NativeCopyException extends IOException {
    public NativeCopyException(String message) {
        super(message);
    }

    public NativeCopyException(String message, Throwable cause) {
        super(message, cause);
    }
}
