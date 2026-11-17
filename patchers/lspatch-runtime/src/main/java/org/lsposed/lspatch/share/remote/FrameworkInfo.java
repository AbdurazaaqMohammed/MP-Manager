package org.lsposed.lspatch.share.remote;

/**
 * The framework-identity answers both services give, gathered once so the shared stubs stay free of
 * any dependency on how the host discovers them (e.g. {@code LSPConfig}). The caller in patch-loader
 * or the manager fills this in.
 */
public final class FrameworkInfo {
    public final String name;
    public final String version;
    public final long versionCode;
    public final long properties;

    public FrameworkInfo(String name, String version, long versionCode, long properties) {
        this.name = name;
        this.version = version;
        this.versionCode = versionCode;
        this.properties = properties;
    }
}
