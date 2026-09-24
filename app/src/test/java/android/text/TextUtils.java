package android.text;

/**
 * Minimal JVM implementation shadowing android.jar's stub so libraries that
 * only need TextUtils (e.g. apksig) can run in local unit tests.
 */
public final class TextUtils {
    private TextUtils() {
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        int len = a.length();
        if (len != b.length()) return false;
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) return false;
        }
        return true;
    }
}
