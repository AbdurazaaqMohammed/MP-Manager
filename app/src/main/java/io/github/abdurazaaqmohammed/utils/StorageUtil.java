package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

public class StorageUtil {
    public static class StorageInfo {
        public String name;
        public String path;
        public long totalBytes;
        public long freeBytes;
        public long usedBytes;

        public int usedPercent() {
            if (totalBytes <= 0) return 0;
            return (int) ((usedBytes * 100L) / totalBytes);
        }
    }

    private static List<StorageInfo> getStorageInfos(@NonNull Context ctx) {
        List<StorageInfo> list = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 30) {
            StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                List<StorageVolume> volumes = sm.getStorageVolumes();
                for (StorageVolume vol : volumes) {
                    File dir = vol.getDirectory();

                    if (dir == null) continue;

                    String state = null;
                    try {
                        state = vol.getState();
                    } catch (Exception ignored) {
                    }
                    if (state != null && !(Environment.MEDIA_MOUNTED.equals(state)
                            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) {
                        continue; // only mounted ones
                    }

                    StorageInfo si = new StorageInfo();
                    si.path = dir.getAbsolutePath();

                    boolean isPrimary = false;
                    try {
                        isPrimary = vol.isPrimary();
                    } catch (Exception ignored) {
                    }
                    if (isPrimary) si.name = "Internal storage";
                    else {
                        CharSequence desc = null;
                        try {
                            desc = vol.getDescription(ctx);
                        } catch (Exception ignored) {
                        }
                        si.name = (desc == null || desc.length() == 0) ? "Storage" : desc.toString();
                    }

                    StatFs statFs = new StatFs(si.path);
                    long blockSize = statFs.getBlockSizeLong();
                    long totalBlocks = statFs.getBlockCountLong();
                    long availableBlocks = statFs.getAvailableBlocksLong();

                    si.totalBytes = blockSize * totalBlocks;
                    si.freeBytes = blockSize * availableBlocks;
                    si.usedBytes = si.totalBytes - si.freeBytes;

                    list.add(si);
                }
            }
        } else {
            // Fallback for older devices (basic)
            StorageInfo internal = new StorageInfo();
            internal.name = "Internal storage";
            internal.path = Environment.getExternalStorageDirectory().getPath();
            StatFs s = new StatFs(internal.path);
            long bs = s.getBlockSizeLong();
            internal.totalBytes = bs * s.getBlockCountLong();
            internal.freeBytes = bs * s.getAvailableBlocksLong();
            internal.usedBytes = internal.totalBytes - internal.freeBytes;
            list.add(internal);

            File rootDirectory = Environment.getRootDirectory();
            StorageInfo sys = new StorageInfo();
            sys.name = "System";
            sys.path = rootDirectory.getPath();
            StatFs s2 = new StatFs(sys.path);
            long bs2 = s2.getBlockSizeLong();
            sys.totalBytes = bs2 * s2.getBlockCountLong();
            sys.freeBytes = bs2 * s2.getAvailableBlocksLong();
            sys.usedBytes = sys.totalBytes - sys.freeBytes;
            list.add(sys);
        }

        return list;
    }

    public static void populateStorageUI(@NonNull MainActivity ctx, @NonNull LinearLayout storageContainer) {
        storageContainer.removeAllViews();

        List<StorageInfo> infos = getStorageInfos(ctx);
        LayoutInflater inflater = LayoutInflater.from(ctx);

        if (infos.isEmpty()) {
            TextView tv = new TextView(ctx);
            tv.setText("No mounted storage found.");
            storageContainer.addView(tv);
            return;
        }

        for (StorageInfo si : infos) {
            View row = inflater.inflate(R.layout.item_storage, storageContainer, false);
            row.setOnClickListener(v -> {
                ctx.loadFolderInPane(new File(si.path), ctx.lastPaneSelected == 1);
                ctx.closeSidebarDrawer();
            });
            TextView tvName = row.findViewById(R.id.tvStorageName);
            ProgressBar pb = row.findViewById(R.id.pbUsed);
            TextView tvUsedFree = row.findViewById(R.id.tvUsedFree);

            tvName.setText(si.name);
            pb.setProgress(si.usedPercent());

            tvUsedFree.setText(ctx.rss.getString(R.string.used, FileSize.getHumanReadableFileSize(si.usedBytes), FileSize.getHumanReadableFileSize(si.freeBytes)));
            TextView tvPercent = row.findViewById(R.id.tvUsedPercent);

            int usedPct = si.usedPercent();
            pb.setProgress(usedPct);
            tvPercent.setText(ctx.rss.getString(R.string.usedpt, usedPct));
            storageContainer.addView(row);
        }
    }
}