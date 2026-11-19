package com.example.mpthemesample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Launcher page for the sample: shows the wallpaper and preview the provider
 * serves from assets. Host apps never need this activity — it exists so the
 * sample is visible after install and so intent-based discovery by
 * queryIntentActivities also finds the THEME action.
 */
public class ThemeInfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_info);
        show(R.id.wallpaper_view, "wallpaper.png");
        show(R.id.preview_view, "preview.png");
        ((TextView) findViewById(R.id.theme_title)).setText(R.string.theme_name);
        ((TextView) findViewById(R.id.theme_details)).setText(R.string.theme_details);
    }

    private void show(int viewId, String asset) {
        try (InputStream in = getAssets().open(asset)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap != null) {
                ((ImageView) findViewById(viewId)).setImageBitmap(bitmap);
            }
        } catch (IOException ignored) {
        }
    }
}
