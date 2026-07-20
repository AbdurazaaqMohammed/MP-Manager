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
package io.github.ratul.topactivity.manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.github.ratul.topactivity.App;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.extensions.GenericExtensions;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.repository.ServiceState;
import io.github.ratul.topactivity.services.AccessibilityMonitoringService;
import io.github.ratul.topactivity.utils.DatabaseUtil;
import io.github.ratul.topactivity.utils.PermissionUtil;
import io.github.ratul.topactivity.utils.WindowManagerUtil;

public class PopupManager {

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final InspectOverlayManager inspectOverlayManager;

    private final WindowManager windowManager;
    private View baseView;
    private HistoryManager historyManager;
    private ImageView inspectBtn;
    private final DataRepository.StateListener stateListener = this::onStateChanged;

    public PopupManager(Context context) {
        this.context = context;
        this.inspectOverlayManager = new InspectOverlayManager(context);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (baseView != null) return;
        AccessibilityMonitoringService accessibilityService = AccessibilityMonitoringService.getInstance();
        if (accessibilityService == null) {
            //hide();
            PermissionUtil.requestAccessibilityPermission((AppCompatActivity) context);
            //      return;
        }
        View view = LayoutInflater.from(context).inflate(R.layout.layout_activity_info, null);
        baseView = view;

        android.util.Pair<Integer, Integer> screenSize = GenericExtensions.getScreenSize(windowManager);
        int displayWidth = screenSize.first;
        double scaleFactor = mapPreferenceToWindowSize(DatabaseUtil.getWindowSize());
        int viewSize = (int) (displayWidth * scaleFactor);

        WindowManager.LayoutParams layoutParams = WindowManagerUtil.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.width = viewSize;

        windowManager.addView(baseView, layoutParams);

        TextView appName = view.findViewById(R.id.app_name);
        TextView packageName = view.findViewById(R.id.package_name);
        TextView className = view.findViewById(R.id.class_name);
        ImageView closeBtn = view.findViewById(R.id.closeBtn);
        ImageView historyBtn = view.findViewById(R.id.historyBtn);
        inspectBtn = view.findViewById(R.id.inspectBtn);

        View.OnLongClickListener copyListener = v -> {
            TextView textView = (TextView) v;
            int message;
            if (v.getId() == R.id.package_name) {
                message = R.string.package_copied;
            } else if (v.getId() == R.id.class_name) {
                message = R.string.class_copied;
            } else {
                message = R.string.copied;
            }
            App.copyString(context, GenericExtensions.value(textView), context.getString(message));
            return true;
        };

        closeBtn.setOnClickListener(v -> DataRepository.getInstance().updateStatus(false));
        historyBtn.setOnClickListener(v -> {
            if (historyManager != null && historyManager.isActive()) return;
            historyManager = new HistoryManager(context);
            historyManager.show();
        });

        inspectBtn.setOnClickListener(v -> {
            if (AccessibilityMonitoringService.getInstance() == null) {
                //hide();
                PermissionUtil.requestAccessibilityPermission((AppCompatActivity) context);
                //      return;
            } else if (inspectOverlayManager.isShowing()) {
                inspectOverlayManager.hide();
                inspectBtn.setColorFilter(null);
                App.showToast(context, context.getString(R.string.inspect_stopped));
            } else {
                baseView.post(inspectOverlayManager::show);
                inspectBtn.setColorFilter(0xFF00FF00);
                App.showToast(context, context.getString(R.string.tap_view_to_inspect));
            }
        });
        packageName.setOnLongClickListener(copyListener);
        className.setOnLongClickListener(copyListener);
        view.setOnTouchListener(new DragTouchManager(windowManager, layoutParams));

        ServiceState serviceState = DataRepository.getInstance().getAppState();
        String defaultAppName = context.getString(R.string.unknown);
        className.setText(serviceState.getCls());
        packageName.setText(serviceState.getPkg());
        appName.setText(getAppName(serviceState.getPkg()) != null
                ? getAppName(serviceState.getPkg()) : defaultAppName);

        DataRepository.getInstance().addListener(stateListener);
    }

    private void hide() {
        inspectOverlayManager.hide();
        if (baseView != null) {
            windowManager.removeView(baseView);
            baseView = null;
        }
        DataRepository.getInstance().removeListener(stateListener);
    }

    private void onStateChanged(ServiceState state) {
        if (!state.isRunning()) {
            hide();
            return;
        }

        if (baseView == null) return;

        TextView appName = baseView.findViewById(R.id.app_name);
        TextView packageName = baseView.findViewById(R.id.package_name);
        TextView className = baseView.findViewById(R.id.class_name);

        String defaultAppName = context.getString(R.string.unknown);
        boolean isPackageChanged = !state.getPkg().equals(GenericExtensions.value(packageName));
        boolean isClassChanged = !state.getCls().equals(GenericExtensions.value(className));

        if (isClassChanged) {
            className.setText(state.getCls());
        }

        if (isPackageChanged) {
            packageName.setText(state.getPkg());
            appName.setText(defaultAppName);
            mainHandler.post(() -> new Thread(() -> {
                final String fetched = getAppName(state.getPkg()) != null
                        ? getAppName(state.getPkg()) : defaultAppName;
                mainHandler.post(() -> appName.setText(fetched));
            }).start());
        }
    }

    private String getAppName(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static double mapPreferenceToWindowSize(String value) {
        switch (value) {
            case "0":
                return 0.80;
            case "1":
                return 0.65;
            default:
                return 0.50;
        }
    }
}
