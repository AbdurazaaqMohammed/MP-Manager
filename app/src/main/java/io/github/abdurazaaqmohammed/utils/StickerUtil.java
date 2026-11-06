package io.github.abdurazaaqmohammed.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class StickerUtil {

    private StickerUtil() {}

    /**
     * Checks if a given file is a WhatsApp animated Lottie sticker package
     * disguised as a .webp (or any other extension).
     */
    public static boolean isLottieSticker(File file) {
        if (file == null || !file.isFile() || file.length() < 30) return false;
        try (InputStream in = FileUtils.getInputStream(file)) {
            byte[] header = new byte[4];
            if (in.read(header) != 4) return false;
            // Must have zip magic PK\03\04
            if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }

        // Verify it contains animation/animation.json
        try (ZipInputStream zis = new ZipInputStream(FileUtils.getInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("animation/animation.json".equals(name) || name.endsWith("/animation.json") || "animation.json".equals(name)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Extracts the animation.json string from the sticker zip file.
     */
    public static String extractLottieJson(File file) {
        try (ZipInputStream zis = new ZipInputStream(FileUtils.getInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("animation/animation.json".equals(name) || name.endsWith("/animation.json") || "animation.json".equals(name)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    return baos.toString("UTF-8");
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Renders a representative frame from the Lottie sticker to a Bitmap thumbnail.
     */
    public static Bitmap renderThumbnailBitmap(File file, int targetSize) {
        String json = extractLottieJson(file);
        if (json == null) return null;
        try {
            LottieResult<LottieComposition> result =
                    LottieCompositionFactory.fromJsonStringSync(json, file.getName());
            LottieComposition composition = result.getValue();
            if (composition == null) return null;

            LottieDrawable drawable = new LottieDrawable();
            drawable.setComposition(composition);

            float startFrame = composition.getStartFrame();
            float endFrame = composition.getEndFrame();
            // Pick a representative frame (middle frame or start frame)
            float midFrame = startFrame + (endFrame - startFrame) * 0.35f;
            drawable.setFrame((int) midFrame);

            Rect bounds = composition.getBounds();
            int w = bounds.width() > 0 ? bounds.width() : 512;
            int h = bounds.height() > 0 ? bounds.height() : 512;

            if (targetSize <= 0) targetSize = 160;
            int renderW, renderH;
            if (w >= h) {
                renderW = targetSize;
                renderH = Math.max(1, (int) (targetSize * ((float) h / w)));
            } else {
                renderH = targetSize;
                renderW = Math.max(1, (int) (targetSize * ((float) w / h)));
            }

            Bitmap bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, renderW, renderH);
            drawable.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
