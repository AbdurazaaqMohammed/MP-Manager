package io.github.abdurazaaqmohammed.plugins.themes;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure content comparison behind the no-op refresh fast path in
 * {@code ThemePacks#importAndSelect}: decides whether a freshly parsed
 * provider row (plus its staged, fully downloaded images) describes a theme
 * the host already stores, so the import can return without a commit,
 * revision bump, generation bump or change notification. No Android imports
 * and no locks: {@link #sameBytes} reads two bounded local files (wallpaper
 * is capped at {@link ThemePackContracts#MAX_WALLPAPER_BYTES}, preview at
 * {@link ThemePackContracts#MAX_PREVIEW_BYTES}; provider IO is already done
 * by the time these run) and everything else is plain map work.
 *
 * <p>Comparison rules — both sides are host-parsed
 * {@link ThemePackStore.StoredSnapshot}s:
 * <ul>
 *   <li>{@code id} is included: a renamed pack id is a real change;</li>
 *   <li>collections compare with null/empty equivalence and canonical keys
 *       (color tokens, label keys and editor-syntax categories via the
 *       {@link ThemePackParser} canonicalizers), so an alias spelling such as
 *       {@code keyword.control} vs {@code keyword} or a null-vs-empty payload
 *       cannot fake a change;</li>
 *   <li>{@code wallpaperScope} compares with null/"files" equivalence;</li>
 *   <li>image references are deliberately NOT compared by name (every staged
 *       import generates a fresh versioned name) — callers compare the actual
 *       bytes with {@link #sameBytes}, because a provider may change content
 *       behind the same endpoint URI.</li>
 *   <li>{@code signer}/{@code schema}/{@code revision} are store bookkeeping
 *       and never count as a visible change.</li>
 * </ul>
 */
final class ThemePackContentEquals {

    private ThemePackContentEquals() {
    }

    /**
     * True when both snapshots render identically apart from image file
     * names (image presence/bytes are the caller's responsibility, since
     * they involve the staged files). False for a null argument.
     */
    static boolean sameMetadata(ThemePackStore.StoredSnapshot a,
                                ThemePackStore.StoredSnapshot b) {
        if (a == null || b == null) return false;
        return eq(a.id, b.id)
                && eq(nameOrNull(a.name), nameOrNull(b.name))
                && eq(a.dark, b.dark)
                && eq(a.accent, b.accent)
                && eq(a.scrim, b.scrim)
                && canonicalColors(a.colors).equals(canonicalColors(b.colors))
                && canonicalStrings(a.strings).equals(canonicalStrings(b.strings))
                && canonicalSyntax(a.editorSyntax).equals(canonicalSyntax(b.editorSyntax))
                && normalizedScope(a.wallpaperScope)
                        .equals(normalizedScope(b.wallpaperScope));
    }

    /**
     * Chunked byte equality of two bounded local files. False when either
     * side is absent — a missing previous file forces a real commit so the
     * import repairs the store instead of fast-pathing over a hole.
     */
    static boolean sameBytes(File left, File right) {
        if (left == null || right == null || !left.isFile() || !right.isFile()) {
            return false;
        }
        if (left.length() != right.length()) return false;
        try (InputStream in = new FileInputStream(left);
             InputStream reference = new FileInputStream(right)) {
            byte[] leftBuf = new byte[8192];
            byte[] rightBuf = new byte[8192];
            int n;
            while ((n = in.read(leftBuf)) != -1) {
                int filled = 0;
                while (filled < n) {
                    int m = reference.read(rightBuf, filled, n - filled);
                    if (m == -1) return false;
                    filled += m;
                }
                for (int i = 0; i < n; i++) {
                    if (leftBuf[i] != rightBuf[i]) return false;
                }
            }
            return reference.read() == -1;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- canonicalizers ----------

    private static Map<String, Integer> canonicalColors(Map<String, Integer> in) {
        if (in == null || in.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : in.entrySet()) {
            if (e.getValue() == null) continue;
            String key = ThemePackParser.canonicalColorToken(e.getKey());
            if (key != null) out.put(key, e.getValue());
        }
        return out;
    }

    private static Map<String, Map<String, String>> canonicalStrings(
            Map<String, Map<String, String>> in) {
        if (in == null || in.isEmpty()) return Collections.emptyMap();
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : in.entrySet()) {
            String key = ThemePackParser.canonicalLabelKey(e.getKey());
            Map<String, String> locales = e.getValue();
            if (key == null || locales == null || locales.isEmpty()) continue;
            Map<String, String> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, String> le : locales.entrySet()) {
                if (le.getValue() == null || le.getValue().isEmpty()) continue;
                cleaned.put(le.getKey(), le.getValue());
            }
            if (!cleaned.isEmpty()) out.put(key, cleaned);
        }
        return out;
    }

    private static Map<String, Integer> canonicalSyntax(Map<String, Integer> in) {
        if (in == null || in.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : in.entrySet()) {
            if (e.getValue() == null) continue;
            String key = ThemePackParser.canonicalSyntaxKey(e.getKey());
            if (key != null) out.put(key, e.getValue());
        }
        return out;
    }

    /** Unknown/absent scope collapses to "files" (the read side does too). */
    private static String normalizedScope(String raw) {
        return ThemePackContracts.isWallpaperScope(raw)
                ? raw : ThemePackContracts.WALLPAPER_SCOPE_FILES;
    }

    private static String nameOrNull(String raw) {
        return raw == null || raw.isEmpty() ? null : raw;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
