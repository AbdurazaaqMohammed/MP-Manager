package io.github.abdurazaaqmohammed.ui.common.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.preference.PreferenceManager;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.color.MaterialColors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.themes.LocalThemes;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;
import io.github.abdurazaaqmohammed.ui.main.LauncherIcons;

/**
 * Real-activity lifecycle coverage of the theme pack palette: palettes
 * applied through ThemePackPalette.resolve on real Resources (API 30+
 * ResourcesLoader), one tracked loader per activity Resources, palette
 * switches affecting only their own activity, empty palettes reverting,
 * clear() releasing, and ThemeRegistry.applyAccent on the real creation
 * path — a colorless active pack must NOT suppress the user accent while a
 * pack with slot colors must still win over it.
 *
 * <p>All palette calls run on the main thread (ResourcesLoader contract).
 * Launcher selection, accent pref and local themes are restored afterwards.
 */
@SdkSuppress(minSdkVersion = 30)
public class ThemePackPaletteLifecycleTest {

    private final Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private Context context;
    private String accentBefore;
    private boolean hadCustomAccentBefore;
    private int customAccentBefore;
    private String launcherSelectionBefore;
    private final List<String> createdThemeIds = new ArrayList<>();
    private final List<ThemePackPaletteTestActivity> launched = new ArrayList<>();

    private static final Map<String, Integer> PALETTE_P1 = palette("P1", 0xFF112233);
    private static final Map<String, Integer> PALETTE_P2 = palette("P2", 0xFF00AA00);

    private static Map<String, Integer> palette(String name, int primary) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("colorPrimary", primary);
        map.put("colorSurface", name.equals("P1") ? 0xFF445566 : 0xFF003366);
        return map;
    }

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue("Run palette lifecycle tests only with the isolated editorRegressionBuild",
                context.getPackageName().endsWith(".editorregression"));
        accentBefore = prefs().getString(ThemeRegistry.ACCENT_PREF_KEY, null);
        hadCustomAccentBefore = prefs().contains(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY);
        customAccentBefore = prefs().getInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, 0);
        launcherSelectionBefore = LauncherIcons.selection(context);
        ThemePacks.deactivate(context);
        prefs().edit().remove(ThemeRegistry.ACCENT_PREF_KEY)
                .remove(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY).apply();
    }

    @After
    public void tearDown() {
        ThemePacks.deactivate(context);
        for (String id : createdThemeIds) {
            try {
                LocalThemes.delete(context, id);
            } catch (Exception ignored) {
            }
        }
        createdThemeIds.clear();
        if (accentBefore == null) {
            prefs().edit().remove(ThemeRegistry.ACCENT_PREF_KEY).apply();
        } else {
            prefs().edit().putString(ThemeRegistry.ACCENT_PREF_KEY, accentBefore).apply();
        }
        if (hadCustomAccentBefore) {
            prefs().edit().putInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, customAccentBefore).apply();
        } else {
            prefs().edit().remove(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY).apply();
        }
        try {
            LauncherIcons.setSelection(context, launcherSelectionBefore);
        } catch (Exception ignored) {
        }
        for (ThemePackPaletteTestActivity activity : launched) {
            try {
                instrumentation.runOnMainSync(activity::finish);
            } catch (Exception ignored) {
            }
        }
        launched.clear();
        instrumentation.waitForIdleSync();
    }

    // ---------- helpers ----------

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private ThemePackPaletteTestActivity launch() {
        Intent intent = new Intent(context, ThemePackPaletteTestActivity.class)
                // CLEAR_TASK: a finished-and-relaunched test host must never
                // reuse a lingering instance (onNewIntent) — startActivitySync
                // would block waiting for a fresh onCreate.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ThemePackPaletteTestActivity activity =
                (ThemePackPaletteTestActivity) instrumentation.startActivitySync(intent);
        launched.add(activity);
        return activity;
    }

    /** Base transparent slot color, i.e. no loader override installed. */
    private static final int BASE_SLOT = 0x00000000;

    private static int slotPrimary(ThemePackPaletteTestActivity activity) {
        return activity.getResources().getColor(R.color.theme_pack_primary);
    }

    private static int primaryAttr(ThemePackPaletteTestActivity activity) {
        return MaterialColors.getColor(activity,
                com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT);
    }

    private static int ownedCount() {
        return ThemePackPalette.STATE.ownedCount();
    }

    /** Remove entries whose Resources belong to activities from prior tests
     * before taking a baseline; current activities are held strongly in
     * {@link #launched}, so entries created by this test remain stable. */
    private int stableOwnedCount() {
        System.gc();
        instrumentation.waitForIdleSync();
        return ownedCount();
    }

    private boolean applyPalette(ThemePackPaletteTestActivity activity,
                                 Map<String, Integer> colors, Integer accent) {
        final boolean[] ok = {false};
        instrumentation.runOnMainSync(() ->
                ok[0] = ThemePackPalette.apply(activity, colors, accent));
        return ok[0];
    }

    private void clearPalette(ThemePackPaletteTestActivity activity) {
        instrumentation.runOnMainSync(() -> ThemePackPalette.clear(activity));
    }

    // ---------- lifecycle ----------

    @Test
    public void apply_switch_emptyClear_revertsAndNeverGrowsTracking() {
        ThemePackPaletteTestActivity activity = launch();
        assertNotEquals("base slot sentinel must differ from test palettes",
                0xFF112233, slotPrimary(activity));
        int owned = stableOwnedCount();

        // Fresh palette: installed and resolvable through Resources AND the
        // applied slot style (theme attr).
        assertTrue(applyPalette(activity, PALETTE_P1, null));
        assertEquals(0xFF112233, slotPrimary(activity));
        assertEquals(0xFF112233, primaryAttr(activity));
        assertEquals("one loader tracked for this Resources",
                owned + 1, ownedCount());

        // Same palette: no new loader, colors still resolve.
        assertTrue(applyPalette(activity, PALETTE_P1, null));
        assertEquals(0xFF112233, slotPrimary(activity));
        assertEquals(0xFF112233, primaryAttr(activity));
        assertEquals("same palette must not grow the tracked loaders",
                owned + 1, ownedCount());

        // Changed palette: replaced, still one tracked entry, new colors live.
        assertTrue(applyPalette(activity, PALETTE_P2, null));
        assertEquals(0xFF00AA00, slotPrimary(activity));
        assertEquals(0xFF00AA00, primaryAttr(activity));
        assertEquals(owned + 1, ownedCount());

        // Empty palette: uninstalls the override and releases tracking.
        assertTrue(applyPalette(activity, Collections.emptyMap(), null));
        assertEquals(BASE_SLOT, slotPrimary(activity));
        assertEquals(owned, ownedCount());

        // clear() afterwards is a no-op and stays clean.
        clearPalette(activity);
        assertEquals(BASE_SLOT, slotPrimary(activity));
        assertEquals(owned, ownedCount());
    }

    @Test
    public void secondActivity_isolatedTracking_switchAndRevertAffectOnlyTheirResources() {
        ThemePackPaletteTestActivity a = launch();
        assertTrue(applyPalette(a, PALETTE_P1, null));
        assertEquals(0xFF112233, slotPrimary(a));

        ThemePackPaletteTestActivity b = launch();
        assertNotSame(a, b);
        assertNotSame("each activity instance gets its own Resources",
                a.getResources(), b.getResources());
        int owned = stableOwnedCount();

        assertTrue(applyPalette(b, PALETTE_P1, null));
        assertEquals(0xFF112233, slotPrimary(b));
        assertEquals(0xFF112233, slotPrimary(a));
        assertEquals("b tracks its own loader even for the same palette",
                owned + 1, ownedCount());

        assertTrue(applyPalette(b, PALETTE_P2, null));
        assertEquals(0xFF00AA00, slotPrimary(b));
        assertEquals("a keeps its own palette untouched", 0xFF112233, slotPrimary(a));
        assertEquals(owned + 1, ownedCount());

        assertTrue(applyPalette(b, Collections.emptyMap(), null));
        assertEquals(BASE_SLOT, slotPrimary(b));
        assertEquals("a still resolves its palette", 0xFF112233, slotPrimary(a));
        assertEquals(owned, ownedCount());
    }

    // ---------- applyAccent regression (colorless pack vs user accent) ----------

    @Test
    public void colorlessActivePack_doesNotSuppressUserAccent() {
        ThemePackPaletteTestActivity probe = launch();
        int baseAttr = primaryAttr(probe);

        // Pack active but with NO palette slot tokens (chrome token only):
        // before the gate fix this path returned early and the user accent
        // was silently dropped. newDraft seeds a full starter palette, so a
        // genuinely colorless pack must clear the preset tokens first and
        // keep only the non-slot chrome token.
        LocalThemes.Draft draft = LocalThemes.newDraft("Colorless", false);
        draft.colors.clear();
        draft.colors.put("status_bar", 0xFF000000);
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull(id);
        createdThemeIds.add(id);
        assertNotNull(ThemePacks.active(context));

        // Custom accent carries the full 0xFF-alpha ARGB. Named presets go
        // through ThemeRegistry.presetArgb (now parsing "#FFRRGGBB" from
        // offset 1); the custom path pins the same seed deterministically.
        final int seed = 0xFFC62828;
        prefs().edit().putString(ThemeRegistry.ACCENT_PREF_KEY, "custom")
                .putInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, seed).apply();

        int owned = stableOwnedCount();
        ThemePackPaletteTestActivity accented = launch();
        assertNotEquals("user accent must apply when the pack has no slot colors",
                baseAttr, primaryAttr(accented));
        // The accent now legitimately installs the runtime ColorRoles loader
        // (API 30+ path runs before DynamicColors): attr AND slot must both
        // resolve to the exact accent role Material computes from the seed.
        int expectedAccent = MaterialColors.getColorRoles(accented, seed).getAccent();
        assertEquals("attr colorPrimary is the seeded accent role",
                expectedAccent, primaryAttr(accented));
        assertEquals("slot resource is the seeded accent role via the loader",
                expectedAccent, slotPrimary(accented));
        assertEquals("one loader tracked for the accented Resources",
                owned + 1, ownedCount());

        // Removing the accent (back to "follow") via the in-place path must
        // uninstall the loader and restore the unaccented base theme.
        prefs().edit().remove(ThemeRegistry.ACCENT_PREF_KEY)
                .remove(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY).apply();
        instrumentation.runOnMainSync(() -> ThemeRegistry.applyInPlace(accented));
        assertEquals("accent removal must clear the loader-owned slot",
                BASE_SLOT, slotPrimary(accented));
        assertEquals("attr falls back to the unaccented base theme",
                baseAttr, primaryAttr(accented));
        assertEquals("loader tracking released on removal", owned, ownedCount());
    }

    @Test
    public void packWithSlotColors_stillWinsOverUserAccent() {
        int owned = stableOwnedCount();

        LocalThemes.Draft draft = LocalThemes.newDraft("Colored", false);
        draft.colors.put("colorPrimary", 0xFF112233);
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull(id);
        createdThemeIds.add(id);
        assertNotNull(ThemePacks.active(context));

        prefs().edit().putString(ThemeRegistry.ACCENT_PREF_KEY, "preset:Red").apply();

        ThemePackPaletteTestActivity packActivity = launch();
        assertEquals("pack colorPrimary wins over the user accent",
                0xFF112233, slotPrimary(packActivity));
        assertEquals(0xFF112233, primaryAttr(packActivity));
        assertEquals("pack palette tracked through the loader path",
                owned + 1, ownedCount());
    }
}
