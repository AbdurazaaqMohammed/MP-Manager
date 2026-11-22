package io.github.abdurazaaqmohammed.plugins.themes;

/**
 * Pure decision table for the background package-update refresh of the
 * active theme selection (no Android imports, JVM testable). The refresh
 * runs after {@code ACTION_PACKAGE_REPLACED} for the actively selected
 * provider and must be safe against races with the user picking a
 * different theme (or deselecting) while the refresh is in flight.
 *
 * <p>Inputs, all captured by {@code ThemePacks}:
 * <ul>
 *   <li>{@code stillSelected} — the updated package is (still) the active
 *       selection at decision time,</li>
 *   <li>{@code generationUnchanged} — the persisted selection generation is
 *       the same one the refresh started with (no import/deactivate happened
 *       in between),</li>
 *   <li>{@code importSucceeded} — the re-import committed atomically,</li>
 *   <li>{@code signerMismatch} — the updated package's signing certificate
 *       no longer matches the pinned one.</li>
 * </ul>
 */
final class ThemePackUpdatePolicy {

    private ThemePackUpdatePolicy() {
    }

    enum Decision {
        /** User deselected or switched away mid-refresh; do nothing. */
        ABORT_STALE,
        /** Re-import committed; the new snapshot is live. */
        COMMITTED,
        /** Provider hiccup or rejected row: keep the previous valid snapshot. */
        KEEP_PREVIOUS,
        /** Updated package is signed differently: deactivate and drop it. */
        DEACTIVATE_SIGNER,
    }

    static Decision onRefreshAttempt(boolean stillSelected,
                                     boolean generationUnchanged,
                                     boolean importSucceeded,
                                     boolean signerMismatch) {
        // A stale refresh must never (re)activate a theme the user moved
        // away from, no matter what the provider now serves.
        if (!stillSelected || !generationUnchanged) return Decision.ABORT_STALE;
        if (importSucceeded) return Decision.COMMITTED;
        if (signerMismatch) return Decision.DEACTIVATE_SIGNER;
        // Provider temporarily unavailable or malformed row: the previously
        // ingested snapshot stays valid and active until a later refresh.
        return Decision.KEEP_PREVIOUS;
    }
}
