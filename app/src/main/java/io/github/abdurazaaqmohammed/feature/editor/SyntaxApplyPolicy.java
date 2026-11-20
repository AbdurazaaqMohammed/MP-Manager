package io.github.abdurazaaqmohammed.feature.editor;

import java.util.Objects;

/**
 * Pure decision + bookkeeping state behind the text editor's syntax application
 * (UnifiedEditorFragment#applySyntax). Kept free of Android/editor classes so
 * the intermittent "XML opens without highlighting" regressions stay covered
 * by JVM unit tests.
 *
 * <p>Failure modes this class prevents (the scope used below is e.g. the
 * "text.xml" scope requested for AndroidManifest.xml):</p>
 *
 * <ul>
 *   <li><b>Dedup across view recreation.</b> Activities that host the editor
 *   fragment in a {@code ViewPager2} (FragmentStateAdapter) destroy and
 *   recreate the fragment's VIEW while keeping the same fragment INSTANCE, so
 *   a recreated editor starts with no language applied. A request whose scope
 *   equals the last one must therefore still be re-applied when the current
 *   editor does not hold the applied language instance; a plain scope-equality
 *   check alone silently left the recreated view unhighlighted.</li>
 *   <li><b>Dropped async results.</b> A grammar load whose completion raced a
 *   detach (activity == null / fragment not added / view gone) used to be
 *   discarded while the scope stayed recorded, so no later trigger ever
 *   re-applied it. Dropped results now invalidate the recorded scope so the
 *   next trigger retries.</li>
 *   <li><b>Failed loads stick as plain text.</b> A transient TextMate load
 *   failure must not be treated as a satisfied request; the next trigger
 *   retries the load instead of permanently serving the plain fallback.</li>
 *   <li><b>Duplicate loads.</b> Repeated triggers for the same still-loading
 *   scope (e.g. tab load finishing while the file name is re-set) must not
 *   spawn extra worker threads.</li>
 *   <li><b>Stale applied record.</b> The language live on the editor belongs
 *   to the scope it was applied for. Recording it without that association
 *   made a still-pending request for a NEW scope dedup-skip against the old
 *   scope's language, so the new scope never loaded. The applied record is
 *   cleared whenever a fresh request is decided and is scoped to what it was
 *   applied for.</li>
 * </ul>
 */
public final class SyntaxApplyPolicy {

    /** What the caller must do for the requested scope. */
    public enum Decision {
        /** Requested scope is already live on the current editor; refresh scheme only. */
        SKIP_APPLIED,
        /** The same scope is still loading off-thread; its completion will apply it. */
        AWAIT_IN_FLIGHT,
        /** Apply the plain (no-highlight) language synchronously. */
        APPLY_PLAIN,
        /** Load the TextMate grammar off-thread for the requested scope. */
        LOAD_ASYNC
    }

    /** Scope of the most recent request; null = plain text requested. */
    private String requestedScope;
    /** Bumped for every APPLY_PLAIN / LOAD_ASYNC decision, never on skips. */
    private int generation;
    /** Generation of a worker still loading, or -1 when none is in flight. */
    private int inflightGeneration = -1;
    /** Language instance actually set on the current editor, or null if none. */
    private Object appliedLanguage;
    /** Scope the applied language was applied for; null when nothing is recorded. */
    private String appliedScope;

    /**
     * True when the editor's current language is the instance this policy last
     * recorded as applied. Identity (==) is used on purpose: a recreated editor
     * view starts with its own default language, which can never be the
     * previously applied instance.
     */
    public synchronized boolean isAppliedToEditor(Object editorLanguage) {
        return appliedLanguage != null && appliedLanguage == editorLanguage;
    }

    /**
     * Decides what to do for the requested scope.
     *
     * @param scope             TextMate scope to apply, or null for plain text
     * @param appliedToEditor   whether the editor currently holds the language
     *                          this policy recorded as applied
     */
    public synchronized Decision onRequest(String scope, boolean appliedToEditor) {
        // Skip only when the editor holds the language recorded FOR THIS SCOPE:
        // a language applied for an earlier scope must never satisfy a new one.
        if (appliedToEditor && Objects.equals(scope, requestedScope) && Objects.equals(scope, appliedScope)) {
            return Decision.SKIP_APPLIED;
        }
        if (Objects.equals(scope, requestedScope) && inflightGeneration == generation) {
            return Decision.AWAIT_IN_FLIGHT;
        }
        // Fresh decision: whatever is applied belongs to a previous request and
        // will be replaced by this one (plain or async load), so forget it.
        appliedLanguage = null;
        appliedScope = null;
        requestedScope = scope;
        generation++;
        // Arm the in-flight slot for async loads so repeated triggers (e.g. the
        // file name being re-set when the tab content finishes loading) await
        // the running worker instead of spawning duplicate grammar loads.
        inflightGeneration = scope == null ? -1 : generation;
        return scope == null ? Decision.APPLY_PLAIN : Decision.LOAD_ASYNC;
    }

    /** Generation to hand to the worker spawned for the current LOAD_ASYNC decision. */
    public synchronized int currentGeneration() {
        return generation;
    }

    /** True when the given generation is still the most recent request. */
    public synchronized boolean isCurrent(int gen) {
        return gen == generation;
    }

    /** Scope of the most recent request (null = plain text). */
    public synchronized String requestedScope() {
        return requestedScope;
    }

    /**
     * Records the language instance that was successfully set on the editor for
     * the given generation (a TextMate language wrapper, or the plain language
     * for a null scope) and releases the in-flight slot.
     */
    public synchronized void onLanguageApplied(Object language, int gen) {
        appliedLanguage = language;
        appliedScope = requestedScope;
        clearInflight(gen);
    }

    /**
     * A plain fallback was set on the editor because the TextMate load failed.
     * The editor language identity is left unrecorded so the next trigger for
     * the same scope retries the load instead of treating the fallback as a
     * satisfied request (a transient failure must not disable highlighting for
     * the rest of the session).
     */
    public synchronized void onLoadFailed(int gen) {
        clearInflight(gen);
    }

    /**
     * The async result was dropped before it could be applied (fragment
     * detached, view gone). Invalidates the recorded scope so the next trigger
     * retries instead of being deduped away; requests superseded by a newer
     * generation are left untouched.
     */
    public synchronized void onResultDropped(int gen) {
        clearInflight(gen);
        if (gen == generation) {
            requestedScope = null;
        }
    }

    /**
     * The editor view holding the applied language was destroyed while the
     * fragment instance survives (FragmentStateAdapter page view recreation).
     * Forget the applied record so a recreated view re-applies the requested
     * scope. In-flight workers are left alone: a landing result is re-validated
     * against the live editor by the fragment.
     */
    public synchronized void onViewDestroyed() {
        appliedLanguage = null;
        appliedScope = null;
    }

    /** A stale worker finished after a newer request superseded it: just release bookkeeping. */
    public synchronized void onResultSuperseded(int gen) {
        clearInflight(gen);
    }

    private void clearInflight(int gen) {
        if (inflightGeneration == gen) {
            inflightGeneration = -1;
        }
    }
}
