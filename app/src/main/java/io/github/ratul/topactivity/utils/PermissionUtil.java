package io.github.ratul.topactivity.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.extensions.ActivityExtensions;

public class PermissionUtil {
    public static boolean requestMissingPermissions(AppCompatActivity c) {
        boolean needsOverlay = DatabaseUtil.getServiceMode().equals("0") && !ActivityExtensions.isSystemOverlayGranted(c);

        List<Request> requests = new ArrayList<>();
        if (!ActivityExtensions.isUsageStatsGranted(c)) {
            requests.add(onDone -> requestUsageStatsPermission(c, onDone));
        }
        if (needsOverlay) {
            requests.add(onDone -> requestSystemOverlayPermission(c, onDone));
        }
        if (!ActivityExtensions.isNotificationGranted(c)) {
            requests.add(onDone -> requestNotificationPermission(c, onDone));
        }
        if (ActivityExtensions.isAccessibilityNotStarted()) {
            requests.add(onDone -> requestAccessibilityPermission(c, onDone));
        }

        if (requests.isEmpty()) return true;
        runSequentially(requests, 0);
        return false;
    }

    private interface Request {
        void show(Runnable onDone);
    }

    private static void runSequentially(List<Request> requests, int index) {
        if (index >= requests.size()) return;
        requests.get(index).show(() -> runSequentially(requests, index + 1));
    }

    public static void requestNotificationPermission(AppCompatActivity c) {
        requestNotificationPermission(c, null);
    }

    public static void requestNotificationPermission(AppCompatActivity c, Runnable onDismiss) {
        c.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (onDismiss != null) onDismiss.run();
                }
        ).launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public static void requestSystemOverlayPermission(AppCompatActivity c) {
        requestSystemOverlayPermission(c, null);
    }

    public static void requestSystemOverlayPermission(AppCompatActivity c, Runnable onDismiss) {
        showPermissionDialog(
                R.string.system_overlay_title,
                String.format(c.getString(R.string.system_overlay_description), c.getString(R.string.app_name)),
                () -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(Uri.parse("package:" + c.getPackageName()));
                    c.startActivity(intent);
                }, onDismiss, c
        );
    }

    public static void requestAccessibilityPermission(AppCompatActivity c) {
        requestAccessibilityPermission(c, null);
    }

    public static void requestAccessibilityPermission(AppCompatActivity c, Runnable onDismiss) {
        showPermissionDialog(
                R.string.accessibility_permission_title,
                String.format(c.getString(R.string.accessibility_permission_description), c.getString(R.string.app_name)),
                () -> c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)), onDismiss, c
        );
    }

    public static void requestUsageStatsPermission(AppCompatActivity c) {
        requestUsageStatsPermission(c, null);
    }

    public static void requestUsageStatsPermission(AppCompatActivity c, Runnable onDismiss) {
        showPermissionDialog(
                R.string.usage_access_title,
                String.format(c.getString(R.string.usage_access_description), c.getString(R.string.app_name)),
                () -> c.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)), onDismiss, c
        );
    }

    public static void showPermissionDialog(@StringRes int titleRes, String message, Runnable onSettings, Runnable onDismiss, Context c) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(c)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    dialog.dismiss();
                    onSettings.run();
                })
                .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
        if (onDismiss != null) {
            builder.setOnDismissListener(dialog -> onDismiss.run());
        }
        builder.show();
    }

}
