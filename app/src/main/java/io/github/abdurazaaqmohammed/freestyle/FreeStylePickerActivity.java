package io.github.abdurazaaqmohammed.freestyle;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FreeStylePickerActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 1001;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private MaterialButton btnNext;
    private TextView pickerTitle;

    private final List<MediaItem> allImages = new ArrayList<>();
    private final List<String> selectedPaths = new ArrayList<>();
    private ImageGridAdapter adapter;

    private ExecutorService loadExecutor;
    private LruCache<String, Bitmap> thumbCache;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class MediaItem {
        public final long id;
        public final Uri uri;
        public final String path;
        public final long dateModified;

        public MediaItem(long id, Uri uri, String path, long dateModified) {
            this.id = id;
            this.uri = uri;
            this.path = path;
            this.dateModified = dateModified;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freestyle_picker);

        // Thumbnail memory cache (1/8th of available runtime memory)
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        thumbCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        loadExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

        recyclerView = findViewById(R.id.pickerRecyclerView);
        progressBar = findViewById(R.id.pickerProgress);
        emptyView = findViewById(R.id.pickerEmptyView);
        btnNext = findViewById(R.id.btnNext);
        pickerTitle = findViewById(R.id.pickerTitle);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageGridAdapter();
        recyclerView.setAdapter(adapter);

        btnNext.setOnClickListener(v -> {
            if (selectedPaths.isEmpty()) {
                Toast.makeText(this, R.string.select_images, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, FreeStyleCanvasActivity.class);
            intent.putStringArrayListExtra("selected_images", new ArrayList<>(selectedPaths));
            startActivity(intent);
        });

        updateSelectionHeader();
        checkPermissionsAndLoad();
    }

    private void updateSelectionHeader() {
        int count = selectedPaths.size();
        if (count == 0) {
            pickerTitle.setText(R.string.select_images);
            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
        } else {
            pickerTitle.setText(getString(R.string.select_images) + " (" + count + ")");
            btnNext.setEnabled(true);
            btnNext.setAlpha(1.0f);
        }
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQ_CODE);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQ_CODE);
                return;
            }
        }
        loadDeviceImages();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDeviceImages();
            } else {
                Toast.makeText(this, "Permission required to display gallery photos", Toast.LENGTH_SHORT).show();
                loadDeviceImages(); // Attempt anyway in case broad access is given
            }
        }
    }

    private void loadDeviceImages() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        loadExecutor.execute(() -> {
            List<MediaItem> items = new ArrayList<>();
            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_MODIFIED
            };

            Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String sortOrder = MediaStore.Images.Media.DATE_MODIFIED + " DESC";

            try (Cursor cursor = getContentResolver().query(queryUri, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    int dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    int dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        String path = (dataCol != -1) ? cursor.getString(dataCol) : null;
                        long date = (dateCol != -1) ? cursor.getLong(dateCol) : 0;
                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        if (path == null || path.isEmpty()) {
                            path = contentUri.toString();
                        }
                        items.add(new MediaItem(id, contentUri, path, date));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                allImages.clear();
                allImages.addAll(items);
                adapter.notifyDataSetChanged();
                if (allImages.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadExecutor != null) {
            loadExecutor.shutdownNow();
        }
    }

    private class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_freestyle_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MediaItem item = allImages.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return allImages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView imgThumb;
            final View selectionOverlay;
            final TextView selectionBadge;
            String currentPath;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgThumb = itemView.findViewById(R.id.imgThumb);
                selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
                selectionBadge = itemView.findViewById(R.id.selectionBadge);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    MediaItem item = allImages.get(pos);
                    String path = item.path;
                    int selectedIndex = selectedPaths.indexOf(path);
                    if (selectedIndex >= 0) {
                        selectedPaths.remove(selectedIndex);
                    } else {
                        selectedPaths.add(path);
                    }
                    notifyItemChanged(pos);
                    updateSelectionHeader();
                });
            }

            public void bind(MediaItem item) {
                currentPath = item.path;
                int selectedIndex = selectedPaths.indexOf(item.path);
                boolean isSelected = selectedIndex >= 0;

                selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                selectionBadge.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                if (isSelected) {
                    selectionBadge.setText(String.valueOf(selectedIndex + 1));
                }

                // Check thumbnail cache
                Bitmap cached = thumbCache.get(item.path);
                if (cached != null) {
                    imgThumb.setImageBitmap(cached);
                } else {
                    imgThumb.setImageDrawable(null);
                    final String path = item.path;
                    final Uri uri = item.uri;
                    loadExecutor.execute(() -> {
                        Bitmap thumb = null;
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                thumb = getContentResolver().loadThumbnail(uri, new android.util.Size(300, 300), null);
                            } else {
                                thumb = MediaStore.Images.Thumbnails.getThumbnail(
                                        getContentResolver(), item.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                            }
                        } catch (Throwable ignored) {}

                        if (thumb == null) {
                            thumb = decodeSampledBitmap(path, uri, 300, 300);
                        }

                        if (thumb != null) {
                            final Bitmap finalThumb = thumb;
                            thumbCache.put(path, finalThumb);
                            mainHandler.post(() -> {
                                if (path.equals(currentPath)) {
                                    imgThumb.setImageBitmap(finalThumb);
                                }
                            });
                        }
                    });
                }
            }
        }
    }

    private Bitmap decodeSampledBitmap(String path, Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            if (path != null && path.startsWith("/") && new File(path).exists()) {
                BitmapFactory.decodeFile(path, options);
            } else {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(is, null, options);
                }
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            if (path != null && path.startsWith("/") && new File(path).exists()) {
                return BitmapFactory.decodeFile(path, options);
            } else {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    return BitmapFactory.decodeStream(is, null, options);
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
