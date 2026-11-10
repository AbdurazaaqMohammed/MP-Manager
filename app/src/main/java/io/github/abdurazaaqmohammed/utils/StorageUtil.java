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
        /** Optional description for a pinned network volume with unknown capacity. */
        public String description;

        public int usedPercent() {
            if (totalBytes <= 0) return 0;
            return (int) Math.max(0, Math.min(100, usedBytes * 100.0 / totalBytes));
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

                    if (dir == null) {
                        // OTG / HDD drives where getDirectory() returns null on Android 11+
                        String uuid = vol.getUuid();
                        if (uuid != null && !uuid.isEmpty()) {
                            File candidate = new File("/storage/" + uuid);
                            if (candidate.exists() || (AccessManager.fileOpsOn(ctx) && AccessManager.exists(ctx, candidate.getAbsolutePath()))) {
                                dir = candidate;
                            } else {
                                File mediaRw = new File("/mnt/media_rw/" + uuid);
                                if (mediaRw.exists() || (AccessManager.fileOpsOn(ctx) && AccessManager.exists(ctx, mediaRw.getAbsolutePath()))) {
                                    dir = mediaRw;
                                }
                            }
                        }
                    }

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

                    try {
                        StatFs statFs = new StatFs(si.path);
                        long blockSize = statFs.getBlockSizeLong();
                        long totalBlocks = statFs.getBlockCountLong();
                        long availableBlocks = statFs.getAvailableBlocksLong();

                        si.totalBytes = blockSize * totalBlocks;
                        si.freeBytes = blockSize * availableBlocks;
                        si.usedBytes = si.totalBytes - si.freeBytes;
                    } catch (Exception ignored) {
                    }

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

        try {
            if (AccessManager.fileOpsOn(ctx)) {
                // Discover additional OTG mounts under /mnt/media_rw directly via root
                File[] mediaRwList = AccessManager.listWithStat(ctx, "/mnt/media_rw");
                if (mediaRwList != null) {
                    for (File m : mediaRwList) {
                        String mPath = m.getAbsolutePath();
                        boolean alreadyAdded = false;
                        for (StorageInfo existing : list) {
                            if (mPath.equals(existing.path) || (existing.path != null && existing.path.endsWith("/" + m.getName()))) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        if (!alreadyAdded) {
                            StorageInfo otg = new StorageInfo();
                            otg.name = "USB Storage (" + m.getName() + ")";
                            otg.path = mPath;
                            try {
                                StatFs sOtg = new StatFs(mPath);
                                long bsOtg = sOtg.getBlockSizeLong();
                                otg.totalBytes = bsOtg * sOtg.getBlockCountLong();
                                otg.freeBytes = bsOtg * sOtg.getAvailableBlocksLong();
                                otg.usedBytes = otg.totalBytes - otg.freeBytes;
                            } catch (Exception ignored) {
                            }
                            list.add(otg);
                        }
                    }
                }

                StorageInfo root = new StorageInfo();
                root.name = "Root (/)";
                root.path = "/";
                StatFs s3 = new StatFs("/");
                long bs3 = s3.getBlockSizeLong();
                root.totalBytes = bs3 * s3.getBlockCountLong();
                root.freeBytes = bs3 * s3.getAvailableBlocksLong();
                root.usedBytes = root.totalBytes - root.freeBytes;
                list.add(root);
            }
        } catch (Exception ignored) {
        }

        return list;
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
