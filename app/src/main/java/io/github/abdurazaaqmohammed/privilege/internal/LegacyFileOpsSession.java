/*
 * Privilege layer — internal adapters over the legacy implementation.
 * Preserve the exact legacy semantics — no behavior change.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.internal.AccessManager;

/** M2 file-ops session: delegates to AccessManager (backend routing inside). */
public final class LegacyFileOpsSession implements FileOpsSession {

    private final Context context;

    public LegacyFileOpsSession(Context context) {
        this.context = context;
    }

    @Override
    public boolean needsElevated(String absPath) {
        return AccessManager.needsElevated(context, absPath);
    }

    @Override
    public boolean exists(String absPath) {
        return AccessManager.exists(context, absPath);
    }

    @Override
    public boolean elevatedExists(String absPath) {
        return AccessManager.elevatedExists(context, absPath);
    }

    @Override
    public boolean isDirectory(String absPath) {
        return AccessManager.isDirectory(context, absPath);
    }

    @Override
    public boolean isFile(String absPath) {
        return AccessManager.isFile(context, absPath);
    }

    @Override
    public File[] listWithStat(String dirPath) {
        return AccessManager.listWithStat(context, dirPath);
    }

    @Override
    public long getSize(String absPath) {
        return AccessManager.getSize(context, absPath);
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
        AccessManager.copyFile(context, src, dest, useElevated);
    }

    @Override
    public void copyDir(String src, String dest, boolean useElevated) throws IOException {
        AccessManager.copyDir(context, src, dest, useElevated);
    }

    @Override
    public void move(String src, String dest, boolean useElevated) throws IOException {
        AccessManager.move(context, src, dest, useElevated);
    }

    @Override
    public void copyPreserving(String src, String dest, boolean directory, boolean useElevated) throws IOException {
        AccessManager.copyPreserving(context, src, dest, directory, useElevated);
    }

    @Override
    public void mkdir(String path, boolean useElevated) throws IOException {
        AccessManager.mkdir(context, path, useElevated);
    }

    @Override
    public void delete(String path, boolean useElevated) throws IOException {
        AccessManager.delete(context, path, useElevated);
    }

    @Override
    public void rename(String oldPath, String newPath, boolean useElevated) throws IOException {
        AccessManager.rename(context, oldPath, newPath, useElevated);
    }

    @Override
    public void touch(String path, boolean useElevated) throws IOException {
        AccessManager.touch(context, path, useElevated);
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
        return AccessManager.deleteAll(context, paths, useElevated);
    }

    @Override
    public void deleteTree(List<String> paths, boolean useElevated) throws IOException {
        AccessManager.deleteTree(context, paths, useElevated, null);
    }
}
