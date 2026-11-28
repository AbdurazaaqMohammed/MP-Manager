package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.ui.main.LauncherIcons.Op;

/**
 * Covers the pure launcher-alias state machine in {@link LauncherIcons}:
 * pack identifier resolution (including the unknown-value fallback that
 * keeps third-party themes from breaking the launcher), the
 * enable-target-before-disabling ordering that guarantees at least one
 * launcher entry at every step, and the rollback plan that restores the
 * pre-call state. The PackageManager-facing {@code apply} executes exactly
 * these plans.
 */
public class LauncherIconsLogicTest {

    // --- pack request resolution ------------------------------------------

    @Test
    public void mapsBlankAndSynonymsToDefault() {
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest(null));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest(""));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("   "));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("none"));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("SYSTEM"));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("original"));
        // follow_theme is a user-selection concept, never a pack value.
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("follow_theme"));
    }

    @Test
    public void mapsUnknownPackValuesToDefaultNeverFails() {
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("bogus"));
        assertEquals(LauncherIcons.ID_DEFAULT,
                LauncherIcons.resolvePackRequest("data:image/png;base64,AAAA"));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("#FF0000"));
    }

    @Test
    public void acceptsBundledIdsCaseInsensitively() {
        assertEquals(LauncherIcons.ID_CRIMSON, LauncherIcons.resolvePackRequest("Crimson"));
        assertEquals(LauncherIcons.ID_CRIMSON, LauncherIcons.resolvePackRequest(" crimson "));
        assertEquals(LauncherIcons.ID_MONO, LauncherIcons.resolvePackRequest("MONO"));
        assertEquals(LauncherIcons.ID_DEFAULT, LauncherIcons.resolvePackRequest("default"));
    }

    @Test
    public void knowsUserSelectableIdentifiers() {
        assertTrue(LauncherIcons.isKnownIdentifier("crimson"));
        assertTrue(LauncherIcons.isKnownIdentifier("follow_theme"));
        assertTrue(LauncherIcons.isKnownIdentifier("Default"));
        assertFalse(LauncherIcons.isKnownIdentifier("bogus"));
        assertFalse(LauncherIcons.isKnownIdentifier(null));
        assertFalse(LauncherIcons.isKnownIdentifier(""));
    }

    // --- choices -----------------------------------------------------------

    @Test
    public void choicesReturnsFreshCopyWithDefaultFirst() {
        LauncherIcons.Choice[] first = LauncherIcons.choices();
        LauncherIcons.Choice[] second = LauncherIcons.choices();
        assertNotSame(first, second);
        assertEquals(LauncherIcons.ID_DEFAULT, first[0].id);
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i].id, second[i].id);
            assertEquals(first[i].aliasSimpleName, second[i].aliasSimpleName);
        }
    }

    // --- transition planning -----------------------------------------------

    @Test
    public void enablesTargetBeforeDisablingPreviousAlias() {
        boolean[] state = {true, false, false, false, false, false};
        List<Op> ops = LauncherIcons.planTransitions(state, 1); // default -> crimson
        assertEquals(2, ops.size());
        assertTrue(ops.get(0).enable);
        assertEquals(1, ops.get(0).index);
        assertFalse(ops.get(1).enable);
        assertEquals(0, ops.get(1).index);
        assertPrefixesNeverLoseLastLauncherEntry(state, ops);
    }

    @Test
    public void noOpWhenTargetIsAlreadyTheOnlyEnabledAlias() {
        boolean[] state = {false, true, false, false, false, false};
        assertTrue(LauncherIcons.planTransitions(state, 1).isEmpty());
    }

    @Test
    public void healsTamperedAllDisabledStateByOnlyEnablingTarget() {
        boolean[] state = {false, false, false, false, false, false};
        List<Op> ops = LauncherIcons.planTransitions(state, 2);
        assertEquals(1, ops.size());
        assertTrue(ops.get(0).enable);
        assertEquals(2, ops.get(0).index);
        assertPrefixesNeverLoseLastLauncherEntry(state, ops);
    }

    @Test
    public void duplicateEntriesCollapseWithoutRedundantEnable() {
        boolean[] state = {true, true, false, false, false, false};
        List<Op> ops = LauncherIcons.planTransitions(state, 0);
        assertEquals(1, ops.size());
        assertFalse(ops.get(0).enable);
        assertEquals(1, ops.get(0).index);
        assertPrefixesNeverLoseLastLauncherEntry(state, ops);
    }

    @Test
    public void everyTransitionPlanKeepsExactlyTargetEnabledAtEnd() {
        for (int target = 0; target < 6; target++) {
            boolean[] state = {true, false, false, false, false, false};
            List<Op> ops = LauncherIcons.planTransitions(state, target);
            boolean[] end = apply(state, ops);
            assertTrue("target must be enabled after plan", end[target]);
            for (int i = 0; i < end.length; i++) {
                if (i != target) assertFalse("extra alias left enabled: " + i, end[i]);
            }
        }
    }

    // --- rollback planning --------------------------------------------------

    @Test
    public void rollbackRestoresPreCallState() {
        boolean[] before = {true, false, false, false, false, false};
        List<Op> ops = LauncherIcons.planTransitions(before, 4);
        boolean[] midCall = apply(before, ops);
        assertTrue(midCall[4]);
        assertFalse(midCall[0]);

        List<Op> undo = LauncherIcons.planRollback(before, ops);
        boolean[] restored = apply(midCall, undo);
        assertTrue(restored[0]);
        assertFalse(restored[4]);
        assertTrue("restored state stays launchable",
                LauncherIcons.atLeastOneEnabled(restored));
    }

    @Test
    public void rollbackOfFailedDisablePassReEnablesOldAlias() {
        // Simulate: enable target succeeded, disable of the old alias threw
        // before being recorded as executed.
        boolean[] before = {true, false, false, false, false, false};
        List<Op> executed = new ArrayList<>();
        executed.add(new Op(1, true)); // target enabled
        List<Op> undo = LauncherIcons.planRollback(before, executed);
        boolean[] restored = apply(before.clone(), undo);
        assertTrue(restored[1] == before[1]);
        assertTrue(restored[0] == before[0]);
        assertTrue(LauncherIcons.atLeastOneEnabled(restored));
    }

    @Test
    public void rollbackFromTamperedZeroStateNeedsCallerSafetyNet() {
        // If the pre-call state was tampered to all-disabled, restoring it
        // exactly would still leave zero launcher entries - the caller must
        // force the target back on. apply() does this via its safety net;
        // this test pins the planner contract that makes the net necessary.
        boolean[] before = {false, false, false, false, false, false};
        List<Op> executed = new ArrayList<>();
        executed.add(new Op(3, true));
        List<Op> undo = LauncherIcons.planRollback(before, executed);
        // Undo reverts the executed enable so the state matches `before`.
        assertEquals(1, undo.size());
        assertFalse(undo.get(0).enable);
        assertEquals(3, undo.get(0).index);
        assertFalse("restored zero state is not launchable by itself",
                LauncherIcons.atLeastOneEnabled(apply(before, undo)));
        // Safety net equivalent in apply(): enable the target anyway.
        assertTrue(LauncherIcons.atLeastOneEnabled(apply(before,
                LauncherIcons.planTransitions(before, 3))));
    }

    // --- alias class recognition (EditorTasks task scanning) ------------------

    @Test
    public void recognizesLauncherAliasClassNames() {
        // EditorTasks matches task baseIntent components; a launcher-tapped
        // task records the alias, not MainActivity.
        assertTrue(LauncherIcons.isLauncherAliasClassName(
                "io.github.abdurazaaqmohammed.MPManager.LauncherDefault"));
        assertTrue(LauncherIcons.isLauncherAliasClassName(
                "io.github.abdurazaaqmohammed.MPManager.editorregression.LauncherMono"));
        assertFalse("MainActivity itself is not an alias",
                LauncherIcons.isLauncherAliasClassName(
                        "io.github.abdurazaaqmohammed.ui.main.MainActivity"));
        assertFalse(LauncherIcons.isLauncherAliasClassName(null));
        assertFalse("suffix must be the full simple name",
                LauncherIcons.isLauncherAliasClassName(
                        "com.other.app.LauncherCrimsonDifferent"));
    }

    // --- alias state rule ----------------------------------------------------

    @Test
    public void componentEnabledStateMapping() {
        // PackageManager constants (0=DEFAULT, 1=ENABLED, 2=DISABLED) inline
        // at compile time, so plain unit tests can use them safely.
        int defaultState = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        int enabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        int disabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        assertTrue(LauncherIcons.isAliasEnabledByState(enabled, false));
        assertTrue(LauncherIcons.isAliasEnabledByState(defaultState, true));
        assertFalse(LauncherIcons.isAliasEnabledByState(defaultState, false));
        assertFalse(LauncherIcons.isAliasEnabledByState(disabled, true));
        assertFalse(LauncherIcons.isAliasEnabledByState(disabled, false));
    }

    // --- helpers -------------------------------------------------------------

    private static boolean[] apply(boolean[] state, List<Op> ops) {
        boolean[] out = state.clone();
        for (Op op : ops) out[op.index] = op.enable;
        return out;
    }

    /** The core launcher guarantee: no prefix of the plan may disable the app. */
    private static void assertPrefixesNeverLoseLastLauncherEntry(boolean[] state, List<Op> ops) {
        boolean[] running = state.clone();
        for (int i = 0; i < ops.size(); i++) {
            Op op = ops.get(i);
            running[op.index] = op.enable;
            assertTrue("op " + i + " leaves zero launcher entries",
                    LauncherIcons.atLeastOneEnabled(running));
        }
    }
}
