package io.github.abdurazaaqmohammed.features.apk.translate;

import com.reandroid.arsc.model.ResourceEntry;

import java.util.Locale;

/**
 * One translatable string resource: the source text pulled from the chosen config, the
 * translation staged for the target config, and the live {@link Entry} handles needed to
 * commit it.
 *
 * <p>The arsc entry handles are resolved lazily and cached, because building them for a
 * large APK up front is both slow and pointless when most rows are never touched.
 */
public final class TranslateRow {

    /** How the current translation was produced. Drives the badge shown on the row. */
    public enum Origin {
        /** Nothing staged yet. */
        NONE,
        /** Typed by the user. */
        MANUAL,
        /** Whole-string hit in the offline glossary. */
        GLOSSARY_PHRASE,
        /** Partial, token-by-token hit in the offline glossary. */
        GLOSSARY_PARTIAL,
        /** Returned by the online endpoint. */
        ONLINE,
        /** Already present in the target config, loaded for review. */
        EXISTING
    }

    public final String key;
    /** Resource type, always {@code string} here but kept for display. */
    public final String type;
    /** Qualifier of the config the source text came from, "" for the default one. */
    public final String sourceQualifier;
    /** Qualifier of the config the translation will be written to. */
    public final String targetQualifier;
    public final String source;
    /** Existing value in the target config, or null when the target config has none yet. */
    public final String existing;

    private String translation;
    private Origin origin;
    private boolean selected = true;
    /** Live arsc handle for this key, so applying never has to look it up again. */
    private ResourceEntry resource;

    public TranslateRow(String key, String type, String sourceQualifier, String targetQualifier,
                        String source, String existing) {
        this.key = key;
        this.type = type;
        this.sourceQualifier = sourceQualifier == null ? "" : sourceQualifier;
        this.targetQualifier = targetQualifier == null ? "" : targetQualifier;
        this.source = source == null ? "" : source;
        this.existing = existing;
        this.translation = existing != null ? existing : "";
        this.origin = existing != null ? Origin.EXISTING : Origin.NONE;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String text, Origin origin) {
        this.translation = text == null ? "" : text;
        this.origin = origin == null ? Origin.MANUAL : origin;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void clearTranslation() {
        this.translation = "";
        this.origin = Origin.NONE;
    }

    /** True when the staged text differs from what the target config holds right now. */
    public boolean isDirty() {
        String current = existing == null ? "" : existing;
        return !current.equals(translation);
    }

    /** True when the staged text is worth writing, i.e. non-blank and actually changed. */
    public boolean isApplicable() {
        return selected && !translation.trim().isEmpty() && isDirty();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean value) {
        this.selected = value;
    }

    public ResourceEntry getResource() {
        return resource;
    }

    void setResource(ResourceEntry resource) {
        this.resource = resource;
    }

    /** Short human label for the row badge. */
    public String originTag() {
        return switch (origin) {
            case MANUAL -> "manual";
            case GLOSSARY_PHRASE -> "exact";
            case GLOSSARY_PARTIAL -> "partial";
            case ONLINE -> "online";
            case EXISTING -> "current";
            case NONE -> "";
        };
    }

    /**
     * Detects values that must not go through a machine translator: format specifiers,
     * XML/HTML markup, and bare placeholders. Translating these silently corrupts the APK.
     */
    public static boolean looksFormatted(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        if (v.indexOf('%') >= 0 && v.matches(".*%\\d*\\$?[sdfxXoeEgGnaAfF].*")) return true;
        if (v.indexOf('{') >= 0 && v.matches(".*\\{\\d+\\}.*")) return true;
        if (v.startsWith("<") && v.endsWith(">")) return true;
        // Pure URLs, paths, package names and single tokens carry no meaning to translate.
        if (v.matches("(?i)https?://\\S+")) return true;
        if (v.matches("[a-z0-9_]+(\\.[a-z0-9_]+)+")) return true;
        if (v.matches("\\S+\\.(png|jpg|jpeg|gif|webp|svg|xml|json|txt|zip|apk|so|dex|ttf|mp4|mp3)")) return true;
        return false;
    }

    /** Lower-cased search key covering name and source text, for the filter box. */
    public String searchKey() {
        return (key + '\n' + source).toLowerCase(Locale.ROOT);
    }
}
