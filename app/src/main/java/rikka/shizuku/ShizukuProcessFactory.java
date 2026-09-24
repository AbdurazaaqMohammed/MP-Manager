package rikka.shizuku;

import moe.shizuku.server.IRemoteProcess;

/**
 * Bridge giving MP Manager access to Shizuku's remote process creation.
 * Lives in the rikka.shizuku package to reach the package-private ShizukuRemoteProcess
 * constructor and the protected requireService() accessor.
 */
public final class ShizukuProcessFactory {

    public Process newProcess(String[] cmd, String[] env, String dir) {
        try {
            IRemoteProcess rp = Shizuku.requireService().newProcess(cmd, env, dir);
            return new ShizukuRemoteProcess(rp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
