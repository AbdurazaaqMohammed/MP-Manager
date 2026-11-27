package io.github.abdurazaaqmohammed.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import com.caverock.androidsvg.SVG;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import io.github.abdurazaaqmohammed.util.io.FileUtils;

/**
 * Decodes SVG and gzipped SVGZ files/streams via AndroidSVG into bounded,
 * checkerboard-backed bitmaps for viewer display and file-list thumbnails.
 */
public final class SvgRenderer {

    private static final int MIN_VIEWER_DIM = 1024;
    private static final int DEFAULT_DIM = 512;

    private SvgRenderer() {}

    /** Returns {@code true} if {@code pathOrName} has a {@code .svg} or {@code .svgz} extension. */
    public static boolean isSvgName(String pathOrName) {
        if (pathOrName == null) return false;
        String lower = pathOrName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".svg") || lower.endsWith(".svgz");
    }

    /** Computes scaled integer {@code [width, height]} preserving aspect ratio within bounds. */
    public static int[] computeTargetSize(float docWidth, float docHeight, RectF viewBox,
                                          int minLongEdge, int maxLongEdge) {
        float w = docWidth;
        float h = docHeight;
        if ((w <= 0f || h <= 0f) && viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
            w = viewBox.width();
            h = viewBox.height();
        }
        if (w <= 0f || h <= 0f) {
            w = DEFAULT_DIM;
            h = DEFAULT_DIM;
        }
        float longEdge = Math.max(w, h);
        float scale = 1f;
        if (maxLongEdge > 0 && longEdge > maxLongEdge) {
            scale = maxLongEdge / longEdge;
        } else if (minLongEdge > 0 && longEdge < minLongEdge) {
            scale = minLongEdge / longEdge;
        }
        int outW = Math.max(1, Math.round(w * scale));
        int outH = Math.max(1, Math.round(h * scale));
        return new int[]{outW, outH};
    }

    /** Reads logical SVG width and height, or {@code null} when unparseable. */
    public static int[] measureFile(String filePath) {
        if (!isSvgName(filePath)) return null;
        try (InputStream in = new FileInputStream(filePath)) {
            SVG svg = parseSvg(in);
            if (svg == null) return null;
            float w = svg.getDocumentWidth();
            float h = svg.getDocumentHeight();
            RectF vb = svg.getDocumentViewBox();
            if ((w <= 0f || h <= 0f) && vb != null && vb.width() > 0f && vb.height() > 0f) {
                w = vb.width();
                h = vb.height();
            }
            if (w <= 0f || h <= 0f) return null;
            return new int[]{Math.max(1, Math.round(w)), Math.max(1, Math.round(h))};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Renders a full-resolution SVG bitmap suitable for zooming inside {@link ImageViewerActivity}. */
    public static Bitmap decodeViewerBitmap(String filePath, int maxSize) {
        if (!isSvgName(filePath)) return null;
        int minEdge = Math.min(MIN_VIEWER_DIM, Math.max(1, maxSize));
        try (InputStream in = new FileInputStream(filePath)) {
            return decodeStream(in, minEdge, maxSize, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Renders a thumbnail bitmap from a local/SAF SVG path. */
    public static Bitmap decodeThumbnail(String path, int maxSize) {
        if (!isSvgName(path)) return null;
        try (InputStream in = FileUtils.getInputStream(new File(path))) {
            return decodeStream(in, maxSize, maxSize, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Renders a thumbnail bitmap directly from buffered SVG/SVGZ bytes. */
    public static Bitmap decodeBytes(byte[] data, int maxSize) {
        if (data == null || data.length == 0) return null;
        try (InputStream in = new ByteArrayInputStream(data)) {
            return decodeStream(in, maxSize, maxSize, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Renders an SVG or SVGZ stream into a bounded bitmap. */
    public static Bitmap decodeStream(InputStream rawStream, int minLongEdge, int maxLongEdge, boolean checkerboard) {
        if (rawStream == null) return null;
        try {
            SVG svg = parseSvg(rawStream);
            if (svg == null) return null;
            float docW = svg.getDocumentWidth();
            float docH = svg.getDocumentHeight();
            RectF viewBox = svg.getDocumentViewBox();
            if (viewBox == null && docW > 0f && docH > 0f) {
                svg.setDocumentViewBox(0f, 0f, docW, docH);
                viewBox = svg.getDocumentViewBox();
            }
            int[] size = computeTargetSize(docW, docH, viewBox, minLongEdge, maxLongEdge);
            int w = size[0];
            int h = size[1];
            svg.setDocumentWidth(w);
            svg.setDocumentHeight(h);

            Bitmap rendered = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(rendered);
            svg.renderToCanvas(canvas, new RectF(0f, 0f, w, h));
            if (!checkerboard) return rendered;
            int squarePx = maxLongEdge <= 256 ? Math.max(6, w / 8) : Math.max(12, Math.min(w, h) / 24);
            Bitmap backed = ApkDrawableRenderer.onCheckerboard(rendered, squarePx);
            rendered.recycle();
            return backed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SVG parseSvg(InputStream rawStream) throws Exception {
        BufferedInputStream buffered = new BufferedInputStream(rawStream, 8192);
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        if (b0 == 0x1F && b1 == 0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(buffered)) {
                return SVG.getFromInputStream(gzip);
            }
        }
        return SVG.getFromInputStream(buffered);
    }
}
