/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import io.github.ratul.topactivity.App;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.extensions.ActivityExtensions;
import io.github.ratul.topactivity.extensions.GenericExtensions;
import io.github.ratul.topactivity.manager.AppUpdateManager;
import io.github.ratul.topactivity.manager.ServiceManager;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.services.PackageMonitoringService;
import io.github.ratul.topactivity.utils.AutostartUtil;
import io.github.ratul.topactivity.utils.DatabaseUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    public AppUpdateManager appUpdateManager;
    public CoordinatorLayout baseView;
    public FloatingActionButton fabStart;
    public SettingsFragment fragment;

    private boolean isServiceBound = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
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

    private final DataRepository.StateListener stateListener = state ->
            fabStart.post(() -> GenericExtensions.setStatus(fabStart, state.isRunning()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupToolbar();
        setupFab();
        setupPreferences();
        //setupAutoUpdate()

        if (handleQsTileIntent(getIntent())) moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (isServiceBound) {
            getApplicationContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        DataRepository.getInstance().removeListener(stateListener);
        DataRepository.getInstance().updateStatus(false);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleQsTileIntent(intent)) moveTaskToBack(true);
    }

    private void initViews() {
        fabStart = findViewById(R.id.start);
        baseView = findViewById(R.id.main);
        fragment = (SettingsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.preferences_container);
        appUpdateManager = new AppUpdateManager(this);

        ViewCompat.setOnApplyWindowInsetsListener(baseView, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.github) {
                ActivityExtensions.openLink(this, App.REPO_URL);
                return true;
            }
            return false;
        });
    }

    private void setupFab() {
        DataRepository.getInstance().addListener(stateListener);
        GenericExtensions.setStatus(fabStart, DataRepository.getInstance().getAppState().isRunning());
        fabStart.setOnClickListener(v -> onFabStartClicked());
    }

    private void setupPreferences() {
        fragment.enableAutostart.setVisible(AutostartUtil.isAutoStartPermissionAvailable(this));
        fragment.enableAutostart.setOnPreferenceClickListener(preference -> {
            AutostartUtil.requestAutoStartPermission(this);
            return true;
        });
        fragment.checkUpdate.setOnPreferenceClickListener(preference -> {
            ActivityExtensions.showMessage(this, R.string.checking_for_update);
            appUpdateManager.checkForUpdate(false);
            return true;
        });
    }

    private void setupAutoUpdate() {
        if (DatabaseUtil.isFirstRun()) showAutoUpdatePolicyDialog();
        if (DatabaseUtil.autoUpdate()) appUpdateManager.checkForUpdate(true);
    }

    private void onFabStartClicked() {
        if (DataRepository.getInstance().getAppState().isRunning()) {
            DataRepository.getInstance().updateStatus(false);
            return;
        }

        if (!requestMissingPermissions()) return;

        DataRepository.getInstance().updateStatus(true);
        Intent intent = new Intent(this, PackageMonitoringService.class);
        getApplicationContext().startService(intent);
        getApplicationContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        new ServiceManager(this).show();
        DataRepository.getInstance().updateData(getPackageName(), this.getClass().getName());
    }

    private boolean handleQsTileIntent(Intent intent) {
        if (!intent.getBooleanExtra(EXTRA_FROM_QS_TILE, false)) return false;
        onFabStartClicked();
        return DataRepository.getInstance().getAppState().isRunning();
    }

    private boolean requestMissingPermissions() {
        boolean needsOverlay = DatabaseUtil.getServiceMode().equals("0") && !ActivityExtensions.isSystemOverlayGranted(this);

        List<Runnable> actions = new ArrayList<>();
        if (ActivityExtensions.isAccessibilityNotStarted()) {
            actions.add(this::requestAccessibilityPermission);
        }
        if (!ActivityExtensions.isUsageStatsGranted(this)) {
            actions.add(this::requestUsageStatsPermission);
        }
        if (!ActivityExtensions.isNotificationGranted(this)) {
            actions.add(this::requestNotificationPermission);
        }
        if (needsOverlay) {
            actions.add(this::requestSystemOverlayPermission);
        }

        for (Runnable action : actions) {
            action.run();
        }
        return actions.isEmpty();
    }

    private void requestNotificationPermission() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void requestSystemOverlayPermission() {
        showPermissionDialog(
                R.string.system_overlay_title,
                getString(R.string.system_overlay_description, getString(R.string.app_name)),
                () -> startActivity(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .setData(android.net.Uri.parse("package:" + getPackageName()))
                )
        );
    }

    private void requestAccessibilityPermission() {
        showPermissionDialog(
                R.string.accessibility_permission_title,
                getString(R.string.accessibility_permission_description, getString(R.string.app_name)),
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
    }

    private void requestUsageStatsPermission() {
        showPermissionDialog(
                R.string.usage_access_title,
                getString(R.string.usage_access_description, getString(R.string.app_name)),
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        );
    }

    private void showPermissionDialog(@StringRes int titleRes, String message, Runnable onSettings) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    dialog.dismiss();
                    onSettings.run();
                })
                .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAutoUpdatePolicyDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_update_check)
                .setMessage(R.string.auto_update_desc)
                .setPositiveButton(R.string.enable, (dialog, which) -> {
                    dialog.dismiss();
                    DatabaseUtil.setAutoUpdate(true);
                    fragment.autoUpdate.setChecked(true);
                    appUpdateManager.checkForUpdate(true);
                })
                .setNeutralButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    DatabaseUtil.setAutoUpdate(false);
                    fragment.autoUpdate.setChecked(false);
                })
                .setOnDismissListener(dialog -> DatabaseUtil.setIsFirstRun(false))
                .show();
    }

    private void applyTheme() {
       // setTheme(DatabaseUtil.useSystemFont() ? R.style.AppTheme_SystemFont : R.style.AppTheme);
    }

    public static final String EXTRA_FROM_QS_TILE = "from_qs_tile";
}
