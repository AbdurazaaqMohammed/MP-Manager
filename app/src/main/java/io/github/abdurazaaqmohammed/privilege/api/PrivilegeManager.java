/*
 * Privilege layer — the ONLY entry point visible to UI/features.
 *
 * Nested mirrors (Files / Shizuku / Installer / Startup) are 1:1 delegations
 * to the legacy implementation classes in privilege/legacy/, so re-pointing
 * call sites is a pure prefix swap and behavior is unchanged. M3+ replaces
 * the implementations behind these mirrors with real backends; M5 deletes
 * legacy once nothing outside privilege/ references it.
 *
 * Direct use of legacy classes from UI/features is a review-rule violation.
 */
package io.github.abdurazaaqmohammed.privilege.api;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import rikka.shizuku.Shizuku;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import io.github.abdurazaaqmohammed.privilege.impl.RootManager;
import io.github.abdurazaaqmohammed.privilege.impl.ShellProgress;
import io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager;
import io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller;
import io.github.abdurazaaqmohammed.privilege.impl.StartupPrivileges;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.TransferControl;

import io.github.abdurazaaqmohammed.privilege.internal.LegacyFileOpsSession;
import io.github.abdurazaaqmohammed.privilege.internal.LegacyInstallerSession;
import io.github.abdurazaaqmohammed.privilege.internal.LegacyShellSession;
import io.github.abdurazaaqmohammed.privilege.impl.AccessManager;

public final class PrivilegeManager {

    private PrivilegeManager() {
    }

    /** Which channel is currently active (mirrors AccessManager.active). */
    public enum Backend { NONE, ROOT, SHIZUKU } // DHIZUKU slot lands with settings wiring

    public static Backend active(Context context) {
        AccessManager.Backend legacy = AccessManager.active(context);
        if (legacy == AccessManager.Backend.ROOT) return Backend.ROOT;
        if (legacy == AccessManager.Backend.SHIZUKU) return Backend.SHIZUKU;
        return Backend.NONE;
    }

    /**
     * Registry-driven backend preference (DHIZUKU > SHIZUKU > ROOT > NONE):
     * the highest-priority backend that is present and authorized, or the
     * FallbackBackend when none is. New code should prefer this over
     * {@link #active(Context)} once backends are wired into settings.
     */
    public static io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend preferredBackend(Context context) {
        io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend backend =
                io.github.abdurazaaqmohammed.privilege.internal.Registry.forCapability(
                        context, io.github.abdurazaaqmohammed.privilege.spi.Capability.class);
        return backend != null ? backend : io.github.abdurazaaqmohammed.privilege.backend.FallbackBackend.get();
    }

    /** Sessions (M3 contract, legacy-backed in M2). */
    public static ShellSession shell() {
        return LegacyShellSession.INSTANCE;
    }

    /** Alias for {@link #shell()}; the Shizuku channel is the M2 shell impl. */
    public static ShellSession shizuku() {
        return shell();
    }

    public static FileOpsSession fileOps(Context context) {
        return new LegacyFileOpsSession(context);
    }

    public static InstallerSession installer() {
        return LegacyInstallerSession.INSTANCE;
    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.impl.AccessManager}, the legacy implementation it delegates to. */
    public static final class Files {

        public static Backend active(Context context) { return PrivilegeManager.active(context); }

        public static boolean fileOpsOn(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.fileOpsOn(context); }

        public static boolean extractorOn(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.extractorOn(context); }

        public static boolean needsElevated(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.needsElevated(context, absPath); }

        public static boolean exists(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.exists(context, absPath); }

        public static boolean elevatedExists(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.elevatedExists(context, absPath); }

        public static boolean isDirectory(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.isDirectory(context, absPath); }

        public static boolean isFile(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.isFile(context, absPath); }

        public static File[] listWithStat(Context context, String dirPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.listWithStat(context, dirPath); }

        public static long getSize(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.getSize(context, absPath); }

        public static long getMtime(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.getMtime(context, absPath); }

        public static File stageForRead(Context context, String srcAbsPath) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.stageForRead(context, srcAbsPath); }

        public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.writeBack(context, stagedCopy, originalAbsPath); }

        public static void copyFile(Context context, String src, String dest, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyFile(context, src, dest, useElevated); }

        public static void copyDir(Context context, String src, String dest, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyDir(context, src, dest, useElevated); }

        public static void move(Context context, String src, String dest, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.move(context, src, dest, useElevated); }

        public static void move(Context context, String src, String dest, boolean useElevated,
                            TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.move(context, src, dest, useElevated, control); }

        public static void moveAll(Context context, List<RootManager.CopyOp> ops,
                               ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.moveAll(context, ops, progress); }

        public static void moveAll(Context context, List<RootManager.CopyOp> ops,
                               ShellProgress.Listener progress,
                               TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.moveAll(context, ops, progress, control); }

        public static void copyPreserving(Context context, String src, String dest, boolean directory,
                                      boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyPreserving(context, src, dest, directory, useElevated); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyAllPreserving(context, ops); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops,
                                         ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyAllPreserving(context, ops, progress); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops,
                                         ShellProgress.Listener progress,
                                         TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.copyAllPreserving(context, ops, progress, control); }

        public static FileUtils.MeasureSet measureSetElevated(Context context, List<String> paths) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.measureSetElevated(context, paths); }

        public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.deleteAll(context, paths, useElevated); }

        public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated,
                                         ShellProgress.Listener progress) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.deleteAll(context, paths, useElevated, progress); }

        public static void deleteTree(Context context, List<String> paths, boolean useElevated,
                                  ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.deleteTree(context, paths, useElevated, progress); }

        public static void deleteTree(Context context, List<String> paths, boolean useElevated,
                                  ShellProgress.Listener progress,
                                  TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.deleteTree(context, paths, useElevated, progress, control); }

        public static int countDirsElevated(Context context, List<String> paths) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.countDirsElevated(context, paths); }

        public static void mkdir(Context context, String path, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.mkdir(context, path, useElevated); }

        public static void delete(Context context, String path, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.delete(context, path, useElevated); }

        public static void rename(Context context, String oldPath, String newPath, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.rename(context, oldPath, newPath, useElevated); }

        public static void touch(Context context, String path, boolean useElevated) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.touch(context, path, useElevated); }

        public static RootManager.ShellResult execute(Context context, String command, int timeout) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.execute(context, command, timeout); }

        public static boolean touchMtime(Context context, String path, long millis) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.touchMtime(context, path, millis); }

        public static long dirSize(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.dirSize(context, path); }

        public static java.util.Map<String, Long> dirSizeTree(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.dirSizeTree(context, path); }

        public static void warmUp(Context context) { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.warmUp(context); }

        public static void uploadFile(Context context, File localSrc, String dstAbs) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.uploadFile(context, localSrc, dstAbs); }

        public static void stageFile(Context context, String srcAbs, File dst) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.stageFile(context, srcAbs, dst); }

        public static void stageTree(Context context, String srcAbs, File dst) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.stageTree(context, srcAbs, dst); }

        public static boolean fileOpsConfigured(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.fileOpsConfigured(context); }

        public static boolean shellCopyOn(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.shellCopyOn(context); }

        public static boolean needsShellCopy(Context context, File src, File dest) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.needsShellCopy(context, src, dest); }

        public static boolean needsShellAccess(Context context, String absPath) { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.needsShellAccess(context, absPath); }

        public static ParcelFileDescriptor openReadFd(Context context, String absPath) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.AccessManager.openReadFd(context, absPath); }

        public static void preserveTime(Context context, String srcAbs, String dstAbs) { io.github.abdurazaaqmohammed.privilege.impl.AccessManager.preserveTime(context, srcAbs, dstAbs); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager}, the legacy implementation it delegates to. */
    public static final class Shizuku {

        public static final int PERMISSION_CODE = io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.PERMISSION_CODE;

        public static boolean isRunning() { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isRunning(); }

        public static boolean hasPermission() { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.hasPermission(); }

        public static boolean isDeniedForever() { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isDeniedForever(); }

        public static boolean ready() { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.ready(); }

        public static void requestPermission(Activity activity,
                                         rikka.shizuku.Shizuku.OnRequestPermissionResultListener listener) { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.requestPermission(activity, listener); }

        public static void removePermissionListener(rikka.shizuku.Shizuku.OnRequestPermissionResultListener listener) { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.removePermissionListener(listener); }

        public static void warmUp(Context context) { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.warmUp(context); }

        public static String normalize(String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.normalize(path); }

        public static boolean isAllowed(String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isAllowed(path); }

        public static boolean isTopLevel(String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isTopLevel(path); }

        public static boolean exists(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.exists(context, path); }

        public static boolean isDirectory(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isDirectory(context, path); }

        public static boolean isFile(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.isFile(context, path); }

        public static long getFileSize(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.getFileSize(context, path); }

        public static long getMtime(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.getMtime(context, path); }

        public static boolean touchMtimeShell(String path, long millis) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.touchMtimeShell(path, millis); }

        public static long streamFromShell(String srcPath, OutputStream out, long maxBytes) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.streamFromShell(srcPath, out, maxBytes); }

        public static long streamToShell(File localSrc, String dstPath) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.streamToShell(localSrc, dstPath); }

        public static long dirSize(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.dirSize(context, path); }

        public static java.util.Map<String, Long> dirSizeTree(Context context, String path) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.dirSizeTree(context, path); }

        public static File[] listWithStat(Context context, String dirPath) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.listWithStat(context, dirPath); }

        public static long streamFrom(Context context, String srcPath, OutputStream out, long maxBytes) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.streamFrom(context, srcPath, out, maxBytes); }

        public static ParcelFileDescriptor openReadPipe(Context context, String srcPath) throws IOException { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.openReadPipe(context, srcPath); }

        public static void streamTo(Context context, File localSrc, String dstPath) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.streamTo(context, localSrc, dstPath); }

        public static void copyFile(Context context, String src, String dest) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.copyFile(context, src, dest); }

        public static void copyDir(Context context, String src, String dest) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.copyDir(context, src, dest); }

        public static void moveAll(Context context, List<RootManager.CopyOp> ops,
                               ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.moveAll(context, ops, progress); }

        public static void moveAll(Context context, List<RootManager.CopyOp> ops,
                               ShellProgress.Listener progress,
                               TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.moveAll(context, ops, progress, control); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.copyAllPreserving(context, ops); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops,
                                         ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.copyAllPreserving(context, ops, progress); }

        public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops,
                                         ShellProgress.Listener progress,
                                         TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.copyAllPreserving(context, ops, progress, control); }

        public static void mkdir(Context context, String path) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.mkdir(context, path); }

        public static void delete(Context context, String path) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.delete(context, path); }

        public static void deleteAll(Context context, List<String> paths) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.deleteAll(context, paths); }

        public static void deleteAll(Context context, List<String> paths,
                                 ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.deleteAll(context, paths, progress); }

        public static void deleteAll(Context context, List<String> paths,
                                 ShellProgress.Listener progress,
                                 TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.deleteAll(context, paths, progress, control); }

        public static void deleteTree(Context context, List<String> paths,
                                  ShellProgress.Listener progress) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.deleteTree(context, paths, progress); }

        public static void deleteTree(Context context, List<String> paths,
                                  ShellProgress.Listener progress,
                                  TransferControl control) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.deleteTree(context, paths, progress, control); }

        public static int countDirs(Context context, List<String> paths) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.countDirs(context, paths); }

        public static FileUtils.Measure measure(Context context, List<String> paths) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.measure(context, paths); }

        public static FileUtils.MeasureSet measureSet(Context context, List<String> paths) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.measureSet(context, paths); }

        public static void rename(Context context, String oldPath, String newPath) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.rename(context, oldPath, newPath); }

        public static void touch(Context context, String path) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.touch(context, path); }

        public static void touchMtime(Context context, String path, long millis) throws IOException { io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.touchMtime(context, path, millis); }

        public static int shellExit(Context context, String command, int timeoutSeconds) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.shellExit(context, command, timeoutSeconds); }

        public static boolean shellOk(Context context, String command, int timeoutSeconds) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.shellOk(context, command, timeoutSeconds); }

        public static boolean shellOkFast(Context context, String command, int timeoutSeconds) { return io.github.abdurazaaqmohammed.privilege.impl.ShizukuManager.shellOkFast(context, command, timeoutSeconds); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller}, the legacy implementation it delegates to. */
    public static final class Installer {

        /** Type alias so callers import only PrivilegeManager. */
        public interface Callback extends io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.Callback {
        }

        public static boolean isRootSilentReady(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.isRootSilentReady(context); }

        public static boolean isShizukuSilentReady(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.isShizukuSilentReady(context); }

        public static boolean isPrivilegedSilentReady(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.isPrivilegedSilentReady(context); }

        public static void installAsync(Context context, List<File> apkFiles, Callback cb) { io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.installAsync(context, apkFiles, cb); }

        public static String lastTrace() { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.lastTrace(); }

        public static String installBlocking(Context context, List<File> apkFiles) throws Exception { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.installBlocking(context, apkFiles); }

        public static boolean hasSignatureMismatch(Context context, List<File> apkFiles) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.hasSignatureMismatch(context, apkFiles); }

        public static int collectFlags(Context context) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.collectFlags(context); }

        public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags) throws Exception { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.createSessionWithMetadata(context, installer, targetPackage, totalSize, installerPkg, flags); }

        public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags,
                                                boolean mayClaimInstaller) throws Exception { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.createSessionWithMetadata(context, installer, targetPackage, totalSize, installerPkg, flags, mayClaimInstaller); }

        public static void applyInstallFlags(PackageInstaller.SessionParams params, int flags) { io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.applyInstallFlags(params, flags); }

        public static String readRecordedInstaller(Context context, String packageName) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.readRecordedInstaller(context, packageName); }

        public static Intent playStoreIntent(String packageName) { return io.github.abdurazaaqmohammed.privilege.impl.PrivilegedInstaller.playStoreIntent(packageName); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.impl.StartupPrivileges}, the legacy implementation it delegates to. */
    public static final class Startup {

        public static void warmShell(Activity activity) { io.github.abdurazaaqmohammed.privilege.impl.StartupPrivileges.warmShell(activity); }

        public static void apply(Activity activity, Handler handler, Runnable refreshSidebar) { io.github.abdurazaaqmohammed.privilege.impl.StartupPrivileges.apply(activity, handler, refreshSidebar); }

    }
}
