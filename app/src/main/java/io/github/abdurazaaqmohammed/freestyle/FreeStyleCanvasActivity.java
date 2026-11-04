package io.github.abdurazaaqmohammed.freestyle;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.mp.mp_manager.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FreeStyleCanvasActivity extends AppCompatActivity {

    private FreeStyleCanvasView canvasView;
    private MaterialButton btnSave;
    private LinearLayout cropControlBar;
    private MaterialButton btnCancelCrop;
    private MaterialButton btnApplyCrop;

    private ExecutorService loadExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freestyle_canvas);

        loadExecutor = Executors.newSingleThreadExecutor();

        canvasView = findViewById(R.id.canvasView);
        btnSave = findViewById(R.id.btnSave);
        cropControlBar = findViewById(R.id.cropControlBar);
        btnCancelCrop = findViewById(R.id.btnCancelCrop);
        btnApplyCrop = findViewById(R.id.btnApplyCrop);

        canvasView.setOnCropRequestedListener(layer -> {
            cropControlBar.setVisibility(View.VISIBLE);
            btnSave.setVisibility(View.GONE);
        });

        btnCancelCrop.setOnClickListener(v -> {
            canvasView.cancelCrop();
            cropControlBar.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        });

        btnApplyCrop.setOnClickListener(v -> {
            canvasView.applyCrop();
            cropControlBar.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        });

        btnSave.setOnClickListener(v -> saveCanvasCollage());

        // Load selected images passed from picker
        ArrayList<String> paths = getIntent().getStringArrayListExtra("selected_images");
        if (paths != null && !paths.isEmpty()) {
            loadImages(paths);
        }
    }

    private void loadImages(ArrayList<String> paths) {
        loadExecutor.execute(() -> {
            for (String path : paths) {
                Bitmap bmp = loadBitmapEfficiently(path, 1200, 1200);
                if (bmp != null) {
                    mainHandler.post(() -> canvasView.addLayer(bmp));
                }
            }
        });
    }

    private Bitmap loadBitmapEfficiently(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(is, null, options);
                }
            } else {
                BitmapFactory.decodeFile(path, options);
            }

            int inSampleSize = 1;
            int height = options.outHeight;
            int width = options.outWidth;
            if (height > reqH || width > reqW) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqH && (halfWidth / inSampleSize) >= reqW) {
                    inSampleSize *= 2;
                }
            }

            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;

            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    return BitmapFactory.decodeStream(is, null, options);
                }
            } else {
                return BitmapFactory.decodeFile(path, options);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private void saveCanvasCollage() {
        Bitmap output = canvasView.renderToBitmap();
        if (output == null) {
            Toast.makeText(this, "Failed to capture canvas", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "FreeStyle_" + timeStamp + ".jpg";

        loadExecutor.execute(() -> {
            boolean success = false;
            String savedPath = "";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FreeStyle");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        output.compress(Bitmap.CompressFormat.JPEG, 95, os);
                        success = true;
                        savedPath = Environment.DIRECTORY_PICTURES + "/FreeStyle/" + fileName;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FreeStyle");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    output.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    success = true;
                    savedPath = outFile.getAbsolutePath();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            final boolean finalSuccess = success;
            final String finalSavedPath = savedPath;
            mainHandler.post(() -> {
                if (finalSuccess) {
                    Toast.makeText(this, getString(R.string.collage_saved, finalSavedPath), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Error saving collage", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onBackPressed() {
        if (canvasView.isCropMode()) {
            canvasView.cancelCrop();
            cropControlBar.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadExecutor != null) {
            loadExecutor.shutdownNow();
        }
    }
}
