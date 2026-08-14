package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.util.List;

public class ApkInfoUtil {

    public static PackageInfo getPackageInfo(Context context, File apkFile) {
        PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apkFile.getPath(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
        if (pi == null) return null;
        if (pi.applicationInfo == null) pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.sourceDir = apkFile.getPath();
        pi.applicationInfo.publicSourceDir = apkFile.getPath();
        return pi;
    }

    public static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isDowngrade(Context context, PackageInfo archive) {
        try {
            PackageInfo installed = context.getPackageManager().getPackageInfo(archive.packageName, 0);
            return getVersionCode(installed) > getVersionCode(archive);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String getInstalledVersion(Context context, String packageName) {
        try {
            PackageInfo installed = context.getPackageManager().getPackageInfo(packageName, 0);
            return (installed.versionName == null ? "" : installed.versionName) + " (" + getVersionCode(installed) + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static long getVersionCode(PackageInfo pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return pi.getLongVersionCode();
        return pi.versionCode;
    }

    public static int getMinSdk(PackageInfo pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && pi.applicationInfo != null)
            return pi.applicationInfo.minSdkVersion;
        return -1;
    }

    public static String getPermissions(PackageInfo pi) {
        if (pi == null || pi.requestedPermissions == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            String p = pi.requestedPermissions[i];
            if (p == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(p);
        }
        return sb.toString();
    }

    public static long getTotalSize(File apk) {
        long total = 0;
        try (ZipFile zf = new ZipFile(apk)) {
            List<FileHeader> headers = zf.getFileHeaders();
            if (headers != null) for (FileHeader h : headers) total += h.getUncompressedSize();
        } catch (Exception ignored) {}
        return total;
    }

    public static int getEntryCount(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            List<FileHeader> headers = zf.getFileHeaders();
            return headers == null ? 0 : headers.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
