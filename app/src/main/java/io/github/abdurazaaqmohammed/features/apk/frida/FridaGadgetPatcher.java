package io.github.abdurazaaqmohammed.features.apk.frida;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import org.apache.commons.io.FilenameUtils;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import com.reandroid.apk.APKLogger;

/**
 * Injects the Frida Hook Engine into an APK (frida-hook-engine-main, Path B):
 * the three engine files ({@code libfrida-gadget.so}, {@code
 * libfrida-gadget.config.so}, {@code libfrida-gadget.script.so}) are added to
 * the APK's {@code lib/<abi>/} folders and a {@code System.loadLibrary(
 * "frida-gadget")} call is wired into the app's Application class.
 *
 * <p>The load call goes into {@code onCreate()}, not {@code <clinit>}: the
 * engine's framework hooks resolve against the app classloader, which only
 * becomes available after {@code Application.attachBaseContext()}. Loading
 * earlier triggers the classloader race described in the engine's
 * troubleshooting docs. The dex patch follows the same rename-and-wrap
 * pattern used for the il2cpp dumper: an existing {@code onCreate} is renamed
 * and replaced by a wrapper that loads the gadget and then calls the
 * original, so no register counting is needed and the original body (including
 * its own super call) is untouched.</p>
 *
 * <p>The engine bundle (25 MB, arm64 gadget) is downloaded on first use from
 * the official release and cached under filesDir/frida; gadgets for other
 * ABIs are pulled from the matching official Frida release. The bundle never
 * ships inside MP-Manager's own APK.</p>
 */
public final class FridaGadgetPatcher {

    /** Official engine bundle: libfrida-gadget.so (arm64) + config.so + script.so. */
    public static final String BUNDLE_URL =
            "https://github.com/muhammadrizwan87/frida-hook-engine/releases/download/v1.0.0/frida-gadget-bundle.zip";
    /** Frida release used for the non-arm64 gadgets and the Path C injector. */
    public static final String FRIDA_VERSION = "17.26.1";

    public static final String LIB_NAME = "frida-gadget";
    public static final String GADGET_SO = "lib" + LIB_NAME + ".so";
    public static final String CONFIG_SO = "lib" + LIB_NAME + ".config.so";
    public static final String SCRIPT_SO = "lib" + LIB_NAME + ".script.so";
    private static final String RENAMED_ON_CREATE = "onCreate$frida";

    /** android.R.attr.extractNativeLibs — a binary manifest stores attributes by resource id. */
    private static final int ATTR_EXTRACT_NATIVE_LIBS = 0x010104ea;

    /** ABIs we can inject a gadget for, with the Frida release arch suffix. */
    public static final String[] SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};

    private static String fridaArchForAbi(String abi) {
        switch (abi) {
            case "arm64-v8a": return "android-arm64";
            case "armeabi-v7a": return "android-arm";
            case "x86_64": return "android-x86_64";
            case "x86": return "android-x86";
            default: return null;
        }
    }

    /** True when the APK contains a lib/<abi>/ folder we can add the gadget to. */
    public static boolean hasNativeLibDir(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            for (java.util.Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (name.startsWith("lib/") && name.endsWith(".so")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** ABI folders (lib/<abi>/) present in the APK that we can add a gadget to. */
    public static List<String> gadgetAbis(File apk) throws IOException {
        Set<String> dirs = new LinkedHashSet<>();
        try (ZipFile zf = new ZipFile(apk)) {
            for (java.util.Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (!name.startsWith("lib/") || !name.endsWith(".so")) continue;
                int secondSlash = name.indexOf('/', 4);
                if (secondSlash > 4) dirs.add(name.substring(4, secondSlash));
            }
        }
        List<String> result = new ArrayList<>();
        for (String abi : SUPPORTED_ABIS) {
            if (dirs.contains(abi)) result.add(abi);
        }
        if (result.isEmpty()) {
            // No native libraries yet: the gadget can be added for any ABI —
            // Android loads the folder matching the device at install time.
            // The dialog pre-checks this device's ABI by default.
            return new ArrayList<>(java.util.Arrays.asList(SUPPORTED_ABIS));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Engine bundle / binary management (download-on-first-use)
    // ------------------------------------------------------------------

    private static File fridaDir(Context context) {
        return new File(context.getFilesDir(), "frida");
    }

    public static File bundleDir(Context context) {
        return new File(fridaDir(context), "bundle");
    }

    public static boolean isBundleReady(Context context) {
        File dir = bundleDir(context);
        return new File(dir, GADGET_SO).isFile()
                && new File(dir, CONFIG_SO).isFile()
                && new File(dir, SCRIPT_SO).isFile();
    }

    /** The engine's pre-compiled agent (Path C script base). */
    public static File agentScriptFile(Context context) {
        return new File(bundleDir(context), SCRIPT_SO);
    }

    /**
     * frida-compile's bundle marker (✄ U+2704) separating the header/source
     * map from the code, as raw UTF-8 bytes to avoid char-encoding mistakes.
     */
    private static final byte[] BUNDLE_SCISSORS =
            {'\n', (byte) 0xE2, (byte) 0x9C, (byte) 0x84, '\n'};

    /**
     * Extracts the raw JavaScript from the engine's pre-compiled agent. The
     * bundle starts with a frida-compile header (📦, entry sizes) and ends
     * with the source map, separated by ✂ markers — Gadget strips those
     * itself in Script mode, but a plain runtime (frida-inject) rejects the
     * file with a SyntaxError, so the console path needs the code section.
     */
    public static byte[] plainAgentScript(File agentFile) throws IOException {
        byte[] data;
        try (InputStream in = new FileInputStream(agentFile)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            data = bos.toByteArray();
        }
        int first = indexOf(data, BUNDLE_SCISSORS, 0);
        if (first < 0) return data; // already a plain script
        int second = indexOf(data, BUNDLE_SCISSORS, first + BUNDLE_SCISSORS.length);
        if (second < 0) return data;
        return java.util.Arrays.copyOfRange(data, first + BUNDLE_SCISSORS.length, second);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Downloaded frida-inject binary for this device's ABI (Path C). */
    public static File injectBinary(Context context) {
        return new File(fridaDir(context), "frida-inject-" + NativeToolManager.deviceAbi());
    }

    public static boolean isInjectReady(Context context) {
        return injectBinary(context).isFile() && agentScriptFile(context).isFile();
    }

    private interface DownloadProgress {
        void onProgress(long done, long total);
    }

    private static void downloadFile(String url, File out, DownloadProgress progress) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            long lastReport = 0;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
                done += n;
                if (done - lastReport > 256 * 1024) {
                    lastReport = done;
                    progress.onProgress(done, total);
                }
            }
            progress.onProgress(done, total);
        } finally {
            conn.disconnect();
        }
    }

    private static void unzip(File zip, File dest) throws IOException {
        String destPath = dest.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[65536];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("/")) name = name.substring(1);
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(destPath + File.separator)
                        && !out.getCanonicalPath().equals(destPath)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void xzDecompress(File src, File dest) throws IOException {
        try (InputStream in = new XZInputStream(new BufferedInputStream(new FileInputStream(src)));
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    /**
     * Makes sure the engine bundle is in the cache, offering to download it
     * (about 25 MB) when it is not. Safe to call from the UI thread.
     */
    public static void ensureBundle(Activity activity, Runnable onReady) {
        if (isBundleReady(activity)) {
            onReady.run();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.frida_download_required)
                .setMessage(activity.getString(R.string.frida_download_message, "~25"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.download, (d, w) ->
                        downloadWithUi(activity, activity.getString(R.string.frida_downloading_engine),
                                BUNDLE_URL, new File(activity.getCacheDir(), "frida-gadget-bundle.zip"),
                                file -> {
                                    File dir = bundleDir(activity);
                                    dir.mkdirs();
                                    unzip(file, dir);
                                    file.delete();
                                    if (!isBundleReady(activity)) {
                                        throw new IOException("Engine bundle is missing expected files");
                                    }
                                })
                                .thenAccept(ready -> {
                                    if (ready) activity.runOnUiThread(onReady);
                                })
                                .exceptionally(e -> {
                                    activity.runOnUiThread(() ->
                                            new ErrorUtil(activity).showError(e));
                                    return null;
                                }))
                .show();
    }

    /**
     * Makes sure the frida-inject binary and the engine agent script are
     * cached for the Path C root tool.
     */
    public static void ensureInject(Activity activity, Runnable onReady) {
        if (isInjectReady(activity)) {
            onReady.run();
            return;
        }
        ensureBundle(activity, () -> {
            String arch = fridaArchForAbi(NativeToolManager.deviceAbi());
            if (arch == null) arch = "android-arm64";
            String name = "frida-inject-" + FRIDA_VERSION + "-" + arch + ".xz";
            String url = "https://github.com/frida/frida/releases/download/"
                    + FRIDA_VERSION + "/" + name;
            downloadWithUi(activity, activity.getString(R.string.frida_downloading_inject),
                    url, new File(activity.getCacheDir(), name), file -> {
                        File dest = injectBinary(activity);
                        fridaDir(activity).mkdirs();
                        xzDecompress(file, dest);
                        file.delete();
                        NativeToolManager.setExecutableChecked(dest);
                        if (!dest.isFile() || dest.length() == 0) {
                            throw new IOException("frida-inject download failed");
                        }
                    })
                    .thenAccept(ready -> {
                        if (ready) activity.runOnUiThread(onReady);
                    })
                    .exceptionally(e -> {
                        activity.runOnUiThread(() -> new ErrorUtil(activity).showError(e));
                        return null;
                    });
        });
    }

    /** Runs a download with a cancelable progress dialog; completes async. */
    private static java.util.concurrent.CompletableFuture<Boolean> downloadWithUi(
            Activity activity, String title, String url, File tmp,
            ThrowingConsumer<File> onDownloaded) {
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);
        android.widget.TextView status = new android.widget.TextView(activity);
        status.setTextSize(14);
        root.addView(status);
        android.widget.ProgressBar bar = new android.widget.ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        root.addView(bar, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        final boolean[] cancelled = {false};
        dialog.setOnDismissListener(d -> cancelled[0] = true);
        new Thread(() -> {
            try {
                downloadFile(url, tmp, (done, total) -> activity.runOnUiThread(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgress((int) (1000 * done / total));
                        status.setText(tmp.getName() + " " + done / 1024 / 1024 + "/"
                                + total / 1024 / 1024 + " MB");
                    } else {
                        status.setText(tmp.getName() + " " + done / 1024 / 1024 + " MB");
                    }
                }));
                if (cancelled[0]) {
                    tmp.delete();
                    future.complete(false);
                    return;
                }
                onDownloaded.accept(tmp);
                activity.runOnUiThread(dialog::dismiss);
                future.complete(true);
            } catch (Exception e) {
                tmp.delete();
                activity.runOnUiThread(() -> {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    future.completeExceptionally(e);
                });
            }
        }).start();
        return future;
    }

    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    /**
     * The gadget .so for an ABI, materialized in {@code workDir}. arm64 comes
     * from the engine bundle; the other ABIs from the matching official Frida
     * release (xz-compressed, decompressed once and cached).
     */
    public static File gadgetSoForAbi(Context context, String abi, File workDir,
                                      APKLogger logger) throws IOException {
        File out = new File(workDir, abi + "_" + GADGET_SO);
        if ("arm64-v8a".equals(abi)) {
            File bundled = new File(bundleDir(context), GADGET_SO);
            if (!bundled.isFile()) throw new IOException("Engine bundle is not downloaded");
            copyFile(bundled, out);
            return out;
        }
        String arch = fridaArchForAbi(abi);
        if (arch == null) throw new IOException("Unsupported ABI " + abi);
        File cached = new File(fridaDir(context), GADGET_SO + "." + abi);
        if (!cached.isFile()) {
            String name = "frida-gadget-" + FRIDA_VERSION + "-" + arch + ".so.xz";
            String url = "https://github.com/frida/frida/releases/download/"
                    + FRIDA_VERSION + "/" + name;
            File tmp = new File(workDir, name);
            if (logger != null) logger.logMessage("Downloading " + name);
            try {
                downloadFile(url, tmp, (done, total) -> { });
            } catch (Exception e) {
                throw new IOException("Download failed for " + name + ": " + e, e);
            }
            xzDecompress(tmp, cached);
            tmp.delete();
        }
        copyFile(cached, out);
        return out;
    }

    // ------------------------------------------------------------------
    // APK patching
    // ------------------------------------------------------------------

    /**
     * Builds the patched APK: patched dex entry (gadget load in onCreate) +
     * added engine files + manifest extractNativeLibs fix.
     *
     * @param targetClass fully qualified class receiving the loadLibrary call
     * @param config      the module configuration to bake into the config file
     */
    public static File patchApk(Context context, File inputApk, List<String> abis,
                                String targetClass, FridaConfig config,
                                APKLogger logger) throws Exception {
        if (abis == null || abis.isEmpty()) {
            throw new IOException("No gadget ABI selected");
        }
        if (!isBundleReady(context)) {
            throw new IOException("Frida engine bundle is not downloaded");
        }
        if (TextUtils.isEmpty(targetClass)) {
            throw new IOException("Could not find an Application or launcher activity class in the manifest");
        }
        String descriptor = "L" + targetClass.replace('.', '/') + ";";
        Opcodes opcodes = Opcodes.getDefault();
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(inputApk, opcodes);

        String targetEntry = null;
        DexBackedDexFile targetDex = null;
        ClassDef targetClassDef = null;
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef classDef : entry.getDexFile().getClasses()) {
                if (descriptor.equals(classDef.getType())) {
                    targetEntry = entryName;
                    targetDex = entry.getDexFile();
                    targetClassDef = classDef;
                    break;
                }
            }
            if (targetEntry != null) break;
        }
        if (targetEntry == null) {
            throw new IOException(targetClass + " is declared in the manifest but its code is "
                    + "not in this APK (split or stubbed). Pick another injection point, "
                    + "the Application class if there is one.");
        }
        // Which lifecycle onCreate the framework actually calls depends on the
        // component type: Application gets onCreate()V, activities get
        // onCreate(Bundle). Wrapping the wrong signature would silently never
        // run, so resolve it from the superclass chain.
        int kind = detectComponentKind(container, targetClassDef);
        if (logger != null) {
            logger.logMessage("Patching " + targetEntry + ": " + descriptor
                    + " (component kind " + (kind == KIND_ACTIVITY ? "activity" : "application/service") + ")");
        }

        byte[] patchedDex = rewriteDexWithGadgetLoad(targetDex, descriptor, kind, logger);
        File workDir = new File(context.getCacheDir(), "fridagadget_" + System.currentTimeMillis());
        workDir.mkdirs();
        try {
            Map<String, File> replacements = new LinkedHashMap<>();
            File patchedDexFile = new File(workDir, targetEntry + ".patched");
            try (FileOutputStream fos = new FileOutputStream(patchedDexFile)) {
                fos.write(patchedDex);
            }
            replacements.put(targetEntry, patchedDexFile);

            // The gadget reads its config and script from the filesystem next
            // to the library; that only exists when the installer extracts the
            // native libs. Flip the manifest attribute so our additions are
            // extracted (idempotent, and the standard approach for gadget
            // repacks).
            File manifest = patchExtractNativeLibs(inputApk, new File(workDir, "AndroidManifest.xml.patched"));
            Map<String, Integer> methods = new LinkedHashMap<>();
            if (manifest != null) {
                replacements.put("AndroidManifest.xml", manifest);
                methods.put("AndroidManifest.xml", 0);
                if (logger != null) logger.logMessage("Setting manifest extractNativeLibs=true");
            }

            Map<String, File> additions = new LinkedHashMap<>();
            File configFile = new File(workDir, CONFIG_SO);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(FridaConfig.buildGadgetConfigJson(config.params())
                        .getBytes(StandardCharsets.UTF_8));
            }
            File scriptFile = new File(bundleDir(context), SCRIPT_SO);
            for (String abi : abis) {
                File so = gadgetSoForAbi(context, abi, workDir, logger);
                String dir = "lib/" + abi + "/";
                for (Map.Entry<String, File> add : new LinkedHashMap<String, File>() {{
                    put(GADGET_SO, so);
                    put(CONFIG_SO, configFile);
                    put(SCRIPT_SO, scriptFile);
                }}.entrySet()) {
                    String entryName = dir + add.getKey();
                    additions.put(entryName, add.getValue());
                    methods.put(entryName, 0);
                }
                if (logger != null) logger.logMessage("Adding " + dir + "{libfrida-gadget.so, config.so, script.so}");
            }

            File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                    FilenameUtils.getBaseName(inputApk.getName()) + "_frida.apk"));
            ApkZipAlignUtil.rebuildApk(inputApk, outputFile, replacements, methods, additions, null);
            if (logger != null) logger.logMessage("Saved " + outputFile.getName());
            return outputFile;
        } finally {
            deleteDirectory(workDir);
        }
    }

    /**
     * Returns a manifest replacement with {@code android:extractNativeLibs}
     * set to true, or null when the manifest already extracts native libs.
     */
    static File patchExtractNativeLibs(File apk, File out) throws IOException {
        AndroidManifestBlock manifest;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) throw new IOException("APK has no AndroidManifest.xml");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (InputStream is = zip.getInputStream(entry)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            }
            manifest = AndroidManifestBlock.load(new ByteArrayInputStream(bos.toByteArray()));
        }
        if (manifest.getPackageName() == null) {
            throw new IOException("APK has no readable AndroidManifest.xml");
        }
        ResXmlElement application = manifest.getOrCreateApplicationElement();
        ResXmlAttribute attribute = application.searchAttributeByResourceId(ATTR_EXTRACT_NATIVE_LIBS);
        if (attribute != null && attribute.getValueAsBoolean()) return null;
        application.getOrCreateAndroidAttribute("extractNativeLibs", ATTR_EXTRACT_NATIVE_LIBS)
                .setValueAsBoolean(true);
        manifest.refresh();
        manifest.writeBytes(out);
        return out;
    }

    // ------------------------------------------------------------------
    // dex patching
    // ------------------------------------------------------------------

    /** Component kinds deciding which onCreate signature the framework calls. */
    static final int KIND_APPLICATION = 0;
    static final int KIND_ACTIVITY = 1;
    static final int KIND_OTHER = 2;

    /**
     * Walks the superclass chain (within the APK's dexes) to the first
     * framework class, and classifies the component: Application/Service get
     * onCreate()V, activities get onCreate(Bundle).
     */
    static int detectComponentKind(MultiDexContainer<? extends DexBackedDexFile> container,
                                   ClassDef target) throws IOException {
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef classDef : entry.getDexFile().getClasses()) {
                classes.put(classDef.getType(), classDef);
            }
        }
        String current = target.getSuperclass();
        int hops = 0;
        while (current != null && hops++ < 64) {
            if (current.startsWith("Landroid/")
                    || current.startsWith("Landroidx/")
                    || current.startsWith("Landroid/support/")) {
                if (current.contains("Application")) return KIND_APPLICATION;
                if (current.contains("Activity")) return KIND_ACTIVITY;
                return KIND_APPLICATION; // Service, Provider, etc.
            }
            ClassDef def = classes.get(current);
            current = def == null ? null : def.getSuperclass();
        }
        return KIND_OTHER;
    }

    static byte[] rewriteDexWithGadgetLoad(DexBackedDexFile dex, String descriptor, int kind,
                                           APKLogger logger) throws IOException {
        DexPool pool = new DexPool(dex.getOpcodes());
        boolean found = false;
        for (ClassDef classDef : dex.getClasses()) {
            if (descriptor.equals(classDef.getType())) {
                classDef = injectLoadLibrary(classDef, kind, logger);
                found = true;
            }
            pool.internClass(classDef);
        }
        if (!found) throw new IOException(descriptor + " disappeared from the dex file");
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        return java.util.Arrays.copyOf(store.getData(), store.getSize());
    }

    /**
     * Wraps every existing {@code onCreate} overload with a loading wrapper
     * (each renamed original keeps its full signature), or — when the class
     * does not override onCreate — adds the override the framework actually
     * calls for this component kind. Load failures are swallowed so the
     * target app keeps working without hooks; the original body always runs.
     */
    static ClassDef injectLoadLibrary(ClassDef classDef, int kind, APKLogger logger) throws IOException {
        List<Method> virtualMethods = new ArrayList<>();
        Set<String> takenNames = new LinkedHashSet<>();
        int existing = 0;
        for (Method m : classDef.getVirtualMethods()) {
            takenNames.add(m.getName());
            if ("onCreate".equals(m.getName()) && m.getImplementation() != null) existing++;
        }
        String delegateBase = RENAMED_ON_CREATE;
        while (takenNames.contains(delegateBase)) delegateBase += "$";

        int wrapped = 0;
        boolean droppedAbstract = false;
        for (Method m : classDef.getVirtualMethods()) {
            if (!"onCreate".equals(m.getName())) {
                virtualMethods.add(m);
                continue;
            }
            if (m.getImplementation() == null) {
                // An abstract onCreate cannot coexist with our concrete
                // override in the same class — drop the declaration.
                droppedAbstract = true;
                continue;
            }
            String delegateName = delegateBase + (wrapped == 0 ? "" : "$" + wrapped);
            virtualMethods.add(new ImmutableMethod(classDef.getType(), delegateName,
                    m.getParameters(), m.getReturnType(), m.getAccessFlags(),
                    m.getAnnotations(), m.getHiddenApiRestrictions(),
                    ImmutableMethodImplementation.of(m.getImplementation())));
            virtualMethods.add(buildOnCreateWrapper(classDef.getType(), delegateName,
                    m.getParameters(), m.getReturnType(), m.getAccessFlags(), false, null));
            wrapped++;
        }
        if (wrapped == 0) {
            // No onCreate override to wrap: add the override the framework
            // calls for this component type and delegate to the superclass.
            String superType = classDef.getSuperclass();
            if (TextUtils.isEmpty(superType)) {
                throw new IOException(classType(classDef)
                        + " has no onCreate to wrap and no superclass to inherit it from — "
                        + "pick another injection point");
            }
            boolean activity = kind == KIND_ACTIVITY;
            List<com.android.tools.smali.dexlib2.iface.MethodParameter> params = activity
                    ? Collections.singletonList(new ImmutableMethodParameter("Landroid/os/Bundle;", null, null))
                    : Collections.emptyList();
            virtualMethods.add(buildOnCreateWrapper(classDef.getType(), "onCreate", params, "V",
                    AccessFlags.PUBLIC.getValue(), true, superType));
            if (logger != null) {
                logger.logMessage("Adding new onCreate(" + (activity ? "Bundle" : "") + ") override");
            }
        } else if (logger != null) {
            logger.logMessage("Wrapped " + wrapped + " existing onCreate overload(s)");
        }
        if (droppedAbstract && logger != null) logger.logMessage("Dropped abstract onCreate()");
        return new ImmutableClassDef(classDef.getType(), classDef.getAccessFlags(),
                classDef.getSuperclass(), classDef.getInterfaces(), classDef.getSourceFile(),
                classDef.getAnnotations(), classDef.getStaticFields(),
                classDef.getInstanceFields(), classDef.getDirectMethods(), virtualMethods);
    }

    private static String classType(ClassDef classDef) {
        return classDef.getType();
    }

    /** Total parameter register words (long/double take two). */
    private static int paramWords(List<? extends CharSequence> params) {
        int words = 0;
        for (CharSequence p : params) {
            String t = p.toString();
            words += ("J".equals(t) || "D".equals(t)) ? 2 : 1;
        }
        return words;
    }

    /**
     * Builds the onCreate wrapper. Registers: v0 scratch, v1..vN = p0..pN.
     * Code unit addresses:
     * <pre>
     * 0:  const-string v0                (2)  ┐ try [0,5)
     * 2:  invoke-static {v0} load        (3)  ┘
     * 5:  invoke-virtual/super-range {v1..vN} delegate (3)
     * 8:  return-void                    (2)
     * 10: move-exception v0              (1)  ┐ catch-all handler
     * 11: invoke-virtual/super-range     (3) |
     * 14: return-void                    (2)  ┘
     * </pre>
     */
    private static ImmutableMethod buildOnCreateWrapper(String classType, String delegateName,
                                                        List<? extends com.android.tools.smali.dexlib2.iface.MethodParameter> params,
                                                        String returnType, int accessFlags,
                                                        boolean invokeSuper, String superType) throws IOException {
        int ins = 1 + paramWords(params);
        int registers = ins + 1;
        String owner = invokeSuper ? superType : classType;
        ImmutableMethodReference delegateRef = new ImmutableMethodReference(
                owner, delegateName, new ArrayList<>(params), returnType);
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                new ImmutableStringReference(LIB_NAME)));
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, loadLibraryRef()));
        instructions.add(new ImmutableInstruction3rc(invokeSuper ? Opcode.INVOKE_SUPER_RANGE
                        : Opcode.INVOKE_VIRTUAL_RANGE, 1, ins, delegateRef));
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));       // addr 8
        instructions.add(new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 0)); // addr 10
        instructions.add(new ImmutableInstruction3rc(invokeSuper ? Opcode.INVOKE_SUPER_RANGE
                : Opcode.INVOKE_VIRTUAL_RANGE, 1, ins, delegateRef));
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));       // addr 14
        List<ImmutableTryBlock> tries = Collections.singletonList(
                // null exception type = catch-all handler
                new ImmutableTryBlock(0, 5, Collections.singletonList(
                        new ImmutableExceptionHandler(null, 10))));
        ImmutableMethodImplementation impl = new ImmutableMethodImplementation(
                registers, instructions, tries, null);
        return new ImmutableMethod(classType, "onCreate", new ArrayList<>(params), returnType,
                accessFlags, Collections.emptySet(), Collections.emptySet(), impl);
    }

    private static ImmutableMethodReference loadLibraryRef() {
        return new ImmutableMethodReference("Ljava/lang/System;", "loadLibrary",
                Collections.singletonList("Ljava/lang/String;"), "V");
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
