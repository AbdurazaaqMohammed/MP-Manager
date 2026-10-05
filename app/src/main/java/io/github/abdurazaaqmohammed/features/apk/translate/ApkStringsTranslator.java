package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.arsc.ArscData;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/**
 * Reads and writes the string resources of a config, plus the plumbing to get the arsc out of an
 * APK in the first place.
 *
 * <p>Two sources are supported, because the entry point is reachable both ways:
 * <ul>
 *   <li><b>Inside an APK</b> - {@code resources.arsc} is copied into the cache and edited there.
 *       The caller injects the result back into the APK and offers signing, so nothing here ever
 *       rewrites the APK in place.</li>
 *   <li><b>A loose {@code .arsc} file</b> - edited where it lies, saved in place, no injection.</li>
 * </ul>
 */
public final class ApkStringsTranslator {

    private static final String WORK_DIR = "xlate_work";
    private static final String ARSC = "resources.arsc";
    private static final int MAX_ARSC_BYTES = 192 * 1024 * 1024;

    private final File apk;
    private final ArscData data;
    private final ArscConfigManager configs;
    /** Key -> arsc entry, so applying is O(1) per row instead of a full table rescan. */
    private final Map<String, ResourceEntry> byName = new HashMap<>();

    private ApkStringsTranslator(File apk, ArscData data) {
        this.apk = apk;
        this.data = data;
        this.configs = new ArscConfigManager(data);
    }

    /** Null when the file is a loose arsc rather than something extracted from an APK. */
    public File apk() {
        return apk;
    }

    public ArscData data() {
        return data;
    }

    public ArscConfigManager configs() {
        return configs;
    }

    /**
     * Opens the arsc of an APK, extracting it into the cache first.
     *
     * @throws IOException when the APK carries no readable resource table
     */
    public static ApkStringsTranslator openFromApk(Context context, File apk) throws IOException {
        File dir = new File(context.getCacheDir(), WORK_DIR + "/" + apk.getName());
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        File arsc = new File(dir, ARSC);
        // A leftover from a crashed run would silently resurrect stale strings.
        arsc.delete();
        if (!copyZipEntry(apk, ARSC, arsc)) {
            throw new IOException("Cannot read " + ARSC + " from " + apk.getName());
        }
        return new ApkStringsTranslator(apk, ArscData.load(arsc, apk, ARSC));
    }

    /** Opens a loose {@code .arsc} file, editing it in place. */
    public static ApkStringsTranslator openArsc(File arsc) throws IOException {
        if (!arsc.isFile()) throw new IOException("No such file: " + arsc.getName());
        return new ApkStringsTranslator(null, ArscData.load(arsc, null, ARSC));
    }

    /**
     * Streams one entry out of a zip. Streaming rather than buffering matters here: the arsc of
     * a large game is tens of megabytes and holding it twice is how you get an OOM on a phone.
     *
     * @return true when the entry was copied
     */
    private static boolean copyZipEntry(File zip, String entry, File dest) throws IOException {
        try (ZipFile zf = new ZipFile(zip)) {
            FileHeader header = zf.getFileHeader(entry);
            // AAPT keeps the table at the root, some toolchains put it under res/ - accept both.
            if (header == null) header = findBySuffix(zf, entry);
            if (header == null) return false;
            if (header.getUncompressedSize() > MAX_ARSC_BYTES) {
                throw new IOException("resources.arsc is too large: " + header.getUncompressedSize());
            }
            try (InputStream in = zf.getInputStream(header);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_ARSC_BYTES) throw new IOException("resources.arsc is too large");
                    out.write(buf, 0, n);
                }
            }
            return dest.length() > 0;
        }
    }

    /** Last-resort lookup by path suffix, for APKs whose table is not where we expected it. */
    private static FileHeader findBySuffix(ZipFile zf, String suffix) {
        List<FileHeader> headers;
        try {
            headers = zf.getFileHeaders();
        } catch (Exception e) {
            return null;
        }
        if (headers == null) return null;
        for (FileHeader header : headers) {
            if (header != null && !header.isDirectory() && header.getFileName().endsWith(suffix)) {
                return header;
            }
        }
        return null;
    }

    /**
     * Builds the editable rows for one config.
     *
     * @param qualifier     the config being translated
     * @param skipFormatted drop values a machine translator would corrupt
     */
    public List<TranslateRow> rows(String qualifier, boolean skipFormatted) {
        String config = Locales.normalize(qualifier);
        List<TranslateRow> out = new java.util.ArrayList<>();
        byName.clear();
        for (ResourceEntry re : configs.entries(config)) {
            String key = safeName(re);
            if (key.isEmpty()) continue;
            String value = valueFor(re, config);
            if (value == null || value.trim().isEmpty()) continue;
            if (skipFormatted && TranslateRow.looksFormatted(value)) continue;
            TranslateRow row = new TranslateRow(key, safeType(re), config, value);
            row.setResource(re);
            byName.put(key, re);
            out.add(row);
        }
        return out;
    }

    private static String safeType(ResourceEntry re) {
        try {
            return re.getType();
        } catch (Exception e) {
            return ArscConfigManager.TYPE;
        }
    }

    private static String safeName(ResourceEntry re) {
        try {
            String name = re.getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    /** @return the value a config holds for this entry, or {@code null} when it has none. */
    public String valueFor(ResourceEntry re, String qualifier) {
        TypeBlock block = configs.block(qualifier);
        if (block == null) return null;
        try {
            Entry entry = block.getEntry(safeName(re));
            if (entry == null || entry.isNull()) return null;
            String value = entry.getValueAsString();
            return value == null ? "" : value;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes every applicable row into the config it came from.
     *
     * @return how many entries were written; the table is only valid to save afterwards
     */
    public int apply(List<TranslateRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        int written = 0;
        for (TranslateRow row : rows) {
            if (!row.isApplicable()) continue;
            if (writeOne(row)) written++;
        }
        if (written > 0) data.pushHistory("Translate (" + written + ")", null);
        return written;
    }

    private boolean writeOne(TranslateRow row) {
        ResourceEntry re = row.getResource() != null ? row.getResource() : byName.get(row.key);
        if (re == null) return false;
        try {
            Entry entry = targetEntry(re, row.configQualifier);
            if (entry == null) return false;
            entry.setValueAsString(row.getTranslation());
            row.setTargetEntry(entry);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves the arsc entry a translation goes into. A config copied through
     * {@link ArscConfigManager#copyConfig} already has every entry, but one created empty does
     * not, so the entry is created on demand here too.
     */
    public Entry targetEntry(ResourceEntry re, String qualifier) {
        String pkgName = configs.packageName();
        if (pkgName == null) return null;
        PackageBlock pkg = data.packageByName(pkgName);
        if (pkg == null) return null;
        SpecTypePair spec = pkg.getSpecTypePair(re.getType());
        if (spec == null) return null;
        TypeBlock block = configs.block(qualifier);
        if (block == null) {
            block = spec.getOrCreateTypeBlock(Locales.normalize(qualifier));
        }
        if (block == null) return null;
        return block.getOrCreateEntry(safeName(re));
    }

    /** Writes the staged table out, after checking it still parses. */
    public void save() throws IOException {
        data.save();
    }

    /** @return BCP-47 tag for a qualifier, for the online engines. */
    public static String bcp47For(String qualifier) {
        String bcp = Locales.guessBcp47(qualifier);
        return bcp == null || bcp.isEmpty() ? "en" : bcp;
    }
}
