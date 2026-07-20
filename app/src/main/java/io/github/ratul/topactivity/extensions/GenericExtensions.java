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
package io.github.ratul.topactivity.extensions;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import io.github.abdurazaaqmohammed.MPManager.R;

public final class GenericExtensions {

    private GenericExtensions() {
    }

    public static String value(TextView textView) {
        return textView.getText().toString();
    }

    public static void setStatus(FloatingActionButton fab, boolean active) {
        fab.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_start);
    }

    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    public static int dp2px(View view, float dp) {
        return dp2px(view.getContext(), dp);
    }

    public static android.util.Pair<Integer, Integer> getScreenSize(WindowManager windowManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            Insets insets = WindowInsetsCompat.toWindowInsetsCompat(windowMetrics.getWindowInsets())
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int usableWidth = windowMetrics.getBounds().width() - insets.left - insets.right;
            int usableHeight = windowMetrics.getBounds().height() - insets.top - insets.bottom;
            return new android.util.Pair<>(usableWidth, usableHeight);
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            return new android.util.Pair<>(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }
    }

    public static boolean isActivity(PackageManager pm, String pkg, String cls) {
        try {
            ComponentName component = new ComponentName(pkg, cls);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, 0);
            } else {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0));
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
