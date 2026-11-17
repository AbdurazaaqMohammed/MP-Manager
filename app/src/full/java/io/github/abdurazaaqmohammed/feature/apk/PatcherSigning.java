package io.github.abdurazaaqmohammed.feature.apk;

import java.security.Security;

/** Patcher entry points may run before MainActivity's asynchronous provider initialization. */
public final class PatcherSigning {
    private PatcherSigning() {}

    public static synchronized void initialize() {
        if (Security.getProvider("JKS") == null) Security.addProvider(new android.sun.security.provider.JavaKeyStoreProvider());
    }
}
