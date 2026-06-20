package io.github.abdurazaaqmohammed.MPManager;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.adapters.BookmarksAdapter;
import io.github.abdurazaaqmohammed.adapters.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import android.view.View;
import android.view.GestureDetector;
import androidx.core.view.GestureDetectorCompat;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import android.content.res.Configuration;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.github.paul035.LocaleHelper;
import com.reandroid.utils.StringsUtil;
import com.reandroid.utils.io.FileUtil;

public class MainActivity extends AppCompatActivity {
    boolean logEnabled;
    private File homeDir1;
    private File homeDir2;
    private String lastVerChecked;
    public File pane1Folder;
    private boolean checkForUpdates;
    public File pane2Folder;
    public int lastPaneSelected = 1;
    public DialogUtil dialogUtil;
    public UIHelper uiHelper;
    private final List<NavigationHistoryEntry> pane1History = new ArrayList<>();
    private final List<NavigationHistoryEntry> pane2History = new ArrayList<>();
    private int pane1HistoryIndex = -1;
    private int pane2HistoryIndex = -1;
    private ArrayList<File> bookmarks;
    private BookmarksAdapter bookmarksAdapter;
    public String signatureKeyPath;
    private DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    public boolean isSidebarDrawerOpen;
    public boolean isBookmarksDrawerOpen;
    public Handler handler;
    private boolean systemTheme;
    public int theme;
    private Runnable checkUpdateAfterStoragePermission;

    private File[] currentPane1Files;
    private File[] currentPane2Files;
    private List<ZipEntryInfo> currentPane1ZipEntries;
    private List<ZipEntryInfo> currentPane2ZipEntries;
    private String currentPane1Filter = "";
    private String currentPane2Filter = "";

    private void checkForUpdates(boolean toast) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(
                        "https://api.github.com/repos/AbdurazaaqMohammed/MP-Manager/releases").openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                try (InputStream inputStream = conn.getInputStream();
                        InputStreamReader in = new InputStreamReader(inputStream);
                        BufferedReader reader = new BufferedReader(in)) {
                    String line;
                    String latestVersion = "";
                    String changelog = "";
                    String dl = "";
                    boolean rightBranch = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("browser_download_url")) {
                            dl = line.split("\"")[3];
                            latestVersion = line.split("/")[7];
                            rightBranch = latestVersion.charAt(0) == '2';
                        } else if (line.contains("body") && rightBranch) {
                            changelog = line.split("\"")[3];
                            break;
                        }
                    }
                    String currentVer;
                    try {
                        currentVer = (MainActivity.this).getPackageManager()
                                .getPackageInfo((MainActivity.this).getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        currentVer = null;
                    }
                    boolean newVer = false;
                    char[] curr = TextUtils.isEmpty(currentVer) ? new char[] { '2', '2', '7' }
                            : currentVer.replace(".", "").toCharArray();
                    char[] latest = latestVersion.replace(".", "").toCharArray();

                    int maxLength = Math.max(curr.length, latest.length);
                    for (int i = 0; i < maxLength; i++) {
                        char currChar = i < curr.length ? curr[i] : '0';
                        char latestChar = i < latest.length ? latest[i] : '0';

                        if (latestChar > currChar) {
                            newVer = true;
                            break;
                        } else if (latestChar < currChar) {
                            break;
                        }
                    }

                    if (newVer) {
                        if (!toast && !TextUtils.isEmpty(lastVerChecked) && lastVerChecked.equals(latestVersion))
                            return;
                        String ending = ".apk";
                        String filename = "MP Manager.v" + latestVersion + ending;
                        String link = dl.endsWith(ending) ? dl : dl + File.separator + filename;
                        MaterialTextView changelogText = new MaterialTextView(MainActivity.this);
                        String linebreak = "<br />";
                        changelogText.setText(Html.fromHtml(
                                rss.getString(R.string.new_ver) + " (" + latestVersion + ")" + linebreak + linebreak
                                        + "Changelog:" + linebreak + changelog.replace("\\r\\n", linebreak)));
                        int padding = 16;
                        changelogText.setPadding(padding, padding, padding, padding);
                        MaterialTextView title = new MaterialTextView(MainActivity.this);
                        title.setText(rss.getString(R.string.update));
                        int size = 20;
                        title.setPadding(size, size, size, size);
                        title.setTextSize(size);
                        title.setGravity(Gravity.CENTER);

                        String finalLatestVersion = latestVersion;
                        handler.post(() -> {
                            AlertDialog alertDialog = new MaterialAlertDialogBuilder(MainActivity.this)
                                    .setCustomTitle(title).setView(changelogText)
                                    .setPositiveButton(rss.getString(R.string.dl), (dialog, which) -> {
                                        if (checkUpdateAfterStoragePermission == null)
                                            checkUpdateAfterStoragePermission = () -> {
                                                DownloadManager.Request request = new DownloadManager.Request(
                                                        Uri.parse(link))
                                                        .setTitle(filename).setDescription(filename)
                                                        .setMimeType("application/vnd.android.package-archive")
                                                        .setDestinationInExternalPublicDir(
                                                                Environment.DIRECTORY_DOWNLOADS, filename)
                                                        .setNotificationVisibility(
                                                                DownloadManager.Request.VISIBILITY_VISIBLE
                                                                        | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                                downloadId = ((DownloadManager) MainActivity.this
                                                        .getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                                            };
                                        if (Build.VERSION.SDK_INT < 29
                                                && doesNotHaveStoragePerm(this))
                                            MainActivity.this.checkStoragePerm();
                                        else
                                            checkUpdateAfterStoragePermission.run();
                                    })
                                    .setNegativeButton("Go to GitHub Release", (dialog, which) -> MainActivity.this
                                            .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(
                                                    "https://github.com/AbdurazaaqMohammed/MP-Manager/releases/latest"))))
                                    .setNeutralButton(rss.getString(R.string.cancel), null).create();
                            alertDialog.setOnDismissListener(dialog -> lastVerChecked = finalLatestVersion);
                            MainActivity.this.runOnUiThread(alertDialog::show);
                        });
                    } else if (toast)
                        handler.post(() -> MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                rss.getString(R.string.no_update_found), Toast.LENGTH_SHORT).show()));
                }
            } catch (Exception e) {
                if (toast)
                    runOnUiThread(() -> Toast
                            .makeText(MainActivity.this, "Failed to check for update", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private ArrayList<File> getBookmarks() {
        if (bookmarks == null) {
            bookmarks = new ArrayList<>();
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String[] savedBookmarks = settings.getString("bookmarks", "").replace("[", "").replace("]", "").split(", ");
            for (String bookmark : savedBookmarks) {
                if (!TextUtils.isEmpty(bookmark)) {
                    File bookmarked = new File(bookmark);
                    if (bookmarked.exists())
                        bookmarks.add(bookmarked);
                }
            }
        }
        return bookmarks;
    }

    public void addBookmark(File toBookmark) {
        bookmarks.add(toBookmark);
        bookmarksAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.edit()
                .putString("bookmarks", bookmarks.toString())
                .putString("keyPath", signatureKeyPath)
                .putBoolean("systemTheme", systemTheme)
                .putInt("theme", theme)
                .apply();
    }

    public void openSidebarDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
        isSidebarDrawerOpen = true;
    }

    public void closeSidebarDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
        isSidebarDrawerOpen = false;
    }

    private void checkStoragePerm() {
        if (doesNotHaveStoragePerm(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())), 0);
            } else if (Build.VERSION.SDK_INT > 22)
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) if (doesNotHaveStoragePerm(this)) {
            Toast.makeText(this, "Storage perm needed as file manager", Toast.LENGTH_LONG).show();
        } else recreate();
    }


    public static boolean doesNotHaveStoragePerm(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? !Environment.isExternalStorageManager() : Build.VERSION.SDK_INT > 22 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 0) if (doesNotHaveStoragePerm(this)) {
            Toast.makeText(this, "Storage perm needed as file manager", Toast.LENGTH_LONG).show();
        } else recreate();
        else if (requestCode == 11 && resultCode == RESULT_OK) {
            String dirToLoad = data.getStringExtra("dirToLoad");
            if (dirToLoad != null) {
                loadFolderInPane(new File(dirToLoad), lastPaneSelected == 1);
            } else {
                Uri path = data.getData();
                if (path != null)
                    loadFolderInPane(new File(path.toString()), lastPaneSelected == 1);
            }
        }
    }

    public void openBookmarksDrawer() {
        findViewById(R.id.bookmarks_drawer).post(() -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            isBookmarksDrawerOpen = true;
        });
    }

    public void closeBookmarksDrawer() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        isBookmarksDrawerOpen = false;
    }

    public ListView getCurrentPane() {
        return findViewById(lastPaneSelected == 1 ? R.id.listViewPane1 : R.id.listViewPane2);
    }

    private String lang;
    public Resources rss;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (systemTheme) {
            int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                if (theme != R.style.Theme_MyApp_Dark) {
                    setTheme(theme = R.style.Theme_MyApp_Dark);
                    recreate();
                }
            } else if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                if (theme != R.style.Theme_MyApp_Light) {
                    setTheme(theme = R.style.Theme_MyApp_Light);
                    recreate();
                }
            }
        }
    }

    long downloadId;
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (id == downloadId) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                            int columnIndex1 = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            String fileUri = cursor.getString(columnIndex1);
                            io.github.abdurazaaqmohammed.utils.InstallUtil.installApk(MainActivity.this,
                                    Uri.parse(fileUri));
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(theme = settings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));

        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        // WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);
        checkStoragePerm();
        filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null && filePickerCallback != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            filePickerCallback.accept(uri);
                        }
                    }
                    filePickerCallback = null;
                });
        String deviceLang = Locale.getDefault().getLanguage();
        boolean supportedLang = deviceLang.equals("ar") || deviceLang.equals("es") || deviceLang.equals("de")
                || deviceLang.equals("fr") || deviceLang.equals("in") || deviceLang.equals("it")
                || deviceLang.equals("pt-BR") || deviceLang.equals("ru") || deviceLang.equals("tr")
                || deviceLang.equals("uk") || deviceLang.equals("vi") || deviceLang.equals("zh-TW")
                || deviceLang.equals("pl") || deviceLang.equals("hu") || deviceLang.equals("ko");

        lang = settings.getString("lang", supportedLang ? deviceLang : "en");
        boolean useDeviceRss = lang.equals(deviceLang);
        rss = useDeviceRss ? getResources() : LocaleHelper.setLocale(this, lang).getResources();

        if (theme == R.style.Theme_MyApp_Black)
            findViewById(R.id.main).setBackgroundColor(Color.BLACK);

        if (Build.VERSION.SDK_INT > 20) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int transparent = Color.TRANSPARENT;
            getWindow().setNavigationBarColor(transparent);
            getWindow().setStatusBarColor(transparent);
        }

        if (!io.github.abdurazaaqmohammed.utils.LegacyUtils.supportsWriteExternalStorage) {
            // EdgeToEdge.enable(this);
            getWindow().setStatusBarContrastEnforced(true);
            getWindow().setNavigationBarContrastEnforced(true);
        }


        new Thread(() -> {
            File frameworks = new File("/storage/emulated/0/MP Manager/frameworks/");
            if(!doesNotHaveStoragePerm(this) && !frameworks.exists()) try(
                    InputStream is23 = rss.openRawResource(R.raw.android_23);
                    InputStream is24 = rss.openRawResource(R.raw.android_24);
                    InputStream is25 = rss.openRawResource(R.raw.android_25);
                    InputStream is26 = rss.openRawResource(R.raw.android_26);
                    InputStream is27 = rss.openRawResource(R.raw.android_27);
                    InputStream is28 = rss.openRawResource(R.raw.android_28);
                    InputStream is29 = rss.openRawResource(R.raw.android_29);
                    InputStream is30 = rss.openRawResource(R.raw.android_30);
                    InputStream is31 = rss.openRawResource(R.raw.android_31);
                    InputStream is32 = rss.openRawResource(R.raw.android_32);
                    InputStream is33 = rss.openRawResource(R.raw.android_33);
                    InputStream is34 = rss.openRawResource(R.raw.android_34);
                    InputStream is35 = rss.openRawResource(R.raw.android_35);
                    InputStream is36 = rss.openRawResource(R.raw.android_36)
            ) {
                frameworks.mkdir();
                FileUtils.copyFile(is23, new File(frameworks, "android_23.apk"));
                FileUtils.copyFile(is24, new File(frameworks, "android_24.apk"));
                FileUtils.copyFile(is25, new File(frameworks, "android_25.apk"));
                FileUtils.copyFile(is26, new File(frameworks, "android_26.apk"));
                FileUtils.copyFile(is27, new File(frameworks, "android_27.apk"));
                FileUtils.copyFile(is28, new File(frameworks, "android_28.apk"));
                FileUtils.copyFile(is29, new File(frameworks, "android_29.apk"));
                FileUtils.copyFile(is30, new File(frameworks, "android_30.apk"));
                FileUtils.copyFile(is31, new File(frameworks, "android_31.apk"));
                FileUtils.copyFile(is32, new File(frameworks, "android_32.apk"));
                FileUtils.copyFile(is33, new File(frameworks, "android_33.apk"));
                FileUtils.copyFile(is34, new File(frameworks, "android_34.apk"));
                FileUtils.copyFile(is35, new File(frameworks, "android_35.apk"));
                FileUtils.copyFile(is36, new File(frameworks, "android_36.apk"));
            } catch (Exception exception) {

            }
        }).start();

            lastVerChecked = settings.getString("lastVerChecked", null);
        if ((checkForUpdates = settings.getBoolean("checkForUpdates", true)))
            checkForUpdates(false);

        handler = new Handler(Looper.getMainLooper());
        drawerLayout = findViewById(R.id.drawer_layout);
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bookmarks_drawer));
        bottomSheetBehavior.setPeekHeight(0, false); // animate=false, keeps it hidden
        bottomSheetBehavior.setHideable(true); // allows fully hidden state
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN); // truly hidden at start

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                isBookmarksDrawerOpen = (newState == BottomSheetBehavior.STATE_EXPANDED
                        || newState == BottomSheetBehavior.STATE_HALF_EXPANDED);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                isSidebarDrawerOpen = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                isSidebarDrawerOpen = false;
            }
        });

        ListView bookmarksList = findViewById(R.id.bookmarksList);
        ArrayList<File> bookmarks1 = getBookmarks();
        bookmarksList.setAdapter(bookmarksAdapter = new BookmarksAdapter(this, bookmarks1));
        bookmarksList.setOnItemClickListener((parent, view, position, id) -> {
            File bookmarked = bookmarks1.get(position);
            loadFolderInPane(bookmarked.isFile() ? bookmarked.getParentFile() : bookmarked, lastPaneSelected == 1);
            closeBookmarksDrawer();
        });

        View.OnClickListener toggleSidebarDrawer = v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            else
                drawerLayout.openDrawer(GravityCompat.START);
        };

        ListView sidebar = findViewById(R.id.sidebarList);
        String[] options = { "Extract APK", "Settings" };
        int[] icons = {R.drawable.apk_document_24px, R.drawable.baseline_settings_24};
        sidebar.setAdapter(new ArrayAdapter<String>(this, R.layout.item_dropdown_option, options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_dropdown_option, parent, false);
                }

                convertView.<ImageView>findViewById(R.id.optionIcon).setImageResource(icons[position]);
                convertView.<TextView>findViewById(R.id.optionText).setText(options[position]);

                return convertView;
            }
        });
        sidebar.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0:
                    startActivityForResult(new Intent(this, APKExtractorActivity.class), 11);
                    break;
                case 1:
                    showSettingsDialog();
                    break;
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        GestureDetectorCompat bottomBarGestureDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true; // MUST return true to receive subsequent events
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                            float velocityX, float velocityY) {
                        if (e1 != null && e2 != null
                                && e1.getY() - e2.getY() > 50 // upward movement (px)
                                && velocityY < -200) { // negative = upward velocity
                            openBookmarksDrawer();
                            return true;
                        }
                        return false;
                    }
                });

        View.OnTouchListener bottomBarTouchListener = (v, event) -> {
            bottomBarGestureDetector.onTouchEvent(event);
            return v.getId() == R.id.bottomBar;
        };

        LinearLayout bottomBar = findViewById(R.id.bottomBar);
        bottomBar.setOnTouchListener(bottomBarTouchListener);
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            bottomBar.getChildAt(i).setOnTouchListener(bottomBarTouchListener);
        }

        findViewById(R.id.hamburgerMenu).setOnClickListener(toggleSidebarDrawer);

        findViewById(R.id.bookmarksText).setOnClickListener(v -> closeBookmarksDrawer());

        dialogUtil = new DialogUtil(this);
        uiHelper = new UIHelper(this);

        systemTheme = settings.getBoolean("systemTheme", true);
        String homeDir1Path = settings.getString("home1", null);
        homeDir1 = TextUtils.isEmpty(homeDir1Path) ? Environment.getExternalStorageDirectory() : new File(homeDir1Path);
        String homeDir2Path = settings.getString("home2", null);
        homeDir2 = TextUtils.isEmpty(homeDir2Path) ? Environment.getExternalStorageDirectory() : new File(homeDir2Path);
        try {
            signatureKeyPath = settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", this).getPath());
        } catch (IOException e) {
            new ErrorUtil(this).showError(e);
        }

        TextView currentFolderView = findViewById(R.id.currentFolderPath);
        currentFolderView.setText(
                TextUtils.isEmpty(homeDir1Path) ? Environment.getExternalStorageDirectory().getPath() : homeDir1Path);
        currentFolderView.setOnClickListener(v -> {
            EditText input = new EditText(MainActivity.this);
            input.setHint("Enter path");
            dialogUtil.getDialogBuilder()
                    .setTitle("Enter path")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("OK", (dialog, which) -> {
                        File inputPath = new File(input.getText().toString());
                        if (inputPath.exists() && inputPath.isDirectory()
                                || (!inputPath.exists() && inputPath.mkdirs())) {
                            boolean isPane1 = lastPaneSelected == 1;
                            if (isPane1)
                                pane1Folder = inputPath;
                            else
                                pane2Folder = inputPath;
                            loadFolderInPane(inputPath, isPane1);
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to navigate to or create path " + inputPath,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }).show();
        });

        File[] dir1Files = homeDir1.listFiles();
        if (dir1Files != null) {
            File[] folders = homeDir1.listFiles(File::isDirectory);
            int foldersCount = folders == null ? 0 : folders.length;
            this.<TextView>findViewById(R.id.folderCount).setText(
                    new StringBuilder("Folders: ").append(foldersCount).append(" Files: ")
                            .append(dir1Files.length - foldersCount));
        }

        ListView pane1 = findViewById(R.id.listViewPane1);
        ListView pane2 = findViewById(R.id.listViewPane2);

        pane1.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN && lastPaneSelected != 1) {
                lastPaneSelected = 1;
                MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane1.getAdapter();
                if (adapter != null) {
                    if (adapter.isInZip) {
                        setCurrentFolder(adapter.currentZipPath, Arrays.asList(adapter.values));
                    } else
                        setCurrentFolder(pane1Folder, pane1Folder.listFiles());
                }
            }
            return false;
        });



        pane2.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN && lastPaneSelected != 2) {
                lastPaneSelected = 2;
                MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane2.getAdapter();
                if (adapter != null) {
                    if (adapter.isInZip) {
                        setCurrentFolder(adapter.currentZipPath, Arrays.asList(adapter.values));
                    } else
                        setCurrentFolder(pane2Folder, pane2Folder.listFiles());
                }
            }
            return false;
        });

        setupNavigationButtons();
        loadFolderInPane(homeDir1, true);
        loadFolderInPane(homeDir2, false);

        ImageView addButton = findViewById(R.id.addButton);
        addButton.setOnClickListener(v -> {
            EditText input = new EditText(MainActivity.this);
            input.setHint("Enter name");
            AlertDialog ad = dialogUtil.getDialogBuilder()
                    .setTitle("Create")
                    .setView(input)
                    .setNegativeButton("Folder", (dialog, which) -> {
                        boolean isPane1 = lastPaneSelected == 1;
                        File ogFolder = isPane1 ? pane1Folder : pane2Folder;
                        String inputStr = input.getText().toString();
                        if (new File(ogFolder, inputStr).mkdir())
                            loadFolderInPane(ogFolder, isPane1);
                        else
                            Toast.makeText(MainActivity.this, "Failed to create folder " + inputStr, Toast.LENGTH_SHORT)
                                    .show();
                    })
                    .setNeutralButton("Paste", null)
                    .setPositiveButton("File", (dialog, which) -> {
                        boolean isPane1 = lastPaneSelected == 1;
                        File ogFolder = isPane1 ? pane1Folder : pane2Folder;
                        String inputStr = input.getText().toString();
                        try {
                            if (new File(ogFolder, inputStr).createNewFile()) {
                                loadFolderInPane(ogFolder, isPane1);
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to create file " + inputStr,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (IOException e) {
                            Toast.makeText(MainActivity.this, "Failed to create file " + inputStr, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }).create();
            ad.show();
            ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v2 -> {
                int selectionStart = input.getSelectionStart();
                int selectionEnd = input.getSelectionEnd();
                if (selectionStart != selectionEnd) {
                    input.getText().delete(selectionStart, selectionEnd);
                }
                CharSequence text = ((android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                        .getText();
                if (!TextUtils.isEmpty(text))
                    input.getText().insert(selectionStart, text);
            });
        });

        ImageView syncPaneButton = findViewById(R.id.syncPaneButton);
        syncPaneButton.setOnClickListener(v -> {
            if (lastPaneSelected == 1)
                loadFolderInPane(pane2Folder = ((MainFilesArrayAdapter)getCurrentPane().getAdapter()).isInZip ? pane1Folder.getParentFile() : pane1Folder, false);
            else
                loadFolderInPane(pane1Folder = ((MainFilesArrayAdapter)getCurrentPane().getAdapter()).isInZip ? pane2Folder.getParentFile() : pane2Folder, true);
        });

        ImageView moreOptionsMenu = findViewById(R.id.moreOptionsMenu);
        moreOptionsMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            Menu menu = popup.getMenu();
            menu.add(0, 0, 0, "Refresh").setIcon(R.drawable.baseline_refresh_24);
            menu.add(0, 1, 0, "Filter").setIcon(R.drawable.baseline_filter_list_24);
            menu.add(0, 2, 0, "Search").setIcon(R.drawable.baseline_search_24);
            menu.add(0, 3, 0, "Select all").setIcon(R.drawable.baseline_select_all_24);
            menu.add(0, 4, 0, "Sort").setIcon(R.drawable.baseline_sort_24);

            SubMenu hiddenMenu = menu.addSubMenu(0, 5, 0, "Hidden files");
            hiddenMenu.setIcon(R.drawable.visibility_off_24px);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            MenuItem sysItem = hiddenMenu.add(0, 6, 0, "Show system hidden files");
            sysItem.setCheckable(true).setChecked(prefs.getBoolean("show_system_hidden", false));
            MenuItem manItem = hiddenMenu.add(0, 7, 0, "Show manually hidden files");
            manItem.setCheckable(true).setChecked(prefs.getBoolean("show_manually_hidden", false));

            MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
            MenuItem hideSel = hiddenMenu.add(0, 8, 0, "Hide selected files");
            hideSel.setEnabled(adapter != null && adapter.isMultiSelectMode());
            hiddenMenu.add(0, 9, 0, "Edit hidden files").setIcon(R.drawable.baseline_drive_file_rename_outline_24);

            menu.add(0, 10, 0, "Add to bookmarks").setIcon(R.drawable.baseline_bookmark_24);
            menu.add(0, 11, 0, "Set as home folder").setIcon(R.drawable.baseline_home_24);
            menu.add(0, 12, 0, "Swap panes").setIcon(R.drawable.baseline_swap_horiz_24);
            menu.add(0, 13, 0, "Preferences").setIcon(R.drawable.baseline_settings_24);
            menu.add(0, 14, 0, "Exit").setIcon(R.drawable.baseline_exit_to_app_24);

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if ("Refresh".equals(title)) {
                    reloadCurrentFolder();
                } else if ("Filter".equals(title)) {
                    LinearLayout pathLayout = (LinearLayout) ((LinearLayout) findViewById(R.id.topBar)).getChildAt(1);
                    EditText filterBar = (EditText) ((LinearLayout) findViewById(R.id.topBar)).getChildAt(2);
                    if (pathLayout.getVisibility() == View.VISIBLE) {
                        pathLayout.setVisibility(View.GONE);
                        filterBar.setVisibility(View.VISIBLE);
                        filterBar.requestFocus();
                    } else {
                        pathLayout.setVisibility(View.VISIBLE);
                        filterBar.setVisibility(View.GONE);
                        filterBar.setText("");
                    }
                } else if ("Search".equals(title)) {
                    showSearchDialog();
                } else if ("Select all".equals(title)) {
                    if (adapter != null)
                        adapter.selectAll();
                } else if ("Sort".equals(title)) {
                    showSortDialog();
                } else if ("Show system hidden files".equals(title)) {
                    boolean isChecked = !item.isChecked();
                    item.setChecked(isChecked);
                    prefs.edit().putBoolean("show_system_hidden", isChecked).apply();
                    reloadCurrentFolder();
                } else if ("Show manually hidden files".equals(title)) {
                    boolean isChecked = !item.isChecked();
                    item.setChecked(isChecked);
                    prefs.edit().putBoolean("show_manually_hidden", isChecked).apply();
                    reloadCurrentFolder();
                } else if ("Hide selected files".equals(title)) {
                    Set<String> manualHidden = new HashSet<>(
                            prefs.getStringSet("manually_hidden_files", new HashSet<>()));
                    for (Object obj : adapter.getSelectedFiles()) {
                        if (obj instanceof File)
                            manualHidden.add(((File) obj).getPath());
                        else if (obj instanceof ZipEntryInfo)
                            manualHidden.add(((ZipEntryInfo) obj).getFullPath());
                    }
                    prefs.edit().putStringSet("manually_hidden_files", manualHidden).apply();
                    adapter.clearSelection();
                    reloadCurrentFolder();
                } else if ("Edit hidden files".equals(title)) {
                    showEditHiddenFilesDialog();
                } else if ("Add to bookmarks".equals(title)) {
                    boolean isPane1 = lastPaneSelected == 1;
                    addBookmark(isPane1 ? pane1Folder : pane2Folder);
                    Toast.makeText(MainActivity.this, "Added to bookmarks", Toast.LENGTH_SHORT).show();
                } else if ("Set as home folder".equals(title)) {
                    boolean isPane1 = lastPaneSelected == 1;
                    prefs.edit().putString(isPane1 ? "home1" : "home2", (isPane1 ? pane1Folder : pane2Folder).getPath())
                            .apply();
                    Toast.makeText(MainActivity.this, "Set as home folder", Toast.LENGTH_SHORT).show();
                } else if ("Swap panes".equals(title)) {
                    File temp = pane1Folder;
                    pane1Folder = pane2Folder;
                    pane2Folder = temp;
                    loadFolderInPane(pane1Folder, true);
                    loadFolderInPane(pane2Folder, false);
                } else if ("Preferences".equals(title)) {
                    showSettingsDialog();
                } else if ("Exit".equals(title)) {
                    finishAffinity();
                }
                return true;
            });
            forceShowIcons(popup);
            popup.show();
        });

        ImageView upButton = findViewById(R.id.upButton);
        upButton.setOnClickListener(v -> {
            boolean isPane1 = lastPaneSelected == 1;
            MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
            if (adapter.isInZip) {
                File zipFile = isPane1 ? pane1Folder : pane2Folder;
                if (TextUtils.isEmpty(adapter.currentZipPath)) {
                    if (zipFile.getParentFile() != null)
                        loadFolderInPane(zipFile.getParentFile(), isPane1);
                } else {
                    File parentInZip = new File(adapter.currentZipPath).getParentFile();
                    loadZipFolderInPane(zipFile, parentInZip != null ? parentInZip.getPath() : "", isPane1, true);
                }
            } else {
                loadFolderInPane((File) adapter.getItem(0), isPane1);
            }
        });
        setupFilterBar();
    }

    private void setupNavigationButtons() {
        ImageView backButton = findViewById(R.id.backButton);
        ImageView forwardButton = findViewById(R.id.forwardButton);
        backButton.setOnClickListener(v -> navigateBack(lastPaneSelected == 1));
        forwardButton.setOnClickListener(v -> navigateForward(lastPaneSelected == 1));
        updateNavigationButtons();
    }

    @Override
    public void onBackPressed() {
        if (isSidebarDrawerOpen) {
            closeSidebarDrawer();
        } else if (isBookmarksDrawerOpen) {
            closeBookmarksDrawer();
        } else {
            ViewGroup topBar = findViewById(R.id.topBar);
            EditText filterBar =k; (EditText) topBar.getChildAt(2);
            if (filterBar.getVisibility() == View.VISIBLE) {
                topBar.getChildAt(1).setVisibility(View.VISIBLE);
                filterBar.setVisibility(View.GONE);
                filterBar.setText("");
            } else {
                MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
                if (adapter.isMultiSelectMode()) adapter.clearSelection();
                else {
                    String s = adapter.currentZipPath;
                    if(adapter.isInZip && !StringsUtil.isEmpty(s)) {
                        char[] chars = s.toCharArray();
                        int i = 0;
                        for(char c : chars) if (c == File.separatorChar) i++;
                        boolean inOneLevelInZip = i < 2;
                        loadZipFolderInPane(((ZipEntryInfo)adapter.values[0]).getZipFile(), inOneLevelInZip ? "" : s.substring(0, s.lastIndexOf('/', s.lastIndexOf('/') - 1)), adapter.pane1, false);
                    } else if (!navigateBack(lastPaneSelected == 1)) {
                        /*String s = this.<TextView>findViewById(R.id.currentFolderPath).getText().toString();
                        File f = new File(s).getParentFile();
                        if(f.canRead()) loadFolderInPane(f, pane1, false);
                        else super.onBackPressed();*/
                    }
                }
            }
        }
    }

    public boolean navigateBack(boolean pane1) {
        String suffix = " (Search Results)";
        String s = this.<TextView>findViewById(R.id.currentFolderPath).getText().toString();
        // We should not add search results on history but look for better way to do this
        if(s.endsWith(suffix)) {
            loadFolderInPane(new File(s.replace(suffix, "")), pane1, false);
            return true;
        }
        else {
            List<NavigationHistoryEntry> history = pane1 ? pane1History : pane2History;
            int historyIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
            if (historyIndex > 0) {
                NavigationHistoryEntry entry = history.get(--historyIndex);
                if (pane1) pane1HistoryIndex = historyIndex;
                else pane2HistoryIndex = historyIndex;
                if (entry.isZip()) {
                    loadZipFolderInPane(entry.getFile(), entry.getZipPath(), pane1, false);
                } else {
                    loadFolderInPane(entry.getFile(), pane1, false);
                }
                return true;
            } else return false;
        }
    }

    public void navigateForward(boolean pane1) {
        List<NavigationHistoryEntry> history = pane1 ? pane1History : pane2History;
        int historyIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
        if (historyIndex < history.size() - 1) {
            NavigationHistoryEntry entry = history.get(++historyIndex);
            if (pane1)
                pane1HistoryIndex = historyIndex;
            else
                pane2HistoryIndex = historyIndex;
            if (entry.isZip()) {
                loadZipFolderInPane(entry.getFile(), entry.getZipPath(), pane1, false);
            } else {
                loadFolderInPane(entry.getFile(), pane1, false);
            }
        }
    }

    private void updateNavigationButtons() {
        ImageView backButton = findViewById(R.id.backButton);
        ImageView forwardButton = findViewById(R.id.forwardButton);
        boolean canGoBack = lastPaneSelected == 1 ? pane1HistoryIndex > 0 : pane2HistoryIndex > 0;
        boolean canGoForward = lastPaneSelected == 1 ? pane1HistoryIndex < pane1History.size() - 1
                : pane2HistoryIndex < pane2History.size() - 1;
        backButton.setEnabled(canGoBack);
        forwardButton.setEnabled(canGoForward);
    }

    public void loadFolderInPane(File folder, boolean pane1, boolean addToHistory) {
        if (folder.getName().endsWith(".zip")) {
            loadZipFolderInPane(folder, "", pane1, addToHistory);
            return;
        }
        File[] files = folder.listFiles(this::isNotHidden);
        if (files == null) {
            Toast.makeText(this, "Could not open folder " + folder.getName(), Toast.LENGTH_SHORT).show();
            return;
        }
        sortFiles(files, folder.getPath());
        if (pane1) {
            currentPane1Files = files;
            pane1Folder = folder;
            if (addToHistory) {
                while (pane1History.size() > pane1HistoryIndex + 1) {
                    pane1History.remove(pane1History.size() - 1);
                }
                pane1History.add(new NavigationHistoryEntry(folder, false, null));
                pane1HistoryIndex++;
            }
        } else {
            currentPane2Files = files;
            pane2Folder = folder;
            if (addToHistory) {
                while (pane2History.size() > pane2HistoryIndex + 1) {
                    pane2History.remove(pane2History.size() - 1);
                }
                pane2History.add(new NavigationHistoryEntry(folder, false, null));
                pane2HistoryIndex++;
            }
        }
        setCurrentFolder(folder, files);
        ListView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        pane.setAdapter(new MainFilesArrayAdapter(this, files, folder.getParentFile(), pane1, false, null));
        updateNavigationButtons();
    }

    public void loadZipFolderInPane(File zipFile, String path, boolean pane1, boolean addToHistory) {
        try {
            List<ZipEntryInfo> entries = new ArrayList<>();
            ZipEntryInfo parent = null;
            HashSet<String> seenDirs = new HashSet<String>() {
            };
            try (ZipFile zf = new ZipFile(zipFile)) {
                String parentPath = TextUtils.isEmpty(path) ? "" : path;
                if (!TextUtils.isEmpty(parentPath) && !parentPath.endsWith("/")) parentPath += "/";
                if (TextUtils.isEmpty(path)) {
                    entries.add(new ZipEntryInfo("..", null, true, 0L, 0L, zipFile));
                } else {
                    String parentDir = new File(path).getParent();
                    if (parentDir == null) parentDir = "";
                    String parentFull = parentDir.isEmpty() ? "" : parentDir.replaceAll("/+$","") + "/";
                    parent = new ZipEntryInfo("..", parentFull, true, 0L, 0L, zipFile);
                    entries.add(parent);
                }

                Enumeration<? extends ZipEntry> e = zf.entries();
                String prefix = parentPath; // already normalized with trailing slash if non-empty
                while (e.hasMoreElements()) {
                    ZipEntry entry = e.nextElement();
                    String entryPath = entry.getName().replace('\\','/');
                    if (!entryPath.startsWith(prefix) || entryPath.equals(prefix)) continue;
                    String rest = entryPath.substring(prefix.length()); // e.g., "subdir/file" or "file.txt" or "subdir/"
                    // direct child if rest has no further '/'
                    int nextSlash = rest.indexOf('/');
                    if (nextSlash == -1) {
                        // file directly inside current folder
                        ZipEntryInfo info = new ZipEntryInfo(entry, zipFile, path);
                        if (isNotHidden(info)) entries.add(info);
                    } else {
                        // it's inside a subdirectory; we should add a single synthetic directory entry for that subdir
                        String childDirName = rest.substring(0, nextSlash + 1); // include trailing slash
                        String childFullPath = prefix + childDirName; // full path of the child dir
                        // add only once: track seen dirs with a Set<String>
                        if (seenDirs.add(childFullPath)) {
                            ZipEntry syntheticDir = new ZipEntry(childFullPath);
                            ZipEntryInfo info = new ZipEntryInfo(syntheticDir, zipFile, path); // or use new ctor
                            if (isNotHidden(info)) entries.add(info);
                        }
                    }
                }
            }
            sortZipEntries(entries, zipFile.getPath() + "!" + path);
            if (pane1) {
                currentPane1ZipEntries = entries;
                pane1Folder = zipFile;
                if (addToHistory) {
                    while (pane1History.size() > pane1HistoryIndex + 1) {
                        pane1History.remove(pane1History.size() - 1);
                    }
                    pane1History.add(new NavigationHistoryEntry(zipFile, true, path));
                    pane1HistoryIndex++;
                }
            } else {
                currentPane2ZipEntries = entries;
                pane2Folder = zipFile;
                if (addToHistory) {
                    while (pane2History.size() > pane2HistoryIndex + 1) {
                        pane2History.remove(pane2History.size() - 1);
                    }
                    pane2History.add(new NavigationHistoryEntry(zipFile, true, path));
                    pane2HistoryIndex++;
                }
            }

            setCurrentFolder(zipFile.getPath() + "!" + path, entries);
            ListView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            ZipEntryInfo finalParent = parent;
            handler.post(() -> {
                pane.setAdapter(new MainFilesArrayAdapter(this, entries.toArray(new ZipEntryInfo[0]), finalParent, pane1, true, path));
                updateNavigationButtons();
            });
        } catch (IOException e) {
            new ErrorUtil(this).showError(e);
        }
    }

    public void loadFolderInPane(File folder, boolean pane1) {
        loadFolderInPane(folder, pane1, true);
    }


   private androidx.activity.result.ActivityResultLauncher<String[]> filePicker;
   private java.util.function.Consumer<Uri> filePickerCallback;

    public void pickFile(String[] mimeTypes, java.util.function.Consumer<Uri> callback) {
       filePickerCallback = callback;
       filePicker.launch(mimeTypes);
   }

        public static class NavigationHistoryEntry {
        private final File file;
        private final boolean isZip;
        private final String zipPath;

        public NavigationHistoryEntry(File file, boolean isZip, String zipPath) {
            this.file = file;
            this.isZip = isZip;
            this.zipPath = zipPath;
        }

        public File getFile() {
            return file;
        }

        public boolean isZip() {
            return isZip;
        }

        public String getZipPath() {
            return zipPath;
        }
    }

    public void reloadCurrentFolder() {
        boolean isPane1 = lastPaneSelected == 1;
        loadFolderInPane(isPane1 ? pane1Folder : pane2Folder, isPane1);
    }

    public void setCurrentFolder(File curr, File[] files) {
        TextView currentFolderPath = findViewById(R.id.currentFolderPath);
        currentFolderPath.setText(curr.getPath());
        uiHelper.scrollTextView(currentFolderPath);
        if (files == null) {
            this.<TextView>findViewById(R.id.folderCount).setText("Folders: 0 Files: 0");
            return;
        }
        File[] folders = curr.listFiles(File::isDirectory);
        int foldersCount = folders == null ? 0 : folders.length;
        this.<TextView>findViewById(R.id.folderCount).setText(
                new StringBuilder("Folders: ").append(foldersCount).append(" Files: ")
                        .append(files.length - foldersCount));
    }

    public void setCurrentPane(int pane) {
        lastPaneSelected = pane;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
        if (adapter != null) {
            if (adapter.isInZip) {
                setCurrentFolder(adapter.currentZipPath, Arrays.asList(adapter.values));
            } else {
                File curr = pane == 1 ? pane1Folder : pane2Folder;
                setCurrentFolder(curr, curr.listFiles());
            }
        }
    }

    public void setCurrentFolder(String path, List<?> files) {
        new Thread(() -> {

            //CollectionsUtils.removeIf(files, (Predicate<Object>) o -> o instanceof ZipEntryInfo && ((ZipEntryInfo) o).isDirectory());
            int foldersCount = 0;
            for(Object item : files) {
                if(item instanceof ZipEntryInfo && ((ZipEntryInfo) item).isDirectory()) foldersCount++;
                else if (item instanceof File && ((File) item).isDirectory()) foldersCount++;
            }
            int finalFoldersCount = foldersCount;
            handler.post(() -> {
                TextView currentFolderPath = findViewById(R.id.currentFolderPath);
                currentFolderPath.setText(path);
                uiHelper.scrollTextView(currentFolderPath);
                this.<TextView>findViewById(R.id.folderCount).setText(
                        new StringBuilder("Folders: ").append(finalFoldersCount).append(" Files: ")
                                .append(files.size() - finalFoldersCount));
            });
        }).start();
    }

    public void setCurrentFolderFromSelected(File curr, Set<File> files) {
        TextView currentFolderPath = findViewById(R.id.currentFolderPath);
        currentFolderPath.setText(curr.getPath());
        uiHelper.scrollTextView(currentFolderPath);
        int foldersCount = 0;
        for (File file : files)
            if (file.isDirectory())
                foldersCount++;
        this.<TextView>findViewById(R.id.folderCount).setText(
                new StringBuilder("Folders: ").append(foldersCount).append(" Files: ")
                        .append(files.size() - foldersCount));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public static boolean areFilesDifferent(File[] files1, File[] files2) throws IOException {
        if (files1 == null || files2 == null)
            return files1 != files2;
        if (files1.length != files2.length - 1)
            return true;
        for (int i = 0; i < files1.length; i++) {
            if (!files1[i].exists() || !files2[i + 1].exists() || files1[i].length() != files2[i + 1].length()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {

        FileUtil.deleteDirectory(getCacheDir());
        super.onDestroy();
    }

    private void showSearchDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.search_dialog, null);
        AutoCompleteTextView searchQuery = dialogView.findViewById(R.id.searchQuery);
        ImageView searchHistoryDropdown = dialogView.findViewById(R.id.searchHistoryDropdown);
        CheckBox searchSubfolders = dialogView.findViewById(R.id.searchSubfolders);
        TextView advancedSearchToggle = dialogView.findViewById(R.id.advancedSearchToggle);
        LinearLayout advancedSearchLayout = dialogView.findViewById(R.id.advancedSearchLayout);
        CheckBox matchCase = dialogView.findViewById(R.id.matchCase);
        CheckBox useRegex = dialogView.findViewById(R.id.useRegex);
        EditText textInsideFile = dialogView.findViewById(R.id.textInsideFile);
        EditText minFileSize = dialogView.findViewById(R.id.minFileSize);
        EditText maxFileSize = dialogView.findViewById(R.id.maxFileSize);

        advancedSearchToggle.setOnClickListener(v -> {
            boolean isVisible = advancedSearchLayout.getVisibility() == View.VISIBLE;
            advancedSearchLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            advancedSearchToggle.setText(isVisible ? "Advanced Search ▼" : "Advanced Search ▲");
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String historyStr = prefs.getString("search_history", "");
        List<String> historyList = new ArrayList<>(Arrays.asList(historyStr.split("\n")));
        if (historyList.size() == 1 && historyList.get(0).isEmpty()) {
            historyList.clear();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                historyList);
        searchQuery.setAdapter(adapter);

        searchHistoryDropdown.setOnClickListener(v -> searchQuery.showDropDown());

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle("Search")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", null) // Prevent auto-dismiss
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String query = searchQuery.getText().toString();
                if (TextUtils.isEmpty(query)) {
                    Toast.makeText(this, "Enter a search query", Toast.LENGTH_SHORT).show();
                    return;
                }

                historyList.remove(query);
                historyList.add(0, query);
                if (historyList.size() > 20) {
                    historyList.remove(historyList.size() - 1);
                }
                prefs.edit().putString("search_history", TextUtils.join("\n", historyList)).apply();

                boolean subfolders = searchSubfolders.isChecked();
                boolean mCase = matchCase.isChecked();
                boolean regex = useRegex.isChecked();
                String textInside = textInsideFile.getText().toString();
                long minSize = -1;
                long maxSize = -1;
                try {
                    if (!TextUtils.isEmpty(minFileSize.getText()))
                        minSize = Long.parseLong(minFileSize.getText().toString());
                    if (!TextUtils.isEmpty(maxFileSize.getText()))
                        maxSize = Long.parseLong(maxFileSize.getText().toString());
                } catch (NumberFormatException ignored) {
                }

                dialog.dismiss();
                executeSearch(query, subfolders, mCase, regex, textInside, minSize, maxSize);
            });
        });
        dialogUtil.styleAlertDialog(dialog);
        dialog.show();
    }

    private void executeSearch(String query, boolean subfolders, boolean mCase, boolean regex, String textInside,
            long minSize, long maxSize) {
        boolean isPane1 = lastPaneSelected == 1;
        File startDir = isPane1 ? pane1Folder : pane2Folder;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
        if (adapter != null && adapter.isInZip) {
            Toast.makeText(this, "Search in ZIP not supported yet", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
        dialogUtil.styleAlertDialog(progressDialog);
        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
        progressText.setText("Searching...");
        final String finalQuery = query;

        new Thread(() -> {
            List<File> results = new ArrayList<>();
            java.util.regex.Pattern pattern = null;
            if (regex) {
                try {
                    pattern = java.util.regex.Pattern.compile(finalQuery,
                            mCase ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Invalid Regex", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
            } else if (!mCase) {
                // finalQuery = finalQuery.toLowerCase();
            }

            final java.util.regex.Pattern finalPattern = pattern;

            searchRecursive(startDir, results, mCase ? finalQuery : finalQuery.toLowerCase(), subfolders, mCase, regex,
                    finalPattern, textInside, minSize,
                    maxSize);

            handler.post(() -> {
                progressDialog.dismiss();
                if (results.isEmpty()) {
                    Toast.makeText(this, "No files found", Toast.LENGTH_SHORT).show();
                } else {
                    File[] resArray = results.toArray(new File[0]);
                    setCurrentFolder(startDir.getPath() + " (Search Results)", Arrays.asList(resArray));
                    ListView pane = findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                    pane.setAdapter(new MainFilesArrayAdapter(this, resArray, startDir, isPane1, false, null));
                }
            });
        }).start();
    }

    private void searchRecursive(File dir, List<File> results, String query, boolean subfolders, boolean mCase,
            boolean regex, java.util.regex.Pattern pattern, String textInside, long minSize, long maxSize) {
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File f : files) {
            boolean matchName = false;
            String name = f.getName();
            if (regex && pattern != null) {
                matchName = pattern.matcher(name).find();
            } else {
                matchName = mCase ? name.contains(query) : name.toLowerCase().contains(query);
            }

            boolean matchSize = true;
            if (f.isFile() && (minSize != -1 || maxSize != -1)) {
                long len = f.length();
                if (minSize != -1 && len < minSize)
                    matchSize = false;
                if (maxSize != -1 && len > maxSize)
                    matchSize = false;
            }

            boolean matchText = true;
            if (f.isFile() && !TextUtils.isEmpty(textInside)) {
                matchText = false;
                if (f.length() < 10485760) { // Limit to 10MB files to prevent OOM
                    try (BufferedReader br = new BufferedReader(new java.io.FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (mCase ? line.contains(textInside)
                                    : line.toLowerCase().contains(textInside.toLowerCase())) {
                                matchText = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (matchName && matchSize && matchText) {
                results.add(f);
            }

            if (subfolders && f.isDirectory()) {
                searchRecursive(f, results, query, subfolders, mCase, regex, pattern, textInside, minSize, maxSize);
            }
        }
    }

    private void showSettingsDialog() {
        android.content.SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        ScrollView settingsDialog = (ScrollView) LayoutInflater.from(MainActivity.this)
                .inflate(R.layout.settings_dialog, null);

        com.google.android.material.button.MaterialButtonToggleGroup themeButtons = settingsDialog
                .findViewById(R.id.themeToggleGroup);
        themeButtons.check(
                systemTheme ? R.id.systemThemeButton
                        : theme == R.style.Theme_MyApp_Light ? R.id.lightThemeButton
                                : theme == R.style.Theme_MyApp_Dark ? R.id.darkThemeButton
                                        : R.id.blackThemeButton);
        for (int i = 0; i < themeButtons.getChildCount(); i++) {
            View child = themeButtons.getChildAt(i);
            if (child instanceof com.google.android.material.button.MaterialButton) {
                child.setOnLongClickListener(v3 -> {
                    int buttonId = v3.getId();
                    if (buttonId == R.id.lightThemeButton) {
                        Toast.makeText(this, R.string.light_theme, Toast.LENGTH_SHORT).show();
                    } else if (buttonId == R.id.darkThemeButton) {
                        Toast.makeText(this, R.string.dark_theme, Toast.LENGTH_SHORT).show();
                    } else if (buttonId == R.id.blackThemeButton) {
                        Toast.makeText(this, R.string.black_theme, Toast.LENGTH_SHORT).show();
                    } else if (buttonId == R.id.systemThemeButton) {
                        Toast.makeText(this, R.string.system_theme, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }
        }

        themeButtons.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                systemTheme = false;
                if (checkedId == R.id.lightThemeButton) {
                    themeButtons.check(R.id.lightThemeButton);
                    theme = R.style.Theme_MyApp_Light;
                } else if (checkedId == R.id.darkThemeButton) {
                    themeButtons.findViewById(R.id.darkThemeButton);
                    theme = R.style.Theme_MyApp_Dark;
                } else if (checkedId == R.id.blackThemeButton) {
                    themeButtons.check(R.id.blackThemeButton);
                    theme = R.style.Theme_MyApp_Black;
                } else {
                    systemTheme = true;
                    themeButtons.check(R.id.systemThemeButton);
                    theme = ((getResources().getConfiguration().uiMode
                            & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                                    ? R.style.Theme_MyApp_Dark
                                    : R.style.Theme_MyApp_Light;
                }

                settings.edit().putInt("theme", theme).apply();
                setTheme(theme);
                recreate();
            }
        });

        android.widget.Button checkUpdateNow = settingsDialog.findViewById(R.id.checkUpdateNow);
        android.widget.CompoundButton updateSwitch = settingsDialog.findViewById(R.id.updateToggle);
        updateSwitch.setChecked(checkForUpdates);
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> checkUpdateNow
                .setVisibility((checkForUpdates = isChecked) ? View.GONE : View.VISIBLE));
        checkUpdateNow.setVisibility(checkForUpdates ? View.GONE : View.VISIBLE);
        checkUpdateNow.setOnClickListener(v1 -> MainActivity.this.checkForUpdates(true));

        android.widget.CompoundButton logSwitch = settingsDialog.findViewById(R.id.logToggle);
        logSwitch.setChecked(logEnabled);
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> logEnabled = isChecked);

        AlertDialog builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this)
                .setTitle("Settings")
                .setView(settingsDialog)
                .create();
        dialogUtil.styleAlertDialog(builder);
        builder.show();
    }

    private void setupFilterBar() {
        LinearLayout topBar = findViewById(R.id.topBar);
        EditText filterBar = new EditText(this);
        filterBar.setHint("Filter...");
        filterBar.setVisibility(View.GONE);
        filterBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterBar.setSingleLine(true);
        topBar.addView(filterBar, 2);

        filterBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (lastPaneSelected == 1)
                    currentPane1Filter = s.toString().toLowerCase();
                else
                    currentPane2Filter = s.toString().toLowerCase();
                applyFilterToCurrentPane();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    private void applyFilterToCurrentPane() {
        boolean isPane1 = lastPaneSelected == 1;
        String filter = isPane1 ? currentPane1Filter : currentPane2Filter;
        ListView pane = findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane.getAdapter();
        if (adapter == null)
            return;

        if (adapter.isInZip) {
            List<ZipEntryInfo> entries = isPane1 ? currentPane1ZipEntries : currentPane2ZipEntries;
            if (entries == null)
                return;
            List<ZipEntryInfo> filtered = new ArrayList<>();
            for (ZipEntryInfo e : entries) {
                if (e.getName().toLowerCase().contains(filter) || e.getName().equals("..")) {
                    filtered.add(e);
                }
            }
            pane.setAdapter(new MainFilesArrayAdapter(this, filtered.toArray(new ZipEntryInfo[0]), null, isPane1, true,
                    adapter.currentZipPath));
        } else {
            File[] files = isPane1 ? currentPane1Files : currentPane2Files;
            if (files == null)
                return;
            List<File> filtered = new ArrayList<>();
            for (File f : files) {
                if (f.getName().toLowerCase().contains(filter) || f.getName().equals("..")) {
                    filtered.add(f);
                }
            }
            pane.setAdapter(new MainFilesArrayAdapter(this, filtered.toArray(new File[0]),
                    (isPane1 ? pane1Folder : pane2Folder).getParentFile(), isPane1, false, null));
        }
    }

    private boolean isNotHidden(File f) {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showSystem = prefs.getBoolean("show_system_hidden", false);
        boolean showManual = prefs.getBoolean("show_manually_hidden", false);
        if (!showSystem && (f.isHidden() || f.getName().startsWith(".")))
            return false;
        if (!showManual) {
            java.util.Set<String> manualHidden = prefs.getStringSet("manually_hidden_files", new java.util.HashSet<>());
            if (manualHidden.contains(f.getPath()))
                return false;
        }
        return true;
    }

    private boolean isNotHidden(ZipEntryInfo e) {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showSystem = prefs.getBoolean("show_system_hidden", false);
        boolean showManual = prefs.getBoolean("show_manually_hidden", false);
        if (!showSystem && e.getName().startsWith("."))
            return false;
        if (!showManual) {
            java.util.Set<String> manualHidden = prefs.getStringSet("manually_hidden_files", new java.util.HashSet<>());
            if (manualHidden.contains(e.getFullPath()))
                return false;
        }
        return true;
    }

    private void showSortDialog() {
        boolean isPane1 = lastPaneSelected == 1;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
        if (adapter == null)
            return;
        String currentPath = adapter.isInZip ? adapter.currentZipPath
                : (isPane1 ? pane1Folder.getPath() : pane2Folder.getPath());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int sortBy = prefs.getInt("sort_by_" + currentPath, prefs.getInt("sort_by", 0));
        boolean reverse = prefs.getBoolean("sort_reverse_" + currentPath, prefs.getBoolean("sort_reverse", false));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        String[] sortOptions = { "Name", "Size", "Date", "Type" };
        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(this);
        for (int i = 0; i < sortOptions.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(sortOptions[i]);
            rb.setId(i);
            radioGroup.addView(rb);
        }
        radioGroup.check(sortBy);
        layout.addView(radioGroup);

        CheckBox cbReverse = new CheckBox(this);
        cbReverse.setText("Reverse order");
        cbReverse.setChecked(reverse);
        layout.addView(cbReverse);

        CheckBox cbOnlyThisFolder = new CheckBox(this);
        cbOnlyThisFolder.setText("Only for this folder");
        layout.addView(cbOnlyThisFolder);

        dialogUtil.getDialogBuilder()
                .setTitle("Sort")
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int selectedSort = radioGroup.getCheckedRadioButtonId();
                    boolean selectedReverse = cbReverse.isChecked();
                    SharedPreferences.Editor editor = prefs.edit();
                    if (cbOnlyThisFolder.isChecked()) {
                        editor.putInt("sort_by_" + currentPath, selectedSort);
                        editor.putBoolean("sort_reverse_" + currentPath, selectedReverse);
                    } else {
                        editor.putInt("sort_by", selectedSort);
                        editor.putBoolean("sort_reverse", selectedReverse);
                    }
                    editor.apply();
                    reloadCurrentFolder();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditHiddenFilesDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> manualHidden = prefs.getStringSet("manually_hidden_files", new HashSet<>());
        List<String> hiddenList = new ArrayList<>(manualHidden);

        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, hiddenList);
        listView.setAdapter(adapter);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle("Edit Hidden Files (Click to unhide)")
                .setView(listView)
                .setPositiveButton("Done", (d, w) -> reloadCurrentFolder())
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String path = hiddenList.get(position);
            hiddenList.remove(position);
            manualHidden.remove(path);
            prefs.edit().putStringSet("manually_hidden_files", new HashSet<>(manualHidden)).apply();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Unhidden: " + path, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    private void sortFiles(File[] files, String folderPath) {
        if (files == null)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int sortBy = prefs.getInt("sort_by_" + folderPath, prefs.getInt("sort_by", 0));
        boolean reverse = prefs.getBoolean("sort_reverse_" + folderPath, prefs.getBoolean("sort_reverse", false));
        Arrays.sort(files, (f1, f2) -> {
            int result = 0;
            switch (sortBy) {
                case 1:
                    result = Long.compare(f1.length(), f2.length());
                    break;
                case 2:
                    result = Long.compare(f1.lastModified(), f2.lastModified());
                    break;
                case 3:
                    String ext1 = getExt(f1.getName());
                    String ext2 = getExt(f2.getName());
                    result = ext1.compareToIgnoreCase(ext2);
                    if (result == 0)
                        result = f1.getName().compareToIgnoreCase(f2.getName());
                    break;
                default:
                    result = f1.getName().compareToIgnoreCase(f2.getName());
                    break;
            }
            if (f1.isDirectory() && !f2.isDirectory())
                return -1;
            if (!f1.isDirectory() && f2.isDirectory())
                return 1;
            return reverse ? -result : result;
        });
    }

    private void sortZipEntries(List<ZipEntryInfo> entries, String folderPath) {
        if (entries == null)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int sortBy = prefs.getInt("sort_by_" + folderPath, prefs.getInt("sort_by", 0));
        boolean reverse = prefs.getBoolean("sort_reverse_" + folderPath, prefs.getBoolean("sort_reverse", false));
        Collections.sort(entries, (e1, e2) -> {
            if (e1.getName().equals(".."))
                return -1;
            if (e2.getName().equals(".."))
                return 1;
            int result = 0;
            switch (sortBy) {
                case 1:
                    result = Long.compare(e1.getSize(), e2.getSize());
                    break;
                case 2:
                    result = Long.compare(e1.getLastModified(), e2.getLastModified());
                    break;
                case 3:
                    String ext1 = getExt(e1.getName());
                    String ext2 = getExt(e2.getName());
                    result = ext1.compareToIgnoreCase(ext2);
                    if (result == 0)
                        result = e1.getName().compareToIgnoreCase(e2.getName());
                    break;
                default:
                    result = e1.getName().compareToIgnoreCase(e2.getName());
                    break;
            }
            if (e1.isDirectory() && !e2.isDirectory())
                return -1;
            if (!e1.isDirectory() && e2.isDirectory())
                return 1;
            return reverse ? -result : result;
        });
    }

    private void forceShowIcons(PopupMenu popupMenu) {
        try {
            java.lang.reflect.Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);
            java.lang.reflect.Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon",
                    boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}