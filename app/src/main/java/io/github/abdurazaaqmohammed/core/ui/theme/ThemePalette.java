package io.github.abdurazaaqmohammed.core.ui.theme;

/**
 * Immutable result of parsing one theme JSON file.
 * A palette with no declared colors is legal: it means "keep whatever the
 * platform theme provides" (Material You dynamic color on the built-in
 * light/dark themes, which declare nightMode only).
 */
public final class ThemePalette {

    public static final int ABSENT = -1;

    private final String id;
    private final String name;
    private final boolean light;
    private final String lightMode;
    private final boolean builtin;
    private final String fileName;
    private final java.util.Map<String, Integer> colors;

    ThemePalette(String id, String name, boolean light, String lightMode,
                 boolean builtin, String fileName, java.util.Map<String, Integer> colors) {
        this.id = id;
        this.name = name;
        this.light = light;
        this.lightMode = lightMode;
        this.builtin = builtin;
        this.fileName = fileName;
        this.colors = colors;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** True for nightMode "no"/"light"/"false"; false for "yes"/"dark"/"true". */
    public boolean isLight() {
        return light;
    }

    /** Raw nightMode string, or "system" when unspecified. */
    public String lightMode() {
        return lightMode;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    /** Source file name, used to delete imported themes. */
    public String fileName() {
        return fileName;
    }

    public boolean hasColors() {
        return colors != null && !colors.isEmpty();
    }

    /**
     * @return ARGB color for a role, or {@link #NO_VALUE} when this palette
     * does not declare it (caller falls back to the platform theme attr).
     */
    public int color(String role) {
        if (colors == null) return ABSENT;
        Integer v = colors.get(role);
        return v == null ? ABSENT : v;
    }
}
