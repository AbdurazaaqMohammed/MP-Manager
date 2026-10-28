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
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;

public final class Extensions {

    public static boolean canShowNotification(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void showMessage(Activity activity, @StringRes int message) {
        showMessage(activity, activity instanceof MainActivity m ? m.rss.getString(message) : activity.getString(message));
    }

    public static void showMessage(Activity activity, CharSequence message) {
        if (activity == null) return;
        Snackbar snackbar;
        View bottomBar = activity.findViewById(R.id.bottomBar);
        if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
            snackbar = Snackbar.make(bottomBar, message, Snackbar.LENGTH_SHORT).setAnchorView(bottomBar);
            try {
                View snackView = snackbar.getView();
                if (snackView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                    mlp.bottomMargin += dp2px(activity, 16);
                    snackView.setLayoutParams(mlp);
                }
            } catch (Exception ignored) {}
        } else {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT);
            try {
                View snackView = snackbar.getView();
                if (snackView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                    mlp.bottomMargin += dp2px(activity, 64);
                    snackView.setLayoutParams(mlp);
                }
            } catch (Exception ignored) {}
        }
        snackbar.getView().setOnTouchListener((v, event) -> {
            snackbar.dismiss();
            return false;
        });
        snackbar.show();
    }

    /**
     * Message with a "Details" action that opens a dialog with the full
     * diagnostics/stack trace, for failures whose one-line text ("Failed to
     * create folder X") hides the actual cause.
     */
    public static void showMessage(Activity activity, CharSequence message, CharSequence details) {
        if (activity == null) return;
        if (details == null || details.length() == 0) {
            showMessage(activity, message);
            return;
        }
        View bottomBar = activity.findViewById(R.id.bottomBar);
        View anchor = (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) ? bottomBar : activity.findViewById(android.R.id.content);
        if (anchor == null) return;
        Snackbar snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG);
        if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(bottomBar);
            try {
                View snackView = snackbar.getView();
                if (snackView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                    mlp.bottomMargin += dp2px(activity, 16);
                    snackView.setLayoutParams(mlp);
                }
            } catch (Exception ignored) {}
        } else {
            try {
                View snackView = snackbar.getView();
                if (snackView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                    mlp.bottomMargin += dp2px(activity, 64);
                    snackView.setLayoutParams(mlp);
                }
            } catch (Exception ignored) {}
        }
        snackbar.setAction(R.string.details, v ->
                new ErrorUtil(activity)
                        .showError(String.valueOf(details)))
                .show();
    }

    public static void showMessage(Dialog d, CharSequence message) {
        if (d == null) return;
        View root = d.findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT);
        try {
            View snackView = snackbar.getView();
            if (snackView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                mlp.bottomMargin += dp2px(root.getContext(), 24);
                snackView.setLayoutParams(mlp);
            }
        } catch (Exception ignored) {}
        snackbar.show();
    }

    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
