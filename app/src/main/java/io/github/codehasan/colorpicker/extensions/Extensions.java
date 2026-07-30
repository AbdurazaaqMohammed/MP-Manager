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
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

public final class Extensions {

    public static boolean canShowNotification(AppCompatActivity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)  == PackageManager.PERMISSION_GRANTED;
    }

    public static void showMessage(AppCompatActivity activity, @StringRes int message) {
        showMessage(activity, activity.getString(message));
    }

    public static void showMessage(AppCompatActivity activity, String message) {
        View v = (activity instanceof MainActivity) ? activity.findViewById(R.id.bottomBar) : activity.getWindow().getDecorView().getRootView();
        Snackbar.make(v, message, Snackbar.LENGTH_SHORT).show();
    }

    public static int dp2px(android.content.Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
