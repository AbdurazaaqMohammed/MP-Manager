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

    public static String getAndroidVersionName(int sdk) {
        return switch (sdk) {
            case 1 -> "Android 1.0 (1)";
            case 2 -> "Android 1.1 (2)";
            case 3 -> "Android 1.5 Cupcake (3)";
            case 4 -> "Android 1.6 Donut (4)";
            case 5 -> "Android 2.0 Eclair (5)";
            case 6 -> "Android 2.0.1 Eclair (6)";
            case 7 -> "Android 2.1 Eclair (7)";
            case 8 -> "Android 2.2 Froyo (8)";
            case 9 -> "Android 2.3 Gingerbread (9)";
            case 10 -> "Android 2.3.3 Gingerbread (10)";
            case 11 -> "Android 3.0 Honeycomb (11)";
            case 12 -> "Android 3.1 Honeycomb (12)";
            case 13 -> "Android 3.2 Honeycomb (13)";
            case 14 -> "Android 4.0 Ice Cream Sandwich (14)";
            case 15 -> "Android 4.0.3 Ice Cream Sandwich (15)";
            case 16 -> "Android 4.1 Jelly Bean (16)";
            case 17 -> "Android 4.2 Jelly Bean (17)";
            case 18 -> "Android 4.3 Jelly Bean (18)";
            case 19 -> "Android 4.4 KitKat (19)";
            case 20 -> "Android 4.4W KitKat Wear (20)";
            case 21 -> "Android 5.0 Lollipop (21)";
            case 22 -> "Android 5.1 Lollipop (22)";
            case 23 -> "Android 6.0 Marshmallow (23)";
            case 24 -> "Android 7.0 Nougat (24)";
            case 25 -> "Android 7.1 Nougat (25)";
            case 26 -> "Android 8.0 Oreo (26)";
            case 27 -> "Android 8.1 Oreo (27)";
            case 28 -> "Android 9 Pie (28)";
            case 29 -> "Android 10 (29)";
            case 30 -> "Android 11 (30)";
            case 31 -> "Android 12 (31)";
            case 32 -> "Android 12L (32)";
            case 33 -> "Android 13 (33)";
            case 34 -> "Android 14 (34)";
            case 35 -> "Android 15 (35)";
            case 36 -> "Android 16 (36)";
            default -> sdk > 36 ? "Android " + (sdk - 19) + " (" + sdk + ")" : String.valueOf(sdk);
        };
    }

    public static CharSequence getPermissions(PackageInfo pi) {
        if (pi == null || pi.requestedPermissions == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            String p = pi.requestedPermissions[i];
            if (p == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(p);
        }
        return sb;
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
