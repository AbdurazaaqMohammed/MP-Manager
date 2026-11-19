/*
 * Privilege layer — internal sessions for the Shizuku backend.
 * Delegates to the legacy ShizukuManager engine; semantics identical to the
 * SHIZUKU branches of AccessManager.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.internal.AccessManager;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuManager;
import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession.CopyOp;

public final class ShizukuSessions {

    private ShizukuSessions() {
    }

    public static FileOpsSession fileOps(Context context) {
        return new ShizukuFileOpsSession(context);
    }

    private static final class ShizukuFileOpsSession implements FileOpsSession {

        private final Context context;

        ShizukuFileOpsSession(Context context) {
            this.context = context;
        }

        @Override
        public boolean needsElevated(String absPath) {
            return AccessManager.needsElevated(context, absPath);
        }

        @Override
        public boolean exists(String absPath) {
            return ShizukuManager.exists(context, absPath);
        }

        @Override
        public boolean elevatedExists(String absPath) {
            return exists(absPath);
        }

        @Override
        public boolean isDirectory(String absPath) {
            return ShizukuManager.isDirectory(context, absPath);
        }

        @Override
        public boolean isFile(String absPath) {
            return ShizukuManager.isFile(context, absPath);
        }

        @Override
        public File[] listWithStat(String dirPath) {
            return ShizukuManager.listWithStat(context, dirPath);
        }

        @Override
        public long getSize(String absPath) {
            return ShizukuManager.getFileSize(context, absPath);
        }

        @Override
        public long getMtime(String absPath) {
            return ShizukuManager.getMtime(context, absPath);
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
            ShizukuManager.copyFile(context, src, dest);
        }

        @Override
        public void copyDir(String src, String dest, boolean useElevated) throws IOException {
            ShizukuManager.copyDir(context, src, dest);
        }

        @Override
        public void move(String src, String dest, boolean useElevated) throws IOException {
            ShizukuManager.moveAll(context, List.of(new CopyOp(src, dest, false, false)), null);
        }

        @Override
        public void copyPreserving(String src, String dest, boolean directory, boolean useElevated) throws IOException {
            AccessManager.copyPreserving(context, src, dest, directory, useElevated);
        }

        @Override
        public void mkdir(String path, boolean useElevated) throws IOException {
            ShizukuManager.mkdir(context, path);
        }

        @Override
        public void delete(String path, boolean useElevated) throws IOException {
            ShizukuManager.delete(context, path);
        }

        @Override
        public void rename(String oldPath, String newPath, boolean useElevated) throws IOException {
            ShizukuManager.rename(context, oldPath, newPath);
        }

        @Override
        public void touch(String path, boolean useElevated) throws IOException {
            ShizukuManager.touch(context, path);
        }

        @Override
        public boolean touchMtime(String path, long millis) {
            return ShizukuManager.touchMtimeShell(path, millis);
        }

        @Override
        public long dirSize(String path) {
            return ShizukuManager.dirSize(context, path);
        }

        @Override
        public List<String> deleteAll(List<String> paths, boolean useElevated) throws IOException {
            ShizukuManager.deleteAll(context, paths, null);
            return paths;
        }

        @Override
        public void deleteTree(List<String> paths, boolean useElevated) throws IOException {
            ShizukuManager.deleteTree(context, paths, null);
        }
    }
}
