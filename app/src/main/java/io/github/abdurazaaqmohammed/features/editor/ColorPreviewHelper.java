package io.github.abdurazaaqmohammed.features.editor;

import android.graphics.Color;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Helper to detect Hex color strings in code lines, allocate dynamic Color IDs
 * in Sora Editor's ColorScheme, and determine high-contrast foreground colors.
 */
public class ColorPreviewHelper {

    // Matches #RGB, #ARGB, #RRGGBB, #AARRGGBB with word boundary / quotes / brackets
    // Examples: "#ffffff", "#008577", "#ff000000", "#FFF"
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile(
            "(?<=[\"'=,:\\s(]|^)#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?=[\"';,>)\\s]|$)"
    );

    // Reserve dynamic color IDs starting from 10000 to avoid conflicting with TextMate/Editor built-ins
    private static final int BASE_COLOR_ID = 10000;
    private static final int MAX_COLOR_ID = 260000; // Well below 19-bit limit (524287)
    private static final AtomicInteger nextColorId = new AtomicInteger(BASE_COLOR_ID);

    private static final Map<Integer, Integer> colorToIdMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> idToColorMap = new ConcurrentHashMap<>();

    public static final int COLOR_ID_TEXT_WHITE = 9998;
    public static final int COLOR_ID_TEXT_BLACK = 9999;

    public static void initPredefinedColors(EditorColorScheme scheme) {
        if (scheme == null) return;
        scheme.setColor(COLOR_ID_TEXT_WHITE, 0xFFFFFFFF);
        scheme.setColor(COLOR_ID_TEXT_BLACK, 0xFF000000);
    }

    public static Pattern getHexColorPattern() {
        return HEX_COLOR_PATTERN;
    }

    /**
     * Parses hex color string like "#ffffff" or "#fff" or "#ff008577"
     * Returns parsed ARGB color int, or null if invalid.
     */
    public static Integer parseHexColor(String hex) {
        if (hex == null || !hex.startsWith("#")) return null;
        try {
            String raw = hex.substring(1);
            if (raw.length() == 3) {
                // #RGB -> #RRGGBB
                char r = raw.charAt(0);
                char g = raw.charAt(1);
                char b = raw.charAt(2);
                raw = "" + r + r + g + g + b + b;
                return 0xFF000000 | (int) Long.parseLong(raw, 16);
            } else if (raw.length() == 4) {
                // #ARGB -> #AARRGGBB
                char a = raw.charAt(0);
                char r = raw.charAt(1);
                char g = raw.charAt(2);
                char b = raw.charAt(3);
                raw = "" + a + a + r + r + g + g + b + b;
                return (int) Long.parseLong(raw, 16);
            } else if (raw.length() == 6) {
                // #RRGGBB
                return 0xFF000000 | (int) Long.parseLong(raw, 16);
            } else if (raw.length() == 8) {
                // #AARRGGBB
                return (int) Long.parseLong(raw, 16);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Allocates or retrieves an existing color ID for the given parsed color,
     * ensuring it is registered on the EditorColorScheme.
     */
    public static int getOrCreateColorId(int color, EditorColorScheme scheme) {
        Integer existingId = colorToIdMap.get(color);
        if (existingId != null) {
            if (scheme != null && scheme.getColor(existingId) != color) {
                scheme.setColor(existingId, color);
            }
            return existingId;
        }

        int newId = nextColorId.getAndIncrement();
        if (newId > MAX_COLOR_ID) {
            // Wrap around safely
            nextColorId.set(BASE_COLOR_ID);
            newId = BASE_COLOR_ID;
        }

        colorToIdMap.put(color, newId);
        idToColorMap.put(newId, color);

        if (scheme != null) {
            scheme.setColor(newId, color);
        }
        return newId;
    }

    /**
     * Determine whether white text or black text is better readable over this background.
     */
    public static int getContrastTextColorId(int backgroundColor) {
        int r = (backgroundColor >> 16) & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = backgroundColor & 0xFF;
        // Standard luminance calculation
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance < 0.55 ? COLOR_ID_TEXT_WHITE : COLOR_ID_TEXT_BLACK;
    }

    /**
     * Synchronize all registered dynamic color IDs into a newly set EditorColorScheme.
     */
    public static void syncScheme(EditorColorScheme scheme) {
        if (scheme == null) return;
        initPredefinedColors(scheme);
        for (Map.Entry<Integer, Integer> entry : idToColorMap.entrySet()) {
            scheme.setColor(entry.getKey(), entry.getValue());
        }
    }
}
