package io.github.abdurazaaqmohammed.plugins.api;

/**
 * Shared tool category names. Single source used by the host catalog and
 * downloadable packs (replaces the host-only ToolRegistry constants).
 */
public final class ToolCategories {

    public static final String NETWORK = "Wi-Fi & Network";
    public static final String STORAGE = "Storage & Apps";
    public static final String DEVICE = "Device & Hardware";
    public static final String MATH = "Math & Finance";
    public static final String TIME = "Time & Productivity";
    public static final String TEXT = "Text & Security";
    public static final String MEDIA = "Media & Sound";
    public static final String RAND = "Random";
    public static final String GENERAL = "General";

    private ToolCategories() {
    }

    public static String[] inOrder() {
        return new String[]{NETWORK, STORAGE, DEVICE, MATH, TIME, TEXT, MEDIA, RAND, GENERAL};
    }
}
