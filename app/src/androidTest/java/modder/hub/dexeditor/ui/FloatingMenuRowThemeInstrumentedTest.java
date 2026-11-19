package modder.hub.dexeditor.ui;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;
import io.github.abdurazaaqmohammed.ui.common.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

/**
 * Dark/light rendering checks for the "Edit floating menus" rows. The screen
 * previously ignored the app-selected theme (only the OS dark mode reached it)
 * and its row drawable hardcoded white, so with Dark chosen in-app while the OS
 * stayed light every row was a white box with black icons.
 *
 * Each test pins the OS uiMode via {@code cmd uimode}, selects an app theme via
 * ThemeRegistry, then renders the first real RecyclerView row and checks the
 * row background, the drag/icon glyph and the title color against the expected
 * lightness for the effective night mode.
 *
 * Runs only against the isolated ".editorregression" debug package
 * (-PeditorRegressionBuild) so it can never replace or disturb the main
 * installed app.
 */
public class FloatingMenuRowThemeInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private String savedThemeId;
    private boolean savedOsNight;
    private EditFloatingMenusActivity activity;

    @Before
    public void setUp() throws Exception {
        context = instrumentation.getTargetContext();
        assertTrue("Must run against the isolated -PeditorRegressionBuild package; refusing to "
                        + "touch the main installed app: " + context.getPackageName(),
                context.getPackageName().endsWith(".editorregression"));
        savedThemeId = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(ThemeRegistry.PREF_KEY, null);
        // A lingering theme pack would win over the selected built-in theme
        // (ThemeRegistry.appliedBaseId checks packs first), so clear the pointer.
        // The isolated regression package has no pack state worth restoring.
        try {
            ThemePacks.deactivate(context);
        } catch (Exception ignored) {
        }
        savedOsNight = osNightMode();
    }

    @After
    public void tearDown() throws Exception {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            activity = null;
        }
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (savedThemeId == null) {
                prefs.edit().remove(ThemeRegistry.PREF_KEY).apply();
                try {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } catch (Exception ignored) {
                }
            } else {
                ThemeRegistry.setCurrentId(context, savedThemeId);
            }
        } finally {
            shell("cmd uimode night " + (savedOsNight ? "yes" : "no"));
        }
    }

    @Test
    public void appSelectedDarkWithOsLightRendersDarkRows() throws Exception {
        shell("cmd uimode night no"); // OS stays light; the app choice must win.
        ThemeRegistry.setCurrentId(context, BuiltInThemes.DARK_ID);
        RowSnapshot row = sampleFirstRow();
        assertTrue("App-selected Dark must force night resources even with a light OS, "
                        + "was " + row.nightConfigDescription,
                row.nightConfig == Configuration.UI_MODE_NIGHT_YES);
        assertTrue("Row background must be dark when Dark is selected in-app (bug: white boxes); "
                        + "sampled " + row.backgroundDescription,
                row.backgroundLuminance < 0.4f);
        assertNotEquals("Row must not be painted with the old hardcoded white background; "
                        + "sampled " + row.backgroundDescription,
                0xFFFFFFFF, row.backgroundDominant);
        assertTrue("Drag icon must render light on the dark row; sampled luminance "
                        + String.format("%.2f", row.iconLuminance), row.iconLuminance > 0.5f);
        assertTrue("Row title must render light on the dark row; sampled 0x"
                        + Integer.toHexString(row.titleColor),
                        luminance(row.titleColor) > 0.5f);
    }

    @Test
    public void systemLightRendersLightRows() throws Exception {
        shell("cmd uimode night no");
        ThemeRegistry.setCurrentId(context, BuiltInThemes.SYSTEM_DEFAULT_ID);
        RowSnapshot row = sampleFirstRow();
        assertTrue("System default with a light OS must stay in day resources, was "
                        + row.nightConfigDescription,
                row.nightConfig == Configuration.UI_MODE_NIGHT_NO);
        assertTrue("Row background must stay light in the light theme (no visual regression); "
                        + "sampled " + row.backgroundDescription,
                row.backgroundLuminance > 0.6f);
        assertTrue("Drag icon must render dark on the light row; sampled luminance "
                        + String.format("%.2f", row.iconLuminance), row.iconLuminance < 0.5f);
        assertTrue("Row title must render dark on the light row; sampled 0x"
                        + Integer.toHexString(row.titleColor),
                        luminance(row.titleColor) < 0.5f);
    }

    @Test
    public void systemDarkFollowsOsAndRendersDarkRows() throws Exception {
        shell("cmd uimode night yes"); // pins the values-night Theme.MyApp.System override
        ThemeRegistry.setCurrentId(context, BuiltInThemes.SYSTEM_DEFAULT_ID);
        RowSnapshot row = sampleFirstRow();
        assertTrue("System default with a dark OS must use night resources, was "
                        + row.nightConfigDescription,
                row.nightConfig == Configuration.UI_MODE_NIGHT_YES);
        assertTrue("Row background must be dark when the OS is dark; sampled "
                        + row.backgroundDescription, row.backgroundLuminance < 0.4f);
        assertTrue("Drag icon must render light on the dark row; sampled luminance "
                        + String.format("%.2f", row.iconLuminance), row.iconLuminance > 0.5f);
        assertTrue("Row title must render light on the dark row; sampled 0x"
                        + Integer.toHexString(row.titleColor),
                        luminance(row.titleColor) > 0.5f);
    }

    // --- helpers -----------------------------------------------------------

    private static final class RowSnapshot {
        int nightConfig;
        String nightConfigDescription;
        int backgroundDominant;
        String backgroundDescription;
        float backgroundLuminance;
        float iconLuminance = -1f;
        int titleColor;
    }

    private RowSnapshot sampleFirstRow() throws Exception {
        // Launch after the OS uiMode and the app-selected theme are both pinned,
        // so BaseActivity's ThemeRegistry.applySaved() applies exactly this setup.
        activity = (EditFloatingMenusActivity) instrumentation.startActivitySync(
                new Intent(context, EditFloatingMenusActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        AtomicReference<RowSnapshot> ref = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                if (recyclerView == null || recyclerView.getChildCount() == 0) return;
                View row = recyclerView.getChildAt(0);
                if (row.getWidth() == 0 || row.getHeight() == 0) return;
                ref.set(snapshotRow(row, activity));
            });
            if (ref.get() != null) return ref.get();
            SystemClock.sleep(50);
        }
        fail("RecyclerView never rendered a row within 10s");
        return null; // unreachable
    }

    /** Must run on the main thread with a laid-out row. */
    private static RowSnapshot snapshotRow(View row, EditFloatingMenusActivity activity) {
        RowSnapshot out = new RowSnapshot();
        Configuration config = activity.getResources().getConfiguration();
        out.nightConfig = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        out.nightConfigDescription = (out.nightConfig == Configuration.UI_MODE_NIGHT_YES)
                ? "night YES" : "night NO";

        Bitmap bitmap = Bitmap.createBitmap(row.getWidth(), row.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            row.draw(new Canvas(bitmap));
            Map<Integer, Integer> counts = new HashMap<>();
            for (int y = 0; y < bitmap.getHeight(); y += 3) {
                for (int x = 0; x < bitmap.getWidth(); x += 3) {
                    int color = bitmap.getPixel(x, y);
                    if ((color >>> 24) != 255) continue; // rounded corners / shadow fringe
                    counts.merge(color, 1, Integer::sum);
                }
            }
            int best = 0;
            int bestCount = -1;
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    best = e.getKey();
                }
            }
            out.backgroundDominant = best;
            out.backgroundLuminance = luminance(best);
            out.backgroundDescription = String.format("dominant 0x%08X luminance %.2f",
                    out.backgroundDominant, out.backgroundLuminance);

            View icon = row.findViewById(R.id.imageView);
            if (icon != null && icon.getWidth() > 0) {
                // Draw separately so row background pixels cannot dilute the glyph.
                Bitmap glyph = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    icon.draw(new Canvas(glyph));
                    float total = 0;
                    int samples = 0;
                    for (int y = 0; y < glyph.getHeight(); y++) {
                        for (int x = 0; x < glyph.getWidth(); x++) {
                            int color = glyph.getPixel(x, y);
                            if ((color >>> 24) < 128) continue;
                            total += luminance(color);
                            samples++;
                        }
                    }
                    if (samples >= 5) out.iconLuminance = total / samples;
                } finally {
                    glyph.recycle();
                }
            }
        } finally {
            bitmap.recycle();
        }

        TextView title = row.findViewById(R.id.txtTitle);
        assertNotNull("Row title view missing", title);
        out.titleColor = title.getCurrentTextColor();
        return out;
    }

    private static float luminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
    }

    private boolean osNightMode() throws Exception {
        return shell("cmd uimode night").contains("yes");
    }

    private String shell(String command) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        SystemClock.sleep(200); // let the uiMode config propagate
        return output.toString("UTF-8");
    }
}
