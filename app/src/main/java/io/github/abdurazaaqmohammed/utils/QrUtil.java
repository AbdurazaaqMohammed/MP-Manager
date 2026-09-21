package io.github.abdurazaaqmohammed.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

public final class QrUtil {

    private QrUtil() {
    }

    public static Bitmap generate(String text, int sizePx) throws Exception {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("Empty text");
        Map<com.google.zxing.EncodeHintType, Object> hints = new EnumMap<>(com.google.zxing.EncodeHintType.class);
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private static String escapeWifi(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:");
    }

    public static String wifiConfig(String ssid, String password, String security) {
        String sec = security == null ? "" : security.toUpperCase();
        String auth;
        if (sec.contains("WEP")) {
            auth = "WEP";
        } else if (sec.contains("WPA") || sec.contains("SAE") || sec.contains("EAP")) {
            auth = "WPA";
        } else {
            auth = "nopass";
        }
        StringBuilder sb = new StringBuilder("WIFI:");
        sb.append("T:").append(auth).append(';');
        sb.append("S:").append(escapeWifi(ssid)).append(';');
        if (!auth.equals("nopass") && password != null && !password.isEmpty()) {
            sb.append("P:").append(escapeWifi(password)).append(';');
        }
        sb.append(';');
        return sb.toString();
    }

    public static android.net.Uri saveToGallery(android.content.Context context, Bitmap bitmap, String name) throws Exception {
        String fileName = (name == null || name.isEmpty() ? "qr" : name.replaceAll("[^a-zA-Z0-9_.-]", "_")) + ".png";
        android.content.ContentResolver resolver = context.getContentResolver();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MP Manager");
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
        }
        android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Cannot create image");
        try (java.io.OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) throw new Exception("Cannot open image");
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) throw new Exception("Compress failed");
        } catch (Exception e) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.clear();
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
        return uri;
    }

    public static String decodeBitmap(Bitmap bitmap) {        if (bitmap == null) return null;
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            LuminanceSource source = new RGBLuminanceSource(w, h, pixels);
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            Result result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
            return result == null ? null : result.getText();
        } catch (Exception e) {
            return null;
        }
    }
}
