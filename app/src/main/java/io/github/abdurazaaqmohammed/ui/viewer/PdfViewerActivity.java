package io.github.abdurazaaqmohammed.ui.viewer;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen horizontal PDF viewer with page swiping, open-with dispatch,
 * and persistent eye-comfort warm reading mode.
 */
public class PdfViewerActivity extends BaseActivity {

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    private static final String PREF_EYE_COMFORT_INTENSITY = "pdf_eye_comfort_intensity";
    private static final long EYE_COMFORT_AUTO_DISMISS_MS = 2000L;

    private File pdfFile;
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private int pageCount = 0;
    private final Object rendererLock = new Object();
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);

    private Toolbar toolbar;
    private TextView pageInfo;
    private ImageButton btnEyeComfort;
    private ImageButton btnOpenWith;
    private ViewPager2 viewPager;
    private View eyeComfortOverlay;
    private View eyeComfortBar;
    private SeekBar eyeComfortSeekBar;
    private TextView eyeComfortPercent;

    private SharedPreferences prefs;
    private int eyeComfortIntensity = 0;
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideEyeComfortRunnable = this::hideEyeComfortBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);

        if (path == null) {
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pdfFile = new File(path);
        if (!pdfFile.exists() || !pdfFile.canRead() || pdfFile.length() < 4) {
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (name == null || name.isEmpty()) {
            name = pdfFile.getName();
        }

        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            byte[] header = new byte[4];
            if (fis.read(header) != 4 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            pageCount = renderer.getPageCount();
            if (pageCount <= 0) {
                cleanupRenderer();
                Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } catch (Exception e) {
            cleanupRenderer();
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews(name);
        setupEyeComfort();
        setupViewPager();
    }

    private void initViews(String fileName) {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(fileName);
        toolbar.setNavigationOnClickListener(v -> finish());

        pageInfo = findViewById(R.id.pageInfo);
        btnEyeComfort = findViewById(R.id.btnEyeComfort);
        btnOpenWith = findViewById(R.id.btnOpenWith);
        viewPager = findViewById(R.id.viewPager);
        eyeComfortOverlay = findViewById(R.id.eyeComfortOverlay);
        eyeComfortBar = findViewById(R.id.eyeComfortBar);
        eyeComfortSeekBar = findViewById(R.id.eyeComfortSeekBar);
        eyeComfortPercent = findViewById(R.id.eyeComfortPercent);

        btnOpenWith.setOnClickListener(v -> openWithExternalApp());
    }

    private void setupEyeComfort() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        eyeComfortIntensity = prefs.getInt(PREF_EYE_COMFORT_INTENSITY, 0);
        applyEyeComfort(eyeComfortIntensity);

        btnEyeComfort.setOnClickListener(v -> {
            if (eyeComfortBar.getVisibility() == View.VISIBLE) {
                hideEyeComfortBar();
            } else {
                showEyeComfortBar();
            }
        });

        eyeComfortSeekBar.setProgress(eyeComfortIntensity);
        eyeComfortPercent.setText(eyeComfortIntensity + "%");

        eyeComfortSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                eyeComfortIntensity = progress;
                applyEyeComfort(progress);
                eyeComfortPercent.setText(progress + "%");
                if (fromUser) {
                    prefs.edit().putInt(PREF_EYE_COMFORT_INTENSITY, progress).apply();
                    resetEyeComfortDismissTimer();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                autoHideHandler.removeCallbacks(hideEyeComfortRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                resetEyeComfortDismissTimer();
            }
        });
    }

    private void applyEyeComfort(int intensity) {
        if (intensity <= 0) {
            eyeComfortOverlay.setVisibility(View.GONE);
            btnEyeComfort.setColorFilter(null);
        } else {
            eyeComfortOverlay.setVisibility(View.VISIBLE);
            // Intensity ranges 1..70; map to alpha 20..185
            int alpha = (int) (intensity * 2.55f);
            eyeComfortOverlay.setBackgroundColor(Color.argb(alpha, 255, 215, 120));
            btnEyeComfort.setColorFilter(0xFFFFB300);
        }
    }

    private void showEyeComfortBar() {
        eyeComfortBar.setVisibility(View.VISIBLE);
        eyeComfortSeekBar.setProgress(eyeComfortIntensity);
        eyeComfortPercent.setText(eyeComfortIntensity + "%");
        resetEyeComfortDismissTimer();
    }

    private void hideEyeComfortBar() {
        autoHideHandler.removeCallbacks(hideEyeComfortRunnable);
        if (eyeComfortBar != null) {
            eyeComfortBar.setVisibility(View.GONE);
        }
    }

    private void resetEyeComfortDismissTimer() {
        autoHideHandler.removeCallbacks(hideEyeComfortRunnable);
        autoHideHandler.postDelayed(hideEyeComfortRunnable, EYE_COMFORT_AUTO_DISMISS_MS);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && eyeComfortBar != null && eyeComfortBar.getVisibility() == View.VISIBLE) {
            Rect barRect = new Rect();
            eyeComfortBar.getGlobalVisibleRect(barRect);
            Rect btnRect = new Rect();
            btnEyeComfort.getGlobalVisibleRect(btnRect);
            int x = (int) ev.getRawX();
            int y = (int) ev.getRawY();
            if (!barRect.contains(x, y) && !btnRect.contains(x, y)) {
                hideEyeComfortBar();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setupViewPager() {
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setAdapter(new PdfPageAdapter());
        pageInfo.setText("1 / " + pageCount);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                pageInfo.setText((position + 1) + " / " + pageCount);
            }
        });
    }

    private void openWithExternalApp() {
        if (pdfFile == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(uri.toString(), uri));
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
        }
    }

    private class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_page, parent, false);
            return new PageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            holder.pageLoading.setVisibility(View.VISIBLE);
            holder.pageImageView.setImageBitmap(null);
            final int pos = position;

            renderExecutor.execute(() -> {
                Bitmap bitmap = null;
                synchronized (rendererLock) {
                    if (renderer == null || isFinishing() || isDestroyed()) return;
                    try {
                        PdfRenderer.Page page = renderer.openPage(pos);
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        if (screenW <= 0) screenW = 1080;
                        int targetW = screenW;
                        int targetH = (int) ((float) page.getHeight() / page.getWidth() * targetW);

                        bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();
                    } catch (Exception ignored) {}
                }

                final Bitmap renderedBitmap = bitmap;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        holder.pageLoading.setVisibility(View.GONE);
                        if (renderedBitmap != null) {
                            holder.pageImageView.setImageBitmap(renderedBitmap);
                        }
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            final ImageView pageImageView;
            final ProgressBar pageLoading;

            PageViewHolder(@NonNull View itemView) {
                super(itemView);
                pageImageView = itemView.findViewById(R.id.pageImageView);
                pageLoading = itemView.findViewById(R.id.pageLoading);
            }
        }
    }

    private void cleanupRenderer() {
        synchronized (rendererLock) {
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception ignored) {}
                renderer = null;
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception ignored) {}
                pfd = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        autoHideHandler.removeCallbacks(hideEyeComfortRunnable);
        renderExecutor.shutdown();
        cleanupRenderer();
        super.onDestroy();
    }
}
