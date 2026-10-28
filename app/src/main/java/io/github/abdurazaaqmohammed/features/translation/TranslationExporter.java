package io.github.abdurazaaqmohammed.features.translation;

import android.content.Context;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.serializableItems.ResEntry;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exports an APK's translatable strings for translators.
 *
 * <p>Strings live in {@code resources.arsc}, never in a text {@code res/values/strings.xml}: aapt
 * compiles every values resource into the resource table, and the {@code res/**} files that do
 * remain are compiled binary AXML. So the table is parsed here, which also yields the resource
 * name and id needed to map a translation back onto the APK.
 *
 * <p>Two formats are written:
 * <ul>
 *   <li>{@code .mtd} - one self-contained {@code { "source" <newline> "target" }} block per
 *       string, ready to hand to a translator;</li>
 *   <li>{@code .json} - a JSON array of {@code {id, name, source, target}} objects, which keeps
 *       the name/id so translations can be applied back automatically.</li>
 * </ul>
 */
public class TranslationExporter {

    private static final String ARSC = "resources.arsc";
    private static final String STRING_TYPE_PREFIX = "@string/";

    /**
     * Represents a single string entry for translation.
     */
    public static class StringEntry {
        public final String name;       // resource name, e.g. "app_name"
        public final String value;      // source text, e.g. "My App"
        public final int resId;         // resource id, 0 when unknown
        public final String translation; // existing translation, or "" for the translator

        public StringEntry(String name, String value, int resId) {
            this(name, value, resId, "");
        }

        public StringEntry(String name, String value, int resId, String translation) {
            this.name = name;
            this.value = value;
            this.resId = resId;
            this.translation = translation == null ? "" : translation;
        }
    }

    /**
     * Result of a translation export operation.
     */
    public static class ExportResult {
        public final boolean success;
        public final File exportedFile;
        public final String errorMessage;
        public final int stringCount;

        public ExportResult(boolean success, File file, String error, int count) {
            this.success = success;
            this.exportedFile = file;
            this.errorMessage = error;
            this.stringCount = count;
        }
    }

    /**
     * Extracts every {@code @string} resource from an APK and exports them to a file.
     *
     * @param context Application context
     * @param apkFile The APK file to extract strings from
     * @param format Export format: "json" or "mtd"
     * @param outputDir Directory to save the exported file
     * @return ExportResult with the exported file or error
     */
    public static ExportResult exportStrings(Context context, File apkFile, String format, File outputDir) {
        if (apkFile == null || !apkFile.exists()) {
            return new ExportResult(false, null, "APK file not found", 0);
        }
        if (outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())) {
            return new ExportResult(false, null, "Output directory not available", 0);
        }

        List<StringEntry> entries;
        try {
            entries = extractStringsFromApk(context, apkFile);
        } catch (Exception e) {
            return new ExportResult(false, null, "Failed to extract strings: " + e.getMessage(), 0);
        }

        if (entries == null || entries.isEmpty()) {
            return new ExportResult(false, null, "No strings found in APK", 0);
        }

        File outputFile;
        try {
            String extension = "mtd".equalsIgnoreCase(format) ? ".mtd" : ".json";
            String baseName = apkFile.getName().replaceAll("\\.apk$", "") + "_strings";
            outputFile = new File(outputDir, baseName + extension);
            
            if (outputFile.exists()) {
                String timestamp = String.valueOf(System.currentTimeMillis());
                outputFile = new File(outputDir, baseName + "_" + timestamp + extension);
            }
            
            writeExportFile(outputFile, entries, format);
        } catch (Exception e) {
            return new ExportResult(false, null, "Failed to save file: " + e.getMessage(), entries.size());
        }

        return new ExportResult(true, outputFile, null, entries.size());
    }

    /**
     * Extracts all translatable strings from an APK.
     *
     * <p>Reads {@code resources.arsc}, the only place aapt stores them. Entries without a value
     * (plurals, string arrays) are skipped because they cannot be translated as a single string.
     */
    public static List<StringEntry> extractStringsFromApk(Context context, File apkFile) throws Exception {
        List<StringEntry> entries = new ArrayList<>();
        try (ZipFile zf = new ZipFile(apkFile)) {
            FileHeader header = zf.getFileHeader(ARSC);
            if (header == null) return entries;
            try (InputStream is = zf.getInputStream(header)) {
                for (ResEntry e : new ResourceTableParser(is).parse()) {
                    String ref = e.getName();
                    if (ref == null || !ref.startsWith(STRING_TYPE_PREFIX)) continue;
                    String value = e.getValue();
                    if (value == null || value.isEmpty()) continue;
                    entries.add(new StringEntry(ref.substring(STRING_TYPE_PREFIX.length()), value, e.getResourceId()));
                }
            }
        }
        return entries;
    }

    /**
     * Writes the export file in the specified format, UTF-8 so non-ASCII sources survive.
     */
    private static void writeExportFile(File file, List<StringEntry> entries, String format) throws IOException {
        if ("mtd".equalsIgnoreCase(format)) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writeMtdFormat(writer, entries);
            }
        } else {
            Files.write(file.toPath(),
                    writeJsonFormat(entries).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Writes the .mtd dictionary format: one self-contained block per string, source first and
     * the translation (empty when there is nothing yet) second, e.g.
     * <pre>
     * {
     * "定时任务"
     * ""
     * }
     * </pre>
     */
    private static void writeMtdFormat(BufferedWriter writer, List<StringEntry> entries) throws IOException {
        for (StringEntry entry : entries) {
            writer.write("{\n");
            writer.write('"' + escapeString(entry.value) + "\"\n");
            writer.write('"' + escapeString(entry.translation) + "\"\n");
            writer.write("}\n");
        }
    }

    /**
     * Writes a JSON array of objects, keeping the resource name and id so a returned translation
     * can be applied back to the APK. Written directly rather than through org.json so the
     * escaping is explicit and the output is verifiable.
     */
    private static String writeJsonFormat(List<StringEntry> entries) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            StringEntry e = entries.get(i);
            if (i > 0) sb.append(',');
            sb.append("\n  {\n    \"id\": \"")
                    .append(String.format(Locale.US, "0x%08X", e.resId)).append("\",\n    \"name\": \"")
                    .append(jsonString(e.name)).append("\",\n    \"source\": \"")
                    .append(jsonString(e.value)).append("\",\n    \"target\": \"")
                    .append(jsonString(e.translation)).append("\"\n  }");
        }
        sb.append(entries.isEmpty() ? "]\n" : "\n]\n");
        return sb.toString();
    }

    /** Escapes a value for a JSON string, including control characters that are illegal raw. */
    private static String jsonString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Escapes a string for the .mtd block format.
     */
    private static String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}