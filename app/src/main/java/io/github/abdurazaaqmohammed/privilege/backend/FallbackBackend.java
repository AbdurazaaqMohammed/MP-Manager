/*
 * Privilege layer — the NONE backend.
 *
 * Returned when no privilege channel is installed/authorized. Its sessions
 * never throw NullPointerException-style surprises: every operation fails
 * with an explicit, descriptive error (or a graceful "not ready" answer for
 * probes), so callers carry zero if-else on their side.
 */
package io.github.abdurazaaqmohammed.privilege.backend;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.api.InstallerSession;
import io.github.abdurazaaqmohammed.privilege.internal.PrivilegedInstaller;
import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.FileOpsCap;
import io.github.abdurazaaqmohammed.privilege.spi.InstallerCap;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;

public final class FallbackBackend implements PrivilegeBackend, FileOpsCap, InstallerCap {

    private static final FallbackBackend INSTANCE = new FallbackBackend();

    public static FallbackBackend get() {
        return INSTANCE;
    }

    private FallbackBackend() {
    }

    @Override
    public String id() {
        return "none";
    }

    @Override
    public String label() {
        return "No privilege backend";
    }

    @Override
    public int priorityFor(Class<? extends Capability> capability) {
        return Integer.MIN_VALUE; // terminal fallback, always loses
    }

    @Override
    public boolean isAvailable(Context context) {
        return true; // always "present" — it is the terminal fallback
    }

    @Override
    public boolean isAuthorized(Context context) {
        return true;
    }

    @Override
    public FileOpsSession openFileOps(Context context) {
        return new UnavailableFileOps();
    }

    @Override
    public InstallerSession openInstaller(Context context) {
        return new UnavailableInstaller();
    }

    /** Explicit-error file ops: every mutation fails loudly, probes say no. */
    private static final class UnavailableFileOps implements FileOpsSession {

        private static IOException unavailable(String op) {
            return new IOException("No privilege backend authorized: cannot " + op);
        }

        @Override
        public boolean needsElevated(String absPath) {
            return false;
        }

        @Override
        public boolean exists(String absPath) {
            return false;
        }

        @Override
        public boolean elevatedExists(String absPath) {
            return false;
        }

        @Override
        public boolean isDirectory(String absPath) {
            return false;
        }

        @Override
        public boolean isFile(String absPath) {
            return false;
        }

        @Override
        public File[] listWithStat(String dirPath) {
            return new File[0];
        }

        @Override
        public long getSize(String absPath) {
            return -1;
        }

        @Override
        public long getMtime(String absPath) {
            return 0;
        }

        @Override
        public File stageForRead(String srcAbsPath) throws IOException {
            throw unavailable("stage " + srcAbsPath);
        }

        @Override
        public void writeBack(File stagedCopy, String originalAbsPath) throws IOException {
            throw unavailable("write back " + originalAbsPath);
        }

        @Override
        public void copyFile(String src, String dest, boolean useElevated) throws IOException {
            throw unavailable("copy " + src);
        }

        @Override
        public void copyDir(String src, String dest, boolean useElevated) throws IOException {
            throw unavailable("copy tree " + src);
        }

        @Override
        public void move(String src, String dest, boolean useElevated) throws IOException {
            throw unavailable("move " + src);
        }

        @Override
        public void copyPreserving(String src, String dest, boolean directory, boolean useElevated) throws IOException {
            throw unavailable("copy " + src);
        }

        @Override
        public void mkdir(String path, boolean useElevated) throws IOException {
            throw unavailable("mkdir " + path);
        }

        @Override
        public void delete(String path, boolean useElevated) throws IOException {
            throw unavailable("delete " + path);
        }

        @Override
        public void rename(String oldPath, String newPath, boolean useElevated) throws IOException {
            throw unavailable("rename " + oldPath);
        }

        @Override
        public void touch(String path, boolean useElevated) throws IOException {
            throw unavailable("touch " + path);
        }

        @Override
        public boolean touchMtime(String path, long millis) {
            return false;
        }

        @Override
        public long dirSize(String path) {
            return -1;
        }

        @Override
        public List<String> deleteAll(List<String> paths, boolean useElevated) throws IOException {
            throw unavailable("delete batch");
        }

        @Override
        public void deleteTree(List<String> paths, boolean useElevated) throws IOException {
            throw unavailable("delete tree batch");
        }
    }

    /** Explicit-error installer: readiness probes say no, installs fail loudly. */
    private static final class UnavailableInstaller implements InstallerSession {

        private static Exception unavailable() {
            return new IllegalStateException("No privilege backend authorized: cannot install");
        }

        @Override
        public boolean isRootSilentReady(Context context) {
            return false;
        }

        @Override
        public boolean isShizukuSilentReady(Context context) {
            return false;
        }

        @Override
        public boolean isPrivilegedSilentReady(Context context) {
            return false;
        }

        @Override
        public void installAsync(Context context, List<File> apkFiles,
                                 PrivilegedInstaller.Callback cb) {
            if (cb != null) cb.onError(new IllegalStateException("No privilege backend authorized"));
        }

        @Override
        public String installBlocking(Context context, List<File> apkFiles) throws Exception {
            throw unavailable();
        }

        @Override
        public boolean hasSignatureMismatch(Context context, List<File> apkFiles) {
            return false;
        }

        @Override
        public String lastTrace() {
            return "fallback: no backend";
        }
    }
}
