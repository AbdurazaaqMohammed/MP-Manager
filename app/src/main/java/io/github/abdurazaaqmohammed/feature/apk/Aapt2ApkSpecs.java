package io.github.abdurazaaqmohammed.feature.apk;

import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.value.ResConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.feature.apk.deepopt.AbiFilter;

/**
 * Discovers the density, language and ABI values that actually exist in an APK,
 * so the optimizer only offers meaningful choices.
 */
final class Aapt2ApkSpecs {
    private static final Pattern DENSITY =
            Pattern.compile("(?i)^(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|anydpi|nodpi|[0-9]+dpi)$");
    private static final Pattern BCP47_LANGUAGE = Pattern.compile("(?i)^b\\+[a-z0-9+]+$");
    private static final Pattern LEGACY_LANGUAGE = Pattern.compile("(?i)^[a-z]{2,3}(-r[A-Za-z0-9]{2,3})?$");
    private static final Set<String> ISO_LANGUAGES =
            new HashSet<>(Arrays.asList(Locale.getISOLanguages()));

    private static final List<String> DENSITY_ORDER = Arrays.asList(
            "ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "anydpi", "nodpi");

    private Aapt2ApkSpecs() {
    }

    static Specs scan(File apk) throws IOException {
        if (apk == null || !apk.isFile()) throw new IOException("APK is unavailable");
        LinkedHashSet<String> densities = new LinkedHashSet<>();
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        Set<String> abis = AbiFilter.getAvailableAbis(apk);

        readResourceConfigs(apk, densities, languages);
        // resources.arsc is authoritative, but some tools also leave qualified
        // files outside the table (and old APKs may be unreadable); union both.
        scanZipNames(apk, densities, languages);
        return new Specs(orderDensities(densities), languages, abis);
    }

    private static void readResourceConfigs(File apk, Set<String> densities, Set<String> languages) {
        try (ApkModule module = ApkModule.loadApkFile(apk)) {
            TableBlock table = module.getTableBlock(false);
            if (table == null) return;
            Iterator<ResConfig> iterator = table.getResConfigs();
            while (iterator.hasNext()) {
                ResConfig config = iterator.next();
                if (config == null) continue;
                ResConfig.Density density = config.getDensity();
                if (density != null && density.toString() != null) {
                    densities.add(density.toString().toLowerCase(Locale.ROOT));
                }
                String language = languageQualifier(config);
                if (language != null) languages.add(language);
            }
        } catch (Throwable ignored) {
            // Fall back to the zip entry names below.
        }
    }

    private static String languageQualifier(ResConfig config) {
        String language = config.getLanguage();
        if (language == null || language.isEmpty()) return null;
        language = language.toLowerCase(Locale.ROOT);
        String script = null;
        char[] scriptChars = config.getLocaleScriptChars();
        if (scriptChars != null && scriptChars.length > 0) {
            script = new String(scriptChars);
            script = script.substring(0, 1).toUpperCase(Locale.ROOT) + script.substring(1);
        }
        String region = config.getRegion();
        if (region != null) region = region.toUpperCase(Locale.ROOT);

        if (script != null && !script.isEmpty()) {
            return "b+" + language + "+" + script
                    + (region == null || region.isEmpty() ? "" : "+" + region);
        }
        if (region != null && !region.isEmpty()) {
            if (region.length() == 3) return "b+" + language + "+" + region;
            return language + "-r" + region;
        }
        return language;
    }

    private static void scanZipNames(File apk, Set<String> densities, Set<String> languages)
            throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("res/")) continue;
                int slash = name.indexOf('/', 4);
                if (slash < 0 || slash == 4) continue;
                String directory = name.substring(4, slash);
                parseDirectory(directory, densities, languages);
            }
        }
    }

    private static void parseDirectory(String directory, Set<String> densities, Set<String> languages) {
        String[] parts = directory.split("-");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (DENSITY.matcher(part).matches()) {
                densities.add(part.toLowerCase(Locale.ROOT));
                continue;
            }
            if (BCP47_LANGUAGE.matcher(part).matches()) {
                languages.add(part.toLowerCase(Locale.ROOT));
                continue;
            }
            if (!isIsoLanguage(part)) continue;
            String qualifier = part.toLowerCase(Locale.ROOT);
            if (i + 1 < parts.length && parts[i + 1].matches("[rR][A-Za-z0-9]{2,3}")) {
                qualifier += "-r" + parts[++i].substring(1).toUpperCase(Locale.ROOT);
            }
            languages.add(qualifier);
        }
    }

    private static boolean isIsoLanguage(String value) {
        return value.length() >= 2 && value.length() <= 3
                && ISO_LANGUAGES.contains(value.toLowerCase(Locale.ROOT));
    }

    private static Set<String> orderDensities(Collection<String> values) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) byLower.put(value.toLowerCase(Locale.ROOT), value);
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String name : DENSITY_ORDER) {
            String value = byLower.remove(name);
            if (value != null) ordered.add(value);
        }
        List<String> numeric = new ArrayList<>();
        for (String name : byLower.keySet()) {
            if (name.matches("[0-9]+dpi")) numeric.add(name);
        }
        numeric.sort((left, right) -> Integer.compare(dpiOf(left), dpiOf(right)));
        for (String name : numeric) ordered.add(byLower.remove(name));
        ordered.addAll(byLower.values());
        return ordered;
    }

    private static int dpiOf(String value) {
        try {
            return Integer.parseInt(value.substring(0, value.length() - 3));
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    /** Closest standard density bucket for a device density. */
    static String densityBucket(int dpi) {
        if (dpi >= 640) return "xxxhdpi";
        if (dpi >= 480) return "xxhdpi";
        if (dpi >= 320) return "xhdpi";
        if (dpi >= 240) return "hdpi";
        if (dpi >= 213) return "tvdpi";
        if (dpi >= 160) return "mdpi";
        return "ldpi";
    }

    static List<String> split(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String part : value.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) result.add(token);
        }
        return result;
    }

    static final class Specs {
        final Set<String> densities;
        final Set<String> languages;
        final Set<String> abis;

        Specs(Collection<String> densities, Collection<String> languages, Collection<String> abis) {
            this.densities = sorted(densities);
            this.languages = sorted(languages);
            this.abis = sorted(abis);
        }

        private static Set<String> sorted(Collection<String> values) {
            TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            result.addAll(values);
            return Collections.unmodifiableSet(new LinkedHashSet<>(result));
        }

        /** Values in {@code selected} that do not exist in {@code available}. */
        List<String> missing(Collection<String> selected, Set<String> available) {
            List<String> missing = new ArrayList<>();
            if (selected == null) return missing;
            for (String value : selected) {
                if (value.isEmpty() || containsIgnoreCase(available, value)) continue;
                missing.add(value);
            }
            return missing;
        }

        boolean containsIgnoreCase(Set<String> values, String candidate) {
            for (String value : values) {
                if (value.equalsIgnoreCase(candidate)) return true;
            }
            return false;
        }

        boolean isEmpty() {
            return densities.isEmpty() && languages.isEmpty() && abis.isEmpty();
        }
    }
}
