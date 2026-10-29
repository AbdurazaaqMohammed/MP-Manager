package io.github.abdurazaaqmohammed.ApkExtractor;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import io.github.abdurazaaqmohammed.data.prefs.ExtractorCache;
import io.github.abdurazaaqmohammed.utils.ApkSignatureInfo;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apkeditor.Util;
import com.reandroid.archive.ArchiveEntry;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;

import org.apache.commons.collections4.Predicate;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.AppRecyclerViewAdapter;
import io.github.abdurazaaqmohammed.adapters.ExtractOptionAdapter;
import io.github.abdurazaaqmohammed.utils.CompareUtils;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;

public class APKExtractorActivity extends BaseActivity {
    private final AppRecyclerViewAdapter[] appAdapter = new AppRecyclerViewAdapter[2];
    public APKLogger logger;
    public boolean ask = false;
    public boolean errorOccurred;
    public String lang;
    public boolean antisplit;
    public String dateFormat;
    public boolean showExtractIcon;
    public boolean showExtractRes;
    public boolean saveWithPkgName;
    public boolean showExtractDex;
    public boolean showExtractManifest;
    public boolean showExtractBase;
    public boolean showExtractSplit;
    public int theme;
    public boolean showExtractLibs;
    public int sortMode;

    public Handler handler;
    public File superSplit;
    public Resources rss;
    public boolean zip = true;
    boolean system = false;
    private boolean signApk;
    private List<AppInfo> userAppInfoList;
    private List<AppInfo> systemAppInfoList;
    private boolean showLaunchActivities;
    private boolean loadingApps;

    public static File getAppFolder() {
        final File appFolder = new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"), "Extracted APKs");
        return appFolder.exists() || appFolder.mkdirs() ? appFolder : new File(Environment.getExternalStorageDirectory(), "Download");
    }

    public static File getAppFolder(Context context) {
        try {
            String base = UiPrefs.appPathDir(context,
                    new File(Environment.getExternalStorageDirectory(), "MP Manager").getAbsolutePath());
            File dir = new File(base, "Extracted APKs");
            if (dir.exists() || dir.mkdirs()) return dir;
        } catch (Exception ignored) {
        }
        return getAppFolder();
    }

    public static String formatDate(long millis, String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("default")) return new Date(millis).toString();
        if (pattern.equals("locale")) return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
        } catch (Exception e) {
            return new Date(millis).toString();
        }
    }

    public static String resolveSigType(File apk) {
        try {
            return ApkSignatureInfo.analyze(apk).getStatusLabel();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public void refreshDateStrings() {
        for (int i = 0; i < 2; i++) {
            if (appAdapter[i] == null) continue;
            for (AppInfo ai : appAdapter[i].appInfoList) {
                ai.firstInstalled = formatDate(ai.firstInstall, dateFormat);
                ai.lastUpdated = formatDate(ai.lastUpdate, dateFormat);
            }
        }
    }

    public void openInFileManager(String path) {
        if (path == null || path.isEmpty()) return;
        setResult(RESULT_OK, new Intent().putExtra("dirToLoad", path));
        finish();
    }

    /** Shared by the toolbar up-arrow and the system back gesture. */
    private void handleBack() {
        onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        handleBack();
        return true;
    }

    @Override
    public void onBackPressed() {
        AppRecyclerViewAdapter adapter = getCurrentAdapter();
        if(adapter.selectedItems.isEmpty()) super.onBackPressed();
        else adapter.clearSelection();
    }

    public static void deleteDir(File dir) {
        Util.deleteDir(dir);
    }

    public Runnable showFinishedDialog(String outputPath) {
        return showFinishedDialog(outputPath, null);
    }

    public Runnable showFinishedDialog(String outputPath, ProgressManager pm) {
        return () -> {
            if(pm != null) pm.dismiss();
            if (!errorOccurred)
                styleAlertDialog(new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.info)
                        .setMessage(rss.getString(R.string.success_saved, outputPath))
                        .setNegativeButton(R.string.locate, (dialog, which) -> {
                            File file = new File(outputPath);
                            this.setResult(RESULT_OK, new Intent().putExtra("dirToLoad", file.isDirectory() ? outputPath : file.getParent()));
                            finish();
                        })
                        .setPositiveButton(android.R.string.ok, null)
                        .create());
            errorOccurred = false;
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        handler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_extractor);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        toolbar.setNavigationOnClickListener(v -> handleBack());

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);


        signApk = settings.getBoolean("signApk", true);
        ask = false;
        saveWithPkgName = settings.getBoolean("saveWithPkgName", false);
        dateFormat = settings.getString("dateFormat", "default");
        showExtractIcon = settings.getBoolean("showExtractIcon", false);
        showExtractBase = settings.getBoolean("showExtractBase", false);
        showExtractDex = settings.getBoolean("showExtractDex", false);
        showExtractLibs = settings.getBoolean("showExtractLibs", false);
        showExtractRes = settings.getBoolean("showExtractRes", false);
        showExtractManifest = settings.getBoolean("showExtractManifest", false);
        showExtractSplit = settings.getBoolean("showExtractSplit", false);
        showLaunchActivities = settings.getBoolean("showLaunchActivities", false);
        antisplit = settings.getBoolean("antisplit", false);
        sortMode = settings.getInt("sortMode", 0);
        lang = settings.getString("lang", "en");
        //if (Objects.equals(lang, Locale.getDefault().getLanguage()))
            rss = getResources();

        userAppInfoList = Collections.synchronizedList(new ArrayList<>());
        systemAppInfoList = Collections.synchronizedList(new ArrayList<>());
        setupAppLists();
        loadApps();
        if (!LegacyUtils.supportsWriteExternalStorage) {
            getWindow().setStatusBarContrastEnforced(true);
            getWindow().setNavigationBarContrastEnforced(true);
        }

        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            ScrollView settingsMenu = (ScrollView) LayoutInflater.from(this).inflate(R.layout.extractor_settings, null);

            CompoundButton signToggle = settingsMenu.findViewById(R.id.signToggle);
            signToggle.setChecked(signApk);
            signToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    styleAlertDialog(new MaterialAlertDialogBuilder(this)
                            .setTitle(rss.getString(R.string.warning))
                            .setMessage(rss.getString(R.string.warn_sign))
                            .setNegativeButton(rss.getString(android.R.string.cancel), (dialog, which) -> {
                                signToggle.setChecked(signApk = false);
                                dialog.dismiss();
                            })
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                signApk = true;
                                dialog.dismiss();
                            })
                            .create());
                }
            });

            SharedPreferences defaultSettings = PreferenceManager.getDefaultSharedPreferences(this);
            CheckBox autosignBox = settingsMenu.findViewById(R.id.autosign);
            autosignBox.setChecked(defaultSettings.getBoolean("autosign", true));
            autosignBox.setOnCheckedChangeListener((buttonView, isChecked) -> defaultSettings.edit().putBoolean("autosign", isChecked).apply());
            settingsMenu.findViewById(R.id.sign_settings).setOnClickListener(v1 -> SignatureKeyDialog.show(this));

            CompoundButton antisplitToggle = settingsMenu.findViewById(R.id.antisplitToggle);
            if (LegacyUtils.aboveSdk20) {
                antisplitToggle.setChecked(antisplit);
                antisplitToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    antisplit = isChecked;
                    signToggle.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                });
            } else {
                antisplit = false;
                antisplitToggle.setVisibility(View.GONE);
            }

            CompoundButton pkgNameToggle = settingsMenu.findViewById(R.id.pkgNameToggle);
            pkgNameToggle.setChecked(saveWithPkgName);
            pkgNameToggle.setOnCheckedChangeListener((buttonView, isChecked) -> saveWithPkgName = isChecked);

            try {
                AutoCompleteTextView dateFormatTv = settingsMenu.findViewById(R.id.dateFormatTv);
                TextView dateFormatPreview = settingsMenu.findViewById(R.id.dateFormatPreview);
                if (dateFormatTv != null) {
                    List<String> presets = new ArrayList<>(Arrays.asList("default", "locale", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm", "MM/dd/yyyy hh:mm a", "yyyy-MM-dd", "dd MMM yyyy HH:mm", "HH:mm dd-MM-yyyy"));
                    if (dateFormat != null && !presets.contains(dateFormat)) presets.add(dateFormat);
                    dateFormatTv.setText(dateFormat == null ? "default" : dateFormat, false);
                    dateFormatTv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, presets));
                    Runnable updatePreview = () -> {
                        if (dateFormatPreview != null) {
                            String cur = dateFormatTv.getText() == null ? "default" : dateFormatTv.getText().toString().trim();
                            if (cur.isEmpty()) cur = "default";
                            dateFormatPreview.setText(formatDate(System.currentTimeMillis(), cur));
                        }
                    };
                    updatePreview.run();
                    dateFormatTv.setOnItemClickListener((p, v2, pos, id) -> {
                        dateFormat = presets.get(pos);
                        updatePreview.run();
                        refreshDateStrings();
                        reloadListView();
                    });
                    dateFormatTv.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) {
                            String cur = s.toString().trim();
                            if (cur.isEmpty()) cur = "default";
                            dateFormat = cur;
                            if (dateFormatPreview != null) dateFormatPreview.setText(formatDate(System.currentTimeMillis(), cur));
                            refreshDateStrings();
                            reloadListView();
                        }
                    });
                }
            } catch (Exception ignored) { }

            CompoundButton showExtractIconToggle = settingsMenu.findViewById(R.id.showExtractIconToggle);
            showExtractIconToggle.setChecked(showExtractIcon);
            showExtractIconToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractIcon = isChecked;
                reloadListView();
            });

            CompoundButton showExtractResToggle = settingsMenu.findViewById(R.id.showExtractResToggle);
            showExtractResToggle.setChecked(showExtractRes);
            showExtractResToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractRes = isChecked;
                reloadListView();
            });

            CompoundButton showExtractDexToggle = settingsMenu.findViewById(R.id.showExtractDexToggle);
            showExtractDexToggle.setChecked(showExtractDex);
            showExtractDexToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractDex = isChecked;
                reloadListView();
            });

            CompoundButton showExtractBaseToggle = settingsMenu.findViewById(R.id.showExtractBaseToggle);
            showExtractBaseToggle.setChecked(showExtractBase);
            showExtractBaseToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractBase = isChecked;
                reloadListView();
            });

            CompoundButton showExtractManifestToggle = settingsMenu.findViewById(R.id.showExtractManifestToggle);
            showExtractManifestToggle.setChecked(showExtractManifest);
            showExtractManifestToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractManifest = isChecked;
                reloadListView();
            });

            CompoundButton showExtractSplitToggle = settingsMenu.findViewById(R.id.showExtractSplitToggle);
            showExtractSplitToggle.setChecked(showExtractSplit);
            showExtractSplitToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractSplit = isChecked;
                reloadListView();
            });

            CompoundButton showExtractLibsToggle = settingsMenu.findViewById(R.id.showExtractLibsToggle);
            showExtractLibsToggle.setChecked(showExtractLibs);
            showExtractLibsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showExtractLibs = isChecked;
                reloadListView();
            });

            CompoundButton showLaunchActivitiesToggle = settingsMenu.findViewById(R.id.showLaunchActivitiesToggle);
            showLaunchActivitiesToggle.setChecked(showLaunchActivities);
            showLaunchActivitiesToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showLaunchActivities = isChecked;
                reloadListView();
            });

            try {
                AutoCompleteTextView compressTv = settingsMenu.findViewById(R.id.compressLevelTv);
                if (compressTv != null) {
                    List<String> levels = new ArrayList<>();
                    for (CompressionLevel cl : CompressionLevel.values()) levels.add(cl.name());
                    SharedPreferences defSettings = PreferenceManager.getDefaultSharedPreferences(this);
                    compressTv.setText(defSettings.getString("compressLevel", CompressionLevel.NORMAL.name()), false);
                    compressTv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, levels));
                    compressTv.setOnItemClickListener((p, v2, pos, id) ->
                            defSettings.edit().putString("compressLevel", levels.get(pos)).apply());
                }
            } catch (Exception ignored) { }

            TextView title = new TextView(this);
            title.setText(rss.getString(R.string.settings));
            title.setTextSize(25);
            styleAlertDialog(
                    new MaterialAlertDialogBuilder(this)
                            .setCustomTitle(title)
                            .setView(settingsMenu)
                            .setPositiveButton(rss.getString(R.string.close), (dialog, which) -> dialog.dismiss())
                            .create()
            );
        });
    }

    private void reloadListView() {
        getCurrentAdapter().notifyDataSetChanged();
    }

    private Comparator<AppInfo> buildComparator() {
        if (sortMode == 0) return CompareUtils::compareAppInfoByName;
        return (p1, p2) -> {
            long field1 = (sortMode == 1) ? p1.lastUpdate : p1.firstInstall;
            long field2 = (sortMode == 1) ? p2.lastUpdate : p2.firstInstall;
            return Long.compare(field2, field1);
        };
    }

    private void paintCachedApps() {
        try {
            ExtractorCache.Snapshot user =
                    ExtractorCache.load(this, false, dateFormat);
            ExtractorCache.Snapshot sys =
                    ExtractorCache.load(this, true, dateFormat);
            boolean painted = false;
            if (user != null && user.sortMode == sortMode && !user.apps.isEmpty()) {
                userAppInfoList.clear();
                userAppInfoList.addAll(user.apps);
                appAdapter[0].setItems(userAppInfoList);
                painted = true;
            }
            if (sys != null && sys.sortMode == sortMode && !sys.apps.isEmpty()) {
                systemAppInfoList.clear();
                systemAppInfoList.addAll(sys.apps);
                appAdapter[1].setItems(systemAppInfoList);
                painted = true;
            }
            if (painted) {
                try {
                    EditText searchBar = findViewById(R.id.search_bar);
                    appAdapter[0].getFilter().filter(searchBar.getText());
                    appAdapter[1].getFilter().filter(searchBar.getText());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        if (userAppInfoList.isEmpty() && systemAppInfoList.isEmpty()) paintCachedApps();
        new Thread(() -> {
            try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> apps = pm.getInstalledPackages(0);

            List<AppInfo> freshUser = Collections.synchronizedList(new ArrayList<>());
            List<AppInfo> freshSystem = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors
                    .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (PackageInfo app : apps) {
                if (app.applicationInfo == null) continue;
                executor.execute(() -> {
                    try {
                        String fmt = dateFormat;
                        AppInfo appInfo = new AppInfo(
                                app.applicationInfo.sourceDir,
                                app.applicationInfo.loadLabel(pm).toString(),
                                null,
                                app.packageName,
                                app.applicationInfo.enabled,
                                LegacyUtils.aboveSdk20 && app.applicationInfo.splitSourceDirs != null,
                                formatDate(app.firstInstallTime, fmt),
                                formatDate(app.lastUpdateTime, fmt),
                                app.versionCode,
                                app.versionName != null ? app.versionName : "");
                        appInfo.firstInstall = app.firstInstallTime;
                        appInfo.lastUpdate = app.lastUpdateTime;
                        appInfo.appInfo = app.applicationInfo;
                        appInfo.targetSdk = app.applicationInfo.targetSdkVersion;
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                appInfo.minSdk = app.applicationInfo.minSdkVersion;
                            else appInfo.minSdk = -1;
                        } catch (Exception ignored2) { appInfo.minSdk = -1; }
                        try {
                            File base = new File(app.applicationInfo.sourceDir);
                            appInfo.baseSize = base.length();
                            appInfo.apkDir = base.getParent();
                            long total = appInfo.baseSize;
                            int splits = 0;
                            String[] splitDirs = app.applicationInfo.splitSourceDirs;
                            if (splitDirs != null) {
                                for (String s : splitDirs) {
                                    try {
                                        total += new File(s).length();
                                        splits++;
                                    } catch (Exception ignored2) {}
                                }
                            }
                            appInfo.totalSize = total;
                            appInfo.splitCount = splits;
                        } catch (Exception ignored2) {}
                        try { appInfo.dataDir = app.applicationInfo.dataDir; } catch (Exception ignored2) {}
                        try {
                            appInfo.extDataDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + app.packageName).getAbsolutePath();
                        } catch (Exception ignored2) {}
                        try {
                            String inst = null;
                            String initiator = null;
                            String origin = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    InstallSourceInfo si = pm.getInstallSourceInfo(app.packageName);
                                    if (si != null) {
                                        inst = si.getInstallingPackageName();
                                        initiator = si.getInitiatingPackageName();
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            try {
                                                int src = si.getPackageSource();
                                                if (src == 1) origin = "Local file";
                                                else if (src == 2) origin = "Downloaded file";
                                                else if (src == 3) origin = "Other";
                                            } catch (Exception ignored3) {}
                                        }
                                    }
                                } catch (Exception ignored2) {}
                            }
                            if (inst == null) inst = pm.getInstallerPackageName(app.packageName);
                            appInfo.installer = inst;
                            appInfo.installInitiator = initiator;
                            appInfo.installOrigin = origin;
                        } catch (Exception ignored2) {}
                        boolean isSystem = (app.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        if (isSystem)
                            freshSystem.add(appInfo);
                        else
                            freshUser.add(appInfo);
                    } catch (Exception ignored) {
                    }
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Comparator<AppInfo> comparator = buildComparator();
            Collections.sort(freshUser, comparator);
            Collections.sort(freshSystem, comparator);
            int mode = sortMode;

            handler.post(() -> {
                findViewById(R.id.progressBar).setVisibility(View.GONE);

                userAppInfoList.clear();
                userAppInfoList.addAll(freshUser);
                systemAppInfoList.clear();
                systemAppInfoList.addAll(freshSystem);

                appAdapter[0].setItems(userAppInfoList);
                appAdapter[1].setItems(systemAppInfoList);

                appAdapter[0].setComparator(comparator);
                appAdapter[1].setComparator(comparator);

                EditText searchBar = findViewById(R.id.search_bar);
                appAdapter[0].getFilter().filter(searchBar.getText());
                appAdapter[1].getFilter().filter(searchBar.getText());

                loadingApps = false;
                ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshUser)).setRefreshing(false);
                ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshSystem)).setRefreshing(false);

                new Thread(() -> {
                    try {
                        ExtractorCache.save(APKExtractorActivity.this, false, mode, freshUser);
                        ExtractorCache.save(APKExtractorActivity.this, true, mode, freshSystem);
                    } catch (Exception ignored) {}
                    try {
                        loadAdditionalDetails(userAppInfoList, appAdapter[0]);
                        loadAdditionalDetails(systemAppInfoList, appAdapter[1]);
                    } catch (Exception e) {
                        new ErrorUtil(APKExtractorActivity.this).showError(e);
                    }
                }).start();
            });
            } catch (Exception e) {
                handler.post(() -> {
                    findViewById(R.id.progressBar).setVisibility(View.GONE);
                    loadingApps = false;
                    ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshUser)).setRefreshing(false);
                    ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshSystem)).setRefreshing(false);
                });
                new ErrorUtil(APKExtractorActivity.this).showError(e);
            }
        }).start();
    }

    private void refreshApps() {
        appAdapter[0].clearSelection();
        appAdapter[1].clearSelection();
        loadApps();
    }

    private void setupAppLists() {
        FastScrollerRecyclerView userAppListView = findViewById(R.id.user_app_list_view);
        FastScrollerRecyclerView systemAppListView = findViewById(R.id.system_app_list_view);

        appAdapter[0] = new AppRecyclerViewAdapter(this, userAppInfoList);
        appAdapter[1] = new AppRecyclerViewAdapter(this, systemAppInfoList);

        userAppListView.setAdapter(appAdapter[0]);
        systemAppListView.setAdapter(appAdapter[1]);

        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshUser)).setOnRefreshListener(this::refreshApps);
        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshSystem)).setOnRefreshListener(this::refreshApps);

        findViewById(R.id.confirmButton).setOnClickListener(v -> {
            AppRecyclerViewAdapter cur = getCurrentAdapter();
            List<String> displayList = new ArrayList<>(Arrays.asList(
                    rss.getString(R.string.extract_apks), rss.getString(R.string.share_apks),
                    getString(R.string.extract_rss), getString(R.string.extract_classes_dex),
                    getString(R.string.extract_am), getString(R.string.extract_base),
                    getString(R.string.extract_libs), getString(R.string.extract_ic)));
            List<Integer> iconList = new ArrayList<>(Arrays.asList(
                    R.drawable.save_24px, R.drawable.baseline_share_24,
                    R.drawable.inventory_2_24px, R.drawable.baseline_folder_zip_24,
                    R.drawable.baseline_text_snippet_24, R.drawable.apk_document_24px,
                    R.drawable.baseline_folder_zip_24, R.drawable.image_24px));
            List<Integer> idList = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
            displayList.add(rss.getString(R.string.uninstall));
            iconList.add(R.drawable.baseline_delete_24);
            idList.add(8);
            if (hasElevatedShell()) {
                displayList.add(rss.getString(R.string.clear_app_data));
                iconList.add(R.drawable.baseline_delete_24);
                idList.add(9);
                displayList.add(rss.getString(R.string.force_stop));
                iconList.add(R.drawable.stop_circle_24px);
                idList.add(10);
                displayList.add(rss.getString(R.string.enable_app));
                iconList.add(R.drawable.baseline_check_circle_24);
                idList.add(11);
                displayList.add(rss.getString(R.string.disable_app));
                iconList.add(R.drawable.visibility_off_24px);
                idList.add(12);
            }

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extract_options, null);
            ListView gridView = dialogView.findViewById(R.id.extractOptionsGrid);
            gridView.setAdapter(new ExtractOptionAdapter(this,
                    displayList.toArray(new String[0]),
                    iconList.stream().mapToInt(Integer::intValue).toArray()));

            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setNegativeButton(rss.getString(android.R.string.cancel), (d, w) -> cur.clearSelection())
                    .create();
            styleAlertDialog(alertDialog);

            gridView.setOnItemClickListener((parent2, view2, which, id2) -> {
                alertDialog.dismiss();
                performAction(idList.get(which), cur);
            });
        });

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    findViewById(R.id.swipeRefreshUser).setVisibility(View.VISIBLE);
                    findViewById(R.id.swipeRefreshSystem).setVisibility(View.GONE);
                    system = false;
                } else {
                    findViewById(R.id.swipeRefreshSystem).setVisibility(View.VISIBLE);
                    findViewById(R.id.swipeRefreshUser).setVisibility(View.GONE);
                    system = true;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        EditText searchBar = findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                getCurrentAdapter().getFilter().filter(s);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.filterButton).setOnClickListener(v -> {
            String[] display = new String[] { getString(R.string.name), getString(R.string.last_updated_date), getString(R.string.first_install_date) };
            AlertDialog ad = new MaterialAlertDialogBuilder(this)
                    .setSingleChoiceItems(display, sortMode, (dialog, which) -> {
                        sortMode = which;
                        Comparator<AppInfo> comparator = buildComparator();

                        Collections.sort(appAdapter[0].appInfoList, comparator);
                        Collections.sort(appAdapter[1].appInfoList, comparator);

                        appAdapter[0].setComparator(comparator);
                        appAdapter[1].setComparator(comparator);

                        appAdapter[0].getFilter().filter(searchBar.getText());
                        appAdapter[1].getFilter().filter(searchBar.getText());

                        dialog.dismiss();
                    }).create();
            styleAlertDialog(ad);
            ad.getListView().setItemChecked(sortMode, true);
        });

    }

    public static final class AppActions {
        public final List<String> displayList = new ArrayList<>();
        public final List<Integer> iconList = new ArrayList<>();
        public final List<Integer> actionIds = new ArrayList<>();
    }

    public void showAppDetails(AppInfo ai, AppRecyclerViewAdapter adapter) {
        AppDetailsDialog.show(this, ai, adapter);
    }

    public AppActions buildAppActions(AppInfo ai) {
        AppActions out = new AppActions();
        List<String> displayList = out.displayList;
        List<Integer> iconList = out.iconList;
        List<Integer> actionIds = out.actionIds;

        displayList.add(getString(R.string.extract_apk));
        iconList.add(ai.isSplit && !antisplit ? R.drawable.archive_24px : R.drawable.save_24px);
        actionIds.add(0);

        displayList.add(getString(R.string.share_apk));
        iconList.add(R.drawable.baseline_share_24);
        actionIds.add(1);

        displayList.add(getString(R.string.open_in_play_store));
        iconList.add(R.drawable.baseline_open_in_new_24);
        actionIds.add(111);

        displayList.add(getString(R.string.launch));
        iconList.add(R.drawable.baseline_open_in_new_24);
        actionIds.add(100);

        displayList.add(getString(R.string.app_info));
        iconList.add(R.drawable.baseline_info_24);
        actionIds.add(101);

        displayList.add(getString(R.string.uninstall));
        iconList.add(R.drawable.baseline_delete_24);
        actionIds.add(102);

        if (showExtractRes) {
            displayList.add(getString(R.string.extract_resources));
            iconList.add(R.drawable.inventory_2_24px);
            actionIds.add(2);
        }
        if (showExtractDex) {
            displayList.add(getString(R.string.extract_dex));
            iconList.add(R.drawable.baseline_folder_zip_24);
            actionIds.add(3);
        }
        if (showExtractManifest) {
            displayList.add(getString(R.string.extract_manifest));
            iconList.add(R.drawable.baseline_text_snippet_24);
            actionIds.add(4);
        }
        if (showExtractBase) {
            displayList.add(getString(R.string.extract_base_apk));
            iconList.add(R.drawable.apk_document_24px);
            actionIds.add(5);
        }
        if (showExtractLibs) {
            displayList.add(getString(R.string.extract_libs));
            iconList.add(R.drawable.baseline_folder_zip_24);
            actionIds.add(6);
        }
        if (showExtractIcon) {
            displayList.add(getString(R.string.extract_icon));
            iconList.add(R.drawable.image_24px);
            actionIds.add(7);
        }

        if(showLaunchActivities) {
            displayList.add(getString(R.string.launch_activity));
            iconList.add(R.drawable.baseline_open_in_new_24);
            actionIds.add(107);
        }
        if (ai.isSplit) {
            if (showExtractSplit) {
                displayList.add(getString(R.string.choose_split_apk));
                iconList.add(R.drawable.baseline_arrow_drop_down_24);
                actionIds.add(103);
            }
            if(antisplit) {
                displayList.add(getString(R.string.save_split_apks));
                iconList.add(R.drawable.archive_24px);
                actionIds.add(106);
            } else {
                displayList.add(getString(R.string.antisplit_merge_and_save));
                iconList.add(R.drawable.baseline_compress_24);
                actionIds.add(104);
            }

            displayList.add(getString(R.string.antisplit_merge_and_share));
            iconList.add(R.drawable.baseline_share_24);
            actionIds.add(105);
        }

        if (ai.apkDir != null && !ai.apkDir.isEmpty()) {
            displayList.add(getString(R.string.open_apk_dir));
            iconList.add(R.drawable.folder_24px);
            actionIds.add(108);
        }
        if (ai.dataDir != null && !ai.dataDir.isEmpty()) {
            displayList.add(getString(R.string.open_data_dir));
            iconList.add(R.drawable.folder_24px);
            actionIds.add(109);
        }
        if (ai.extDataDir != null && !ai.extDataDir.isEmpty()) {
            displayList.add(getString(R.string.open_ext_data_dir));
            iconList.add(R.drawable.folder_24px);
            actionIds.add(110);
        }

        RootManager rootManager = RootManager.getInstance(this);
        if (rootManager.isRootExtractorEnabled() && rootManager.isRootAvailable()) {
            displayList.add(getString(R.string.clear_app_data));
            iconList.add(R.drawable.baseline_delete_24);
            actionIds.add(200);

            displayList.add(getString(R.string.force_stop));
            iconList.add(R.drawable.stop_circle_24px);
            actionIds.add(201);

            displayList.add(getString(R.string.enable_app));
            iconList.add(R.drawable.baseline_check_circle_24);
            actionIds.add(202);

            displayList.add(getString(R.string.disable_app));
            iconList.add(R.drawable.visibility_off_24px);
            actionIds.add(203);

            displayList.add(getString(R.string.uninstall_roots));
            iconList.add(R.drawable.ic_delete);
            actionIds.add(204);
        }

        return out;
    }

    public void handleAppAction(int actionId, AppInfo ai, AppRecyclerViewAdapter adapter) {
        int groupPosition = adapter == null ? -1 : adapter.filteredAppInfoList.indexOf(ai);
        if (actionId == 0 || actionId == 1 || (actionId >= 104 && actionId <= 106)) {
            if (groupPosition < 0) return;
        }

        if (actionId == 0) extract(groupPosition, antisplit, true);
        else if (actionId == 1) share(groupPosition, antisplit);
        else if (actionId < 100) {
            if (groupPosition < 0) return;
            adapter.selectedItems.clear();
            adapter.selectedItems.add(ai.packageName);
            performAction(actionId, adapter);
            adapter.clearSelection();
        } else {
            String packageName = ai.packageName;
            RootManager rootManager = RootManager.getInstance(this);
            switch (actionId) {
                    case 100: // Launch
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                        if (launchIntent == null)
                            Extensions.showMessage(this, getString(R.string.cannot_launch_app));
                        else startActivity(launchIntent);
                        break;
                    case 111: // Open in Play Store
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                            } catch (Exception ignored) {}
                        }
                        break;
                    case 101: // App Info
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + packageName)));
                        break;
                    case 102: // Uninstall
                        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:" + packageName));
                        try {
                            startActivity(uninstallIntent);
                        } catch (Exception e) {
                            startActivity(uninstallIntent.setAction(Intent.ACTION_DELETE));
                        }
                        break;
                    case 103: // Choose split APK
                        File[] splits = new File(ai.filePath).getParentFile().listFiles();
                        ArrayList<File> splitting = new ArrayList<>();
                        ArrayList<String> splitties = new ArrayList<>();
                        for (File f : splits) {
                            String curr = f.getName();
                            if (curr.endsWith(".apk")) {
                                splitting.add(f);
                                splitties.add(curr);
                            }
                        }
                        CharSequence[] displayArr = new CharSequence[splitties.size()];
                        styleAlertDialog(new MaterialAlertDialogBuilder(this)
                                .setSingleChoiceItems(splitties.toArray(displayArr), -1, (dialog, which) -> {
                                    if (ask) {
                                        File f = superSplit = splitting.get(which);
                                        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .setType("application/vnd.android.package-archive")
                                                .putExtra(Intent.EXTRA_TITLE, f.getName()), 5010);
                                    }
                                }).create());
                        break;
                    case 104: // Antisplit merge and save
                        extract(groupPosition, true, true);
                        break;
                    case 105: // Antisplit merge and share
                        share(groupPosition, true);
                        break;
                    case 106: // Antisplit =true save split apk as apks
                        extract(groupPosition, false, true);
                        break;
                    case 108:
                        openInFileManager(ai.apkDir);
                        break;
                    case 109:
                        openInFileManager(ai.dataDir);
                        break;
                    case 110:
                        openInFileManager(ai.extDataDir);
                        break;
                    case 107: // Launch activity
                        PackageManager pm = getPackageManager();
                        try {
                            ActivityInfo[] activities = pm.getPackageInfo(ai.packageName, PackageManager.GET_ACTIVITIES).activities;
                            if(activities == null) Extensions.showMessage(this, getString(R.string.no_launch_activities));

                            else {
                                String[] labels = new String[activities.length];
                                for (int i = 0, activitiesLength = activities.length; i < activitiesLength; i++) labels[i] = activities[i].name;
                                /*List<ResEntry> resEntries;
                                try (ZipFile zf = new ZipFile(ai.filePath); InputStream is = zf.getInputStream(zf.getFileHeader("resources.arsc"))) {
                                    resEntries = new ResourceTableParser(is).parse();
                                }

                                for (ResEntry resEntry : resEntries) {
                                    for (int i = 0, activitiesLength = activities.length; i < activitiesLength; i++) {
                                        ActivityInfo activityInfo = activities[i];
                                        if(resEntry.getResourceId() == activityInfo.labelRes) labels[i] = new StringBuilder(resEntry.getValue()).append(' ').append('(').append(activityInfo.name).append(')');
                                    }
                                }*/ //why this not working
                                List<String> labelsList = Arrays.asList(labels);

                                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

                                View dialogView = getLayoutInflater().inflate(R.layout.dialog_label_search, null);
                                EditText searchEt = dialogView.findViewById(R.id.searchEt);

                                builder.setView(dialogView);

                                AlertDialog dialog = builder.create();

                                ArrayList<String> filtered = new ArrayList<>(labelsList);
                                ArrayAdapter<String> finalAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, filtered);

                                ListView lv = dialogView.findViewById(R.id.listView);
                                lv.setAdapter(finalAdapter);

                                lv.setOnItemClickListener((parent1, view1, position1, id1) -> {
                                    dialog.dismiss();
                                    String selected = filtered.get(position1);
                                    Intent intent = new Intent().setComponent(new ComponentName(packageName, selected)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                });

                                searchEt.addTextChangedListener(new TextWatcher() {
                                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                                    @Override public void afterTextChanged(Editable s) {
                                        String q = s.toString().toLowerCase().trim();
                                        filtered.clear();
                                        if (q.isEmpty()) filtered.addAll(labelsList);
                                        else for (String label : labelsList) if (label.toLowerCase().contains(q)) filtered.add(label);
                                        finalAdapter.notifyDataSetChanged();
                                    }
                                });

                                dialog.show();
                            }
                        } catch (Exception e) { new ErrorUtil(APKExtractorActivity.this).showError(e); }
                        break;
                    case 200: // Clear app data (root)
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.clear_app_data)
                                .setMessage(getString(R.string.clear_all_data_for, ai.name))
                                .setPositiveButton(R.string.clear_app_data, (d, w) -> executeRootAction("Clear data", () -> rootManager.clearAppData(packageName), packageName))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        break;
                    case 201: // Force stop (root)
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.force_stop)
                                .setMessage(getString(R.string.force_stop_x, ai.name))
                                .setPositiveButton(R.string.force_stop, (d, w) -> executeRootAction("Force stop", () -> rootManager.forceStopApp(packageName), packageName))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        break;
                    case 202: // Enable app (root)
                        executeRootAction("Enable app", () -> rootManager.enableApp(packageName), packageName);
                        break;
                    case 203: // Disable app (root)
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.disable_app)
                                .setMessage(getString(R.string.disable_x, ai.name))
                                .setPositiveButton(R.string.disable, (d, w) -> executeRootAction("Disable app", () -> rootManager.disableApp(packageName), packageName))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        break;
                    case 204: // uninstall (root)
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.root_uninstall)
                                .setMessage(rss.getString(R.string.uninstall_root, ai.name))
                                .setPositiveButton(rss.getString(R.string.uninstall), (d, w) -> executeRootAction("Uninstall", () -> rootManager.uninstallSilent(packageName), packageName))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        break;
            }
        }
    }

    private void executeRootAction(String actionName, RootRunnable action, String packageName) {
        RootManager rootManager = RootManager.getInstance(this);
        if (!rootManager.isRootMode() || !rootManager.isRootAvailable()) {
            Extensions.showMessage(this, getString(R.string.root_not_available));
            return;
        }
        new Thread(() -> {
            try {
                action.run();
                //runOnUiThread(() -> Extensions.showMessage(this, actionName + " done"));
            } catch (Exception e) {
                new ErrorUtil(this).showError(e);
                //runOnUiThread(() -> Extensions.showMessage(this, actionName + " failed: " + e.getMessage()));
            }
        }).start();
    }

    interface RootRunnable {
        void run() throws Exception;
    }

    interface ElevatedOp {
        void run(String packageName) throws Exception;
    }

    private boolean hasElevatedShell() {
        try {
            RootManager rm = RootManager.getInstance(this);
            if (rm.isRootMode() && rm.isRootAvailable()) return true;
            return rm.isShizukuMode() && ShizukuManager.ready();
        } catch (Exception e) {
            return false;
        }
    }

    private void elevatedExec(String baseCmd, String packageName, boolean guardCritical) throws Exception {
        if (!RootManager.isPackageNameValid(packageName)) throw new IOException("Invalid package name: " + packageName);
        if (guardCritical && RootManager.isCriticalPackage(packageName)) throw new IOException("Blocked: refusing to modify critical system app: " + packageName);
        String cmd = baseCmd + " " + RootManager.escapeShellArg(packageName);
        RootManager rm = RootManager.getInstance(this);
        if (rm.isRootMode() && rm.isRootAvailable()) {
            RootManager.ShellResult r = rm.execute(cmd, 30);
            if (!r.isSuccess()) throw new IOException(r.error() != null && !r.error().isEmpty() ? r.error() : r.output());
        } else {
            ShizukuShell.Result r =
                    ShizukuShell.exec(cmd, 30);
            if (!r.success) throw new IOException(r.stderr != null && !r.stderr.isEmpty() ? r.stderr : "failed");
        }
    }

    private void elevatedUninstall(String packageName) throws Exception {
        if (!RootManager.isPackageNameValid(packageName)) throw new IOException("Invalid package name: " + packageName);
        if (RootManager.isCriticalPackage(packageName)) throw new IOException("Blocked: refusing to uninstall critical system app: " + packageName);
        RootManager rm = RootManager.getInstance(this);
        if (rm.isRootMode() && rm.isRootAvailable()) {
            rm.uninstallSilent(packageName);
            return;
        }
        ShizukuShell.Result r =
                ShizukuShell.exec("pm uninstall " + RootManager.escapeShellArg(packageName), 30);
        if (!r.success || r.stdout == null || !r.stdout.contains("Success")) throw new IOException(r.stderr != null && !r.stderr.isEmpty() ? r.stderr : r.stdout);
    }

    private String batchNames(List<AppInfo> targets) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(targets.size(), 10);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append("• ").append(targets.get(i).name).append(" (").append(targets.get(i).packageName).append(')');
        }
        if (targets.size() > n) sb.append('\n').append(getString(R.string.and_n_more, targets.size() - n));
        return sb.toString();
    }

    private List<AppInfo> batchTargets(AppRecyclerViewAdapter adapter, List<Integer> positions) {
        List<AppInfo> out = new ArrayList<>(positions.size());
        for (int pos : positions) {
            try {
                out.add(adapter.filteredAppInfoList.get(pos));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private void confirmBatchUninstall(List<AppInfo> targets) {
        if (targets.isEmpty()) return;
        boolean elevated = hasElevatedShell();
        String msg = (elevated ? getString(R.string.uninstall_batch_elevated, targets.size())
                : getString(R.string.uninstall_batch_normal, targets.size())) + "\n\n" + batchNames(targets);
        styleAlertDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.uninstall)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.uninstall, (d, w) -> {
                    if (elevated) runElevatedBatch(getString(R.string.uninstall), targets, this::elevatedUninstall);
                    else {
                        for (AppInfo ai : targets) {
                            try {
                                Intent u = new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:" + ai.packageName));
                                try {
                                    startActivity(u);
                                } catch (Exception e) {
                                    startActivity(u.setAction(Intent.ACTION_DELETE));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                })
                .create());
    }

    private void confirmElevatedBatch(int titleId, int msgId, List<AppInfo> targets, ElevatedOp op) {
        if (targets.isEmpty()) return;
        styleAlertDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(titleId)
                .setMessage(getString(msgId, targets.size()) + "\n\n" + batchNames(targets))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(titleId, (d, w) -> runElevatedBatch(getString(titleId), targets, op))
                .create());
    }

    private void runElevatedBatch(String progressTitle, List<AppInfo> targets, ElevatedOp op) {
        ProgressManager bpm = new ProgressManager(this, true);
        bpm.setText(progressTitle);
        bpm.show();
        new Thread(() -> {
            List<String> failed = new ArrayList<>();
            for (AppInfo ai : targets) {
                try {
                    bpm.setText(progressTitle + ": " + ai.name);
                    op.run(ai.packageName);
                } catch (Exception e) {
                    failed.add(ai.name);
                }
            }
            bpm.dismiss();
            int total = targets.size();
            handler.post(() -> {
                if (failed.isEmpty()) Extensions.showMessage(this, getString(R.string.batch_done_x, total));
                else Extensions.showMessage(this, getString(R.string.batch_failed_x, failed.size(), TextUtils.join(", ", failed)));
                refreshApps();
            });
        }).start();
    }

    private void performAction(int whichAction, AppRecyclerViewAdapter adapter) {
        File appFolder = getAppFolder(this);
        final List<Integer> itemsToProcess = adapter.selectedPositions();

        String path = appFolder.getPath();
        boolean singleItem = itemsToProcess.size() == 1;
        ProgressManager pm = new ProgressManager(this, true).show();
        switch (whichAction) {
            case 0:
                for (int i = 0, itemsToProcessSize = itemsToProcess.size(); i < itemsToProcessSize; i++) {
                    int integer = itemsToProcess.get(i);
                    extract(integer, antisplit, i == itemsToProcessSize-1);
                }
                break;
            case 1:
                share(antisplit, itemsToProcess);
                break;
            case 2:
                new Thread(() -> {
                    String thing = "resources.arsc";
                    for (int i = 0, itemsToProcessSize = itemsToProcess.size(); i < itemsToProcessSize; i++) {
                        int integer = itemsToProcess.get(i);
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        String packageName = ai.packageName;
                        String nameToSaveFile = saveWithPkgName ? packageName : ai.name;
                        try (ArchiveFile zf = new ArchiveFile(new File(ai.filePath))) {
                            InputSource inputSource = zf.getEntrySource(thing);
                            String destName = nameToSaveFile + " v" + ai.versionName + ' ' + thing;
                            pm.setText(rss.getString(R.string.extracting, destName));
                            inputSource.write(FileUtils.getUnusedFile(appFolder, destName));
                            if (i == itemsToProcessSize-1) handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, pm));
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                }).start();
                break;

            case 3:
                new Thread(() -> {
                    final String classes = "classes", dex = ".dex";
                    ZipParameters zp = MergeUtil.newPreferredZipParameters(APKExtractorActivity.this);
                    FileHeader fh;
                    for (int j = 0, itemsToProcessSize = itemsToProcess.size(); j < itemsToProcessSize; j++) {
                        AppInfo ai = adapter.filteredAppInfoList.get(itemsToProcess.get(j));
                        String destName = (saveWithPkgName ? ai.packageName : ai.name) + " v" + ai.versionName + ' ' + classes + ".zip";
                        try (ZipFile izf = new ZipFile(new File(ai.filePath));
                             ZipFile ozf = new ZipFile(FileUtils.getUnusedFile(appFolder, destName))) {
                            for (int i = 1; ; i++) {
                                String entryName = (i == 1) ? classes + dex : classes + i + dex;
                                pm.setText(rss.getString(R.string.extracting_to, entryName, destName));
                                if ((fh = izf.getFileHeader(entryName)) == null) break;
                                else try(InputStream is = izf.getInputStream(fh)) {
                                    zp.setFileNameInZip(entryName);
                                    pm.setText(rss.getString(R.string.adding, entryName));
                                    ozf.addStream(is, zp);
                                }
                            }
                            if (j == itemsToProcessSize-1) handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, pm));
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                }).start();
                break;

            case 4:
                new Thread(() -> {
                    String am = "AndroidManifest.xml";
                    int itemsToProcessSize = itemsToProcess.size();
                    for (int i = 0; i < itemsToProcessSize; i++) {
                        int integer = itemsToProcess.get(i);
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        String packageName = ai.packageName;
                        try (ArchiveFile zf = new ArchiveFile(new File(ai.filePath))) {
                            String destName = singleItem ? am : (saveWithPkgName ? packageName : ai.name) + " v" + ai.versionName + ' ' + am;
                            pm.setText(rss.getString(R.string.extracting, destName));
                            zf.getEntrySource(am).write(FileUtils.getUnusedFile(appFolder, destName));
                            if (i == itemsToProcessSize-1) {
                                handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, pm));
                            }
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                }).start();
                break;

            case 5:
                new Thread(() -> {
                    for (int integer : itemsToProcess) {
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        try {
                            final String pkgName = ai.packageName;
                            String name = (saveWithPkgName ? pkgName : ai.name) + " v" + ai.versionName + "_base.apk";
                            pm.setText(rss.getString(R.string.extracting, name));
                            FileUtils.copyFile(new File(ai.filePath), FileUtils.getUnusedFile(appFolder, name));
                        } catch (Exception e) {
                            pm.dismiss();
                            errorOccurred = true;
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                    handler.post(showFinishedDialog(path, pm));
                }).start();
                break;

            case 6:
                new Thread(() -> {
                    APKLogger logger = pm.getLogger();
                    for (int integer : itemsToProcess) {
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        try {
                            File sourceDir = new File(ai.filePath);
                            File[] files = sourceDir.getParentFile().listFiles();
                            if (files == null) continue;
                            File outputDir = FileUtils.getUnusedFile(appFolder, (saveWithPkgName ? ai.packageName : ai.name) + " libs");
                            for (File f : files) {
                                String name = f.getName();
                                if (name.endsWith(".apk")) {
                                    try (ArchiveFile zf = new ArchiveFile(f)) {
                                        zf.extractAll(outputDir, (Predicate<ArchiveEntry>) archiveEntry -> archiveEntry.getName().startsWith("lib/"), logger);
                                    }
                                }
                            }
                            logger.close();
                        } catch (Exception e) {
                            pm.dismiss();
                            logger.close();
                            errorOccurred = true;
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                    handler.post(showFinishedDialog(path, pm));
                }).start();
                break;

            case 7:
                new Thread(() -> {
                    PackageManager pmgr = getPackageManager();
                    for (int integer : itemsToProcess) {
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        String name = (saveWithPkgName ? ai.packageName : ai.name) + " v" + ai.versionName + "_icon.png";
                        pm.setText(rss.getString(R.string.extracting, name));
                        try (OutputStream os = FileUtils.getOutputStream(FileUtils.getUnusedFile(appFolder, name))) {
                            PackageInfo packageInfo = pmgr.getPackageInfo(ai.packageName, 0);

                            Bitmap bm = drawableToBitmap(packageInfo.applicationInfo.loadIcon(pmgr));

                            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        } catch (Exception e) {
                            pm.dismiss();
                            errorOccurred = true;
                            new ErrorUtil(APKExtractorActivity.this).showError(e);
                        }
                    }
                    handler.post(showFinishedDialog(path, pm));
                }).start();
                break;
            case 8:
                pm.dismiss();
                confirmBatchUninstall(batchTargets(adapter, itemsToProcess));
                break;
            case 9:
                pm.dismiss();
                confirmElevatedBatch(R.string.clear_app_data, R.string.batch_clear_data,
                        batchTargets(adapter, itemsToProcess),
                        pkg -> elevatedExec("pm clear", pkg, true));
                break;
            case 10:
                pm.dismiss();
                confirmElevatedBatch(R.string.force_stop, R.string.batch_force_stop,
                        batchTargets(adapter, itemsToProcess),
                        pkg -> elevatedExec("am force-stop", pkg, true));
                break;
            case 11:
                pm.dismiss();
                confirmElevatedBatch(R.string.enable_app, R.string.batch_enable,
                        batchTargets(adapter, itemsToProcess),
                        pkg -> elevatedExec("pm enable", pkg, false));
                break;
            case 12:
                pm.dismiss();
                confirmElevatedBatch(R.string.disable_app, R.string.batch_disable,
                        batchTargets(adapter, itemsToProcess),
                        pkg -> elevatedExec("pm disable-user --user 0", pkg, true));
                break;
        }
    }

    public Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable)
            return ((BitmapDrawable) drawable).getBitmap();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    final ExtractorIconCache iconCache = new ExtractorIconCache();

    private void loadAdditionalDetails(List<AppInfo> apps, AppRecyclerViewAdapter adapter) {
        PackageManager pm = getPackageManager();
        for (int i = 0; i < apps.size(); i++) {
            AppInfo app = apps.get(i);
            try {
                if (app.icon == null && app.packageName != null) {
                    Drawable cached = iconCache.getMemory(app.packageName, app.getVersionCode());
                    if (cached == null) cached = iconCache.getDisk(this, app.packageName, app.getVersionCode());
                    if (cached != null) {
                        app.icon = cached;
                    } else {
                        ApplicationInfo info = app.appInfo;
                        if (info == null) {
                            try {
                                info = pm.getApplicationInfo(app.packageName, 0);
                                app.appInfo = info;
                            } catch (Exception ignored) {}
                        }
                        if (info != null) {
                            Drawable loaded = info.loadIcon(pm);
                            app.icon = loaded;
                            iconCache.put(this, app.packageName, app.getVersionCode(), loaded);
                        }
                    }
                }
            } catch (Exception ignored) {}
            final int pos = adapter.filteredAppInfoList.indexOf(app);
            if (pos >= 0) handler.post(() -> adapter.notifyItemChanged(pos));
        }
    }

    public void extract(int pos, boolean antisplit, boolean showFinishedDialog) {
        AppInfo ai = getCurrentAdapter().filteredAppInfoList.get(pos);
        final String pkgName = ai.packageName;
        StringBuilder fileName = new StringBuilder(saveWithPkgName ? pkgName : ai.name).append(' ').append('v').append(ai.versionName);
        fileName.append(".apk");
        if (LegacyUtils.aboveSdk20 && ai.isSplit && !antisplit) fileName.append('s');
        String fileNameString = fileName.toString();
        File output = new File(getAppFolder(this), fileNameString);

        ProgressManager pm = new ProgressManager(this, true).show();

        new Thread(() -> {
            try {
                File baseApk = new File(ai.filePath);
                File apkDirectory = baseApk.getParentFile();
                boolean split = LegacyUtils.aboveSdk20 && ai.isSplit;
                File finalOutput;
                if (split && antisplit) try (ApkBundle bundle = new ApkBundle()) {
                    bundle.loadApkDirectory(apkDirectory, false);
                    MergeUtil.applyPreferredCompression(bundle, APKExtractorActivity.this);
                    APKLogger logger = pm.getLogger();
                    try {
                        bundle.setAPKLogger(logger);
                        finalOutput = FileUtils.getUnusedFile(output);
                        MergeUtil.mergeBundle(bundle, APKExtractorActivity.this).renameTo(finalOutput);
                    } finally {
                        logger.close();
                    }
                }
                else {
                    finalOutput = FileUtils.getUnusedFile(output);
                    if (split) try (ZipFile zf = new ZipFile(finalOutput)) {
                        ZipParameters zp = MergeUtil.newPreferredZipParameters(APKExtractorActivity.this);
                        File[] apkFiles = apkDirectory.listFiles();
                        if (apkFiles == null) throw new IOException("Cannot list " + apkDirectory);
                        for (File f : apkFiles) {
                            String name = f.getName();
                            if (f.isFile() && name.endsWith(".apk")) {
                                pm.setText(rss.getString(R.string.adding_to, name, fileNameString));
                                zf.addFile(f, zp);
                            }
                        }
                    }
                    else {
                        pm.setText(rss.getString(R.string.extracting, fileNameString));
                        try (OutputStream os = FileUtils.getOutputStream(finalOutput)) {
                            FileUtils.copyFile(baseApk, os);
                        }
                    }
                }
                pm.dismiss();
                if (split && antisplit && signApk && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autosign", true)) {
                    File toSign = finalOutput;
                    handler.post(() -> SignWrapper.requireAuth(this, sw -> {
                        ProgressManager signPm = new ProgressManager(this, true);
                        signPm.setText(getString(R.string.signing, toSign.getName()));
                        signPm.show();
                        new Thread(() -> {
                            try {
                                sw.signApk(toSign);
                                signPm.dismiss();
                                if (showFinishedDialog) handler.post(showFinishedDialog(toSign.getPath(), null));
                            } catch (Exception e) {
                                signPm.dismiss();
                                new ErrorUtil(this).showError(e);
                            }
                        }).start();
                    }));
                } else if (showFinishedDialog) handler.post(showFinishedDialog(finalOutput.getPath(), null));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(APKExtractorActivity.this).showError(e);
            }
        }).start();
    }

    private void share(boolean antisplit, List<Integer> itemsToProcess) {
        ProgressManager pm = new ProgressManager(this, true).show();
        ArrayList<Uri> fileUris = new ArrayList<>();
        ArrayList<File> mergedFiles = new ArrayList<>();

        new Thread(() -> {
            String authority = "io.github.abdurazaaqmohammed.MPManager.provider";
            for(int i : itemsToProcess) try {
                AppInfo ai = getCurrentAdapter().filteredAppInfoList.get(i);
                boolean split = ai.isSplit;

                if (split) {
                    if (antisplit) {
                        try (ApkBundle bundle = new ApkBundle()) {
                            bundle.loadApkDirectory(new File(ai.filePath).getParentFile());
                            MergeUtil.applyPreferredCompression(bundle, APKExtractorActivity.this);
                            APKLogger logger = pm.getLogger();
                            try {
                                bundle.setAPKLogger(logger);
                                File merged = MergeUtil.mergeBundle(bundle, APKExtractorActivity.this);
                                fileUris.add(FileProvider.getUriForFile(this, authority, merged));
                                mergedFiles.add(merged);
                            } finally {
                                logger.close();
                            }
                        }
                    } else {
                        File[] files = new File(ai.filePath).getParentFile().listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && f.getName().endsWith(".apk")) {
                                    fileUris.add(FileProvider.getUriForFile(this, authority, f));
                                }
                            }
                        }
                    }
                } else {
                    fileUris.add(FileProvider.getUriForFile(this, authority, new File(ai.filePath)));
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(APKExtractorActivity.this).showError(e);
            }
            pm.dismiss();

            if (fileUris.isEmpty()) return;
            if (!mergedFiles.isEmpty() && signApk && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autosign", true)) {
                handler.post(() -> SignWrapper.requireAuth(this, sw -> {
                    ProgressManager signPm = new ProgressManager(this, true);
                    signPm.show();
                    new Thread(() -> {
                        try {
                            for (File f : mergedFiles) {
                                signPm.setText(getString(R.string.signing, f.getName()));
                                sw.signApk(f);
                            }
                            signPm.dismiss();
                            handler.post(() -> fireMultipleShareIntent(fileUris));
                        } catch (Exception e) {
                            signPm.dismiss();
                            new ErrorUtil(this).showError(e);
                        }
                    }).start();
                }));
            } else fireMultipleShareIntent(fileUris);

        }).start();
    }

    private void fireMultipleShareIntent(ArrayList<Uri> fileUris) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("application/vnd.android.package-archive")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, rss.getString(R.string.share_apk)));
    }

    public void share(int pos, boolean antisplit) {
        ProgressManager pm = new ProgressManager(this, true).show();

        new Thread(() -> {
            try {
                AppInfo ai = getCurrentAdapter().filteredAppInfoList.get(pos);
                boolean split = ai.isSplit;
                ArrayList<Uri> fileUris = new ArrayList<>();
                boolean isMultiple = false;
                final File[] toShare = new File[1];

                if (split) {
                    if (antisplit) {
                        try (ApkBundle bundle = new ApkBundle()) {
                            bundle.loadApkDirectory(new File(ai.filePath).getParentFile());
                            MergeUtil.applyPreferredCompression(bundle, APKExtractorActivity.this);
                            APKLogger logger = pm.getLogger();
                            try {
                                bundle.setAPKLogger(logger);
                                toShare[0] = MergeUtil.mergeBundle(bundle, APKExtractorActivity.this);
                            } finally {
                                logger.close();
                            }
                        }
                    } else {
                        File[] files = new File(ai.filePath).getParentFile().listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && f.getName().endsWith(".apk")) {
                                    fileUris.add(FileProvider.getUriForFile(this, "io.github.abdurazaaqmohammed.MPManager.provider", f));
                                }
                            }
                        }
                        if (fileUris.size() > 1) {
                            isMultiple = true;
                        } else {
                            toShare[0] = new File(ai.filePath);
                        }
                    }
                } else {
                    toShare[0] = new File(ai.filePath);
                }

                pm.dismiss();

                final boolean shareMultiple = isMultiple;
                boolean merged = split && antisplit && toShare[0] != null;
                if (merged && signApk && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autosign", true)) {
                    File toSign = toShare[0];
                    handler.post(() -> SignWrapper.requireAuth(this, sw -> {
                        ProgressManager signPm = new ProgressManager(this, true);
                        signPm.setText(getString(R.string.signing, toSign.getName()));
                        signPm.show();
                        new Thread(() -> {
                            try {
                                sw.signApk(toSign);
                                signPm.dismiss();
                                handler.post(() -> fireSingleShareIntent(fileUris, shareMultiple, toSign));
                            } catch (Exception e) {
                                signPm.dismiss();
                                new ErrorUtil(this).showError(e);
                            }
                        }).start();
                    }));
                } else fireSingleShareIntent(fileUris, shareMultiple, toShare[0]);
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(APKExtractorActivity.this).showError(e);
            }
        }).start();
    }

    private void fireSingleShareIntent(ArrayList<Uri> fileUris, boolean isMultiple, File single) {
        Intent intent;
        if (isMultiple) {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("application/vnd.android.package-archive")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
        } else {
            Uri u = FileProvider.getUriForFile(this, "io.github.abdurazaaqmohammed.MPManager.provider", single);
            intent = new Intent(Intent.ACTION_SEND)
                    .setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_STREAM, u);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, rss.getString(R.string.share_apk)));
    }

    public void styleAlertDialog(AlertDialog ad) {
        runOnUiThread(ad::show);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh in case apps were installed/uninstalled while away.
        try {
            if (!loadingApps) refreshApps();
            else reloadListView();
        } catch (Exception ignored) { }
    }


    @Override
    protected void onPause() {
        SharedPreferences.Editor e = getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putBoolean("ask", ask)
                .putBoolean("signApk", signApk)
                .putBoolean("saveWithPkgName", saveWithPkgName)
                .putBoolean("showExtractIcon", showExtractIcon)
                .putBoolean("showExtractRes", showExtractRes)
                .putBoolean("showExtractBase", showExtractBase)
                .putBoolean("showExtractManifest", showExtractManifest)
                .putBoolean("showExtractDex", showExtractDex)
                .putBoolean("showExtractSplit", showExtractSplit)
                .putBoolean("showExtractLibs", showExtractLibs)
                .putBoolean("showLaunchActivities", showLaunchActivities)
                .putString("dateFormat", dateFormat == null ? "default" : dateFormat)
                .putBoolean("antisplit", antisplit)
                .putInt("sortMode", sortMode)
                .putString("lang", lang);
        e.apply();
        super.onPause();
    }

    private AppRecyclerViewAdapter getCurrentAdapter() {
        return appAdapter[system ? 1 : 0];
    }
}