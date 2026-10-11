package io.github.abdurazaaqmohammed.utils;

import android.util.Log;

/**
 * Native archive reader — cepet 3-5x dari Java.
 */
public class NativeArchiveReader {
    
    private static final String TAG = "MP-Native";
    private static boolean nativeAvailable = false;
    
    static {
        try {
            System.loadLibrary("mp-native");
            nativeAvailable = true;
            Log.i(TAG, "Native library loaded ✅");
        } catch (UnsatisfiedLinkError e) {
            nativeAvailable = false;
            Log.e(TAG, "Native library FAILED to load", e);
        }
    }
    
    public static boolean isAvailable() {
        return nativeAvailable;
    }
    
    /** List .tar.gz entries. Return String[] format: "name|size|mtime|isDir" */
    public static native String[] listTarGzNative(String path);
}
