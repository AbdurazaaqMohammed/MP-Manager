package android.os;

/**
 * Minimal JVM stub shadowing android.jar so libraries referencing
 * Build.VERSION (e.g. apksig) can run in local unit tests.
 */
public final class Build {
    private Build() {
    }

    public static final class VERSION {
        private VERSION() {
        }

        public static final int SDK_INT = 35;
        public static final int RESOURCES_SDK_INT = 35;
    }
}
