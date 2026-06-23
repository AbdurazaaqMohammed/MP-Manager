package io.github.abdurazaaqmohammed.ApkExtractor;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import android.provider.Settings;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apkeditor.Util;
import com.reandroid.archive.ArchiveEntry;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.widget.PopupWindow;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;

import org.apache.commons.collections4.Predicate;

import io.github.abdurazaaqmohammed.adapters.DropdownAdapter;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.AppExpandableListAdapter;
import io.github.abdurazaaqmohammed.adapters.ExtractOptionAdapter;
import io.github.abdurazaaqmohammed.utils.CompareUtils;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.LogUtil;
import io.github.abdurazaaqmohammed.utils.MergeUtil;

public class APKExtractorActivity extends AppCompatActivity {
    private final AppExpandableListAdapter[] appAdapter = new AppExpandableListAdapter[2];
    public APKLogger logger;
    public boolean ask = false;
    public boolean errorOccurred;
    public String lang;
    public boolean showIcon;
    public boolean antisplit;
    public boolean showLastUpdate;
    public boolean showFirstInstalled;
    public boolean showVersionCode;
    public boolean showVersionName;
    public boolean showPackageName;
    public boolean showAppName;
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
    public DialogUtil dialogUtil;
    public Handler handler;
    public File superSplit;
    public Resources rss;
    public boolean zip = true;
    boolean system = false;
    private boolean signApk;
    private List<AppInfo> userAppInfoList;
    private List<AppInfo> systemAppInfoList;
    private String packageName;
    private boolean showLaunchActivities;

    public static File getAppFolder() {
        final File appFolder = new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"),
                "Extracted APKs");
        return appFolder.exists() || appFolder.mkdirs() ? appFolder
                : new File(Environment.getExternalStorageDirectory(), "Download");
    }

    @Override
    public void onBackPressed() {
        AppExpandableListAdapter adapter = getCurrentAdapter();
        if(!adapter.selectedItems.isEmpty()) adapter.clearSelection();
        else super.onBackPressed();
    }

    public static void deleteDir(File dir) {
        Util.deleteDir(dir);
    }

    public Runnable showFinishedDialog(String outputPath) {
        return showFinishedDialog(outputPath, null);
    }

    public Runnable showFinishedDialog(String outputPath, AlertDialog ad) {
        return () -> {
            if(ad != null) ad.dismiss();
            if (!errorOccurred)
                styleAlertDialog(new MaterialAlertDialogBuilder(this)
                        .setTitle("Info")
                        .setMessage(rss.getString(R.string.success_saved, outputPath))
                        .setNegativeButton("Locate", (dialog, which) -> {
                            File file = new File(outputPath);
                            this.setResult(RESULT_OK, new Intent().putExtra("dirToLoad",
                                    file.isDirectory() ? outputPath : file.getParent()));
                            finish();
                        })
                        .setPositiveButton("OK", null)
                        .create());
            errorOccurred = false;
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences themeSettings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(theme = themeSettings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));

        handler = new Handler(Looper.getMainLooper());
        dialogUtil = new DialogUtil(this);
        setContentView(R.layout.activity_extractor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle("Extract APKs");
        }

        signApk = settings.getBoolean("signApk", true);
        ask = false;
        saveWithPkgName = settings.getBoolean("saveWithPkgName", false);
        showIcon = settings.getBoolean("showIcon", true);
        showFirstInstalled = settings.getBoolean("showFirstInstalled", true);
        showAppName = settings.getBoolean("showAppName", true);
        showLastUpdate = settings.getBoolean("showLastUpdate", true);
        showPackageName = settings.getBoolean("showPackageName", true);
        showVersionCode = settings.getBoolean("showVersionCode", true);
        showVersionName = settings.getBoolean("showVersionName", true);
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
        if (Objects.equals(lang, Locale.getDefault().getLanguage()))
            rss = getResources();

        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        userAppInfoList = Collections.synchronizedList(new ArrayList<>());
        systemAppInfoList = Collections.synchronizedList(new ArrayList<>());
        setupAppLists();
        if (!LegacyUtils.supportsWriteExternalStorage) {
            getWindow().setStatusBarContrastEnforced(true);
            getWindow().setNavigationBarContrastEnforced(true);
        }

            new Thread(() -> {
                PackageManager pm = getPackageManager();
                List<PackageInfo> apps = pm.getInstalledPackages(0);

                ExecutorService executor = Executors
                        .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                for (PackageInfo app : apps) {
                    if (app.applicationInfo == null) continue;
                    executor.execute(() -> {
                        try {
                            AppInfo appInfo = new AppInfo(
                                    app.applicationInfo.sourceDir,
                                    app.applicationInfo.loadLabel(pm).toString(),
                                    null,
                                    app.packageName,
                                    app.applicationInfo.enabled,
                                    LegacyUtils.aboveSdk20 && app.applicationInfo.splitSourceDirs != null,
                                    new Date(app.firstInstallTime).toString(),
                                    new Date(app.lastUpdateTime).toString(),
                                    app.versionCode,
                                    app.versionName != null ? app.versionName : "");
                            appInfo.firstInstall = app.firstInstallTime;
                            appInfo.lastUpdate = app.lastUpdateTime;
                            appInfo.appInfo = app.applicationInfo;
                            boolean isSystem = (app.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            if (isSystem)
                                systemAppInfoList.add(appInfo);
                            else
                                userAppInfoList.add(appInfo);

                            handler.post(() -> {
                                if (isSystem)
                                    appAdapter[1].addItem(appInfo);
                                else
                                    appAdapter[0].addItem(appInfo);
                            });
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

                handler.post(() -> {
                    findViewById(R.id.progressBar).setVisibility(View.GONE);

                    Comparator<AppInfo> comparator;
                    if (sortMode == 0)
                        comparator = CompareUtils::compareAppInfoByName;
                    else
                        comparator = (p1, p2) -> {
                            long field1 = (sortMode == 1) ? p1.lastUpdate : p1.firstInstall;
                            long field2 = (sortMode == 1) ? p2.lastUpdate : p2.firstInstall;
                            return Long.compare(field2, field1);
                        };

                    Collections.sort(appAdapter[0].appInfoList, comparator);
                    Collections.sort(appAdapter[1].appInfoList, comparator);

                    appAdapter[0].setComparator(comparator);
                    appAdapter[1].setComparator(comparator);

                    EditText searchBar = findViewById(R.id.search_bar);
                    appAdapter[0].getFilter().filter(searchBar.getText());
                    appAdapter[1].getFilter().filter(searchBar.getText());

                    new Thread(() -> {
                        try {
                            loadAdditionalDetails(userAppInfoList, appAdapter[0]);
                            loadAdditionalDetails(systemAppInfoList, appAdapter[1]);
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
                        }
                    }).start();
                });
            }).start();


        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            ScrollView settingsMenu = (ScrollView) LayoutInflater.from(this).inflate(R.layout.extractor_settings, null);

            CompoundButton signToggle = settingsMenu.findViewById(R.id.signToggle);
            signToggle.setChecked(signApk);
            signToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    styleAlertDialog(new MaterialAlertDialogBuilder(this)
                            .setTitle(rss.getString(R.string.warning))
                            .setMessage(rss.getString(R.string.warn_sign))
                            .setNegativeButton(rss.getString(R.string.cancel), (dialog, which) -> {
                                signToggle.setChecked(signApk = false);
                                dialog.dismiss();
                            })
                            .setPositiveButton("OK", (dialog, which) -> {
                                signApk = true;
                                dialog.dismiss();
                            })
                            .create());
                }
            });

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

            CompoundButton showIconToggle = settingsMenu.findViewById(R.id.showIconToggle);
            showIconToggle.setChecked(showIcon);
            showIconToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showIcon = isChecked;
                reloadListView();
            });

            CompoundButton showLastUpdateToggle = settingsMenu.findViewById(R.id.showLastUpdateToggle);
            showLastUpdateToggle.setChecked(showLastUpdate);
            showLastUpdateToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showLastUpdate = isChecked;
                reloadListView();
            });

            CompoundButton showFirstInstallToggle = settingsMenu.findViewById(R.id.showFirstInstallToggle);
            showFirstInstallToggle.setChecked(showFirstInstalled);
            showFirstInstallToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showFirstInstalled = isChecked;
                reloadListView();
            });

            CompoundButton showVersionCodeToggle = settingsMenu.findViewById(R.id.showVersionCodeToggle);
            showVersionCodeToggle.setChecked(showVersionCode);
            showVersionCodeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showVersionCode = isChecked;
                reloadListView();
            });

            CompoundButton showVersionNameToggle = settingsMenu.findViewById(R.id.showVersionNameToggle);
            showVersionNameToggle.setChecked(showVersionName);
            showVersionNameToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showVersionName = isChecked;
                reloadListView();
            });

            CompoundButton showAppNameToggle = settingsMenu.findViewById(R.id.showAppNameToggle);
            showAppNameToggle.setChecked(showAppName);
            showAppNameToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showAppName = isChecked;
                reloadListView();
            });

            CompoundButton showPkgNameToggle = settingsMenu.findViewById(R.id.showPkgNameToggle);
            showPkgNameToggle.setChecked(showPackageName);
            showPkgNameToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showPackageName = isChecked;
                reloadListView();
            });

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
        ((ListView) findViewById(system ? R.id.user_app_list_view : R.id.system_app_list_view)).invalidateViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void setupAppLists() {
        ExpandableListView userAppListView = findViewById(R.id.user_app_list_view);
        ExpandableListView systemAppListView = findViewById(R.id.system_app_list_view);

        appAdapter[0] = new AppExpandableListAdapter(this, userAppInfoList);
        userAppListView.setOnItemLongClickListener((parent, view, position, id) -> {
            long packedPosition = userAppListView.getExpandableListPosition(position);
            if (ExpandableListView
                    .getPackedPositionType(packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
                appAdapter[0].toggleSelection(ExpandableListView.getPackedPositionGroup(packedPosition));
                userAppListView.setOnGroupClickListener((parent1, view1, position1, id1) -> {
                    appAdapter[0].toggleSelection(position1);
                    return true;
                });
                return true;
            }
            return false;
        });

        appAdapter[1] = new AppExpandableListAdapter(this, systemAppInfoList);
        systemAppListView.setOnItemLongClickListener((parent, view, position, id) -> {
            long packedPosition = systemAppListView.getExpandableListPosition(position);
            if (ExpandableListView
                    .getPackedPositionType(packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
                appAdapter[1].toggleSelection(ExpandableListView.getPackedPositionGroup(packedPosition));
                systemAppListView.setOnGroupClickListener((parent1, view1, position1, id1) -> {
                    appAdapter[1].toggleSelection(position1);
                    return true;
                });
                return true;
            }
            return false;
        });

        findViewById(R.id.confirmButton).setOnClickListener(v -> {
            v.setVisibility(View.INVISIBLE);
            systemAppListView.setOnGroupClickListener(null);
            userAppListView.setOnGroupClickListener(null);
            String[] display = {
                    "Extract APKs", "Share APKs",
                    "Extract resources.arsc files", "Extract classes.dex files",
                    "Extract AndroidManifest.xml files", "Extract base.apk files",
                    "Extract libs", "Extract app icon"
            };
            int[] icons = {
                    R.drawable.save_24px, R.drawable.baseline_share_24,
                    R.drawable.inventory_2_24px, R.drawable.baseline_folder_zip_24,
                    R.drawable.baseline_text_snippet_24, R.drawable.apk_document_24px,
                    R.drawable.baseline_folder_zip_24, R.drawable.image_24px
            };

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extract_options, null);
            ListView gridView = dialogView.findViewById(R.id.extractOptionsGrid);
            gridView.setAdapter(new ExtractOptionAdapter(this, display, icons));

            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setNegativeButton(rss.getString(R.string.cancel), (d, w) -> getCurrentAdapter().clearSelection())
                    .create();

            styleAlertDialog(alertDialog);

            gridView.setOnItemClickListener((parent2, view2, which, id2) -> {
                alertDialog.dismiss();
                performAction(which, getCurrentAdapter());
            });
        });

        userAppListView.setAdapter(appAdapter[0]);
        systemAppListView.setAdapter(appAdapter[1]);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    userAppListView.setVisibility(View.VISIBLE);
                    systemAppListView.setVisibility(View.GONE);
                    system = false;
                } else {
                    systemAppListView.setVisibility(View.VISIBLE);
                    userAppListView.setVisibility(View.GONE);
                    system = true;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });


        EditText searchBar = findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                getCurrentAdapter().getFilter().filter(s);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        findViewById(R.id.filterButton).setOnClickListener(v -> {
            String[] display = new String[] { "Name", "Last updated date", "First install date" };
            AlertDialog ad = new MaterialAlertDialogBuilder(this)
                    .setSingleChoiceItems(display, sortMode, (dialog, which) -> {
                        sortMode = which;
                        Comparator<AppInfo> comparator;
                        if (sortMode == 0)
                            comparator = CompareUtils::compareAppInfoByName;
                        else
                            comparator = (p1, p2) -> {
                                long field1 = (sortMode == 1) ? p1.lastUpdate : p1.firstInstall;
                                long field2 = (sortMode == 1) ? p2.lastUpdate : p2.firstInstall;
                                return Long.compare(field2, field1);
                            };

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

    public void showListViewDropdown(View anchor, int groupPosition, AppExpandableListAdapter adapter) {
        AppInfo ai = adapter.filteredAppInfoList.get(groupPosition);

        List<String> displayList = new ArrayList<>();
        List<Integer> iconList = new ArrayList<>();
        List<Integer> actionIds = new ArrayList<>();

        displayList.add("Extract APK");
        iconList.add(ai.isSplit && !antisplit ? R.drawable.archive_24px : R.drawable.save_24px);
        actionIds.add(0);

        displayList.add("Share APK");
        iconList.add(R.drawable.baseline_share_24);
        actionIds.add(1);

        displayList.add("Launch");
        iconList.add(R.drawable.baseline_open_in_new_24);
        actionIds.add(100);

        displayList.add("App Info");
        iconList.add(R.drawable.baseline_info_24);
        actionIds.add(101);

        displayList.add("Uninstall");
        iconList.add(R.drawable.baseline_delete_24);
        actionIds.add(102);

        if (showExtractRes) {
            displayList.add("Extract resources");
            iconList.add(R.drawable.inventory_2_24px);
            actionIds.add(2);
        }
        if (showExtractDex) {
            displayList.add("Extract dex");
            iconList.add(R.drawable.baseline_folder_zip_24);
            actionIds.add(3);
        }
        if (showExtractManifest) {
            displayList.add("Extract Manifest");
            iconList.add(R.drawable.baseline_text_snippet_24);
            actionIds.add(4);
        }
        if (showExtractBase) {
            displayList.add("Extract base.apk");
            iconList.add(R.drawable.apk_document_24px);
            actionIds.add(5);
        }
        if (showExtractLibs) {
            displayList.add("Extract libs");
            iconList.add(R.drawable.baseline_folder_zip_24);
            actionIds.add(6);
        }
        if (showExtractIcon) {
            displayList.add("Extract icon");
            iconList.add(R.drawable.image_24px);
            actionIds.add(7);
        }

        if(showLaunchActivities) {
            displayList.add("Launch Activity");
            iconList.add(R.drawable.baseline_open_in_new_24);
            actionIds.add(107);
        }
        if (ai.isSplit) {
            if (showExtractSplit) {
                displayList.add("Choose split APK");
                iconList.add(R.drawable.baseline_arrow_drop_down_24);
                actionIds.add(103);
            }
            if(antisplit) {
                displayList.add("Save split APKS");
                iconList.add(R.drawable.archive_24px);
                actionIds.add(106);
            } else {
                displayList.add("Antisplit merge and save");
                iconList.add(R.drawable.baseline_compress_24);
                actionIds.add(104);
            }

            displayList.add("Antisplit merge and share");
            iconList.add(R.drawable.baseline_share_24);
            actionIds.add(105);
        }

        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_dropdown_menu, null);
        ListView listView = popupView.findViewById(R.id.dropdown_list);

        DropdownAdapter dropdownAdapter = new DropdownAdapter(this, displayList, iconList);
        listView.setAdapter(dropdownAdapter);

        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        if(LegacyUtils.aboveSdk20) popupWindow.setElevation(24f);

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int measuredWidth = popupView.getMeasuredWidth();
        int popupWidth = Math.max(measuredWidth, (int) (220 * getResources().getDisplayMetrics().density));
        popupWindow.setWidth(popupWidth);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.showAsDropDown(anchor, 0, 0);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            popupWindow.dismiss();
            int actionId = actionIds.get(position);

            if(actionId==0) extract(groupPosition, antisplit, true);
            else if(actionId == 1) share(groupPosition, antisplit);
            else if (actionId < 100) {
                adapter.selectedItems.clear();
                adapter.toggleSelection(groupPosition);
                performAction(actionId, adapter);
            } else {
                String packageName = ai.packageName;
                switch (actionId) {
                    case 100: // Launch
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                        if (launchIntent == null)
                            Toast.makeText(this, "Cannot launch this app", Toast.LENGTH_SHORT).show();
                        else startActivity(launchIntent);
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
                    case 107: // Launch activity
                        PackageManager pm = getPackageManager();
                        try {
                            ActivityInfo[] activities = pm.getPackageInfo(ai.packageName, PackageManager.GET_ACTIVITIES).activities;
                            if(activities == null) Toast.makeText(this, "No launchable activities found", Toast.LENGTH_SHORT).show();

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
                        } catch (Exception e) { showError(e); }
                        break;
                }
            }
        });

    }

    private void performAction(int whichAction, AppExpandableListAdapter adapter) {
        File appFolder = getAppFolder();
        final List<Integer> itemsToProcess = new ArrayList<>(adapter.selectedItems);
        adapter.clearSelection();
        //findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);

        String path = appFolder.getPath();
        boolean singleItem = itemsToProcess.size() == 1;
        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
        dialogUtil.styleAlertDialog(progressDialog);
        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
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
                            handler.post(() -> progressText.setText("Extracting " + destName));
                            inputSource.write(FileUtils.getUnusedFile(appFolder, destName));
                            if (i == itemsToProcessSize-1) handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, progressDialog));
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
                        }
                    }
                }).start();
                break;

            case 3:
                new Thread(() -> {
                    final String classes = "classes", dex = ".dex";
                    ZipParameters zp = new ZipParameters();
                    zp.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
                    FileHeader fh;
                    for (int j = 0, itemsToProcessSize = itemsToProcess.size(); j < itemsToProcessSize; j++) {
                        AppInfo ai = adapter.filteredAppInfoList.get(itemsToProcess.get(j));
                        String destName = (saveWithPkgName ? ai.packageName : ai.name) + " v" + ai.versionName + ' ' + classes + ".zip";
                        try (ZipFile izf = new ZipFile(new File(ai.filePath));
                             ZipFile ozf = new ZipFile(FileUtils.getUnusedFile(appFolder, destName))) {
                            for (int i = 1; ; i++) {
                                String entryName = (i == 1) ? classes + dex : classes + i + dex;
                                handler.post(() -> progressText.setText(rss.getString(R.string.extracting_to, entryName, destName)));
                                if ((fh = izf.getFileHeader(entryName)) == null) break;
                                else try(InputStream is = izf.getInputStream(fh)) {
                                    zp.setFileNameInZip(entryName);
                                    handler.post(() -> progressText.setText(rss.getString(R.string.adding, entryName)));
                                    ozf.addStream(is, zp);
                                }
                            }
                            if (j == itemsToProcessSize-1) handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, progressDialog));
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
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
                            handler.post(() -> progressText.setText("Extracting " + destName));
                            zf.getEntrySource(am).write(FileUtils.getUnusedFile(appFolder, destName));
                            if (i == itemsToProcessSize-1) {
                                handler.post(showFinishedDialog(singleItem ? path + File.separator + destName : path, progressDialog));
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
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
                            handler.post(() -> progressText.setText("Extracting " + name));
                            FileUtils.copyFile(new File(ai.filePath), FileUtils.getUnusedFile(appFolder, name));
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
                        }
                    }
                    handler.post(showFinishedDialog(path, progressDialog));
                }).start();
                break;

            case 6: //libs
                new Thread(() -> {
                    APKLogger logger = LogUtil.getApkLogger(progressText, handler, this);
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
                            progressDialog.dismiss();
                            logger.close();
                            showError(e);
                        }
                    }
                    handler.post(showFinishedDialog(path, progressDialog));
                }).start();
                break;

            case 7:
                new Thread(() -> {
                    PackageManager pm = getPackageManager();
                    for (int integer : itemsToProcess) {
                        AppInfo ai = adapter.filteredAppInfoList.get(integer);
                        String name = (saveWithPkgName ? ai.packageName : ai.name) + " v" + ai.versionName + "_icon.png";
                        handler.post(() -> progressText.setText("Extracting " + name));
                        try (OutputStream os = FileUtils.getOutputStream(FileUtils.getUnusedFile(appFolder, name))) {
                            PackageInfo packageInfo = pm.getPackageInfo(ai.packageName, 0);

                            Bitmap bm = drawableToBitmap(packageInfo.applicationInfo.loadIcon(pm));

                            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e));
                        }
                    }
                    handler.post(showFinishedDialog(path, progressDialog));
                }).start();
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

    private void loadAdditionalDetails(List<AppInfo> apps, AppExpandableListAdapter adapter) {
        PackageManager pm = getPackageManager();
        for (int i = 0; i < apps.size(); i++) {
            AppInfo app = apps.get(i);
            if (showIcon && app.appInfo != null) {
                app.icon = app.appInfo.loadIcon(pm);
            }
            if (i < 10 || i % 10 == 0 || i == apps.size() - 1) {
                handler.post(adapter::notifyDataSetChanged);
            }
        }
    }

    public void extract(int pos, boolean antisplit, boolean showFinishedDialog) {
        AppInfo ai = getCurrentAdapter().filteredAppInfoList.get(pos);
        final String pkgName = ai.packageName;
        StringBuilder fileName = new StringBuilder(saveWithPkgName ? pkgName : ai.name).append(' ').append('v').append(ai.versionName);
        fileName.append(".apk");
        if (LegacyUtils.aboveSdk20 && ai.isSplit && !antisplit) fileName.append('s');
        String fileNameString = fileName.toString();
        File output = new File(getAppFolder(), fileNameString);

        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
        dialogUtil.styleAlertDialog(progressDialog);
        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);

        new Thread(() -> {
            try {
                File baseApk = new File(ai.filePath);
                File apkDirectory = baseApk.getParentFile();
                boolean split = LegacyUtils.aboveSdk20 && ai.isSplit;
                File finalOutput;
                if (split && antisplit) try (ApkBundle bundle = new ApkBundle()) {
                    bundle.loadApkDirectory(apkDirectory, false);
                    APKLogger logger = LogUtil.getApkLogger(progressText, handler, this);
                    bundle.setAPKLogger(logger);
                    finalOutput = FileUtils.getUnusedFile(output);
                    MergeUtil.mergeBundle(bundle).renameTo(finalOutput);
                    logger.close();
                }
                else {
                    finalOutput = FileUtils.getUnusedFile(output);
                    if (split) try (ZipFile zf = new ZipFile(finalOutput)) {
                        ZipParameters zp = new ZipParameters();
                        zp.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
                        for (File f : apkDirectory.listFiles()) {
                            String name = f.getName();
                            if (f.isFile() && name.endsWith(".apk")) {
                                handler.post(() -> progressText.setText(rss.getString(R.string.adding_to, name, fileNameString)));
                                zf.addFile(f, zp);
                            }
                        }
                    }
                    else {
                        handler.post(() -> progressText.setText(rss.getString(R.string.extracting, fileNameString)));
                        try (OutputStream os = FileUtils.getOutputStream(finalOutput)) {
                            FileUtils.copyFile(baseApk, os);
                        }
                    }
                }
                 handler.post(showFinishedDialog ? showFinishedDialog(finalOutput.getPath(), progressDialog) : progressDialog::dismiss);
            } catch (Exception e) {
                showError(e);
            }
        }).start();
    }

    private void share(boolean antisplit, List<Integer> itemsToProcess) {
        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
        dialogUtil.styleAlertDialog(progressDialog);
        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
        ArrayList<Uri> fileUris = new ArrayList<>();

        new Thread(() -> {
            String authority = "io.github.abdurazaaqmohammed.MPManager.provider";
            for(int i : itemsToProcess) try {
                AppInfo ai = getCurrentAdapter().filteredAppInfoList.get(i);
                boolean split = ai.isSplit;

                if (split) {
                    if (antisplit) {
                        try (ApkBundle bundle = new ApkBundle()) {
                            bundle.loadApkDirectory(new File(ai.filePath).getParentFile());
                            APKLogger logger = LogUtil.getApkLogger(progressText, handler, this);
                            bundle.setAPKLogger(logger);
                            fileUris.add(FileProvider.getUriForFile(this, authority, MergeUtil.mergeBundle(bundle)));
                            logger.close();
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
                progressDialog.dismiss();
                showError(e);
            }
            progressDialog.dismiss();

            Intent intent= new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("application/vnd.android.package-archive")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, rss.getString(R.string.share_apk)));

        }).start();
    }

    public void share(int pos, boolean antisplit) {
        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
        dialogUtil.styleAlertDialog(progressDialog);
        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);

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
                            APKLogger logger = LogUtil.getApkLogger(progressText, handler, this);
                            bundle.setAPKLogger(logger);
                            new Thread(() -> {
                                try {
                                    toShare[0] = MergeUtil.mergeBundle(bundle);
                                } catch (Exception e) {
                                    handler.post(progressDialog::dismiss);
                                    showError(e);
                                }
                            }).start();
                            logger.close();
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

                progressDialog.dismiss();

                Intent intent;
                if (isMultiple) {
                    intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType("application/vnd.android.package-archive")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
                } else {
                    Uri u = FileProvider.getUriForFile(this, "io.github.abdurazaaqmohammed.MPManager.provider", toShare[0]);
                    intent = new Intent(Intent.ACTION_SEND)
                            .setType("application/vnd.android.package-archive")
                            .putExtra(Intent.EXTRA_STREAM, u);
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, rss.getString(R.string.share_apk)));
            } catch (Exception e) {
                progressDialog.dismiss();
                showError(e);
            }
        }).start();
    }

    public void styleAlertDialog(AlertDialog ad) {
        runOnUiThread(ad::show);
    }


    @Override
    protected void onPause() {
        SharedPreferences.Editor e = getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putBoolean("ask", ask)
                .putBoolean("signApk", signApk)
                .putBoolean("saveWithPkgName", saveWithPkgName)
                .putBoolean("showIcon", showIcon)
                .putBoolean("showAppName", showAppName)
                .putBoolean("showVersionName", showVersionName)
                .putBoolean("showLastUpdate", showLastUpdate)
                .putBoolean("showVersionCode", showVersionCode)
                .putBoolean("showPackageName", showPackageName)
                .putBoolean("showExtractIcon", showExtractIcon)
                .putBoolean("showExtractRes", showExtractRes)
                .putBoolean("showExtractBase", showExtractBase)
                .putBoolean("showExtractManifest", showExtractManifest)
                .putBoolean("showExtractDex", showExtractDex)
                .putBoolean("showExtractSplit", showExtractSplit)
                .putBoolean("showExtractLibs", showExtractLibs)
                .putBoolean("showFirstInstalled", showFirstInstalled)
                .putBoolean("showLaunchActivities", showLaunchActivities)
                .putBoolean("antisplit", antisplit)
                .putInt("sortMode", sortMode)
                .putString("lang", lang);
        e.apply();
        super.onPause();
    }

    private void copyText(CharSequence text) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
        Toast.makeText(this, rss.getString(R.string.copied_log), Toast.LENGTH_SHORT).show();
    }

    public void showError(Exception e) {
        final String mainErr = e.toString();
        errorOccurred = !mainErr.equals(rss.getString(R.string.sign_failed));
        StringBuilder stackTrace = new StringBuilder().append(mainErr).append('\n');
        for (StackTraceElement line : e.getStackTrace())
            stackTrace.append(line).append('\n');
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setNegativeButton(rss.getString(R.string.cancel), null)
                .setPositiveButton(rss.getString(R.string.create_issue),
                        (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://github.com/AbdurazaaqMohammed/APKExtractor/issues/new?title=Crash%20Report&body="
                                        + stackTrace))))
                .setNeutralButton(rss.getString(R.string.copy_log), (dialog, which) -> copyText(stackTrace));
        runOnUiThread(() -> {
            TextView title = new TextView(this);
            title.setText(mainErr);
            title.setTextSize(20);
            TextView msg = new TextView(this);
            msg.setText(stackTrace);
            ScrollView sv = new ScrollView(this);
            msg.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    (int) (rss.getDisplayMetrics().heightPixels * 0.6)));
            sv.addView(msg);
            styleAlertDialog(b.setCustomTitle(title).setView(sv).create());
        });
    }

    private AppExpandableListAdapter getCurrentAdapter() {
        return appAdapter[system ? 1 : 0];
    }
}