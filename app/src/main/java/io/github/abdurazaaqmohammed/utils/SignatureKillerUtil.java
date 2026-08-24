package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import android.util.Base64;

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SignatureKillerUtil {

    public static final String KILLER_CLASS = "bin.mt.signature.KillerApplication";
    private static final String INJECTED_ASSET_DIR = "assets/SignatureKiller";
    private static final String[] ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};

    public static File apply(Context context, File inputApk) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(inputApk.getPath(), 0);
        if (info == null || TextUtils.isEmpty(info.packageName))
            throw new IOException("Could not determine package name of " + inputApk.getName());
        final String packageName = info.packageName;

        // Original signature - extracted WITHOUT verification so it also works on
        // merged/antisplit APKs whose v1/v2/v3 blocks are present but invalid.
        List<X509Certificate> certificates = null;
        IOException unverifiedFailure = null;
        try {
            certificates = CertUtil.getCertificatesUnverified(inputApk);
        } catch (IOException e) {
            unverifiedFailure = e;
        }
        if (certificates == null || certificates.isEmpty()) {
            try {
                certificates = CertUtil.getCertificates(inputApk); // apksig fallback (fully valid APKs)
            } catch (Exception e) {
                if (unverifiedFailure != null) e.addSuppressed(unverifiedFailure);
                throw new IOException("Could not extract the original signature from " + inputApk.getName(), e);
            }
        }
        if (certificates == null || certificates.isEmpty())
            throw new IOException("Could not extract the original signature from " + inputApk.getName());

        String signatureB64;
        try {
            signatureB64 = Base64.encodeToString(certificates.get(0).getEncoded(), Base64.NO_WRAP);
        } catch (CertificateEncodingException e) {
            throw new IOException(e);
        }

        File outFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(), FilenameUtils.getBaseName(inputApk.getName()) + "_killed.apk"));

        try (ZipFile zin = new ZipFile(inputApk);
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outFile))) {
            byte[] manifestBytes = null;
            String originalAppClass = null;
            int maxDexIndex = 0;
            List<String> sourceNames = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zin.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                sourceNames.add(name);
                if (name.equals("AndroidManifest.xml")) {
                    AndroidManifestBlock block;
                    try (InputStream is = zin.getInputStream(entry)) {
                        block = AndroidManifestBlock.load(is);
                    }
                    originalAppClass = block.getApplicationClassName();
                    block.setApplicationClassName(KILLER_CLASS);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    block.refreshFull();
                    block.writeBytes(bos);
                    manifestBytes = bos.toByteArray();
                } else if (name.matches("classes\\d*\\.dex")) {
                    String digits = name.substring("classes".length(), name.length() - ".dex".length());
                    int idx = digits.isEmpty() ? 1 : Integer.parseInt(digits);
                    if (idx > maxDexIndex) maxDexIndex = idx;
                }
            }
            if (manifestBytes == null) throw new IOException("No AndroidManifest.xml found");

            for (String name : sourceNames) {
                if (isOldSignature(name)) continue;
                ZipEntry entry = zin.getEntry(name);
                if (name.startsWith(INJECTED_ASSET_DIR + "/")) continue;
                byte[] payload;
                if (name.equals("AndroidManifest.xml")) {
                    payload = manifestBytes;
                } else {
                    try (InputStream is = zin.getInputStream(entry)) {
                        payload = readAll(is);
                    }
                }
                putEntry(zout, name, payload, false);
            }

            // 1. killer dex
            byte[] killerDex = readAll(context.getAssets().open("signature_killer/killer.dex"));
            String killerDexName = "classes" + (maxDexIndex + 1) + ".dex";
            putEntry(zout, killerDexName, killerDex, false);

            // 2. native hook libs (stored/uncompressed so they load fast; extractNativeLibs applies)
            for (String abi : ABIS) {
                InputStream is = openOrNull(context, "signature_killer/lib/" + abi + "/libSignatureKiller.so");
                if (is == null) continue;
                byte[] so = readAll(is);
                putEntry(zout, "lib/" + abi + "/libSignatureKiller.so", so, true);
            }

            // 3. untouched original APK (keeps its original v1/v2/v3 signing blocks)
            putEntryFromStream(zout, INJECTED_ASSET_DIR + "/origin.apk", inputApk);

            // 4. runtime configuration
            Properties cfg = new Properties();
            cfg.setProperty("package", packageName);
            cfg.setProperty("signature", signatureB64);
            if (!TextUtils.isEmpty(originalAppClass) && !KILLER_CLASS.equals(originalAppClass))
                cfg.setProperty("appClass", originalAppClass);
            ByteArrayOutputStream cbos = new ByteArrayOutputStream();
            cfg.store(cbos, "Generated by MP Manager");
            putEntry(zout, INJECTED_ASSET_DIR + "/config.properties", cbos.toByteArray(), false);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw e;
        }
        return outFile;
    }

    private static boolean isOldSignature(String name) {
        String upper = name.toUpperCase(Locale.US);
        return upper.startsWith("META-INF/") &&
                (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                        || upper.endsWith(".SF") || upper.endsWith("MANIFEST.MF"));
    }

    private static void putEntry(ZipOutputStream zout, String name, byte[] data, boolean stored) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (stored) {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
        } else {
            entry.setMethod(ZipEntry.DEFLATED);
        }
        zout.putNextEntry(entry);
        zout.write(data);
        zout.closeEntry();
    }

    private static void putEntryFromStream(ZipOutputStream zout, String name, File source) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        zout.putNextEntry(entry);
        try (InputStream is = new java.io.FileInputStream(source)) {
            byte[] buf = new byte[1024 * 256];
            int len;
            while ((len = is.read(buf)) != -1) zout.write(buf, 0, len);
        }
        zout.closeEntry();
    }

    private static InputStream openOrNull(Context context, String path) {
        try {
            return context.getAssets().open(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024 * 64];
        int len;
        while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
        is.close();
        return bos.toByteArray();
    }
}
