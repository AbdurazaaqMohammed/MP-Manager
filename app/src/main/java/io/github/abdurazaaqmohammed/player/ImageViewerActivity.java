package io.github.abdurazaaqmohammed.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.exifinterface.media.ExifInterface;

import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ImageViewerActivity extends AppCompatActivity {

    private RecyclerView pager;
    private TextView titleText, subtitleText, counterText;
    private CheckBox batchCheck;
    private ImagePagerAdapter pagerAdapter;

    private List<String> imagePaths;
    private int currentIndex;
    private int pagerPage;
    private final Set<Integer> checkedPositions = new HashSet<>();

    interface PageProvider {
        int getPage();
    }

    static class ZoomPagerLayoutManager extends LinearLayoutManager {
        private RecyclerView attachedPager;
        private PageProvider pageProvider;

        ZoomPagerLayoutManager(Context context) {
            super(context, LinearLayoutManager.HORIZONTAL, false);
        }

        void bind(RecyclerView pager, PageProvider provider) {
            attachedPager = pager;
            pageProvider = provider;
        }

        @Override
        public boolean canScrollHorizontally() {
            try {
                if (attachedPager != null && pageProvider != null) {
                    RecyclerView.ViewHolder holder =
                            attachedPager.findViewHolderForAdapterPosition(pageProvider.getPage());
                    if (holder != null && holder.itemView instanceof ZoomableImageView) {
                        if (((ZoomableImageView) holder.itemView).getCurrentScale() > 1.01f) return false;
                    }
                }
            } catch (Exception ignored) {
            }
            return super.canScrollHorizontally();
        }
    }

    public static void open(Activity activity, String filePath) {
        Intent intent = new Intent(activity, ImageViewerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("image_path", filePath);
        activity.startActivity(intent);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        int themeId = PreferenceManager.getDefaultSharedPreferences(this).getInt("theme", 0);
        if (themeId != 0) setTheme(themeId);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        pager = findViewById(R.id.pager);
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);
        counterText = findViewById(R.id.counterText);
        batchCheck = findViewById(R.id.batchCheck);
        View btnShare = findViewById(R.id.btnShare);
        View btnOpenWith = findViewById(R.id.btnOpenWith);
        View btnBack = findViewById(R.id.btnBack);
        View btnInfo = findViewById(R.id.btnInfo);

        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            boolean multi = batchCheck.isChecked() && !checkedPositions.isEmpty();
            String currPath, currName;
            if (multi) {
                currPath = null;
                currName = null;
                int size = checkedPositions.size();
                for (int p : checkedPositions) {
                    String s = imagePaths.get(p);
                    sb.append(s.substring(s.lastIndexOf(File.separatorChar) + 1));
                    if (p < size) sb.append(',').append(' ');
                }
            } else {
                currPath = imagePaths.get(currentIndex);
                currName = currPath.substring(currPath.lastIndexOf(File.separatorChar) + 1);
                sb.append(currName);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.confirm)
                    .setMessage(getString(R.string.confirm_delete_f, sb))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        ProgressManager pm = new ProgressManager(this, true).show();
                        new Thread(() -> {
                            if (multi) for (int p : checkedPositions) {
                                String pathname = imagePaths.get(p);
                                int size = checkedPositions.size();
                                pm.setText(getString(R.string.deleting, pathname.substring(pathname.lastIndexOf(File.separatorChar) + 1)));
                                pm.setProgress(p, size);
                                new File(pathname).delete();
                            } else {
                                pm.setText(getString(R.string.deleting, currName));
                                pm.setProgress(0, 1);
                                new File(currPath).delete();
                            }
                            pm.dismiss();
                        }).start();
                    }).show();
        });

        String startPath = getIntent().getStringExtra("image_path");
        if (startPath == null || !new File(startPath).exists()) {
            finish();
            return;
        }

        loadImagePaths(new File(startPath));
        currentIndex = imagePaths.indexOf(startPath);
        if (currentIndex < 0) currentIndex = 0;

        btnBack.setOnClickListener(v -> finish());
        btnInfo.setOnClickListener(v -> showProperties(currentIndex));
        titleText.setOnClickListener(v -> showProperties(currentIndex));
        subtitleText.setOnClickListener(v -> showProperties(currentIndex));

        btnShare.setOnClickListener(v -> {
            if (batchCheck.isChecked() && !checkedPositions.isEmpty()) {
                shareImages(new ArrayList<>(checkedPositions));
            } else {
                shareImage(currentIndex);
            }
        });

        btnOpenWith.setOnClickListener(v -> {
            if (batchCheck.isChecked() && !checkedPositions.isEmpty()) {
                openWithImages(new ArrayList<>(checkedPositions));
            } else {
                openWithImage(currentIndex);
            }
        });

        batchCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) checkedPositions.clear();
        });

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(pager);

        ZoomPagerLayoutManager pagerLayout = new ZoomPagerLayoutManager(this);
        pager.setLayoutManager(pagerLayout);
        pagerAdapter = new ImagePagerAdapter(imagePaths);
        pager.setAdapter(pagerAdapter);
        pagerLayout.bind(pager, () -> pagerPage);
        pager.scrollToPosition(currentIndex);
        pagerPage = currentIndex;
        findViewById(R.id.btnEdit).setOnClickListener(v -> showEditMenu(currentIndex));

        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                pagerPage = getCurrentPage();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateForPosition(getCurrentPage());
                }
            }
        });

        updateForPosition(currentIndex);
    }

    private void loadImagePaths(File startFile) {
        imagePaths = new ArrayList<>();
        File dir = startFile.getParentFile();
        if (dir == null || !dir.isDirectory()) {
            imagePaths.add(startFile.getAbsolutePath());
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            imagePaths.add(startFile.getAbsolutePath());
            return;
        }
        for (File f : files) {
            if (f.isFile() && FileUtils.isImageFile(f.getName())) {
                imagePaths.add(f.getAbsolutePath());
            }
        }
    }

    private int getCurrentPage() {
        LinearLayoutManager lm = (LinearLayoutManager) pager.getLayoutManager();
        if (lm == null) return currentIndex;
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first == last) return first;
        View firstView = lm.findViewByPosition(first);
        View lastView = lm.findViewByPosition(last);
        if (firstView == null || lastView == null) return currentIndex;
        int mid = pager.getWidth() / 2;
        int firstCenter = (int) (firstView.getX() + firstView.getWidth() / 2f);
        int lastCenter = (int) (lastView.getX() + lastView.getWidth() / 2f);
        return Math.abs(firstCenter - mid) < Math.abs(lastCenter - mid) ? first : last;
    }

    private void updateForPosition(int pos) {
        if (pos < 0 || pos >= imagePaths.size()) return;
        currentIndex = pos;
        String path = imagePaths.get(pos);
        File file = new File(path);
        titleText.setText(file.getName());
        counterText.setText((pos + 1) + "/" + imagePaths.size());

        String resolution = "";
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            resolution = opts.outWidth + "x" + opts.outHeight;
        }

        String modDate = "";
        if (file.exists()) {
            modDate = DateFormat.getDateFormat(this).format(new Date(file.lastModified()));
        }

        subtitleText.setText(modDate + (resolution.isEmpty() ? "" : "  " + resolution));

        boolean checked = checkedPositions.contains(pos);
        batchCheck.setOnCheckedChangeListener(null);
        batchCheck.setChecked(checked);
        batchCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) checkedPositions.add(pos);
            else checkedPositions.remove(pos);
        });
    }

    private void showProperties(int pos) {
        if (pos < 0 || pos >= imagePaths.size()) return;
        String path = imagePaths.get(pos);
        File file = new File(path);

        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(file.getName()).append("\n");
        sb.append("Path: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Size: ").append(formatFileSize(file.length())).append("\n");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("Modified: ").append(sdf.format(new Date(file.lastModified()))).append("\n");

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            sb.append("Resolution: ").append(opts.outWidth).append("x").append(opts.outHeight).append(" px\n");
        }

        try {
            ExifInterface exif = new ExifInterface(path);
            sb.append("\n--- EXIF Data ---\n");
            String[] exifTags = {
                ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME, ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH,
                ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_EXIF_VERSION, ExifInterface.TAG_FLASH_ENERGY,
                ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT
            };
            for (String tag : exifTags) {
                String val = exif.getAttribute(tag);
                if (val != null && !val.isEmpty()) {
                    sb.append(tag).append(": ").append(val).append("\n");
                }
            }

            float[] latLong = new float[2];
            if (exif.getLatLong(latLong)) {
                sb.append("GPS Coordinates: ").append(latLong[0]).append(", ").append(latLong[1]).append("\n");
            }
        } catch (IOException e) {
            sb.append("(No EXIF data available)\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.image_properties)
                .setMessage(sb)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024));
        return String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024 * 1024));
    }

    private Uri getFileUri(String path) {
        return FileProvider.getUriForFile(this,
                "io.github.abdurazaaqmohammed.MPManager.provider", new File(path));
    }

    private void shareImage(int pos) {
        if (pos < 0 || pos >= imagePaths.size()) return;
        Uri uri = getFileUri(imagePaths.get(pos));
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("image/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share image"));
    }

    private void shareImages(List<Integer> positions) {
        if (positions.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (int p : positions) uris.add(getFileUri(imagePaths.get(p)));
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share images"));
    }

    private void openWithImage(int pos) {
        if (pos < 0 || pos >= imagePaths.size()) return;
        Uri uri = getFileUri(imagePaths.get(pos));
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Open with"));
    }

    private void openWithImages(List<Integer> positions) {
        if (positions.isEmpty()) return;
        if (positions.size() == 1) {
            openWithImage(positions.get(0));
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (int p : positions) uris.add(getFileUri(imagePaths.get(p)));
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Open with"));
    }

    private boolean isJpegFile(String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return true;
        try (FileInputStream fis = new FileInputStream(path)) {
            return fis.read() == 0xFF && fis.read() == 0xD8;
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshCurrentImage() {
        if (pagerAdapter != null) pagerAdapter.notifyItemChanged(currentIndex);
        updateForPosition(currentIndex);
    }

    private boolean isPngFile(String path) {
        return path.toLowerCase(Locale.US).endsWith(".png");
    }

    static final int REQ_EDIT_IMAGE = 1401;

    private void showEditMenu(int pos) {
        if (pos < 0 || pos >= imagePaths.size()) return;
        String path = imagePaths.get(pos);
        boolean jpeg = isJpegFile(path);
        if (!jpeg && !isPngFile(path)) {
            showError("Only JPEG and PNG supported");
            return;
        }
        ImageEditActivity.sessionPath = path;
        startActivityForResult(new Intent(this, ImageEditActivity.class), REQ_EDIT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_IMAGE && resultCode == RESULT_OK) {
            refreshCurrentImage();
        }
    }

    private void checkNativeTools() {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(NativeToolManager.diagnoseExec(ImageViewerActivity.this));
            sb.append("ABI: ").append(NativeToolManager.deviceAbi()).append("\n");
            File pack = new File(getFilesDir(), "native/native-" + NativeToolManager.deviceAbi());
            sb.append("Pack dir: ").append(pack.isDirectory() ? "present" : "missing").append("\n");
            try {
                File nativeLibDir = new File(getApplicationInfo().nativeLibraryDir);
                String[] bundled = nativeLibDir.list((dir, name) ->
                        name.equals("libperl.so") || name.equals("libjpegtran.so") || name.startsWith("libperl_xs_"));
                sb.append("Bundled tools: ");
                if (bundled == null || bundled.length == 0) sb.append("none\n");
                else {
                    for (int i = 0; i < bundled.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(bundled[i]);
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                sb.append("Bundled tools: error ").append(e.getMessage()).append("\n");
            }
            File jpegtran = NativeToolManager.jpegtranBinary(this);
            sb.append("jpegtran: ").append(NativeToolManager.describeFile(jpegtran)).append("\n");
            if (jpegtran.isFile()) {
                try {
                    System.load(jpegtran.getAbsolutePath());
                    sb.append("System.load probe: LOADED\n");
                } catch (Throwable t) {
                    sb.append("System.load probe: FAILED ").append(t.getMessage()).append("\n");
                }
            }
            if (jpegtran.isFile()) {
                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(jpegtran.getAbsolutePath());
                    Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                    StringBuilder out = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        char[] buf = new char[2048];
                        int n;
                        while ((n = br.read(buf)) != -1 && out.length() < 2048) out.append(buf, 0, n);
                    }
                    process.waitFor();
                    String firstLine = out.length() == 0 ? "(no output)" : out.toString().split("\n")[0];
                    sb.append("jpegtran exec: OK (").append(firstLine.trim()).append(")\n");
                } catch (Exception e) {
                    sb.append("jpegtran exec: FAILED ").append(e.getMessage()).append("\n");
                }
            }
            File jniLib = new File(getFilesDir(), "native/" + "native-" + NativeToolManager.deviceAbi() + "/lib/libjpegtran_jni.so");
            sb.append("jni lib: ").append(NativeToolManager.describeFile(jniLib)).append("\n");
            if (jniLib.isFile()) {
                try {
                    if (JpegtranJni.load(jniLib.getAbsolutePath())) {
                        sb.append("JNI load probe: LOADED\n");
                    } else {
                        sb.append("JNI load probe: FAILED\n");
                    }
                } catch (Throwable t) {
                    sb.append("JNI load probe: FAILED ").append(t.getMessage()).append("\n");
                }
            }
            pm.dismiss();
            String report = sb.toString();
            runOnUiThread(() -> {
                TextView text = new TextView(this);
                text.setTextSize(13);
                text.setTypeface(Typeface.MONOSPACE);
                text.setTextIsSelectable(true);
                int pad = dp(12);
                text.setPadding(pad, pad, pad, pad);
                text.setText(report.trim());
                ScrollView scroll = new ScrollView(this);
                scroll.addView(text);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.native_required))
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }).start();
    }



    private void showError(String message) {
        Extensions.showMessage(this, message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return null;
        }
        return matrix;
    }


    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ViewHolder> {
        private final List<String> paths;

        ImagePagerAdapter(List<String> paths) { this.paths = paths; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ZoomableImageView imageView = new ZoomableImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
            return new ViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String path = paths.get(position);
            Bitmap bitmap = decodeSampled(path, 2048);
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap);
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            holder.imageView.resetZoom();
        }

        private Bitmap decodeSampled(String path, int maxSize) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / (sample * 2) >= maxSize && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeFile(path, opts);
            } catch (OutOfMemoryError e) {
                opts.inSampleSize = sample * 2;
                try {
                    bitmap = BitmapFactory.decodeFile(path, opts);
                } catch (OutOfMemoryError ignored) {
                    return null;
                }
            }
            if (bitmap == null) return null;
            try {
                int orientation = new ExifInterface(path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                Matrix matrix = orientationMatrix(orientation);
                if (matrix != null) {
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    bitmap.recycle();
                    bitmap = rotated;
                }
            } catch (Exception ignored) {
            }
            return bitmap;
        }

        @Override public int getItemCount() { return paths.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ZoomableImageView imageView;
            ViewHolder(ZoomableImageView v) { super(v); imageView = v; }
        }
    }
}
