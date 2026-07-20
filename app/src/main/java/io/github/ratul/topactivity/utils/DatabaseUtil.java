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
package io.github.ratul.topactivity.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import io.github.ratul.topactivity.App;

public final class DatabaseUtil {

    private static SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(App.getInstance());
    }

    public static String getServiceMode() {
        return getPrefs().getString("service_mode", "0");
    }

    public static void setServiceMode(String value) {
        getPrefs().edit().putString("service_mode", value).apply();
    }

    public static String getScanSpeed() {
        return getPrefs().getString("scan_speed", "2");
    }

    public static void setScanSpeed(String value) {
        getPrefs().edit().putString("scan_speed", value).apply();
    }

    public static String getHistorySize() {
        return getPrefs().getString("history_size", "1");
    }

    public static void setHistorySize(String value) {
        getPrefs().edit().putString("history_size", value).apply();
    }

    public static String getWindowSize() {
        return getPrefs().getString("window_size", "1");
    }

    public static void setWindowSize(String value) {
        getPrefs().edit().putString("window_size", value).apply();
    }

    public static boolean useAccessibility() {
        return getPrefs().getBoolean("use_accessibility", false);
    }

    public static void setUseAccessibility(boolean value) {
        getPrefs().edit().putBoolean("use_accessibility", value).apply();
    }

    public static boolean autoUpdate() {
        return getPrefs().getBoolean("auto_update", false);
    }

    public static void setAutoUpdate(boolean value) {
        getPrefs().edit().putBoolean("auto_update", value).apply();
    }

    public static boolean useSystemFont() {
        return getPrefs().getBoolean("system_font", false);
    }

    public static void setUseSystemFont(boolean value) {
        getPrefs().edit().putBoolean("system_font", value).apply();
    }

    public static boolean isFirstRun() {
        return getPrefs().getBoolean("first_run", true);
    }

    public static void setIsFirstRun(boolean value) {
        getPrefs().edit().putBoolean("first_run", value).apply();
    }
}
