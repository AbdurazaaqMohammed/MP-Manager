package io.github.abdurazaaqmohammed.util.archive;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Entry-name charset handling for zip archives, the piece that decides whether
 * legacy Chinese filenames read as 文件.txt or as mojibake.
 *
 * <p>The zip specification leaves non-UTF-8 names in an undefined "legacy"
 * charset. Western tools wrote CP437; Chinese archivers (WinRAR, 好压, 360zip,
 * Bandizip on a Chinese Windows) wrote GBK. Nothing in the archive says which.
 * zip4j decodes every unflagged name with one archive-wide charset (UTF-8 by
 * default — the flag itself is ignored whenever a charset is set), so a legacy
 * GBK name browses as U+FFFD garbage and is written to disk as that garbage on
 * extraction. The fallback parser in {@link ZipCompat} used CP437, which is
 * equally wrong for GBK bytes. Both had to be fixed together, with one
 * decision per entry, so listing, opening, extracting and mutating all agree
 * on the same name for the same entry.
 *
 * <p>The per-entry heuristic: honour the UTF-8 flag; treat pure ASCII (same in
 * every charset) separately; accept any strictly valid UTF-8 next, so unflagged
 * unicode — accents, emoji, CJK written by tools that skip the flag — survives
 * intact; accept GBK/Big5 only with strong evidence (strictly valid double-byte
 * pairs, a CJK reading, and at least one pair in the Hanzi area, trail
 * >= 0xA1 — Western names like "cafés.txt" form one coincidental pair whose
 * trail never reaches that area, and keep their CP437 reading); anything else
 * keeps the historical CP437 reading (ISO-8859-1 where Android lacks CP437).
 * Unicode names flagged as UTF-8 are always preserved exactly.
 */
public final class ZipCharset {

    /**
     * What a caller that can only apply ONE charset to a whole archive (zip4j)
     * should do with it.
     */
    public enum Plan {
        /**
         * Every name is pure ASCII: zip4j's decode is identical in every
         * charset, keep it untouched.
         */
        DEFAULT,
        /**
         * The non-ASCII names are UTF-8 (flagged, or written without the
         * flag): zip4j must be given UTF-8 explicitly, because its unflagged
         * fallback is CP437, which turns "café" into "caf├⌐".
         */
        UTF8,
        /**
         * Every legacy (unflagged) non-ASCII name decodes as GBK and no UTF-8
         * name exists: giving zip4j the GBK charset decodes the whole archive
         * correctly (ASCII is unaffected, and the flag is overridden but would
         * have been UTF-8-decoded to the same ASCII bytes anyway).
         */
        GBK,
        /**
         * Legacy Traditional-Chinese (Big5) archive with no other non-ASCII
         * family present: zip4j gets the Big5 charset.
         */
        BIG5,
        /**
         * Every legacy non-ASCII name keeps the historical CP437/ISO-8859-1
         * reading and no UTF-8 name exists: zip4j gets that single-byte
         * charset, which round-trips the bytes instead of producing
         * replacement garbage.
         */
        LATIN1,
        /**
         * Archive where no single charset decodes every entry (legacy GBK
         * names beside UTF-8 names, GBK beside Big5, Unicode Path extra
         * fields...): zip4j cannot be used for name-sensitive work as-is —
         * callers fall back to the per-entry aware ZipCompat paths, or correct
         * zip4j's header names by offset when it must run anyway.
         */
        MIXED
    }

    private static final Charset GBK = getSupported("GBK");
    /** Big5 for legacy Traditional-Chinese archives, checked the same way. */
    private static final Charset BIG5 = getSupported("Big5");
    /** IBM437/CP437 only — never other IBM code pages (IBM037 is EBCDIC). */
    private static final Charset FALLBACK_LEGACY = firstSupported() != null
            ? firstSupported() : StandardCharsets.ISO_8859_1;

    private static Charset firstSupported() {
        for (String name : new String[]{"IBM437", "CP437"}) {
            Charset cs = getSupported(name);
            if (cs != null) return cs;
        }
        return null;
    }

    /**
     * User override for the legacy charset, or null for auto-detection.
     * The settings layer can point this at a charset from preferences; tests
     * use it to pin behaviour on JVMs whose charset tables differ.
     */
    private static volatile Charset legacyOverride;

    private ZipCharset() {
    }

    /** Decodes one central-directory name with the per-entry heuristic. */
    public static String decodeName(byte[] nameBytes, boolean utf8Flag) {
        String name = new String(nameBytes, charsetFor(nameBytes, utf8Flag));
        name = name.replace('\\', '/');
        int nul = name.indexOf('\0');
        return nul >= 0 ? name.substring(0, nul) : name;
    }

    /**
     * The charset the heuristic picks for one name (exposed for tests and plan
     * scans).
     *
     * <p>Ordering: the UTF-8 flag wins; pure ASCII is charset-neutral; a
     * strictly valid UTF-8 reading wins next, so unflagged unicode — accents,
     * emoji, CJK written by tools that skip the flag — survives intact; GBK
     * and Big5 then need strong evidence — strictly valid double-byte pairs,
     * a CJK reading, AND at least one pair in the Hanzi/symbol area (trail
     * byte >= 0xA1), because Western legacy names like "cafés.txt" or
     * "résumé.txt" can form one coincidental pair whose trail (0x40-0x7E)
     * never reaches that area; anything else keeps the historical CP437 /
     * ISO-8859-1 reading.
     */
    public static Charset charsetFor(byte[] nameBytes, boolean utf8Flag) {
        if (utf8Flag) return StandardCharsets.UTF_8;
        if (isPureAscii(nameBytes)) return StandardCharsets.US_ASCII;
        if (isStrictlyEncodable(StandardCharsets.UTF_8, nameBytes)) return StandardCharsets.UTF_8;
        Charset override = legacyOverride;
        if (override != null) return override;
        String gbk = tryDecode(GBK, nameBytes);
        if (gbk != null && containsCjk(gbk) && hasHanziAreaPair(nameBytes)) return GBK;
        String big5 = tryDecode(BIG5, nameBytes);
        if (big5 != null && containsCjk(big5) && hasHanziAreaPair(nameBytes)) return BIG5;
        return legacyCharset();
    }

    /**
     * True when at least one double-byte pair uses a trail byte in the
     * 0xA1-0xFE range — the GBK/Big5 area that carries Hanzi and CJK
     * punctuation. Coincidental pairs formed by Western legacy bytes (accent
     * followed by a letter, trail 0x40-0x7E) never reach it.
     */
    private static boolean hasHanziAreaPair(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            int lead = bytes[i] & 0xFF;
            int trail = bytes[i + 1] & 0xFF;
            if (lead >= 0x81 && lead <= 0xFE && trail >= 0xA1 && trail <= 0xFE) return true;
        }
        return false;
    }

    /**
     * Classifies a whole archive by scanning its decoded central-directory
     * entries. Pure-ASCII names never constrain the result; UTF-8 names
     * (flagged or written without the flag) count together in the UTF-8
     * bucket.
     */
    public static Plan planFor(List<ZipCompat.Entry> entries) {
        boolean sawGbk = false;
        boolean sawBig5 = false;
        boolean sawLatin = false;
        boolean sawUtf8 = false;
        for (ZipCompat.Entry e : entries) {
            if (e.unicodePathName) {
                // zip4j ignores the Unicode Path field, so its names would
                // diverge from the per-entry decode even for otherwise
                // unambiguous archives; force per-entry handling.
                return Plan.MIXED;
            }
            if (!e.nameNonAscii) continue;
            Charset cs = e.nameCharset;
            if (cs == StandardCharsets.UTF_8) {
                sawUtf8 = true;
            } else if (cs == GBK) {
                sawGbk = true;
            } else if (cs == BIG5) {
                sawBig5 = true;
            } else if (cs != StandardCharsets.US_ASCII) {
                sawLatin = true;
            }
        }
        if (!sawUtf8 && !sawGbk && !sawBig5 && !sawLatin) return Plan.DEFAULT;
        // One family alone maps to that family's charset for zip4j; anything
        // else cannot be decoded with one charset and needs the per-entry
        // paths.
        if (sawUtf8 && !sawGbk && !sawBig5 && !sawLatin) return Plan.UTF8;
        if (sawGbk && !sawUtf8 && !sawBig5 && !sawLatin) return Plan.GBK;
        if (sawBig5 && !sawUtf8 && !sawGbk && !sawLatin) return Plan.BIG5;
        if (sawLatin && !sawUtf8 && !sawGbk && !sawBig5) return Plan.LATIN1;
        return Plan.MIXED;
    }

    /**
     * Classifies the archive on disk. A central directory this parser cannot
     * read leaves the plan at DEFAULT so the zip4j path stays untouched;
     * zip4j is more forgiving and its failure handling is unchanged.
     *
     * <p>Plans are memoized per archive path and size/mtime stamp — per-entry
     * operations call this for every open, and re-reading a large central
     * directory each time would double their cost. Mutating an archive changes
     * its stamp, so stale plans never survive a modification.
     */
    public static Plan planFor(File zip) {
        if (zip == null) return Plan.DEFAULT;
        String key = zip.getAbsolutePath() + "#" + zip.lastModified() + "#" + zip.length();
        CachedPlan cached = PLAN_CACHE.get(key);
        if (cached != null) return cached.plan;
        Plan plan = computePlan(zip);
        PLAN_CACHE.put(key, new CachedPlan(plan));
        return plan;
    }

    private static Plan computePlan(File zip) {
        try {
            return planFor(ZipCompat.centralDirectoryFor(zip));
        } catch (IOException | RuntimeException e) {
            return Plan.DEFAULT;
        }
    }

    private static final int MAX_PLAN_CACHE = 32;

    private static final java.util.Map<String, CachedPlan> PLAN_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, CachedPlan>(
                    16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedPlan> eldest) {
                    return size() > MAX_PLAN_CACHE;
                }
            });

    private static final class CachedPlan {
        final Plan plan;

        CachedPlan(Plan plan) {
            this.plan = plan;
        }
    }

    /**
     * The single archive-wide charset zip4j should be given for a plan, or
     * null to leave zip4j at its own default. MIXED maps to null: zip4j cannot
     * handle those archives correctly and callers must not use it for
     * name-sensitive work.
     */
    public static Charset charsetForPlan(Plan plan) {
        switch (plan) {
            case UTF8: return StandardCharsets.UTF_8;
            case GBK: return GBK != null ? GBK : StandardCharsets.ISO_8859_1;
            case BIG5: return BIG5 != null ? BIG5 : StandardCharsets.ISO_8859_1;
            case LATIN1: return legacyCharset();
            default: return null;
        }
    }

    /**
     * Opens a zip4j handle for {@code zip} with the archive's detected legacy
     * charset applied. Only plans with one unambiguous archive-wide charset
     * are adjusted; DEFAULT and MIXED keep zip4j's own default.
     */
    public static net.lingala.zip4j.ZipFile apply(net.lingala.zip4j.ZipFile zf, Plan plan) {
        Charset cs = charsetForPlan(plan);
        if (cs != null) {
            try {
                zf.setCharset(cs);
            } catch (Exception ignored) {
            }
        }
        return zf;
    }

    /** Convenience overload that classifies the archive itself. */
    public static net.lingala.zip4j.ZipFile apply(net.lingala.zip4j.ZipFile zf, File zip) {
        return apply(zf, planFor(zip));
    }

    /** The legacy fallback charset (CP437 where available, else ISO-8859-1). */
    public static Charset legacyCharset() {
        Charset override = legacyOverride;
        return override != null ? override : FALLBACK_LEGACY;
    }

    /** True when the platform can decode GBK (Android ICU and desktop JVMs both ship it). */
    public static boolean isGbkSupported() {
        return GBK != null;
    }

    /** Pins the legacy charset used for unflagged names; null restores auto-detection. */
    public static void setLegacyOverride(Charset override) {
        legacyOverride = override;
    }

    private static String tryDecode(Charset cs, byte[] bytes) {
        if (cs == null || bytes.length == 0) return null;
        try {
            return strictDecoder(cs).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static boolean isStrictlyEncodable(Charset cs, byte[] bytes) {
        return tryDecode(cs, bytes) != null;
    }

    private static CharsetDecoder strictDecoder(Charset cs) {
        return cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private static boolean isPureAscii(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 0) return false;
        }
        return true;
    }

    /**
     * CJK-ish characters: the ideograph blocks, CJK punctuation, fullwidth
     * forms, kana and Hangul syllables. Any of these in a decoded legacy name
     * is a strong signal of an East-Asian archive.
     */
    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x1100 && c <= 0x11FF)         // Hangul Jamo
                    || (c >= 0x2E80 && c <= 0x2EFF)   // CJK Radicals
                    || (c >= 0x3000 && c <= 0x303F)   // CJK punctuation
                    || (c >= 0x3040 && c <= 0x30FF)   // Kana
                    || (c >= 0x3130 && c <= 0x318F)   // Hangul Compatibility Jamo
                    || (c >= 0x3400 && c <= 0x4DBF)   // CJK Extension A
                    || (c >= 0x4E00 && c <= 0x9FFF)   // CJK Unified Ideographs
                    || (c >= 0xAC00 && c <= 0xD7AF)   // Hangul syllables
                    || (c >= 0xF900 && c <= 0xFAFF)   // CJK Compatibility Ideographs
                    || (c >= 0xFF00 && c <= 0xFFEF)) { // Fullwidth forms
                return true;
            }
        }
        return false;
    }

    private static Charset getSupported(String... names) {
        for (String name : names) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
