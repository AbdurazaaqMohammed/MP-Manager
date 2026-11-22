package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Decision-table coverage for the background package-update refresh of the
 * active theme selection: stale refreshes must never reactivate a theme the
 * user deselected/switched, signer mismatches deactivate, and provider
 * failures keep the previous valid snapshot.
 */
public class ThemePackUpdatePolicyTest {

    private static ThemePackUpdatePolicy.Decision decide(boolean stillSelected,
                                                         boolean generationUnchanged,
                                                         boolean importSucceeded,
                                                         boolean signerMismatch) {
        return ThemePackUpdatePolicy.onRefreshAttempt(
                stillSelected, generationUnchanged, importSucceeded, signerMismatch);
    }

    @Test
    public void successfulReimport_commits() {
        assertEquals(ThemePackUpdatePolicy.Decision.COMMITTED,
                decide(true, true, true, false));
        // Signer mismatch is irrelevant once the import already succeeded
        // (the import itself re-verifies the pin before committing).
        assertEquals(ThemePackUpdatePolicy.Decision.COMMITTED,
                decide(true, true, true, true));
    }

    @Test
    public void userDeselectedOrSwitchedDuringRefresh_abortsStale() {
        // generation bumped while the refresh ran -> never commit/reactivate
        assertEquals(ThemePackUpdatePolicy.Decision.ABORT_STALE,
                decide(true, false, true, false));
        assertEquals(ThemePackUpdatePolicy.Decision.ABORT_STALE,
                decide(true, false, false, true));
        // package no longer the active selection (deselected or another
        // theme picked) -> stale even if generation matches
        assertEquals(ThemePackUpdatePolicy.Decision.ABORT_STALE,
                decide(false, true, true, false));
        assertEquals(ThemePackUpdatePolicy.Decision.ABORT_STALE,
                decide(false, false, false, true));
    }

    @Test
    public void signerMismatchOnActiveSelection_deactivates() {
        assertEquals(ThemePackUpdatePolicy.Decision.DEACTIVATE_SIGNER,
                decide(true, true, false, true));
    }

    @Test
    public void providerFailureOrRejectedRow_keepsPreviousSnapshot() {
        // provider hiccup / malformed row / caps exceeded: previous snapshot
        // stays valid and active
        assertEquals(ThemePackUpdatePolicy.Decision.KEEP_PREVIOUS,
                decide(true, true, false, false));
    }

    @Test
    public void fullTruthTable() {
        for (boolean stillSelected : new boolean[]{true, false}) {
            for (boolean generationUnchanged : new boolean[]{true, false}) {
                for (boolean importSucceeded : new boolean[]{true, false}) {
                    for (boolean signerMismatch : new boolean[]{true, false}) {
                        ThemePackUpdatePolicy.Decision d = decide(
                                stillSelected, generationUnchanged,
                                importSucceeded, signerMismatch);
                        if (!stillSelected || !generationUnchanged) {
                            assertEquals(ThemePackUpdatePolicy.Decision.ABORT_STALE, d);
                        } else if (importSucceeded) {
                            assertEquals(ThemePackUpdatePolicy.Decision.COMMITTED, d);
                        } else if (signerMismatch) {
                            assertEquals(ThemePackUpdatePolicy.Decision.DEACTIVATE_SIGNER, d);
                        } else {
                            assertEquals(ThemePackUpdatePolicy.Decision.KEEP_PREVIOUS, d);
                        }
                    }
                }
            }
        }
    }
}
