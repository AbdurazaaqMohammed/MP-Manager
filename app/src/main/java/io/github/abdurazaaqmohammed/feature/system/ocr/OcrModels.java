package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.content.Context;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

/** Immutable upstream release URLs and SHA-256 pins from RapidOCR's model registry. */
public final class OcrModels {
    private static final String BASE = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/";
    public record Artifact(String filename, String url, String sha256) {}
    public record Language(String id, int label, Artifact model) {}

    public static final Artifact DETECTOR = new Artifact("ch_PP-OCRv5_det_mobile.onnx",
            BASE + "det/ch_PP-OCRv5_det_mobile.onnx",
            "4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae");

    public static final List<Language> LANGUAGES = List.of(
            language("en", R.string.ocr_language_english, "c3461add59bb4323ecba96a492ab75e06dda42467c9e3d0c18db5d1d21924be8"),
            language("latin", R.string.ocr_language_latin, "b20bd37c168a570f583afbc8cd7925603890efbcdc000a59e22c269d160b5f5a"),
            language("ch", R.string.ocr_language_cjk, "5825fc7ebf84ae7a412be049820b4d86d77620f204a041697b0494669b1742c5"),
            language("arabic", R.string.ocr_language_arabic, "c1192e632d0baa9146ae5b756a0e635e3dc63c1733737ebfd1629e87144e9295"),
            language("cyrillic", R.string.ocr_language_cyrillic, "90f761b4bfcce0c8c561c0cb5c887b0971d3ec01c32164bdf7374a35b0982711"),
            language("korean", R.string.ocr_language_korean, "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b"),
            language("devanagari", R.string.ocr_language_devanagari, "d6f0a906580e3fa6b324a318718f1f31f268b6ea8ef985f91c2012a37f52c91e"),
            language("th", R.string.ocr_language_thai, "de541dd83161c241ff426f7ecfd602a0ba77d686cf3ab9a6c255ea82fd08006e"),
            language("el", R.string.ocr_language_greek, "b4368bccd557123c702b7549fee6cd1e94b581337d1c9b65310f109131542b7f"),
            language("ta", R.string.ocr_language_tamil, "a42448808b7dea87597336f12438935f40353f1949e8360acd9e06b4da21bfe1"),
            language("te", R.string.ocr_language_telugu, "a3690451b50028a09a3316a1274f7c05728151ea3f8fd392696397a7fefcbd92"));

    private OcrModels() {}

    private static Language language(String id, int label, String hash) {
        String file = id + "_PP-OCRv5_rec_mobile.onnx";
        return new Language(id, label, new Artifact(file, BASE + "rec/" + file, hash));
    }

    public static Language find(String id) {
        for (Language language : LANGUAGES) if (language.id.equals(id)) return language;
        return LANGUAGES.get(0);
    }

    public static Language selected(Context context) {
        return find(context.getSharedPreferences("screen_ocr", Context.MODE_PRIVATE).getString("language", "en"));
    }

    public static void select(Context context, Language language) {
        context.getSharedPreferences("screen_ocr", Context.MODE_PRIVATE).edit().putString("language", language.id).apply();
    }

    public static File file(Context context, Artifact artifact) {
        // Model downloads and screenshots must never enter cloud backup.
        return new File(new File(context.getNoBackupFilesDir(), "ocr-models"), artifact.filename);
    }

    public static boolean ready(Context context, Language language) {
        return file(context, DETECTOR).isFile() && file(context, DETECTOR).length() > 0
                && file(context, language.model).isFile() && file(context, language.model).length() > 0;
    }
}
