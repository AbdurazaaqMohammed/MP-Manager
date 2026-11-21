package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DialogEditorEngine {

    public static final String FORMAT = "mpmanager-dialog";
    public static final int FORMAT_VERSION = 1;

    public enum InjectionMethod {
        AUTO,
        SMALI,
        XML,
        RESOURCES
    }

    public static final class DialogPackage {
        public String format = FORMAT;
        public int version = FORMAT_VERSION;
        public String name = "Dialog";
        public String injectionMethod = InjectionMethod.AUTO.name();
        public String customHook = "";
        public String customSmali = "";
        public OverlayInjectorUtil.DialogOptions dialog;
        public OverlayInjectorUtil.ToastOptions toast;
    }

    public static final class AnalysisResult {
        public int dexCount;
        public int resourceCount;
        public int xmlCount;
        public int imageCount;
        public boolean manifestFound;
        public boolean activitiesFound;
        public final List<String> issues = new ArrayList<>();
        public InjectionMethod recommended = InjectionMethod.SMALI;

        public String summary(Context context) {
            String separator = "\n";
            String result = "DEX: " + dexCount + separator +
                    "XML: " + xmlCount + separator +
                    "Resources: " + resourceCount + separator +
                    "Images: " + imageCount;
            if (!issues.isEmpty()) result += separator + "Warnings: " + String.join(", ", issues);
            result += separator + "Recommended: " + recommended.name();
            return result;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private DialogEditorEngine() {}

    public static String toJson(DialogPackage pack) {
        if (pack == null) throw new IllegalArgumentException("Design package is required");
        return GSON.toJson(pack);
    }

    public static DialogPackage fromJson(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) throw new IOException("Empty design package");
        try {
            DialogPackage pack = GSON.fromJson(json, DialogPackage.class);
            if (pack == null || !FORMAT.equals(pack.format)) {
                throw new IOException("Unsupported Dialog design format");
            }
            if (pack.version > FORMAT_VERSION) throw new IOException("Design package is from a newer version");
            return pack;
        } catch (Exception e) {
            throw new IOException("Invalid Dialog design package: " + e.getMessage(), e);
        }
    }

    public static void exportToFile(File file, DialogPackage pack) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create export directory");
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(toJson(pack).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static DialogPackage importFromFile(File file) throws IOException {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line).append('\n');
        }
        return fromJson(json.toString());
    }

    public static File createBackup(File apk) throws IOException {
        if (apk == null || !apk.isFile()) throw new IOException("APK not found");
        File backup = new File(apk.getParentFile(),
                apk.getName() + ".mpmanager-backup-" + System.currentTimeMillis());
        try (java.io.InputStream in = new FileInputStream(apk);
             FileOutputStream out = new FileOutputStream(backup)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return backup;
    }

    public static AnalysisResult analyzeApk(File apk) throws IOException {
        AnalysisResult result = new AnalysisResult();
        if (apk == null || !apk.isFile()) throw new IOException("APK not found");
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("AndroidManifest.xml")) result.manifestFound = true;
                if (name.matches("classes[^/]*\\.dex")) result.dexCount++;
                if (name.startsWith("res/") && name.endsWith(".xml")) result.xmlCount++;
                if (name.startsWith("res/")) {
                    result.resourceCount++;
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            || lower.endsWith(".webp") || lower.endsWith(".gif")) result.imageCount++;
                }
            }
        }
        result.activitiesFound = result.manifestFound;
        if (!result.manifestFound) result.issues.add("AndroidManifest.xml was not found");
        if (result.dexCount == 0) result.issues.add("No DEX files detected");
        if (result.dexCount > 1) result.issues.add("Multi-dex APK; activity and patch locations will be scanned");
        if (result.resourceCount == 0) result.issues.add("No drawable/layout resources detected");
        result.recommended = result.dexCount > 0 ? InjectionMethod.SMALI : InjectionMethod.AUTO;
        return result;
    }

    public static List<String> validate(DialogPackage pack) {
        List<String> issues = new ArrayList<>();
        if (pack == null) {
            issues.add("Design package is missing");
            return issues;
        }
        try {
            InjectionMethod.valueOf(pack.injectionMethod == null ? "AUTO" : pack.injectionMethod);
        } catch (IllegalArgumentException e) {
            issues.add("Unknown injection method");
        }
        String configurationError = OverlayInjectorUtil.injectionConfigurationError(
                pack.injectionMethod, pack.customHook);
        if (configurationError != null && !issues.contains(configurationError)) {
            issues.add(configurationError);
        }
        if (pack.dialog != null) {
            if (pack.dialog.advanced && (pack.dialog.widgets == null || pack.dialog.widgets.isEmpty())) {
                issues.add("Advanced dialog has no widgets");
            }
            if (pack.dialog.cornerRadiusDp < 0) issues.add("Negative dialog corner radius");
            if (pack.dialog.animSpeedMs < 100) issues.add("Animation speed is below 100 ms");
        }
        if (pack.toast != null && (pack.toast.message == null || pack.toast.message.trim().isEmpty())) {
            issues.add("Toast message is empty");
        }
        return issues;
    }

    public static void repair(DialogPackage pack) {
        if (pack == null) return;
        if (pack.dialog != null) {
            pack.dialog.cornerRadiusDp = Math.max(0f, pack.dialog.cornerRadiusDp);
            pack.dialog.borderWidthDp = Math.max(0f, pack.dialog.borderWidthDp);
            pack.dialog.animSpeedMs = Math.max(100, pack.dialog.animSpeedMs);
            if (pack.dialog.positive == null) pack.dialog.positive = "";
            if (pack.dialog.negative == null) pack.dialog.negative = "";
            if (pack.dialog.neutral == null) pack.dialog.neutral = "";
        }
        if (pack.toast != null) {
            if (pack.toast.message == null) pack.toast.message = "";
        }
        if (pack.injectionMethod == null || pack.injectionMethod.trim().isEmpty()) {
            pack.injectionMethod = InjectionMethod.AUTO.name();
        } else if (!InjectionMethod.AUTO.name().equals(pack.injectionMethod)
                && !InjectionMethod.SMALI.name().equals(pack.injectionMethod)) {
            pack.injectionMethod = InjectionMethod.AUTO.name();
        }
        if (pack.dialog != null) {
            pack.dialog.injectionMethod = pack.injectionMethod;
        }
    }

    public static Map<String, String> normalizedFields(DialogPackage pack) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (pack == null) return fields;
        fields.put("format", pack.format);
        fields.put("name", pack.name);
        fields.put("method", pack.injectionMethod);
        fields.put("hook", pack.customHook);
        fields.put("hasDialog", String.valueOf(pack.dialog != null));
        fields.put("hasToast", String.valueOf(pack.toast != null));
        return fields;
    }
}
