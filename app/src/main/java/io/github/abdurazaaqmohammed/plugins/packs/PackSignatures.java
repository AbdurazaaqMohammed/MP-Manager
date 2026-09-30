package io.github.abdurazaaqmohammed.plugins.packs;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Signature checks for the in-process pack path.
 *
 * <p>Rule: code loaded with DexClassLoader runs with every host permission
 * (root, Shizuku, all files, network), so it must be first-party — i.e.
 * signed with the same certificate as the host app itself. Anything else is
 * refused and must ship as an external (out-of-process) plugin instead.
 */
public final class PackSignatures {

    private static String sRefusal;

    private PackSignatures() {
    }

    /** True for developer builds; relaxes install gates for local testing. */
    public static boolean isDebuggable(Context context) {
        try {
            return (context.getApplicationInfo().flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Records why the last loadPack refused; consumed by the installer. */
    static synchronized void refuse(String reason) {
        sRefusal = reason;
    }

    /** Takes and clears the pending refusal, or null when the load passed. */
    public static synchronized String takeRefusal() {
        String r = sRefusal;
        sRefusal = null;
        return r;
    }

    /**
     * Whether the APK file is signed with one of the host app's own
     * certificates. False on any verification failure (fail closed).
     */
    public static boolean isSameSignerAsHost(Context context, File apk) {
        try {
            List<String> pack = apkCertDigests(context, apk);
            List<String> host = hostCertDigests(context);
            if (pack.isEmpty() || host.isEmpty()) return false;
            for (String p : pack) {
                if (host.contains(p)) return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** SHA-256 digests (lowercase hex) of an APK file's signing certs. */
    public static List<String> apkCertDigests(Context context, File apk) {
        List<String> out = new ArrayList<>();
        try {
            if (apk == null || !apk.exists()) return out;
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (info == null || info.signingInfo == null) return out;
                SigningInfo si = info.signingInfo;
                Signature[] sigs = si.hasMultipleSigners()
                        ? si.getApkContentsSigners()
                        : si.getSigningCertificateHistory();
                return digestsOf(sigs);
            } else {
                PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        PackageManager.GET_SIGNATURES);
                if (info == null || info.signatures == null) return out;
                return digestsOf(info.signatures);
            }
        } catch (Exception ignored) {
            return out;
        }
    }

    /** SHA-256 digests (lowercase hex) of the host app's own signing certs. */
    public static List<String> hostCertDigests(Context context) {
        List<String> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(pkg,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (info == null || info.signingInfo == null) return out;
                SigningInfo si = info.signingInfo;
                Signature[] sigs = si.hasMultipleSigners()
                        ? si.getApkContentsSigners()
                        : si.getSigningCertificateHistory();
                return digestsOf(sigs);
            } else {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (info == null || info.signatures == null) return out;
                return digestsOf(info.signatures);
            }
        } catch (Exception ignored) {
            return out;
        }
    }

    private static List<String> digestsOf(Signature[] sigs) {
        List<String> out = new ArrayList<>();
        try {
            if (sigs == null) return out;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) {
                if (s == null) continue;
                byte[] raw = s.toByteArray();
                md.reset();
                byte[] hash = md.digest(raw);
                StringBuilder sb = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    String h = Integer.toHexString(0xFF & b);
                    if (h.length() == 1) sb.append('0');
                    sb.append(h);
                }
                String hex = sb.toString();
                if (!out.contains(hex)) out.add(hex);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
