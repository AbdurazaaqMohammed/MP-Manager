package io.github.abdurazaaqmohammed.MPManager;

import static io.github.ratul.topactivity.utils.PermissionUtil.requestMissingPermissions;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.lilincpp.github.libezftp.EZFtpServer;
import com.lilincpp.github.libezftp.user.EZFtpUser;
import com.lilincpp.github.libezftp.user.EZFtpUserPermission;
import com.lilincpp.github.libezftp.IEZFtpServer;
import com.lilincpp.github.libezftp.EZFtpClient;
import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.EZFtpFile;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpForegroundService;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileHelper;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileManager;
import io.github.abdurazaaqmohammed.adapters.BookmarksAdapter;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.player.ImageViewerActivity;
import io.github.abdurazaaqmohammed.player.MediaPlayerActivity;
import io.github.abdurazaaqmohammed.player.MiniPlayerDialog;
import io.github.abdurazaaqmohammed.player.PlayerManager;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.PasswordEncryptor;
import io.github.abdurazaaqmohammed.utils.StorageUtil;
import io.github.codehasan.colorpicker.ServiceState;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.codehasan.colorpicker.services.ColorPickerService;
import io.github.codehasan.colorpicker.PreferencesDialogFragment;
import io.github.ratul.topactivity.manager.ServiceManager;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.services.PackageMonitoringService;
import io.github.ratul.topactivity.utils.PermissionUtil;

import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.View;
import android.view.GestureDetector;
import androidx.core.view.GestureDetectorCompat;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import android.content.res.Configuration;

import android.content.res.Resources;
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
import java.util.Collections;
import java.util.Locale;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.github.paul035.LocaleHelper;
import com.google.common.io.Files;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.compile.BuildOptions;
import com.reandroid.apkeditor.compile.Builder;
import com.reandroid.utils.StringsUtil;
import com.reandroid.utils.io.FileUtil;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

public class MainActivity extends AppCompatActivity {
    boolean logEnabled;
    private File homeDir1;
    private File homeDir2;
    private MediaProjectionManager mediaProjectionManager;
    private String lastVerChecked;
    public File pane1Folder;
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

    private File[] currentPane1Files;
    private File[] currentPane2Files;
    private List<ZipEntryInfo> currentPane1ZipEntries;
    private List<ZipEntryInfo> currentPane2ZipEntries;
    private String currentPane1Filter = "";
    private String currentPane2Filter = "";

    private MiniPlayerDialog miniPlayerDialog;
    private ProfileManager profileManager;
    private MaterialAutoCompleteTextView profileSpinner;
    private ImageButton profileManageButton;

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

    public void openImageViewer(String filePath) {
        ImageViewerActivity.open(this, filePath);
    }

    public void playMediaFile(String filePath) {
        boolean isVideo = filePath.endsWith(".mp4") || filePath.endsWith(".mkv") || filePath.endsWith(".avi")
                || filePath.endsWith(".mov") || filePath.endsWith(".webm") || filePath.endsWith(".3gp")
                || filePath.endsWith(".ts") || filePath.endsWith(".flv") || filePath.endsWith(".wmv");
        boolean useActivity = isVideo || PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("player_open_activity", false);
        if (useActivity) {
            MediaPlayerActivity.openAndPlay(this, filePath);
        } else {
            PlayerManager pm = PlayerManager.getInstance(this);
            pm.play(PlayerManager.buildMediaItem(this, filePath));
            if (miniPlayerDialog == null || !miniPlayerDialog.isShowing()) {
                miniPlayerDialog = new MiniPlayerDialog(this);
                miniPlayerDialog.show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.edit()
                .putString("bookmarks", bookmarks.toString())
                //.putString("keyPath", signatureKeyPath)
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
            Extensions.showMessage(this, "Storage perm needed as file manager");
        } else recreate();
    }


    public static boolean doesNotHaveStoragePerm(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? !Environment.isExternalStorageManager() : Build.VERSION.SDK_INT > 22 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 0) if (doesNotHaveStoragePerm(this)) {
            Extensions.showMessage(this, "Storage perm needed as file manager");
        } else recreate();
        else {
            boolean pane1 = lastPaneSelected == 1;
            if (requestCode == 11 && resultCode == RESULT_OK) {
                String dirToLoad = data.getStringExtra("dirToLoad");
                if (dirToLoad != null) {
                    loadFolderInPane(new File(dirToLoad), pane1);
                } else {
                    Uri path = data.getData();
                    if (path != null) loadFolderInPane(new File(path.toString()), pane1);
                }
            } else if(requestCode == 757) {
                Uri uri = data.getData();
                String path = uri.getPath();
                if(path.startsWith(getCacheDir().getPath())) {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                    String name = path.substring(path.lastIndexOf("/") + 1);
                    LinearLayout ll = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.item_modified_dialog, null);
                    File file = pane1 ? pane1Folder : pane2Folder;
                    String zipFileName = file.getName();
                    ll.<TextView>findViewById(R.id.modifiedText).setText(rss.getString(R.string.file_modified, name, (zipFileName.endsWith(".apk") ? "APK" : "ZIP")));
                    CheckBox autosign = ll.findViewById(R.id.autosign);
                    boolean[] sign = new boolean[1];
                    autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                    autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                    ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                        .setTitle("File modified")
                        .setView(ll)
                        .setPositiveButton("Yes", (dialog, which) -> {
                            dialog.dismiss();
                            ProgressManager pm = new ProgressManager(this, true).show();
                            pm.setText(rss.getString(R.string.adding, name));
                            new Thread(() -> {
                                try(ZipFile zf = new ZipFile(file)) {
                                    boolean dex = name.startsWith("classes") && name.endsWith(".dex");
                                    if(dex) {
                                        File modifiedFile = new File(path);
                                        File folder = modifiedFile.getParentFile();
                                        zf.addFiles(Arrays.asList(folder.listFiles((FilenameFilter) (dir, name1) -> name1.endsWith(".dex"))));
                                    } else {
                                        ZipParameters zp = new ZipParameters();
                                        boolean store = name.equals("AndroidManifest.xml") || name.equals("resources.arsc");
                                        zp.setCompressionMethod(store ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
                                        zf.addFile(path, zp);
                                    }
                                    if(sign[0]) new SignWrapper(
                                            settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", this).getPath()),
                                            PasswordEncryptor.decryptString(settings.getString("keyPass", "android")), settings.getBoolean("v1", true),
                                            settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false)).signApk(file);
                                    pm.dismiss();
                                    handler.post(() -> loadZipFolderInPane(file, ((MainFilesArrayAdapter) getCurrentPane().getAdapter()).currentZipPath, pane1, false));
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(this).showError(e);
                                }
                            }).start();
                        }).setNegativeButton(rss.getString(R.string.cancel), null);
                    builder.show();
                }
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

    public RecyclerView getCurrentPane() {
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

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };
    private boolean isServiceBound = false;
    private final androidx.activity.result.ActivityResultLauncher<Intent> colorPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ColorPickerService.class)
            .putExtra(ColorPickerService.EXTRA_RESULT_CODE, result.getResultCode())
            .putExtra(ColorPickerService.EXTRA_RESULT_DATA, result.getData());
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
        } else {
            Extensions.showMessage(this, "Screen capture permission needed for color picker");
        }});
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(theme = settings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);
        checkStoragePerm();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        String deviceLang = Locale.getDefault().getLanguage();
        boolean supportedLang = deviceLang.equals("ar") || deviceLang.equals("es") || deviceLang.equals("de")
                || deviceLang.equals("fr") || deviceLang.equals("in") || deviceLang.equals("it")
                || deviceLang.equals("pt-BR") || deviceLang.equals("ru") || deviceLang.equals("tr")
                || deviceLang.equals("uk") || deviceLang.equals("vi") || deviceLang.equals("zh-TW")
                || deviceLang.equals("pl") || deviceLang.equals("hu") || deviceLang.equals("ko");

        lang = settings.getString("lang", supportedLang ? deviceLang : "en");
        boolean useDeviceRss = lang.equals(deviceLang);
        rss = useDeviceRss ? getResources() : LocaleHelper.setLocale(this, lang).getResources();

        View main = findViewById(R.id.main);
        if (theme == R.style.Theme_MyApp_Black) main.setBackgroundColor(Color.BLACK);

        if (Build.VERSION.SDK_INT > 20) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int transparent = Color.TRANSPARENT;
            getWindow().setNavigationBarColor(transparent);
            getWindow().setStatusBarColor(transparent);
        }

        if (!LegacyUtils.supportsWriteExternalStorage) {
            // EdgeToEdge.enable(this);
            getWindow().setStatusBarContrastEnforced(true);
            getWindow().setNavigationBarContrastEnforced(true);
        }

        new Thread(() -> {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            Security.addProvider(new android.sun.security.provider.JavaKeyStoreProvider());
        }).start();

        requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> { });

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
            } catch (Exception ignored) { }
        }).start();

        lastVerChecked = settings.getString("lastVerChecked", null);

        handler = new Handler(Looper.getMainLooper());
        drawerLayout = findViewById(R.id.drawer_layout);
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bookmarks_drawer));
        bottomSheetBehavior.setPeekHeight(0, false); // animate=false, keeps it hidden
        bottomSheetBehavior.setHideable(true); // allows fully hidden state
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN); // truly hidden at start

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                isBookmarksDrawerOpen = (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_HALF_EXPANDED);
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

        LinearLayout container = findViewById(R.id.storageContainer);
        ListView sidebar = findViewById(R.id.sidebarList);
        String[] options = { "Extract APK", "FTP Server", "FTP Client", "Color Picker", "Layout Inspector", "Settings" };
        int[] icons = {R.drawable.apk_document_24px, R.drawable.cloud_upload_24px, R.drawable.cloud_download_24px, R.drawable.colorize_24px, R.drawable.ic_inspect, R.drawable.baseline_settings_24};
        sidebar.setAdapter(new ArrayAdapter<String>(this, R.layout.item_dropdown_option, options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_dropdown_option, parent, false);
                }

                convertView.<ImageView>findViewById(R.id.optionIcon).setImageResource(icons[position]);
                convertView.<TextView>findViewById(R.id.optionText).setText(options[position]);
                if(position == 3 && Build.VERSION.SDK_INT < 24) convertView.setVisibility(View.GONE);
                if(position == 4 && Build.VERSION.SDK_INT < 20) convertView.setVisibility(View.GONE); // Technically floating window works in sdk 19 but you can't exit the app with it
                return convertView;
            }
        });
        sidebar.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0:
                    startActivityForResult(new Intent(this, APKExtractorActivity.class), 11);
                    break;
                case 1:
                    showFtpServerDialog();
                    break;
                case 2:
                    showFtpClientDialog();
                    break;
                case 3:
                    if(Build.VERSION.SDK_INT < 24) return;
                    PreferencesDialogFragment dialogFragment = new PreferencesDialogFragment();
                    dialogFragment.show(getSupportFragmentManager(), "preferences_dialog");
                    handler.post(() -> {
                        AlertDialog ad = (AlertDialog) dialogFragment.requireDialog();
                        ((androidx.appcompat.widget.Toolbar) ad.findViewById(R.id.topAppBar)).setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.menu_github) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codehasan/ScreenColorPicker")));
                                return true;
                            }
                            return false;
                        });
                        boolean isRunning = ServiceState.getInstance().isRunning();
                        TextView button = ad.getButton(DialogInterface.BUTTON_POSITIVE);
                        button.setText(isRunning ? "Stop" : "Start");
                        button.setOnClickListener(v -> {
                            ad.dismiss();
                            if (isRunning) ServiceState.getInstance().stopColorPickerService(MainActivity.this);
                            else {
                                if (!Settings.canDrawOverlays(MainActivity.this)) {
                                    PermissionUtil.requestSystemOverlayPermission(MainActivity.this);
                                    return;
                                }

                                if (!Extensions.canShowNotification(MainActivity.this)) {
                                    PermissionUtil.requestNotificationPermission(MainActivity.this);
                                    return;
                                }

                                colorPickerLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
                            }
                        });
                    });
                    break;
                case 4:
                    if(Build.VERSION.SDK_INT < 20) return;
                    if (DataRepository.getInstance().getAppState().isRunning()) {
                        DataRepository.getInstance().updateStatus(false);
                        return;
                    }

                    if (!requestMissingPermissions(this)) return;

                    DataRepository.getInstance().updateStatus(true);
                    Intent intent = new Intent(this, PackageMonitoringService.class);
                    startService(intent);
                    bindService(intent, serviceConnection, BIND_AUTO_CREATE);
                    new ServiceManager(this).show();
                    DataRepository.getInstance().updateData(getPackageName(), this.getClass().getName());
                    break;
                case 5:
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

        logEnabled = settings.getBoolean("logEnabled", false);
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
        currentFolderView.setText(TextUtils.isEmpty(homeDir1Path) ? Environment.getExternalStorageDirectory().getPath() : homeDir1Path);
        currentFolderView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(this, ((TextView) v).getText());
            return false;
        });
        currentFolderView.setOnClickListener(v -> {

            View textInputLayout = LayoutInflater.from(this).inflate(R.layout.material_edittext, null);//new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            TextInputEditText input = textInputLayout.findViewById(R.id.m_et_edittext);
            input.setText(((TextView) v).getText());
            dialogUtil.getDialogBuilder()
                    .setTitle(R.string.path)
                    .setView(textInputLayout)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("OK", (dialog, which) -> {
                        File inputPath = new File(input.getText().toString());
                        if (inputPath.exists() && inputPath.isDirectory() || (!inputPath.exists() && inputPath.mkdirs())) {
                            boolean isPane1 = lastPaneSelected == 1;
                            if (isPane1)
                                pane1Folder = inputPath;
                            else
                                pane2Folder = inputPath;
                            loadFolderInPane(inputPath, isPane1);
                        } else {
                            Extensions.showMessage(MainActivity.this, "Failed to navigate to or create path " + inputPath);
                        }
                    }).show();
        });

        RecyclerView pane1 = findViewById(R.id.listViewPane1);
        RecyclerView pane2 = findViewById(R.id.listViewPane2);

        pane1.setLayoutManager(new LinearLayoutManager(this));
        pane2.setLayoutManager(new LinearLayoutManager(this));

        pane1.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && lastPaneSelected != 1) {
                lastPaneSelected = 1;
                RecyclerView.Adapter a = pane1.getAdapter();
                if (!(a instanceof MainFilesArrayAdapter)) return false;
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
                RecyclerView.Adapter a = pane2.getAdapter();
                if (!(a instanceof MainFilesArrayAdapter)) return false;
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
                            Extensions.showMessage(MainActivity.this, "Failed to create folder " + inputStr);
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
                                Extensions.showMessage(MainActivity.this, "Failed to create file " + inputStr);
                            }
                        } catch (IOException e) {
                            Extensions.showMessage(MainActivity.this, "Failed to create file " + inputStr);
                        }
                    }).create();
            ad.show();
            ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v2 -> {
                int selectionStart = input.getSelectionStart();
                int selectionEnd = input.getSelectionEnd();
                if (selectionStart != selectionEnd) {
                    input.getText().delete(selectionStart, selectionEnd);
                }
                CharSequence text = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                if (!TextUtils.isEmpty(text)) input.getText().insert(selectionStart, text);
            });
        });

        ImageView syncPaneButton = findViewById(R.id.syncPaneButton);
        syncPaneButton.setOnClickListener(v -> {
            RecyclerView.Adapter a = getCurrentPane().getAdapter();
            if(a instanceof MainFilesArrayAdapter) {
                if (lastPaneSelected == 1)
                    loadFolderInPane(pane2Folder = ((MainFilesArrayAdapter) a).isInZip ? pane1Folder.getParentFile() : pane1Folder, false);
                else
                    loadFolderInPane(pane1Folder = ((MainFilesArrayAdapter) a).isInZip ? pane2Folder.getParentFile() : pane2Folder, true);
            }
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

            RecyclerView.Adapter a = getCurrentPane().getAdapter();
            if ((a instanceof MainFilesArrayAdapter)) {
                MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
                MenuItem hideSel = hiddenMenu.add(0, 8, 0, "Hide selected files");
                hideSel.setEnabled(adapter != null && adapter.isMultiSelectMode());
                hiddenMenu.add(0, 9, 0, "Edit hidden files").setIcon(R.drawable.baseline_drive_file_rename_outline_24);
            }

            menu.add(0, 10, 0, "Add to bookmarks").setIcon(R.drawable.baseline_bookmark_24);
            menu.add(0, 11, 0, "Set as home folder").setIcon(R.drawable.baseline_home_24);
            menu.add(0, 12, 0, "Swap panes").setIcon(R.drawable.baseline_swap_horiz_24);
            menu.add(0, 13, 0, "Preferences").setIcon(R.drawable.baseline_settings_24);
            menu.add(0, 14, 0, "Exit").setIcon(R.drawable.baseline_exit_to_app_24);

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                switch (title) {
                    case "Refresh":
                        reloadCurrentFolder();
                        break;
                    case "Filter":
                        LinearLayout topBar = findViewById(R.id.topBar);
                        LinearLayout pathLayout = (LinearLayout) topBar.getChildAt(1);
                        EditText filterBar = (EditText) topBar.getChildAt(2);
                        if (pathLayout.getVisibility() == View.VISIBLE) {
                            pathLayout.setVisibility(View.GONE);
                            filterBar.setVisibility(View.VISIBLE);
                            filterBar.requestFocus();
                        } else {
                            pathLayout.setVisibility(View.VISIBLE);
                            filterBar.setVisibility(View.GONE);
                            filterBar.setText("");
                        }
                        break;
                    case "Search":
                        showSearchDialog();
                        break;
                    case "Select all":
                        if (a instanceof MainFilesArrayAdapter) ((MainFilesArrayAdapter) a).selectAll();
                        break;
                    case "Sort":
                        showSortDialog();
                        break;
                    case "Show system hidden files": {
                        boolean isChecked = !item.isChecked();
                        item.setChecked(isChecked);
                        prefs.edit().putBoolean("show_system_hidden", isChecked).apply();
                        reloadCurrentFolder();
                        break;
                    }
                    case "Show manually hidden files": {
                        boolean isChecked = !item.isChecked();
                        item.setChecked(isChecked);
                        prefs.edit().putBoolean("show_manually_hidden", isChecked).apply();
                        reloadCurrentFolder();
                        break;
                    }
                    case "Hide selected files":
                        Set<String> manualHidden = new HashSet<>(prefs.getStringSet("manually_hidden_files", new HashSet<>()));
                        for (Object obj : ((MainFilesArrayAdapter) a).getSelectedFiles()) {
                            if (obj instanceof File)
                                manualHidden.add(((File) obj).getPath());
                            else if (obj instanceof ZipEntryInfo)
                                manualHidden.add(((ZipEntryInfo) obj).getFullPath());
                        }
                        prefs.edit().putStringSet("manually_hidden_files", manualHidden).apply();
                        ((MainFilesArrayAdapter) a).clearSelection();
                        reloadCurrentFolder();
                        break;
                    case "Edit hidden files":
                        showEditHiddenFilesDialog();
                        break;
                    case "Add to bookmarks": {
                        boolean isPane1 = lastPaneSelected == 1;
                        addBookmark(isPane1 ? pane1Folder : pane2Folder);
                        Extensions.showMessage(MainActivity.this, "Added to bookmarks");
                        break;
                    }
                    case "Set as home folder": {
                        boolean isPane1 = lastPaneSelected == 1;
                        prefs.edit().putString(isPane1 ? "home1" : "home2", (isPane1 ? pane1Folder : pane2Folder).getPath())
                                .apply();
                        Extensions.showMessage(MainActivity.this, "Set as home folder");
                        break;
                    }
                    case "Swap panes":
                        File temp = pane1Folder;
                        pane1Folder = pane2Folder;
                        pane2Folder = temp;
                        loadFolderInPane(pane1Folder, true);
                        loadFolderInPane(pane2Folder, false);
                        break;
                    case "Preferences":
                        showSettingsDialog();
                        break;
                    case "Exit":
                        finishAffinity();
                        break;
                }
                return true;
            });
            forceShowIcons(popup);
            popup.show();
        });

        ImageView upButton = findViewById(R.id.upButton);
        upButton.setOnClickListener(v -> {
            boolean isPane1 = lastPaneSelected == 1;
            RecyclerView.Adapter a = getCurrentPane().getAdapter();
            if ((a instanceof MainFilesArrayAdapter)) {
                MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
                if (adapter.isInZip) {
                    File zipFile = isPane1 ? pane1Folder : pane2Folder;
                    if (TextUtils.isEmpty(adapter.currentZipPath)) {
                        if (zipFile.getParentFile() != null)
                            loadFolderInPane(zipFile.getParentFile(), isPane1);
                    } else {
                        File parentInZip = new File(adapter.currentZipPath).getParentFile();
                        loadZipFolderInPane(zipFile, parentInZip != null ? parentInZip.getPath() : "", isPane1, true);
                    }
                } else loadFolderInPane((File) adapter.getItem(0), isPane1);
            } else {
                ftpClient.getCurDirPath(new OnEZFtpCallBack<String>() {
                    @Override
                    public void onSuccess(String response) {
                        int startIndex = response.indexOf(File.separator);
                        int endIndex = response.lastIndexOf(File.separator);
                        fetchFtpDirAndLoad((startIndex == endIndex) ? File.separator : response.substring(0, endIndex), isPane1);
                    }

                    @Override
                    public void onFail(int code, String msg) {
                    }
                });
            }
        });
        setupFilterBar();

        handler.post(() -> {
            StorageUtil.populateStorageUI(this, container);
            File[] dir1Files = homeDir1.listFiles();
            if (dir1Files != null) {
                File[] folders = homeDir1.listFiles(File::isDirectory);
                int foldersCount = folders == null ? 0 : folders.length;
                MainActivity.this.<TextView>findViewById(R.id.folderCount).setText(
                        new StringBuilder("Folders: ").append(foldersCount).append(" Files: ")
                                .append(dir1Files.length - foldersCount));
            }
            loadFolderInPane(homeDir1, true);
            loadFolderInPane(homeDir2, false);
        });
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
        if (isSidebarDrawerOpen) closeSidebarDrawer();
        else if (isBookmarksDrawerOpen) closeBookmarksDrawer();
        else {
            ViewGroup topBar = findViewById(R.id.topBar);
            EditText filterBar = (EditText) topBar.getChildAt(2);
            if (filterBar.getVisibility() == View.VISIBLE) {
                topBar.getChildAt(1).setVisibility(View.VISIBLE);
                filterBar.setVisibility(View.GONE);
                filterBar.setText("");
            } else {
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if(a instanceof MainFilesArrayAdapter) {
                    MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
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
                } else ftpClient.getCurDirPath(new OnEZFtpCallBack<String>() {
                    @Override
                    public void onSuccess(String response) {
                        int startIndex = response.indexOf(File.separator);
                        int endIndex = response.lastIndexOf(File.separator);
                        fetchFtpDirAndLoad((startIndex == endIndex) ? File.separator : response.substring(0, endIndex), lastPaneSelected == 1);
                    }

                    @Override
                    public void onFail(int code, String msg) {
                    }
                });
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
        if (folder instanceof FTPFileWrapper) {
            loadFtpFolderInPane((FTPFileWrapper) folder, pane1);
            return;
        }
        if (folder.getName().endsWith(".zip")) {
            loadZipFolderInPane(folder, "", pane1, addToHistory);
            return;
        }
        File[] files = folder.listFiles(this::isNotHidden);
        if (files == null) {
            Extensions.showMessage(this, "Could not open folder " + folder.getName());
            return;
        }
        Arrays.sort(files);
        View buildButton = findViewById(R.id.build);
        boolean xml;
        boolean json = false;
        if(Arrays.binarySearch(files, new File(folder, "AndroidManifest.xml")) >= 0
                && (Arrays.binarySearch(files, new File(folder, "classes.dex")) >= 0 || Arrays.binarySearch(files, new File(folder, "classes")) >= 0 || Arrays.binarySearch(files, new File(folder, "smali")) >= 0)
                && ((xml = Arrays.binarySearch(files, new File(folder, "resources")) >= 0 || Arrays.binarySearch(files, new File(folder, "res")) >= 0)
                || (json = Arrays.binarySearch(files, new File(folder, "uncompressed-files.json")) >= 0)
                || (Arrays.binarySearch(files, new File(folder, "resources.arsc")) >= 0))) {
            buildButton.setVisibility(View.VISIBLE);
            boolean finalJson = json;
            buildButton.setOnClickListener(v1 -> {
                BuildOptions bo = new BuildOptions();
                LayoutInflater inflater = LayoutInflater.from(this);
                View content = inflater.inflate(R.layout.dialog_build_options_content, null, false);

                RadioGroup rgExtract = content.findViewById(R.id.rg_extract_native_libs);
                RadioGroup rgDexLib = content.findViewById(R.id.rg_dex_lib);

                CheckBox cbVrd = content.findViewById(R.id.cb_vrd);
                CheckBox cbNoCache = content.findViewById(R.id.cb_no_cache);
                CheckBox cbDexProfile = content.findViewById(R.id.cb_dex_profile);

                TextInputEditText etResDir = content.findViewById(R.id.et_res_dir);

                rgExtract.addView(uiHelper.makeRadioButton("manifest", "Default"));
                rgExtract.addView(uiHelper.makeRadioButton("none", "None"));
                rgExtract.addView(uiHelper.makeRadioButton("false", "False"));
                rgExtract.addView(uiHelper.makeRadioButton("true", "True"));
                UIHelper.selectRadioByValue(rgExtract, bo.extractNativeLibs != null ? bo.extractNativeLibs : "Default");

                rgDexLib.addView(uiHelper.makeRadioButton(BuildOptions.DEX_LIB_INTERNAL, "Internal (supports dex versions up to 042)"));
                rgDexLib.addView(uiHelper.makeRadioButton(BuildOptions.DEX_LIB_JF, "jf (supports dex versions 035 and below)"));
                UIHelper.selectRadioByValue(rgDexLib, bo.dexLib != null ? bo.dexLib : BuildOptions.DEX_LIB_INTERNAL);

                cbVrd.setChecked(bo.validateResDir);
                cbNoCache.setChecked(bo.noCache);
                cbDexProfile.setChecked(bo.dexProfile);

                if (bo.resDirName != null) etResDir.setText(bo.resDirName);
                SharedPreferences settings = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
                final boolean[] sign = new boolean[1];
                CheckBox autosign = content.findViewById(R.id.autosign);
                autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                content.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

                new MaterialAlertDialogBuilder(this)
                        .setTitle("Build Options")
                        .setView(content)
                        .setPositiveButton("OK", (dialog, which) -> {
                            ProgressManager pm = new ProgressManager(this, true).show();
                            bo.type = xml ? BuildOptions.TYPE_XML : finalJson ? BuildOptions.TYPE_JSON : BuildOptions.TYPE_RAW;
                            bo.extractNativeLibs = UIHelper.radioGroupValue(rgExtract, "manifest");
                            bo.dexLib = UIHelper.radioGroupValue(rgDexLib, BuildOptions.DEX_LIB_INTERNAL);
                            bo.validateResDir = cbVrd.isChecked();
                            bo.noCache = cbNoCache.isChecked();
                            bo.dexProfile = cbDexProfile.isChecked();
                            CharSequence resDirName = (etResDir.getText());
                            String resDir = TextUtils.isEmpty(resDirName) ? "" : resDirName.toString().trim();
                            bo.resDirName = resDir.isEmpty() ? null : resDir;
                            bo.inputFile = folder;
                            bo.outputFile = new File(folder, folder.getName() + ".apk");
                            new Thread(() -> {
                                try {
                                    APKLogger logger = pm.getLogger();
                                    new Builder(bo, logger).runCommand();
                                    logger.close();
                                    if(sign[0]) new SignWrapper(
                                            settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", this).getPath()),
                                            PasswordEncryptor.decryptString(settings.getString("keyPass", "android")), settings.getBoolean("v1", true),
                                            settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false)).signApk(bo.outputFile);
                                    pm.dismiss();
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(MainActivity.this).showError(e);
                                }
                            }).start();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        } else buildButton.setVisibility(View.GONE);

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
        RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
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

                List<FileHeader> fhs = zf.getFileHeaders();
                String prefix = parentPath; // already normalized with trailing slash if non-empty
                for (FileHeader fh : fhs) {
                    String entryPath = fh.getFileName().replace('\\','/');
                    if (!entryPath.startsWith(prefix) || entryPath.equals(prefix)) continue;
                    String rest = entryPath.substring(prefix.length()); // e.g., "subdir/file" or "file.txt" or "subdir/"
                    // direct child if rest has no further '/'
                    int nextSlash = rest.indexOf('/');
                    if (nextSlash == -1) {
                        // file directly inside current folder
                        ZipEntryInfo info = new ZipEntryInfo(fh, zipFile, path);
                        if (isNotHidden(info)) entries.add(info);
                    } else {
                        // it's inside a subdirectory; we should add a single synthetic directory entry for that subdir
                        String childDirName = rest.substring(0, nextSlash + 1); // include trailing slash
                        String childFullPath = prefix + childDirName; // full path of the child dir
                        // add only once: track seen dirs with a Set<String>
                        if (seenDirs.add(childFullPath)) {
                            FileHeader syntheticDir = new FileHeader();
                            syntheticDir.setFileName(childFullPath);
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
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
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
        if (files == null) this.<TextView>findViewById(R.id.folderCount).setText("Folders: 0 Files: 0");
        else {
            File[] folders = curr.listFiles(File::isDirectory);
            int foldersCount = folders == null ? 0 : folders.length;
            this.<TextView>findViewById(R.id.folderCount).setText(new StringBuilder("Folders: ").append(foldersCount).append(" Files: ").append(files.length - foldersCount));
        }
    }

    public void setCurrentPane(int pane) {
        lastPaneSelected = pane;
        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        boolean b = a instanceof MainFilesArrayAdapter;
        findViewById(R.id.syncPaneButton).setEnabled(b);
        if (b) {
            MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
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
        if (isServiceBound) {
            getApplicationContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        super.onDestroy();
    }

    private void showSearchDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null);
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
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String query = searchQuery.getText().toString();
                if (TextUtils.isEmpty(query)) {
                    Extensions.showMessage(this, "Enter a search query");
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

        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) {
            Extensions.showMessage(this, "Search in FTP not supported yet");
            return;
        }
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
        if(adapter.isInZip) {
            Extensions.showMessage(this, "Search in ZIP not supported yet");
            return;
        }

        ProgressManager pm = new ProgressManager(this, true).show();
        pm.setText(rss.getString(R.string.searching));
        final String finalQuery = query;

        new Thread(() -> {
            List<File> results = new ArrayList<>();
            Pattern pattern = null;
            if (regex) try {
                pattern = Pattern.compile(finalQuery, mCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                handler.post(() -> {
                    pm.dismiss();
                    Extensions.showMessage(this, "Invalid Regex");
                });
                return;
            }

            final Pattern finalPattern = pattern;

            searchRecursive(startDir, results, mCase ? finalQuery : finalQuery.toLowerCase(), subfolders, mCase, regex,
                    finalPattern, textInside, minSize,
                    maxSize);

            pm.dismiss();
            handler.post(() -> {
                if (results.isEmpty()) Extensions.showMessage(this, "No files found");
                else {
                    File[] resArray = results.toArray(new File[0]);
                    setCurrentFolder(startDir.getPath() + " (Search Results)", Arrays.asList(resArray));
                    RecyclerView pane = findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                    pane.setAdapter(new MainFilesArrayAdapter(this, resArray, startDir, isPane1, false, null));
                }
            });
        }).start();
    }

    private void searchRecursive(File dir, List<File> results, String query, boolean subfolders, boolean mCase,
            boolean regex, Pattern pattern, String textInside, long minSize, long maxSize) {
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File f : files) {
            boolean matchName;
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
                    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
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
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        ScrollView settingsDialog = (ScrollView) LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_settings, null);

        MaterialButtonToggleGroup themeButtons = settingsDialog.findViewById(R.id.themeToggleGroup);
        themeButtons.check(
                systemTheme ? R.id.systemThemeButton
                        : theme == R.style.Theme_MyApp_Light ? R.id.lightThemeButton
                                : theme == R.style.Theme_MyApp_Dark ? R.id.darkThemeButton
                                        : R.id.blackThemeButton);
        for (int i = 0; i < themeButtons.getChildCount(); i++) {
            View child = themeButtons.getChildAt(i);
            if (child instanceof MaterialButton) {
                child.setOnLongClickListener(v3 -> {
                    int buttonId = v3.getId();
                    if (buttonId == R.id.lightThemeButton) {
                        Extensions.showMessage(this, R.string.light_theme);
                    } else if (buttonId == R.id.darkThemeButton) {
                        Extensions.showMessage(this, R.string.dark_theme);
                    } else if (buttonId == R.id.blackThemeButton) {
                        Extensions.showMessage(this, R.string.black_theme);
                    } else if (buttonId == R.id.systemThemeButton) {
                        Extensions.showMessage(this, R.string.system_theme);
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
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                                    ? R.style.Theme_MyApp_Dark
                                    : R.style.Theme_MyApp_Light;
                }

                settings.edit().putInt("theme", theme).apply();
                setTheme(theme);
                recreate();
            }
        });

        CompoundButton logSwitch = settingsDialog.findViewById(R.id.logToggle);
        logSwitch.setChecked(logEnabled);
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("logEnabled", logEnabled = isChecked).apply());

        CompoundButton playerModeSwitch = settingsDialog.findViewById(R.id.playerModeToggle);
        playerModeSwitch.setChecked(settings.getBoolean("player_open_activity", false));
        playerModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("player_open_activity", isChecked).apply());

        CheckBox autosign = settingsDialog.findViewById(R.id.autosign);
        autosign.setChecked(settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", isChecked).apply());
        settingsDialog.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
        settingsDialog.findViewById(R.id.about).setOnClickListener(v -> {
            String[] projects = new String[] {"MP Manager", "Open Source Projects Used", "APKEditor", "ApkCloner", "Sora Editor", "zip4j", "aXML", "Commons Collections", "material-design-icons", "android-filepicker", "java-diff-utils", "guava"};
            String[] authors = new String[] {"AbdurazaaqMohammed", "", "REAndroid", "developer-krushna", "Rosemoe", "srikanth-lingala", "apk-editor", "apache", "google", "singhangadin", "java-diff-utils", "google"};
            String[] copyright = new String[] {"", "", "2022 github.com/REAndroid", "2025 Krushna Chandra", "2020-2026  Rosemoe", "2026 srikanth-lingala", "2023-2026 APK Explorer & Editor <apkeditor@protonmail.com>", "2001-2026 The Apache Software Foundation", "2026 Google", "2016 Angad Singh", "2026 java-diff-utils", "2026 Google"};
            String[] strings = new String[projects.length];
            for (int i = 0, projectsLength = projects.length; i < projectsLength; i++) {
                String project = projects[i];
                String author = authors[i];
                strings[i] = i == 1 ? project : (author + " - " + project);
            }
            new MaterialAlertDialogBuilder(this).setTitle("About").setItems(strings, (dialog, which) -> {
                if(which == 1) return;
                String license = "";
                try {
                    license = Files.asCharSource(FileUtils.copyFileFromAssetsAndGetFile(which == 4 ? "LGPL-2.1.txt" : which == 6 ? "GPL-3.0+.txt" : "Apache-2.0.txt", this), StandardCharsets.UTF_8).read();
                } catch (Exception ignored) {}
                new MaterialAlertDialogBuilder(this).setTitle(projects[which]).setMessage(which == 0 ? "Version 0.1\nA free dual pane, Material Design file manager for Android with focus on APKs and the goal to be an open source alternative to MT Manager" : license.replaceFirst("[<\\[](?:yyyy|year)[]>]\\s+[\\[<]name of (?:author|copyright owner)[>\\]]", copyright[which])).setPositiveButton("GitHub", (dialog1, which1) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + authors[which] + '/' + projects[which].toLowerCase().replace(' ', '-')));
                    startActivity(intent);
                }).setNegativeButton(rss.getString(R.string.cancel), null).show();
            }).show();
        });
        new MaterialAlertDialogBuilder(this).setTitle("Settings").setView(settingsDialog).show();
    }

    private void setupFilterBar() {
        LinearLayout topBar = findViewById(R.id.topBar);
        EditText filterBar = new EditText(this);
        filterBar.setHint("Filter...");
        filterBar.setVisibility(View.GONE);
        filterBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterBar.setSingleLine(true);
        topBar.addView(filterBar, 2);

        filterBar.addTextChangedListener(new TextWatcher() {
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
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void applyFilterToCurrentPane() {
        boolean isPane1 = lastPaneSelected == 1;
        String filter = isPane1 ? currentPane1Filter : currentPane2Filter;
        RecyclerView pane = findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);

        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane.getAdapter();

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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showSystem = prefs.getBoolean("show_system_hidden", false);
        boolean showManual = prefs.getBoolean("show_manually_hidden", false);
        return (showSystem || (!f.isHidden() && !f.getName().startsWith("."))) && (showManual || !prefs.getStringSet("manually_hidden_files", new HashSet<>()).contains(f.getPath()));
    }

    private boolean isNotHidden(ZipEntryInfo e) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showSystem = prefs.getBoolean("show_system_hidden", false);
        boolean showManual = prefs.getBoolean("show_manually_hidden", false);
        return (showSystem || !e.getName().startsWith(".")) && (showManual || !prefs.getStringSet("manually_hidden_files", new HashSet<>()).contains(e.getFullPath()));
    }

    private void showSortDialog() {
        boolean isPane1 = lastPaneSelected == 1;
        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
        String currentPath = adapter.isInZip ? adapter.currentZipPath : (isPane1 ? pane1Folder.getPath() : pane2Folder.getPath());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int sortBy = prefs.getInt("sort_by_" + currentPath, prefs.getInt("sort_by", 0));
        boolean reverse = prefs.getBoolean("sort_reverse_" + currentPath, prefs.getBoolean("sort_reverse", false));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        String[] sortOptions = { "Name", "Size", "Date", "Type" };
        RadioGroup radioGroup = new RadioGroup(this);
        for (int i = 0; i < sortOptions.length; i++) {
            RadioButton rb = new RadioButton(this);
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
            HashSet<String> values = new HashSet<>(manualHidden);
            values.remove(path);
            prefs.edit().putStringSet("manually_hidden_files", values).apply();
            adapter.notifyDataSetChanged();
            Extensions.showMessage(this, "Unhidden: " + path);
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
            int result;
            switch (sortBy) {
                case 1:
                    result = Long.compare(f1.length(), f2.length());
                    break;
                case 2:
                    result = Long.compare(f1.lastModified(), f2.lastModified());
                    break;
                case 3:
                    String ext1 = this.getExt(f1.getName());
                    String ext2 = this.getExt(f2.getName());
                    result = ext1.compareToIgnoreCase(ext2);
                    if (result == 0) result = f1.getName().compareToIgnoreCase(f2.getName());
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
            int result;
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
                    if (result == 0) result = e1.getName().compareToIgnoreCase(e2.getName());
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
            Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);
            Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon",
                    boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static IEZFtpServer ftpServer;

    private void showFtpServerDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ftp_server, null);
        FrameLayout container = view.findViewById(R.id.container);
        View header = LayoutInflater.from(this).inflate(R.layout.dialog_ftp_server_header, container, false);
        container.addView(header, 0);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        String ipString = Formatter.formatIpAddress(ipAddress);
        TextView ipTv = view.findViewById(R.id.ip);
        ipTv.setText(rss.getString(R.string.ip, ipString));
        ipTv.setOnLongClickListener(v -> {
            ((android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
            Extensions.showMessage(this, rss.getString(R.string.copied));
            return false;
        });
        view.findViewById(R.id.copy).setOnClickListener(v -> {
            ((android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
            Extensions.showMessage(this, rss.getString(R.string.copied));
        });
        view.findViewById(R.id.share).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, ipString).setType("text/plain")));
        EditText portInput = view.findViewById(R.id.portInput);
        EditText userInput = view.findViewById(R.id.userInput);
        EditText passInput = view.findViewById(R.id.passInput);
        profileSpinner = header.findViewById(R.id.profile_spinner);
        profileManageButton = header.findViewById(R.id.manage_profiles);
        new ProfileHelper(this, null, portInput, userInput, passInput, profileSpinner, profileManageButton).setupProfileSpinner(true);

        boolean serverNotStarted = ftpServer == null;
        portInput.setEnabled(serverNotStarted);
        userInput.setEnabled(serverNotStarted);
        passInput.setEnabled(serverNotStarted);
        View pl = header.findViewById(R.id.profile_layout);
        pl.setEnabled(serverNotStarted);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            Extensions.showMessage(this, "Please allow notifications to show FTP server running");
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            /*new MaterialAlertDialogBuilder(this)
                .setTitle("Notifications")
                .setMessage("Please allow notifications to show FTP server running")
                .setPositiveButton("Allow", (dialog, which) -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                .setOnDismissListener(dialog -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                .show();*/
        }

        // This is very important to have notification so user remember that the FTP server is running and can stop it easily and should be shown always not just if dialog or app closed
        Intent serviceIntent = new Intent(this, FtpForegroundService.class);
        serviceIntent.putExtra("io.github.abdurazaaqmohammed.MPManager.ip", ipString);

        AlertDialog ad = dialogUtil.getDialogBuilder()
                .setTitle("FTP Server")
                .setView(view)
                .setOnDismissListener(null)
                .setPositiveButton(serverNotStarted ? "Start" : "Stop", null)
                .show();
                ad.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    TextView tv = (TextView) v;
                    boolean wasStarted = "Stop".equals(tv.getText().toString());
                    tv.setText(wasStarted ? "Start" : "Stop");
                    portInput.setEnabled(wasStarted);
                    userInput.setEnabled(wasStarted);
                    passInput.setEnabled(wasStarted);
                    pl.setEnabled(serverNotStarted);
                    if(wasStarted) {
                        if(ftpServer != null) ftpServer.stop();
                        ftpServer = null;
                        stopService(serviceIntent);
                        Extensions.showMessage(MainActivity.this, "FTP Server stopped");
                    } else {
                        int port = Integer.parseInt(portInput.getText().toString());
                        String user = userInput.getText().toString();
                        String pass = passInput.getText().toString();

                        try {
                            ftpServer = new EZFtpServer.Builder()
                                    .setListenPort(port)
                                    .addUser(new EZFtpUser(user, pass, Environment.getExternalStorageDirectory().getPath(), EZFtpUserPermission.WRITE))
                                    .create();
                            ftpServer.start();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent);
                            } else {
                                startService(serviceIntent);
                            }
                            Extensions.showMessage(MainActivity.this, "FTP Server started on port " + port);
                        } catch (Exception e) {
                            stopService(serviceIntent);
                            portInput.setEnabled(true);
                            userInput.setEnabled(true);
                            passInput.setEnabled(true);
                            pl.setEnabled(true);
                            if(ftpServer != null) ftpServer.stop();
                            tv.setText("Start");
                            e.printStackTrace();
                            Extensions.showMessage(MainActivity.this, "Failed to start FTP server: " + e.getMessage());
                        }
                        BroadcastReceiver ftpStopReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                if (ad.isShowing()) {
                                    tv.setText("Start");
                                    portInput.setEnabled(true);
                                    userInput.setEnabled(true);
                                    passInput.setEnabled(true);
                                    header.setEnabled(true);
                                }
                                unregisterReceiver(this);
                            }
                        };
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            registerReceiver(ftpStopReceiver, new IntentFilter("io.github.abdurazaaqmohammed.FTP_STOPPED"), Context.RECEIVER_NOT_EXPORTED);
                        } else registerReceiver(ftpStopReceiver, new IntentFilter("io.github.abdurazaaqmohammed.FTP_STOPPED"));
                    }
                });
    }

    private void showFtpClientDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ftp_client, null);
        EditText ipInput = view.findViewById(R.id.ipInput);
        EditText portInput = view.findViewById(R.id.portInput);
        EditText userInput = view.findViewById(R.id.userInput);
        EditText passInput = view.findViewById(R.id.passInput);
        FrameLayout container = view.findViewById(R.id.container);
        View header = LayoutInflater.from(this).inflate(R.layout.dialog_ftp_client_header, container, false);
        container.addView(header, 0);

        profileSpinner = header.findViewById(R.id.profile_spinner);
        profileManageButton = header.findViewById(R.id.manage_profiles);

        new ProfileHelper(this, ipInput, portInput, userInput, passInput, profileSpinner, profileManageButton).setupProfileSpinner(false);
        AlertDialog ad = dialogUtil.getDialogBuilder()
                .setTitle("FTP Client")
                .setView(view)
                .setPositiveButton("Connect", (dialog, which) -> {

                    String ip = ipInput.getText().toString().trim();
                    int port = Integer.parseInt(portInput.getText().toString());
                    String user = userInput.getText().toString();
                    String pass = passInput.getText().toString();

                    ftpClient = new EZFtpClient();
                    ftpClient.connect(ip, port, user, pass, new OnEZFtpCallBack<Void>() {
                        @Override
                        public void onSuccess(Void response) {
                            runOnUiThread(() -> {
                                Extensions.showMessage(MainActivity.this, "Connected to FTP");
                                fetchFtpDirAndLoad(File.separator, lastPaneSelected == 1);
                            });
                        }

                        @Override
                        public void onFail(int code, String msg) {
                            runOnUiThread(() -> Extensions.showMessage(MainActivity.this, "FTP Connect Failed: " + msg));
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private IEZFtpClient ftpClient;
    
    public void fetchFtpDirAndLoad(String path, boolean pane1) {
        if (ftpClient == null || !ftpClient.isConnected()) return;
        
        ftpClient.changeDirectory(path, new OnEZFtpCallBack<String>() {
            @Override
            public void onSuccess(String newPath) {
                ftpClient.getCurDirFileList(new OnEZFtpCallBack<List<EZFtpFile>>() {
                    @Override
                    public void onSuccess(List<EZFtpFile> response) {
                        runOnUiThread(() -> {
                            List<FTPFileWrapper> files = new ArrayList<>();
                            files.add(new FTPFileWrapper(newPath, new EZFtpFile("..", "",0, 0, new Date())));
                            int foldersCount = 0;
                            //int filesCount = 0;
                            if (response != null) {
                                for (EZFtpFile f : response) {
                                    if (f.getType() == EZFtpFile.TYPE_DIRECTORY) foldersCount++;
                                    //else filesCount++;
                                    files.add(new FTPFileWrapper(newPath, f));
                                }
                            }
                            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                            pane.setAdapter(new FtpFilesArrayAdapter(MainActivity.this, files.toArray(new FTPFileWrapper[0]), pane1, ftpClient));
                            TextView currentFolderPath = findViewById(R.id.currentFolderPath);
                            currentFolderPath.setText(rss.getString(R.string.ftp, path));
                            uiHelper.scrollTextView(currentFolderPath);

                            MainActivity.this.<TextView>findViewById(R.id.folderCount).setText(new StringBuilder("Folders: ").append(foldersCount).append(" Files: ").append(response.size() - foldersCount));
                        });
                    }
                    @Override
                    public void onFail(int code, String msg) { }
                });
            }
            @Override
            public void onFail(int code, String msg) { }
        });
    }

    private void loadFtpFolderInPane(FTPFileWrapper folder, boolean pane1) {
        if (folder.getName().equals("..")) {
            if (ftpClient != null) {
                ftpClient.getCurDirPath(new OnEZFtpCallBack<String>() {
                    @Override
                    public void onSuccess(String response) {
                        int startIndex = response.indexOf(File.separator);
                        int endIndex = response.lastIndexOf(File.separator);
                        fetchFtpDirAndLoad((startIndex == endIndex) ? File.separator : response.substring(0, endIndex), lastPaneSelected == 1);
                    }

                    @Override
                    public void onFail(int code, String msg) {
                    }
                });
            }
        } else if (folder.isDirectory()) {
            fetchFtpDirAndLoad(folder.getFtpFile().getName(), pane1);
        } else {
            Extensions.showMessage(this, "FTP File Download coming soon");
        }
    }
}