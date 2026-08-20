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

        List<Runnable> actions = new ArrayList<>();
        if (ActivityExtensions.isAccessibilityNotStarted()) {
            actions.add(() -> requestAccessibilityPermission(c));
        }
        if (!ActivityExtensions.isUsageStatsGranted(c)) {
            actions.add(() -> requestUsageStatsPermission(c));
        }
        if (!ActivityExtensions.isNotificationGranted(c)) {
            actions.add(() -> requestNotificationPermission(c));
        }
        if (needsOverlay) {
            actions.add(() -> requestSystemOverlayPermission(c));
        }

        for (Runnable action : actions) {
            action.run();
        }
        return actions.isEmpty();
    }

    public static void requestNotificationPermission(AppCompatActivity c) {
        c.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                }
        ).launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public static void requestSystemOverlayPermission(AppCompatActivity c) {
        showPermissionDialog(
                R.string.system_overlay_title,
                String.format(c.getString(R.string.system_overlay_description), c.getString(R.string.app_name)),
                () -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(Uri.parse("package:" + c.getPackageName()));
                    c.startActivity(intent);
                }, c
        );
    }

    public static void requestAccessibilityPermission(AppCompatActivity c) {
        showPermissionDialog(
                R.string.accessibility_permission_title,
                String.format(c.getString(R.string.accessibility_permission_description), c.getString(R.string.app_name)),
                () -> c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)), c
        );
    }

    public static void requestUsageStatsPermission(AppCompatActivity c) {
        showPermissionDialog(
                R.string.usage_access_title,
                String.format(c.getString(R.string.usage_access_description), c.getString(R.string.app_name)),
                () -> c.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)), c
        );
    }

    public static void showPermissionDialog(@StringRes int titleRes, String message, Runnable onSettings, Context c) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    dialog.dismiss();
                    onSettings.run();
                })
                .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

}
