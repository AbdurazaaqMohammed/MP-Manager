package io.github.abdurazaaqmohammed.MPManager;

import static io.github.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.sun.security.provider.JavaKeyStoreProvider;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.data.prefs.BookmarkStore;
import io.github.abdurazaaqmohammed.data.prefs.DirSizeCache;
import io.github.abdurazaaqmohammed.data.prefs.PaneCache;
import io.github.abdurazaaqmohammed.app.UpdateController;
import io.github.abdurazaaqmohammed.domain.files.FileSorting;
import io.github.abdurazaaqmohammed.features.apk.ApkResultHandler;
import io.github.abdurazaaqmohammed.features.files.BookmarksController;
import io.github.abdurazaaqmohammed.features.files.FileSearchController;
import io.github.abdurazaaqmohammed.features.files.FtpController;
import io.github.abdurazaaqmohammed.features.files.MultiSelectController;
import io.github.abdurazaaqmohammed.features.files.MainSettingsActivity;
import io.github.abdurazaaqmohammed.features.files.NavigationHistoryEntry;
import io.github.abdurazaaqmohammed.features.files.PaneHighlightController;
import io.github.abdurazaaqmohammed.features.files.PaneNavigationController;
import io.github.abdurazaaqmohammed.features.files.RightMenuController;
import io.github.abdurazaaqmohammed.features.files.SidebarController;
import io.github.abdurazaaqmohammed.utils.KeyboardUtil;
import io.github.abdurazaaqmohammed.utils.RecycleBin;
import io.github.abdurazaaqmohammed.utils.StartupPrivileges;
import io.github.abdurazaaqmohammed.features.files.SortFilterController;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.compile.BuildOptions;
import com.reandroid.apkeditor.compile.Builder;
import com.reandroid.utils.StringsUtil;
import com.reandroid.utils.io.FileUtil;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.Security;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.HistoryAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.player.ImageViewerActivity;
import io.github.abdurazaaqmohammed.player.MediaItem;
import io.github.abdurazaaqmohammed.player.MediaPlayerActivity;
import io.github.abdurazaaqmohammed.player.MiniPlayerDialog;
import io.github.abdurazaaqmohammed.player.PlayerManager;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootPermissionHelper;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.UpdateUtil;
import io.github.codehasan.colorpicker.PreferencesDialogFragment;
import io.github.codehasan.colorpicker.ServiceState;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.codehasan.colorpicker.services.ColorPickerService;
import io.github.ratul.topactivity.extensions.ActivityExtensions;
import io.github.ratul.topactivity.manager.ServiceManager;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.services.AccessibilityMonitoringService;
import io.github.ratul.topactivity.services.PackageMonitoringService;
import io.github.ratul.topactivity.utils.PermissionUtil;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

public class MainActivity extends BaseActivity implements PaneNavigationController.Host {

    private static WeakReference<MainActivity> CURRENT = new WeakReference<>(null);

    public static MainActivity current() {
        return CURRENT.get();
    }

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
    public DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    public boolean isSidebarDrawerOpen;
    public boolean isBookmarksDrawerOpen;

    public Handler handler;
    private boolean systemTheme;
    public int theme;
    private boolean checkForUpdates;
    private File[] currentPane1Files;
    private File[] currentPane2Files;
    private List<ZipEntryInfo> currentPane1ZipEntries;
    private List<ZipEntryInfo> currentPane2ZipEntries;
    private final SortFilterController sortFilter = new SortFilterController(this);
    private final FileSearchController fileSearch = new FileSearchController(this);
    /** Right (more-options) drawer content; see RightMenuController. */
    private final RightMenuController rightMenu = new RightMenuController(this, fileSearch, sortFilter);
    private final BookmarkStore bookmarkStore = new BookmarkStore(this);
    private final SidebarController sidebar = new SidebarController(this);
    private final BookmarksController bookmarksUI = new BookmarksController(this);
    private final MultiSelectController multiSelect = new MultiSelectController(this);
    private final FtpController ftp = new FtpController(this);
    private final ApkResultHandler apkResults = new ApkResultHandler(this);
    private final UpdateController updates = new UpdateController(this);
    private final PaneHighlightController paneHighlight = new PaneHighlightController(this);

    /**
     * Cold-start listing worker: listFiles/filter/sort run here so first paint
     * never waits on storage I/O. Single thread keeps ordering predictable.
     */
    private final ExecutorService listingExecutor = Executors.newSingleThreadExecutor();
    /** Generation per pane: a stale background listing never overwrites a newer pane. */
    private volatile long paneLoadGen1;
    private volatile long paneLoadGen2;

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

    public void setCheckForUpdates(boolean checkForUpdates) {
        this.checkForUpdates = checkForUpdates;
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
        try {
            // Needs group metadata; explicit user action so setup cost is fine.
            ensureBookmarksReady();
        } catch (Exception ignored) {
        }
        bookmarksUI.addBookmark(toBookmark);
    }

    public void openImageViewer(String filePath) {
        ImageViewerActivity.open(this, filePath);
    }

    public void playMediaFile(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".3gp")
                || lower.endsWith(".ts") || lower.endsWith(".flv") || lower.endsWith(".wmv")
                || lower.endsWith(".m4v");
        boolean useActivity = isVideo || PreferenceManager.getDefaultSharedPreferences(this).getBoolean("player_open_activity", false);
        if (useActivity) MediaPlayerActivity.openAndPlay(this, filePath);
        else {
            PlayerManager pm = PlayerManager.getInstance(this);
            try {
                File cur = new File(filePath);
                File dir = cur.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    File[] all = dir.listFiles();
                    if (all != null) {
                        List<File> media = new ArrayList<>();
                        for (File f : all) {
                            if (f == null || !f.isFile()) continue;
                            String n = f.getName().toLowerCase(Locale.ROOT);
                            if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac") || n.endsWith(".ogg")
                                    || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wma") || n.endsWith(".opus")
                                    || n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov")
                                    || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".ts") || n.endsWith(".flv")
                                    || n.endsWith(".wmv") || n.endsWith(".m4v")) media.add(f);
                        }
                        io.github.abdurazaaqmohammed.data.prefs.ListPrefs lp = new io.github.abdurazaaqmohammed.data.prefs.ListPrefs(this);
                        String dirPath = dir.getAbsolutePath();
                        File[] arr = media.toArray(new File[0]);
                        FileSorting.sortFiles(arr, lp.sortBy(dirPath), lp.sortReverse(dirPath));
                        if (arr.length > 0) {
                            List<MediaItem> q = new ArrayList<>();
                            int start = 0;
                            String curAbs = cur.getAbsolutePath();
                            String curName = cur.getName();
                            for (int i = 0; i < arr.length; i++) {
                                String path = arr[i].getAbsolutePath();
                                if (path.equals(curAbs) || arr[i].getName().equalsIgnoreCase(curName)) {
                                    start = i;
                                    q.add(PlayerManager.buildMediaItem(this, path));
                                } else {
                                    q.add(PlayerManager.buildMediaItemFast(path));
                                }
                            }
                            pm.play(q, start);
                            if (miniPlayerDialog == null || !miniPlayerDialog.isShowing()) (miniPlayerDialog = new MiniPlayerDialog(this)).show();
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
            pm.play(PlayerManager.buildMediaItem(this, filePath));
            if (miniPlayerDialog == null || !miniPlayerDialog.isShowing()) (miniPlayerDialog = new MiniPlayerDialog(this)).show();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            if (pane1Folder != null) outState.putString("pane1_path", pane1Folder.getAbsolutePath());
            if (pane2Folder != null) outState.putString("pane2_path", pane2Folder.getAbsolutePath());
            outState.putInt("last_pane", lastPaneSelected);
            try {
                RecyclerView p1 = findViewById(R.id.listViewPane1);
                if (p1 != null && p1.getAdapter() instanceof MainFilesArrayAdapter a1 && a1.isInZip) {
                    outState.putBoolean("pane1_in_zip", true);
                    outState.putString("pane1_zip_path", a1.currentZipPath);
                }
                RecyclerView p2 = findViewById(R.id.listViewPane2);
                if (p2 != null && p2.getAdapter() instanceof MainFilesArrayAdapter a2 && a2.isInZip) {
                    outState.putBoolean("pane2_in_zip", true);
                    outState.putString("pane2_zip_path", a2.currentZipPath);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updates.unregister();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit()
                .putString("bookmarks", getBookmarks().toString())
                .putBoolean("systemTheme", systemTheme)
                .putBoolean("checkForUpdates", checkForUpdates)
                .putInt("theme", theme);
        
        try {
            RecyclerView pane1View = findViewById(R.id.listViewPane1);
            if (pane1View != null && pane1View.getAdapter() instanceof MainFilesArrayAdapter adapter1) {
                Set<Integer> selected1 = new HashSet<>(adapter1.selectedPositions);
                editor.putStringSet("pane1_selected", selected1.stream().map(String::valueOf).collect(Collectors.toSet()));
            }
        } catch (Exception ignored) {}
        
        try {
            RecyclerView pane2View = findViewById(R.id.listViewPane2);
            if (pane2View != null && pane2View.getAdapter() instanceof MainFilesArrayAdapter adapter2) {
                Set<Integer> selected2 = new HashSet<>(adapter2.selectedPositions);
                editor.putStringSet("pane2_selected", selected2.stream().map(String::valueOf).collect(Collectors.toSet()));
            }
        } catch (Exception ignored) {}
        
        editor.apply();
    }

    public void openSidebarDrawer() {
        ensureSidebarReady();
        try {
            drawerLayout.openDrawer(GravityCompat.START);
        } catch (Exception ignored) {
        }
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
            } else if(requestCode == 757 || resultCode == 757) {
                if (data == null || data.getData() == null) return;
                String zipEntryPath = data.getStringExtra("zipEntryPath");
                String zipFilePath = data.getStringExtra("zipFilePath");
                if (zipFilePath != null || zipEntryPath != null) {
                    handleModifiedFileResult(data.getData(), zipEntryPath, zipFilePath);
                } else {
                    File modified = new File(data.getData().getPath());
                    highlightAndFocusItem(pane1, modified.getName());
                }
            }
        }
    }

    public void handleModifiedFileResult(Uri uri) {
        apkResults.handleModifiedFileResult(uri);
    }

    public void handleModifiedFileResult(Uri uri, String entryPath, String zipFileExtra) {
        apkResults.handleModifiedFileResult(uri, entryPath, zipFileExtra);
    }

    public void offerOuterUpdate(File zipFile, boolean pane1) {
        try {
            apkResults.offerOuterUpdate(zipFile, pane1);
        } catch (Exception ignored) {
        }
    }

    public File outermostArchive(File staged) {
        File cur = staged;
        try {
            for (int i = 0; i < 6 && cur != null; i++) {
                ArchiveParent p = archiveParentOf(cur.getAbsolutePath());
                if (p == null || p.outerZip == null || !p.outerZip.isFile()) break;
                cur = p.outerZip;
            }
        } catch (Exception ignored) {
        }
        return cur;
    }

    public void openBookmarksDrawer() {
        try {
            ensureBookmarksReady();
        } catch (Exception ignored) {
        }
        try {
            findViewById(R.id.bookmarks_drawer).post(() -> {
                try {
                    showHistory(navigation.historyFor(lastPaneSelected == 1));
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    isBookmarksDrawerOpen = true;
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** First-drawer-open path: bookmarks views + async sidebar data, exactly once. */
    private boolean sidebarReady;

    private void ensureSidebarReady() {
        if (sidebarReady) return;
        sidebarReady = true;
        try {
            ensureBookmarksReady();
        } catch (Exception ignored) {
        }
        try {
            sidebar.ensureSidebarData();
        } catch (Exception ignored) {
        }
    }

    /** Builds the history list + bookmarks pager on first use, never on cold start. */
    private boolean bookmarksReady;

    public void ensureBookmarksReady() {
        if (bookmarksReady) return;
        bookmarksReady = true;
        try {
            ListView hl = new ListView(this);
            hl.setDivider(null);
            hl.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            hl.setOnTouchListener(bookmarksUI.getSwipeDownCloseListener());
            hl.setAdapter(historyAdapter = new HistoryAdapter(this, navigation.historyFor(lastPaneSelected == 1)));
            hl.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    NavigationHistoryEntry entry = historyAdapter.getItem(position);
                    if (entry == null) return;
                    navigation.jumpTo(entry, lastPaneSelected == 1);
                    closeBookmarksDrawer();
                } catch (Exception ignored) {
                }
            });
            historyList = hl;
            bookmarksUI.setup(hl);
        } catch (Exception ignored) {
            // Stay marked: callers null-guard, so a half-built drawer still
            // opens instead of retry-janking every time.
        }
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

    /** Surprise-arrival sidebar refresh (resume, root probe): I/O off-main. */
    public void refreshSidebarAsync(List<String> sectionOrder) {
        try {
            sidebar.refreshSidebarAsync(sectionOrder != null ? sectionOrder : getSidebarSectionOrder());
        } catch (Exception ignored) {
        }
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
        if (RootPermissionHelper.hasAccessibility(this)
                && AccessibilityMonitoringService.getInstance() == null) {
            waitForAccessibilityService(0);
            return;
        }
        startLayoutInspector();
    }

    private void waitForAccessibilityService(int attempt) {
        if (attempt >= 20 || isFinishing() || isDestroyed()
                || AccessibilityMonitoringService.getInstance() != null) {
            startLayoutInspector();
            return;
        }
        handler.postDelayed(() -> waitForAccessibilityService(attempt + 1), 200);
    }

    private void startLayoutInspector() {
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
    private Consumer<ActivityResult> pendingExternalFile;
    private Consumer<ActivityResult> pendingExternalSetting;
    private final ActivityResultLauncher<Intent> externalFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Consumer<ActivityResult> cb = pendingExternalFile;
        pendingExternalFile = null;
        if (cb != null) {
            try {
                cb.accept(result);
            } catch (Exception ignored) {
            }
        }
    });
    private final ActivityResultLauncher<Intent> externalSettingLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Consumer<ActivityResult> cb = pendingExternalSetting;
        pendingExternalSetting = null;
        if (cb != null) {
            try {
                cb.accept(result);
            } catch (Exception ignored) {
            }
        }
    });

    /** Launches an external file-plugin activity; result goes to cb (may be null). */
    public void launchExternalFile(Intent intent, Consumer<ActivityResult> cb) {
        try {
            pendingExternalFile = cb;
            externalFileLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalFile = null;
        }
    }

    private Consumer<ActivityResult> pendingExternalApk;
    private final ActivityResultLauncher<Intent> externalApkLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Consumer<ActivityResult> cb = pendingExternalApk;
        pendingExternalApk = null;
        if (cb != null) {
            try {
                cb.accept(result);
            } catch (Exception ignored) {
            }
        }
    });

    /** Launches an external APK-plugin activity; result goes to cb (may be null). */
    public void launchExternalApk(Intent intent, Consumer<ActivityResult> cb) {
        try {
            pendingExternalApk = cb;
            externalApkLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalApk = null;
        }
    }

    /** Launches an external setting config activity; result goes to cb (may be null). */
    public void launchExternalSetting(Intent intent, Consumer<ActivityResult> cb) {
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
        // Global insets are applied in BaseActivity.


        boolean lightBars;
        String pluginId = ThemeRegistry.getCurrentId(this);
        if (pluginId == null || BuiltInThemes.SYSTEM_DEFAULT_ID.equals(pluginId)) {
            lightBars = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_NO;
        } else {
            lightBars = BuiltInThemes.LIGHT_ID.equals(pluginId) || theme == R.style.Theme_MyApp_Light;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int surfaceColor = BuiltInThemes.BLACK_ID.equals(pluginId) ? Color.BLACK : MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
            getWindow().setStatusBarColor(surfaceColor);
            getWindow().setNavigationBarColor(surfaceColor);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && lightBars) {
                // Old devices can't render dark status bar icons; use a dark bar so icons stay visible
                int darkBar = MaterialColors.getColor(this, android.R.attr.colorPrimary, surfaceColor);
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
        DirSizeCache.init(this);
        if ("always".equals(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("pref_folder_size_mode", "archive"))) {
            DirSizeCache.preload();
        }
        // Applies the custom app dir to REAndroid's framework loader before
        // any decode/build can read the default location.
        UiPrefs.frameworkDir(this);
        // ShizukuShell.warmUp() does binder IPC: deferred to runWhenIdle below,
        // never on the cold-start critical path.
        //WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);
        setupSystemBars();
        paneHighlight.attach();
        checkStoragePerm();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        // App language is applied by AppCompatDelegate per-app locales
        // (see SettingsController.setupLanguageSettings + res/xml/locales_config.xml).
        // System locale automatically picks values / values-es / values-ru / values-zh-rCN.
        rss = getResources();

        new Thread(() -> {
            Security.addProvider(new BouncyCastleProvider());
            Security.addProvider(new JavaKeyStoreProvider());
        }).start();

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> { });

        // Frameworks are only needed by the APK tools, and the first copy is tens of
        // megabytes of raw-resource I/O. It starts after the first frame is up, on a
        // background-priority thread, so it never competes with the startup listing.
        runWhenIdle(() -> new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            File frameworks = UiPrefs.frameworkDir(this);
            if (frameworks.exists() || frameworks.mkdirs()) {
                int[] resourceIds = {
                        R.raw.android_23, R.raw.android_24, R.raw.android_25,
                        R.raw.android_26, R.raw.android_27, R.raw.android_28,
                        R.raw.android_29, R.raw.android_30, R.raw.android_31,
                        R.raw.android_32, R.raw.android_33, R.raw.android_34,
                        R.raw.android_35, R.raw.android_36
                };

                for (int i = 23; i < 37; i++) {
                    File out = new File(frameworks, "android_" + i + ".apk");

                    if(!out.exists()) try (InputStream in = rss.openRawResource(resourceIds[i-23])) {
                        FileUtils.copyFile(in, out);
                    } catch (Exception ignored) { }
                }

            }
            // NOTE: setupPullToRefresh() must run on the main thread; it is
            // called from onCreate below (moved: was a WrongThread call here).
        }, "framework-copy").start());

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
                // Direct-drag open path: build content if it was never set up.
                if (isBookmarksDrawerOpen && !bookmarksReady) {
                    try {
                        ensureBookmarksReady();
                        showHistory(navigation.historyFor(lastPaneSelected == 1));
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (drawerView.getId() == R.id.right_menu_drawer) {
                    refreshRightMenuDrawer();
                    return;
                }
                // Edge-swipe opens bypass openSidebarDrawer(): ensure data here too.
                try {
                    ensureSidebarReady();
                } catch (Exception ignored) {
                }
                // Packs load on a background thread; rebind so groups that
                // finished after the first render appear without a restart.
                try {
                    refreshSidebarAsync(getSidebarSectionOrder());
                } catch (Exception ignored) {
                }
                isSidebarDrawerOpen = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if (drawerView.getId() == R.id.sidebar_drawer) {
                    isSidebarDrawerOpen = false;
                }
            }
        });

        // History list + bookmarks pager are built on first drawer open
        // (see ensureBookmarksReady): HistoryAdapter, Gson loads and the
        // ViewPager2/TabLayoutMediator wiring never run on cold start.

        View.OnClickListener toggleSidebarDrawer = v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                closeSidebarDrawer();
            else
                openSidebarDrawer();
        };

        findViewById(R.id.sidebarTitle).setOnClickListener(v -> uiHelper.showAboutDialog());
        setupSidebar();
        // Right (more) menu builds ~13 rows of views on the UI thread; prebuild it shortly
        // after startup, off the cold-start path, so the first open is instant (the left
        // sidebar follows the same deferred pattern via ensureSidebarReady()).
        handler.postDelayed(this::refreshRightMenuDrawer, 600);
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
        // signatureKeyPath intentionally NOT resolved here: getDebugKeystore()
        // does KeyStore I/O on the caller thread and nothing reads this field
        // (sign flows read the "keyPath" pref directly). Resolved lazily if ever needed.

        RecyclerView pane1 = findViewById(R.id.listViewPane1);
        RecyclerView pane2 = findViewById(R.id.listViewPane2);

        pane1.setLayoutManager(new LinearLayoutManager(this));
        pane2.setLayoutManager(new LinearLayoutManager(this));
        // Fast scroller only makes sense on long listings; short folders keep
        // the plain view (threshold check lives in FastScrollerRecyclerView).
        ((FastScrollerRecyclerView) pane1).setMinItemsForScroller(50);
        ((FastScrollerRecyclerView) pane2).setMinItemsForScroller(50);
        // Stable rows: skips a requestLayout per bind and shares the view pool
        // between panes so the second pane inflates fewer row views.
        pane1.setHasFixedSize(true);
        pane2.setHasFixedSize(true);
        pane1.setItemViewCacheSize(24);
        pane2.setItemViewCacheSize(24);
        RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();
        pane1.setRecycledViewPool(sharedPool);
        pane2.setRecycledViewPool(sharedPool);
        setupPullToRefresh();

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

        // Listener wiring must run on the main thread (was a WrongThread
        // violation inside new Thread). Registration is cheap: dialog/menu
        // inflation stays inside the click callbacks.
        handler.post(() -> {
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
                        }).create();
                KeyboardUtil.focusOnShow(ad, input, true);
                dialogUtil.styleAlertDialog(ad);
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
                            String name = resolveNewEntryName(ogFolder, input.getText().toString(), true);
                            File newDir = new File(ogFolder, name);
                            boolean success = newDir.mkdir() || mkdirViaRoot(ogFolder, name);
                            if (success) {
                                highlightAndFocusItem(isPane1, name);
                            } else {
                                Extensions.showMessage(MainActivity.this,
                                        rss.getString(R.string.failed_to_create_folder, name),
                                        FileUtils.describeWriteProblem(this, newDir));
                            }
                        })
                        .setNeutralButton(android.R.string.paste, null) // Note: Need to set it after otherwise the dialog auto close
                        .setPositiveButton(rss.getString(R.string.file), (dialog, which) -> {
                            boolean isPane1 = lastPaneSelected == 1;
                            File ogFolder = isPane1 ? pane1Folder : pane2Folder;
                            String name = resolveNewEntryName(ogFolder, input.getText().toString(), false);
                            File newFile = new File(ogFolder, name);
                            boolean success = false;
                            try {
                                success = newFile.createNewFile();
                            } catch (IOException ignored) {}
                            if (!success) {
                                success = touchViaRoot(ogFolder, name);
                            }
                            if (success) {
                                highlightAndFocusItem(isPane1, name);
                            } else {
                                Extensions.showMessage(MainActivity.this,
                                        rss.getString(R.string.failed_to_create_file, name),
                                        FileUtils.describeWriteProblem(this, newFile));
                            }
                        }).create();
                KeyboardUtil.focusOnShow(ad, input, false);
                dialogUtil.styleAlertDialog(ad);
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
                    if (lastPaneSelected == 1) {
                        int[] saved = capturePaneScroll(true);
                        boolean wasZip = mainFilesArrayAdapter.isInZip;
                        File srcZip = pane1Folder;
                        File target = wasZip && srcZip != null ? srcZip.getParentFile() : srcZip;
                        String archiveName = wasZip && srcZip != null ? srcZip.getName() : null;
                        if (wasZip && srcZip != null && isStagedArchive(srcZip)) {
                            File outer = outermostArchive(srcZip);
                            if (outer != null && outer.getParentFile() != null) {
                                target = outer.getParentFile();
                                archiveName = outer.getName();
                            }
                        }
                        if (target == null) target = srcZip;
                        loadFolderInPane(pane2Folder = target, false);
                        restorePaneScroll(false, saved);
                        if (archiveName != null) flashPaneItem(false, archiveName);
                    } else {
                        int[] saved = capturePaneScroll(false);
                        boolean wasZip = mainFilesArrayAdapter.isInZip;
                        File srcZip = pane2Folder;
                        File target = wasZip && srcZip != null ? srcZip.getParentFile() : srcZip;
                        String archiveName = wasZip && srcZip != null ? srcZip.getName() : null;
                        if (wasZip && srcZip != null && isStagedArchive(srcZip)) {
                            File outer = outermostArchive(srcZip);
                            if (outer != null && outer.getParentFile() != null) {
                                target = outer.getParentFile();
                                archiveName = outer.getName();
                            }
                        }
                        if (target == null) target = srcZip;
                        loadFolderInPane(pane1Folder = target, true);
                        restorePaneScroll(true, saved);
                        if (archiveName != null) flashPaneItem(true, archiveName);
                    }
                }
            });

            View moreOptionsMenu = findViewById(R.id.moreOptionsMenu);
            moreOptionsMenu.setOnLongClickListener(this::showMsgOnLongPress);
            moreOptionsMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        drawerLayout.closeDrawer(GravityCompat.END);
                    } else {
                        // Open first, build after: drawer animation must not wait on view building.
                        drawerLayout.openDrawer(GravityCompat.END);
                        refreshRightMenuDrawer();
                    }
                }
            });

            View upButton = findViewById(R.id.upButton);
            upButton.setOnLongClickListener(this::showMsgOnLongPress);
            upButton.setOnClickListener(v -> {
                boolean isPane1 = lastPaneSelected == 1;
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if ((a instanceof MainFilesArrayAdapter adapter)) {
                    adapter.goUp();
                } else {
                    ftp.ftpParent(isPane1);
                }
            });
        });

        final String locate = getIntent() == null ? null : getIntent().getStringExtra("locatePath");
        final Bundle startupState = savedInstanceState;
        final File startHome1 = homeDir1;
        final File startHome2 = homeDir2;
        handler.post(() -> {
            try {
                // Filter bar is created lazily on first use (menu/back press);
                // it ships GONE so building it eagerly only delayed first frame.
                setupNavigationButtons();
                refreshSidebar(getSidebarSectionOrder());
                boolean restored = false;
                if (TextUtils.isEmpty(locate) && startupState != null
                        && (startupState.containsKey("pane1_path") || startupState.containsKey("pane2_path"))) {
                    restored = restorePanesFromSavedState(startupState);
                }
                if (!restored) {
                    boolean locateMode = !TextUtils.isEmpty(locate);
                    // Instant paint: only when the cache matches the folder this
                    // start will actually open (home or last-opened per pane),
                    // otherwise the paint would flash the wrong folder.
                    String expect1 = expectedStartupPath(true, startHome1);
                    String expect2 = expectedStartupPath(false, startHome2);
                    if (!locateMode) paintCachedPane(true, expect1);
                    paintCachedPane(false, expect2);
                    // Real listing off the main thread. Startup-folder
                    // resolution can hit the elevated backend (shell), so it
                    // runs on the worker too, then binds back on the main thread.
                    listingExecutor.execute(() -> {
                        File target1 = null;
                        File target2 = null;
                        try {
                            if (!locateMode) target1 = resolveStartupFolder(true, startHome1);
                        } catch (Exception ignored) {
                        }
                        try {
                            target2 = resolveStartupFolder(false, startHome2);
                        } catch (Exception ignored) {
                        }
                        if (target1 == null) target1 = startHome1;
                        if (target2 == null) target2 = startHome2;
                        final File f1 = target1;
                        final File f2 = target2;
                        handler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            try {
                                if (!locateMode) loadFolderInPaneAsync(f1, true, true);
                                // Locate mode: onResume loads the located folder
                                // into pane 1. This block runs after onResume,
                                // so loading home here would clobber the
                                // located folder — leave pane 1 alone.
                                loadFolderInPaneAsync(f2, false, true);
                            } catch (Exception ignored) {
                            }
                        });
                    });
                }
            } catch (Exception ignored) {
            }
            // Sidebar preload: its data fills shortly after first paint so
            // the drawer is already populated on first open. Cheap metadata
            // on the main thread, heavy I/O on a worker; never blocking paint.
            runWhenIdle(() -> {
                try {
                    bookmarksUI.ensureBookmarkMeta();
                } catch (Exception ignored) {
                }
                try {
                    sidebar.ensureSidebarData();
                } catch (Exception ignored) {
                }
            }, 800);
            // Deferred until first paint: backend warm-ups, root probe and the
            // update check must never block the cold-start critical path.
            runWhenIdle(() -> {
                new Thread(() -> {
                    try {
                        StartupPrivileges.warmShell(MainActivity.this);
                    } catch (Exception ignored) {
                    }
                }, "shizuku-warmup").start();
                new Thread(() -> {
                    try {
                        AccessManager.warmUp(MainActivity.this);
                    } catch (Exception ignored) {
                    }
                }, "access-warmup").start();
                new Thread(() -> {
                    try {
                        StartupPrivileges.apply(MainActivity.this, handler,
                                () -> refreshSidebarAsync(getSidebarSectionOrder()));
                    } catch (Exception ignored) {
                    }
                }, "privilege-startup").start();
                new Thread(() -> {
                    try {
                        int purged = RecycleBin.purgeExpired(MainActivity.this);
                        if (purged > 0) handler.post(() ->
                                Extensions.showMessage(MainActivity.this,
                                        rss.getString(R.string.recycle_bin_purged_x, purged)));
                    } catch (Exception ignored) {
                    }
                }, "bin-purge").start();
            });
            if ((checkForUpdates = settings.getBoolean("checkForUpdates", true))) {
                runWhenIdle(() -> {
                    try {
                        UpdateUtil.checkForUpdates(false, MainActivity.this);
                        } catch (Exception ignored) {
                    }
                }, 2500);
            }
        });
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

    private String expectedStartupPath(boolean pane1, File home) {
        try {
            if ("last".equals(UiPrefs.startupMode(this, pane1))) {
                String saved = PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(pane1 ? "last_path_1" : "last_path_2", null);
                if (!TextUtils.isEmpty(saved)) return new File(saved).getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        try {
            return home == null ? null : home.getAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Restores both panes after a configuration recreation (rotation, multi-window,
     * night-mode change). Returns true when at least one pane was restored, so the
     * caller can skip the default startup-folder load that would drop the user
     * back at the home directory and cancel in-progress dialogs.
     */
    private boolean restorePanesFromSavedState(Bundle saved) {
        boolean ok = false;
        try {
            int last = saved.getInt("last_pane", lastPaneSelected);
            if (last == 1 || last == 2) lastPaneSelected = last;
        } catch (Exception ignored) {
        }
        try {
            String p1 = saved.getString("pane1_path", null);
            if (!TextUtils.isEmpty(p1)) {
                File f1 = new File(p1);
                if (saved.getBoolean("pane1_in_zip", false)) {
                    String zp = saved.getString("pane1_zip_path", "");
                    if (f1.isFile()) {
                        loadZipFolderInPane(f1, zp == null ? "" : zp, true, false);
                        ok = true;
                    } else loadFolderInPane(resolveStartupFolder(true, homeDir1), true, false);
                } else if (f1.isDirectory() || f1.getAbsolutePath().equals("/") || AccessManager.exists(this, p1)) {
                    loadFolderInPane(f1, true, false);
                    ok = true;
                } else loadFolderInPane(resolveStartupFolder(true, homeDir1), true, false);
            }
        } catch (Exception ignored) {
        }
        try {
            String p2 = saved.getString("pane2_path", null);
            if (!TextUtils.isEmpty(p2)) {
                File f2 = new File(p2);
                if (saved.getBoolean("pane2_in_zip", false)) {
                    String zp = saved.getString("pane2_zip_path", "");
                    if (f2.isFile()) {
                        loadZipFolderInPane(f2, zp == null ? "" : zp, false, false);
                        ok = true;
                    } else loadFolderInPane(resolveStartupFolder(false, homeDir2), false, false);
                } else if (f2.isDirectory() || f2.getAbsolutePath().equals("/") || AccessManager.exists(this, p2)) {
                    loadFolderInPane(f2, false, false);
                    ok = true;
                } else loadFolderInPane(resolveStartupFolder(false, homeDir2), false, false);
            }
        } catch (Exception ignored) {
        }
        try {
            setCurrentPane(lastPaneSelected);
        } catch (Exception ignored) {
        }
        return ok;
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

    private String resolveNewEntryName(File parent, String inputStr, boolean isFolder) {
        String baseName = inputStr != null ? inputStr.trim() : "";
        String ext = "";
        if (baseName.isEmpty()) {
            baseName = isFolder ? "New folder" : "New file";
        } else if (!isFolder) {
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                ext = baseName.substring(dot);
                baseName = baseName.substring(0, dot);
            }
        }
        File candidate = new File(parent, baseName + ext);
        if (!candidate.exists()) {
            return baseName + ext;
        }
        int counter = 1;
        while (true) {
            String candidateName = baseName + " " + counter + ext;
            if (!new File(parent, candidateName).exists()) {
                return candidateName;
            }
            counter++;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isBookmarksDrawerOpen && ev.getAction() == MotionEvent.ACTION_DOWN) {
            View drawer = findViewById(R.id.bookmarks_drawer);
            if (drawer != null && drawer.getVisibility() == View.VISIBLE) {
                Rect outRect = new Rect();
                drawer.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    closeBookmarksDrawer();
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        } else if (isSidebarDrawerOpen) closeSidebarDrawer();
        else if (isBookmarksDrawerOpen) closeBookmarksDrawer();
        else {
            ViewGroup topBar = findViewById(R.id.topBar);
            TextInputLayout filterBox = sortFilter.filterBarIfCreated();
            EditText filterBar = filterBox == null ? null : filterBox.getEditText();
            if (filterBox != null && filterBox.getVisibility() == View.VISIBLE) {
                topBar.getChildAt(1).setVisibility(View.VISIBLE);
                filterBox.setVisibility(View.GONE);
                if (filterBar != null) filterBar.setText("");
            } else {
                RecyclerView.Adapter a = getCurrentPane().getAdapter();
                if(a instanceof MainFilesArrayAdapter adapter) {
                    if (adapter.isMultiSelectMode()) adapter.clearSelection();
                    else if (adapter.isSearchResultsMode()) {
                        clearSearchResults(adapter.pane1);
                    } else {
                        String s = adapter.currentZipPath;
                        if(adapter.isInZip && !StringsUtil.isEmpty(s)) {
                            char[] chars = s.toCharArray();
                            int i = 0;
                            for(char c : chars) if (c == File.separatorChar) i++;
                            boolean inOneLevelInZip = i < 2;
                            loadZipFolderInPane(((ZipEntryInfo)adapter.values[0]).getZipFile(), inOneLevelInZip ? "" : s.substring(0, s.lastIndexOf('/', s.lastIndexOf('/') - 1)), adapter.pane1, true);
                        } else if (adapter.isInZip) {
                            File zipFile = ((ZipEntryInfo) adapter.values[0]).getZipFile();
                            if (zipFile != null && isStagedArchive(zipFile) && navigateBack(adapter.pane1)) {
                                return;
                            }
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

    public void clearSearchResults(boolean pane1) {
        File dir = fileSearch.getLastSearchStartDir();
        if (dir == null) dir = pane1 ? pane1Folder : pane2Folder;
        loadFolderInPaneAsync(dir, pane1);
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
        // Back/forward/history taps: listing I/O off the main thread.
        loadFolderInPaneAsync(folder, pane1, addToHistory);
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
        invalidatePaneLoad(pane1);
        if (folder instanceof FTPFileWrapper) {
            loadFtpFolderInPane((FTPFileWrapper) folder, pane1);
            return;
        }
        if (!folder.isDirectory() && ArchiveUtil.isSupportedArchive(folder.getName())) {
            loadZipFolderInPane(folder, "", pane1, addToHistory);
            return;
        }
        saveCurrentPaneScroll(pane1);
        String targetKey = folder == null ? null : "file:" + folder.getAbsolutePath();
        int[] saved = samePlainFolderShown(folder, pane1) ? capturePaneScroll(pane1) : getSavedScrollForLocation(pane1, targetKey);
        if (!samePlainFolderShown(folder, pane1)) {
            clearGreen(pane1);
        }
        File[] files = listPlainFolder(folder, pane1);
        if (files == null) return;
        Arrays.sort(files);
        sortFiles(files, folder.getPath());
        bindListedFolder(folder, files, pane1, addToHistory);
        if (saved != null) restorePaneScroll(pane1, saved);
    }

    /** True when the pane currently lists this exact plain folder. */
    private boolean samePlainFolderShown(File folder, boolean pane1) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || !(pane.getAdapter() instanceof MainFilesArrayAdapter a) || a.isInZip) return false;
            File shown = pane1 ? pane1Folder : pane2Folder;
            return shown != null && folder != null
                    && shown.getAbsolutePath().equals(folder.getAbsolutePath());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Background-safe first half of a plain-folder load: storage listing plus
     * the elevated-backend fallbacks. Any dialog/toast is re-posted to the UI
     * thread so this may run on {@link #listingExecutor}.
     *
     * @return the unsorted listing, or null when the folder cannot be listed
     *         (the user has already been notified in that case).
     */
    private File[] listPlainFolder(File folder, boolean pane1) {
        boolean shizukuDir = ShizukuFile.isAndroidDataPath(folder);
        File[] files = null;
        String folderPath = folder.getAbsolutePath();
        boolean rootListingPath = "/".equals(folderPath) || RootManager.isRootOnlyPath(folderPath);
        if (rootListingPath && AccessManager.active(this) == AccessManager.Backend.ROOT && AccessManager.fileOpsOn(this)) {
            files = AccessManager.listWithStat(this, folder.getAbsolutePath());
            if (files != null) files = Arrays.stream(files).filter(this::isNotHidden).toArray(File[]::new);
        }
        if (files == null) files = folder.listFiles(this::isNotHidden);
        // A folder the app cannot read may still be listable (read-only) through the shell:
        // Android/data on API 30+, plus browsable system paths like /storage/emulated and
        // /system. Retry via Shizuku, then root, when the app got nothing or an empty
        // listing from a folder it cannot write to (an empty listing there means the OS hid
        // the contents rather than the folder really being empty).
        boolean appListingIncomplete = files == null || (files.length == 0 && !canWriteNormally(folder));
        if (appListingIncomplete) {
            File[] viaShizuku = ShizukuFile.tryList(this, folder);
            if (viaShizuku != null && (viaShizuku.length > 0 || files == null)) files = viaShizuku;
        }
        if (files == null || (files.length == 0 && appListingIncomplete)) {
            boolean elevated = AccessManager.fileOpsOn(this);
            if (!elevated) {
                try {
                    elevated = RootPermissionHelper.hasElevatedShell(this);
                } catch (Exception ignored) {
                }
            }
            if (elevated) {
                File[] viaRoot = AccessManager.listWithStat(this, folderPath);
                if (viaRoot != null) {
                    viaRoot = Arrays.stream(viaRoot)
                            .filter(this::isNotHidden)
                            .toArray(File[]::new);
                    if (viaRoot.length > 0 || files == null) files = viaRoot;
                }
                if ((files == null || files.length == 0) && (shizukuDir || rootListingPath || !folder.canRead())) {
                    try {
                        if (RootManager.getInstance(this).isRootAvailable()) {
                            File[] directRoot = RootManager.getInstance(this).listRootFilesWithStat(folderPath);
                            if (directRoot != null) {
                                directRoot = Arrays.stream(directRoot).filter(this::isNotHidden).toArray(File[]::new);
                                if (directRoot.length > 0 || files == null) files = directRoot;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                try {
                    if (RootManager.getInstance(this).isRootAvailable()) {
                        File[] directRoot = RootManager.getInstance(this).listRootFilesWithStat(folderPath);
                        if (directRoot != null) {
                            directRoot = Arrays.stream(directRoot).filter(this::isNotHidden).toArray(File[]::new);
                            if (directRoot.length > 0 || files == null) files = directRoot;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (files == null && shizukuDir && AccessManager.active(this) != AccessManager.Backend.ROOT) {
                boolean rootAvailable = false;
                try { rootAvailable = RootManager.getInstance(this).isRootAvailable(); } catch (Exception ignored) {}
                if (!rootAvailable) runOnUiThread(() -> {
                    try {
                        showShizukuGuideOnce(folder, pane1);
                    } catch (Exception ignored) {
                    }
                });
            }
            if (files == null) {
                runOnUiThread(() -> {
                    try {
                        Extensions.showMessage(this, getString(R.string.open_folder_failed, folder.getName()));
                    } catch (Exception ignored) {
                    }
                });
                return null;
            }
        }
        return files;
    }

    /**
     * Main-thread second half of a plain-folder load: everything after the
     * (already sorted) File[] is ready. Split out so cold start can list/sort
     * on {@link #listingExecutor} and only bind here.
     */
    private void bindListedFolder(File folder, File[] files, boolean pane1, boolean addToHistory) {
        // Callers pass a fully sorted array (Arrays.sort + sortFiles); the
        // build-button scan below relies on that order for binarySearch.
        try {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(pane1 ? "last_path_1" : "last_path_2", folder.getAbsolutePath()).apply();
        } catch (Exception ignored) {
        }
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
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                final boolean[] sign = new boolean[1];
                CheckBox autosign = content.findViewById(R.id.autosign);
                autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                content.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

                new MaterialAlertDialogBuilder(this)
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
        MainFilesArrayAdapter adapter = new MainFilesArrayAdapter(this, files, parent, pane1, false, null);
        pane.setAdapter(adapter);
        
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            String prefKey = pane1 ? "pane1_selected" : "pane2_selected";
            Set<String> savedSelection = settings.getStringSet(prefKey, null);
            if (savedSelection != null && !savedSelection.isEmpty()) {
                Set<Integer> positions = new HashSet<>();
                for (String s : savedSelection) {
                    try {
                        positions.add(Integer.parseInt(s));
                    } catch (Exception ignored) {}
                }
                adapter.restoreSelection(positions);
                settings.edit().remove(prefKey).apply();
            }
        } catch (Exception ignored) {}
        
        UiPrefs.playFolderTransition(pane);
        if ((pane1 ? lastPaneSelected == 1 : lastPaneSelected == 2)) setMultiSelectModeUI(false);
        updateNavigationButtons();
    }

    public void loadZipFolderInPane(File zipFile, String path, boolean pane1, boolean addToHistory) {
        loadZipFolderInPane(zipFile, path, pane1, addToHistory, null);
    }

    /**
     * @param afterBind optional main-thread hook running right after the new
     *                  adapter is bound (e.g. scroll restore). Null to skip.
     */
    public void loadZipFolderInPane(File zipFile, String path, boolean pane1, boolean addToHistory, Runnable afterBind) {
        invalidatePaneLoad(pane1);
        final long gen = pane1 ? paneLoadGen1 : paneLoadGen2;
        saveCurrentPaneScroll(pane1);
        String targetKey = zipFile == null ? null : "zip:" + zipFile.getAbsolutePath() + "!" + (path == null ? "" : path);
        final int[] saved = sameZipFolderShown(zipFile, path, pane1) ? capturePaneScroll(pane1) : getSavedScrollForLocation(pane1, targetKey);
        if (!sameZipFolderShown(zipFile, path, pane1)) {
            clearGreen(pane1);
        }
        listingExecutor.execute(() -> {
            final ZipRead read;
            try {
                read = readZipFolder(zipFile, path);
            } catch (Exception e) {
                handler.post(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) new ErrorUtil(this).showError(e);
                    } catch (Exception ignored) {
                    }
                });
                return;
            }
            try {
                sortZipEntries(read.entries, zipFile.getPath() + "!" + path);
            } catch (Exception ignored) {
            }
            handler.post(() -> {
                try {
                    if (isFinishing() || isDestroyed()) return;
                    if (gen != (pane1 ? paneLoadGen1 : paneLoadGen2)) return;
                    bindZipFolder(read, zipFile, path, pane1, addToHistory);
                } catch (Exception ignored) {
                    return;
                }
                if (saved != null) restorePaneScroll(pane1, saved);
                if (afterBind != null) {
                    try {
                        afterBind.run();
                    } catch (Exception ignored) {
                    }
                }
            });
        });
    }

    /** True when the pane currently lists this exact archive location. */
    private boolean sameZipFolderShown(File zipFile, String path, boolean pane1) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || !(pane.getAdapter() instanceof MainFilesArrayAdapter a) || !a.isInZip) return false;
            File shown = pane1 ? pane1Folder : pane2Folder;
            String inner = a.currentZipPath == null ? "" : a.currentZipPath;
            String target = path == null ? "" : path;
            return shown != null && zipFile != null
                    && shown.getAbsolutePath().equals(zipFile.getAbsolutePath())
                    && inner.equals(target);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Background-safe half of an archive listing: entry scan + filter. Works
     * for every browsable archive format — zip via the central directory, the
     * other formats through {@link ArchiveBrowseUtil}.
     */
    private ZipRead readZipFolder(File zipFile, String path) throws IOException {
        List<ZipEntryInfo> entries = new ArrayList<>();
        ZipEntryInfo parent = null;
        HashSet<String> seenDirs = new HashSet<>() {
        };
        {
            String parentPath = TextUtils.isEmpty(path) ? "" : path;
            if (!TextUtils.isEmpty(parentPath) && !parentPath.endsWith("/")) parentPath += "/";
            String parentFull = null;
            if (!TextUtils.isEmpty(path)) {
                String parentDir = new File(path).getParent();
                parentFull = (parentDir == null || parentDir.isEmpty())
                        ? "" : parentDir.replaceAll("/+$", "") + "/";
            }
            parent = new ZipEntryInfo("..", parentFull, true, 0L, 0L, zipFile);
            entries.add(parent);

            List<ZipEntryInfo> all = new ArrayList<>();
            for (ArchiveBrowseUtil.RawEntry re : ArchiveBrowseUtil.listEntries(zipFile)) {
                String entryPath = re.path();
                boolean isDir = re.isDirectory();
                String cleaned = isDir ? entryPath.replaceAll("/+$", "") : entryPath;
                String name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1);
                all.add(new ZipEntryInfo(name, entryPath, isDir, re.size(), re.lastModified(), zipFile));
            }
            String prefix = parentPath;
            Map<String, long[]> dirStats = new HashMap<>();
            for (ZipEntryInfo info : all) {
                String entryPath = info.getFullPath();
                if (!entryPath.startsWith(prefix) || entryPath.equals(prefix)) continue;
                String rest = entryPath.substring(prefix.length());
                int nextSlash = rest.indexOf('/');
                if (nextSlash != -1) {
                    String childFullPath = prefix + rest.substring(0, nextSlash + 1);
                    long[] st = dirStats.get(childFullPath);
                    if (st == null) {
                        st = new long[2];
                        dirStats.put(childFullPath, st);
                    }
                    long s = info.getSize();
                    if (s > 0) st[0] += s;
                    long t = info.getLastModified();
                    if (t > st[1]) st[1] = t;
                }
            }
            for (ZipEntryInfo info : all) {
                String entryPath = info.getFullPath();
                if (!entryPath.startsWith(prefix) || entryPath.equals(prefix)) continue;
                String rest = entryPath.substring(prefix.length());
                int nextSlash = rest.indexOf('/');
                if (nextSlash == -1) {
                    if (isNotHidden(info)) entries.add(info);
                } else {
                    String childDirName = rest.substring(0, nextSlash + 1);
                    String childFullPath = prefix + childDirName;
                    if (seenDirs.add(childFullPath)) {
                        String cleaned = childFullPath.replaceAll("/+$", "");
                        ZipEntryInfo info2 = new ZipEntryInfo(
                                cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1),
                                childFullPath, true, 0L, 0L, zipFile);
                        long[] st = dirStats.get(childFullPath);
                        if (st != null) info2.setAggregated(st[0], st[1]);
                        if (isNotHidden(info2)) entries.add(info2);
                    }
                }
            }
        }
        return new ZipRead(entries, parent);
    }

    private record ZipRead(List<ZipEntryInfo> entries, ZipEntryInfo parent) {
    }

    /** Main-thread half of a zip listing: state, header, adapter bind. */
    private void bindZipFolder(ZipRead read, File zipFile, String path, boolean pane1, boolean addToHistory) {
        List<ZipEntryInfo> entries = read.entries;
        ZipEntryInfo parent = read.parent;
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
        if (pane != null) {
            boolean isCurrentPane = pane1 ? lastPaneSelected == 1 : lastPaneSelected == 2;
            pane.setAdapter(new MainFilesArrayAdapter(this, entries.toArray(new ZipEntryInfo[0]), parent, pane1, true, path));
            UiPrefs.playFolderTransition(pane);
            // Fresh listing = no selection in this pane; sync the bottom bar if it's current.
            if (isCurrentPane) setMultiSelectModeUI(false);
            updateNavigationButtons();
        }
    }

    public void loadFolderInPane(File folder, boolean pane1) {
        loadFolderInPane(folder, pane1, true);
    }

    /** Bumps the generation so any in-flight async load for this pane is dropped on arrival. */
    private void invalidatePaneLoad(boolean pane1) {
        if (pane1) paneLoadGen1++;
        else paneLoadGen2++;
    }

    /**
     * Cold-start / non-interactive plain-folder load: listing, filtering and
     * sorting run on {@link #listingExecutor}; only the adapter bind runs on
     * the main thread. FTP and archive folders fall back to the synchronous
     * path. A generation guard keeps a slow listing from overwriting a newer
     * pane (e.g. the user navigated before it finished).
     */
    public void loadFolderInPaneAsync(File folder, boolean pane1, boolean addToHistory) {
        loadFolderInPaneAsync(folder, pane1, addToHistory, null);
    }

    public void loadFolderInPaneAsync(File folder, boolean pane1) {
        loadFolderInPaneAsync(folder, pane1, true);
    }

    /**
     * @param afterBind optional main-thread hook running right after the new
     *                  adapter is bound (e.g. scroll restore). Null to skip.
     */
    public void loadFolderInPaneAsync(File folder, boolean pane1, boolean addToHistory, Runnable afterBind) {
        if (folder instanceof FTPFileWrapper
                || (!folder.isDirectory() && ArchiveUtil.isSupportedArchive(folder.getName()))) {
            loadFolderInPane(folder, pane1, addToHistory);
            if (afterBind != null) {
                try {
                    afterBind.run();
                } catch (Exception ignored) {
                }
            }
            return;
        }
        invalidatePaneLoad(pane1);
        final long gen = pane1 ? paneLoadGen1 : paneLoadGen2;
        final String folderPath = folder.getAbsolutePath();
        saveCurrentPaneScroll(pane1);
        String targetKey = folder == null ? null : "file:" + folder.getAbsolutePath();
        final int[] saved = samePlainFolderShown(folder, pane1) ? capturePaneScroll(pane1) : getSavedScrollForLocation(pane1, targetKey);
        if (!samePlainFolderShown(folder, pane1)) {
            clearGreen(pane1);
        }
        listingExecutor.execute(() -> {
            final File[] files;
            try {
                files = listPlainFolder(folder, pane1);
            } catch (Exception ignored) {
                return;
            }
            if (files == null) return;
            try {
                Arrays.sort(files);
                sortFiles(files, folderPath);
            } catch (Exception ignored) {
            }
            handler.post(() -> {
                try {
                    if (isFinishing() || isDestroyed()) return;
                    if (gen != (pane1 ? paneLoadGen1 : paneLoadGen2)) return;
                    bindListedFolder(folder, files, pane1, addToHistory);
                } catch (Exception ignored) {
                    return;
                }
                if (saved != null) restorePaneScroll(pane1, saved);
                if (afterBind != null) {
                    try {
                        afterBind.run();
                    } catch (Exception ignored) {
                    }
                }
                // Persist the instant-paint cache off the main thread, but only
                // for folders a future cold start will actually open (the home
                // folder, or anything when "last opened" mode is on). Caching
                // every browsed folder would paint the wrong folder on next
                // start when home mode is on.
                listingExecutor.execute(() -> {
                    try {
                        String expect = expectedStartupPath(pane1, pane1 ? homeDir1 : homeDir2);
                        if (expect == null || !expect.equals(folderPath)) return;
                        PaneCache.save(MainActivity.this, pane1, folderPath, files);
                    } catch (Exception ignored) {
                    }
                });
            });
        });
    }

    /**
     * Paints the last cached listing for a pane slot: no stat, no sort, one
     * tiny JSON read. Returns true when something was painted. The real
     * listing (loadFolderInPaneAsync) replaces it right after.
     */
    private boolean paintCachedPane(boolean pane1, String expectedPath) {
        try {
            PaneCache.Snapshot snap = PaneCache.load(this, pane1);
            if (snap == null || snap.dirPath == null || snap.dirPath.isEmpty()) return false;
            if (expectedPath == null || !expectedPath.equals(snap.dirPath)) return false;
            File dir = new File(snap.dirPath);
            int n = snap.names.size();
            File[] files = new File[n];
            for (int i = 0; i < n; i++) files[i] = new File(dir, snap.names.get(i));
            if (pane1) {
                currentPane1Files = files;
                pane1Folder = dir;
            } else {
                currentPane2Files = files;
                pane2Folder = dir;
            }
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null) return false;
            File parent = dir.getParentFile() != null ? dir.getParentFile() : dir;
            pane.setAdapter(new MainFilesArrayAdapter(this, files, parent, pane1, false, null));
            // Header now; folder counts come from the snapshot flags so no
            // per-file stat happens on the main thread.
            try {
                TextView pathView = findViewById(R.id.currentFolderPath);
                if (pathView != null) {
                    pathView.setText(dir.getPath());
                    uiHelper.scrollTextView(pathView);
                }
                int folders = 0;
                for (int i = 0; i < snap.isDir.size() && i < n; i++) {
                    try {
                        if (snap.isDir.get(i)) folders++;
                    } catch (Exception ignored) {
                    }
                }
                TextView countView = findViewById(R.id.folderCount);
                if (countView != null) countView.setText(rss.getString(R.string.folders_files_x, folders, n - folders));
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Runs {@code r} on the main thread once the message queue goes idle
     * (first paint done), with a time-based fallback so it still runs on busy
     * devices. Use for everything not visible at open: backend warm-ups, root
     * probe, update check.
     */
    private void runWhenIdle(Runnable r, long fallbackMs) {
        if (r == null || isFinishing() || isDestroyed()) return;
        final boolean[] done = new boolean[1];
        Runnable once = () -> {
            if (done[0]) return;
            done[0] = true;
            try {
                if (!isFinishing() && !isDestroyed()) r.run();
            } catch (Exception ignored) {
            }
        };
        try {
            Looper.myQueue().addIdleHandler(() -> {
                handler.post(once);
                return false;
            });
        } catch (Exception ignored) {
            handler.post(once);
            return;
        }
        try {
            handler.postDelayed(once, fallbackMs);
        } catch (Exception ignored) {
        }
    }

    private void runWhenIdle(Runnable r) {
        runWhenIdle(r, 400);
    }

    private boolean canListViaRoot(File folder) {
        try {
            return AccessManager.fileOpsOn(this) && folder != null && AccessManager.exists(this, folder.getAbsolutePath());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True when the app uid itself can write to this directory. Read-only corners such as
     * /storage/emulated or /system report false here, which is the signal that an empty
     * listing means "hidden by the OS" and deserves an elevated/Shizuku retry.
     */
    private static boolean canWriteNormally(File folder) {
        try {
            return folder != null && folder.canWrite();
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
            onPositive = ShizukuShell::requestPermission;
        }
        // Once the binder arrives (user started Shizuku / granted), reload this folder automatically.
        ShizukuShell.onBinderReceived(() -> runOnUiThread(() -> {
            if (ShizukuShell.isGranted()) loadFolderInPane(folder, pane1, false);
        }));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Shizuku")
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> onPositive.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean showMsgOnLongPress(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        CharSequence contentDescription = v.getContentDescription();
        if(!TextUtils.isEmpty(contentDescription)) Extensions.showMessage(this, contentDescription);
        return false;
    }

    public void reloadCurrentFolder() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::reloadCurrentFolder);
            return;
        }
        // reloadPane already cascades to the other pane when both show the same folder.
        reloadPane(lastPaneSelected == 1, true);
    }

    private void setupPullToRefresh() {
        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshPane1)).setOnRefreshListener(() -> refreshPane(true));
        ((SwipeRefreshLayout) findViewById(R.id.swipeRefreshPane2)).setOnRefreshListener(() -> refreshPane(false));
    }

    /**
     * Refreshes the given pane. When both panes are pointing at the same folder (and the same
     * location inside an archive) the other pane is refreshed too, so the dual view stays in sync.
     * This is user-initiated (pull-to-refresh), so scroll is reset to the top.
     */
    public void refreshPane(boolean pane1) {
        clearGreen(pane1);
        if (panesShowSameFolder()) {
            clearGreen(!pane1);
            try {
                ((SwipeRefreshLayout) findViewById(pane1 ? R.id.swipeRefreshPane2 : R.id.swipeRefreshPane1)).setRefreshing(false);
            } catch (Exception ignored) { }
        }
        reloadPane(pane1, false);
        try {
            ((SwipeRefreshLayout) findViewById(pane1 ? R.id.swipeRefreshPane1 : R.id.swipeRefreshPane2)).setRefreshing(false);
        } catch (Exception ignored) { }
    }

    public void reloadPane(boolean pane1) {
        reloadPane(pane1, false);
    }

    /**
     * Reloads a pane, optionally preserving the current scroll position.
     * Programmatic (non-user) refreshes should pass true so the list
     * does not jump to the top while the user is browsing.
     */
    public void reloadPane(boolean pane1, boolean preserveScroll) {
        // When both panes point at the same listing, a change made through
        // either one is visible in both, so the other pane has to be reloaded
        // too. Decided before the first reload, since that rewrites the pane's
        // folder fields.
        boolean alsoOther = panesShowSameFolder();
        reloadOnePane(pane1, preserveScroll);
        if (alsoOther) reloadOnePane(!pane1, preserveScroll);
    }

    private void reloadOnePane(boolean pane1, boolean preserveScroll) {
        int[] saved = preserveScroll ? capturePaneScroll(pane1) : null;
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
        if (saved != null) restorePaneScroll(pane1, saved);
    }

    /** Captures first-visible position + offset so a refresh can restore it. */
    private int[] capturePaneScroll(boolean pane1) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || pane.getLayoutManager() == null) return null;
            if (pane.getLayoutManager() instanceof LinearLayoutManager lm) {
                int pos = lm.findFirstVisibleItemPosition();
                if (pos == RecyclerView.NO_POSITION) return null;
                View v = lm.findViewByPosition(pos);
                return new int[]{pos, v == null ? 0 : v.getTop()};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void restorePaneScroll(boolean pane1, int[] saved) {
        if (saved == null) return;
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || !(pane.getLayoutManager() instanceof LinearLayoutManager lm)) return;
            handler.post(() -> {
                try {
                    RecyclerView.Adapter<?> adapter = pane.getAdapter();
                    int count = adapter == null ? 0 : adapter.getItemCount();
                    int pos = count <= 0 ? 0 : Math.max(0, Math.min(saved[0], count - 1));
                    lm.scrollToPositionWithOffset(pos, saved[1]);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    public boolean isSameLocationInPanes() {
        if (pane1Folder == null || pane2Folder == null) return false;
        if (!pane1Folder.equals(pane2Folder)) return false;
        try {
            RecyclerView p1 = findViewById(R.id.listViewPane1);
            RecyclerView p2 = findViewById(R.id.listViewPane2);
            if (p1 != null && p2 != null
                    && p1.getAdapter() instanceof MainFilesArrayAdapter a1
                    && p2.getAdapter() instanceof MainFilesArrayAdapter a2) {
                if (a1.isInZip != a2.isInZip) return false;
                if (a1.isInZip) {
                    return Objects.equals(a1.currentZipPath, a2.currentZipPath);
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    private final Map<String, int[]> folderScrollPositions1 = new HashMap<>();
    private final Map<String, int[]> folderScrollPositions2 = new HashMap<>();

    public void saveCurrentPaneScroll(boolean pane1) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || pane.getAdapter() == null) return;
            RecyclerView.Adapter<?> a = pane.getAdapter();
            String key = null;
            if (a instanceof MainFilesArrayAdapter mfa) {
                if (mfa.isInZip) {
                    File zip = pane1 ? pane1Folder : pane2Folder;
                    if (zip != null) key = "zip:" + zip.getAbsolutePath() + "!" + (mfa.currentZipPath == null ? "" : mfa.currentZipPath);
                } else {
                    File shown = pane1 ? pane1Folder : pane2Folder;
                    if (shown != null) key = "file:" + shown.getAbsolutePath();
                }
            }
            if (key != null) {
                int[] scroll = capturePaneScroll(pane1);
                if (scroll != null) {
                    (pane1 ? folderScrollPositions1 : folderScrollPositions2).put(key, scroll);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public int[] getSavedScrollForLocation(boolean pane1, String key) {
        if (key == null) return null;
        try {
            return (pane1 ? folderScrollPositions1 : folderScrollPositions2).get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void highlightAndFocusItems(boolean pane1, Collection<String> names) {
        if (names == null || names.isEmpty()) return;
        for (String name : names) {
            if (name != null) markGreen(pane1, name);
        }
        boolean syncOther = isSameLocationInPanes();
        if (syncOther) {
            for (String name : names) {
                if (name != null) markGreen(!pane1, name);
            }
        }
        reloadPane(pane1, false);
        handler.postDelayed(() -> focusFirstItem(pane1, names), 120);
        if (syncOther) {
            reloadPane(!pane1, false);
            handler.postDelayed(() -> focusFirstItem(!pane1, names), 120);
        }
    }

    public void highlightAndFocusItem(boolean pane1, String name) {
        if (name != null) highlightAndFocusItems(pane1, Collections.singletonList(name));
    }

    private void focusFirstItem(boolean pane1, Collection<String> names) {
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null || !(pane.getLayoutManager() instanceof LinearLayoutManager lm)) return;
            RecyclerView.Adapter<?> adapter = pane.getAdapter();
            if (!(adapter instanceof MainFilesArrayAdapter filesAdapter)) return;
            int minPos = Integer.MAX_VALUE;
            for (String name : names) {
                if (name == null) continue;
                int pos = filesAdapter.findPositionByName(name);
                if (pos >= 0 && pos < minPos) {
                    minPos = pos;
                }
            }
            if (minPos != Integer.MAX_VALUE) {
                lm.scrollToPositionWithOffset(minPos, 0);
            }
        } catch (Exception ignored) {
        }
    }

    public void refreshPaneAfterChange(boolean pane1, String followName) {
        if (followName != null) {
            highlightAndFocusItem(pane1, followName);
        } else {
            reloadPane(pane1, true);
        }
    }

    public void flashPaneItem(boolean pane1, String name) {
        if (name != null) {
            highlightAndFocusItem(pane1, name);
        }
    }

    private final Set<String> greenNames1 = new HashSet<>();
    private final Set<String> greenNames2 = new HashSet<>();

    public void markGreen(boolean pane1, String name) {
        if (name == null) return;
        try {
            (pane1 ? greenNames1 : greenNames2).add(name);
        } catch (Exception ignored) {
        }
    }

    public boolean isGreen(boolean pane1, String name) {
        if (name == null) return false;
        try {
            return (pane1 ? greenNames1 : greenNames2).contains(name);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void clearGreen(boolean pane1) {
        try {
            Set<String> set = pane1 ? greenNames1 : greenNames2;
            if (set.isEmpty()) return;
            set.clear();
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane != null && pane.getAdapter() != null) pane.getAdapter().notifyDataSetChanged();
        } catch (Exception ignored) {
        }
    }

    public record ArchiveParent(File outerZip, String entryPath) {
    }

    private final Map<String, ArchiveParent> archiveParents = new HashMap<>();
    public void noteArchiveParent(File staged, File outer, String entryPath) {
        if (staged == null || outer == null || entryPath == null) return;
        try {
            archiveParents.put(staged.getCanonicalPath(), new ArchiveParent(outer, entryPath));
        } catch (Exception ignored) {
        }
    }

    public ArchiveParent archiveParentOf(String stagedPath) {
        if (stagedPath == null) return null;
        try {
            return archiveParents.get(new File(stagedPath).getCanonicalPath());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isStagedArchive(File f) {
        if (f == null) return false;
        try {
            String p = f.getCanonicalPath();
            String cache = getCacheDir().getCanonicalPath();
            if (p.equals(cache) || p.startsWith(cache + File.separator)) return true;
            String codeCache = getCodeCacheDir().getCanonicalPath();
            if (p.equals(codeCache) || p.startsWith(codeCache + File.separator)) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Steps out of an archive that is currently open at its root. A staged
     * archive is a temp copy of an entry that lives somewhere else (a nested
     * zip, an entry opened from another archive), so history is the only
     * sensible destination; a plain archive goes to the folder holding it.
     */
    public void leaveArchive(File zipFile, boolean pane1) {
        if (zipFile == null) return;
        if (isStagedArchive(zipFile) && navigateBack(pane1)) return;
        File outer = zipFile.getParentFile();
        if (outer != null) loadFolderInPane(outer, pane1);
    }

    public MainFilesArrayAdapter getCurrentFilesAdapter() {
        return paneFilesAdapter(lastPaneSelected == 1);
    }

    public MainFilesArrayAdapter paneFilesAdapter(boolean pane1) {
        RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        if (pane != null && pane.getAdapter() instanceof MainFilesArrayAdapter adapter) return adapter;
        return null;
    }

    /** True when both panes list the same folder and the same location inside an archive. */
    public boolean panesShowSameFolder() {
        File first = pane1Folder;
        File second = pane2Folder;
        if (first == null || !first.equals(second)) return false;
        MainFilesArrayAdapter a = paneFilesAdapter(true);
        MainFilesArrayAdapter b = paneFilesAdapter(false);
        if (a == null || b == null) return false;
        if (a.isInZip != b.isInZip) return false;
        return Objects.equals(a.currentZipPath, b.currentZipPath);
    }

    public void refreshAllPanes() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::refreshAllPanes);
            return;
        }
        // Programmatic refresh (onResume / returning from editors): keep scroll
        // position, but never block the main thread on storage I/O.
        try { reloadPaneAsync(true, true); } catch (Exception ignored) { }
        try { reloadPaneAsync(false, true); } catch (Exception ignored) { }
    }

    /**
     * onResume/programmatic refresh without main-thread I/O: plain folders
     * re-list on {@link #listingExecutor} with scroll preserved; archives and
     * FTP keep the synchronous path. Pull-to-refresh keeps sync
     * {@link #reloadPane} for immediate spinner semantics.
     */
    private void reloadPaneAsync(boolean pane1, boolean preserveScroll) {
        final int[] saved = preserveScroll ? capturePaneScroll(pane1) : null;
        try {
            RecyclerView pane = findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (pane == null) return;
            RecyclerView.Adapter<?> adapter = pane.getAdapter();
            if (adapter instanceof MainFilesArrayAdapter filesAdapter) {
                if (filesAdapter.isInZip) {
                    File zipFile = pane1 ? pane1Folder : pane2Folder;
                    if (zipFile == null) return;
                    String zipPath = filesAdapter.currentZipPath == null ? "" : filesAdapter.currentZipPath;
                    loadZipFolderInPane(zipFile, zipPath, pane1, false,
                            saved != null ? () -> restorePaneScroll(pane1, saved) : null);
                    return;
                }
                File folder = pane1 ? pane1Folder : pane2Folder;
                if (folder == null) return;
                loadFolderInPaneAsync(folder, pane1, false,
                        saved != null ? () -> restorePaneScroll(pane1, saved) : null);
            } else if (adapter instanceof FtpFilesArrayAdapter) {
                reloadPane(pane1, preserveScroll);
            }
            // No adapter yet (cold start): nothing to refresh.
        } catch (Exception ignored) {
        }
    }

    public void setCurrentFolder(File curr, File[] files) {
        if (files == null) {
            updateFolderCount(0, 0, curr);
            return;
        }
        // Count from the already-listed array (root listings carry
        // isDirectory via RootFile). Re-listing with curr.listFiles()
        // returns null on root-only dirs and would show 0 folders.
        // Skip the ".." up-entry: getShownFiles() includes the parent
        // folder at index 0, which is not inside curr. A raw listing
        // from loadFolderInPane never contains curr's parent, so also
        // guard by parent equality instead of blindly skipping index 0.
        File upDir = null;
        try {
            upDir = curr == null ? null : curr.getParentFile();
        } catch (Exception ignored) {
        }
        // At filesystem root there is no parent; the adapter reuses the
        // folder itself as the ".." entry, so skip that too.
        final boolean skipFirst = files.length > 0 && (files[0] != null
                && (files[0].equals(upDir) || (upDir == null && files[0].equals(curr))));
        // One isDirectory() per entry is one stat syscall per entry. Doing that
        // on the main thread blocked the first frame of big listings such as
        // /vendor, so count in the background and post just the two numbers.
        final File folder = curr;
        listingExecutor.execute(() -> {
            int foldersCount = 0;
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                try {
                    if (f == null) continue;
                    if (i == 0 && skipFirst) continue;
                    if (f.isDirectory()) foldersCount++;
                } catch (Exception ignored) {
                }
            }
            int listed = files.length - (skipFirst ? 1 : 0);
            updateFolderCount(foldersCount, listed - foldersCount, folder);
        });
    }

    private void updateFolderCount(int foldersCount, int totalCount, File curr) {
        TextView currentFolderPath = findViewById(R.id.currentFolderPath);
        final int finalFoldersCount = foldersCount;
        final int finalTotalCount = totalCount;
        final String path = curr == null ? "" : curr.getPath();
        handler.post(() -> {
            currentFolderPath.setText(path);
            uiHelper.scrollTextView(currentFolderPath);
            this.<TextView>findViewById(R.id.folderCount)
                    .setText(rss.getString(R.string.folders_files_x, finalFoldersCount, finalTotalCount));
        });
    }

    public void setCurrentPane(int pane) {
        lastPaneSelected = pane;
        paneHighlight.onPaneSelected(pane, true);
        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        boolean b = a instanceof MainFilesArrayAdapter;
        findViewById(R.id.syncPaneButton).setEnabled(b);
        if (b) {
            MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
            if (adapter.isInZip) {
                // Show the full archive location (zip!/inner), not just the
                // inner path: currentZipPath is "" at the archive root, which
                // would otherwise blank the header when the pane is selected.
                File zipFile = pane == 1 ? pane1Folder : pane2Folder;
                String inner = adapter.currentZipPath == null ? "" : adapter.currentZipPath;
                String display = zipFile == null ? inner : zipFile.getPath() + "!" + inner;
                setCurrentFolder(display, Arrays.asList(adapter.values));
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
        paneHighlight.onPaneSelected(pane, true);
        updateNavigationButtons();
    }

    public void setCurrentFolder(String path, List<?> files) {
        new Thread(() -> {

            //CollectionsUtils.removeIf(files, (Predicate<Object>) o -> o instanceof ZipEntryInfo && ((ZipEntryInfo) o).isDirectory());
            int foldersCount = 0;
            for(Object item : files) {
                // Skip the ".." up-entry (fullPath == null): it is not inside
                // this folder and must not inflate the folder count.
                if(item instanceof ZipEntryInfo z) {
                    if (z.getFullPath() == null) continue;
                    if (z.isDirectory()) foldersCount++;
                }
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // FLAG_ACTIVITY_CLEAR_TOP from the share entry reuses this instance;
        // without this, getIntent() in onResume would keep the old extras and
        // a locate request would be silently dropped.
        try {
            setIntent(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CURRENT = new WeakReference<>(this);
        // Pane highlight style may have changed in Settings.
        try {
            paneHighlight.attach();
            paneHighlight.reload();
        } catch (Exception ignored) {
        }
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
                // exists() is false under /data for the app uid, so fall back to the elevated
                // backend before giving up on a root/shizuku-resolved path.
                boolean reachable = folder != null && (folder.exists()
                        || AccessManager.exists(this, folder.getAbsolutePath()));
                if (reachable) {
                    loadFolderInPane(folder, true);
                    Extensions.showMessage(this, rss.getString(R.string.loaded_X, target.getPath()));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            refreshSidebarAsync(getSidebarSectionOrder());
        } catch (Exception ignored) {
        }
        // Returning from editors/viewers (started with startActivity, not for result)
        // leaves listings stale — re-list the visible panes off the main thread,
        // preserving scroll position.
        try {
            refreshAllPanes();
        } catch (Exception ignored) {
        }
        try {
            fileSearch.onActivityResume();
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
        // Drop any in-flight background listing so it never touches dead views.
        invalidatePaneLoad(true);
        invalidatePaneLoad(false);
        try {
            listingExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
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
            // This activity owns the layout-inspector session (see
            // startLayoutInspector): stop its sticky UsageStats polling so it
            // doesn't keep running in the background after the app is gone.
            try {
                stopService(new Intent(this, PackageMonitoringService.class));
            } catch (Exception ignored) {
            }
            try {
                DataRepository.getInstance().updateStatus(false);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    public void showSearchDialog() {
        fileSearch.showSearchDialog();
    }

    /** Recycle bin browser: restore / permanently delete binned items. */
    private void showRecycleBinDialog() {
        new Thread(() -> {
            RecycleBin.purgeExpired(this);
            List<RecycleBin.Entry> entries =
                    RecycleBin.entries(this);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (entries.isEmpty()) {
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(R.string.recycle_bin)
                            .setMessage(R.string.recycle_bin_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                    return;
                }
                CharSequence[] labels = new CharSequence[entries.size()];
                DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                for (int i = 0; i < entries.size(); i++) {
                    RecycleBin.Entry e = entries.get(i);
                    String when = e.deletedAt > 0 ? df.format(new Date(e.deletedAt)) : "";
                    labels[i] = e.displayName() + (when.isEmpty() ? "" : "\n" + when)
                            + (e.originPath == null || e.originPath.isEmpty()
                            ? "" : "\n" + rss.getString(R.string.deleted_from_x, e.originPath));
                }
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(getString(R.string.recycle_bin) + " (" + entries.size() + ")")
                        .setItems(labels, (dialog, which) ->
                                showRecycleBinItemDialog(entries.get(which)))
                        .setNeutralButton(R.string.empty_recycle_bin, (dialog, which) ->
                                new MaterialAlertDialogBuilder(MainActivity.this)
                                        .setTitle(R.string.empty_recycle_bin)
                                        .setMessage(R.string.empty_recycle_bin_confirm)
                                        .setPositiveButton(R.string.delete, (d, w) -> {
                                            RecycleBin.emptyBin(MainActivity.this);
                                            Extensions.showMessage(MainActivity.this, R.string.recycle_bin_emptied);
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show())
                        .setNegativeButton(android.R.string.cancel, null)
                        .create());
            });
        }).start();
    }

    private void showRecycleBinItemDialog(RecycleBin.Entry entry) {
        String[] actions = {rss.getString(R.string.restore), rss.getString(R.string.delete_forever)};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(entry.displayName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        new Thread(() -> {
                            RecycleBin.RestoreResult res = RecycleBin.restoreEntry(MainActivity.this, entry);
                            handler.post(() -> {
                                if (res != null && res.restoredFile != null) {
                                    File restored = res.restoredFile;
                                    Extensions.showMessage(MainActivity.this, rss.getString(R.string.restore_to_x, restored.getAbsolutePath()));
                                    File parent = restored.getParentFile();
                                    if (parent != null
                                            && (parent.equals(pane1Folder) || parent.equals(pane2Folder))) {
                                        refreshPaneAfterChange(parent.equals(pane1Folder), restored.getName());
                                    }
                                } else {
                                    Extensions.showMessage(MainActivity.this, R.string.restore_failed);
                                }
                            });
                        }).start();
                    } else {
                        new Thread(() -> {
                            boolean ok = RecycleBin.deleteEntry(MainActivity.this, entry);
                            if (!ok) handler.post(() ->
                                    Extensions.showMessage(MainActivity.this, R.string.delete_forever_failed));
                        }).start();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    public void showFindInFilesDialog() {
        fileSearch.showFindInFilesDialog();
    }

    public void showSettingsDialog() {
        startActivity(new Intent(this, MainSettingsActivity.class));
    }

    /** Re-applies the active-pane highlight style after it changes in Settings. */
    public void reloadPaneHighlight() {
        try {
            paneHighlight.reload();
        } catch (Exception ignored) {
        }
    }

    private boolean isNotHidden(File f) {
        return sortFilter.isNotHidden(f);
    }

    private boolean isNotHidden(ZipEntryInfo e) {
        return sortFilter.isNotHidden(e);
    }

    public void showSortDialog() {
        sortFilter.showSortDialog();
    }

    private void showEditHiddenFilesDialog() {
        sortFilter.showEditHiddenFilesDialog();
    }

    /** Right (more-options) drawer; rows are built by RightMenuController. */
    public void refreshRightMenuDrawer() {
        rightMenu.build();
    }

    public void showHiddenFilesMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        MenuItem sysItem = menu.add(0, 1, 0, getString(R.string.menu_show_system_hidden));
        sysItem.setCheckable(true).setChecked(prefs.getBoolean("show_system_hidden", false));
        MenuItem manItem = menu.add(0, 2, 0, getString(R.string.menu_show_manual_hidden));
        manItem.setCheckable(true).setChecked(prefs.getBoolean("show_manually_hidden", false));

        RecyclerView.Adapter a = getCurrentPane().getAdapter();
        if (a instanceof MainFilesArrayAdapter adapter) {
            MenuItem hideSel = menu.add(0, 3, 0, getString(R.string.menu_hide_selected));
            hideSel.setEnabled(adapter.isMultiSelectMode());
            menu.add(0, 4, 0, getString(R.string.edit_hidden_files)).setIcon(R.drawable.baseline_drive_file_rename_outline_24);
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    boolean isChecked = !item.isChecked();
                    item.setChecked(isChecked);
                    prefs.edit().putBoolean("show_system_hidden", isChecked).apply();
                    reloadCurrentFolder();
                    break;
                }
                case 2: {
                    boolean isChecked = !item.isChecked();
                    item.setChecked(isChecked);
                    prefs.edit().putBoolean("show_manually_hidden", isChecked).apply();
                    reloadCurrentFolder();
                    break;
                }
                case 3: {
                    if (a instanceof MainFilesArrayAdapter adapter) {
                        Set<String> manualHidden = new HashSet<>(prefs.getStringSet("manually_hidden_files", new HashSet<>()));
                        for (Object obj : adapter.getSelectedFiles()) {
                            if (obj instanceof File)
                                manualHidden.add(((File) obj).getPath());
                            else if (obj instanceof ZipEntryInfo)
                                manualHidden.add(((ZipEntryInfo) obj).getFullPath());
                        }
                        prefs.edit().putStringSet("manually_hidden_files", manualHidden).apply();
                        adapter.clearSelection();
                        reloadCurrentFolder();
                    }
                    break;
                }
                case 4:
                    showEditHiddenFilesDialog();
                    break;
            }
            return true;
        });
        popup.show();
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
        invalidatePaneLoad(pane1);
        ftp.loadFtpFolderInPane(folder, pane1);
    }
}