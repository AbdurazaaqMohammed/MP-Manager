/*
 * Copyright (c) 2026 Ratul Hasan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */
package io.github.codehasan.colorpicker.extensions;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

public final class Extensions {

    public static boolean canShowNotification(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void showMessage(Activity activity, @StringRes int message) {
        showMessage(activity, activity instanceof MainActivity m ? m.rss.getString(message) : activity.getString(message));
    }

    public static void showMessage(Activity activity, CharSequence message) {
        if (activity instanceof MainActivity && activity.hasWindowFocus()) {
            View bottomBar = activity.findViewById(R.id.bottomBar);
            Snackbar.make(bottomBar, message, Snackbar.LENGTH_SHORT).setAnchorView(bottomBar).show();
        } else {
            Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
            // When dialog is open visibility is still reduced and can get cut off need to check all usages of this and change it or something
        }
    }

    public static void showMessage(Dialog d, CharSequence message) {
        Snackbar.make(d.findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    public static int dp2px(android.content.Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
