package io.github.abdurazaaqmohammed.features.apk.translate;

import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.util.Locale;

/**
 * One string entry of the config currently being translated.
 *
 * <p>Deliberately single-config: MT copies a config to create a language pack, so the text
 * being translated and the text being replaced live in the <em>same</em> place. That removes the
 * source/target pair my first version carried and matches what the user actually sees - a value
 * that still holds the original language until an engine or the human replaces it.
 */
public final class TranslateRow {

    /** How the current translation was produced. Drives the badge shown on the row. */
    public enum Origin {
        /** Nothing staged; the value is whatever the config holds. */
        NONE,
        /** Typed by the user. */
        MANUAL,
        /** Whole-string hit in the offline glossary. */
        GLOSSARY_PHRASE,
        /** Partial, token-by-token hit in the offline glossary. */
        GLOSSARY_PARTIAL,
        /** Returned by the online endpoint. */
        ONLINE
    }

    public final String key;
    public final String type;
    /** Qualifier of the config being edited, "" for the default one. */
    public final String configQualifier;
    /** The value as it stands in the config, i.e. the text to translate. */
    public final String current;

    private String translation;
    private Origin origin;
    private boolean selected = true;
    private ResourceEntry resource;
    private Entry targetEntry;

    public TranslateRow(String key, String type, String configQualifier, String current) {
        this.key = key;
        this.type = type;
        this.configQualifier = configQualifier == null ? "" : configQualifier;
        this.current = current == null ? "" : current;
        this.translation = this.current;
        this.origin = Origin.NONE;
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

    /** Drops the staged text and shows the config's own value again. */
    public void revert() {
        this.translation = current;
        this.origin = Origin.NONE;
    }

    /** True when the staged text differs from what the config holds. */
    public boolean isDirty() {
        return !current.equals(translation);
    }

    /** True when the row is worth writing: ticked, non-blank and actually changed. */
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

    public void setResource(ResourceEntry resource) {
        this.resource = resource;
    }

    Entry getTargetEntry() {
        return targetEntry;
    }

    void setTargetEntry(Entry entry) {
        this.targetEntry = entry;
    }

    /**
     * Values a machine translator must not touch: format specifiers, positional placeholders,
     * XML/HTML markup, bare URLs and dotted identifiers.
     *
     * <p>These are skipped by default rather than translated, because a mangled {@code %1$s}
     * or a stripped tag produces a string that crashes or blanks the UI at runtime.
     */
    public static boolean looksFormatted(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        if (v.contains("%") && v.matches("(?s).*%\\d*\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%].*")) return true;
        if (v.contains("{") && v.matches("(?s).*\\{\\d+(?:,[^}]*)?\\}.*")) return true;
        if (v.startsWith("<") && v.endsWith(">")) return true;
        if (v.matches("(?i)https?://\\S+")) return true;
        if (v.matches("[a-z0-9_]+(\\.[a-z0-9_]+)+")) return true;
        if (v.matches("(?i)\\S+\\.(png|jpg|jpeg|gif|webp|svg|xml|json|txt|zip|apk|so|dex|ttf|mp4|mp3)")) {
            return true;
        }
        return false;
    }

    /** Lower-cased search key covering the entry name and its value. */
    public String searchKey() {
        return (key + '\n' + current).toLowerCase(Locale.ROOT);
    }
}
