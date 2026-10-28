package io.github.abdurazaaqmohammed.features.apk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ApkInfoUtil;
import io.github.abdurazaaqmohammed.utils.ApkSignatureInfo;
import io.github.abdurazaaqmohammed.utils.CertUtil;

/**
 * Compares an APK on disk with the version already installed on the device, the
 * way InstallerX Revived's "app info" does: is the candidate newer, older or the
 * same build; is it signed by the same key or by someone else; are the two
 * archives byte-for-byte the same; and does the signer's certificate look like
 * the same developer.
 *
 * <p>The interesting case for a file manager is not "will this install" (the
 * system answers that) but "is this actually the app I have, or has someone
 * swapped in a look-alike". A different signer on a downgrade is the classic
 * way to get code executed as a package you already trust.
 */
public final class ApkInstallAnalysis {

    public static final int VERSION_NEWER = 0;
    public static final int VERSION_OLDER = 1;
    public static final int VERSION_SAME = 2;
    public static final int VERSION_DIFFERENT = 3;

    public static final int SIGNER_SAME = 0;
    public static final int SIGNER_DIFFERENT = 1;
    public static final int SIGNER_UNKNOWN = 2;

    public final boolean installed;
    public final String packageName;
    public final String versionName;
    public final long versionCode;
    public final String installedVersionName;
    public final long installedVersionCode;
    public final int versionRelation;
    public final int signerRelation;
    public final boolean sameBytes;
    public final String checksum;
    public final String installedChecksum;
    public final String signerSubject;
    public final String installedSignerSubject;
    public final String signerCertSha256;
    public final String installedSignerCertSha256;

    private ApkInstallAnalysis(boolean installed, String packageName, String versionName, long versionCode,
                              String installedVersionName, long installedVersionCode, int versionRelation,
                              int signerRelation, boolean sameBytes, String checksum, String installedChecksum,
                              String signerSubject, String installedSignerSubject,
                              String signerCertSha256, String installedSignerCertSha256) {
        this.installed = installed;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.installedVersionName = installedVersionName;
        this.installedVersionCode = installedVersionCode;
        this.versionRelation = versionRelation;
        this.signerRelation = signerRelation;
        this.sameBytes = sameBytes;
        this.checksum = checksum;
        this.installedChecksum = installedChecksum;
        this.signerSubject = signerSubject;
        this.installedSignerSubject = installedSignerSubject;
        this.signerCertSha256 = signerCertSha256;
        this.installedSignerCertSha256 = installedSignerCertSha256;
    }

    public boolean isDifferentSigner() {
        return signerRelation == SIGNER_DIFFERENT;
    }

    public boolean isDowngrade() {
        return versionRelation == VERSION_OLDER;
    }

    /** True when the two APKs are the same file with the same signer. */
    public boolean isIdentical() {
        return sameBytes && signerRelation == SIGNER_SAME;
    }

    /**
     * A same-named package signed by a different key cannot be an update; it is
     * either a repackaged app or an outright impostor.
     */
    public boolean isSuspicious() {
        return installed && signerRelation == SIGNER_DIFFERENT;
    }

    /** One-line summary for the APK info dialog's "Installed" row. */
    public String summary(Context context) {
        if (!installed) return context.getString(R.string.not_installed);
        StringBuilder sb = new StringBuilder();
        String installedLabel = describe(installedVersionName, installedVersionCode);
        switch (versionRelation) {
            case VERSION_NEWER:
                sb.append(context.getString(R.string.apk_compare_newer, installedLabel));
                break;
            case VERSION_OLDER:
                sb.append(context.getString(R.string.apk_compare_older, installedLabel));
                break;
            case VERSION_SAME:
                sb.append(context.getString(R.string.apk_compare_same, installedLabel));
                break;
            default:
                sb.append(installedLabel);
                break;
        }
        switch (signerRelation) {
            case SIGNER_SAME:
                sb.append(" · ").append(context.getString(R.string.apk_compare_same_signer));
                break;
            case SIGNER_DIFFERENT:
                sb.append(" · ").append(context.getString(R.string.apk_compare_other_signer));
                break;
            default:
                break;
        }
        if (sameBytes) sb.append(" · ").append(context.getString(R.string.apk_compare_same_file));
        return sb.toString();
    }

    /** Multi-line detail for the pre-install confirmation. */
    public String details(Context context) {
        StringBuilder sb = new StringBuilder();
        if (!installed) {
            sb.append(context.getString(R.string.apk_compare_new_install));
            if (!TextUtils_isEmpty(checksum)) {
                sb.append('\n').append(context.getString(R.string.apk_compare_sha256, shortHash(checksum)));
            }
            return sb.toString();
        }
        sb.append(summary(context));
        if (!TextUtils_isEmpty(installedChecksum) && !checksum.isEmpty()) {
            sb.append('\n').append(context.getString(R.string.apk_compare_sha256,
                    shortHash(installedChecksum) + " \u2192 " + shortHash(checksum)));
        }
        if (!TextUtils_isEmpty(signerSubject)) {
            sb.append('\n').append(context.getString(R.string.apk_compare_signer_subject, signerSubject));
        }
        if (!TextUtils_isEmpty(installedSignerSubject) && !installedSignerSubject.equals(signerSubject)) {
            sb.append('\n').append(context.getString(R.string.apk_compare_installed_signer, installedSignerSubject));
        }
        return sb.toString();
    }

    private static String describe(String versionName, long versionCode) {
        String name = TextUtils_isEmpty(versionName) ? "" : versionName;
        return name.isEmpty() ? String.valueOf(versionCode) : name + " (" + versionCode + ")";
    }

    public static ApkInstallAnalysis analyze(Context context, File apk) {
        String pkg = null;
        String versionName = null;
        long versionCode = 0;
        try {
            PackageInfo archive = context.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (archive != null) {
                pkg = archive.packageName;
                versionName = archive.versionName;
                versionCode = ApkInfoUtil.getVersionCode(archive);
            }
        } catch (Throwable ignored) {
        }

        String checksum = sha256(apk);

        ApkSignatureInfo archiveSig = null;
        try {
            archiveSig = ApkSignatureInfo.analyze(apk);
        } catch (Throwable ignored) {
        }
        String archiveCert = archiveSig == null ? null : sha256(archiveSig.getPrimaryCertificate());
        String archiveSubject = archiveSig == null ? null : subjectOf(archiveSig.getPrimaryCertificate());

        if (pkg == null) {
            return new ApkInstallAnalysis(false, null, versionName, versionCode, null, 0,
                    VERSION_DIFFERENT, SIGNER_UNKNOWN, false, checksum, null, archiveSubject, null,
                    archiveCert, null);
        }

        PackageInfo installedInfo;
        try {
            installedInfo = context.getPackageManager().getPackageInfo(pkg, signingFlags());
        } catch (Throwable e) {
            return new ApkInstallAnalysis(false, pkg, versionName, versionCode, null, 0,
                    VERSION_DIFFERENT, SIGNER_UNKNOWN, false, checksum, null, archiveSubject, null,
                    archiveCert, null);
        }
        if (installedInfo == null) {
            return new ApkInstallAnalysis(false, pkg, versionName, versionCode, null, 0,
                    VERSION_DIFFERENT, SIGNER_UNKNOWN, false, checksum, null, archiveSubject, null,
                    archiveCert, null);
        }

        long installedCode = ApkInfoUtil.getVersionCode(installedInfo);
        String installedName = installedInfo.versionName;
        int relation = installedCode == versionCode && eq(installedName, versionName)
                ? VERSION_SAME
                : installedCode > versionCode ? VERSION_OLDER
                : installedCode < versionCode ? VERSION_NEWER : VERSION_DIFFERENT;

        String installedCert = installedSignerSha256(installedInfo);
        int signerRelation;
        if (TextUtils_isEmpty(installedCert) || TextUtils_isEmpty(archiveCert)) {
            signerRelation = SIGNER_UNKNOWN;
        } else {
            signerRelation = installedCert.equals(archiveCert) ? SIGNER_SAME : SIGNER_DIFFERENT;
        }

        String installedSource = installedInfo.applicationInfo == null ? null : installedInfo.applicationInfo.sourceDir;
        String installedChecksum = TextUtils_isEmpty(installedSource) ? null : sha256(new File(installedSource));
        boolean sameBytes = installedChecksum != null && installedChecksum.equals(checksum);

        String installedSubject = subjectOf(installedSignerCert(installedInfo));

        return new ApkInstallAnalysis(true, pkg, versionName, versionCode, installedName, installedCode,
                relation, signerRelation, sameBytes, checksum, installedChecksum, archiveSubject,
                installedSubject, archiveCert, installedCert);
    }

    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    private static String installedSignerSha256(PackageInfo info) {
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo si = info.signingInfo;
                if (si == null) return null;
                signatures = si.hasMultipleSigners()
                        ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else {
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return null;
            return CertUtil.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static String subjectOf(@Nullable X509Certificate cert) {
        if (cert == null) return null;
        try {
            String dn = CertUtil.formatPrincipal(cert.getSubjectX500Principal());
            if (dn != null && dn.length() > 120) dn = dn.substring(0, 120) + "…";
            return dn;
        } catch (Throwable t) {
            return null;
        }
    }

    private static X509Certificate installedSignerCert(PackageInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo si = info.signingInfo;
                if (si == null) return null;
                Signature[] signatures = si.hasMultipleSigners()
                        ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
                return toX509(signatures);
            }
            return toX509(info.signatures);
        } catch (Throwable t) {
            return null;
        }
    }

    /** SigningInfo hands back bare signatures; rebuild the certificate for the DN. */
    @Nullable
    private static X509Certificate toX509(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return null;
        try (InputStream in = new ByteArrayInputStream(signatures[0].toByteArray())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sha256(X509Certificate cert) {
        if (cert == null) return null;
        try {
            return CertUtil.getSha256(cert);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String sha256(File file) {
        if (file == null || !file.isFile()) return "";
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return CertUtil.formatHex(md.digest());
        } catch (Throwable t) {
            return "";
        }
    }

    private static String shortHash(String full) {
        if (full == null) return "";
        String flat = full.replace(":", "");
        return flat.length() <= 16 ? flat : flat.substring(0, 16);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean TextUtils_isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** Signer fingerprints of every archive in the list, for a bulk comparison. */
    public static List<String> signerFingerprints(List<File> apks) {
        List<String> out = new ArrayList<>();
        if (apks == null) return out;
        for (File f : apks) {
            try {
                ApkSignatureInfo info = ApkSignatureInfo.analyze(f);
                out.add(info == null ? null : sha256(info.getPrimaryCertificate()));
            } catch (Throwable t) {
                out.add(null);
            }
        }
        return out;
    }
}