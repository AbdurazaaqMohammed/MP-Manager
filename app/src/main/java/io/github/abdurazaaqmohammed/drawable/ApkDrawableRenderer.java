package io.github.abdurazaaqmohammed.drawable;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;

import com.apk.axml.aXMLDecoder;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;

/**
 * Renders an Android drawable XML that lives inside an APK (or any archive) to
 * a {@link Bitmap}.
 *
 * <p><b>Why not {@code Drawable.createFromXml}?</b> The framework inflater
 * casts every AttributeSet to {@code XmlBlock.Parser}, which only exists for
 * compiled binary XML. Feeding it a text pull parser always throws a
 * ClassCastException, and decoding binary AXML to text first loses all
 * {@code @color}/{@code @dimen}/{@code ?attr} references (they would resolve
 * against MP Manager, not the APK). The existing thumbnail code did exactly
 * that and silently fell back to a generic icon for virtually every real APK
 * drawable.</p>
 *
 * <p><b>Primary path.</b> {@link PackageManager#getResourcesForApplication} is a
 * public API that opens the APK's own resource table. Combined with the
 * resource id derived from {@code res/drawable-xxx/name.xml}, a plain
 * {@link Resources#getDrawable} inflates the compiled AXML using the framework's
 * own {@code XmlBlock.Parser}. That produces exactly what Android would draw,
 * including vectors, gradients, selectors, layer lists, nine-patches, adaptive
 * icons, aliases, density/locale/night configurations and theme attributes.</p>
 *
 * <p><b>Fallback path.</b> For loose/decompiled XML, ZIPs without a manifest, or
 * resources that cannot be looked up, {@link XmlDrawable} renders the decoded
 * XML directly onto a Canvas (shape, selector, layer-list, vector, ...). Nested
 * references are still delegated to the APK Resources when available.</p>
 */
public final class ApkDrawableRenderer {

    private static final String TAG = "ApkDrawableRenderer";
    private static final Object LOCK = new Object();
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_BITMAP = 2048;

    private static final Set<String> DRAWABLE_ROOT_TAGS = new HashSet<>(Arrays.asList(
            "shape", "selector", "animated-selector", "layer-list", "level-list",
            "inset", "clip", "scale", "rotate", "ripple", "transition",
            "animation-list", "bitmap", "nine-patch", "color", "vector",
            "animated-vector", "adaptive-icon", "drawable"));

    private ApkDrawableRenderer() {
    }

    // ------------------------------------------------------------------ result

    /** Everything the preview needs, produced once off the UI thread. */
    public static final class Result {
        /** Live drawable; re-rasterize with a different state/level to preview it. */
        public final Drawable drawable;
        /** Decoded XML text, for the "view source" action. */
        public final String xml;
        public final String rootTag;
        /** "APK resources" or "XML renderer". */
        public final String source;
        /** e.g. "@drawable/ic_launcher". */
        public final String resourceRef;
        /** Human readable warnings (never fatal). */
        public final String note;
        public final int intrinsicWidth;
        public final int intrinsicHeight;

        Result(Drawable drawable, String xml, String rootTag, String source,
               String resourceRef, String note) {
            this.drawable = drawable;
            this.xml = xml;
            this.rootTag = rootTag;
            this.source = source;
            this.resourceRef = resourceRef;
            this.note = note;
            this.intrinsicWidth = drawable == null ? -1 : drawable.getIntrinsicWidth();
            this.intrinsicHeight = drawable == null ? -1 : drawable.getIntrinsicHeight();
        }

        public boolean ok() {
            return drawable != null;
        }
    }

    /** Result describing a failure, so the UI can still show the XML source. */
    public static final class RenderException extends Exception {
        public final String xml;

        RenderException(String message, String xml, Throwable cause) {
            super(message, cause);
            this.xml = xml;
        }
    }

    // -------------------------------------------------------------- entry points

    /**
     * @param archive   the APK/ZIP on disk, or null when {@code entryPath} is a
     *                  plain filesystem path to a loose XML file
     * @param entryPath entry path inside the archive (or the file path)
     */
    public static Result render(Context context, File archive, String entryPath,
                                int targetPx) throws RenderException {
        synchronized (LOCK) {
            byte[] data = archive == null
                    ? readFileBytes(new File(entryPath), MAX_ENTRY_BYTES)
                    : readEntryBytes(archive, entryPath, MAX_ENTRY_BYTES);
            if (data == null || data.length == 0) {
                throw new RenderException("The resource is empty or unreadable.", null, null);
            }
            return renderBytes(context, archive, entryPath, data, targetPx);
        }
    }

    /** Renders a loose XML file (e.g. an extracted/decompiled drawable). */
    public static Result renderFile(Context context, File file, int targetPx)
            throws RenderException {
        return render(context, null, file.getAbsolutePath(), targetPx);
    }

    /**
     * Cheap path-based check used for thumbnails. Matches the resource
     * <em>directory</em> (drawable, drawable-hdpi, mipmap-anydpi-v26,
     * color-v31, ...) rather than a loose substring, so files such as
     * {@code res/anim/color_animator.xml} are not mistaken for drawables.
     */
    public static boolean isDrawableXmlPath(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!p.endsWith(".xml")) return false;
        int slash = p.lastIndexOf('/');
        String parent = slash >= 0 ? p.substring(0, slash) : "";
        int prev = parent.lastIndexOf('/');
        String dir = prev >= 0 ? parent.substring(prev + 1) : parent;
        return isDrawableDir(dir);
    }

    private static boolean isDrawableDir(String dir) {
        if (dir == null) return false;
        return dir.equals("drawable") || dir.startsWith("drawable-")
                || dir.equals("mipmap") || dir.startsWith("mipmap-")
                || dir.equals("color") || dir.startsWith("color-");
    }

    /**
     * Path check + root-tag check. APKs sometimes keep animators and
     * pointer-icons inside res/drawable; those must still open as text, so use
     * this before hijacking a click to show the preview.
     */
    public static boolean isDrawableXmlEntry(File archive, String entryPath) {
        if (!isDrawableXmlPath(entryPath)) return false;
        return isDrawableXmlFile(archive, entryPath);
    }

    /**
     * Content based check: reads the XML and looks at its root tag. Use for
     * staged files whose name no longer carries the res/ path.
     */
    public static boolean isDrawableXmlFile(File archive, String entryPath) {
        try {
            byte[] data = archive == null
                    ? readFileBytes(new File(entryPath), 512 * 1024)
                    : readEntryBytes(archive, entryPath, 512 * 1024);
            if (data == null || data.length == 0) return false;
            String xml = decodeXml(data);
            XmlNode root = XmlNode.parse(xml);
            return root != null && DRAWABLE_ROOT_TAGS.contains(root.tag);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------- render

    private static Result renderBytes(Context context, File archive, String entryPath,
                                      byte[] data, int targetPx) throws RenderException {
        String xml = null;
        try {
            xml = decodeXml(data);
        } catch (Throwable t) {
            throw new RenderException("Could not decode this XML resource.", null, t);
        }
        XmlNode root;
        try {
            root = XmlNode.parse(xml);
        } catch (Throwable t) {
            throw new RenderException("This file is not well-formed XML.", xml, t);
        }
        if (root == null) {
            throw new RenderException("This file has no XML root element.", xml, null);
        }
        if (!DRAWABLE_ROOT_TAGS.contains(root.tag)) {
            throw new RenderException("Not a drawable XML (root: <" + root.tag + ">).", xml, null);
        }

        ApkRes apkRes = loadApkResources(context, archive);
        String type = resourceType(entryPath);
        String name = resourceName(entryPath);
        String ref = (type == null || name == null) ? null : "@" + type + "/" + name;

        Drawable drawable = null;
        String source = null;
        String note = null;

        if (apkRes != null && type != null && name != null) {
            int id = apkRes.findId(type, name);
            if (id != 0) {
                try {
                    if ("color".equals(type)) {
                        ColorStateList csl = apkRes.res.getColorStateList(id, apkRes.theme);
                        drawable = new StateColorDrawable(csl);
                    } else {
                        drawable = apkRes.res.getDrawable(id, apkRes.theme);
                    }
                    source = "APK resources";
                    if (ref != null) ref += String.format(Locale.US, " (0x%08X)", id);
                } catch (Throwable t) {
                    Log.w(TAG, "getDrawable failed for " + ref, t);
                    note = "Framework inflation failed (" + shortReason(t) + "); "
                            + "using the built-in XML renderer instead.";
                }
            }
        }

        if (drawable == null) {
            DrawableResolver resolver = new DrawableResolver(context,
                    apkRes == null ? null : apkRes.res,
                    apkRes == null ? null : apkRes.theme,
                    apkRes == null ? null : apkRes.pkg,
                    archive);
            drawable = new XmlDrawable(root, resolver, 0);
            source = "XML renderer";
            if (note == null) {
                note = apkRes == null
                        ? "No APK resource table available; rendered from the XML directly."
                        : "Resource id not found in the APK table; rendered from the XML directly.";
            }
        }
        return new Result(drawable, xml, root.tag, source,
                ref == null ? name : ref, note);
    }

    private static String shortReason(Throwable t) {
        String s = t.getClass().getSimpleName();
        return s == null ? "unknown error" : s;
    }

    /** Package-private: used by {@link DrawableResolver} for nested references. */
    static byte[] readEntryBytes(File archive, String entryPath, int limit) {
        if (archive == null || entryPath == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = ArchiveBrowseUtil.openEntry(archive, entryPath)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > limit) return null;
                out.write(buf, 0, n);
            }
        } catch (Throwable t) {
            return null;
        }
        return out.toByteArray();
    }

    static byte[] readFileBytes(File file, int limit) {
        if (file == null || !file.isFile() || file.length() > limit) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > limit) return null;
                out.write(buf, 0, n);
            }
        } catch (Throwable t) {
            return null;
        }
        return out.toByteArray();
    }

    /** Binary AXML (compiled resource file) -&gt; editable XML text. */
    static String decodeXml(byte[] data) throws Exception {
        if (data == null || data.length == 0) return "";
        if (data.length >= 2 && (data[0] & 0xFF) == 0x03 && (data[1] & 0xFF) == 0x00) {
            try {
                return new aXMLDecoder(new ByteArrayInputStream(data)).decodeAsString();
            } catch (Throwable primaryFailure) {
                // Some aapt2 pools (sparse entries, unusual UTF-16 flag combos)
                // trip the lightweight decoder. ARSCLib's ResXmlDocument is a
                // second, independent implementation - use it before giving up.
                try {
                    ResXmlDocument doc = new ResXmlDocument();
                    doc.readBytes(new ByteArrayInputStream(data));
                    return doc.serializeToXml();
                } catch (Throwable ignored) {
                    throw new Exception("Unable to decode binary XML", primaryFailure);
                }
            }
        }
        String s;
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            s = new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            s = new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        } else if (data.length >= 3 && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            s = new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        } else {
            s = new String(data, StandardCharsets.UTF_8);
        }
        // Strip an XML declaration with a non-UTF-8 encoding; the pull parser
        // otherwise tries to honour it and fails on UTF-8 bytes.
        if (s.startsWith("<?xml")) {
            int end = s.indexOf("?>");
            if (end > 0) s = s.substring(end + 2);
        }
        return s;
    }

    private static String resourceType(String entryPath) {
        String dir = resourceDir(entryPath);
        if (dir == null) return null;
        int dash = dir.indexOf('-');
        String type = dash >= 0 ? dir.substring(0, dash) : dir;
        if ("drawable".equals(type) || "mipmap".equals(type) || "color".equals(type)) {
            return type;
        }
        return null;
    }

    private static String resourceName(String entryPath) {
        if (entryPath == null) return null;
        String p = entryPath.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        String file = slash >= 0 ? p.substring(slash + 1) : p;
        if (!file.toLowerCase(Locale.ROOT).endsWith(".xml")) return null;
        String name = file.substring(0, file.length() - 4);
        // Resource names cannot contain these; aapt2 mangles them to '_'.
        return name.replace('.', '_').replace('-', '_').replace(':', '_');
    }

    private static String resourceDir(String entryPath) {
        if (entryPath == null) return null;
        String p = entryPath.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        if (slash < 0) return null;
        String parent = p.substring(0, slash);
        int prev = parent.lastIndexOf('/');
        return prev >= 0 ? parent.substring(prev + 1) : parent;
    }

    // ------------------------------------------------------------ APK resources

    private static final int[] EMPTY_STATE = new int[0];

    /** A color state list rendered as a swatch that follows the preview's state chips. */
    private static final class StateColorDrawable extends ColorDrawable {
        private final ColorStateList colors;

        StateColorDrawable(ColorStateList colors) {
            super(colors.getDefaultColor());
            this.colors = colors;
        }

        @Override
        public void draw(Canvas canvas) {
            setColor(colors.getColorForState(getState(), colors.getDefaultColor()));
            super.draw(canvas);
        }
    }

    private static final class ApkRes {
        final Resources res;
        final Resources.Theme theme;
        final String pkg;

        ApkRes(Resources res, Resources.Theme theme, String pkg) {
            this.res = res;
            this.theme = theme;
            this.pkg = pkg;
        }

        int findId(String type, String name) {
            if (pkg != null) {
                int id = safeIdentifier(type, name, pkg);
                if (id != 0) return id;
            }
            int id = safeIdentifier(type, name, "android");
            if (id != 0) return id;
            id = safeIdentifier(type, name, null);
            if (id != 0) return id;
            return 0;
        }

        private int safeIdentifier(String type, String name, String p) {
            try {
                return res.getIdentifier(name, type, p);
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    private static final LruCache<String, ApkRes> RES_CACHE = new LruCache<>(6);

    private static ApkRes loadApkResources(Context context, File archive) {
        if (archive == null || !archive.isFile()) return null;
        String key = archive.getAbsolutePath() + ":" + archive.lastModified()
                + ":" + archive.length();
        synchronized (RES_CACHE) {
            ApkRes cached = RES_CACHE.get(key);
            if (cached != null) return cached;
        }
        ApkRes result = null;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(archive.getAbsolutePath(), 0);
            String pkg = null;
            Resources res = null;
            int themeId = 0;
            if (pi != null && pi.applicationInfo != null) {
                ApplicationInfo ai = pi.applicationInfo;
                pkg = pi.packageName;
                themeId = ai.theme;
                // getResourcesForApplication() reads these two fields. For an
                // archive (not an installed package) they must point at the file.
                ai.sourceDir = archive.getAbsolutePath();
                ai.publicSourceDir = archive.getAbsolutePath();
                res = pm.getResourcesForApplication(ai);
            }
            if (res == null) res = reflectResources(context, archive);
            if (res == null) return null;
            if (pkg == null) pkg = guessPackageName(archive);
            Resources.Theme theme = res.newTheme();
            try {
                theme.applyStyle(android.R.style.Theme_Material_Light, true);
            } catch (Throwable ignored) {
            }
            if (themeId != 0) {
                try {
                    theme.applyStyle(themeId, true);
                } catch (Throwable ignored) {
                }
            }
            result = new ApkRes(res, theme, pkg);
        } catch (Throwable t) {
            Log.w(TAG, "Could not open APK resources for " + archive, t);
        }
        if (result != null) {
            synchronized (RES_CACHE) {
                RES_CACHE.put(key, result);
            }
        }
        return result;
    }

    /**
     * Secondary way to obtain a Resources object. Works on older Android and on
     * emulator/eng builds; on newer releases the public
     * getResourcesForApplication path above already covers normal APKs.
     */
    private static Resources reflectResources(Context context, File apk) {
        try {
            java.lang.reflect.Constructor<AssetManager> ctor =
                    AssetManager.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AssetManager assets = ctor.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod(
                    "addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            Object cookie = addAssetPath.invoke(assets, apk.getAbsolutePath());
            if (cookie == null) return null;
            return new Resources(assets, context.getResources().getDisplayMetrics(),
                    context.getResources().getConfiguration());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String guessPackageName(File archive) {
        try (InputStream in = ArchiveBrowseUtil.openEntry(archive, "resources.arsc")) {
            TableBlock table = TableBlock.load(in);
            Iterator<PackageBlock> it = table.getPackages();
            while (it.hasNext()) {
                String name = it.next().getName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Drops cached Resources/arsc handles after an APK is modified. */
    public static void clearCache() {
        synchronized (RES_CACHE) {
            RES_CACHE.evictAll();
        }
    }

    // -------------------------------------------------------------- rasterization

    /** Draws the result at up to {@code targetPx} on each axis, preserving aspect. */
    public static Bitmap rasterize(Result result, int[] state, int level, int targetPx) {
        if (result == null || result.drawable == null) return null;
        synchronized (LOCK) {
            Drawable d = result.drawable;
            int iw = d.getIntrinsicWidth();
            int ih = d.getIntrinsicHeight();
            int w, h;
            if (iw > 0 && ih > 0) {
                float scale = Math.min(targetPx / (float) iw, targetPx / (float) ih);
                w = Math.max(1, Math.round(iw * scale));
                h = Math.max(1, Math.round(ih * scale));
            } else {
                w = targetPx;
                h = targetPx;
            }
            w = Math.min(w, MAX_BITMAP);
            h = Math.min(h, MAX_BITMAP);
            Bitmap bm;
            try {
                bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                return null;
            }
            Canvas canvas = new Canvas(bm);
            try {
                d.setBounds(0, 0, w, h);
            } catch (Throwable ignored) {
            }
            // RippleDrawable / AnimatedVectorDrawable start ValueAnimators when
            // their state changes, and ValueAnimator refuses to run on a thread
            // without a Looper. Rendering happens on worker threads, so keep
            // going with whatever state the drawable reached instead of failing
            // the whole preview.
            try {
                d.setState(state == null ? EMPTY_STATE : state);
            } catch (Throwable t) {
                Log.w(TAG, "setState failed (animation requires a Looper)", t);
            }
            try {
                d.setLevel(level);
            } catch (Throwable ignored) {
            }
            try {
                d.jumpToCurrentState();
            } catch (Throwable ignored) {
            }
            try {
                d.setAlpha(255);
                d.draw(canvas);
            } catch (Throwable t) {
                Log.w(TAG, "Drawable.draw failed", t);
            }
            return bm;
        }
    }

    /** Composites a transparent bitmap over a light/dark checkerboard. */
    public static Bitmap onCheckerboard(Bitmap source, int squarePx) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int s = Math.max(2, squarePx);
        for (int y = 0; y < h; y += s) {
            for (int x = 0; x < w; x += s) {
                boolean dark = ((x / s) + (y / s)) % 2 == 0;
                p.setColor(dark ? 0xFFB9B9B9 : 0xFFE9E9E9);
                c.drawRect(x, y, Math.min(x + s, w), Math.min(y + s, h), p);
            }
        }
        c.drawBitmap(source, 0, 0, null);
        return out;
    }

    /** Repeating tile used as the preview background. */
    public static BitmapDrawable checkerboardTile(Context context) {
        int s = 12;
        Bitmap tile = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tile);
        Paint p = new Paint();
        p.setColor(0xFFE9E9E9);
        c.drawRect(0, 0, s * 2, s * 2, p);
        p.setColor(0xFFB9B9B9);
        c.drawRect(0, 0, s, s, p);
        c.drawRect(s, s, s * 2, s * 2, p);
        BitmapDrawable d = new BitmapDrawable(context.getResources(), tile);
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return d;
    }

    /** Decodes an image entry from an archive (used for the non-XML thumbnail path). */
    public static Bitmap decodeImageEntry(File archive, String entryPath, int maxPx) {
        byte[] data = readEntryBytes(archive, entryPath, 24 * 1024 * 1024);
        if (data == null) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int scale = 1;
            while (opts.outWidth / scale > maxPx * 2 && opts.outHeight / scale > maxPx * 2) {
                scale *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Throwable t) {
            return null;
        }
    }
}
