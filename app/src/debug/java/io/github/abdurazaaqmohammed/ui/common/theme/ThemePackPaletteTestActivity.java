package io.github.abdurazaaqmohammed.ui.common.theme;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Debug-only minimal activity that mirrors {@code BaseActivity}'s theme
 * application order exactly (applySaved + applyAccent before super.onCreate)
 * so the lifecycle test exercises the real creation path on real Resources,
 * themes and ResourcesLoaders — without MainActivity's heavy startup.
 *
 * <p>Declared in {@code src/debug/AndroidManifest.xml} so it lands in the
 * debug app APK itself (androidTest-manifest components belong to the test
 * APK's own package and are NOT resolvable/launchable from the app process
 * the instrumentation runs in). Release builds never see it.
 */
public class ThemePackPaletteTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        // Stacked accent overlay (also before super.onCreate).
        ThemeRegistry.applyAccent(this);
        super.onCreate(savedInstanceState);
    }
}
