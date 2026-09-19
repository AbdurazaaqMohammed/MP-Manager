package io.github.abdurazaaqmohammed.tools;

import android.app.usage.StorageStatsManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.player.ImageViewerActivity;
import io.github.abdurazaaqmohammed.player.MediaPlayerActivity;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootPermissionHelper;
import io.github.abdurazaaqmohammed.utils.StorageUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class StorageManagerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout volumeBox;
    private LinearLayout typeBox;
    private ListView largeList;
    private ListView cacheList;
    private TextView scanStatus;
    private ProgressBar scanBar;
    private volatile boolean scanning = false;
    private final List<FileRow> largeFiles = new ArrayList<>();
    private final List<FileRow> largeVisible = new ArrayList<>();
    private BaseAdapter largeAdapter;
    private final List<CacheRow> cacheRows = new ArrayList<>();
    private BaseAdapter cacheAdapter;
    private TextView cacheStatus;
    private TextView largeTitle;
    private MaterialButton scanBtn;
    private MaterialButton stopBtn;
    private MaterialButton deleteSelectedBtn;
    private String typeFilter;
    private Map<String, Long> lastBuckets = new HashMap<>();
    private long lastGrand;
    private LruCache<String, Bitmap> thumbCache;
    private ExecutorService thumbExecutor;

    private static class FileRow {
        final File file;
        final long size;
        final String bucket;
        boolean checked;

        FileRow(File file, long size, String bucket) {
            this.file = file;
            this.size = size;
            this.bucket = bucket == null ? "Other" : bucket;
        }
    }

    private static class CacheRow {
        final String packageName;
        final String label;
        long cacheBytes;
        boolean checked;

        CacheRow(String packageName, String label, long cacheBytes) {
            this.packageName = packageName;
            this.label = label;
            this.cacheBytes = cacheBytes;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(prefs.getInt("theme", dark ? io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark : io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Storage Manager");
        toolbar.setSubtitle("Free up space");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        section(box, "Volumes");
        volumeBox = new LinearLayout(this);
        volumeBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(volumeBox);
        section(box, "Space By Type");
        scanStatus = new TextView(this);
        scanStatus.setText("Not scanned yet");
        scanStatus.setTextSize(13);
        box.addView(scanStatus);
        scanBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        scanBar.setMax(100);
        box.addView(scanBar);
        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(scanRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scanBtn = new MaterialButton(this);
        scanBtn.setText("Scan now");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = dp(4);
        sp.setMargins(m, m, m, m);
        scanRow.addView(scanBtn, sp);
        stopBtn = new MaterialButton(this);
        stopBtn.setText("Stop");
        scanRow.addView(stopBtn, sp);
        scanBtn.setOnClickListener(v -> startScan());
        stopBtn.setOnClickListener(v -> scanning = false);
        updateScanButtons();
        typeBox = new LinearLayout(this);
        typeBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(typeBox);
        TextView typeHint = new TextView(this);
        typeHint.setText("Tap a type to filter the largest files below");
        typeHint.setTextSize(12);
        box.addView(typeHint);
        largeTitle = section(box, "Largest Files");
        View.OnTouchListener nestedScrollFix = (v, event) -> {
            int action = event.getAction();
            boolean hasItems = v instanceof ListView && ((ListView) v).getAdapter() != null && ((ListView) v).getAdapter().getCount() > 0;
            if (hasItems && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        };
        largeList = new ListView(this);
        largeList.setOnTouchListener(nestedScrollFix);
        largeList.setVisibility(View.GONE);
        box.addView(largeList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        largeAdapter = fileAdapter();
        largeList.setAdapter(largeAdapter);
        largeList.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= largeVisible.size()) return;
            openFile(largeVisible.get(pos).file, largeVisible.get(pos).bucket);
        });
        largeList.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= largeVisible.size()) return true;
            confirmDeleteFile(largeVisible.get(pos).file);
            return true;
        });
        deleteSelectedBtn = new MaterialButton(this);
        deleteSelectedBtn.setText("Delete selected");
        deleteSelectedBtn.setVisibility(View.GONE);
        box.addView(deleteSelectedBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelected());
        updateDeleteSelectedBtn();
        section(box, "App Caches");
        cacheStatus = new TextView(this);
        cacheStatus.setTextSize(13);
        box.addView(cacheStatus);
        LinearLayout cacheRow = new LinearLayout(this);
        cacheRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(cacheRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton loadCache = new MaterialButton(this);
        loadCache.setText("Load");
        cacheRow.addView(loadCache, sp);
        MaterialButton clearSel = new MaterialButton(this);
        clearSel.setText("Clear selected");
        cacheRow.addView(clearSel, sp);
        MaterialButton clearAll = new MaterialButton(this);
        clearAll.setText("Clear all");
        box.addView(clearAll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(autoRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton autoSel = new MaterialButton(this);
        autoSel.setText("Auto-clear selected");
        autoRow.addView(autoSel, sp);
        MaterialButton autoAll = new MaterialButton(this);
        autoAll.setText("Auto-clear all");
        autoRow.addView(autoAll, sp);
        MaterialButton rootCheck = new MaterialButton(this);
        rootCheck.setText("Root check");
        autoRow.addView(rootCheck, sp);
        TextView autoHint = new TextView(this);
        autoHint.setText("No root? Auto-clear opens each app info and taps Clear cache for you using accessibility.");
        autoHint.setTextSize(12);
        box.addView(autoHint);
        cacheList = new ListView(this);
        cacheList.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        box.addView(cacheList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        cacheAdapter = cacheAdapter();
        cacheList.setAdapter(cacheAdapter);
        cacheList.setOnItemClickListener((p, v, pos, id) -> {
            CacheRow row = cacheRows.get(pos);
            row.checked = !row.checked;
            cacheAdapter.notifyDataSetChanged();
        });
        cacheList.setOnItemLongClickListener((p, v, pos, id) -> {
            openAppInfo(cacheRows.get(pos).packageName);
            return true;
        });
        loadCache.setOnClickListener(v -> loadCaches());
        clearSel.setOnClickListener(v -> clearCaches(true));
        clearAll.setOnClickListener(v -> clearCaches(false));
        autoSel.setOnClickListener(v -> autoClearCaches(true));
        autoAll.setOnClickListener(v -> autoClearCaches(false));
        rootCheck.setOnClickListener(v -> showRootCheck(rootCheck));
        refreshVolumes();
    }

    private void showRootCheck(MaterialButton button) {
        button.setEnabled(false);
        Extensions.showMessage(this, "Probing root");
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            try {
                RootManager rm = RootManager.getInstance(StorageManagerActivity.this);
                out.append("root available: ").append(rm.isRootAvailable()).append("\n");
                out.append("working mode: ").append(rm.getWorkingMode()).append("\n");
                out.append("file ops: ").append(rm.isRootFileOpsEnabled()).append("\n");
                RootManager.ShellResult id = rm.execute("id 2>&1", 10);
                out.append("id: ").append(id.isSuccess() ? id.output().trim() : "fail " + safeErr(id)).append("\n");
                RootManager.ShellResult ctx = rm.execute("cat /proc/self/attr/current 2>&1", 10);
                out.append("selinux: ").append(ctx.isSuccess() ? ctx.output().trim() : "fail " + safeErr(ctx)).append("\n");
                RootManager.ShellResult plain = rm.execute("ls -1A -- /data/data 2>/dev/null | wc -l", 15);
                out.append("plain count: ").append(plain.isSuccess() ? plain.output().trim() : "fail " + safeErr(plain)).append("\n");
                RootManager.ShellResult named = rm.execute("ls -ld /data/data/com.android.settings 2>&1", 15);
                out.append("named probe: ").append(named.isSuccess() ? named.output().trim() : "fail " + safeErr(named)).append("\n");
                RootManager.ShellResult ns = rm.execute("nsenter --help >/dev/null 2>&1 && echo yes || echo no", 10);
                out.append("nsenter: ").append(ns.isSuccess() ? ns.output().trim() : "fail").append("\n");
                RootManager.ShellResult global = rm.executeGlobalNs("ls -1A -- /data/data 2>/dev/null | wc -l", 15);
                out.append("global-ns count: ").append(global.isSuccess() ? global.output().trim() : "fail " + safeErr(global)).append("\n");
                out.append("prefers global ns: ").append(rm.prefersGlobalNs()).append("\n");
            } catch (Exception e) {
                out.append("error: ").append(e.getMessage()).append("\n");
            }
            String text = out.toString();
            handler.post(() -> {
                button.setEnabled(true);
                ScrollView scroll = new ScrollView(StorageManagerActivity.this);
                TextView body = new TextView(StorageManagerActivity.this);
                body.setText(text.trim());
                body.setTypeface(android.graphics.Typeface.MONOSPACE);
                body.setTextIsSelectable(true);
                int p = dp(16);
                body.setPadding(p, p, p, p);
                scroll.addView(body);
                new MaterialAlertDialogBuilder(StorageManagerActivity.this)
                        .setTitle("Root check")
                        .setView(scroll)
                        .setPositiveButton("Copy", (d, w) -> {
                            try {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(ClipData.newPlainText("rootcheck", body.getText().toString()));
                                Extensions.showMessage(StorageManagerActivity.this, "Copied");
                            } catch (Exception ignored) {
                            }
                        })
                        .setNeutralButton("Test read+write", (d, w) -> runRootRoundTrip(body))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private String safeErr(RootManager.ShellResult r) {
        try {
            if (r == null || r.error() == null) return "";
            String e = r.error().trim().replace("\n", " ");
            return e.length() > 120 ? e.substring(0, 120) : e;
        } catch (Exception e) {
            return "";
        }
    }

    private void runRootRoundTrip(TextView body) {
        Extensions.showMessage(this, "Testing root read+write");
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            java.io.File local = null;
            java.io.File back = null;
            String remote = "/data/local/tmp/mpman_roundtrip_test";
            try {
                RootManager rm = RootManager.getInstance(StorageManagerActivity.this);
                String canary = "MPManager roundtrip " + System.currentTimeMillis() + " 0123456789 abcdefgh";
                local = new java.io.File(getCacheDir(), "roundtrip_src.tmp");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(local)) {
                    fos.write(canary.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                try {
                    rm.streamToRoot(local, remote);
                    out.append("roundtrip write: OK\n");
                } catch (Exception e) {
                    out.append("roundtrip write FAIL: ").append(e.getMessage()).append("\n");
                    throw new Exception("stop");
                }
                try {
                    back = new java.io.File(getCacheDir(), "roundtrip_back.tmp");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(back)) {
                        rm.streamFromRoot(remote, fos, 65536L);
                    }
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(back)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
                    }
                    byte[] data = bos.toByteArray();
                    String readBack = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    out.append("roundtrip read: OK\n");
                    out.append("roundtrip match: ").append(canary.equals(readBack) ? "YES" : "NO (got " + data.length + " bytes)").append("\n");
                } catch (Exception e) {
                    out.append("roundtrip read FAIL: ").append(e.getMessage()).append("\n");
                    throw new Exception("stop");
                }
            } catch (Exception e) {
                if (!"stop".equals(e.getMessage())) out.append("roundtrip error: ").append(e.getMessage()).append("\n");
            } finally {
                try {
                    RootManager.getInstance(StorageManagerActivity.this).deleteFile(remote);
                    out.append("roundtrip cleanup: OK\n");
                } catch (Exception e) {
                    out.append("roundtrip cleanup FAIL: ").append(e.getMessage()).append("\n");
                }
                try {
                    if (local != null) local.delete();
                    if (back != null) back.delete();
                } catch (Exception ignored) {
                }
            }
            String extra = out.toString();
            handler.post(() -> {
                body.setText(body.getText().toString() + "\n" + extra.trim());
                Extensions.showMessage(StorageManagerActivity.this, "Round-trip done");
            });
        }).start();
    }

    private void autoClearCaches(boolean selectedOnly) {
        List<CacheCleaner.QueueItem> targets = new ArrayList<>();
        for (CacheRow r : cacheRows) {
            if (!selectedOnly || r.checked) targets.add(new CacheCleaner.QueueItem(r.packageName, r.label));
        }
        if (targets.isEmpty()) {
            Extensions.showMessage(this, "Nothing selected, tap Load first");
            return;
        }
        if (RootPermissionHelper.isRootShellReady(this)) {
            clearCaches(selectedOnly);
            return;
        }
        if (!CacheCleaner.isServiceEnabled(this)) {
            boolean elevated = RootPermissionHelper.hasElevatedShell(this);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Enable auto-clear service")
                    .setMessage("Auto-clear needs the MP Manager Cache Cleaner accessibility service." + (elevated ? " Root/Shizuku found, it can be enabled automatically." : " Enable it in system settings, then start again."))
                    .setPositiveButton(elevated ? "Enable automatically" : "Open settings", (d, w) -> {
                        if (elevated) {
                            new Thread(() -> {
                                boolean ok = RootPermissionHelper.enableAccessibilityForViaElevated(StorageManagerActivity.this, CacheCleanerService.class.getName());
                                handler.post(() -> {
                                    Extensions.showMessage(StorageManagerActivity.this, ok ? "Service enabled, tap auto-clear again" : "Auto-enable failed, enable manually");
                                    if (!ok) CacheCleaner.openAccessibilitySettings(StorageManagerActivity.this);
                                });
                            }).start();
                        } else {
                            CacheCleaner.openAccessibilitySettings(StorageManagerActivity.this);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(selectedOnly ? "Auto-clear selected" : "Auto-clear all")
                .setMessage(targets.size() + " apps. Settings screens will open one by one, stay on this device until done.")
                .setPositiveButton("Start", (d, w) -> startAutoClear(targets))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startAutoClear(List<CacheCleaner.QueueItem> targets) {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("Auto-clearing caches");
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(targets.size());
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.setButton(android.content.DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), (d, w) -> CacheCleaner.stop());
        progress.show();
        boolean started = CacheCleaner.start(this, targets, new CacheCleaner.Listener() {
            public void onAppStarted(String label, int index, int total) {
                progress.setMessage((index + 1) + "/" + total + " " + label);
            }

            public void onAppCleared(String packageName) {
                progress.setProgress(progress.getProgress() + 1);
            }

            public void onAppSkipped(String packageName) {
                progress.setProgress(progress.getProgress() + 1);
            }

            public void onFinished(int cleared, int total) {
                try {
                    progress.dismiss();
                } catch (Exception ignored) {
                }
                Extensions.showMessage(StorageManagerActivity.this, "Cleared " + cleared + "/" + total);
                loadCaches();
                refreshVolumes();
            }
        });
        if (!started) {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            Extensions.showMessage(this, "Could not start, enable the service first");
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView section(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(16), 0, dp(8));
        box.addView(t, p);
        return t;
    }

    private void refreshVolumes() {
        new Thread(() -> {
            List<StorageUtil.StorageInfo> infos = StorageUtil.getStorageInfos(StorageManagerActivity.this);
            handler.post(() -> {
                volumeBox.removeAllViews();
                for (StorageUtil.StorageInfo si : infos) {
                    MaterialCardView card = new MaterialCardView(this);
                    card.setRadius(dp(12));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.setMargins(0, dp(4), 0, dp(4));
                    card.setLayoutParams(cp);
                    LinearLayout inner = new LinearLayout(this);
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setPadding(dp(12), dp(12), dp(12), dp(12));
                    TextView name = new TextView(this);
                    name.setText(si.name + "  " + si.path);
                    name.setTypeface(null, android.graphics.Typeface.BOLD);
                    inner.addView(name);
                    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    bar.setMax(100);
                    bar.setProgress(si.usedPercent());
                    inner.addView(bar);
                    TextView detail = new TextView(this);
                    detail.setText(FileSize.getHumanReadableFileSize(si.usedBytes) + " used of " + FileSize.getHumanReadableFileSize(si.totalBytes) + "  (" + si.usedPercent() + "%)  free " + FileSize.getHumanReadableFileSize(si.freeBytes));
                    detail.setTextSize(13);
                    inner.addView(detail);
                    card.addView(inner);
                    card.setOnClickListener(v -> startScan());
                    volumeBox.addView(card);
                }
            });
        }).start();
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        updateScanButtons();
        largeFiles.clear();
        applyTypeFilter();
        typeBox.removeAllViews();
        scanBar.setIndeterminate(true);
        scanStatus.setText("Scanning");
        new Thread(() -> {
            Map<String, Long> buckets = new HashMap<>();
            String[] names = new String[]{"Images", "Videos", "Audio", "Documents", "Archives", "APKs", "Other"};
            for (String n : names) buckets.put(n, 0L);
            PriorityQueue<FileRow> top = new PriorityQueue<>(40, (a, b) -> Long.compare(a.size, b.size));
            long totalFiles = 0;
            try {
                File rootDir = Environment.getExternalStorageDirectory();
                Deque<File> stack = new ArrayDeque<>();
                if (rootDir != null) stack.push(rootDir);
                int visited = 0;
                while (!stack.isEmpty() && scanning && visited < 400000) {
                    File dir = stack.pop();
                    File[] kids;
                    try {
                        kids = dir.listFiles();
                    } catch (Exception e) {
                        continue;
                    }
                    if (kids == null) continue;
                    for (File kid : kids) {
                        if (!scanning) break;
                        try {
                            if (kid.isDirectory()) {
                                if (!isSymlink(kid)) stack.push(kid);
                            } else {
                                long len = kid.length();
                                totalFiles++;
                                String bucket = bucketFor(kid.getName());
                                buckets.put(bucket, buckets.get(bucket) + len);
                                if (len > 1024 * 1024) {
                                    if (top.size() < 40) {
                                        top.offer(new FileRow(kid, len, bucket));
                                    } else if (len > top.peek().size) {
                                        top.poll();
                                        top.offer(new FileRow(kid, len, bucket));
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    visited++;
                    if (visited % 400 == 0) {
                        long done = totalFiles;
                        handler.post(() -> scanStatus.setText("Scanning " + done + " files"));
                    }
                }
            } catch (Exception ignored) {
            }
            List<FileRow> sorted = new ArrayList<>(top);
            sorted.sort((a, b) -> Long.compare(b.size, a.size));
            long files = totalFiles;
            Map<String, Long> result = buckets;
            scanning = false;
            handler.post(() -> {
                scanBar.setIndeterminate(false);
                scanBar.setProgress(100);
                scanStatus.setText(files + " files scanned, tap a volume to rescan");
                largeFiles.clear();
                largeFiles.addAll(sorted);
                lastBuckets = result;
                lastGrand = 0;
                for (long v : result.values()) lastGrand += v;
                applyTypeFilter();
                rebuildTypeRows();
                updateDeleteSelectedBtn();
                updateScanButtons();
                refreshVolumes();
            });
        }).start();
    }

    private boolean isSymlink(File f) {
        try {
            File canon;
            if (Build.VERSION.SDK_INT >= 26) {
                canon = f.getCanonicalFile();
            } else {
                canon = new File(f.getCanonicalPath());
            }
            File parent = f.getAbsoluteFile().getParentFile();
            return parent != null && !canon.getParent().equals(parent.getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    private String bucketFor(String name) {
        String n = name.toLowerCase(Locale.US);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        if (isExt(ext, new String[]{"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "raw", "dng"})) return "Images";
        if (isExt(ext, new String[]{"mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m2ts", "flv"})) return "Videos";
        if (isExt(ext, new String[]{"mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "mid", "midi"})) return "Audio";
        if (isExt(ext, new String[]{"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "epub", "rtf", "odt"})) return "Documents";
        if (isExt(ext, new String[]{"zip", "rar", "7z", "tar", "gz", "bz2", "xz"})) return "Archives";
        if (isExt(ext, new String[]{"apk", "apks", "xapk"})) return "APKs";
        return "Other";
    }

    private boolean isExt(String ext, String[] list) {
        for (String s : list) if (s.equals(ext)) return true;
        return false;
    }

    private void updateScanButtons() {
        if (scanBtn != null) scanBtn.setEnabled(!scanning);
        if (stopBtn != null) stopBtn.setEnabled(scanning);
    }

    private void applyTypeFilter() {
        largeVisible.clear();
        if (typeFilter == null) {
            largeVisible.addAll(largeFiles);
            if (largeTitle != null) largeTitle.setText("Largest Files (" + largeVisible.size() + ")");
        } else {
            for (FileRow row : largeFiles) {
                if (typeFilter.equals(row.bucket)) largeVisible.add(row);
            }
            if (largeTitle != null) largeTitle.setText("Largest " + typeFilter + " (" + largeVisible.size() + "), tap type again to clear");
        }
        if (largeAdapter != null) largeAdapter.notifyDataSetChanged();
        updateLargeVisibility();
        updateDeleteSelectedBtn();
    }

    private void updateLargeVisibility() {
        boolean show = !largeVisible.isEmpty();
        if (largeList != null) largeList.setVisibility(show ? View.VISIBLE : View.GONE);
        if (deleteSelectedBtn != null) deleteSelectedBtn.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void rebuildTypeRows() {
        typeBox.removeAllViews();
        String[] names = new String[]{"Images", "Videos", "Audio", "Documents", "Archives", "APKs", "Other"};
        for (String n : names) {
            long v = lastBuckets.containsKey(n) ? lastBuckets.get(n) : 0L;
            addBucketRow(n, v, lastGrand);
        }
    }

    private void updateDeleteSelectedBtn() {
        if (deleteSelectedBtn == null) return;
        int count = 0;
        for (FileRow row : largeVisible) {
            if (row.checked) count++;
        }
        deleteSelectedBtn.setText(count == 0 ? "Delete selected" : "Delete selected (" + count + ")");
        deleteSelectedBtn.setEnabled(count > 0);
    }

    private void addBucketRow(String name, long value, long grand) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.setBackgroundResource(android.R.color.transparent);
        row.setClickable(true);
        row.setFocusable(true);
        TextView label = new TextView(this);
        int pct = grand <= 0 ? 0 : (int) (value * 100 / grand);
        label.setText(name + "  " + FileSize.getHumanReadableFileSize(value) + "  " + pct + "%");
        label.setTextSize(14);
        if (name.equals(typeFilter)) {
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE));
        }
        row.addView(label);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(pct);
        row.addView(bar);
        row.setOnClickListener(v -> {
            if (name.equals(typeFilter)) {
                typeFilter = null;
            } else {
                typeFilter = name;
            }
            applyTypeFilter();
            rebuildTypeRows();
        });
        typeBox.addView(row);
    }

    private static class LargeHolder {
        ImageView thumb;
        TextView name;
        TextView path;
        CheckBox box;
    }

    private void ensureThumbCache() {
        if (thumbCache != null && thumbExecutor != null) return;
        int mem = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int size = Math.max(4096, mem / 8);
        thumbCache = new LruCache<String, Bitmap>(size) {
            protected int sizeOf(String key, Bitmap value) {
                try {
                    return value.getByteCount() / 1024;
                } catch (Exception e) {
                    return 64;
                }
            }
        };
        thumbExecutor = Executors.newFixedThreadPool(2);
    }

    private Bitmap loadThumb(File file, String bucket) {
        try {
            if ("Images".equals(bucket)) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                int sample = 1;
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                while (max / (sample * 2) >= 144 && sample < 16) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
            if ("Videos".equals(bucket)) {
                if (Build.VERSION.SDK_INT >= 29) {
                    return ThumbnailUtils.createVideoThumbnail(file, new android.util.Size(144, 144), null);
                }
                return ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), android.provider.MediaStore.Images.Thumbnails.MINI_KIND);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private BaseAdapter fileAdapter() {
        ensureThumbCache();
        float density = getResources().getDisplayMetrics().density;
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        return new BaseAdapter() {
            public int getCount() {
                return largeVisible.size();
            }

            public Object getItem(int position) {
                return largeVisible.get(position);
            }

            public long getItemId(int position) {
                return position;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                FileRow row = largeVisible.get(position);
                LargeHolder holder;
                if (convertView == null) {
                    MaterialCardView card = new MaterialCardView(StorageManagerActivity.this);
                    card.setRadius(12 * density);
                    LinearLayout line = new LinearLayout(StorageManagerActivity.this);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setGravity(Gravity.CENTER_VERTICAL);
                    int cp = (int) (10 * density);
                    line.setPadding(cp, cp, cp, cp);
                    ImageView thumb = new ImageView(StorageManagerActivity.this);
                    int ts = (int) (56 * density);
                    thumb.setLayoutParams(new LinearLayout.LayoutParams(ts, ts));
                    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    line.addView(thumb);
                    LinearLayout text = new LinearLayout(StorageManagerActivity.this);
                    text.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tp.setMargins(cp, 0, cp, 0);
                    text.setLayoutParams(tp);
                    TextView name = new TextView(StorageManagerActivity.this);
                    name.setTypeface(null, android.graphics.Typeface.BOLD);
                    name.setTextColor(onSurface);
                    text.addView(name);
                    TextView path = new TextView(StorageManagerActivity.this);
                    path.setTextSize(12);
                    path.setTextColor(onVariant);
                    text.addView(path);
                    line.addView(text);
                    CheckBox box = new CheckBox(StorageManagerActivity.this);
                    box.setFocusable(false);
                    line.addView(box);
                    card.addView(line);
                    holder = new LargeHolder();
                    holder.thumb = thumb;
                    holder.name = name;
                    holder.path = path;
                    holder.box = box;
                    card.setTag(holder);
                    convertView = card;
                } else {
                    holder = (LargeHolder) convertView.getTag();
                }
                holder.name.setText(row.file.getName() + "  " + FileSize.getHumanReadableFileSize(row.size));
                holder.path.setText(row.file.getAbsolutePath());
                holder.box.setOnCheckedChangeListener(null);
                holder.box.setChecked(row.checked);
                holder.box.setOnCheckedChangeListener((b, checked) -> {
                    row.checked = checked;
                    updateDeleteSelectedBtn();
                });
                String absPath = row.file.getAbsolutePath();
                if ("Images".equals(row.bucket) || "Videos".equals(row.bucket)) {
                    holder.thumb.setTag(absPath);
                    Bitmap cached = thumbCache.get(absPath);
                    if (cached != null) {
                        holder.thumb.setImageBitmap(cached);
                    } else {
                        holder.thumb.setImageResource(android.R.drawable.ic_menu_gallery);
                        ImageView target = holder.thumb;
                        thumbExecutor.execute(() -> {
                            Bitmap bmp = loadThumb(row.file, row.bucket);
                            if (bmp != null) {
                                try {
                                    thumbCache.put(absPath, bmp);
                                } catch (Exception ignored) {
                                }
                            }
                            final Bitmap done = bmp;
                            handler.post(() -> {
                                if (absPath.equals(target.getTag()) && done != null) {
                                    target.setImageBitmap(done);
                                }
                            });
                        });
                    }
                } else if ("APKs".equals(row.bucket)) {
                    holder.thumb.setTag(null);
                    holder.thumb.setImageResource(io.github.abdurazaaqmohammed.MPManager.R.drawable.apk_document_24px);
                } else if ("Archives".equals(row.bucket)) {
                    holder.thumb.setTag(null);
                    holder.thumb.setImageResource(io.github.abdurazaaqmohammed.MPManager.R.drawable.archive_24px);
                } else {
                    holder.thumb.setTag(null);
                    holder.thumb.setImageResource(R.drawable.baseline_insert_drive_file_24);
                }
                return convertView;
            }
        };
    }

    private void openFile(File file, String bucket) {
        try {
            if ("Images".equals(bucket)) {
                ImageViewerActivity.open(this, file.getAbsolutePath());
                return;
            }
            if ("Videos".equals(bucket) || "Audio".equals(bucket)) {
                MediaPlayerActivity.openAndPlay(this, file.getAbsolutePath());
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String type = getContentResolver().getType(uri);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type == null ? "*/*" : type);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Extensions.showMessage(this, "Cannot open");
        }
    }

    private void removeFromLargeLists(String absPath) {
        for (int i = largeFiles.size() - 1; i >= 0; i--) {
            if (largeFiles.get(i).file.getAbsolutePath().equals(absPath)) largeFiles.remove(i);
        }
        try {
            thumbCache.remove(absPath);
        } catch (Exception ignored) {
        }
        applyTypeFilter();
    }

    private boolean deleteOneFile(File file) {
        try {
            io.github.abdurazaaqmohammed.utils.AccessManager.delete(StorageManagerActivity.this, file.getAbsolutePath(), true);
        } catch (Exception ignored) {
        }
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {
            }
        }
        return !file.exists();
    }

    private void confirmDeleteSelected() {
        List<FileRow> targets = new ArrayList<>();
        for (FileRow row : largeVisible) {
            if (row.checked) targets.add(row);
        }
        if (targets.isEmpty()) return;
        long total = 0;
        for (FileRow row : targets) total += row.size;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + targets.size() + " files")
                .setMessage(FileSize.getHumanReadableFileSize(total) + " will be freed")
                .setPositiveButton("Delete", (d, w) -> new Thread(() -> {
                    int ok = 0;
                    for (FileRow row : targets) {
                        if (deleteOneFile(row.file)) ok++;
                    }
                    int done = ok;
                    handler.post(() -> {
                        for (FileRow row : targets) {
                            if (!row.file.exists()) removeFromLargeLists(row.file.getAbsolutePath());
                        }
                        refreshVolumes();
                        Extensions.showMessage(StorageManagerActivity.this, "Deleted " + done + "/" + targets.size());
                    });
                }).start())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteFile(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete file")
                .setMessage(file.getAbsolutePath() + "\n" + FileSize.getHumanReadableFileSize(file.length()))
                .setPositiveButton("Delete", (d, w) -> new Thread(() -> {
                    boolean ok = deleteOneFile(file);
                    final boolean done = ok;
                    handler.post(() -> {
                        Extensions.showMessage(StorageManagerActivity.this, done ? "Deleted" : "Delete failed");
                        if (done) {
                            removeFromLargeLists(file.getAbsolutePath());
                            refreshVolumes();
                        }
                    });
                }).start())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private BaseAdapter cacheAdapter() {
        float density = getResources().getDisplayMetrics().density;
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        return new BaseAdapter() {
            public int getCount() {
                return cacheRows.size();
            }

            public Object getItem(int position) {
                return cacheRows.get(position);
            }

            public long getItemId(int position) {
                return position;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                CacheRow row = cacheRows.get(position);
                LinearLayout line = new LinearLayout(StorageManagerActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
                CheckBox box = new CheckBox(StorageManagerActivity.this);
                box.setChecked(row.checked);
                box.setFocusable(false);
                line.addView(box);
                LinearLayout text = new LinearLayout(StorageManagerActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView name = new TextView(StorageManagerActivity.this);
                name.setText(row.label);
                name.setTextColor(onSurface);
                text.addView(name);
                TextView sub = new TextView(StorageManagerActivity.this);
                sub.setText(row.packageName + "  " + FileSize.getHumanReadableFileSize(row.cacheBytes));
                sub.setTextSize(12);
                sub.setTextColor(onVariant);
                text.addView(sub);
                line.addView(text);
                box.setOnCheckedChangeListener((b, checked) -> row.checked = checked);
                return line;
            }
        };
    }

    @android.annotation.TargetApi(26)
    private long queryCacheBytes26(ApplicationInfo app) {
        try {
            StorageStatsManager stats = (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE);
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            if (stats == null || sm == null) return -1;
            return stats.queryStatsForPackage(sm.getUuidForPath(new File(app.sourceDir)), app.packageName, android.os.Process.myUserHandle()).getCacheBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    private void loadCaches() {
        cacheStatus.setText("Loading caches");
        new Thread(() -> {
            List<CacheRow> rows = new ArrayList<>();
            boolean rootReady = RootPermissionHelper.isRootShellReady(StorageManagerActivity.this);
            Map<String, Long> rootSizes = rootReady ? readCacheSizesViaRoot() : new HashMap<>();
            boolean statsReady = Build.VERSION.SDK_INT >= 26 && RootPermissionHelper.hasUsageAccess(StorageManagerActivity.this);
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps;
            try {
                apps = pm.getInstalledApplications(0);
            } catch (Exception e) {
                apps = new ArrayList<>();
            }
            for (ApplicationInfo app : apps) {
                long cache = -1;
                if (rootSizes.containsKey(app.packageName)) {
                    cache = rootSizes.get(app.packageName);
                } else if (statsReady && Build.VERSION.SDK_INT >= 26) {
                    cache = queryCacheBytes26(app);
                }
                if (cache < 0) continue;
                if (cache == 0 && !rootReady) continue;
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(app));
                } catch (Exception e) {
                    label = app.packageName;
                }
                rows.add(new CacheRow(app.packageName, label, cache));
            }
            rows.sort((a, b) -> Long.compare(b.cacheBytes, a.cacheBytes));
            handler.post(() -> {
                cacheRows.clear();
                cacheRows.addAll(rows);
                cacheAdapter.notifyDataSetChanged();
                long total = 0;
                for (CacheRow r : rows) total += r.cacheBytes;
                if (rows.isEmpty()) {
                    if (Build.VERSION.SDK_INT >= 26 && !RootPermissionHelper.hasUsageAccess(StorageManagerActivity.this) && !rootReady) {
                        cacheStatus.setText("Grant Usage Access or root to read app caches");
                        MaterialButton grant = new MaterialButton(StorageManagerActivity.this);
                        grant.setText("Grant Usage Access");
                        grant.setOnClickListener(v -> RootPermissionHelper.ensureUsageAccess(StorageManagerActivity.this));
                        ((ViewGroup) cacheList.getParent()).addView(grant, ((ViewGroup) cacheList.getParent()).indexOfChild(cacheList));
                    } else {
                        cacheStatus.setText("No caches found");
                    }
                } else {
                    cacheStatus.setText(rows.size() + " apps, " + FileSize.getHumanReadableFileSize(total) + " cache. Tap to select, long-press for app info.");
                }
            });
        }).start();
    }

    private Map<String, Long> readCacheSizesViaRoot() {
        Map<String, Long> out = new HashMap<>();
        try {
            RootManager rm = RootManager.getInstance(this);
            RootManager.ShellResult r = rm.executeFs("du -sb /data/data/*/cache 2>/dev/null", 60);
            if (r.isSuccess() && r.output() != null && !r.output().trim().isEmpty()) {
                parseDuOutput(r.output(), 1L, out);
            } else {
                RootManager.ShellResult k = rm.executeFs("du -sk /data/data/*/cache 2>/dev/null", 60);
                if (k.isSuccess() && k.output() != null) parseDuOutput(k.output(), 1024L, out);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void parseDuOutput(String output, long factor, Map<String, Long> out) {
        for (String line : output.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int tab = t.indexOf('\t');
            if (tab <= 0) tab = t.indexOf(' ');
            if (tab <= 0) continue;
            try {
                long size = Long.parseLong(t.substring(0, tab).trim()) * factor;
                String path = t.substring(tab).trim();
                String[] parts = path.split("/");
                if (parts.length >= 4) out.put(parts[3], size);
            } catch (Exception ignored) {
            }
        }
    }

    private void clearCaches(boolean selectedOnly) {
        List<String> targets = new ArrayList<>();
        for (CacheRow r : cacheRows) {
            if (!selectedOnly || r.checked) targets.add(r.packageName);
        }
        if (targets.isEmpty()) {
            Extensions.showMessage(this, "Nothing selected");
            return;
        }
        if (!RootPermissionHelper.isRootShellReady(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Root needed")
                    .setMessage("One-tap clearing needs root. Without root use Auto-clear below, it taps Clear cache for each app using accessibility, or long-press an app to open its App info manually.")
                    .setPositiveButton("Use auto-clear", (d, w) -> autoClearCaches(selectedOnly))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(selectedOnly ? "Clear selected caches" : "Clear all app caches")
                .setMessage(targets.size() + " apps")
                .setPositiveButton("Clear", (d, w) -> new Thread(() -> {
                    RootManager rm = RootManager.getInstance(StorageManagerActivity.this);
                    int ok = 0;
                    List<String> failures = new ArrayList<>();
                    String firstError = "";
                    for (String pkg : targets) {
                        try {
                            String[] dirs = new String[]{
                                    "/data/data/" + pkg + "/cache",
                                    "/data/data/" + pkg + "/code_cache",
                                    "/data/media/0/Android/data/" + pkg + "/cache"
                            };
                            StringBuilder script = new StringBuilder("for d in");
                            for (String dir : dirs) {
                                script.append(" ").append(RootManager.escapeShellArg(dir));
                            }
                            script.append("; do [ -d \"$d\" ] || continue; ");
                            script.append("for f in \"$d\"/* \"$d\"/.*; do ");
                            script.append("case \"$f\" in \"$d/..\"|\"$d/.\") continue;; esac; ");
                            script.append("[ -e \"$f\" ] || [ -L \"$f\" ] || continue; ");
                            script.append("rm -rf \"$f\" 2>/dev/null || exit 3; ");
                            script.append("done; done; ");
                            script.append("echo ALLDONE");
                            RootManager.ShellResult r = rm.executeFs(script.toString(), 30);
                            boolean clearedOk = r.isSuccess() && r.output() != null && r.output().contains("ALLDONE");
                            if (clearedOk) {
                                ok++;
                            } else {
                                failures.add(pkg);
                                if (firstError.isEmpty()) {
                                    String err = r.error() == null ? "" : r.error().trim();
                                    if (!err.isEmpty()) firstError = err.length() > 300 ? err.substring(0, 300) : err;
                                }
                            }
                        } catch (Exception e) {
                            failures.add(pkg);
                            if (firstError.isEmpty() && e.getMessage() != null) firstError = e.getMessage();
                        }
                    }
                    try {
                        rm.execute("pm trim-caches 9223372036854775807", 90);
                    } catch (Exception ignored) {
                    }
                    if (!failures.isEmpty()) {
                        try {
                            RootManager.ShellResult diag = rm.executeFs("id; ls -ld " + RootManager.escapeShellArg("/data/data/" + failures.get(0) + "/cache") + " 2>&1", 15);
                            String extra = diag.output() == null ? "" : diag.output().trim();
                            String derr = diag.error() == null ? "" : diag.error().trim();
                            if (!derr.isEmpty()) extra = (extra + " " + derr).trim();
                            if (!extra.isEmpty()) firstError = (firstError + "\n" + extra).trim();
                        } catch (Exception ignored) {
                        }
                    }
                    int done = ok;
                    int total = targets.size();
                    List<String> failedPkgs = new ArrayList<>(failures);
                    String errText = firstError;
                    handler.post(() -> {
                        Extensions.showMessage(StorageManagerActivity.this, "Cleared " + done + "/" + total);
                        if (!failedPkgs.isEmpty()) {
                            StringBuilder names = new StringBuilder();
                            int shown = Math.min(failedPkgs.size(), 5);
                            for (int i = 0; i < shown; i++) {
                                if (i > 0) names.append(", ");
                                names.append(failedPkgs.get(i));
                            }
                            new MaterialAlertDialogBuilder(StorageManagerActivity.this)
                                    .setTitle("Some caches not cleared")
                                    .setMessage(failedPkgs.size() + " apps failed" + (names.length() == 0 ? "" : ": " + names.toString()) + (errText.isEmpty() ? "" : "\n\n" + errText))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                        loadCaches();
                        refreshVolumes();
                    });
                }).start())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAppInfo(String pkg) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            Extensions.showMessage(this, "Cannot open");
        }
    }

    protected void onDestroy() {
        scanning = false;
        try {
            CacheCleaner.stopQuiet();
        } catch (Exception ignored) {
        }
        try {
            if (thumbExecutor != null) thumbExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
