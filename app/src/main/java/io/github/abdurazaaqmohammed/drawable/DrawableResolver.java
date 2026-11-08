package io.github.abdurazaaqmohammed.drawable;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;

/**
 * Turns the string values found in drawable XML ({@code #ff0000},
 * {@code 16dp}, {@code @color/accent}, {@code @7F010034},
 * {@code ?attr/colorControlNormal}, {@code @drawable/ic_back}, ...) into
 * concrete values the {@link XmlDrawable} renderer can use.
 *
 * <p>Resolution order is: the APK's own {@link Resources} (fully accurate,
 * public API, resolves aliases/configurations/theme attributes) -&gt; the
 * {@code resources.arsc} table via ARSCLib -&gt; literal parsing -&gt; matching
 * entries inside the archive. That keeps rendering working even for a bare ZIP
 * with loose XML files and no manifest.</p>
 */
final class DrawableResolver {

    private final Context context;
    private final Resources res;
    private final Resources.Theme theme;
    private final String pkg;
    private final File archive;
    private final DisplayMetrics metrics;

    private TableBlock table;
    private boolean tableTried;
    private List<ArchiveBrowseUtil.RawEntry> entries;
    private boolean entriesTried;
    private final Map<String, Drawable> drawableCache = new HashMap<>();

    DrawableResolver(Context context, Resources res, Resources.Theme theme,
                     String pkg, File archive) {
        this.context = context;
        this.res = res;
        this.theme = theme;
        this.pkg = pkg;
        this.archive = archive;
        this.metrics = context.getResources().getDisplayMetrics();
    }

    // ---------------------------------------------------------------- literals

    static Integer parseColorLiteral(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.equals("@null") || s.equals("@empty") || s.equals("null")) return Color.TRANSPARENT;
        if (!s.startsWith("#")) return null;
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Float parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("dp") || s.endsWith("dip")) {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        Float.parseFloat(s.substring(0, s.length() - (s.endsWith("dip") ? 3 : 2))),
                        currentMetrics());
            }
            if (s.endsWith("px")) return Float.parseFloat(s.substring(0, s.length() - 2));
            if (s.endsWith("sp")) {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                        Float.parseFloat(s.substring(0, s.length() - 2)), currentMetrics());
            }
            if (s.endsWith("pt")) {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT,
                        Float.parseFloat(s.substring(0, s.length() - 2)), currentMetrics());
            }
            if (s.endsWith("in")) {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN,
                        Float.parseFloat(s.substring(0, s.length() - 2)), currentMetrics());
            }
            if (s.endsWith("mm")) {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM,
                        Float.parseFloat(s.substring(0, s.length() - 2)), currentMetrics());
            }
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private final DisplayMetrics metrics() {
        return metrics;
    }

    private static DisplayMetrics currentMetrics() {
        return Resources.getSystem().getDisplayMetrics();
    }

    // --------------------------------------------------------------- resources

    /** Resolves {@code @...} / {@code ?...} to a resource id, or 0. */
    int resolveId(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.length() < 2 || (s.charAt(0) != '@' && s.charAt(0) != '?')) return 0;
        String body = s.substring(1);
        if (body.startsWith("+")) body = body.substring(1);
        if (body.equals("null") || body.equals("empty")) return 0;

        // Raw hex id, as emitted by the AXML decoder: @7F010034 / @0x7F010034.
        String hex = body.startsWith("0x") || body.startsWith("0X")
                ? body.substring(2) : body;
        if (hex.matches("(?i)^[0-9a-f]{6,8}$")) {
            try {
                return (int) Long.parseLong(hex, 16);
            } catch (NumberFormatException ignored) {
            }
        }

        String packageName = null;
        String type = null;
        String name;
        int colon = body.indexOf(':');
        if (colon >= 0) {
            packageName = body.substring(0, colon);
            body = body.substring(colon + 1);
        }
        int slash = body.indexOf('/');
        if (slash >= 0) {
            type = body.substring(0, slash);
            name = body.substring(slash + 1);
        } else {
            name = body;
        }
        if (name.isEmpty()) return 0;
        if (packageName == null) packageName = pkg;
        if (type == null || type.isEmpty()) type = s.charAt(0) == '?' ? "attr" : "drawable";

        if (res != null) {
            int id = tryIdentifier(name, type, packageName);
            if (id == 0) id = tryIdentifier(name, type, "android");
            if (id == 0) id = tryIdentifier(name, type, null);
            if (id != 0) return id;
        }
        TableBlock t = table();
        if (t != null) {
            int id = t.resolveResourceId(packageName, type, name);
            if (id == 0) {
                Iterator<PackageBlock> it = t.getPackages();
                while (it.hasNext() && id == 0) {
                    id = t.resolveResourceId(it.next().getName(), type, name);
                }
            }
            return id;
        }
        return 0;
    }

    private int tryIdentifier(String name, String type, String packageName) {
        try {
            return res.getIdentifier(name, type, packageName);
        } catch (Throwable t) {
            return 0;
        }
    }

    private TableBlock table() {
        if (tableTried) return table;
        tableTried = true;
        if (archive == null) return null;
        try (InputStream in = ArchiveBrowseUtil.openEntry(archive, "resources.arsc")) {
            table = TableBlock.load(in);
        } catch (Throwable ignored) {
            table = null;
        }
        return table;
    }

    /** The final TypedValue behind a reference (references already followed). */
    TypedValue valueOf(String raw, int depth) {
        if (raw == null || depth > 8) return null;
        String s = raw.trim();
        if (s.length() < 2 || (s.charAt(0) != '@' && s.charAt(0) != '?')) return null;
        int id = resolveId(s);
        if (id == 0) return null;
        return valueById(id, s.charAt(0) == '?', depth);
    }

    private TypedValue valueById(int id, boolean attribute, int depth) {
        TypedValue tv = new TypedValue();
        if (attribute && theme != null) {
            try {
                if (theme.resolveAttribute(id, tv, true)) return tv;
            } catch (Throwable ignored) {
            }
        }
        if (res != null) {
            try {
                res.getValue(id, tv, true);
                return tv;
            } catch (Throwable ignored) {
            }
        }
        TableBlock t = table();
        if (t == null) return null;
        ResourceEntry re;
        try {
            re = t.getResource(id);
        } catch (Throwable e) {
            return null;
        }
        Entry e = null;
        if (re != null) {
            try {
                e = re.any();
            } catch (Throwable ignored) {
            }
        }
        if (e == null) return null;
        try {
            ValueType vt = e.getValueType();
            int data = e.getResValue() != null ? e.getResValue().getData() : 0;
            String str = e.getValueAsString();
            if (vt == null) return null;
            if (vt.isReference()) {
                if (depth < 8 && data != 0) return valueById(data, vt == ValueType.ATTRIBUTE, depth + 1);
                return null;
            }
            switch (vt) {
                case STRING:
                    tv.type = TypedValue.TYPE_STRING;
                    tv.string = str;
                    return tv;
                case DEC:
                    tv.type = TypedValue.TYPE_INT_DEC;
                    tv.data = data;
                    return tv;
                case HEX:
                    tv.type = TypedValue.TYPE_INT_HEX;
                    tv.data = data;
                    return tv;
                case BOOLEAN:
                    tv.type = TypedValue.TYPE_INT_BOOLEAN;
                    tv.data = data;
                    return tv;
                case COLOR_ARGB8:
                    tv.type = TypedValue.TYPE_INT_COLOR_ARGB8;
                    tv.data = data;
                    return tv;
                case COLOR_RGB8:
                    tv.type = TypedValue.TYPE_INT_COLOR_RGB8;
                    tv.data = data;
                    return tv;
                case COLOR_ARGB4:
                    tv.type = TypedValue.TYPE_INT_COLOR_ARGB4;
                    tv.data = data;
                    return tv;
                case COLOR_RGB4:
                    tv.type = TypedValue.TYPE_INT_COLOR_RGB4;
                    tv.data = data;
                    return tv;
                case DIMENSION:
                    tv.type = TypedValue.TYPE_DIMENSION;
                    tv.data = data;
                    return tv;
                case FRACTION:
                    tv.type = TypedValue.TYPE_FRACTION;
                    tv.data = data;
                    return tv;
                case FLOAT:
                    tv.type = TypedValue.TYPE_FLOAT;
                    Float f = str == null ? null : parseFloatSafe(str);
                    tv.data = f == null ? data : Float.floatToIntBits(f);
                    return tv;
                default:
                    if (str != null) {
                        tv.type = TypedValue.TYPE_STRING;
                        tv.string = str;
                        return tv;
                    }
                    return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int colorFromTyped(TypedValue tv) {
        switch (tv.type) {
            case TypedValue.TYPE_INT_COLOR_ARGB8:
                return tv.data;
            case TypedValue.TYPE_INT_COLOR_RGB8:
                return 0xFF000000 | (tv.data & 0x00FFFFFF);
            case TypedValue.TYPE_INT_COLOR_ARGB4: {
                int a = (tv.data >> 12) & 0xF, r = (tv.data >> 8) & 0xF;
                int g = (tv.data >> 4) & 0xF, b = tv.data & 0xF;
                return (a << 28) | (a << 24) | (r << 20) | (r << 16)
                        | (g << 12) | (g << 8) | (b << 4) | b;
            }
            case TypedValue.TYPE_INT_COLOR_RGB4: {
                int r = (tv.data >> 8) & 0xF, g = (tv.data >> 4) & 0xF, b = tv.data & 0xF;
                return 0xFF000000 | (r << 20) | (r << 16) | (g << 12) | (g << 8) | (b << 4) | b;
            }
            case TypedValue.TYPE_INT_DEC:
            case TypedValue.TYPE_INT_HEX:
                return 0xFF000000 | tv.data;
            default:
                return Color.TRANSPARENT;
        }
    }

    // ------------------------------------------------------------ typed values

    int color(String raw, int fallback) {
        Integer lit = parseColorLiteral(raw);
        if (lit != null) return lit;
        TypedValue tv = valueOf(raw, 0);
        if (tv == null) return fallback;
        if (tv.type == TypedValue.TYPE_STRING && tv.string != null) {
            Integer c = parseColorLiteral(tv.string.toString());
            return c == null ? fallback : c;
        }
        if (tv.type == TypedValue.TYPE_REFERENCE || tv.type == TypedValue.TYPE_ATTRIBUTE) {
            TypedValue again = valueById(tv.data, tv.type == TypedValue.TYPE_ATTRIBUTE, 1);
            return again == null ? fallback : colorFromTyped(again);
        }
        return colorFromTyped(tv);
    }

    float dimensionPx(String raw, float fallback) {
        Float lit = parseNumber(raw);
        if (lit != null) return lit;
        TypedValue tv = valueOf(raw, 0);
        if (tv == null) return fallback;
        switch (tv.type) {
            case TypedValue.TYPE_DIMENSION:
                return TypedValue.complexToDimension(tv.data, metrics());
            case TypedValue.TYPE_FLOAT:
                return Float.intBitsToFloat(tv.data);
            case TypedValue.TYPE_INT_DEC:
            case TypedValue.TYPE_INT_HEX:
                return tv.data;
            case TypedValue.TYPE_STRING:
                Float f = tv.string == null ? null : parseNumber(tv.string.toString());
                return f == null ? fallback : f;
            case TypedValue.TYPE_REFERENCE:
            case TypedValue.TYPE_ATTRIBUTE: {
                TypedValue again = valueById(tv.data, tv.type == TypedValue.TYPE_ATTRIBUTE, 1);
                return again == null ? fallback : dimensionPxFrom(again, fallback);
            }
            default:
                return fallback;
        }
    }

    private float dimensionPxFrom(TypedValue tv, float fallback) {
        if (tv.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimension(tv.data, metrics());
        if (tv.type == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(tv.data);
        if (tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX) return tv.data;
        return fallback;
    }

    float number(String raw, float fallback) {
        Float lit = parseNumber(raw);
        if (lit != null) return lit;
        TypedValue tv = valueOf(raw, 0);
        if (tv == null) return fallback;
        switch (tv.type) {
            case TypedValue.TYPE_FLOAT:
                return Float.intBitsToFloat(tv.data);
            case TypedValue.TYPE_INT_DEC:
            case TypedValue.TYPE_INT_HEX:
            case TypedValue.TYPE_INT_BOOLEAN:
                return tv.data;
            case TypedValue.TYPE_DIMENSION:
                return TypedValue.complexToDimension(tv.data, metrics());
            case TypedValue.TYPE_STRING:
                Float f = tv.string == null ? null : parseNumber(tv.string.toString());
                return f == null ? fallback : f;
            default:
                return fallback;
        }
    }

    boolean bool(String raw, boolean fallback) {
        if (raw == null) return fallback;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        TypedValue tv = valueOf(raw, 0);
        if (tv == null) return fallback;
        if (tv.type == TypedValue.TYPE_STRING && tv.string != null) {
            return Boolean.parseBoolean(tv.string.toString().trim());
        }
        return tv.data != 0;
    }

    int integer(String raw, int fallback) {
        Float n = parseNumber(raw);
        if (n != null) return (int) (float) n;
        TypedValue tv = valueOf(raw, 0);
        if (tv == null) return fallback;
        return tv.data;
    }

    String text(String raw) {
        if (raw == null) return "";
        if (!raw.trim().startsWith("@") && !raw.trim().startsWith("?")) return raw;
        TypedValue tv = valueOf(raw, 0);
        if (tv != null && tv.string != null) return tv.string.toString();
        return raw;
    }

    // -------------------------------------------------------- nested drawables

    /**
     * Resolves an {@code android:drawable="..."} reference. Returns a framework
     * drawable inflated from the APK's resource table when possible, otherwise a
     * hand-inflated {@link XmlDrawable} or a {@link BitmapDrawable}.
     */
    Drawable drawable(String raw, int depth) {
        if (raw == null || depth > 6) return null;
        String s = raw.trim();
        if (s.equals("@null") || s.equals("@empty")) return null;
        Drawable cached = drawableCache.get(s + ":" + depth);
        if (cached != null) return cached;

        Drawable result = null;
        if (res != null) {
            int id = resolveId(s);
            if (id != 0) {
                try {
                    result = res.getDrawable(id, theme);
                } catch (Throwable ignored) {
                }
                if (result == null) {
                    try {
                        ColorStateList csl = res.getColorStateList(id, theme);
                        result = new ColorDrawable(csl.getDefaultColor());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        if (result == null && archive != null) result = drawableFromArchive(s, depth);
        if (result != null) drawableCache.put(s + ":" + depth, result);
        return result;
    }

    private Drawable drawableFromArchive(String ref, int depth) {
        String name = refName(ref);
        if (name == null || name.isEmpty()) return null;
        String path = findEntry(name);
        if (path == null) return null;
        byte[] data = ApkDrawableRenderer.readEntryBytes(archive, path, 8 * 1024 * 1024);
        if (data == null || data.length == 0) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xml")) {
            try {
                XmlNode node = XmlNode.parse(ApkDrawableRenderer.decodeXml(data));
                return node == null ? null : new XmlDrawable(node, this, depth + 1);
            } catch (Throwable ignored) {
                return null;
            }
        }
        try {
            android.graphics.Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
            return bm == null ? null : new BitmapDrawable(context.getResources(), bm);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String refName(String ref) {
        String s = ref.trim();
        if (!s.startsWith("@")) return null;
        s = s.substring(1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s;
    }

    private String findEntry(String name) {
        if (entries == null) {
            if (entriesTried) return null;
            entriesTried = true;
            try {
                entries = ArchiveBrowseUtil.listEntries(archive);
            } catch (Throwable ignored) {
                return null;
            }
        }
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ArchiveBrowseUtil.RawEntry e : entries) {
            if (e.isDirectory()) continue;
            String p = e.path();
            String lower = p.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("res/drawable") && !lower.startsWith("res/mipmap")) continue;
            int slash = p.lastIndexOf('/');
            String file = slash >= 0 ? p.substring(slash + 1) : p;
            int dot = file.lastIndexOf('.');
            String base = dot >= 0 ? file.substring(0, dot) : file;
            if (!base.equals(name)) continue;
            int score = 0;
            if (lower.endsWith(".xml")) score += 10;
            else if (lower.endsWith(".png") || lower.endsWith(".webp")) score += 5;
            if (lower.contains("xxhdpi")) score += 3;
            if (lower.contains("anydpi")) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }
}
