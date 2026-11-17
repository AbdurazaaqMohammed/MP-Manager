package io.github.abdurazaaqmohammed.packs.ocr;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.File;

import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrEngine;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrToolPlugin;
import io.github.abdurazaaqmohammed.plugins.res.PackRes;

public final class ScreenOcrPlugin implements OcrToolPlugin {
    @Override public String id() { return "screen_ocr"; }
    @Override public String title(Context context) {
        return PackRes.str("title_screen_ocr", "Copy text on screen");
    }
    @Override public String subtitle(Context context) {
        return PackRes.str("subtitle_screen_ocr", "Offline OCR for screens and images");
    }
    @Override public String category(Context context) {
        int resource = context.getResources().getIdentifier("cat_text", "string", context.getPackageName());
        return resource == 0 ? "Text & Security" : context.getString(resource);
    }
    @Override public int iconRes(Context context) {
        return context.getResources().getIdentifier("screen_ocr_24px", "drawable", context.getPackageName());
    }
    @Override public View createView(Context context, ViewGroup container) {
        Button button = new Button(context);
        button.setText(title(context));
        button.setOnClickListener(v -> context.startActivity(new Intent().setClassName(context,
                "io.github.abdurazaaqmohammed.feature.system.ocr.ScreenOcrActivity")));
        return button;
    }
    @Override public OcrEngine createEngine(File detectionModel, File recognitionModel) throws Exception {
        return new PaddleOcrEngine(detectionModel, recognitionModel);
    }
}
