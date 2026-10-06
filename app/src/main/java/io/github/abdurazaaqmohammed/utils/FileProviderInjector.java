package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.apk.APKLogger;
import com.reandroid.app.AndroidManifest;
import com.reandroid.xml.XMLPath;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MT-style "inject documents provider": adds the provider + wake-up activity
 * manifest entries for {@code bin.mt.file.content.MTDataFilesProvider} and
 * appends the prebuilt provider dex ({@code assets/file_provider/provider.dex})
 * as a new {@code classesN.dex}, so the target app's data directories become
 * browsable over SAF after reinstallation.
 *
 * <p>Returns {@code null} when the APK already contains the provider.</p>
 */
public final class FileProviderInjector {

    public static final String PROVIDER_CLASS = "bin.mt.file.content.MTDataFilesProvider";
    public static final String WAKEUP_CLASS = "bin.mt.file.content.MTDataFilesWakeUpActivity";
    public static final String AUTHORITY_SUFFIX = ".MTDataFilesProvider";
    public static final String WAKEUP_AFFINITY_SUFFIX = ".MTDataFilesWakeUp";
    private static final String DOCUMENTS_ACTION = "android.content.action.DOCUMENTS_PROVIDER";
    private static final String MANAGE_DOCUMENTS = "android.permission.MANAGE_DOCUMENTS";
    private static final String PROVIDER_DEX_ASSET = "file_provider/provider.dex";
    private static final String OUTPUT_SUFFIX = "_dp";

    // Framework attr ids for attributes missing from REAndroid's AndroidManifest
    // constants. Sourced from android.R$attr (android.jar) and cross-checked
    // against framework-res (res/raw/android_31.apk resources.arsc): all values
    // match, and name/exported/authorities also match REAndroid's own constants.
    // Required because REAndroid's autoSetNamespace() strips the android: prefix
    // from any attribute whose nameId == 0 during refreshFull().
    private static final int ID_permission = 0x01010006;
    private static final int ID_grantUriPermissions = 0x0101001B;
    private static final int ID_taskAffinity = 0x01010012;
    private static final int ID_excludeFromRecents = 0x01010017;
    private static final int ID_noHistory = 0x0101022D;

    private FileProviderInjector() {
    }

    /**
     * @return the rewritten APK ({@code <name>_dp.apk}), or {@code null} if the
     * provider is already present.
     */
    public static File inject(Context context, File inputApk, APKLogger logger) throws IOException {
        byte[] manifestBytes = null;
        int maxDexIndex = 0;
        Set<String> oldSignatures = new HashSet<>();
        try (ZipFile zip = new ZipFile(inputApk)) {
            List<FileHeader> headers = zip.getFileHeaders();
            for (FileHeader header : headers) {
                String name = header.getFileName();
                if (header.isDirectory()) continue;
                if ("AndroidManifest.xml".equals(name)) {
                    AndroidManifestBlock block;
                    try (InputStream is = zip.getInputStream(header)) {
                        block = AndroidManifestBlock.load(is);
                    }
                    String packageName = block.getPackageName();
                    if (packageName == null || packageName.isEmpty()) {
                        throw new IOException("Package name not found in manifest");
                    }
                    String authority = packageName + AUTHORITY_SUFFIX;
                    if (findProvider(block, authority) != null) {
                        if (logger != null) logger.logMessage("File provider already injected");
                        return null;
                    }
                    addProviderElements(block, packageName, authority);
                    block.refreshFull();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    block.writeBytes(bos);
                    manifestBytes = bos.toByteArray();
                } else if (name.matches("classes\\d*\\.dex")) {
                    String digits = name.substring("classes".length(), name.length() - ".dex".length());
                    int idx = digits.isEmpty() ? 1 : Integer.parseInt(digits);
                    if (idx > maxDexIndex) maxDexIndex = idx;
                }
                if (isOldSignature(name)) oldSignatures.add(name);
            }
        }
        if (manifestBytes == null) {
            throw new IOException("No AndroidManifest.xml found");
        }

        byte[] providerDex;
        try (InputStream is = context.getAssets().open(PROVIDER_DEX_ASSET)) {
            providerDex = readAll(is);
        }

        File outFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                FilenameUtils.getBaseName(inputApk.getName()) + OUTPUT_SUFFIX + ".apk"));
        File tempDir = new File(context.getCacheDir(), "fileprov_" + System.currentTimeMillis());
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Cannot create temp directory: " + tempDir);
        }
        try {
            Map<String, File> replacements = new LinkedHashMap<>();
            replacements.put("AndroidManifest.xml", writeTemp(tempDir, "manifest.xml", manifestBytes));
            Map<String, File> additions = new LinkedHashMap<>();
            additions.put("classes" + (maxDexIndex + 1) + ".dex",
                    writeTemp(tempDir, "provider.dex", providerDex));
            ApkZipAlignUtil.rebuildApk(inputApk, outFile, replacements, null, additions, oldSignatures);
            if (logger != null) {
                logger.logMessage("Injected " + PROVIDER_CLASS + " as " + outFile.getName());
            }
            return outFile;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        } finally {
            deleteDir(tempDir);
        }
    }

    private static ResXmlElement findProvider(AndroidManifestBlock block, String authority) {
        ResXmlAttribute attribute = AndroidManifest.PATH_APPLICATION
                .element(AndroidManifest.TAG_provider)
                .attribute(AndroidManifest.NAME_authorities)
                .value(authority)
                .findFirst(block);
        return attribute != null ? attribute.getParentElement() : null;
    }

    private static void addProviderElements(AndroidManifestBlock block, String packageName,
                                            String authority) {
        ResXmlElement application = block.getOrCreateApplicationElement();

        ResXmlElement provider = application.newElement(AndroidManifest.TAG_provider);
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name)
                .setValueAsString(PROVIDER_CLASS);
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_authorities, AndroidManifest.ID_authorities)
                .setValueAsString(authority);
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_exported, AndroidManifest.ID_exported)
                .setValueAsBoolean(true);
        provider.getOrCreateAndroidAttribute("permission", ID_permission)
                .setValueAsString(MANAGE_DOCUMENTS);
        provider.getOrCreateAndroidAttribute("grantUriPermissions", ID_grantUriPermissions)
                .setValueAsBoolean(true);
        ResXmlElement intentFilter = provider.newElement(AndroidManifest.TAG_intent_filter);
        intentFilter.newElement(AndroidManifest.TAG_action)
                .getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name)
                .setValueAsString(DOCUMENTS_ACTION);

        ResXmlElement activity = application.newElement(AndroidManifest.TAG_activity);
        activity.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name)
                .setValueAsString(WAKEUP_CLASS);
        activity.getOrCreateAndroidAttribute(AndroidManifest.NAME_exported, AndroidManifest.ID_exported)
                .setValueAsBoolean(true);
        activity.getOrCreateAndroidAttribute("taskAffinity", ID_taskAffinity)
                .setValueAsString(packageName + WAKEUP_AFFINITY_SUFFIX);
        activity.getOrCreateAndroidAttribute("excludeFromRecents", ID_excludeFromRecents)
                .setValueAsBoolean(true);
        activity.getOrCreateAndroidAttribute("noHistory", ID_noHistory).setValueAsBoolean(true);
    }

    private static boolean isOldSignature(String name) {
        String upper = name.toUpperCase(Locale.US);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                || upper.endsWith(".SF") || upper.endsWith("MANIFEST.MF"));
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536];
        int count;
        while ((count = is.read(buffer)) != -1) {
            bos.write(buffer, 0, count);
        }
        return bos.toByteArray();
    }

    private static File writeTemp(File dir, String name, byte[] data) throws IOException {
        File temp = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write(data);
        }
        return temp;
    }

    private static void deleteDir(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }
}
