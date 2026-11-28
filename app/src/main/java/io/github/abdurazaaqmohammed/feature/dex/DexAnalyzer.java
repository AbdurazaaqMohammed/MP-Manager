package io.github.abdurazaaqmohammed.feature.dex;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Multi-dex indexer used by the DEX browser.
 * Inspects class definitions, method counts, and field signatures without modifying the file.
 */
public final class DexAnalyzer {

    public interface Progress {
        void onProgress(int done, int total, String dexName);
    }

    public static final class ClassInfo {
        public final String type;
        public final String dex;
        public final int methods;
        public final int fields;
        public final String superType;

        ClassInfo(String type, String dex, int methods, int fields, String superType) {
            this.type = type;
            this.dex = dex;
            this.methods = methods;
            this.fields = fields;
            this.superType = superType;
        }

        public String simpleName() {
            int slash = type.lastIndexOf('/');
            String name = slash >= 0 ? type.substring(slash + 1) : type;
            return name.endsWith(";") ? name.substring(0, name.length() - 1) : name;
        }
    }

    public static final class Detail {
        public final String type;
        public final String superType;
        public final List<String> interfaces = new ArrayList<>();
        public final List<String> methods = new ArrayList<>();
        public final List<String> fields = new ArrayList<>();
        public final String dex;

        Detail(String type, String superType, String dex) {
            this.type = type;
            this.superType = superType;
            this.dex = dex;
        }
    }

    public static final class Report {
        public final File apk;
        public final List<String> dexNames = new ArrayList<>();
        public final List<ClassInfo> classes = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int methodCount;
        public int fieldCount;

        Report(File apk) {
            this.apk = apk;
        }

        public String summary() {
            return "DEX: " + dexNames.size()
                    + " · Classes: " + classes.size()
                    + " · Methods: " + methodCount
                    + " · Fields: " + fieldCount
                    + (errors.isEmpty() ? "" : " · Errors: " + errors.size());
        }
    }

    private DexAnalyzer() {
    }

    public static Report analyze(Context context, File source, Progress progress) {
        Report report = new Report(source);
        if (source == null || !source.isFile()) {
            report.errors.add("File not found");
            return report;
        }
        File cache = new File(context.getCacheDir(), "dex_browser");
        if (!cache.isDirectory() && !cache.mkdirs()) {
            report.errors.add("Could not create cache directory");
            return report;
        }
        List<File> dexFiles = new ArrayList<>();
        if (source.getName().toLowerCase(Locale.ROOT).endsWith(".dex")) {
            dexFiles.add(source);
            report.dexNames.add(source.getName());
        } else {
            extractDex(source, cache, dexFiles, report);
        }
        Map<String, ClassInfo> unique = new LinkedHashMap<>();
        int done = 0;
        for (File dex : dexFiles) {
            String name = dex.getName();
            try {
                DexBackedDexFile file = DexFileFactory.loadDexFile(dex, null);
                for (ClassDef cls : file.getClasses()) {
                    int methods = 0;
                    int fields = 0;
                    for (Method ignored : cls.getMethods()) methods++;
                    for (Field ignored : cls.getFields()) fields++;
                    report.methodCount += methods;
                    report.fieldCount += fields;
                    String type = cls.getType();
                    if (!unique.containsKey(type)) {
                        unique.put(type, new ClassInfo(type, name, methods, fields, cls.getSuperclass()));
                    }
                }
            } catch (Exception e) {
                report.errors.add(name + ": " + safeMessage(e));
            }
            done++;
            if (progress != null) progress.onProgress(done, dexFiles.size(), name);
        }
        report.classes.addAll(unique.values());
        Collections.sort(report.classes, (a, b) -> a.type.compareToIgnoreCase(b.type));
        return report;
    }

    public static Detail describe(Context context, File source, String type) {
        if (source == null || !source.isFile() || type == null) return null;
        File cache = new File(context.getCacheDir(), "dex_browser");
        List<File> dexFiles = new ArrayList<>();
        if (source.getName().toLowerCase(Locale.ROOT).endsWith(".dex")) {
            dexFiles.add(source);
        } else {
            Report ignored = new Report(source);
            extractDex(source, cache, dexFiles, ignored);
        }
        for (File dex : dexFiles) {
            try {
                DexBackedDexFile file = DexFileFactory.loadDexFile(dex, null);
                for (ClassDef cls : file.getClasses()) {
                    if (!type.equals(cls.getType())) continue;
                    Detail detail = new Detail(cls.getType(), cls.getSuperclass(), dex.getName());
                    for (CharSequence iface : cls.getInterfaces()) {
                        detail.interfaces.add(iface.toString());
                    }
                    List<String> methods = new ArrayList<>();
                    for (Method method : cls.getMethods()) {
                        StringBuilder signature = new StringBuilder(method.getName()).append('(');
                        boolean first = true;
                        for (CharSequence param : method.getParameterTypes()) {
                            if (!first) signature.append(", ");
                            signature.append(param);
                            first = false;
                        }
                        signature.append(") -> ").append(method.getReturnType());
                        methods.add(signature.toString());
                    }
                    Collections.sort(methods);
                    detail.methods.addAll(methods);
                    List<String> fields = new ArrayList<>();
                    for (Field field : cls.getFields()) {
                        fields.add(field.getName() + " : " + field.getType());
                    }
                    Collections.sort(fields);
                    detail.fields.addAll(fields);
                    return detail;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void extractDex(File apk, File cache, List<File> out, Report report) {
        try (ZipFile zip = new ZipFile(apk)) {
            List<ZipEntry> entries = new ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName();
                if (name.matches("classes(\\d*)\\.dex")) entries.add(entry);
            }
            entries.sort((a, b) -> Integer.compare(dexNumber(a.getName()), dexNumber(b.getName())));
            byte[] buffer = new byte[65536];
            for (ZipEntry entry : entries) {
                File target = new File(cache, apk.getName() + "_" + apk.length() + "_"
                        + entry.getName().replace('/', '_'));
                if (!target.isFile() || target.length() == 0) {
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream os = new FileOutputStream(target)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
                    }
                }
                out.add(target);
                report.dexNames.add(entry.getName());
            }
            if (entries.isEmpty()) report.errors.add("No classes*.dex files found in archive");
        } catch (Exception e) {
            report.errors.add(safeMessage(e));
        }
    }

    private static int dexNumber(String name) {
        String digits = name.replaceAll("\\D", "");
        if (digits.isEmpty()) return 1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
