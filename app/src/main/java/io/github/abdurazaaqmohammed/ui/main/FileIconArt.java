package io.github.abdurazaaqmohammed.ui.main;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.core.graphics.ColorUtils;

import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;

/**
 * Renders supersampled, Light/Dark-adaptive 3D vector artwork for all file types
 * and folders, caching immutable bitmaps once per theme state and density.
 */
public final class FileIconArt {

    private static final float ARTBOARD = 100f;
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<>(96);

    private FileIconArt() {}

    /** Returns a crisp 3D {@link Drawable} for {@code spec} adapted to the active theme. */
    public static Drawable drawable(Context context, FileIconSpec spec) {
        return drawable(context, spec, false);
    }

    /** Returns a crisp 3D Rose-Gold parent folder ("..") drawable adapted to the active theme. */
    public static Drawable parentFolderDrawable(Context context) {
        return drawable(context, FileIconSpec.FOLDER, true);
    }

    /** Draws {@code spec} directly onto {@code canvas} within a square of {@code sizePx}. */
    public static void drawToCanvas(Context context, Canvas canvas, FileIconSpec spec,
                                    boolean parentUp, float sizePx,
                                    boolean includeCardBg, int customCardBg) {
        if (canvas == null || spec == null || sizePx <= 0f) return;
        boolean dark = ThemeRegistry.isDarkTheme(context);
        Integer packFolder = ThemeRegistry.packColorOrNull(context, "folder_icon_bg");
        Integer packFile = ThemeRegistry.packColorOrNull(context, "file_icon_bg");
        int save = canvas.save();
        float scale = sizePx / ARTBOARD;
        canvas.scale(scale, scale);
        if (includeCardBg && customCardBg != spec.background()) {
            Paint bgPaint = fillPaint(customCardBg);
            canvas.drawRoundRect(new RectF(2f, 2f, 98f, 98f), 20f, 20f, bgPaint);
            canvas.translate(10f, 10f);
            canvas.scale(0.8f, 0.8f);
        }
        renderSpec(canvas, spec, parentUp, dark, packFolder, packFile);
        canvas.restoreToCount(save);
    }

    private static Drawable drawable(Context context, FileIconSpec spec, boolean parentUp) {
        Resources res = context.getResources();
        float density = Math.max(1f, res.getDisplayMetrics().density);
        int sizePx = Math.min(192, Math.max(96, Math.round(48f * density)));
        boolean dark = ThemeRegistry.isDarkTheme(context);
        Integer packFolder = ThemeRegistry.packColorOrNull(context, "folder_icon_bg");
        Integer packFile = ThemeRegistry.packColorOrNull(context, "file_icon_bg");

        String key = kindKey(spec, parentUp)
                + '|' + (dark ? 'D' : 'L')
                + '|' + sizePx
                + '|' + (packFolder == null ? "-" : Integer.toHexString(packFolder))
                + '|' + (packFile == null ? "-" : Integer.toHexString(packFile));

        Bitmap bitmap = BITMAP_CACHE.get(key);
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            bitmap.setDensity(res.getDisplayMetrics().densityDpi);
            Canvas canvas = new Canvas(bitmap);
            float scale = sizePx / ARTBOARD;
            canvas.scale(scale, scale);
            renderSpec(canvas, spec, parentUp, dark, packFolder, packFile);
            BITMAP_CACHE.put(key, bitmap);
        }
        return new BitmapDrawable(res, bitmap);
    }

    private static String kindKey(FileIconSpec spec, boolean parentUp) {
        if (spec == FileIconSpec.FOLDER) return parentUp ? "FOLDER_UP" : "FOLDER";
        if (spec == FileIconSpec.ARCHIVE) return "ARCHIVE";
        if (spec == FileIconSpec.APK) return "APK";
        if (spec == FileIconSpec.IMAGE) return "IMAGE";
        if (spec == FileIconSpec.VIDEO) return "VIDEO";
        if (spec == FileIconSpec.AUDIO) return "AUDIO";
        if (spec == FileIconSpec.ARSC) return "ARSC";
        if (spec == FileIconSpec.DEX) return "DEX";
        if (spec == FileIconSpec.STICKER) return "STICKER";
        if (spec == FileIconSpec.PDF) return "PDF";
        if (spec == FileIconSpec.TEXT) return "TEXT";
        if (spec == FileIconSpec.ELF) return "ELF";
        if (spec == FileIconSpec.DISK) return "DISK";
        if (spec == FileIconSpec.DATABASE) return "DATABASE";
        if (spec == FileIconSpec.FONT) return "FONT";
        if (spec == FileIconSpec.BINARY) return "BINARY";
        if (spec == FileIconSpec.PROTO) return "PROTO";
        if (spec == FileIconSpec.DOCUMENT) return "DOCUMENT";
        if (spec == FileIconSpec.SHEET) return "SHEET";
        if (spec == FileIconSpec.SLIDE) return "SLIDE";
        if (spec == FileIconSpec.SCRIPT) return "SCRIPT";
        if (spec == FileIconSpec.BACKUP) return "BACKUP";
        if (spec == FileIconSpec.CERTIFICATE) return "CERTIFICATE";
        return "FILE";
    }

    private static void renderSpec(Canvas c, FileIconSpec spec, boolean parentUp,
                                   boolean dark, Integer packFolder, Integer packFile) {
        if (spec == FileIconSpec.FOLDER) {
            drawFolder3D(c, dark, packFolder, parentUp);
        } else if (spec == FileIconSpec.ARCHIVE) {
            drawArchiveFolder3D(c, dark);
        } else if (spec == FileIconSpec.APK) {
            drawApkBadge3D(c, dark);
        } else if (spec == FileIconSpec.IMAGE) {
            drawImageFrame3D(c, dark);
        } else if (spec == FileIconSpec.VIDEO) {
            drawVideoCard3D(c, dark);
        } else if (spec == FileIconSpec.AUDIO) {
            drawAudioCard3D(c, dark);
        } else if (spec == FileIconSpec.STICKER) {
            drawSticker3D(c, dark);
        } else if (spec == FileIconSpec.PDF) {
            drawPdfCard3D(c, dark);
        } else if (spec == FileIconSpec.TEXT) {
            drawTextCard3D(c, dark);
        } else if (spec == FileIconSpec.DOCUMENT) {
            drawDocumentCard3D(c, dark);
        } else if (spec == FileIconSpec.SHEET) {
            drawSheetCard3D(c, dark);
        } else if (spec == FileIconSpec.SLIDE) {
            drawSlideCard3D(c, dark);
        } else if (spec == FileIconSpec.DEX) {
            drawDexCard3D(c, dark);
        } else if (spec == FileIconSpec.ARSC) {
            drawArscStacks3D(c, dark);
        } else if (spec == FileIconSpec.DATABASE) {
            drawDatabaseCylinders3D(c, dark);
        } else if (spec == FileIconSpec.DISK) {
            drawDiskDrive3D(c, dark);
        } else if (spec == FileIconSpec.ELF) {
            drawElfProcessor3D(c, dark);
        } else if (spec == FileIconSpec.SCRIPT) {
            drawScriptTerminal3D(c, dark);
        } else if (spec == FileIconSpec.FONT) {
            drawFontCard3D(c, dark);
        } else if (spec == FileIconSpec.PROTO) {
            drawProtoCard3D(c, dark);
        } else if (spec == FileIconSpec.BACKUP) {
            drawBackupCard3D(c, dark);
        } else if (spec == FileIconSpec.CERTIFICATE) {
            drawCertificateCard3D(c, dark);
        } else if (spec == FileIconSpec.BINARY) {
            drawBinaryGears3D(c, dark);
        } else {
            drawPlainFile3D(c, dark, packFile);
        }
    }

    // ---------- Shared Lighting, Gradient, Shadow & Page Primitives ----------

    private static Paint fillPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    private static Paint strokePaint(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    private static Paint linearGrad(float x0, float y0, float x1, float y1, int top, int bot) {
        Paint p = fillPaint(top);
        p.setShader(new LinearGradient(x0, y0, x1, y1, top, bot, Shader.TileMode.CLAMP));
        return p;
    }

    private static Paint linearGrad3(float x0, float y0, float x1, float y1, int top, int mid, int bot) {
        Paint p = fillPaint(top);
        p.setShader(new LinearGradient(x0, y0, x1, y1,
                new int[]{top, mid, bot}, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        return p;
    }

    private static Paint radialGrad(float cx, float cy, float r, int centerColor, int edgeColor) {
        Paint p = fillPaint(centerColor);
        p.setShader(new RadialGradient(cx, cy, Math.max(1f, r), centerColor, edgeColor, Shader.TileMode.CLAMP));
        return p;
    }

    private static int tint(int color, float ratio) {
        return ColorUtils.blendARGB(color, Color.WHITE, ratio);
    }

    private static int shade(int color, float ratio) {
        return ColorUtils.blendARGB(color, Color.BLACK, ratio);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static void drawSoftDropShadow(Canvas c, RectF rect, float radius, boolean dark) {
        int a1 = dark ? 0x36 : 0x1D;
        int a2 = dark ? 0x22 : 0x12;
        c.drawRoundRect(new RectF(rect.left + 0.5f, rect.top + 4.5f, rect.right - 0.5f, rect.bottom + 5.0f),
                radius, radius, fillPaint(withAlpha(0x0B1018, a2)));
        c.drawRoundRect(new RectF(rect.left + 1.2f, rect.top + 2.5f, rect.right - 1.2f, rect.bottom + 2.8f),
                radius, radius, fillPaint(withAlpha(0x0B1018, a1)));
    }

    private static void drawRoundedTile3D(Canvas c, RectF bounds, float radius,
                                          int topColor, int midColor, int botColor, boolean dark) {
        drawSoftDropShadow(c, bounds, radius, dark);
        c.drawRoundRect(bounds, radius, radius,
                linearGrad3(bounds.left, bounds.top, bounds.right, bounds.bottom, topColor, midColor, botColor));

        // Top-left glossy rim catchlight gives tactile beveled thickness.
        int rimAlpha = dark ? 0x62 : 0x80;
        Paint rim = strokePaint(withAlpha(Color.WHITE, rimAlpha), 1.8f);
        Path topRim = new Path();
        float r = Math.min(radius, bounds.width() * 0.45f);
        topRim.moveTo(bounds.left + 1.5f, bounds.bottom - r);
        topRim.lineTo(bounds.left + 1.5f, bounds.top + r);
        topRim.quadTo(bounds.left + 1.5f, bounds.top + 1.5f, bounds.left + r, bounds.top + 1.5f);
        topRim.lineTo(bounds.right - r, bounds.top + 1.5f);
        topRim.quadTo(bounds.right - 1.5f, bounds.top + 1.5f, bounds.right - 1.5f, bounds.top + r * 0.65f);
        c.drawPath(topRim, rim);

        // Crisp subtle border in Light mode prevents edge softness on white backgrounds.
        int borderColor = dark ? withAlpha(Color.WHITE, 0x22) : withAlpha(shade(botColor, 0.45f), 0x45);
        c.drawRoundRect(bounds, radius, radius, strokePaint(borderColor, dark ? 1.0f : 1.3f));
    }

    /** Draws a sculpted 3D folded document page and returns the body bounds for inner details. */
    private static RectF drawFoldedPage(Canvas c, float fold, int paperTop, int paperBot,
                                        int foldTop, int foldBot, int stroke, boolean dark) {
        RectF shadowRect = new RectF(15f, 8f, 85f, 92f);
        drawSoftDropShadow(c, shadowRect, 9f, dark);

        float l = 15f, t = 8f, r = 85f, b = 92f, rad = 9f;
        Path page = new Path();
        page.moveTo(l + rad, t);
        page.lineTo(r - fold, t);
        page.lineTo(r, t + fold);
        page.lineTo(r, b - rad);
        page.quadTo(r, b, r - rad, b);
        page.lineTo(l + rad, b);
        page.quadTo(l, b, l, b - rad);
        page.lineTo(l, t + rad);
        page.quadTo(l, t, l + rad, t);
        page.close();

        c.drawPath(page, linearGrad(l, t, r, b, paperTop, paperBot));
        c.drawPath(page, strokePaint(stroke, dark ? 1.2f : 1.5f));

        // Top specular rim along page top & left edge.
        Path edgeHi = new Path();
        edgeHi.moveTo(l + 1.5f, b - rad);
        edgeHi.lineTo(l + 1.5f, t + rad);
        edgeHi.quadTo(l + 1.5f, t + 1.5f, l + rad, t + 1.5f);
        edgeHi.lineTo(r - fold - 1f, t + 1.5f);
        c.drawPath(edgeHi, strokePaint(withAlpha(Color.WHITE, dark ? 0x38 : 0xB0), 1.6f));

        // Cast triangle shadow tucked beneath the dog-ear flap.
        Path foldShadow = new Path();
        foldShadow.moveTo(r - fold, t + fold);
        foldShadow.lineTo(r, t + fold);
        foldShadow.lineTo(r, t + fold + 7f);
        foldShadow.lineTo(r - fold + 4f, t + fold + 4.5f);
        foldShadow.close();
        c.drawPath(foldShadow, fillPaint(dark ? 0x42000000 : 0x22101A28));

        // Folded corner flap.
        Path flap = new Path();
        flap.moveTo(r - fold, t);
        flap.lineTo(r - fold, t + fold - 4f);
        flap.quadTo(r - fold, t + fold, r - fold + 4f, t + fold);
        flap.lineTo(r, t + fold);
        flap.close();
        c.drawPath(flap, linearGrad(r - fold, t, r, t + fold, foldTop, foldBot));
        c.drawPath(flap, strokePaint(withAlpha(Color.WHITE, dark ? 0x32 : 0x70), 1.0f));
        return shadowRect;
    }

    private static int[] pageColors(boolean dark) {
        if (dark) {
            return new int[]{0xFF3E4654, 0xFF282E38, 0xFF5D687A, 0xFF475161, 0x559AB0CC};
        }
        return new int[]{0xFFFFFFFF, 0xFFEAF0F7, 0xFFE2E9F2, 0xFFC5D0DE, 0xFFBAC6D6};
    }

    private static int[] tintedPageColors(int accent, boolean dark) {
        if (dark) {
            int top = ColorUtils.blendARGB(0xFF3A414E, accent, 0.22f);
            int bot = ColorUtils.blendARGB(0xFF242933, accent, 0.28f);
            return new int[]{top, bot, tint(accent, 0.28f), shade(accent, 0.12f), withAlpha(tint(accent, 0.5f), 0x60)};
        }
        int top = ColorUtils.blendARGB(Color.WHITE, accent, 0.06f);
        int bot = ColorUtils.blendARGB(0xFFEEF3F9, accent, 0.14f);
        return new int[]{top, bot, tint(accent, 0.42f), tint(accent, 0.10f), ColorUtils.blendARGB(0xFFB5C2D1, accent, 0.35f)};
    }

    // ---------- 1 & 2. Rose Gold 3D Folder & Parent Folder ("..") ----------

    private static void drawFolder3D(Canvas c, boolean dark, Integer customFolderBg, boolean parentUp) {
        int tabTop, tabBot, bodyTop, bodyMid, bodyBot;
        if (customFolderBg != null) {
            int base = customFolderBg | 0xFF000000;
            bodyTop = tint(base, dark ? 0.24f : 0.18f);
            bodyMid = base;
            bodyBot = shade(base, dark ? 0.22f : 0.26f);
            tabTop = shade(base, 0.18f);
            tabBot = shade(base, 0.36f);
        } else if (dark) {
            // Luminous Rose Gold for Dark / AMOLED Black surfaces.
            tabTop = 0xFFB87467;
            tabBot = 0xFF8C4E43;
            bodyTop = 0xFFF3C3B8;
            bodyMid = 0xFFDD9587;
            bodyBot = 0xFFBA6D5F;
        } else {
            // Richer Rose Gold for crisp contrast on Light backgrounds.
            tabTop = 0xFFA25A4E;
            tabBot = 0xFF7D3F35;
            bodyTop = 0xFFE8A497;
            bodyMid = 0xFFCC7C6E;
            bodyBot = 0xFFA95B4E;
        }

        drawFolderShape3D(c, dark, tabTop, tabBot, bodyTop, bodyMid, bodyBot, !parentUp);
        if (parentUp) {
            // Crisp embossed Up/Parent return arrow centered on the front pocket.
            Path arrow = new Path();
            arrow.moveTo(50f, 73f);
            arrow.lineTo(50f, 48f);
            Path head = new Path();
            head.moveTo(38f, 58f);
            head.lineTo(50f, 46f);
            head.lineTo(62f, 58f);

            Paint shadowStroke = strokePaint(withAlpha(shade(bodyBot, 0.55f), dark ? 0x6A : 0x55), 7.2f);
            c.save();
            c.translate(0f, 2.2f);
            c.drawPath(arrow, shadowStroke);
            c.drawPath(head, shadowStroke);
            c.restore();

            Paint mainArrow = strokePaint(dark ? 0xFFFFF8F5 : 0xFFFFFFFF, 6.4f);
            c.drawPath(arrow, mainArrow);
            c.drawPath(head, mainArrow);
        }
    }

    private static void drawFolderShape3D(Canvas c, boolean dark,
                                          int tabTop, int tabBot,
                                          int bodyTop, int bodyMid, int bodyBot,
                                          boolean showPaperEdge) {
        drawSoftDropShadow(c, new RectF(6f, 26f, 94f, 86f), 10f, dark);

        // Rear folder tab + back plate.
        Path back = new Path();
        back.moveTo(6f, 25f);
        back.quadTo(6f, 15f, 16f, 15f);
        back.lineTo(35f, 15f);
        back.cubicTo(42f, 15f, 44f, 24f, 51f, 24f);
        back.lineTo(84f, 24f);
        back.quadTo(94f, 24f, 94f, 34f);
        back.lineTo(94f, 76f);
        back.quadTo(94f, 85f, 85f, 85f);
        back.lineTo(15f, 85f);
        back.quadTo(6f, 85f, 6f, 76f);
        back.close();
        c.drawPath(back, linearGrad(6f, 15f, 6f, 62f, tabTop, tabBot));

        // Tab top highlight catchlight.
        Path tabRim = new Path();
        tabRim.moveTo(9f, 23f);
        tabRim.quadTo(9f, 16.5f, 16f, 16.5f);
        tabRim.lineTo(35f, 16.5f);
        tabRim.cubicTo(41f, 16.5f, 44f, 25.5f, 51f, 25.5f);
        tabRim.lineTo(85f, 25.5f);
        c.drawPath(tabRim, strokePaint(withAlpha(Color.WHITE, dark ? 0x3C : 0x4C), 1.5f));

        if (showPaperEdge) {
            // Subtle ivory document sheet peeking between rear tab and front pocket.
            RectF sheet = new RectF(13f, 28f, 87f, 44f);
            c.drawRoundRect(sheet, 4.5f, 4.5f,
                    linearGrad(13f, 28f, 13f, 40f, 0xFFFFFCFA, dark ? 0xFFE4D6D2 : 0xFFE9E2E0));
            c.drawRoundRect(new RectF(9f, 31f, 91f, 42f), 6f, 6f,
                    fillPaint(dark ? 0x36000000 : 0x22000000));
        }

        // Front convex pocket.
        RectF front = new RectF(6f, 33f, 94f, 86f);
        c.drawRoundRect(front, 10f, 10f,
                linearGrad3(6f, 33f, 14f, 86f, bodyTop, bodyMid, bodyBot));

        // Specular bevel along top lip of the front flap.
        Path lipHi = new Path();
        lipHi.moveTo(10f, 36.5f);
        lipHi.quadTo(11f, 34.3f, 16f, 34.3f);
        lipHi.lineTo(84f, 34.3f);
        lipHi.quadTo(89f, 34.3f, 90f, 36.5f);
        c.drawPath(lipHi, strokePaint(withAlpha(Color.WHITE, dark ? 0x72 : 0x90), 1.8f));

        // Subtle outer border defines folder silhouette cleanly in Light and Dark themes.
        int edgeStroke = dark ? withAlpha(Color.WHITE, 0x24) : withAlpha(shade(bodyBot, 0.45f), 0x4E);
        c.drawRoundRect(front, 10f, 10f, strokePaint(edgeStroke, 1.2f));
    }

    // ---------- 3. Archive 3D Zip Folder ----------

    private static void drawArchiveFolder3D(Canvas c, boolean dark) {
        int tabTop = dark ? 0xFFD96500 : 0xFFC45100;
        int tabBot = dark ? 0xFF9C3D00 : 0xFF8C3200;
        int bodyTop = dark ? 0xFFFFBA42 : 0xFFFFA726;
        int bodyMid = dark ? 0xFFF57C00 : 0xFFEB6B00;
        int bodyBot = dark ? 0xFFC74600 : 0xFFB83E00;
        drawFolderShape3D(c, dark, tabTop, tabBot, bodyTop, bodyMid, bodyBot, false);

        // Recessed vertical zipper channel.
        RectF track = new RectF(56f, 33f, 70f, 86f);
        c.drawRect(track, fillPaint(0x484A1800));

        // Interlocking metallic zipper teeth.
        Paint toothPaint = linearGrad(56f, 34f, 70f, 34f, 0xFFFFFFFF, 0xFFCFD8DC);
        for (int i = 0; i < 8; i++) {
            float y = 36f + i * 6.0f;
            boolean leftTooth = (i % 2 == 0);
            float tx = leftTooth ? 56.5f : 63.0f;
            c.drawRoundRect(new RectF(tx, y, tx + 6.5f, y + 3.8f), 1.5f, 1.5f, toothPaint);
        }

        // 3D metallic zipper slider & pull ring.
        RectF sliderShadow = new RectF(53.5f, 44f, 72.5f, 67f);
        c.drawRoundRect(sliderShadow, 4f, 4f, fillPaint(0x44000000));
        RectF sliderBody = new RectF(53.5f, 42f, 72.5f, 54.5f);
        c.drawRoundRect(sliderBody, 3.8f, 3.8f,
                linearGrad(53.5f, 42f, 72.5f, 54.5f, 0xFFFFFFFF, 0xFF90A4AE));
        RectF pull = new RectF(56.5f, 52.5f, 69.5f, 68f);
        c.drawRoundRect(pull, 3.5f, 3.5f,
                linearGrad(56.5f, 52.5f, 69.5f, 68f, 0xFFECEFF1, 0xFF78909C));
        c.drawRoundRect(new RectF(59.8f, 57.5f, 66.2f, 64f), 1.8f, 1.8f, fillPaint(bodyBot));
    }

    // ---------- 4. Generic File 3D Paper ----------

    private static void drawPlainFile3D(Canvas c, boolean dark, Integer packFile) {
        int[] p = packFile != null ? tintedPageColors(packFile | 0xFF000000, dark) : pageColors(dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);
        Paint linePaint = fillPaint(dark ? 0x45DCE4F0 : 0x45607D8B);
        c.drawRoundRect(new RectF(27f, 42f, 66f, 47.5f), 2.8f, 2.8f, linePaint);
        c.drawRoundRect(new RectF(27f, 55f, 72f, 60.5f), 2.8f, 2.8f, linePaint);
        c.drawRoundRect(new RectF(27f, 68f, 56f, 73.5f), 2.8f, 2.8f, linePaint);
    }

    // ---------- 5. APK 3D Emerald Badge ----------

    private static void drawApkBadge3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(9f, 9f, 91f, 91f);
        drawRoundedTile3D(c, bounds, 22f,
                dark ? 0xFF6AE87A : 0xFF5CD669,
                0xFF2E9C3C,
                0xFF196924, dark);

        // Soft upper radial sheen for dome curvature.
        c.drawOval(new RectF(16f, 12f, 84f, 52f),
                linearGrad(50f, 12f, 50f, 52f, 0x38FFFFFF, 0x00FFFFFF));

        // 3D Android mascot head shadow + bright ivory body.
        drawAndroidHead(c, 0f, 2.5f, 0x420B2E10, 0x420B2E10);
        drawAndroidHead(c, 0f, 0f, 0xFFFFFFFF, 0xFF1B5E20);
    }

    private static void drawAndroidHead(Canvas c, float dx, float dy, int headColor, int eyeColor) {
        c.save();
        c.translate(dx, dy);
        Paint head = linearGrad(50f, 35f, 50f, 68f, headColor, headColor == 0xFFFFFFFF ? 0xFFDCFCE0 : headColor);
        Paint ant = strokePaint(headColor, 4.4f);
        c.drawLine(34f, 38f, 27f, 27f, ant);
        c.drawLine(66f, 38f, 73f, 27f, ant);

        RectF dome = new RectF(24f, 34f, 76f, 84f);
        c.drawArc(dome, 180f, 180f, true, head);
        c.drawRoundRect(new RectF(24f, 58f, 76f, 68f), 4f, 4f, head);

        Paint eye = fillPaint(eyeColor);
        c.drawCircle(39f, 50f, 3.6f, eye);
        c.drawCircle(61f, 50f, 3.6f, eye);
        c.restore();
    }

    // ---------- 6. Image 3D Frame ----------

    private static void drawImageFrame3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(9f, 9f, 91f, 91f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFFC46CE8 : 0xFFAB47BC,
                0xFF7B1FA2,
                0xFF420D6B, dark);

        // Glowing golden radial sun orb.
        c.drawCircle(33f, 34f, 13f, radialGrad(33f, 34f, 13f, 0x60FFE082, 0x00FF9800));
        c.drawCircle(33f, 34f, 8.5f, radialGrad(31f, 32f, 8.5f, 0xFFFFF8B8, 0xFFFF9800));

        // Back sunlit lavender mountain + crisp front teal-cyan mountain clipped to rounded frame.
        c.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(11f, 11f, 89f, 89f), 16f, 16f, Path.Direction.CW);
        c.clipPath(clip);

        Path backMt = new Path();
        backMt.moveTo(32f, 90f);
        backMt.lineTo(65f, 40f);
        backMt.lineTo(94f, 78f);
        backMt.lineTo(94f, 92f);
        backMt.close();
        c.drawPath(backMt, linearGrad(65f, 40f, 78f, 90f, 0xFFF3B8FF, 0xFF8E24AA));

        Path frontMt = new Path();
        frontMt.moveTo(8f, 90f);
        frontMt.lineTo(42f, 47f);
        frontMt.lineTo(74f, 90f);
        frontMt.close();
        c.drawPath(frontMt, linearGrad(42f, 47f, 42f, 90f, 0xFF80F8FF, 0xFF0097A7));
        c.restore();

        // Frosted inner Polaroid rim.
        c.drawRoundRect(new RectF(12.5f, 12.5f, 87.5f, 87.5f), 15f, 15f,
                strokePaint(0x55FFFFFF, 2.2f));
    }

    // ---------- 7. Video 3D Cinema Card ----------

    private static void drawVideoCard3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(9f, 11f, 91f, 89f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFFFF6666 : 0xFFF44336,
                0xFFD32F2F,
                0xFF8A0E12, dark);

        // Angled cinema clapper header stripes clipped inside upper strip.
        c.save();
        Path clip = new Path();
        clip.addRoundRect(bounds, 18f, 18f, Path.Direction.CW);
        c.clipPath(clip);
        c.drawRect(new RectF(9f, 11f, 91f, 29f), fillPaint(0x452A0005));
        Paint stripe = fillPaint(0x48FFFFFF);
        for (int i = 0; i < 4; i++) {
            float x = 16f + i * 20f;
            Path s = new Path();
            s.moveTo(x, 11f);
            s.lineTo(x + 9f, 11f);
            s.lineTo(x + 2f, 29f);
            s.lineTo(x - 7f, 29f);
            s.close();
            c.drawPath(s, stripe);
        }
        c.restore();

        // Translucent frosted Play circle & crisp 3D triangle.
        c.drawCircle(50f, 58f, 20f, fillPaint(0x28FFFFFF));
        Path play = new Path();
        play.moveTo(43f, 45f);
        play.lineTo(66f, 58f);
        play.lineTo(43f, 71f);
        play.close();

        c.save();
        c.translate(0f, 2.2f);
        c.drawPath(play, fillPaint(0x4A400000));
        c.restore();
        c.drawPath(play, linearGrad(43f, 45f, 66f, 71f, 0xFFFFFFFF, 0xFFFFDFDE));
    }

    // ---------- 8. Audio 3D Sonic Disc ----------

    private static void drawAudioCard3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(9f, 9f, 91f, 91f);
        drawRoundedTile3D(c, bounds, 24f,
                dark ? 0xFF42F5CD : 0xFF26D9B2,
                0xFF00897B,
                0xFF004D40, dark);

        // Acoustic wave rings.
        c.drawCircle(50f, 50f, 31f, strokePaint(0x28FFFFFF, 2.0f));
        c.drawCircle(50f, 50f, 22f, strokePaint(0x22FFFFFF, 1.8f));

        // 3D double beamed music note.
        drawMusicNote(c, 0f, 2.5f, 0x45002620);
        drawMusicNote(c, 0f, 0f, 0xFFFFFFFF);
    }

    private static void drawMusicNote(Canvas c, float dx, float dy, int color) {
        c.save();
        c.translate(dx, dy);
        Paint p = fillPaint(color);
        Paint stem = strokePaint(color, 5.5f);
        c.drawOval(new RectF(27f, 58f, 43f, 72f), p);
        c.drawOval(new RectF(54f, 53f, 70f, 67f), p);
        c.drawLine(41f, 63f, 41f, 31f, stem);
        c.drawLine(68f, 58f, 68f, 26f, stem);
        Path beam = new Path();
        beam.moveTo(39f, 30f);
        beam.lineTo(70f, 25f);
        beam.lineTo(70f, 35f);
        beam.lineTo(39f, 40f);
        beam.close();
        c.drawPath(beam, p);
        c.restore();
    }

    // ---------- 9. Sticker 3D Peeling Card ----------

    private static void drawSticker3D(Canvas c, boolean dark) {
        Path body = new Path();
        float l = 11f, t = 11f, r = 89f, b = 89f, rad = 24f, peel = 26f;
        body.moveTo(l + rad, t);
        body.lineTo(r - rad, t);
        body.quadTo(r, t, r, t + rad);
        body.lineTo(r, b - peel);
        body.lineTo(r - peel, b);
        body.lineTo(l + rad, b);
        body.quadTo(l, b, l, b - rad);
        body.lineTo(l, t + rad);
        body.quadTo(l, t, l + rad, t);
        body.close();

        c.save();
        c.translate(0f, 3.5f);
        c.drawPath(body, fillPaint(dark ? 0x3A000000 : 0x20001820));
        c.restore();

        c.drawPath(body, linearGrad3(l, t, r, b,
                dark ? 0xFF69F0FA : 0xFF38D4E6, 0xFF0097A7, 0xFF006064));
        c.drawPath(body, strokePaint(withAlpha(Color.WHITE, dark ? 0x45 : 0x80), 1.6f));

        // Cheerful embossed star mark inside sticker.
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float radPt = (i % 2 == 0) ? 20f : 9f;
            float px = 47f + (float) (Math.cos(ang) * radPt);
            float py = 48f + (float) (Math.sin(ang) * radPt);
            if (i == 0) star.moveTo(px, py);
            else star.lineTo(px, py);
        }
        star.close();
        c.save();
        c.translate(0f, 2.2f);
        c.drawPath(star, fillPaint(0x4500363A));
        c.restore();
        c.drawPath(star, linearGrad(47f, 28f, 47f, 68f, 0xFFFFFFFF, 0xFFD5FAFF));

        // Lifted peeled bottom-right corner curl.
        Path curlShadow = new Path();
        curlShadow.moveTo(r - peel, b);
        curlShadow.lineTo(r, b - peel);
        curlShadow.lineTo(r - peel + 7f, b - peel + 7f);
        curlShadow.close();
        c.drawPath(curlShadow, fillPaint(0x36000000));

        Path curl = new Path();
        curl.moveTo(r - peel, b);
        curl.lineTo(r - peel, b - peel + 6f);
        curl.quadTo(r - peel, b - peel, r - peel + 6f, b - peel);
        curl.lineTo(r, b - peel);
        curl.close();
        c.drawPath(curl, linearGrad(r - peel, b - peel, r, b, 0xFFFFFFFF, 0xFF80DEEA));
    }

    // ---------- 10. PDF 3D Ribbon Sheet ----------

    private static void drawPdfCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFFE53935, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        // Wrapped left shadow triangle under banner.
        Path wrap = new Path();
        wrap.moveTo(9f, 58f);
        wrap.lineTo(15f, 64f);
        wrap.lineTo(15f, 58f);
        wrap.close();
        c.drawPath(wrap, fillPaint(0xFF7F0000));

        // 3D Crimson Banner Ribbon.
        RectF banner = new RectF(9f, 33f, 79f, 58f);
        drawSoftDropShadow(c, banner, 6f, dark);
        c.drawRoundRect(banner, 6f, 6f, linearGrad3(9f, 33f, 79f, 58f,
                dark ? 0xFFFF6E6E : 0xFFF44336, 0xFFD32F2F, 0xFF9A0007));
        c.drawRoundRect(banner, 6f, 6f, strokePaint(0x55FFFFFF, 1.3f));

        Paint textPaint = fillPaint(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(17.5f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("PDF", 44f, 51.5f, textPaint);

        Paint subLines = fillPaint(dark ? 0x45FFCDD2 : 0x35B71C1C);
        c.drawRoundRect(new RectF(27f, 67f, 72f, 72f), 2.5f, 2.5f, subLines);
        c.drawRoundRect(new RectF(27f, 77f, 60f, 82f), 2.5f, 2.5f, subLines);
    }

    // ---------- 11. Text 3D Sheet ----------

    private static void drawTextCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFF1E88E5, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        // Glowing blue header pill + varied text lines.
        c.drawRoundRect(new RectF(26f, 33f, 54f, 40f), 3.5f, 3.5f,
                linearGrad(26f, 33f, 54f, 40f, 0xFF64B5F6, 0xFF1565C0));
        Paint lineBlue = linearGrad(26f, 48f, 72f, 82f,
                dark ? 0xFF81D4FA : 0xFF42A5F5, dark ? 0xFF29B6F6 : 0xFF1976D2);
        c.drawRoundRect(new RectF(26f, 48f, 73f, 54f), 3f, 3f, lineBlue);
        c.drawRoundRect(new RectF(26f, 60f, 73f, 66f), 3f, 3f, lineBlue);
        c.drawRoundRect(new RectF(26f, 72f, 58f, 78f), 3f, 3f, lineBlue);
    }

    // ---------- 12. Document (Word) 3D Card ----------

    private static void drawDocumentCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFF1565C0, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        RectF badge = new RectF(10f, 26f, 48f, 64f);
        drawRoundedTile3D(c, badge, 9f, 0xFF64B5F6, 0xFF1E88E5, 0xFF0D47A1, dark);

        Paint wPaint = fillPaint(Color.WHITE);
        wPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        wPaint.setTextSize(23f);
        wPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("W", 29f, 53f, wPaint);

        Paint lines = fillPaint(dark ? 0x5590CAF9 : 0x451565C0);
        c.drawRoundRect(new RectF(54f, 39f, 75f, 44.5f), 2.5f, 2.5f, lines);
        c.drawRoundRect(new RectF(54f, 51f, 75f, 56.5f), 2.5f, 2.5f, lines);
        c.drawRoundRect(new RectF(26f, 73f, 73f, 78.5f), 2.5f, 2.5f, lines);
    }

    // ---------- 13. Sheet (Excel) 3D Grid Card ----------

    private static void drawSheetCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFF2E7D32, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        RectF grid = new RectF(26f, 38f, 74f, 80f);
        drawSoftDropShadow(c, grid, 5f, dark);
        c.drawRoundRect(grid, 5f, 5f, linearGrad(26f, 38f, 74f, 80f, 0xFF4CAF50, 0xFF1B5E20));

        // Inner glossy table cells.
        Paint cellPaint = fillPaint(0xD8E8F5E9);
        for (int r = 0; r < 2; r++) {
            float cy = 53f + r * 13f;
            c.drawRoundRect(new RectF(29.5f, cy, 48.5f, cy + 9.5f), 2f, 2f, cellPaint);
            c.drawRoundRect(new RectF(51.5f, cy, 70.5f, cy + 9.5f), 2f, 2f, cellPaint);
        }
        // Highlighted top header row bars.
        c.drawRoundRect(new RectF(29.5f, 42f, 70.5f, 49.5f), 2f, 2f, fillPaint(0xAAFFFFFF));
    }

    // ---------- 14. Slide (PowerPoint) 3D Pie Chart Card ----------

    private static void drawSlideCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFFE65100, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        RectF pieRect = new RectF(30f, 40f, 70f, 80f);
        c.drawCircle(50f, 62f, 20f, fillPaint(dark ? 0x45000000 : 0x22000000));
        c.drawArc(pieRect, 0f, 270f, true, linearGrad(30f, 40f, 70f, 80f, 0xFFFF9800, 0xFFD84315));

        // Floating raised slice shifted slightly up-right for 3D pop.
        RectF sliceRect = new RectF(33f, 37f, 73f, 77f);
        c.drawArc(sliceRect, -90f, 90f, true, linearGrad(53f, 37f, 73f, 57f, 0xFFFFE082, 0xFFFF6D00));
    }

    // ---------- 15. DEX 3D Code Card ----------

    private static void drawDexCard3D(Canvas c, boolean dark) {
        int accent = dark ? 0xFF26C6DA : 0xFF00838F;
        drawFoldedPage(c, 22f,
                dark ? 0xFF1F4C54 : 0xFF26A69A,
                dark ? 0xFF102E35 : 0xFF00695C,
                dark ? 0xFF5EE8F5 : 0xFF80DEEA,
                dark ? 0xFF1C7580 : 0xFF00838F,
                withAlpha(accent, 0x80), dark);

        Paint brace = fillPaint(Color.WHITE);
        brace.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        brace.setTextSize(29f);
        brace.setTextAlign(Paint.Align.CENTER);
        c.drawText("{ }", 50f, 62f, brace);
        c.drawCircle(50f, 54f, 4.2f, radialGrad(49f, 53f, 4.2f, 0xFFFFF59D, 0xFFFFA000));
    }

    // ---------- 16. ARSC 3D Resource Stacks ----------

    private static void drawArscStacks3D(Canvas c, boolean dark) {
        drawIsometricDiamondLayer(c, 68f, dark ? 0xFFA1887F : 0xFF8D6E63, 0xFF4E342E, dark);
        drawIsometricDiamondLayer(c, 50f, 0xFFFFB74D, 0xFFE65100, dark);
        drawIsometricDiamondLayer(c, 32f, 0xFFFFF176, 0xFFFFA000, dark);
    }

    private static void drawIsometricDiamondLayer(Canvas c, float cy, int topColor, int botColor, boolean dark) {
        Path diamond = new Path();
        diamond.moveTo(50f, cy - 17f);
        diamond.lineTo(88f, cy);
        diamond.lineTo(50f, cy + 17f);
        diamond.lineTo(12f, cy);
        diamond.close();

        c.save();
        c.translate(0f, 5.5f);
        c.drawPath(diamond, fillPaint(dark ? 0x55000000 : 0x2C000000));
        c.restore();

        // Lower extruded thickness slab gives unmistakable 3D volume.
        c.save();
        c.translate(0f, 3.0f);
        c.drawPath(diamond, fillPaint(shade(botColor, 0.28f)));
        c.restore();

        c.drawPath(diamond, linearGrad(12f, cy - 17f, 88f, cy + 17f, topColor, botColor));
        c.drawPath(diamond, strokePaint(withAlpha(Color.WHITE, 0x70), 1.4f));
    }

    // ---------- 17. Database 3D Cylinders ----------

    private static void drawDatabaseCylinders3D(Canvas c, boolean dark) {
        for (int tier = 2; tier >= 0; tier--) {
            float topY = 18f + tier * 20f;
            float rx = 34f, ry = 10f;
            RectF wall = new RectF(50f - rx, topY + ry, 50f + rx, topY + ry + 18f);
            // Cylinder side wall + curved base.
            Path body = new Path();
            body.moveTo(50f - rx, topY + ry);
            body.lineTo(50f - rx, topY + ry + 17f);
            body.arcTo(new RectF(50f - rx, topY + 17f, 50f + rx, topY + 17f + ry * 2f), 180f, -180f, false);
            body.lineTo(50f + rx, topY + ry);
            body.close();

            c.save();
            c.translate(0f, 3f);
            c.drawPath(body, fillPaint(dark ? 0x45000000 : 0x24001815));
            c.restore();

            c.drawPath(body, linearGrad(wall.left, topY, wall.right, topY + 28f,
                    dark ? 0xFF4DB6AC : 0xFF26A69A, dark ? 0xFF004D40 : 0xFF005B4F));

            // Bright top elliptical disc cap.
            RectF cap = new RectF(50f - rx, topY, 50f + rx, topY + ry * 2f);
            c.drawOval(cap, linearGrad(cap.left, cap.top, cap.right, cap.bottom,
                    tier == 0 ? 0xFFB2FFF5 : 0xFF80CBC4, 0xFF00897B));
            c.drawOval(cap, strokePaint(0x60FFFFFF, 1.2f));
        }
    }

    // ---------- 18. Disk Image 3D Metal Drive ----------

    private static void drawDiskDrive3D(Canvas c, boolean dark) {
        RectF body = new RectF(13f, 9f, 87f, 91f);
        drawRoundedTile3D(c, body, 12f,
                dark ? 0xFFC5D0D8 : 0xFFCFD8DC,
                0xFF78909C,
                0xFF37474F, dark);

        // Recessed silver spinning platter inside upper drive bay.
        c.drawCircle(50f, 43f, 24.5f, fillPaint(0xFF263238));
        c.drawCircle(50f, 43f, 21.5f, radialGrad(44f, 36f, 25f, 0xFFFFFFFF, 0xFF90A4AE));
        c.drawCircle(50f, 43f, 7.5f, fillPaint(0xFF455A64));
        c.drawCircle(50f, 43f, 3.8f, fillPaint(0xFFECEFF1));

        // Lower bezel with glowing status LED.
        c.drawRoundRect(new RectF(21f, 74f, 79f, 84f), 4f, 4f, fillPaint(0xFF263238));
        c.drawRoundRect(new RectF(56f, 77.5f, 74f, 80.5f), 1.5f, 1.5f,
                linearGrad(56f, 77f, 74f, 80f, 0xFF69F0AE, 0xFF00BFA5));
    }

    // ---------- 19. ELF Native Processor 3D ----------

    private static void drawElfProcessor3D(Canvas c, boolean dark) {
        // Metallic gold pins along sides & top/bottom.
        Paint pinPaint = linearGrad(10f, 20f, 90f, 80f, 0xFFFFE082, 0xFFF57F17);
        for (int i = 0; i < 3; i++) {
            float pos = 30f + i * 18f;
            c.drawRoundRect(new RectF(6f, pos - 3.5f, 18f, pos + 3.5f), 2f, 2f, pinPaint);
            c.drawRoundRect(new RectF(82f, pos - 3.5f, 94f, pos + 3.5f), 2f, 2f, pinPaint);
            c.drawRoundRect(new RectF(pos - 3.5f, 6f, pos + 3.5f, 18f), 2f, 2f, pinPaint);
            c.drawRoundRect(new RectF(pos - 3.5f, 82f, pos + 3.5f, 94f), 2f, 2f, pinPaint);
        }

        RectF chip = new RectF(15f, 15f, 85f, 85f);
        drawRoundedTile3D(c, chip, 14f,
                dark ? 0xFF546E7A : 0xFF455A64,
                0xFF2E3B42,
                0xFF182024, dark);

        c.drawRoundRect(new RectF(24f, 24f, 76f, 76f), 8f, 8f, strokePaint(0x5069F0AE, 1.8f));
        Paint label = fillPaint(0xFF69F0AE);
        label.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        label.setTextSize(20f);
        label.setTextAlign(Paint.Align.CENTER);
        c.drawText("ELF", 50f, 57f, label);
    }

    // ---------- 20. Script 3D Terminal Window ----------

    private static void drawScriptTerminal3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(9f, 13f, 91f, 87f);
        drawRoundedTile3D(c, bounds, 15f,
                dark ? 0xFF6D7CE0 : 0xFF5C6BC0,
                0xFF3949AB,
                0xFF1A237E, dark);

        // Title bar traffic lights.
        c.drawCircle(23f, 24f, 3.6f, fillPaint(0xFFFF6B6B));
        c.drawCircle(34f, 24f, 3.6f, fillPaint(0xFFFFD93D));
        c.drawCircle(45f, 24f, 3.6f, fillPaint(0xFF6BCB77));

        // Inner midnight terminal glass pane.
        c.drawRoundRect(new RectF(15f, 33f, 85f, 80f), 9f, 9f, fillPaint(0x9A10142E));

        Paint prompt = strokePaint(0xFF69F0AE, 5.2f);
        Path chevron = new Path();
        chevron.moveTo(27f, 47f);
        chevron.lineTo(41f, 57f);
        chevron.lineTo(27f, 67f);
        c.drawPath(chevron, prompt);
        c.drawLine(49f, 67f, 69f, 67f, strokePaint(0xFFFFFFFF, 5.0f));
    }

    // ---------- 21. Font 3D Typography Card ----------

    private static void drawFontCard3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(10f, 10f, 90f, 90f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFFFF9A76 : 0xFFFF7043,
                0xFFE64A19,
                0xFF9F2808, dark);

        Paint text = fillPaint(0x45501000);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextSize(37f);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("Aa", 50f, 65.5f, text);

        text.setShader(new LinearGradient(50f, 32f, 50f, 64f, 0xFFFFFFFF, 0xFFFFE0D6, Shader.TileMode.CLAMP));
        c.drawText("Aa", 50f, 63f, text);
        c.drawLine(25f, 73f, 75f, 73f, strokePaint(0x60FFFFFF, 2.5f));
    }

    // ---------- 22. Proto / Data 3D Node Card ----------

    private static void drawProtoCard3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(10f, 10f, 90f, 90f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFFA480F2 : 0xFF7E57C2,
                0xFF512DA8,
                0xFF28106A, dark);

        Paint edge = strokePaint(0xB8E0D7FF, 4.2f);
        c.drawLine(32f, 34f, 68f, 50f, edge);
        c.drawLine(68f, 50f, 34f, 68f, edge);
        c.drawLine(32f, 34f, 34f, 68f, strokePaint(0x70E0D7FF, 3.2f));

        drawGlowNode(c, 32f, 34f, 9.5f, 0xFFFFFFFF, 0xFF80D8FF);
        drawGlowNode(c, 68f, 50f, 11.0f, 0xFFFFF59D, 0xFFFFB300);
        drawGlowNode(c, 34f, 68f, 9.0f, 0xFFFFFFFF, 0xFFE1BEE7);
    }

    private static void drawGlowNode(Canvas c, float cx, float cy, float r, int hi, int edge) {
        c.drawCircle(cx, cy + 2f, r, fillPaint(0x45000000));
        c.drawCircle(cx, cy, r, radialGrad(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, hi, edge));
    }

    // ---------- 23. Backup 3D Time Capsule Card ----------

    private static void drawBackupCard3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(10f, 10f, 90f, 90f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFFA88376 : 0xFF8D6E63,
                0xFF5D4037,
                0xFF36211A, dark);

        Paint arc = strokePaint(0xFFFFD54F, 6.2f);
        c.drawArc(new RectF(27f, 27f, 73f, 73f), -220f, 285f, false, arc);

        Path arrow = new Path();
        arrow.moveTo(21f, 33f);
        arrow.lineTo(35f, 26f);
        arrow.lineTo(34f, 41f);
        arrow.close();
        c.drawPath(arrow, fillPaint(0xFFFFD54F));

        // Clock hands.
        Paint hand = strokePaint(Color.WHITE, 4.8f);
        c.drawLine(50f, 50f, 50f, 36f, hand);
        c.drawLine(50f, 50f, 60f, 56f, hand);
    }

    // ---------- 24. Certificate 3D Gold Wax Seal Sheet ----------

    private static void drawCertificateCard3D(Canvas c, boolean dark) {
        int[] p = tintedPageColors(0xFFF57F17, dark);
        drawFoldedPage(c, 22f, p[0], p[1], p[2], p[3], p[4], dark);

        // Twin ribbon tails behind seal.
        Paint ribbon = linearGrad(38f, 52f, 62f, 82f, 0xFFFF6E40, 0xFFC62828);
        Path leftTail = new Path();
        leftTail.moveTo(39f, 54f);
        leftTail.lineTo(33f, 81f);
        leftTail.lineTo(42f, 76f);
        leftTail.lineTo(48f, 82f);
        leftTail.lineTo(48f, 54f);
        leftTail.close();
        c.drawPath(leftTail, ribbon);

        Path rightTail = new Path();
        rightTail.moveTo(52f, 54f);
        rightTail.lineTo(52f, 82f);
        rightTail.lineTo(58f, 76f);
        rightTail.lineTo(67f, 81f);
        rightTail.lineTo(61f, 54f);
        rightTail.close();
        c.drawPath(rightTail, ribbon);

        // Scalloped 3D Gold Wax Seal Rosette.
        c.drawCircle(50f, 47f, 20f, fillPaint(dark ? 0x45000000 : 0x26000000));
        c.drawCircle(50f, 45f, 19.5f, radialGrad(44f, 38f, 23f, 0xFFFFF59D, 0xFFF57F17));
        c.drawCircle(50f, 45f, 14f, strokePaint(0x70FFFFFF, 1.8f));

        Path check = new Path();
        check.moveTo(41f, 45f);
        check.lineTo(47.5f, 51.5f);
        check.lineTo(59.5f, 38.5f);
        c.drawPath(check, strokePaint(Color.WHITE, 4.4f));
    }

    // ---------- 25. Binary 3D Dual Gears ----------

    private static void drawBinaryGears3D(Canvas c, boolean dark) {
        RectF bounds = new RectF(10f, 10f, 90f, 90f);
        drawRoundedTile3D(c, bounds, 18f,
                dark ? 0xFF78909C : 0xFF607D8B,
                0xFF455A64,
                0xFF263238, dark);

        drawGear(c, 40f, 42f, 19f, 14f, 7f, 8, 0xFFFFFFFF, 0xFFB0BEC5, dark);
        drawGear(c, 66f, 65f, 14f, 10f, 4.8f, 6, 0xFFFFE082, 0xFFFF9800, dark);
    }

    private static void drawGear(Canvas c, float cx, float cy, float outerR, float innerR,
                                 float holeR, int teeth, int topColor, int botColor, boolean dark) {
        Path gear = new Path();
        for (int i = 0; i < teeth * 2; i++) {
            double a0 = i * Math.PI / teeth;
            double a1 = (i + 0.55) * Math.PI / teeth;
            float r = (i % 2 == 0) ? outerR : innerR;
            float x0 = cx + (float) (Math.cos(a0) * r);
            float y0 = cy + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r);
            float y1 = cy + (float) (Math.sin(a1) * r);
            if (i == 0) gear.moveTo(x0, y0);
            else gear.lineTo(x0, y0);
            gear.lineTo(x1, y1);
        }
        gear.close();
        c.save();
        c.translate(0f, 2.2f);
        c.drawPath(gear, fillPaint(dark ? 0x50000000 : 0x30000000));
        c.restore();
        c.drawPath(gear, linearGrad(cx - outerR, cy - outerR, cx + outerR, cy + outerR, topColor, botColor));
        c.drawCircle(cx, cy, holeR, fillPaint(0xFF263238));
    }
}
