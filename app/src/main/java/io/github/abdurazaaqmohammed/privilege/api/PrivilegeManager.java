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

import io.github.abdurazaaqmohammed.privilege.backend.FallbackBackend;
import io.github.abdurazaaqmohammed.privilege.internal.Registry;
import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;

import android.content.Context;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

import io.github.abdurazaaqmohammed.privilege.api.ShellProgress;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuManager;
import io.github.abdurazaaqmohammed.privilege.internal.PrivilegedInstaller;
import io.github.abdurazaaqmohammed.privilege.internal.StartupPrivileges;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.TransferControl;

import io.github.abdurazaaqmohammed.privilege.internal.LegacyFileOpsSession;
import io.github.abdurazaaqmohammed.privilege.internal.LegacyInstallerSession;
import io.github.abdurazaaqmohammed.privilege.internal.LegacyShellSession;
import io.github.abdurazaaqmohammed.privilege.internal.AccessManager;
import io.github.abdurazaaqmohammed.privilege.internal.NativeRootManager;
import io.github.abdurazaaqmohammed.privilege.internal.NativeToolManager;
import io.github.abdurazaaqmohammed.privilege.internal.RootManager;
import io.github.abdurazaaqmohammed.privilege.internal.RootPermissionHelper;
import io.github.abdurazaaqmohammed.privilege.internal.RootStaging;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuFile;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuFileOps;
import io.github.abdurazaaqmohammed.util.io.DirectoryStats;
import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession.CopyOp;

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
     * Registry-driven backend preference (SHIZUKU > DHIZUKU > ROOT > NONE):
     * the highest-priority authorized backend for the base capability, i.e.
     * the general preference across domains — SHIZUKU and DHIZUKU coexist
     * and are chosen per capability, ROOT is the last-resort fallback.
     * New code should prefer this over {@link #active(Context)} once
     * backends are wired into settings.
     */
    public static PrivilegeBackend preferredBackend(Context context) {
        PrivilegeBackend backend =
                Registry.forCapability(
                        context, Capability.class);
        return backend != null ? backend : FallbackBackend.get();
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

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.internal.AccessManager}, the legacy implementation it delegates to. */
    public static final class Files {

        public static Backend active(Context context) { return PrivilegeManager.active(context); }

        public static boolean fileOpsOn(Context context) { return AccessManager.fileOpsOn(context); }

        public static boolean extractorOn(Context context) { return AccessManager.extractorOn(context); }

        public static boolean needsElevated(Context context, String absPath) { return AccessManager.needsElevated(context, absPath); }

        public static boolean exists(Context context, String absPath) { return AccessManager.exists(context, absPath); }

        public static boolean elevatedExists(Context context, String absPath) { return AccessManager.elevatedExists(context, absPath); }

        public static boolean isDirectory(Context context, String absPath) { return AccessManager.isDirectory(context, absPath); }

        public static boolean isFile(Context context, String absPath) { return AccessManager.isFile(context, absPath); }

        public static File[] listWithStat(Context context, String dirPath) { return AccessManager.listWithStat(context, dirPath); }

        public static long getSize(Context context, String absPath) { return AccessManager.getSize(context, absPath); }

        public static long getMtime(Context context, String absPath) { return AccessManager.getMtime(context, absPath); }

        public static File stageForRead(Context context, String srcAbsPath) throws IOException { return AccessManager.stageForRead(context, srcAbsPath); }

        public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException { AccessManager.writeBack(context, stagedCopy, originalAbsPath); }

        public static void copyFile(Context context, String src, String dest, boolean useElevated) throws IOException { AccessManager.copyFile(context, src, dest, useElevated); }

        public static void copyDir(Context context, String src, String dest, boolean useElevated) throws IOException { AccessManager.copyDir(context, src, dest, useElevated); }

        public static void move(Context context, String src, String dest, boolean useElevated) throws IOException { AccessManager.move(context, src, dest, useElevated); }

        public static void move(Context context, String src, String dest, boolean useElevated,
                            TransferControl control) throws IOException { AccessManager.move(context, src, dest, useElevated, control); }

        public static void moveAll(Context context, List<CopyOp> ops,
                               ShellProgress.Listener progress) throws IOException { AccessManager.moveAll(context, ops, progress); }

        public static void moveAll(Context context, List<CopyOp> ops,
                               ShellProgress.Listener progress,
                               TransferControl control) throws IOException { AccessManager.moveAll(context, ops, progress, control); }

        public static void copyPreserving(Context context, String src, String dest, boolean directory,
                                      boolean useElevated) throws IOException { AccessManager.copyPreserving(context, src, dest, directory, useElevated); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops) throws IOException { AccessManager.copyAllPreserving(context, ops); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops,
                                         ShellProgress.Listener progress) throws IOException { AccessManager.copyAllPreserving(context, ops, progress); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops,
                                         ShellProgress.Listener progress,
                                         TransferControl control) throws IOException { AccessManager.copyAllPreserving(context, ops, progress, control); }

        public static FileUtils.MeasureSet measureSetElevated(Context context, List<String> paths) { return AccessManager.measureSetElevated(context, paths); }

        public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated) throws IOException { return AccessManager.deleteAll(context, paths, useElevated); }

        public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated,
                                         ShellProgress.Listener progress) throws IOException { return AccessManager.deleteAll(context, paths, useElevated, progress); }

        public static void deleteTree(Context context, List<String> paths, boolean useElevated,
                                  ShellProgress.Listener progress) throws IOException { AccessManager.deleteTree(context, paths, useElevated, progress); }

        public static void deleteTree(Context context, List<String> paths, boolean useElevated,
                                  ShellProgress.Listener progress,
                                  TransferControl control) throws IOException { AccessManager.deleteTree(context, paths, useElevated, progress, control); }

        public static int countDirsElevated(Context context, List<String> paths) { return AccessManager.countDirsElevated(context, paths); }

        public static void mkdir(Context context, String path, boolean useElevated) throws IOException { AccessManager.mkdir(context, path, useElevated); }

        public static void delete(Context context, String path, boolean useElevated) throws IOException { AccessManager.delete(context, path, useElevated); }

        public static void rename(Context context, String oldPath, String newPath, boolean useElevated) throws IOException { AccessManager.rename(context, oldPath, newPath, useElevated); }

        public static void touch(Context context, String path, boolean useElevated) throws IOException { AccessManager.touch(context, path, useElevated); }

        public static ShellSession.ShellResult execute(Context context, String command, int timeout) { return AccessManager.execute(context, command, timeout); }

        public static boolean touchMtime(Context context, String path, long millis) { return AccessManager.touchMtime(context, path, millis); }

        public static long dirSize(Context context, String path) { return AccessManager.dirSize(context, path); }

        public static Map<String, Long> dirSizeTree(Context context, String path) { return AccessManager.dirSizeTree(context, path); }

        public static void warmUp(Context context) { AccessManager.warmUp(context); }

        public static void uploadFile(Context context, File localSrc, String dstAbs) throws IOException { AccessManager.uploadFile(context, localSrc, dstAbs); }

        public static void stageFile(Context context, String srcAbs, File dst) throws IOException { AccessManager.stageFile(context, srcAbs, dst); }

        public static void stageTree(Context context, String srcAbs, File dst) throws IOException { AccessManager.stageTree(context, srcAbs, dst); }

        public static boolean fileOpsConfigured(Context context) { return AccessManager.fileOpsConfigured(context); }

        public static boolean shellCopyOn(Context context) { return AccessManager.shellCopyOn(context); }

        public static boolean needsShellCopy(Context context, File src, File dest) { return AccessManager.needsShellCopy(context, src, dest); }

        public static boolean needsShellAccess(Context context, String absPath) { return AccessManager.needsShellAccess(context, absPath); }

        public static ParcelFileDescriptor openReadFd(Context context, String absPath) throws IOException { return AccessManager.openReadFd(context, absPath); }

        public static void preserveTime(Context context, String srcAbs, String dstAbs) { AccessManager.preserveTime(context, srcAbs, dstAbs); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.internal.ShizukuManager}, the legacy implementation it delegates to. */
    public static final class Shizuku {

        public static final int PERMISSION_CODE = ShizukuManager.PERMISSION_CODE;

        public static boolean isRunning() { return ShizukuManager.isRunning(); }

        public static boolean hasPermission() { return ShizukuManager.hasPermission(); }

        public static boolean isDeniedForever() { return ShizukuManager.isDeniedForever(); }

        public static boolean ready() { return ShizukuManager.ready(); }

        public static void requestPermission(Activity activity,
                                         rikka.shizuku.Shizuku.OnRequestPermissionResultListener listener) { ShizukuManager.requestPermission(activity, listener); }

        public static void removePermissionListener(rikka.shizuku.Shizuku.OnRequestPermissionResultListener listener) { ShizukuManager.removePermissionListener(listener); }

        public static void warmUp(Context context) { ShizukuManager.warmUp(context); }

        public static void stopFileService(Context context) { ShizukuManager.stopFileService(context); }

        public static String normalize(String path) { return ShizukuManager.normalize(path); }

        public static boolean isAllowed(String path) { return ShizukuManager.isAllowed(path); }

        public static boolean isTopLevel(String path) { return ShizukuManager.isTopLevel(path); }

        public static boolean exists(Context context, String path) { return ShizukuManager.exists(context, path); }

        public static boolean isDirectory(Context context, String path) { return ShizukuManager.isDirectory(context, path); }

        public static boolean isFile(Context context, String path) { return ShizukuManager.isFile(context, path); }

        public static long getFileSize(Context context, String path) { return ShizukuManager.getFileSize(context, path); }

        public static long getMtime(Context context, String path) { return ShizukuManager.getMtime(context, path); }

        public static boolean touchMtimeShell(String path, long millis) { return ShizukuManager.touchMtimeShell(path, millis); }

        public static long streamFromShell(String srcPath, OutputStream out, long maxBytes) throws IOException { return ShizukuManager.streamFromShell(srcPath, out, maxBytes); }

        public static long streamToShell(File localSrc, String dstPath) throws IOException { return ShizukuManager.streamToShell(localSrc, dstPath); }

        public static long dirSize(Context context, String path) { return ShizukuManager.dirSize(context, path); }

        public static Map<String, Long> dirSizeTree(Context context, String path) { return ShizukuManager.dirSizeTree(context, path); }

        public static File[] listWithStat(Context context, String dirPath) { return ShizukuManager.listWithStat(context, dirPath); }

        public static long streamFrom(Context context, String srcPath, OutputStream out, long maxBytes) throws IOException { return ShizukuManager.streamFrom(context, srcPath, out, maxBytes); }

        public static ParcelFileDescriptor openReadPipe(Context context, String srcPath) throws IOException { return ShizukuManager.openReadPipe(context, srcPath); }

        public static void streamTo(Context context, File localSrc, String dstPath) throws IOException { ShizukuManager.streamTo(context, localSrc, dstPath); }

        public static void copyFile(Context context, String src, String dest) throws IOException { ShizukuManager.copyFile(context, src, dest); }

        public static void copyDir(Context context, String src, String dest) throws IOException { ShizukuManager.copyDir(context, src, dest); }

        public static void moveAll(Context context, List<CopyOp> ops,
                               ShellProgress.Listener progress) throws IOException { ShizukuManager.moveAll(context, ops, progress); }

        public static void moveAll(Context context, List<CopyOp> ops,
                               ShellProgress.Listener progress,
                               TransferControl control) throws IOException { ShizukuManager.moveAll(context, ops, progress, control); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops) throws IOException { ShizukuManager.copyAllPreserving(context, ops); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops,
                                         ShellProgress.Listener progress) throws IOException { ShizukuManager.copyAllPreserving(context, ops, progress); }

        public static void copyAllPreserving(Context context, List<CopyOp> ops,
                                         ShellProgress.Listener progress,
                                         TransferControl control) throws IOException { ShizukuManager.copyAllPreserving(context, ops, progress, control); }

        public static void mkdir(Context context, String path) throws IOException { ShizukuManager.mkdir(context, path); }

        public static void delete(Context context, String path) throws IOException { ShizukuManager.delete(context, path); }

        public static void deleteAll(Context context, List<String> paths) throws IOException { ShizukuManager.deleteAll(context, paths); }

        public static void deleteAll(Context context, List<String> paths,
                                 ShellProgress.Listener progress) throws IOException { ShizukuManager.deleteAll(context, paths, progress); }

        public static void deleteAll(Context context, List<String> paths,
                                 ShellProgress.Listener progress,
                                 TransferControl control) throws IOException { ShizukuManager.deleteAll(context, paths, progress, control); }

        public static void deleteTree(Context context, List<String> paths,
                                  ShellProgress.Listener progress) throws IOException { ShizukuManager.deleteTree(context, paths, progress); }

        public static void deleteTree(Context context, List<String> paths,
                                  ShellProgress.Listener progress,
                                  TransferControl control) throws IOException { ShizukuManager.deleteTree(context, paths, progress, control); }

        public static int countDirs(Context context, List<String> paths) { return ShizukuManager.countDirs(context, paths); }

        public static FileUtils.Measure measure(Context context, List<String> paths) { return ShizukuManager.measure(context, paths); }

        public static FileUtils.MeasureSet measureSet(Context context, List<String> paths) { return ShizukuManager.measureSet(context, paths); }

        public static void rename(Context context, String oldPath, String newPath) throws IOException { ShizukuManager.rename(context, oldPath, newPath); }

        public static void touch(Context context, String path) throws IOException { ShizukuManager.touch(context, path); }

        public static void touchMtime(Context context, String path, long millis) throws IOException { ShizukuManager.touchMtime(context, path, millis); }

        public static int shellExit(Context context, String command, int timeoutSeconds) { return ShizukuManager.shellExit(context, command, timeoutSeconds); }

        public static boolean shellOk(Context context, String command, int timeoutSeconds) { return ShizukuManager.shellOk(context, command, timeoutSeconds); }

        public static boolean shellOkFast(Context context, String command, int timeoutSeconds) { return ShizukuManager.shellOkFast(context, command, timeoutSeconds); }

        public static void init(Context context) { ShizukuFileOps.init(context); }

        public static boolean isAndroidDataPath(String path) { return ShizukuFile.isAndroidDataPath(path); }

        public static boolean isAndroidDataPath(File f) { return ShizukuFile.isAndroidDataPath(f); }

        public static File[] tryList(Context context, File folder) { return ShizukuFile.tryList(context, folder); }

        public static boolean involvesShizukuPath(File src, File dest) { return ShizukuFileOps.involvesShizukuPath(src, dest); }

        public static File materialize(Context context, File file) { return ShizukuFileOps.materialize(context, file); }

        public static File shellCopy(File src, File destFolder, String name) { return ShizukuFileOps.shellCopy(src, destFolder, name); }

        public static boolean shellMove(File src, File destFolder, String name) { return ShizukuFileOps.shellMove(src, destFolder, name); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.internal.PrivilegedInstaller}, the legacy implementation it delegates to. */
    public static final class Installer {

        /** Type alias so callers import only PrivilegeManager. */
        public interface Callback extends PrivilegedInstaller.Callback {
        }

        public static boolean isRootSilentReady(Context context) { return PrivilegedInstaller.isRootSilentReady(context); }

        public static boolean isShizukuSilentReady(Context context) { return PrivilegedInstaller.isShizukuSilentReady(context); }

        public static boolean isPrivilegedSilentReady(Context context) { return PrivilegedInstaller.isPrivilegedSilentReady(context); }

        public static void installAsync(Context context, List<File> apkFiles, Callback cb) { PrivilegedInstaller.installAsync(context, apkFiles, cb); }

        public static String lastTrace() { return PrivilegedInstaller.lastTrace(); }

        public static String installBlocking(Context context, List<File> apkFiles) throws Exception { return PrivilegedInstaller.installBlocking(context, apkFiles); }

        public static boolean hasSignatureMismatch(Context context, List<File> apkFiles) { return PrivilegedInstaller.hasSignatureMismatch(context, apkFiles); }

        public static int collectFlags(Context context) { return PrivilegedInstaller.collectFlags(context); }

        public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags) throws Exception { return PrivilegedInstaller.createSessionWithMetadata(context, installer, targetPackage, totalSize, installerPkg, flags); }

        public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags,
                                                boolean mayClaimInstaller) throws Exception { return PrivilegedInstaller.createSessionWithMetadata(context, installer, targetPackage, totalSize, installerPkg, flags, mayClaimInstaller); }

        public static void applyInstallFlags(PackageInstaller.SessionParams params, int flags) { PrivilegedInstaller.applyInstallFlags(params, flags); }

        public static String readRecordedInstaller(Context context, String packageName) { return PrivilegedInstaller.readRecordedInstaller(context, packageName); }

        public static Intent playStoreIntent(String packageName) { return PrivilegedInstaller.playStoreIntent(packageName); }

    }

    /** 1:1 mirror of {@code io.github.abdurazaaqmohammed.privilege.internal.StartupPrivileges}, the legacy implementation it delegates to. */
    public static final class Startup {

        public static void warmShell(Activity activity) { StartupPrivileges.warmShell(activity); }

        public static void apply(Activity activity, Handler handler, Runnable refreshSidebar) { StartupPrivileges.apply(activity, handler, refreshSidebar); }

    }

    /**
     * 1:1 mirror of {@code RootManager}'s instance API as used by UI/features
     * (S4–S6 migration): same RootManager delegation, reachable without
     * importing {@code privilege.internal}. Shell outcomes use
     * {@link ShellSession.ShellResult}.
     */
    public static final class Root {

        public static boolean isRootAvailable(Context context) { return RootManager.getInstance(context).isRootAvailable(); }

        public static boolean isRootMode(Context context) { return RootManager.getInstance(context).isRootMode(); }

        public static boolean isShizukuMode(Context context) { return RootManager.getInstance(context).isShizukuMode(); }

        public static boolean isRootFileOpsEnabled(Context context) { return RootManager.getInstance(context).isRootFileOpsEnabled(); }

        public static boolean isSilentInstallEnabled(Context context) { return RootManager.getInstance(context).isSilentInstallEnabled(); }

        public static boolean isRootExtractorEnabled(Context context) { return RootManager.getInstance(context).isRootExtractorEnabled(); }

        public static boolean autoEnableRootIfAvailable(Context context) { return RootManager.getInstance(context).autoEnableRootIfAvailable(); }

        public static String suBinary(Context context) { return RootManager.getInstance(context).suBinary(); }

        public static ShellSession.ShellResult execute(Context context, String command) { return RootManager.getInstance(context).execute(command); }

        public static ShellSession.ShellResult execute(Context context, String command, int timeoutSeconds) { return RootManager.getInstance(context).execute(command, timeoutSeconds); }

        public static ShellSession.ShellResult executeFs(Context context, String command, int timeoutSeconds) { return RootManager.getInstance(context).executeFs(command, timeoutSeconds); }

        public static ShellSession.ShellResult executeFs(Context context, String command, int timeoutSeconds, ShellProgress.Listener progress) { return RootManager.getInstance(context).executeFs(command, timeoutSeconds, progress); }

        public static ShellSession.ShellResult executeGlobalNs(Context context, String command, int timeoutSeconds) { return RootManager.getInstance(context).executeGlobalNs(command, timeoutSeconds); }

        public static String getPermissions(Context context, String path) { return RootManager.getInstance(context).getPermissions(path); }

        public static String getOwner(Context context, String path) { return RootManager.getInstance(context).getOwner(path); }

        public static void uninstallSilent(Context context, String packageName) throws IOException { RootManager.getInstance(context).uninstallSilent(packageName); }

        public static void reboot(Context context, String mode) throws IOException { RootManager.getInstance(context).reboot(mode); }

        public static File[] listRootFilesWithStat(Context context, String dirPath) { return RootManager.getInstance(context).listRootFilesWithStat(dirPath); }

        public static void invalidateListCache(Context context) { RootManager.getInstance(context).invalidateListCache(); }

        public static void refreshRootCache(Context context) { RootManager.getInstance(context).refreshRootCache(); }

        public static boolean exists(Context context, String path) { return RootManager.getInstance(context).exists(path); }

        public static void deleteFile(Context context, String path) throws IOException { RootManager.getInstance(context).deleteFile(path); }

        public static void chmodFs(Context context, String path, String mode) throws IOException { RootManager.getInstance(context).chmodFs(path, mode); }

        public static void copyFile(Context context, String src, String dest) throws IOException { RootManager.getInstance(context).copyFile(src, dest); }

        public static void clearAppData(Context context, String packageName) throws IOException { RootManager.getInstance(context).clearAppData(packageName); }

        public static void forceStopApp(Context context, String packageName) throws IOException { RootManager.getInstance(context).forceStopApp(packageName); }

        public static void enableApp(Context context, String packageName) throws IOException { RootManager.getInstance(context).enableApp(packageName); }

        public static void disableApp(Context context, String packageName) throws IOException { RootManager.getInstance(context).disableApp(packageName); }

        public static String getAppUid(Context context, String packageName) { return RootManager.getInstance(context).getAppUid(packageName); }

        public static List<String> getAppApkPaths(Context context, String packageName) { return RootManager.getInstance(context).getAppApkPaths(packageName); }

        public static List<String> getAppDataDirs(Context context, String packageName) { return RootManager.getInstance(context).getAppDataDirs(packageName); }

        public static long streamFromRoot(Context context, String srcPath, OutputStream out, long maxBytes) throws IOException { return RootManager.getInstance(context).streamFromRoot(srcPath, out, maxBytes); }

        public static void streamToRoot(Context context, File localSrc, String dstPath) throws IOException { RootManager.getInstance(context).streamToRoot(localSrc, dstPath); }

    }

    /** 1:1 mirror of {@code RootStaging}: staging decisions and read/write-back of staged copies. */
    public static final class Staging {

        public static boolean needsStaging(Context context, File file) { return RootStaging.needsStaging(context, file); }

        public static boolean needsStaging(Context context, String absPath) { return RootStaging.needsStaging(context, absPath); }

        public static File stageForRead(Context context, String srcAbsPath) throws IOException { return RootStaging.stageForRead(context, srcAbsPath); }

        public static InputStream openInputStream(Context context, File file) throws IOException { return RootStaging.openInputStream(context, file); }

        public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException { RootStaging.writeBack(context, stagedCopy, originalAbsPath); }

        public static boolean needsWriteConfirm(String absPath) { return RootStaging.needsWriteConfirm(absPath); }

    }

    /** 1:1 mirror of {@code RootPermissionHelper}: elevated-capability probes and grant helpers. */
    public static final class Permissions {

        public static boolean hasUsageAccess(Context context) { return RootPermissionHelper.hasUsageAccess(context); }

        public static boolean hasAccessibility(Context context) { return RootPermissionHelper.hasAccessibility(context); }

        public static boolean isRootShellReady(Context context) { return RootPermissionHelper.isRootShellReady(context); }

        public static boolean hasElevatedShell(Context context) { return RootPermissionHelper.hasElevatedShell(context); }

        public static boolean grantWriteSecureViaRoot(Context context) { return RootPermissionHelper.grantWriteSecureViaRoot(context); }

        public static boolean enableAccessibilityForViaElevated(Context context, String serviceClass) { return RootPermissionHelper.enableAccessibilityForViaElevated(context, serviceClass); }

        public static boolean tryAutoGrantInspector(Context context) { return RootPermissionHelper.tryAutoGrantInspector(context); }

        public static boolean ensureUsageAccess(AppCompatActivity activity) { return RootPermissionHelper.ensureUsageAccess(activity); }

    }

    /** 1:1 mirror of the native tool/scan surface: jpegtran JNI and mpio stats. */
    public static final class Native {

        /** Type alias so callers import only PrivilegeManager. */
        public interface ReadyCallback extends NativeToolManager.ReadyCallback {
        }

        public static String deviceAbi() { return NativeToolManager.deviceAbi(); }

        public static void ensureJpegtran(Activity activity, ReadyCallback cb) { NativeToolManager.ensureJpegtran(activity, cb); }

        public static boolean loadJpegtranJni(Context context) { return NativeToolManager.loadJpegtranJni(context); }

        public static void runJpegtranJni(Context context, int op, String src, String dst,
                                          int cropW, int cropH, int cropX, int cropY) throws Exception {
            NativeToolManager.runJpegtranJni(context, op, src, dst, cropW, cropH, cropX, cropY);
        }

        public static String describeFile(File f) { return NativeToolManager.describeFile(f); }

        public static DirectoryStats stats(Context context, String path, TransferControl control, long timeoutMs) { return NativeRootManager.stats(context, path, control, timeoutMs); }

        public static List<DirectoryStats> stats(Context context, List<String> paths, TransferControl control, long timeoutMs) { return NativeRootManager.stats(context, paths, control, timeoutMs); }

        public static Map<String, Long> sizes(Context context, List<String> paths, long timeoutMs) { return NativeRootManager.sizes(context, paths, timeoutMs); }

        public static FileUtils.Measure scan(Context context, String path) { return NativeRootManager.scan(context, path); }

        public static FileUtils.MeasureSet scan(Context context, List<String> paths) { return NativeRootManager.scan(context, paths); }

        public static File jpegtranBinary(Context context) { return NativeToolManager.jpegtranBinary(context); }

        public static String diagnoseExec(Context context) { return NativeToolManager.diagnoseExec(context); }

        public static boolean setExecutableChecked(File f) { return NativeToolManager.setExecutableChecked(f); }

    }
}
