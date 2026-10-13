package io.github.abdurazaaqmohammed.utils;

import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

public class LegacyUtils {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static final boolean supportsFileChannel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    public static final boolean supportsWriteExternalStorage = Build.VERSION.SDK_INT < 30;
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
    public final static boolean aboveSdk20 = Build.VERSION.SDK_INT > 20;

}