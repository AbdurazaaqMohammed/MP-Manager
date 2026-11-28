package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.abdurazaaqmohammed.plugins.themes.LocalThemes;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;

/**
 * Real-PackageManager coverage of the launcher-alias state machine: manual
 * override, theme follow (pack request applies only while the user is on
 * "follow theme"), unknown pack values falling back to the default variant,
 * and the "at least one launcher entry stays enabled" invariant after every
 * transition. Restores the default alias afterwards so the device stays
 * launchable.
 */
public class LauncherIconsAliasInstrumentedTest {
    private final android.app.Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private Context context;
    private String previousSelection;
    private String previousPack;

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue("Refusing a non-isolated package", context.getPackageName()
                .endsWith(".editorregression"));
        previousSelection = LauncherIcons.selection(context);
        previousPack = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(LauncherIcons.PREF_THEME_REQUEST, null);
        ThemePacks.deactivate(context);
        // Invariant baseline: exactly one alias enabled.
        assertEquals(1, enabledAliasCount());
    }

    @After
    public void tearDown() {
        // Restore the pre-test prefs, then re-apply that selection (handles
        // follow_theme too) so the device ends launchable and unchanged.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(LauncherIcons.PREF_SELECTION, previousSelection)
                .putString(LauncherIcons.PREF_THEME_REQUEST, previousPack)
                .apply();
        assertTrue(LauncherIcons.setSelection(context, previousSelection));
        assertEquals(previousSelection, LauncherIcons.selection(context));
        assertEquals(1, enabledAliasCount());
    }

    /** Counts aliases enabled with the same semantics LauncherIcons uses
     *  (STATE_DEFAULT follows the manifest, where the default alias ships
     *  enabled and every variant ships disabled). */
    private int enabledAliasCount() {
        PackageManager pm = context.getPackageManager();
        int count = 0;
        for (LauncherIcons.Choice choice : LauncherIcons.choices()) {
            ComponentName component = choice.component(context);
            int state = pm.getComponentEnabledSetting(component);
            boolean manifestEnabled = component.getClassName().endsWith(".LauncherDefault");
            if (LauncherIcons.isAliasEnabledByState(state, manifestEnabled)) count++;
        }
        return count;
    }

    private void assertExactlyOneEnabled(String expectedId) {
        assertEquals("At every step exactly one launcher alias stays enabled",
                1, enabledAliasCount());
        assertEquals(expectedId, LauncherIcons.currentIdentifier(context));
        ComponentName active = choiceFor(expectedId).component(context);
        int state = context.getPackageManager().getComponentEnabledSetting(active);
        assertTrue("Expected " + expectedId + " alias effectively enabled",
                LauncherIcons.isAliasEnabledByState(state,
                        active.getClassName().endsWith(".LauncherDefault")));
    }

    private static LauncherIcons.Choice choiceFor(String id) {
        for (LauncherIcons.Choice choice : LauncherIcons.choices()) {
            if (choice.id.equals(id)) return choice;
        }
        throw new AssertionError("No choice " + id);
    }

    @Test
    public void manualOverride_switchesAliases_andPersists() {
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_CRIMSON));
        assertExactlyOneEnabled(LauncherIcons.ID_CRIMSON);
        assertEquals(LauncherIcons.ID_CRIMSON, LauncherIcons.selection(context));

        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_MONO));
        assertExactlyOneEnabled(LauncherIcons.ID_MONO);

        // Returning to the default alias keeps exactly one entry enabled.
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_DEFAULT));
        assertExactlyOneEnabled(LauncherIcons.ID_DEFAULT);
    }

    @Test
    public void followTheme_appliesPackRequest_onlyWhileSelected() {
        // Manual pin first: pack requests must NOT move it.
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_CRIMSON));
        assertEquals(LauncherIcons.ID_CRIMSON,
                LauncherIcons.sync(context, "emerald"));
        assertExactlyOneEnabled(LauncherIcons.ID_CRIMSON);

        // Switch to follow: the last synced request applies immediately.
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_FOLLOW_THEME));
        assertEquals(LauncherIcons.ID_EMERALD, LauncherIcons.currentIdentifier(context));
        assertExactlyOneEnabled(LauncherIcons.ID_EMERALD);

        // A new pack request moves the follow selection.
        assertEquals(LauncherIcons.ID_VIOLET, LauncherIcons.sync(context, "Violet"));
        assertExactlyOneEnabled(LauncherIcons.ID_VIOLET);

        // An unknown pack value falls back to the default variant, never
        // leaving the app unlaunchable.
        assertEquals(LauncherIcons.ID_DEFAULT,
                LauncherIcons.sync(context, "some.plugin.base64junk"));
        assertExactlyOneEnabled(LauncherIcons.ID_DEFAULT);
    }

    @Test
    public void packActivation_viaLocalSnapshot_updatesFollowSelection() {
        // Real ThemePacks flow: save+apply a local snapshot carrying a
        // bundled launcher id, then let the registry's sync path apply it.
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_FOLLOW_THEME));
        LocalThemes.Draft draft = LocalThemes.newDraft("LauncherFollow", false);
        draft.launcherId = "amber";
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull("Local theme with launcher id must save and apply", id);
        try {
            ThemePacks.Snapshot active = ThemePacks.active(context);
            assertNotNull(active);
            assertEquals("amber", active.launcherIcon);
            assertEquals(LauncherIcons.ID_AMBER,
                    LauncherIcons.sync(context, active.launcherIcon));
            assertExactlyOneEnabled(LauncherIcons.ID_AMBER);

            // Deleting the pack deactivates it; a sync with null (no pack)
            // restores the default icon under follow selection.
            assertTrue(LocalThemes.delete(context, id));
            ThemePacks.invalidateCachedSnapshot();
            assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.sync(context, null));
            assertExactlyOneEnabled(LauncherIcons.ID_DEFAULT);
        } finally {
            LocalThemes.delete(context, id);
        }
    }

    @Test
    public void repeatedSelectionIsANoOpAndInvariantHolds() {
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_CRIMSON));
        assertTrue(LauncherIcons.setSelection(context, LauncherIcons.ID_CRIMSON));
        assertExactlyOneEnabled(LauncherIcons.ID_CRIMSON);
        assertFalse(enabledAliasCount() > 1);
    }
}
