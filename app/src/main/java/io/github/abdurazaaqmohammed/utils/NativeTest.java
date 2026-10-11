package io.github.abdurazaaqmohammed.utils;

/**
 * Native library wrapper — test JNI.
 */
public class NativeTest {
    static {
        try {
            System.loadLibrary("mp-native");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("MP-Native", "Failed to load native lib", e);
        }
    }
    
    public static native String helloFromC();
    public static native String getNativeInfo();
}
