package io.github.abdurazaaqmohammed.utils;

import java.util.HashSet;
import java.util.Set;

/** JNI binding for the downloaded jpegtran helper.
 *
 * <p>The package/class name is part of the native symbol
 * ({@code Java_io_github_abdurazaaqmohammed_utils_JpegtranJni_transform}) exported
 * by {@code libjpegtran_jni.so}. Do not move or rename this class without
 * rebuilding the native library.</p> */
public class JpegtranJni {
    public static final int OP_ROT_90 = 0;
    public static final int OP_ROT_270 = 1;
    public static final int OP_FLIP_H = 2;
    public static final int OP_FLIP_V = 3;
    public static final int OP_CROP = 4;
    public static final int OP_ROT_180 = 5;
    public static final int OP_TRANSPOSE = 6;
    public static final int OP_TRANSVERSE = 7;

    private static final Set<String> loadedPaths = new HashSet<>();

    public static synchronized boolean load(String absolutePath) {
        if (absolutePath == null) return false;
        if (loadedPaths.contains(absolutePath)) return true;
        try {
            System.load(absolutePath);
            loadedPaths.add(absolutePath);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static native int transform(String src, String dst, int op,
                                       int cropW, int cropH, int cropX, int cropY,
                                       String[] err);
}
