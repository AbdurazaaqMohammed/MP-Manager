package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.tools.StorageManagerActivity;

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

    public static List<StorageInfo> getStorageInfos(@NonNull Context ctx) {
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
                    if (isPrimary) si.name = ctx.getString(R.string.internal_storage);
                    else {
                        CharSequence desc = null;
                        try {
                            desc = vol.getDescription(ctx);
                        } catch (Exception ignored) {
                        }
                        si.name = (TextUtils.isEmpty(desc)) ? ctx.getString(R.string.storage) : desc.toString();
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
            internal.name = ctx.getString(R.string.internal_storage);
            internal.path = Environment.getExternalStorageDirectory().getPath();
            StatFs s = new StatFs(internal.path);
            long bs = s.getBlockSizeLong();
            internal.totalBytes = bs * s.getBlockCountLong();
            internal.freeBytes = bs * s.getAvailableBlocksLong();
            internal.usedBytes = internal.totalBytes - internal.freeBytes;
            list.add(internal);

            File rootDirectory = Environment.getRootDirectory();
            StorageInfo sys = new StorageInfo();
            sys.name = ctx.getString(R.string.system);
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

    /**
     * Usage of the filesystem root, shown next to the internal volume so the root row carries the
     * same progress bar and capacity line. Returns null when {@code /} cannot be stat'ed, which is
     * what happens on devices that hide it from unprivileged apps.
     */
    public static StorageInfo getRootInfo(@NonNull Context ctx) {
        StorageInfo info = new StorageInfo();
        info.name = ctx.getString(R.string.sidebar_root);
        info.path = "/";
        try {
            StatFs fs = new StatFs("/");
            long blockSize = fs.getBlockSizeLong();
            info.totalBytes = blockSize * fs.getBlockCountLong();
            info.freeBytes = blockSize * fs.getAvailableBlocksLong();
            info.usedBytes = info.totalBytes - info.freeBytes;
        } catch (Exception ignored) {
        }
        if (info.totalBytes <= 0 || !isBrowsable(new File("/"))) return null;
        return info;
    }

    /**
     * True when the directory can actually be listed. A volume row that opens onto a folder the
     * app may not read is worse than no row at all, so the drawer drops it. An empty directory is
     * still browsable; only a denied or non-directory path is not.
     */
    private static boolean isBrowsable(@NonNull File dir) {
        if (!dir.isDirectory() || !dir.canRead()) return false;
        return dir.list() != null;
    }

    public static void populateStorageUI(@NonNull MainActivity ctx, @NonNull LinearLayout storageContainer) {
        storageContainer.removeAllViews();

        List<StorageInfo> infos = getStorageInfos(ctx);
        LayoutInflater inflater = LayoutInflater.from(ctx);

        if (infos.isEmpty()) {
            TextView tv = new TextView(ctx);
            tv.setText(R.string.no_mounted_storage_found);
            storageContainer.addView(tv);
            return;
        }

        for (StorageInfo si : infos) {
            View row = inflater.inflate(R.layout.item_storage, storageContainer, false);
            row.setOnClickListener(v -> {
                ctx.loadFolderInPane(new File(si.path), ctx.lastPaneSelected == 1);
                ctx.closeSidebarDrawer();
            });
            row.setOnLongClickListener(v -> {
                PopupMenu menu = new PopupMenu(ctx, v);
                String s = ctx.rss.getString(R.string.manage_storage);
                menu.getMenu().add(s);
                menu.getMenu().add(ctx.rss.getString(R.string.open_location));
                menu.setOnMenuItemClickListener(item -> {
                    if (s.equals(item.getTitle().toString())) {
                        ctx.startActivity(new Intent(ctx, StorageManagerActivity.class));
                    } else {
                        ctx.loadFolderInPane(new File(si.path), ctx.lastPaneSelected == 1);
                        ctx.closeSidebarDrawer();
                    }
                    return true;
                });
                menu.show();
                return true;
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