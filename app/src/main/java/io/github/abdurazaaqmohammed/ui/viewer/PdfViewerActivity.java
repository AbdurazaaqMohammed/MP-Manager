package io.github.abdurazaaqmohammed.ui.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

import com.google.android.material.card.MaterialCardView;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance PDF Viewer Activity featuring:
 * - Edge-to-edge fit-width rendering with two-finger pinch-to-zoom (in/out) and double-tap zoom.
 * - Hardware-accelerated Night Mode (Invert Matrix) turning background AMOLED black and blue headers gold.
 * - Persistent Eye Protection reading mode with floating action button (FAB) and feedback pill.
 * - In-memory LruCache for instantaneous page navigation without re-rendering lag.
 * - Immersive fullscreen toggle, file sharing, open-with, and marquee title display.
 */
public class PdfViewerActivity extends BaseActivity {

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";

    private static final String PREF_NIGHT_MODE = "pdf_night_mode_enabled";
    private static final String PREF_EYE_PROTECTION = "pdf_eye_protection_enabled";
    private static final String PREF_EYE_COMFORT_INTENSITY = "pdf_eye_comfort_intensity";
    private static final long EYE_COMFORT_AUTO_DISMISS_MS = 2500L;
    private static final long PILL_AUTO_DISMISS_MS = 1800L;
    private static final int BITMAP_CACHE_CAPACITY = 8;

    private File pdfFile;
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private int pageCount = 0;
    private final Object rendererLock = new Object();
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);
    private final LruCache<Integer, Bitmap> bitmapCache = new LruCache<>(BITMAP_CACHE_CAPACITY);

    private Toolbar toolbar;
    private TextView toolbarTitle;
    private TextView pageInfo;
    private ImageButton btnNightMode;
    private ImageButton btnShare;
    private ImageButton btnFullscreen;
    private ImageButton btnOpenWith;

    private View contentArea;
    private ViewPager2 viewPager;
    private PdfPageAdapter pageAdapter;

    private View eyeComfortOverlay;
    private MaterialCardView fabEyeComfort;
    private MaterialCardView eyeComfortPill;
    private TextView eyeComfortPillText;
    private MaterialCardView eyeComfortBar;
    private SeekBar eyeComfortSeekBar;
    private TextView eyeComfortPercent;

    private SharedPreferences prefs;
    private boolean isNightMode = false;
    private boolean isEyeProtection = false;
    private int eyeComfortIntensity = 30;
    private boolean isFullscreen = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideEyeComfortBarRunnable = this::hideEyeComfortBar;
    private final Runnable hidePillRunnable = this::hideEyePill;

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
        loadPreferences();
        setupViewPager();
        setupActions();
    }

    private void initViews(String fileName) {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText(fileName);
        toolbarTitle.setSelected(true);

        pageInfo = findViewById(R.id.pageInfo);
        btnNightMode = findViewById(R.id.btnNightMode);
        btnShare = findViewById(R.id.btnShare);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnOpenWith = findViewById(R.id.btnOpenWith);

        contentArea = findViewById(R.id.contentArea);
        viewPager = findViewById(R.id.viewPager);
        eyeComfortOverlay = findViewById(R.id.eyeComfortOverlay);

        fabEyeComfort = findViewById(R.id.fabEyeComfort);
        ImageView fabIcon = findViewById(R.id.fabEyeComfortIcon);
        if (fabIcon != null) {
            fabIcon.setColorFilter(Color.WHITE);
        }

        eyeComfortPill = findViewById(R.id.eyeComfortPill);
        eyeComfortPillText = findViewById(R.id.eyeComfortPillText);

        eyeComfortBar = findViewById(R.id.eyeComfortBar);
        eyeComfortSeekBar = findViewById(R.id.eyeComfortSeekBar);
        eyeComfortPercent = findViewById(R.id.eyeComfortPercent);
    }

    private void loadPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isNightMode = prefs.getBoolean(PREF_NIGHT_MODE, false);
        isEyeProtection = prefs.getBoolean(PREF_EYE_PROTECTION, false);
        eyeComfortIntensity = prefs.getInt(PREF_EYE_COMFORT_INTENSITY, 30);

        applyNightMode(isNightMode);
        applyEyeProtection(isEyeProtection);
    }

    private void setupActions() {
        btnNightMode.setOnClickListener(v -> {
            isNightMode = !isNightMode;
            if (isNightMode && isEyeProtection) {
                isEyeProtection = false;
                prefs.edit().putBoolean(PREF_EYE_PROTECTION, false).apply();
                applyEyeProtection(false);
            }
            prefs.edit().putBoolean(PREF_NIGHT_MODE, isNightMode).apply();
            applyNightMode(isNightMode);
        });

        btnShare.setOnClickListener(v -> sharePdfFile());
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());
        btnOpenWith.setOnClickListener(v -> openWithExternalApp());

        fabEyeComfort.setOnClickListener(v -> {
            isEyeProtection = !isEyeProtection;
            if (isEyeProtection && isNightMode) {
                isNightMode = false;
                prefs.edit().putBoolean(PREF_NIGHT_MODE, false).apply();
                applyNightMode(false);
            }
            prefs.edit().putBoolean(PREF_EYE_PROTECTION, isEyeProtection).apply();
            applyEyeProtection(isEyeProtection);
            showEyePill(getString(isEyeProtection ? R.string.eye_protection_on : R.string.eye_protection_off));
        });

        fabEyeComfort.setOnLongClickListener(v -> {
            if (eyeComfortBar.getVisibility() == View.VISIBLE) {
                hideEyeComfortBar();
            } else {
                showEyeComfortBar();
            }
            return true;
        });

        eyeComfortSeekBar.setProgress(eyeComfortIntensity);
        eyeComfortPercent.setText(eyeComfortIntensity + "%");

        eyeComfortSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                eyeComfortIntensity = progress;
                eyeComfortPercent.setText(progress + "%");
                if (isEyeProtection) {
                    updateEyeOverlayAlpha(progress);
                }
                if (fromUser) {
                    prefs.edit().putInt(PREF_EYE_COMFORT_INTENSITY, progress).apply();
                    resetEyeComfortDismissTimer();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideEyeComfortBarRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                resetEyeComfortDismissTimer();
            }
        });
    }

    private void applyNightMode(boolean night) {
        if (night) {
            contentArea.setBackgroundColor(Color.BLACK);
            viewPager.setBackgroundColor(Color.BLACK);
            btnNightMode.setColorFilter(Color.parseColor("#FFD54F"));
        } else {
            contentArea.setBackgroundColor(Color.WHITE);
            viewPager.setBackgroundColor(Color.WHITE);
            btnNightMode.clearColorFilter();
        }

        // Instant update on all visible attached page views without re-rendering bitmaps
        RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
        if (rv != null) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                PdfZoomImageView img = child.findViewById(R.id.pageImageView);
                if (img != null) {
                    img.setNightMode(night);
                }
            }
        }
    }

    private void applyEyeProtection(boolean enabled) {
        if (enabled) {
            fabEyeComfort.setCardBackgroundColor(Color.parseColor("#D32F2F"));
            eyeComfortOverlay.setVisibility(View.VISIBLE);
            updateEyeOverlayAlpha(eyeComfortIntensity);
        } else {
            fabEyeComfort.setCardBackgroundColor(Color.parseColor("#212121"));
            eyeComfortOverlay.setVisibility(View.GONE);
        }
    }

    private void updateEyeOverlayAlpha(int intensity) {
        int alpha = (int) (intensity * 2.55f);
        if (alpha < 35) alpha = 35;
        if (alpha > 190) alpha = 190;
        eyeComfortOverlay.setBackgroundColor(Color.argb(alpha, 255, 185, 90));
    }

    private void showEyePill(String message) {
        handler.removeCallbacks(hidePillRunnable);
        eyeComfortPillText.setText(message);
        eyeComfortPill.setAlpha(0f);
        eyeComfortPill.setVisibility(View.VISIBLE);
        eyeComfortPill.animate().alpha(1f).setDuration(180).setListener(null).start();
        handler.postDelayed(hidePillRunnable, PILL_AUTO_DISMISS_MS);
    }

    private void hideEyePill() {
        if (eyeComfortPill != null && eyeComfortPill.getVisibility() == View.VISIBLE) {
            eyeComfortPill.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    eyeComfortPill.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void showEyeComfortBar() {
        eyeComfortBar.setVisibility(View.VISIBLE);
        eyeComfortSeekBar.setProgress(eyeComfortIntensity);
        eyeComfortPercent.setText(eyeComfortIntensity + "%");
        resetEyeComfortDismissTimer();
    }

    private void hideEyeComfortBar() {
        handler.removeCallbacks(hideEyeComfortBarRunnable);
        if (eyeComfortBar != null) {
            eyeComfortBar.setVisibility(View.GONE);
        }
    }

    private void resetEyeComfortDismissTimer() {
        handler.removeCallbacks(hideEyeComfortBarRunnable);
        handler.postDelayed(hideEyeComfortBarRunnable, EYE_COMFORT_AUTO_DISMISS_MS);
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            toolbar.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } else {
            toolbar.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen();
            return;
        }
        super.onBackPressed();
    }

    private void sharePdfFile() {
        if (pdfFile == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(uri.toString(), uri));
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
        }
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && eyeComfortBar != null && eyeComfortBar.getVisibility() == View.VISIBLE) {
            Rect barRect = new Rect();
            eyeComfortBar.getGlobalVisibleRect(barRect);
            Rect fabRect = new Rect();
            fabEyeComfort.getGlobalVisibleRect(fabRect);
            int x = (int) ev.getRawX();
            int y = (int) ev.getRawY();
            if (!barRect.contains(x, y) && !fabRect.contains(x, y)) {
                hideEyeComfortBar();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setupViewPager() {
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pageAdapter = new PdfPageAdapter();
        viewPager.setAdapter(pageAdapter);
        pageInfo.setText("1 / " + pageCount);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                pageInfo.setText((position + 1) + " / " + pageCount);
                // Ensure page swiping is enabled on newly selected page
                viewPager.setUserInputEnabled(true);
            }
        });
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
            holder.pageImageView.setNightMode(isNightMode);
            holder.pageImageView.setOnClickListener(v -> toggleFullscreen());
            final int pos = position;

            holder.pageImageView.setScaleChangeListener(scale -> {
                // Disable ViewPager2 horizontal intercept when zoomed in so user can pan freely
                viewPager.setUserInputEnabled(scale <= 1.05f);
            });

            Bitmap cached = bitmapCache.get(pos);
            if (cached != null && !cached.isRecycled()) {
                holder.pageLoading.setVisibility(View.GONE);
                holder.pageImageView.setImageBitmap(cached);
                return;
            }

            holder.pageLoading.setVisibility(View.VISIBLE);
            holder.pageImageView.setImageBitmap(null);

            renderExecutor.execute(() -> {
                Bitmap bitmap = null;
                synchronized (rendererLock) {
                    if (renderer == null || isFinishing() || isDestroyed()) return;
                    PdfRenderer.Page page = null;
                    try {
                        page = renderer.openPage(pos);
                        int pw = page.getWidth();
                        int ph = page.getHeight();
                        if (pw <= 0 || ph <= 0) {
                            pw = 1080;
                            ph = 1920;
                        }
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        if (screenW <= 0) screenW = 1080;
                        // High-DPI target resolution for crystal clear text when zoomed
                        int targetW = Math.min((int) (screenW * 1.5f), 2048);
                        int targetH = Math.max(1, (int) ((float) ph / pw * targetW));

                        bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } catch (Throwable ignored) {
                    } finally {
                        if (page != null) {
                            try {
                                page.close();
                            } catch (Exception ignored) {}
                        }
                    }
                }

                final Bitmap renderedBitmap = bitmap;
                if (renderedBitmap != null) {
                    bitmapCache.put(pos, renderedBitmap);
                }

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (holder.getBindingAdapterPosition() == pos) {
                            holder.pageLoading.setVisibility(View.GONE);
                            if (renderedBitmap != null) {
                                holder.pageImageView.setImageBitmap(renderedBitmap);
                            }
                        }
                    }
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull PageViewHolder holder) {
            super.onViewRecycled(holder);
            holder.pageImageView.setImageBitmap(null);
            holder.pageImageView.resetZoom();
            holder.pageLoading.setVisibility(View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            final PdfZoomImageView pageImageView;
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
        handler.removeCallbacks(hideEyeComfortBarRunnable);
        handler.removeCallbacks(hidePillRunnable);
        renderExecutor.shutdown();
        bitmapCache.evictAll();
        cleanupRenderer();
        super.onDestroy();
    }
}
