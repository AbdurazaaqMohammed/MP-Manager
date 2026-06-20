package io.github.abdurazaaqmohammed.utils;


import java.util.Locale;

import io.github.abdurazaaqmohammed.ApkExtractor.AppInfo;

public class CompareUtils {
    public static int compareAppInfoByName(AppInfo p1, AppInfo p2) {
        return p1.name.toLowerCase(true ? Locale.ROOT : Locale.getDefault()).compareTo(p2.name.toLowerCase(true ? Locale.ROOT : Locale.getDefault()));
    }
    public static int compareByName(String p1, String p2) {
        return p1.toLowerCase(true ? Locale.ROOT : Locale.getDefault()).compareTo(p2.toLowerCase(true ? Locale.ROOT : Locale.getDefault()));
    }
    public long getSortField(AppInfo appInfo, int sortMode) {
        return sortMode == 1 ? appInfo.lastUpdate : appInfo.firstInstall;
    }
}