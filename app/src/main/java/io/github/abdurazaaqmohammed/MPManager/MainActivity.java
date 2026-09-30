package io.github.abdurazaaqmohammed.MPManager;

import static io.github.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RotateDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.sun.security.provider.JavaKeyStoreProvider;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.core.ui.UIKit;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;
import io.github.abdurazaaqmohammed.core.ui.theme.ActiveTheme;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.core.ui.util.PopupMenus;
import io.github.abdurazaaqmohammed.data.prefs.BookmarkStore;
import io.github.abdurazaaqmohammed.app.UpdateController;
import io.github.abdurazaaqmohammed.core.ui.util.ViewPagers;
import io.github.abdurazaaqmohammed.features.apk.ApkResultHandler;
import io.github.abdurazaaqmohammed.features.files.BookmarksController;
import io.github.abdurazaaqmohammed.features.files.FileSearchController;
import io.github.abdurazaaqmohammed.features.files.FtpController;
import io.github.abdurazaaqmohammed.features.files.MultiSelectController;
import io.github.abdurazaaqmohammed.features.files.SettingsController;
import io.github.abdurazaaqmohammed.features.files.NavigationHistoryEntry;
import io.github.abdurazaaqmohammed.features.files.PaneNavigationController;
import io.github.abdurazaaqmohammed.features.files.SidebarController;
import io.github.abdurazaaqmohammed.features.files.SortFilterController;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lilincpp.github.libezftp.EZFtpClient;
import com.lilincpp.github.libezftp.EZFtpFile;
import com.lilincpp.github.libezftp.EZFtpServer;
import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.IEZFtpServer;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import com.lilincpp.github.libezftp.user.EZFtpUser;
import com.lilincpp.github.libezftp.user.EZFtpUserPermission;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.compile.BuildOptions;
import com.reandroid.apkeditor.compile.Builder;
import com.reandroid.utils.StringsUtil;
import com.reandroid.utils.io.FileUtil;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpForegroundService;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpsCertificateUtil;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileHelper;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.adapters.BookmarksAdapter;
import io.github.abdurazaaqmohammed.adapters.SidebarAdapter;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.HistoryAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuCustomizer;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.player.ImageViewerActivity;
import io.github.abdurazaaqmohammed.player.MediaPlayerActivity;
import io.github.abdurazaaqmohammed.player.MiniPlayerDialog;
import io.github.abdurazaaqmohammed.player.PlayerManager;
import io.github.abdurazaaqmohammed.tools.StorageManagerActivity;
import io.github.abdurazaaqmohammed.tools.ToolsHubActivity;
import io.github.abdurazaaqmohammed.tools.WifiManagerActivity;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.ui.views.SortDirectionToggle;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootPermissionHelper;
import io.github.abdurazaaqmohammed.utils.SearchHistoryDropdown;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.StorageUtil;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.UpdateUtil;
import io.github.codehasan.colorpicker.PreferencesDialogFragment;
import io.github.codehasan.colorpicker.ServiceState;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.codehasan.colorpicker.services.ColorPickerService;
import io.github.ratul.topactivity.extensions.ActivityExtensions;
import io.github.ratul.topactivity.manager.ServiceManager;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.services.PackageMonitoringService;
import io.github.ratul.topactivity.utils.PermissionUtil;
import rikka.shizuku.Shizuku;

public class MainActivity extends BaseActivity implements PaneNavigationController.Host {
    boolean logEnabled;
    private File homeDir1;
    private File homeDir2;
    private MediaProjectionManager mediaProjectionManager;
    public File pane1Folder;
    public File pane2Folder;
    public int lastPaneSelected = 1;
    public DialogUtil dialogUtil;
    public UIHelper uiHelper;
    private final PaneNavigationController navigation = new PaneNavigationController(this);
    private HistoryAdapter historyAdapter;
    public String signatureKeyPath;
    private DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    public boolean isSidebarDrawerOpen;
    public boolean isBookmarksDrawerOpen;

    public Handler handler;
    private boolean systemTheme;
    public int theme;
    private boolean checkForUpdates;
    public String lastVerChecked;
    public long downloadId;
    private File[] currentPane1Files;
    private File[] currentPane2Files;
    private List<ZipEntryInfo> currentPane1ZipEntries;
    private List<ZipEntryInfo> currentPane2ZipEntries;
    private final SortFilterController sortFilter = new SortFilterController(this);
    private final FileSearchController fileSearch = new FileSearchController(this);
    private final BookmarkStore bookmarkStore = new BookmarkStore(this);
    private final SidebarController sidebar = new SidebarController(this);
    private final BookmarksController bookmarksUI = new BookmarksController(this);
    private final MultiSelectController multiSelect = new MultiSelectController(this);
    private final SettingsController settingsUI = new SettingsController(this);
    private final FtpController ftp = new FtpController(this);
    private final ApkResultHandler apkResults = new ApkResultHandler(this);
    private final UpdateController updates = new UpdateController(this);

    public ActivityResultLauncher<String> permissionLauncher() {
        return requestPermissionLauncher;
    }

    public boolean isSystemTheme() {
        return systemTheme;
    }

    public void setSystemTheme(boolean systemTheme) {
        this.systemTheme = systemTheme;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
        try {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean("logEnabled", logEnabled).apply();
        } catch (Exception ignored) {
        }
    }

    public boolean isCheckForUpdates() {
        return checkForUpdates;
    }

    public boolean setCheckForUpdates(boolean checkForUpdates) {
        this.checkForUpdates = checkForUpdates;
        return checkForUpdates;
    }

    public File getHomeDir1() {
        return homeDir1;
    }

    public void setHomeDir1(File homeDir1) {
        this.homeDir1 = homeDir1;
    }

    public File getHomeDir2() {
        return homeDir2;
    }

    public void setHomeDir2(File homeDir2) {
        this.homeDir2 = homeDir2;
    }

    public View.OnTouchListener bottomBarTouchListener() {
        return bottomBarTouchListener;
    }

    public File[] paneFiles(boolean pane1) {
        return pane1 ? currentPane1Files : currentPane2Files;
    }

    public List<ZipEntryInfo> paneZipEntries(boolean pane1) {
        return pane1 ? currentPane1ZipEntries : currentPane2ZipEntries;
    }

    private MiniPlayerDialog miniPlayerDialog;

    private boolean isBackPressedToExit;
    private final Runnable resetExitPrompt = () -> isBackPressedToExit = false;

    private GestureDetectorCompat bottomBarGestureDetector;
    private View.OnTouchListener bottomBarTouchListener;
    private ListView historyList;

    public ArrayList<File> getBookmarks() {
        return bookmarksUI.getBookmarks();
    }

    public void addBookmark(File toBookmark) {
        bookmarksUI.addBookmark(toBookmark);
    }

    public void openImageViewer(String filePath) {
        ImageViewerActivity.open(this, filePath);
    }

    public void playMediaFile(String filePath) {
        boolean isVideo = filePath.endsWith(".mp4") || filePath.endsWith(".mkv") || filePath.endsWith(".avi")
                || filePath.endsWith(".mov") || filePath.endsWith(".webm") || filePath.endsWith(".3gp")
                || filePath.endsWith(".ts") || filePath.endsWith(".flv") || filePath.endsWith(".wmv");
        boolean useActivity = isVideo || PreferenceManager.getDefaultSharedPreferences(this).getBoolean("player_open_activity", false);
        if (useActivity) MediaPlayerActivity.openAndPlay(this, filePath);
        else {
            PlayerManager pm = PlayerManager.getInstance(this);
            pm.play(PlayerManager.buildMediaItem(this, filePath));
            if (miniPlayerDialog == null || !miniPlayerDialog.isShowing()) (miniPlayerDialog = new MiniPlayerDialog(this)).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updates.unregister();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.edit()
                .putString("bookmarks", getBookmarks().toString())
                .putBoolean("systemTheme", systemTheme)
                .putBoolean("checkForUpdates", checkForUpdates)
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
            } else if (Build.VERSION.SDK_INT > 22) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (doesNotHaveStoragePerm(this)) Extensions.showMessage(this, R.string.storage_perm_needed);
            else recreate();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (io.github.abdurazaaqmohammed.ui.dialogs.ThemePickerDialog
                .handleImportResult(this, requestCode, resultCode, data)) {
            return;
        }
        if (requestCode == 9021) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && overlayImageCallback != null) {
                overlayImageCallback.onImagePicked(data.getData());
            }
            return;
        }
        if (resultCode == 0) if (doesNotHaveStoragePerm(this)) {
            Extensions.showMessage(this, R.string.storage_perm_needed);
        } else {
            // Editor was closed without returning a modified file (back press /
            // discard in ARSC, text or dex editors). Refresh the listing in place;
            // recreate() would drop the user back at the home folder.
            try {
                refreshPane(lastPaneSelected == 1);
            } catch (Exception ignored) {
            }
        }
        else {
            boolean pane1 = lastPaneSelected == 1;
            if (requestCode == 11 && resultCode == RESULT_OK) {
                String dirToLoad = data.getStringExtra("dirToLoad");
                if (dirToLoad != null) loadFolderInPane(new File(dirToLoad), pane1);
                else {
                    Uri path = data.getData();
                    if (path != null) loadFolderInPane(new File(path.toString()), pane1);
                }
            } else if(requestCode == 757) {
                if (data == null || data.getData() == null) return;
                handleModifiedFileResult(data.getData(), data.getStringExtra("zipEntryPath"), data.getStringExtra("zipFilePath"));
            }
        }
    }

    public void handleModifiedFileResult(Uri uri) {
        apkResults.handleModifiedFileResult(uri);
    }

    public void handleModifiedFileResult(Uri uri, String entryPath, String zipFileExtra) {
        apkResults.handleModifiedFileResult(uri, entryPath, zipFileExtra);
    }

    public void openBookmarksDrawer() {
        findViewById(R.id.bookmarks_drawer).post(() -> {
            showHistory(navigation.historyFor(lastPaneSelected == 1));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            isBookmarksDrawerOpen = true;
        });
    }

    public void closeBookmarksDrawer() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        isBookmarksDrawerOpen = false;
    }

    public void clearPaneSelection(boolean pane1) {
        multiSelect.clearPaneSelection(pane1);
    }

    public void setMultiSelectModeUI(boolean enabled) {
        multiSelect.setMultiSelectModeUI(enabled);
    }

    public void onPaneTouched(int pane) {
        multiSelect.onPaneTouched(pane);
    }




    private void setupSidebar() {
        sidebar.setupSidebar();
    }

    public List<String> getSidebarSectionOrder() {
        return sidebar.getSidebarSectionOrder();
    }

    public void refreshSidebar(List<String> sectionOrder) {
        sidebar.refreshSidebar(sectionOrder);
    }

    /** Sidebar shortcut: screen color picker service. Called by SidebarController. */
    public void openColorPickerTool() {
        if (Build.VERSION.SDK_INT < 24) return;
        PreferencesDialogFragment dialogFragment = new PreferencesDialogFragment();
        dialogFragment.show(getSupportFragmentManager(), "preferences_dialog");
        handler.post(() -> {
            AlertDialog ad = (AlertDialog) dialogFragment.requireDialog();
            ((Toolbar) ad.findViewById(R.id.topAppBar)).setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_github) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codehasan/ScreenColorPicker")));
                    return true;
                }
                return false;
            });
            boolean isRunning = ServiceState.getInstance().isRunning();
            TextView button = ad.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setText(isRunning ? getString(R.string.color_stop) : getString(R.string.color_start));
            button.setOnClickListener(v -> {
                ad.dismiss();
                if (isRunning) ServiceState.getInstance().stopColorPickerService(MainActivity.this);
                else {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        PermissionUtil.requestSystemOverlayPermission(MainActivity.this);
                        return;
                    }
                    if (!ActivityExtensions.isNotificationGranted(MainActivity.this)) {
                        requestNotificationPermission(() -> { });
                        return;
                    }
                    colorPickerLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
                }
            });
        });
    }

    /** Sidebar shortcut: layout inspector service. Called by SidebarController. */
    public void openLayoutInspectorTool() {
        if (Build.VERSION.SDK_INT < 20) return;
        if (DataRepository.getInstance().getAppState().isRunning()) {
            DataRepository.getInstance().updateStatus(false);
            return;
        }
        try {
            RootPermissionHelper.tryAutoGrantInspector(MainActivity.this);
        } catch (Exception ignored) {
        }
        if (!PermissionUtil.requestMissingPermissions(this, this::requestNotificationPermission)) return;
        DataRepository.getInstance().updateStatus(true);
        Intent intent = new Intent(this, PackageMonitoringService.class);
        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        new ServiceManager(this).show();
        DataRepository.getInstance().updateData(getPackageName(), this.getClass().getName());
    }

    private void requestNotificationPermission(Runnable onResult) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (onResult != null) onResult.run();
            return;
        }
        notificationPermissionContinuation = onResult;
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Bookmarks state, forwarded to BookmarksController (used by SidebarController). */
    public List<String> bookmarkGroups() {
        return bookmarksUI.bookmarkGroups();
    }

    public Map<String, String> bookmarkLabels() {
        return bookmarksUI.bookmarkLabels();
    }

    public ArrayList<File> groupBookmarks(String group) {
        return bookmarksUI.groupBookmarks(group);
    }

    public int findBookmarkIndex(String key, String path) {
        return bookmarksUI.findBookmarkIndex(key, path);
    }

    public void showBookmarkMenu(String key, int index, View anchor) {
        bookmarksUI.showBookmarkMenu(key, index, anchor);
    }

    public void moveBookmark(String key, String fromPath, String toPath) {
        bookmarksUI.moveBookmark(key, fromPath, toPath);
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
                    // BaseActivity re-applies the SYSTEM_DEFAULT plugin on recreate.
                    theme = R.style.Theme_MyApp_Dark;
                    recreate();
                }
            } else if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                if (theme != R.style.Theme_MyApp_Light) {
                    theme = R.style.Theme_MyApp_Light;
                    recreate();
                }
            }
        }
    }

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Runnable notificationPermissionContinuation;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Runnable continuation = notificationPermissionContinuation;
                notificationPermissionContinuation = null;
                if (continuation != null) continuation.run();
            });
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
    private java.util.function.Consumer<ActivityResult> pendingExternalFile;
    private java.util.function.Consumer<ActivityResult> pendingExternalSetting;
    private final ActivityResultLauncher<Intent> externalFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        java.util.function.Consumer<ActivityResult> cb = pendingExternalFile;
        pendingExternalFile = null;
        if (cb != null) {
            try {
                cb.accept(result);
            } catch (Exception ignored) {
            }
        }
    });
    private final ActivityResultLauncher<Intent> externalSettingLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        java.util.function.Consumer<ActivityResult> cb = pendingExternalSetting;
        pendingExternalSetting = null;
        if (cb != null) {
            try {
                cb.accept(result);
            } catch (Exception ignored) {
            }
        }
    });

    /** Launches an external file-plugin activity; result goes to cb (may be null). */
    public void launchExternalFile(Intent intent, java.util.function.Consumer<ActivityResult> cb) {
        try {
            pendingExternalFile = cb;
            externalFileLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalFile = null;
        }
    }

    /** Launches an external setting config activity; result goes to cb (may be null). */
    public void launchExternalSetting(Intent intent, java.util.function.Consumer<ActivityResult> cb) {
        try {
            pendingExternalSetting = cb;
            externalSettingLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalSetting = null;
        }
    }

    private final ActivityResultLauncher<Intent> colorPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ColorPickerService.class)
            .putExtra(ColorPickerService.EXTRA_RESULT_CODE, result.getResultCode())
            .putExtra(ColorPickerService.EXTRA_RESULT_DATA, result.getData());
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
        } else {
            Extensions.showMessage(this, rss.getString(R.string.screen_capture_permission_needed_for_color_picker));
        }});

    private void setupSystemBars() {
        // "main" owns the window-inset padding, so on API 35+ (where
        // setStatusBarColor is a no-op and the bar is transparent) its background
        // is what shows through behind the status bar. Make that the app-bar
        // colour. The bottom bar and the navigation bar stay on the surface
        // colour, which is what MT Manager paints them; the two list panes carry
        // the surface colour themselves.
        int chrome = ThemeAttrs.toolbar(this);
        int surface = ThemeAttrs.surface(this);
        findViewById(R.id.main).setBackgroundColor(chrome);
        findViewById(R.id.topBar).setBackgroundColor(chrome);
        findViewById(R.id.bottomBar).setBackgroundColor(surface);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sidebar_drawer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bookmarks_drawer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, v.getPaddingTop(), systemBars.right, systemBars.bottom);
            return insets;
        });

        boolean lightBars = ActiveTheme.isLight(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // No-op for targetSdk 35+, but still the only way on older releases.
            getWindow().setStatusBarColor(chrome);
            getWindow().setNavigationBarColor(surface);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && lightBars) {
                // Old devices can't render dark status bar icons; use a dark bar so icons stay visible
                int darkBar = ThemeAttrs.accent(this);
                getWindow().setStatusBarColor(darkBar);
                getWindow().setNavigationBarColor(darkBar);
            }
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    /**
     * One-time upgrade from legacy theme storage (boolean "systemTheme" +
     * int style-res "theme") to ThemeRegistry's string id. Runs before
     * BaseActivity applies the theme so first launch after update is correct.
     */
    private void upgradeThemePrefsIfNeeded() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.contains(ThemeRegistry.PREF_KEY)) return;
            String id;
            if (prefs.getBoolean("systemTheme", true)) {
                id = BuiltInThemes.SYSTEM_DEFAULT_ID;
            } else {
                int legacy = prefs.getInt("theme", 0);
                if (legacy == R.style.Theme_MyApp_Light) id = BuiltInThemes.LIGHT_ID;
                else if (legacy == R.style.Theme_MyApp_Black) id = BuiltInThemes.BLACK_ID;
                else id = BuiltInThemes.DARK_ID;
            }
            ThemeRegistry.setCurrentId(this, id);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        upgradeThemePrefsIfNeeded();
        super.onCreate(savedInstanceState);
        // BaseActivity applied the ThemeRegistry plugin; sync legacy fields for
        // status-bar tinting, icon colors and intent extras during transition.
        theme = ThemeRegistry.currentStyleRes(this);
        systemTheme = BuiltInThemes.SYSTEM_DEFAULT_ID.equals(ThemeRegistry.getCurrentId(this));
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        ShizukuFileOps.init(this);
        ShizukuShell.warmUp(this);
        //WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);
        setupSystemBars();
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
        rss = getResources();// /*useDeviceRss ? getResources() :*/ LocaleHelper.setLocale(this, Locale.getDefault().getLanguage()).getResources();

        new Thread(() -> {
            Security.addProvider(new BouncyCastleProvider());
            Security.addProvider(new JavaKeyStoreProvider());
        }).start();

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> { });

        new Thread(() -> {
            File frameworks = new File("/sdcard/MP Manager/frameworks/");
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
            setupPullToRefresh();
        }).start();

        handler = new Handler(Looper.getMainLooper());
        drawerLayout = findViewById(R.id.drawer_layout);
        View sidebarDrawer = findViewById(R.id.sidebar_drawer);

        if (sidebarDrawer.getBackground() instanceof GradientDrawable sidebarBackground) {
//            sidebarBackground.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK));
//            sidebarDrawer.setBackground(sidebarBackground);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sidebarDrawer.setClipToOutline(true);
        }
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

        ListView historyList = this.historyList = new ListView(this);
        historyList.setDivider(null);
        historyList.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        historyList.setOnTouchListener(bookmarksUI.getSwipeDownCloseListener());
        historyList.setAdapter(historyAdapter = new HistoryAdapter(this, navigation.historyFor(lastPaneSelected == 1)));
        historyList.setOnItemClickListener((parent, view, position, id) -> {
            NavigationHistoryEntry entry = historyAdapter.getItem(position);
            if (entry == null) return;
            navigation.jumpTo(entry, lastPaneSelected == 1);
            closeBookmarksDrawer();
        });

        bookmarksUI.setup(historyList);

        View.OnClickListener toggleSidebarDrawer = v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            else
                drawerLayout.openDrawer(GravityCompat.START);
        };

        findViewById(R.id.sidebarTitle).setOnClickListener(v -> uiHelper.showAboutDialog());
        setupSidebar();
        bottomBarGestureDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true; // MUST return true to receive subsequent events
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2,
                                           float velocityX, float velocityY) {
                        // upward movement (px)
                        if (e1 != null && e1.getY() - e2.getY() > 50 && velocityY < -200) { // negative = upward velocity
                            openBookmarksDrawer();
                            return true;
                        }
                        return false;
                    }
                });

        bottomBarTouchListener = (v, event) -> {
            bottomBarGestureDetector.onTouchEvent(event);
            return v.getId() == R.id.bottomBar;
        };

        LinearLayout bottomBar = findViewById(R.id.bottomBar);
        bottomBar.setOnTouchListener(bottomBarTouchListener);
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            bottomBar.getChildAt(i).setOnTouchListener(bottomBarTouchListener);
        }

        findViewById(R.id.hamburgerMenu).setOnClickListener(toggleSidebarDrawer);

        dialogUtil = new DialogUtil(this);
        uiHelper = new UIHelper(this);

        logEnabled = settings.getBoolean("logEnabled", false);
        systemTheme = BuiltInThemes.SYSTEM_DEFAULT_ID.equals(ThemeRegistry.getCurrentId(this));
        String homeDir1Path = settings.getString("home1", null);
        homeDir1 = TextUtils.isEmpty(homeDir1Path) ? Environment.getExternalStorageDirectory() : new File(homeDir1Path);
        String homeDir2Path = settings.getString("home2", null);
        homeDir2 = TextUtils.isEmpty(homeDir2Path) ? Environment.getExternalStorageDirectory() : new File(homeDir2Path);
        try {
            signatureKeyPath = settings.getString("keyPath", FileUtils.getDebugKeystore(this).getPath());
        } catch (IOException e) {
            new ErrorUtil(this).showError(e);
        }

        RecyclerView pane1 = findViewById(R.id.listViewPane1);
        RecyclerView pane2 = findViewById(R.id.listViewPane2);

        pane1.setLayoutManager(new LinearLayoutManager(this));
        pane2.setLayoutManager(new LinearLayoutManager(this));

        pane1.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (lastPaneSelected != 1) setCurrentPane(1);
                onPaneTouched(1);
            }
            return false;
        });



        pane2.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (lastPaneSelected != 2) setCurrentPane(2);
                onPaneTouched(2);
            }
            return false;
        });

        new Thread(() -> {
            TextView currentFolderView = findViewById(R.id.currentFolderPath);
            currentFolderView.setText(TextUtils.isEmpty(homeDir1Path) ? Environment.getExternalStorageDirectory().getPath() : homeDir1Path);
            currentFolderView.setOnLongClickListener(v -> {
                CopyUtil.copyToClipboard(this, ((TextView) v).getText());
                return false;
            });
            currentFolderView.setOnClickListener(v -> {
                View textInputLayout = LayoutInflater.from(this).inflate(R.layout.material_edittext, null);//new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
                EditText input = textInputLayout.findViewById(R.id.m_et_edittext);
                input.setText(((TextView) v).getText());
                AlertDialog ad = dialogUtil.getDialogBuilder()
                        .setTitle(R.string.path)
                        .setView(textInputLayout)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(android.R.string.paste, null) // Note: Need to set it after otherwise the dialog auto close
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            File inputPath = new File(input.getText().toString());
                            boolean canOpen = inputPath.exists() && inputPath.isDirectory() || (!inputPath.exists() && inputPath.mkdirs());
                            if (!canOpen) {
                                try {
                                    String abs = inputPath.getAbsolutePath();
                                    if (AccessManager.exists(this, abs) && RootManager.getInstance(this).isDirectory(abs)) {
                                        canOpen = true;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            if (canOpen) {
                                boolean isPane1 = lastPaneSelected == 1;
                                if (isPane1)
                                    pane1Folder = inputPath;
                                else
                                    pane2Folder = inputPath;
                                loadFolderInPane(inputPath, isPane1);
                            } else {
                                Extensions.showMessage(MainActivity.this, getString(R.string.navigate_create_failed, inputPath));
                            }
                        }).show();
                ad.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v2 -> {
                    int selectionStart = input.getSelectionStart();
                    int selectionEnd = input.getSelectionEnd();
                    if (selectionStart != selectionEnd) {
                        input.getText().delete(selectionStart, selectionEnd);
                    }
                    CharSequence text = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                    if (TextUtils.isEmpty(text)) Extensions.showMessage(this, rss.getString(R.string.nothing_found_to_paste));
                    else input.getText().insert(selectionStart, text);
                });
            });
            View addButton = findViewById(R.id.addButton);
            addButton.setOnLongClickListener(this::showMsgOnLongPress);
            addButton.setOnClickListener(v -> {
                if (multiSelect.isActive()) {
                    multiSelect.exitAll();
                    return;
                } else if(getCurrentPane().getAdapter() instanceof MainFilesArrayAdapter adapter && adapter.isInZip) {
                    adapter.setMultiSelectMode(true);
                    return;
                }
                View textInputLayout = LayoutInflater.from(this).inflate(R.layout.enter_name, null);
                EditText input = textInputLayout.findViewById(R.id.m_et_edittext);
                AlertDialog ad = dialogUtil.getDialogBuilder()
                        .setTitle(getString(R.string.create))
                        .setView(textInputLayout)
                        .setNegativeButton(rss.getString(R.string.folder), (dialog, which) -> {
                            boolean isPane1 = lastPaneSelected == 1;
                            File ogFolder = isPane1 ? pane1Folder : pane2Folder;
                            String inputStr = input.getText().toString();
                            if (new File(ogFolder, inputStr).mkdir()) loadFolderInPane(ogFolder, isPane1);
                            else if (mkdirViaRoot(ogFolder, inputStr)) loadFolderInPane(ogFolder, isPane1);
                            else Extensions.showMessage(MainActivity.this, rss.getString(R.string.failed_to_create_folder, inputStr));
                        })
                        .setNeutralButton(android.R.string.paste, null) // Note: Need to set it after otherwise the dialog auto close
                        .setPositiveButton(rss.getString(R.string.file), (dialog, which) -> {
                            boolean isPane1 = lastPaneSelected == 1;
                            File ogFolder = isPane1 ? pane1Folder : pane2Folder;
                            String inputStr = input.getText().toString();
                            try {
                                if (new File(ogFolder, inputStr).createNewFile()) loadFolderInPane(ogFolder, isPane1);
                                else if (touchViaRoot(ogFolder, inputStr)) loadFolderInPane(ogFolder, isPane1);
                                else Extensions.showMessage(MainActivity.this, rss.getString(R.string.failed_to_create_file, inputStr));
                            } catch (IOException e) {
                                if (touchViaRoot(ogFolder, inputStr)) loadFolderInPane(ogFolder, isPane1);
                                else Extensions.showMessage(MainActivity.this, rss.getString(R.string.failed_to_create_file, inputStr));
                            }
                        }).show();
                ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v2 -> {
                    CharSequence text = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                    if (TextUtils.isEmpty(text)) {
                        Extensions.showMessage(MainActivity.this, rss.getString(R.string.nothing_found_to_paste)); return;
                    }
                    int selectionStart = input.getSelectionStart();
                    int selectionEnd = input.getSelectionEnd();
                    if (selectionStart != selectionEnd) {
                        input.getText().delete(selectionStart, selectionEnd);
                        input.getText().insert(selectionStart, text);
                    } else if(selectionEnd == -1) { // Empty
                        input.setText(text);
                    }
                });
            });

            View syncPaneButton = findViewById(R.id.syncPaneButton);
            syncPaneButton.setOnLongClickListener(this::showMsgOnLongPress);
            syncPaneButton.setOnClickListener(v -> {
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if(a instanceof MainFilesArrayAdapter mainFilesArrayAdapter) {
                    if (lastPaneSelected == 1)
                        loadFolderInPane(pane2Folder = mainFilesArrayAdapter.isInZip ? pane1Folder.getParentFile() : pane1Folder, false);
                    else
                        loadFolderInPane(pane1Folder = mainFilesArrayAdapter.isInZip ? pane2Folder.getParentFile() : pane2Folder, true);
                }
            });

            View moreOptionsMenu = findViewById(R.id.moreOptionsMenu);
            moreOptionsMenu.setOnLongClickListener(this::showMsgOnLongPress);
            moreOptionsMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                Menu menu = popup.getMenu();
                menu.add(0, 0, 0, getString(R.string.menu_refresh)).setIcon(R.drawable.baseline_refresh_24);
                menu.add(0, 1, 0, getString(R.string.filter)).setIcon(R.drawable.baseline_filter_list_24);
                menu.add(0, 2, 0, getString(R.string.search)).setIcon(R.drawable.baseline_search_24);
                menu.add(0, 15, 0, getString(R.string.find_in_files)).setIcon(R.drawable.ic_search_replace);
                menu.add(0, 3, 0, getString(R.string.menu_select_all)).setIcon(R.drawable.baseline_select_all_24);
                menu.add(0, 4, 0, getString(R.string.sort)).setIcon(R.drawable.baseline_sort_24);

                SubMenu hiddenMenu = menu.addSubMenu(0, 5, 0, getString(R.string.menu_hidden_files));
                hiddenMenu.setIcon(R.drawable.visibility_off_24px);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                MenuItem sysItem = hiddenMenu.add(0, 6, 0, getString(R.string.menu_show_system_hidden));
                sysItem.setCheckable(true).setChecked(prefs.getBoolean("show_system_hidden", false));
                MenuItem manItem = hiddenMenu.add(0, 7, 0, getString(R.string.menu_show_manual_hidden));
                manItem.setCheckable(true).setChecked(prefs.getBoolean("show_manually_hidden", false));

                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if ((a instanceof MainFilesArrayAdapter)) {
                    MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) getCurrentPane().getAdapter();
                    MenuItem hideSel = hiddenMenu.add(0, 8, 0, getString(R.string.menu_hide_selected));
                    hideSel.setEnabled(adapter != null && adapter.isMultiSelectMode());
                    hiddenMenu.add(0, 9, 0, getString(R.string.edit_hidden_files)).setIcon(R.drawable.baseline_drive_file_rename_outline_24);
                }

                menu.add(0, 10, 0, getString(R.string.menu_add_bookmark)).setIcon(R.drawable.baseline_bookmark_24);
                menu.add(0, 11, 0, getString(R.string.set_as_home)).setIcon(R.drawable.baseline_home_24);
                menu.add(0, 12, 0, getString(R.string.menu_swap_panes)).setIcon(R.drawable.baseline_swap_horiz_24);
                menu.add(0, 13, 0, getString(R.string.preferences)).setIcon(R.drawable.baseline_settings_24);
                menu.add(0, 14, 0, getString(R.string.exit)).setIcon(R.drawable.baseline_exit_to_app_24);

                popup.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case 0:
                            reloadCurrentFolder();
                            break;
                        case 1:
                            LinearLayout topBar = findViewById(R.id.topBar);
                            LinearLayout pathLayout = (LinearLayout) topBar.getChildAt(1);
                            TextInputLayout filterBox = (TextInputLayout) topBar.getChildAt(2);
                            EditText filterBar = filterBox.getEditText();
                            if (pathLayout.getVisibility() == View.VISIBLE) {
                                pathLayout.setVisibility(View.GONE);
                                filterBox.setVisibility(View.VISIBLE);
                                if (filterBar != null) {
                                    filterBar.requestFocus();
                                    try {
                                        InputMethodManager imm =
                                                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                        if (imm != null) imm.showSoftInput(filterBar, InputMethodManager.SHOW_IMPLICIT);
                                    } catch (Exception ignored) {
                                    }
                                }
                            } else {
                                pathLayout.setVisibility(View.VISIBLE);
                                filterBox.setVisibility(View.GONE);
                                if (filterBar != null) filterBar.setText("");
                                try {
                                    InputMethodManager imm =
                                            (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.hideSoftInputFromWindow(topBar.getWindowToken(), 0);
                                } catch (Exception ignored) {
                                }
                            }
                            break;
                        case 2:
                            showSearchDialog();
                            break;
                        case 15:
                            showFindInFilesDialog();
                            break;
                        case 3:
                            if (a instanceof MainFilesArrayAdapter) ((MainFilesArrayAdapter) a).selectAll();
                            break;
                        case 4:
                            showSortDialog();
                            break;
                        case 6: {
                            boolean isChecked = !item.isChecked();
                            item.setChecked(isChecked);
                            prefs.edit().putBoolean("show_system_hidden", isChecked).apply();
                            reloadCurrentFolder();
                            break;
                        }
                        case 7: {
                            boolean isChecked = !item.isChecked();
                            item.setChecked(isChecked);
                            prefs.edit().putBoolean("show_manually_hidden", isChecked).apply();
                            reloadCurrentFolder();
                            break;
                        }
                        case 8:
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
                        case 9:
                            showEditHiddenFilesDialog();
                            break;
                        case 10: {
                            boolean isPane1 = lastPaneSelected == 1;
                            File toBookmark = isPane1 ? pane1Folder : pane2Folder;
                            addBookmark(toBookmark);
                            Extensions.showMessage(MainActivity.this, rss.getString(R.string.added_to_bookmarks, toBookmark.getName()));
                            break;
                        }
                        case 11: {
                            boolean isPane1 = lastPaneSelected == 1;
                            prefs.edit().putString(isPane1 ? "home1" : "home2", (isPane1 ? pane1Folder : pane2Folder).getPath())
                                    .apply();
                            Extensions.showMessage(MainActivity.this, R.string.set_as_home);
                            break;
                        }
                        case 12:
                            File temp = pane1Folder;
                            pane1Folder = pane2Folder;
                            pane2Folder = temp;
                            loadFolderInPane(pane1Folder, true);
                            loadFolderInPane(pane2Folder, false);
                            break;
                        case 13:
                            showSettingsDialog();
                            break;
                        case 14:
                            finishAffinity();
                            break;
                    }
                    return true;
                });
                PopupMenus.forceShowIcons(popup);
                popup.show();
            });

            View upButton = findViewById(R.id.upButton);
            upButton.setOnLongClickListener(this::showMsgOnLongPress);
            upButton.setOnClickListener(v -> {
                boolean isPane1 = lastPaneSelected == 1;
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if ((a instanceof MainFilesArrayAdapter adapter)) {
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
                    ftp.ftpParent(isPane1);
                }
            });
        }).start();

        String locate = getIntent() == null ? null : getIntent().getStringExtra("locatePath");
        handler.post(() -> {
            sortFilter.setupFilterBar();
            setupNavigationButtons();
            refreshSidebar(getSidebarSectionOrder());
            File[] dir1Files = homeDir1.listFiles();
            if (dir1Files != null) {
                File[] folders = homeDir1.listFiles(File::isDirectory);
                int foldersCount = folders == null ? 0 : folders.length;
                MainActivity.this.<TextView>findViewById(R.id.folderCount).setText(
                        new StringBuilder("Folders: ").append(foldersCount).append(" Files: ")
                                .append(dir1Files.length - foldersCount));
            }
            if(TextUtils.isEmpty(locate)) loadFolderInPane(resolveStartupFolder(true, homeDir1), true);
            loadFolderInPane(resolveStartupFolder(false, homeDir2), false);
            new Thread(() -> AccessManager.warmUp(MainActivity.this)).start();
            new Thread(() -> {
                try {
                    RootManager rm = RootManager.getInstance(MainActivity.this);
                    if (rm.autoEnableRootIfAvailable()) {
                        handler.post(() -> {
                            try {
                                Extensions.showMessage(MainActivity.this, R.string.root_detected_enabled);
                                refreshSidebar(getSidebarSectionOrder());
                            } catch (Exception ignored) {
                            }
                        });
                    }
                    try {
                        rm.kickNsProbe();
                    } catch (Exception ignored) {
                    }
                } catch (Exception ignored) {
                }
            }).start();
        });
        if ((checkForUpdates = settings.getBoolean("checkForUpdates", true))) UpdateUtil.checkForUpdates(false, this);
    }

    private File resolveStartupFolder(boolean pane1, File home) {
        try {
            if ("last".equals(UiPrefs.startupMode(this, pane1))) {
                String saved = PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(pane1 ? "last_path_1" : "last_path_2", null);
                if (!TextUtils.isEmpty(saved)) {
                    File f = new File(saved);
                    if (f.isDirectory() || AccessManager.exists(this, saved)) return f;
                }
            }
        } catch (Exception ignored) {
        }
        return home;
    }

    private void setupNavigationButtons() {
        View backButton = findViewById(R.id.backButton);
        View forwardButton = findViewById(R.id.forwardButton);
        backButton.setOnLongClickListener(this::showMsgOnLongPress);
        forwardButton.setOnLongClickListener(this::showMsgOnLongPress);
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
            TextInputLayout filterBox = (TextInputLayout) topBar.getChildAt(2);
            EditText filterBar = filterBox.getEditText();
            if (filterBox.getVisibility() == View.VISIBLE) {
                topBar.getChildAt(1).setVisibility(View.VISIBLE);
                filterBox.setVisibility(View.GONE);
                if (filterBar != null) filterBar.setText("");
            } else {
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if(a instanceof MainFilesArrayAdapter adapter) {
                    if (adapter.isMultiSelectMode()) adapter.clearSelection();
                    else {
                        String s = adapter.currentZipPath;
                        if(adapter.isInZip && !StringsUtil.isEmpty(s)) {
                            char[] chars = s.toCharArray();
                            int i = 0;
                            for(char c : chars) if (c == File.separatorChar) i++;
                            boolean inOneLevelInZip = i < 2;
                            loadZipFolderInPane(((ZipEntryInfo)adapter.values[0]).getZipFile(), inOneLevelInZip ? "" : s.substring(0, s.lastIndexOf('/', s.lastIndexOf('/') - 1)), adapter.pane1, true);
                        } else {
                            String path = this.<TextView>findViewById(R.id.currentFolderPath).getText().toString();
                            File f = new File(path).getParentFile();
                            if (f != null && (f.canRead() || canListViaRoot(f))) loadFolderInPane(f, lastPaneSelected == 1);
                            else if (isBackPressedToExit) {
                                handler.removeCallbacks(resetExitPrompt);
                                finishAffinity();
                            } else {
                                isBackPressedToExit = true;
                                Extensions.showMessage(this, rss.getString(R.string.press_back_again_to_exit));
                                handler.postDelayed(resetExitPrompt, 2000);
                            }
                        }
                    }
                } else ftp.ftpParent(lastPaneSelected == 1);
            }
        }
    }

    public boolean navigateBack(boolean pane1) {
        return navigation.navigateBack(pane1);
    }

    public void navigateForward(boolean pane1) {
        navigation.navigateForward(pane1);
    }

    private void updateNavigationButtons() {
        navigation.refreshButtons();
    }

    private void pushNavigationHistory(boolean pane1, NavigationHistoryEntry entry) {
        navigation.push(pane1, entry);
    }

    private void refreshHistoryTab() {
        navigation.refresh();
    }

    // PaneNavigationController.Host: view/loading access for the controller.

    @Override
    public void loadFolder(File folder, boolean pane1, boolean addToHistory) {
        loadFolderInPane(folder, pane1, addToHistory);
    }

    @Override
    public void loadZip(File zipFile, String path, boolean pane1, boolean addToHistory) {
        loadZipFolderInPane(zipFile, path, pane1, addToHistory);
    }

    @Override
    public void applyNavButtons(boolean canGoBack, boolean canGoForward) {
        findViewById(R.id.backButton).setEnabled(canGoBack);
        findViewById(R.id.forwardButton).setEnabled(canGoForward);
    }

    @Override
    public void showHistory(List<NavigationHistoryEntry> history) {
        try {
            if (historyAdapter != null) {
                historyAdapter.setData(history);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String shownPath() {
        return this.<TextView>findViewById(R.id.currentFolderPath).getText().toString();
    }

    @Override
    public int shownPane() {
        return lastPaneSelected;
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
        boolean shizukuDir = ShizukuFile.isAndroidDataPath(folder);
        File[] files = null;
        String folderPath = folder.getAbsolutePath();
        boolean rootListingPath = "/".equals(folderPath) || RootManager.isRootOnlyPath(folderPath);
        if (rootListingPath && AccessManager.active(this) == AccessManager.Backend.ROOT && AccessManager.fileOpsOn(this)) {
            files = AccessManager.listWithStat(this, folder.getAbsolutePath());
            if (files != null) files = Arrays.stream(files).filter(this::isNotHidden).toArray(File[]::new);
        }
        if (files == null) files = folder.listFiles(this::isNotHidden);
        if (files == null || (files.length == 0 && shizukuDir)) {
            File[] viaShizuku = ShizukuFile.tryList(this, folder);
            if (viaShizuku != null) files = viaShizuku;
        }
        if (files == null) {
            if (shizukuDir) showShizukuGuideOnce(folder, pane1);
            boolean elevated = AccessManager.fileOpsOn(this);
            if (!elevated) {
                try {
                    elevated = RootPermissionHelper.hasElevatedShell(this);
                } catch (Exception ignored) {
                }
            }
            if (elevated) {
            files = AccessManager.listWithStat(this, folderPath);

                if (files != null) {
                    files = Arrays.stream(files)
                            .filter(this::isNotHidden)
                            .toArray(File[]::new);
                }
            }
            if (files == null) {
                Extensions.showMessage(this, getString(R.string.open_folder_failed, folder.getName()));
                return;
            }
        }
        try {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(pane1 ? "last_path_1" : "last_path_2", folder.getAbsolutePath()).apply();
        } catch (Exception ignored) {
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

                rgDexLib.addView(uiHelper.makeRadioButton(BuildOptions.DEX_LIB_INTERNAL, "Internal (REAndroid)"));
                rgDexLib.addView(uiHelper.makeRadioButton(BuildOptions.DEX_LIB_JF, "developer-krushna"));
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

                UIKit.dialog(this)
                        .setTitle(getString(R.string.build_options))
                        .setView(content)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            SignWrapper[] wrapper = new SignWrapper[1];
                            Runnable doBuild = () -> {
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
                                        if(sign[0]) wrapper[0].signApk(bo.outputFile);
                                        pm.dismiss();
                                    } catch (Exception e) {
                                        pm.dismiss();
                                        new ErrorUtil(MainActivity.this).showError(e);
                                    }
                                }).start();
                            };
                            if(sign[0]) SignWrapper.requireAuth(this, sw -> {
                                wrapper[0] = sw;
                                doBuild.run();
                            }); else doBuild.run();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .show();
            });
        } else buildButton.setVisibility(View.GONE);

        sortFiles(files, folder.getPath());

        if (pane1) {
            currentPane1Files = files;
            pane1Folder = folder;
            if (addToHistory) {
                pushNavigationHistory(true, new NavigationHistoryEntry(folder, false, null));
            }
        } else {
            currentPane2Files = files;
            pane2Folder = folder;
            if (addToHistory) {
                pushNavigationHistory(false, new NavigationHistoryEntry(folder, false, null));
            }
        }
        setCurrentFolder(folder, files);
        RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        File parent = folder.getParentFile() != null ? folder.getParentFile() : folder;
        pane.setAdapter(new MainFilesArrayAdapter(this, files, parent, pane1, false, null));
        // Fresh listing = no selection in this pane; sync the bottom bar if it's current.
        if ((pane1 ? lastPaneSelected == 1 : lastPaneSelected == 2)) setMultiSelectModeUI(false);
        updateNavigationButtons();
    }

    public void loadZipFolderInPane(File zipFile, String path, boolean pane1, boolean addToHistory) {
        try {
            List<ZipEntryInfo> entries = new ArrayList<>();
            ZipEntryInfo parent = null;
            HashSet<String> seenDirs = new HashSet<>() {
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
                    pushNavigationHistory(true, new NavigationHistoryEntry(zipFile, true, path));
                }
            } else {
                currentPane2ZipEntries = entries;
                pane2Folder = zipFile;
                if (addToHistory) {
                    pushNavigationHistory(false, new NavigationHistoryEntry(zipFile, true, path));
                }
            }

            setCurrentFolder(zipFile.getPath() + "!" + path, entries);
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            ZipEntryInfo finalParent = parent;
            boolean isCurrentPane = pane1 ? lastPaneSelected == 1 : lastPaneSelected == 2;
            handler.post(() -> {
                pane.setAdapter(new MainFilesArrayAdapter(this, entries.toArray(new ZipEntryInfo[0]), finalParent, pane1, true, path));
                // Fresh listing = no selection in this pane; sync the bottom bar if it's current.
                if (isCurrentPane) setMultiSelectModeUI(false);
                updateNavigationButtons();
            });
        } catch (IOException e) {
            new ErrorUtil(this).showError(e);
        }
    }

    public void loadFolderInPane(File folder, boolean pane1) {
        loadFolderInPane(folder, pane1, true);
    }

    private boolean canListViaRoot(File folder) {
        try {
            return AccessManager.fileOpsOn(this) && folder != null && AccessManager.exists(this, folder.getAbsolutePath());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean mkdirViaRoot(File parent, String name) {
        try {
            if (name == null || name.isEmpty() || name.contains("/") || name.contains("\0")) return false;
            if (!AccessManager.fileOpsOn(this)) return false;
            AccessManager.mkdir(this, new File(parent, name).getAbsolutePath(), true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean touchViaRoot(File parent, String name) {
        try {
            if (name == null || name.isEmpty() || name.contains("/") || name.contains("\0")) return false;
            if (!AccessManager.fileOpsOn(this)) return false;
            String target = new File(parent, name).getAbsolutePath();
            if (AccessManager.exists(this, target)) return false;
            AccessManager.touch(this, target, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Nudge to install/start/grant Shizuku when the user hits an Android/data folder without it. */
    private void showShizukuGuideOnce(File folder, boolean pane1) {
        boolean installed = ShizukuShell.isInstalled(this);
        String message;
        int positiveLabel;
        Runnable onPositive;
        if (!installed) {
            message = rss.getString(R.string.shizuku_needed);
            positiveLabel = R.string.shizuku_install;
            onPositive = () -> ShizukuShell.openShizukuApp(this);
        } else if (!ShizukuShell.isAvailable()) {
            message = rss.getString(R.string.shizuku_not_running_hint);
            positiveLabel = R.string.shizuku_open;
            onPositive = () -> ShizukuShell.openShizukuApp(this);
        } else {
            message = rss.getString(R.string.shizuku_needed);
            positiveLabel = R.string.shizuku_grant;
            onPositive = () -> ShizukuShell.requestPermission();
        }
        // Once the binder arrives (user started Shizuku / granted), reload this folder automatically.
        ShizukuShell.onBinderReceived(() -> runOnUiThread(() -> {
            if (ShizukuShell.isGranted()) loadFolderInPane(folder, pane1, false);
        }));
        UIKit.dialog(this)
                .setTitle("Shizuku")
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> onPositive.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean showMsgOnLongPress(View v) {
        CharSequence contentDescription = v.getContentDescription();
        if(!TextUtils.isEmpty(contentDescription)) Extensions.showMessage(this, contentDescription);
        return false;
    }



    public void reloadCurrentFolder() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::reloadCurrentFolder);
            return;
        }
        boolean isPane1 = lastPaneSelected == 1;
        loadFolderInPane(isPane1 ? pane1Folder : pane2Folder, isPane1);
    }

    private void setupPullToRefresh() {
        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshPane1)).setOnRefreshListener(() -> refreshPane(true));
        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshPane2)).setOnRefreshListener(() -> refreshPane(false));
    }

    private void refreshPane(boolean pane1) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            RecyclerView.Adapter<?> adapter = pane.getAdapter();
            if (adapter instanceof MainFilesArrayAdapter filesAdapter) {
                if (filesAdapter.isInZip) loadZipFolderInPane(pane1 ? pane1Folder : pane2Folder, filesAdapter.currentZipPath, pane1, false);
                else loadFolderInPane(pane1 ? pane1Folder : pane2Folder, pane1, false);
            } else if (adapter instanceof FtpFilesArrayAdapter ftpAdapter && ftpAdapter.getItemCount() > 0)
                fetchFtpDirAndLoad(ftpAdapter.getItem(0).getParent(), pane1);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
        ((SwipeRefreshLayout) findViewById(pane1 ? R.id.swipeRefreshPane1 : R.id.swipeRefreshPane2)).setRefreshing(false);
    }

    public void setCurrentFolder(File curr, File[] files) {
        int foldersCount = 0;
        int totalCount;
        if (files != null) {
            // Count from the already-listed array (root listings carry
            // isDirectory via RootFile). Re-listing with curr.listFiles()
            // returns null on root-only dirs and would show 0 folders.
            for (File f : files) {
                try {
                    if (f != null && f.isDirectory()) foldersCount++;
                } catch (Exception ignored) {
                }
            }
            totalCount = files.length - foldersCount;
        } else totalCount = 0;
        TextView currentFolderPath = findViewById(R.id.currentFolderPath);
        int finalFoldersCount = foldersCount;
        handler.post(() -> {
            currentFolderPath.setText(curr.getPath());
            uiHelper.scrollTextView(currentFolderPath);
            this.<TextView>findViewById(R.id.folderCount).setText(rss.getString(R.string.folders_files_x, finalFoldersCount, totalCount));
        });
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
                // ShizukuFile.listFiles() can't list; reuse the entries the adapter already shows.
                File[] shown = adapter.getShownFiles();
                setCurrentFolder(curr, shown != null ? shown : curr.listFiles());
            }
        }
        updateNavigationButtons();
    }

    public void setSelectedPane(int pane) {
        if (lastPaneSelected == pane) return;
        lastPaneSelected = pane;
        updateNavigationButtons();
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
        try {
            updates.register();
            String locate = getIntent() == null ? null : getIntent().getStringExtra("locatePath");
            if (locate != null && !locate.isEmpty()) {
                try {
                    getIntent().removeExtra("locatePath");
                } catch (Exception ignored) {
                }
                File target = new File(locate);
                File folder = target.isFile() ? target.getParentFile() : target;
                if (folder != null && folder.exists()) {
                    loadFolderInPane(folder, true);
                    Extensions.showMessage(this, rss.getString(R.string.loaded_X, target.getPath()));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            refreshSidebar(getSidebarSectionOrder());
        } catch (Exception ignored) {
        }
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
        try {
            File cache = getCacheDir();
            File[] kids = cache.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.getName().equals("root_staging")) continue;
                    FileUtil.deleteDirectory(k);
                }
            }
        } catch (Exception ignored) {
        }
        sidebar.unregister();
        if (isServiceBound) {
            getApplicationContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        super.onDestroy();
    }

    private void showSearchDialog() {
        fileSearch.showSearchDialog();
    }

    private void showFindInFilesDialog() {
        fileSearch.showFindInFilesDialog();
    }

    public void showSettingsDialog() {
        settingsUI.showSettingsDialog();
    }







    private boolean isNotHidden(File f) {
        return sortFilter.isNotHidden(f);
    }

    private boolean isNotHidden(ZipEntryInfo e) {
        return sortFilter.isNotHidden(e);
    }

    private void showSortDialog() {
        sortFilter.showSortDialog();
    }

    private void showEditHiddenFilesDialog() {
        sortFilter.showEditHiddenFilesDialog();
    }

    private void sortFiles(File[] files, String folderPath) {
        sortFilter.sortFiles(files, folderPath);
    }

    private void sortZipEntries(List<ZipEntryInfo> entries, String folderPath) {
        sortFilter.sortZipEntries(entries, folderPath);
    }

    public interface ImagePickCallback {
        void onImagePicked(Uri uri);
    }

    public static ImagePickCallback overlayImageCallback;

    public void showFtpServerDialog() {
        ftp.showFtpServerDialog();
    }

    public void showFtpClientDialog() {
        ftp.showFtpClientDialog();
    }

    public void fetchFtpDirAndLoad(String path, boolean pane1) {
        ftp.fetchFtpDirAndLoad(path, pane1);
    }

    private void loadFtpFolderInPane(FTPFileWrapper folder, boolean pane1) {
        ftp.loadFtpFolderInPane(folder, pane1);
    }
}

