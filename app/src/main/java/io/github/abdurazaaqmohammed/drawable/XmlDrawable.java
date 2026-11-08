package io.github.abdurazaaqmohammed.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Drawable} inflated from plain (or AXML-decoded) XML, without the
 * framework inflater.
 *
 * <p>{@code Drawable.createFromXml()} cannot be used here: both
 * {@code Resources.obtainAttributes} and {@code Theme.obtainStyledAttributes}
 * cast the AttributeSet to {@code XmlBlock.Parser}, so any text-XML parser
 * fails with a ClassCastException. Rendering to a Canvas directly is the only
 * portable route, and it also gives us control over states, levels and alpha
 * for the preview.</p>
 *
 * <p>Supported roots: shape, selector, layer-list, level-list, inset, clip,
 * scale, rotate, ripple, transition, animation-list, bitmap, nine-patch, color,
 * vector (including groups, paths, clip-paths and gradients) and adaptive-icon.
 * When the APK's own {@code Resources} are available, nested
 * {@code @drawable/...} references are inflated by the framework instead, so
 * those keep 100% fidelity.</p>
 */
final class XmlDrawable extends Drawable {

    private static final Map<String, Integer> STATE_ATTRS = new HashMap<>();

    static {
        STATE_ATTRS.put("state_pressed", android.R.attr.state_pressed);
        STATE_ATTRS.put("state_focused", android.R.attr.state_focused);
        STATE_ATTRS.put("state_selected", android.R.attr.state_selected);
        STATE_ATTRS.put("state_checkable", android.R.attr.state_checkable);
        STATE_ATTRS.put("state_checked", android.R.attr.state_checked);
        STATE_ATTRS.put("state_enabled", android.R.attr.state_enabled);
        STATE_ATTRS.put("state_activated", android.R.attr.state_activated);
        STATE_ATTRS.put("state_window_focused", android.R.attr.state_window_focused);
        STATE_ATTRS.put("state_hovered", android.R.attr.state_hovered);
        STATE_ATTRS.put("state_first", android.R.attr.state_first);
        STATE_ATTRS.put("state_middle", android.R.attr.state_middle);
        STATE_ATTRS.put("state_last", android.R.attr.state_last);
        STATE_ATTRS.put("state_single", android.R.attr.state_single);
        STATE_ATTRS.put("state_accelerated", android.R.attr.state_accelerated);
        STATE_ATTRS.put("state_drag_can_accept", android.R.attr.state_drag_can_accept);
        STATE_ATTRS.put("state_drag_hovered", android.R.attr.state_drag_hovered);
    }

    private final XmlNode root;
    private final DrawableResolver res;
    private final int depth;
    private int alpha = 255;
    private ColorFilter colorFilter;
    private int intrinsicWidth = -1;
    private int intrinsicHeight = -1;

    XmlDrawable(XmlNode root, DrawableResolver res, int depth) {
        this.root = root;
        this.res = res;
        this.depth = depth;
        measure();
    }

    private void measure() {
        if (root == null) return;
        if ("vector".equals(root.tag)) {
            intrinsicWidth = (int) res.dimensionPx(root.attr("width"), -1);
            intrinsicHeight = (int) res.dimensionPx(root.attr("height"), -1);
        } else if ("shape".equals(root.tag)) {
            XmlNode size = root.first("size");
            if (size != null) {
                intrinsicWidth = (int) res.dimensionPx(size.attr("width"), -1);
                intrinsicHeight = (int) res.dimensionPx(size.attr("height"), -1);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (root == null) return;
        renderNode(canvas, getBounds(), root, 0);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        colorFilter = cf;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        invalidateSelf();
        return true;
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    // ------------------------------------------------------------------ switch

    private void renderNode(Canvas canvas, Rect bounds, XmlNode node, int d) {
        if (node == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0
                || d > 12) return;
        switch (node.tag) {
            case "shape":
                drawShape(canvas, bounds, node);
                break;
            case "selector":
            case "animated-selector":
                drawSelector(canvas, bounds, node, d);
                break;
            case "layer-list":
                drawLayerList(canvas, bounds, node, d);
                break;
            case "level-list":
                drawLevelList(canvas, bounds, node, d);
                break;
            case "inset":
                drawInset(canvas, bounds, node, d);
                break;
            case "clip":
                drawClip(canvas, bounds, node, d);
                break;
            case "scale":
                drawScale(canvas, bounds, node, d);
                break;
            case "rotate":
                drawRotate(canvas, bounds, node, d);
                break;
            case "ripple":
                drawRipple(canvas, bounds, node, d);
                break;
            case "transition":
            case "animation-list": {
                List<XmlNode> items = node.all("item");
                if (!items.isEmpty()) {
                    int index = getLevel() >= 5000 && items.size() > 1 ? 1 : 0;
                    drawItem(canvas, bounds, items.get(index), d + 1);
                }
                break;
            }
            case "adaptive-icon":
                drawAdaptiveIcon(canvas, bounds, node, d);
                break;
            case "bitmap":
            case "nine-patch":
                drawRef(canvas, bounds, node.attr("src"), d);
                break;
            case "color":
                fillColor(canvas, bounds, res.color(node.attr("color"), Color.TRANSPARENT));
                break;
            case "vector":
                drawVector(canvas, bounds, node, d);
                break;
            case "item":
                drawItem(canvas, bounds, node, d);
                break;
            default:
                if (!drawRef(canvas, bounds, node.attr("drawable"), d)) {
                    for (XmlNode child : node.children) {
                        renderNode(canvas, bounds, child, d + 1);
                    }
                }
                break;
        }
    }

    private boolean drawRef(Canvas canvas, Rect bounds, String ref, int d) {
        if (ref == null || ref.trim().isEmpty()) return false;
        Drawable child = res.drawable(ref, depth + d);
        if (child == null) return false;
        drawChild(canvas, bounds, child);
        return true;
    }

    private void drawChild(Canvas canvas, Rect bounds, Drawable child) {
        child.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
        child.setState(getState());
        child.setLevel(getLevel());
        child.setAlpha(alpha);
        if (colorFilter != null) child.setColorFilter(colorFilter);
        child.draw(canvas);
    }

    private void fillColor(Canvas canvas, Rect bounds, int color) {
        if (Color.alpha(color) == 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setAlpha((int) (Color.alpha(color) * (alpha / 255f)));
        if (colorFilter != null) p.setColorFilter(colorFilter);
        canvas.drawRect(bounds, p);
    }

    // ------------------------------------------------------------------- shape

    private void drawShape(Canvas canvas, Rect bounds, XmlNode node) {
        GradientDrawable gd = new GradientDrawable();
        String shape = res.text(node.attr("shape", "rectangle"));
        int shapeType = GradientDrawable.RECTANGLE;
        if ("oval".equals(shape)) shapeType = GradientDrawable.OVAL;
        else if ("line".equals(shape)) shapeType = GradientDrawable.LINE;
        else if ("ring".equals(shape)) shapeType = GradientDrawable.RING;
        gd.setShape(shapeType);

        XmlNode gradient = node.first("gradient");
        if (gradient != null) {
            int start = res.color(gradient.attr("startColor"), Color.TRANSPARENT);
            int end = res.color(gradient.attr("endColor"), Color.TRANSPARENT);
            String centerRaw = gradient.attr("centerColor");
            int[] colors;
            if (centerRaw != null) {
                colors = new int[]{start, res.color(centerRaw, start), end};
            } else {
                colors = new int[]{start, end};
            }
            try {
                gd.setColors(colors);
            } catch (Throwable ignored) {
            }
            float angle = res.number(gradient.attr("angle"), 0f);
            try {
                gd.setOrientation(angleToOrientation(angle));
            } catch (Throwable ignored) {
            }
            String type = res.text(gradient.attr("type", "linear"));
            if ("radial".equals(type)) {
                gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                gd.setGradientRadius(res.dimensionPx(gradient.attr("gradientRadius"), 100f));
                gd.setGradientCenter(res.number(gradient.attr("centerX"), 0.5f),
                        res.number(gradient.attr("centerY"), 0.5f));
            } else if ("sweep".equals(type)) {
                gd.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                gd.setGradientCenter(res.number(gradient.attr("centerX"), 0.5f),
                        res.number(gradient.attr("centerY"), 0.5f));
            } else {
                gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            }
            gd.setUseLevel(res.bool(gradient.attr("useLevel"), false));
        } else {
            XmlNode solid = node.first("solid");
            int c = solid == null ? Color.TRANSPARENT
                    : res.color(solid.attr("color"), Color.TRANSPARENT);
            gd.setColor(c);
        }

        XmlNode corners = node.first("corners");
        if (corners != null) {
            float radius = res.dimensionPx(corners.attr("radius"), 0f);
            if (radius > 0) {
                gd.setCornerRadius(radius);
            } else {
                float tl = res.dimensionPx(corners.attr("topLeftRadius"), 0f);
                float tr = res.dimensionPx(corners.attr("topRightRadius"), 0f);
                float bl = res.dimensionPx(corners.attr("bottomLeftRadius"), 0f);
                float br = res.dimensionPx(corners.attr("bottomRightRadius"), 0f);
                gd.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
            }
        }
        XmlNode stroke = node.first("stroke");
        if (stroke != null) {
            int width = (int) res.dimensionPx(stroke.attr("width"), 0f);
            int color = res.color(stroke.attr("color"), Color.TRANSPARENT);
            float dash = res.dimensionPx(stroke.attr("dashWidth"), 0f);
            float gap = res.dimensionPx(stroke.attr("dashGap"), 0f);
            if (width > 0) {
                if (dash > 0) gd.setStroke(width, color, dash, gap);
                else gd.setStroke(width, color);
            }
        }
        XmlNode size = node.first("size");
        if (size != null) {
            gd.setSize((int) res.dimensionPx(size.attr("width"), -1),
                    (int) res.dimensionPx(size.attr("height"), -1));
        }
        gd.setBounds(bounds);
        gd.setAlpha(alpha);
        if (colorFilter != null) gd.setColorFilter(colorFilter);
        gd.draw(canvas);
    }

    private static GradientDrawable.Orientation angleToOrientation(float angle) {
        float a = ((angle % 360) + 360) % 360;
        if (a >= 337.5f || a < 22.5f) return GradientDrawable.Orientation.LEFT_RIGHT;
        if (a < 67.5f) return GradientDrawable.Orientation.BL_TR;
        if (a < 112.5f) return GradientDrawable.Orientation.BOTTOM_TOP;
        if (a < 157.5f) return GradientDrawable.Orientation.BR_TL;
        if (a < 202.5f) return GradientDrawable.Orientation.RIGHT_LEFT;
        if (a < 247.5f) return GradientDrawable.Orientation.TR_BL;
        if (a < 292.5f) return GradientDrawable.Orientation.TOP_BOTTOM;
        return GradientDrawable.Orientation.TL_BR;
    }

    // ---------------------------------------------------------------- selector

    private void drawSelector(Canvas canvas, Rect bounds, XmlNode node, int d) {
        List<XmlNode> items = node.all("item");
        if (items.isEmpty()) return;
        XmlNode selected = null;
        XmlNode fallback = null;
        int[] state = getState();
        for (XmlNode item : items) {
            boolean hasStateSpec = false;
            for (String key : item.attrs.keySet()) {
                if (STATE_ATTRS.containsKey(key)) {
                    hasStateSpec = true;
                    break;
                }
            }
            if (!hasStateSpec) {
                fallback = item;
                continue;
            }
            if (selected == null && matchesState(item, state)) selected = item;
        }
        if (selected == null) selected = fallback;
        if (selected != null) drawItem(canvas, bounds, selected, d + 1);
    }

    private void drawLevelList(Canvas canvas, Rect bounds, XmlNode node, int d) {
        int level = getLevel();
        XmlNode chosen = null;
        int bestMin = Integer.MIN_VALUE;
        for (XmlNode item : node.all("item")) {
            int min = res.integer(item.attr("minLevel"), 0);
            int max = res.integer(item.attr("maxLevel"), Integer.MAX_VALUE);
            if (level >= min && level <= max && min >= bestMin) {
                bestMin = min;
                chosen = item;
            }
        }
        if (chosen != null) drawItem(canvas, bounds, chosen, d + 1);
    }

    private boolean matchesState(XmlNode item, int[] state) {
        for (Map.Entry<String, String> e : item.attrs.entrySet()) {
            Integer attr = STATE_ATTRS.get(e.getKey());
            if (attr == null) continue;
            boolean want = res.bool(e.getValue(), false);
            if (want != contains(state, attr)) return false;
        }
        return true;
    }

    private static boolean contains(int[] state, int attr) {
        if (state == null) return false;
        for (int i : state) {
            if (i == attr) return true;
        }
        return false;
    }

    private void drawItem(Canvas canvas, Rect bounds, XmlNode item, int d) {
        String ref = item.attr("drawable");
        if (ref != null && drawRef(canvas, bounds, ref, d)) return;
        for (XmlNode child : item.children) {
            renderNode(canvas, bounds, child, d + 1);
        }
    }

    private Rect inset(Rect bounds, XmlNode node, String left, String top,
                       String right, String bottom) {
        int l = (int) res.dimensionPx(node.attr(left), 0f);
        int t = (int) res.dimensionPx(node.attr(top), 0f);
        int r = (int) res.dimensionPx(node.attr(right), 0f);
        int b = (int) res.dimensionPx(node.attr(bottom), 0f);
        Rect out = new Rect(bounds.left + l, bounds.top + t,
                bounds.right - r, bounds.bottom - b);
        return out;
    }

    // ------------------------------------------------------- wrappers / layers

    private void drawLayerList(Canvas canvas, Rect bounds, XmlNode node, int d) {
        for (XmlNode item : node.all("item")) {
            Rect r = inset(bounds, item, "left", "top", "right", "bottom");
            drawItem(canvas, r, item, d + 1);
        }
    }

    private void drawInset(Canvas canvas, Rect bounds, XmlNode node, int d) {
        float all = res.dimensionPx(node.attr("inset"), -1f);
        Rect r;
        if (all >= 0) {
            int v = (int) all;
            r = new Rect(bounds.left + v, bounds.top + v, bounds.right - v, bounds.bottom - v);
        } else {
            r = inset(bounds, node, "insetLeft", "insetTop", "insetRight", "insetBottom");
        }
        if (!drawRef(canvas, r, node.attr("drawable"), d)) {
            for (XmlNode child : node.children) renderNode(canvas, r, child, d + 1);
        }
    }

    private void drawClip(Canvas canvas, Rect bounds, XmlNode node, int d) {
        float fraction = Math.max(0f, Math.min(1f, getLevel() / 10000f));
        boolean horizontal = !"vertical".equals(res.text(node.attr("clipOrientation", "horizontal")));
        int save = canvas.save();
        Rect clip = new Rect(bounds);
        if (horizontal) clip.right = bounds.left + (int) (bounds.width() * fraction);
        else clip.top = bounds.bottom - (int) (bounds.height() * fraction);
        canvas.clipRect(clip);
        renderContent(canvas, bounds, node, d);
        canvas.restoreToCount(save);
    }

    private void drawScale(Canvas canvas, Rect bounds, XmlNode node, int d) {
        float fraction = Math.max(0f, Math.min(1f, getLevel() / 10000f));
        float scaleX = res.number(node.attr("scaleWidth"), 1f);
        float scaleY = res.number(node.attr("scaleHeight"), 1f);
        float sx = 1f - scaleX * (1f - fraction);
        float sy = 1f - scaleY * (1f - fraction);
        int save = canvas.save();
        canvas.scale(sx, sy, bounds.centerX(), bounds.centerY());
        renderContent(canvas, bounds, node, d);
        canvas.restoreToCount(save);
    }

    private void drawRotate(Canvas canvas, Rect bounds, XmlNode node, int d) {
        float from = res.number(node.attr("fromDegrees"), 0f);
        float to = res.number(node.attr("toDegrees"), 360f);
        float level = Math.max(0f, Math.min(1f, getLevel() / 10000f));
        float angle = from + (to - from) * level;
        float px = res.number(node.attr("pivotX"), 50f) / 100f * bounds.width();
        float py = res.number(node.attr("pivotY"), 50f) / 100f * bounds.height();
        int save = canvas.save();
        canvas.rotate(angle, bounds.left + px, bounds.top + py);
        renderContent(canvas, bounds, node, d);
        canvas.restoreToCount(save);
    }

    private void drawRipple(Canvas canvas, Rect bounds, XmlNode node, int d) {
        for (XmlNode item : node.all("item")) {
            drawItem(canvas, bounds, item, d + 1);
        }
        if (node.children.isEmpty()) {
            // A ripple with no child is a solid mask; paint its colour faintly.
            int c = res.color(node.attr("color"), Color.TRANSPARENT);
            if (c != Color.TRANSPARENT) fillColor(canvas, bounds, (c & 0x00FFFFFF) | 0x22000000);
        } else if (contains(getState(), android.R.attr.state_pressed)) {
            int c = res.color(node.attr("color"), Color.TRANSPARENT);
            if (c != Color.TRANSPARENT) fillColor(canvas, bounds, (c & 0x00FFFFFF) | 0x55000000);
        }
    }

    private void drawAdaptiveIcon(Canvas canvas, Rect bounds, XmlNode node, int d) {
        for (String tag : new String[]{"background", "foreground", "monochrome"}) {
            XmlNode layer = node.first(tag);
            if (layer == null) continue;
            if (!drawRef(canvas, bounds, layer.attr("drawable"), d)) {
                for (XmlNode child : layer.children) renderNode(canvas, bounds, child, d + 1);
            }
        }
    }

    private void renderContent(Canvas canvas, Rect bounds, XmlNode node, int d) {
        if (!drawRef(canvas, bounds, node.attr("drawable"), d)) {
            for (XmlNode child : node.children) renderNode(canvas, bounds, child, d + 1);
        }
    }

    // ------------------------------------------------------------------ vector

    private void drawVector(Canvas canvas, Rect bounds, XmlNode node, int d) {
        float vw = res.number(node.attr("viewportWidth"), bounds.width());
        float vh = res.number(node.attr("viewportHeight"), bounds.height());
        if (vw <= 0 || vh <= 0) return;
        final float vectorAlpha = res.number(node.attr("alpha"), 1f);
        int tint = res.color(node.attr("tint"), 0);
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / vw, bounds.height() / vh);
        for (XmlNode child : node.children) {
            drawVectorPart(canvas, child, vectorAlpha, tint, d + 1);
        }
        canvas.restoreToCount(save);
    }

    private void drawVectorPart(Canvas canvas, XmlNode node, float vectorAlpha,
                                int tint, int d) {
        if (d > 12) return;
        switch (node.tag) {
            case "group": {
                int save = canvas.save();
                float px = res.number(node.attr("pivotX"), 0f);
                float py = res.number(node.attr("pivotY"), 0f);
                float tx = res.number(node.attr("translateX"), 0f);
                float ty = res.number(node.attr("translateY"), 0f);
                canvas.translate(tx, ty);
                float rotation = res.number(node.attr("rotation"), 0f);
                if (rotation != 0f) canvas.rotate(rotation, px, py);
                float sx = res.number(node.attr("scaleX"), 1f);
                float sy = res.number(node.attr("scaleY"), 1f);
                if (sx != 1f || sy != 1f) canvas.scale(sx, sy, px, py);
                for (XmlNode child : node.children) {
                    drawVectorPart(canvas, child, vectorAlpha, tint, d + 1);
                }
                canvas.restoreToCount(save);
                break;
            }
            case "clip-path": {
                Path p = VectorPath.parse(node.attr("pathData"));
                if (!p.isEmpty()) canvas.clipPath(p);
                break;
            }
            case "path":
                drawVectorPath(canvas, node, vectorAlpha, tint);
                break;
            case "gradient":
                break;
            default:
                for (XmlNode child : node.children) {
                    drawVectorPart(canvas, child, vectorAlpha, tint, d + 1);
                }
                break;
        }
    }

    private void drawVectorPath(Canvas canvas, XmlNode node, float vectorAlpha, int tint) {
        String data = node.attr("pathData");
        if (data == null) return;
        Path path = VectorPath.parse(data);
        if (path.isEmpty()) return;
        if ("evenOdd".equals(res.text(node.attr("fillType", "nonZero")))) {
            path.setFillType(Path.FillType.EVEN_ODD);
        } else {
            path.setFillType(Path.FillType.WINDING);
        }
        float global = vectorAlpha * (alpha / 255f);

        int fill = res.color(node.attr("fillColor"), Color.TRANSPARENT);
        float fillAlpha = res.number(node.attr("fillAlpha"), 1f);
        if (tint != 0) fill = tint;
        if (fill != Color.TRANSPARENT || node.first("gradient") != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            p.setAlpha((int) (Color.alpha(fill) * fillAlpha * global));
            applyShader(p, node.first("gradient"));
            if (colorFilter != null) p.setColorFilter(colorFilter);
            canvas.drawPath(path, p);
        }

        int stroke = res.color(node.attr("strokeColor"), Color.TRANSPARENT);
        float strokeWidth = res.number(node.attr("strokeWidth"), 0f);
        float strokeAlpha = res.number(node.attr("strokeAlpha"), 1f);
        if (stroke != Color.TRANSPARENT && strokeWidth > 0) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(strokeWidth);
            p.setStrokeCap(capFor(res.text(node.attr("strokeLineCap", "butt"))));
            p.setStrokeJoin(joinFor(res.text(node.attr("strokeLineJoin", "miter"))));
            p.setStrokeMiter(res.number(node.attr("strokeMiterLimit"), 4f));
            if (tint != 0) stroke = tint;
            p.setColor(stroke);
            p.setAlpha((int) (Color.alpha(stroke) * strokeAlpha * global));
            applyShader(p, node.first("gradient"));
            if (colorFilter != null) p.setColorFilter(colorFilter);
            canvas.drawPath(path, p);
        }
    }

    private void applyShader(Paint paint, XmlNode grad) {
        if (grad == null) return;
        int start = res.color(grad.attr("startColor"), Color.TRANSPARENT);
        int end = res.color(grad.attr("endColor"), Color.TRANSPARENT);
        String centerRaw = grad.attr("centerColor");
        int[] colors = centerRaw == null ? new int[]{start, end}
                : new int[]{start, res.color(centerRaw, start), end};
        float sx = res.number(grad.attr("startX"), 0f);
        float sy = res.number(grad.attr("startY"), 0f);
        float ex = res.number(grad.attr("endX"), 0f);
        float ey = res.number(grad.attr("endY"), 0f);
        float cx = res.number(grad.attr("centerX"), 0f);
        float cy = res.number(grad.attr("centerY"), 0f);
        float radius = res.number(grad.attr("gradientRadius"), 1f);
        Shader.TileMode tile = tileFor(res.text(grad.attr("tileMode", "clamp")));
        String type = res.text(grad.attr("type", "linear"));
        try {
            if ("radial".equals(type)) {
                paint.setShader(new RadialGradient(cx, cy, Math.max(radius, 0.01f),
                        colors, null, tile));
            } else if ("sweep".equals(type)) {
                paint.setShader(new SweepGradient(cx, cy, colors, null));
            } else {
                paint.setShader(new LinearGradient(sx, sy, ex, ey, colors, null, tile));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Shader.TileMode tileFor(String s) {
        if ("repeat".equals(s)) return Shader.TileMode.REPEAT;
        if ("mirror".equals(s)) return Shader.TileMode.MIRROR;
        return Shader.TileMode.CLAMP;
    }

    private static Paint.Cap capFor(String s) {
        if ("round".equals(s)) return Paint.Cap.ROUND;
        if ("square".equals(s)) return Paint.Cap.SQUARE;
        return Paint.Cap.BUTT;
    }

    private static Paint.Join joinFor(String s) {
        if ("round".equals(s)) return Paint.Join.ROUND;
        if ("bevel".equals(s)) return Paint.Join.BEVEL;
        return Paint.Join.MITER;
    }
}
