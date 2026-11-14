/*
 * Privilege layer — internal sessions for the root backend.
 * Delegates to the legacy RootManager engine; semantics identical to the
 * ROOT branches of AccessManager.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.privilege.impl.AccessManager;
import io.github.abdurazaaqmohammed.privilege.impl.RootManager;

public final class RootSessions {

    private RootSessions() {
    }

    public static ShellSession shell() {
        return RootShellSession.INSTANCE;
    }

    public static FileOpsSession fileOps(Context context) {
        return new RootFileOpsSession(context);
    }

    private static final class RootShellSession implements ShellSession {

        static final RootShellSession INSTANCE = new RootShellSession();

        private RootShellSession() {
        }

        @Override
        public Result exec(String command, long timeoutSeconds) {
            RootManager.ShellResult r = RootManager.getInstance(null).execute(command, (int) timeoutSeconds);
            return new Result(r.exitCode() == 0, r.output(), r.error(), r.exitCode());
        }

        @Override
        public Result exec(String command) {
            return exec(command, 30);
        }

        @Override
        public boolean isGranted() {
            return true; // root sessions are handed out only when authorized
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isInstalled(Context context) {
            return true;
        }

        @Override
        public void warmUp(Context context) {
            // root needs no warm-up
        }

        @Override
        public void requestPermission() {
            // interactive su prompt happens on first exec
        }

        @Override
        public void openShizukuApp(Context context) {
            // n/a
        }

        @Override
        public void onBinderReceived(Runnable callback) {
            // n/a
        }
    }

    private static final class RootFileOpsSession implements FileOpsSession {

        private final Context context;
        private final RootManager rm;

        RootFileOpsSession(Context context) {
            this.context = context;
            this.rm = RootManager.getInstance(context);
        }

        @Override
        public boolean needsElevated(String absPath) {
            return rm.needsRootFor(absPath);
        }

        @Override
        public boolean exists(String absPath) {
            return rm.exists(absPath);
        }

        @Override
        public boolean elevatedExists(String absPath) {
            return rm.exists(absPath);
        }

        @Override
        public boolean isDirectory(String absPath) {
            return rm.isDirectory(absPath);
        }

        @Override
        public boolean isFile(String absPath) {
            return rm.isFile(absPath);
        }

        @Override
        public File[] listWithStat(String dirPath) {
            return AccessManager.listWithStat(context, dirPath);
        }

        @Override
        public long getSize(String absPath) {
            return rm.getFileSize(absPath);
        }

        @Override
        public long getMtime(String absPath) {
            return AccessManager.getMtime(context, absPath);
        }

        @Override
        public File stageForRead(String srcAbsPath) throws IOException {
            return AccessManager.stageForRead(context, srcAbsPath);
        }

        @Override
        public void writeBack(File stagedCopy, String originalAbsPath) throws IOException {
            AccessManager.writeBack(context, stagedCopy, originalAbsPath);
        }

        @Override
        public void copyFile(String src, String dest, boolean useElevated) throws IOException {
            rm.copyFile(src, dest);
        }

        @Override
        public void copyDir(String src, String dest, boolean useElevated) throws IOException {
            rm.copyDir(src, dest);
        }

        @Override
        public void move(String src, String dest, boolean useElevated) throws IOException {
            AccessManager.move(context, src, dest, useElevated);
        }

        @Override
        public void copyPreserving(String src, String dest, boolean directory, boolean useElevated) throws IOException {
            rm.copyFilePreserving(src, dest);
        }

        @Override
        public void mkdir(String path, boolean useElevated) throws IOException {
            rm.mkdir(path);
        }

        @Override
        public void delete(String path, boolean useElevated) throws IOException {
            rm.delete(path);
        }

        @Override
        public void rename(String oldPath, String newPath, boolean useElevated) throws IOException {
            rm.rename(oldPath, newPath);
        }

        @Override
        public void touch(String path, boolean useElevated) throws IOException {
            rm.touch(path);
        }

        @Override
        public boolean touchMtime(String path, long millis) {
            return AccessManager.touchMtime(context, path, millis);
        }

        @Override
        public long dirSize(String path) {
            return AccessManager.dirSize(context, path);
        }

        @Override
        public List<String> deleteAll(List<String> paths, boolean useElevated) throws IOException {
            return rm.deleteAll(paths, null);
        }

        @Override
        public void deleteTree(List<String> paths, boolean useElevated) throws IOException {
            AccessManager.deleteTree(context, paths, useElevated, null);
        }
    }
}
