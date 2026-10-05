package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.arsc.ArscData;

/**
 * Reads and writes the string resources of an APK.
 *
 * <p>In a compiled APK the user-facing strings do not live in {@code res/values/strings.xml};
 * they are compiled into {@code resources.arsc}. Translating therefore means picking the
 * config to read from, staging a translation per key, and writing it back into the config for
 * the target language - creating that config if the APK has never shipped it.
 *
 * <p>Work happens on an extracted copy of the arsc. Nothing touches the APK until
 * {@link #save()} has produced a table that re-parsed cleanly, at which point the caller
 * pushes it back into the zip and offers signing, exactly like the arsc editor does.
 */
public final class ApkStringsTranslator {

    private static final String WORK_DIR = "xlate_work";

    private final File apk;
    private final ArscData data;
    /** Key -> arsc entry, so applying is O(1) per row instead of a full table rescan. */
    private final Map<String, ResourceEntry> byName = new HashMap<>();

    private ApkStringsTranslator(File apk, ArscData data) {
        this.apk = apk;
        this.data = data;
    }

    public File apk() {
        return apk;
    }

    public ArscData data() {
        return data;
    }

    /**
     * Extracts {@code resources.arsc} from the APK into the cache and loads it.
     *
     * @throws IOException when the APK has no readable resource table.
     */
    public static ApkStringsTranslator open(Context context, File apk) throws IOException {
        File workDir = new File(context.getCacheDir(), WORK_DIR + "/" + apk.getName());
        if (!workDir.isDirectory() && !workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("Cannot create " + workDir);
        }
        File arsc = new File(workDir, ApkXmlCatalog.ARSC);
        arsc.delete();
        ApkXmlCatalog catalog = ApkXmlCatalog.open(apk, false);
        if (catalog.extractResourceTable(arsc) == null || !arsc.isFile() || arsc.length() == 0) {
            throw new IOException("Cannot extract resources.arsc from " + apk.getName());
        }
        return new ApkStringsTranslator(apk, ArscData.load(arsc, apk, ApkXmlCatalog.ARSC));
    }

    /** Package that actually owns the string resources, usually {@code com.example.app}. */
    public String primaryStringPackage() {
        for (PackageBlock pkg : data.table.listPackages()) {
            try {
                if (pkg.getSpecTypePair("string") != null) return pkg.getName();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Every language the APK already carries strings for, in qualifier form.
     *
     * <p>Only configs that actually hold at least one non-null entry count, otherwise an
     * APK that merely reserves a config would show up as "already translated".
     */
    public List<String> existingQualifiers() {
        Set<String> out = new LinkedHashSet<>();
        String pkgName = primaryStringPackage();
        if (pkgName == null) return new ArrayList<>(out);
        for (SpecTypePair spec : data.typesOf(pkgName)) {
            if (spec == null || !"string".equals(safeTypeName(spec))) continue;
            for (TypeBlock tb : data.configsOf(pkgName, "string")) {
                try {
                    if (!tb.isEmpty()) out.add(Locales.normalize(tb.getQualifiers()));
                } catch (Exception ignored) {
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String safeTypeName(SpecTypePair spec) {
        try {
            return spec.getTypeName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds the editable row list.
     *
     * @param sourceQualifier config to read from, "" for the default one
     * @param targetQualifier config to write into
     * @param onlyMissing     drop rows that already have a value in the target config
     * @param skipFormatted   drop values that machine translation would corrupt
     */
    public List<TranslateRow> rows(String sourceQualifier, String targetQualifier,
                                   boolean onlyMissing, boolean skipFormatted) {
        List<TranslateRow> out = new ArrayList<>();
        String src = Locales.normalize(sourceQualifier);
        String dst = Locales.normalize(targetQualifier);
        for (ResourceEntry re : data.stringEntries("")) {
            if (re == null) continue;
            String key = safeName(re);
            if (key.isEmpty()) continue;
            String source = valueFor(re, src);
            if (source == null) continue;
            if (source.trim().isEmpty()) continue;
            if (skipFormatted && TranslateRow.looksFormatted(source)) continue;
            String existing = valueFor(re, dst);
            if (onlyMissing && existing != null && !existing.trim().isEmpty()) continue;
            TranslateRow row = new TranslateRow(key, safeType(re), src, dst, source, existing);
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
            return "string";
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

    /** @return the value stored in the given config, or {@code null} when absent. */
    private String valueFor(ResourceEntry re, String qualifier) {
        String wanted = Locales.normalize(qualifier);
        try {
            for (ArscData.ConfigValue cv : data.configValues(re)) {
                if (Locales.normalize(cv.qualifiers).equals(wanted)) return cv.value;
            }
        } catch (Exception ignored) {
        }
        if (wanted.isEmpty()) return null;
        return null;
    }

    /**
     * Writes every applicable row into the target config, creating the config when the APK
     * has never shipped that language.
     *
     * @return how many entries were written; the table is only valid to save afterwards.
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
        ResourceEntry re = row.getResource() != null ? row.getResource() : findResource(row.key);
        if (re == null) return false;
        try {
            Entry entry = targetEntry(re, row.targetQualifier);
            if (entry == null) return false;
            entry.setValueAsString(row.getTranslation());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves the arsc entry a translation must go into, creating the locale's
     * {@code TypeBlock} and the entry itself when they do not exist yet.
     */
    public Entry targetEntry(ResourceEntry re, String targetQualifier) {
        PackageBlock pkg = data.packageByName(re.getPackageName());
        if (pkg == null) return null;
        SpecTypePair spec = pkg.getSpecTypePair(re.getType());
        if (spec == null) return null;
        TypeBlock block = spec.getOrCreateTypeBlock(Locales.normalize(targetQualifier));
        if (block == null) return null;
        return block.getOrCreateEntry(re.getName());
    }

    private ResourceEntry findResource(String key) {
        ResourceEntry cached = byName.get(key);
        if (cached != null) return cached;
        // getResources() hands back an Iterator, so this loop cannot be a for-each.
        Iterator<ResourceEntry> resources = data.table.getResources();
        while (resources.hasNext()) {
            ResourceEntry re;
            try {
                re = resources.next();
            } catch (Exception e) {
                continue;
            }
            if (re == null || !"string".equals(safeType(re))) continue;
            if (key.equals(safeName(re))) {
                byName.put(key, re);
                return re;
            }
        }
        return null;
    }

    /**
     * Validates and writes the staged table. The table is serialised, re-parsed as a sanity
     * check and only then swapped in, so a bad write cannot destroy the extracted copy.
     */
    public void save() throws IOException {
        data.save();
    }

    /** Guess for the default UI language, used to preselect the source config. */
    public String guessSourceQualifier() {
        List<String> qualifiers = existingQualifiers();
        if (qualifiers.contains("en")) return "en";
        String device = Locale.getDefault().toString();
        String bcp = device.replace('_', '-');
        for (String qualifier : qualifiers) {
            String guess = Locales.guessBcp47(qualifier);
            if (guess != null && guess.equalsIgnoreCase(bcp)) return qualifier;
        }
        for (String qualifier : qualifiers) {
            if (!qualifier.isEmpty()) return qualifier;
        }
        return "";
    }

    /** BCP-47 tag for a qualifier, for handing to the online engines. */
    public static String bcp47For(String qualifier) {
        String bcp = Locales.guessBcp47(qualifier);
        return bcp == null || bcp.isEmpty() ? "en" : bcp;
    }
}
