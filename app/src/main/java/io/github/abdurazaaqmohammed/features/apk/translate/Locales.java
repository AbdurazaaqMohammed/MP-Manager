package io.github.abdurazaaqmohammed.features.apk.translate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target/source language table for the XML translation mode.
 *
 * <p>Two different identifiers are needed for every language and mixing them up is the
 * classic source of "translation silently did nothing" bugs:
 * <ul>
 *   <li>{@code qualifier} - the Android resource qualifier, i.e. what ends up in the
 *       {@code TypeBlock} inside {@code resources.arsc} ({@code zh-rCN}, {@code pt-rBR},
 *       {@code fil}). This is the only thing that actually decides which config the
 *       translated value lands in.</li>
 *   <li>{@code bcp47} - the language tag understood by the online translation APIs
 *       ({@code zh-CN}, {@code pt-BR}, {@code tl}).</li>
 * </ul>
 */
public final class Locales {

    /** One selectable language. */
    public static final class Lang {
        public final String qualifier;
        public final String bcp47;
        public final String label;

        Lang(String qualifier, String bcp47, String label) {
            this.qualifier = qualifier;
            this.bcp47 = bcp47;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Map<String, Lang> BY_QUALIFIER = new LinkedHashMap<>();
    private static final Map<String, Lang> BY_BCP47 = new LinkedHashMap<>();

    private static void add(String qualifier, String bcp47, String label) {
        Lang lang = new Lang(qualifier, bcp47, label);
        BY_QUALIFIER.put(qualifier, lang);
        // The default entry is also the answer for "en", so register it under both.
        if (!BY_BCP47.containsKey(bcp47.toLowerCase())) {
            BY_BCP47.put(bcp47.toLowerCase(), lang);
        }
    }

    static {
        add("", "en", "English (default)");
        add("en", "en", "English");
        add("en-rGB", "en-GB", "English (UK)");
        add("zh-rCN", "zh-CN", "简体中文");
        add("zh-rTW", "zh-TW", "繁體中文");
        add("ja", "ja", "日本語");
        add("ko", "ko", "한국어");
        add("fr", "fr", "Français");
        add("de", "de", "Deutsch");
        add("es", "es", "Español");
        add("pt", "pt", "Português");
        add("pt-rBR", "pt-BR", "Português (Brasil)");
        add("it", "it", "Italiano");
        add("ru", "ru", "Русский");
        add("ru-rUA", "uk", "Українська");
        add("ar", "ar", "العربية");
        add("hi", "hi", "हिन्दी");
        add("id", "id", "Bahasa Indonesia");
        add("ms", "ms", "Bahasa Melayu");
        add("th", "th", "ไทย");
        add("vi", "vi", "Tiếng Việt");
        add("tr", "tr", "Türkçe");
        add("pl", "pl", "Polski");
        add("nl", "nl", "Nederlands");
        add("sv", "sv", "Svenska");
        add("da", "da", "Dansk");
        add("fi", "fi", "Suomi");
        add("nb", "nb", "Norsk bokmål");
        add("cs", "cs", "Čeština");
        add("el", "el", "Ελληνικά");
        add("he", "he", "עברית");
        add("hu", "hu", "Magyar");
        add("ro", "ro", "Română");
        add("uk", "uk", "Українська");
        add("bn", "bn", "বাংলা");
        add("ta", "ta", "தமிழ்");
        add("fa", "fa", "فارسی");
    }

    private Locales() {
    }

    public static List<Lang> all() {
        return new ArrayList<>(BY_QUALIFIER.values());
    }

    /** @return the language for an Android resource qualifier, or the default entry. */
    public static Lang byQualifier(String qualifier) {
        if (qualifier == null) qualifier = "";
        Lang lang = BY_QUALIFIER.get(qualifier);
        return lang == null ? BY_QUALIFIER.get("") : lang;
    }

    /** @return the language for a BCP-47 tag, falling back to the default entry. */
    public static Lang byBcp47(String tag) {
        if (tag == null) tag = "";
        Lang lang = BY_BCP47.get(tag.toLowerCase());
        return lang == null ? BY_QUALIFIER.get("") : lang;
    }

    /**
     * Normalises a qualifier for comparison: case, and the {@code r} region marker, do not
     * matter to us but do matter to aapt, so they are lower-cased and trimmed only.
     */
    public static String normalize(String qualifier) {
        if (qualifier == null) return "";
        String q = qualifier.trim();
        while (q.startsWith("-")) q = q.substring(1);
        return q;
    }

    /**
     * Guesses the BCP-47 tag for a raw {@code resources.arsc} qualifier, for APKs that ship
     * locales this table does not know about. Handles {@code b+zh+Hans+CN}, {@code zh_CN},
     * {@code values-zh-rCN} and plain {@code de}.
     *
     * @return a tag usable with the online engines, or {@code null} when unrecognisable.
     */
    public static String guessBcp47(String rawQualifier) {
        if (rawQualifier == null) return null;
        String q = rawQualifier.trim();
        if (q.isEmpty()) return "en";
        int plus = q.indexOf('+');
        if (plus >= 0) {
            // BCP-47-ish private-use form, e.g. b+zh+Hans+CN
            StringBuilder sb = new StringBuilder();
            String[] parts = q.substring(plus + 1).split("\\+");
            for (String part : parts) {
                if (part.length() == 2) sb.append(part.toLowerCase());
                else if (part.length() == 4) {
                    // Script subtag: Hans -> Hans, but it never helps the APIs we call.
                    continue;
                } else if (sb.length() > 0) {
                    sb.append('-').append(part.toUpperCase());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        q = q.replace('_', '-');
        String[] segs = q.split("-");
        if (segs.length == 0 || segs[0].isEmpty()) return null;
        StringBuilder sb = new StringBuilder(segs[0].toLowerCase());
        for (int i = 1; i < segs.length; i++) {
            String seg = segs[i];
            if (seg.length() == 3 && Character.isDigit(seg.charAt(0))) continue; // rCN digit3
            sb.append('-').append(seg.length() == 2 ? seg.toUpperCase() : seg);
        }
        return sb.toString();
    }
}
