package io.github.abdurazaaqmohammed.feature.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression checks for the intermittent "XML/AndroidManifest.xml opens without
 * highlighting" bug in UnifiedEditorFragment#applySyntax.
 *
 * The old implementation deduped on the requested scope alone
 * (Objects.equals(scope, currentSyntaxScope)), which permanently skipped
 * re-applying the language whenever the editor view was recreated by a
 * configuration change (fragment instances survive, views do not) or whenever
 * an async TextMate load was dropped or failed. These tests pin the corrected
 * decision rules in SyntaxApplyPolicy.
 */
public class SyntaxApplyPolicyTest {

    private static final String XML = "text.xml";

    @Test
    public void xmlScopeLoadsThenSkipsOnlyWhileLanguageIsLive() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));

        Object language = new Object();
        policy.onLanguageApplied(language, policy.currentGeneration());

        // Same scope, language still attached to the editor: skip (refresh scheme only).
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest(XML, true));
    }

    @Test
    public void recreatedEditorViewReloadsSameScopeInsteadOfSkipping() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        Object language = new Object();
        policy.onLanguageApplied(language, policy.currentGeneration());

        // Configuration change: the fragment (and this policy) survives while the
        // editor view is recreated with its default language, so the applied
        // identity no longer matches. The scope must be re-applied; the old
        // scope-only dedup left the new view without highlighting.
        assertFalse(policy.isAppliedToEditor(new Object()));
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
    }

    @Test
    public void droppedAsyncResultIsRetriedOnNextTrigger() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        int generation = policy.currentGeneration();

        // Worker finished while the fragment was detached: result discarded.
        policy.onResultDropped(generation);
        assertNull(policy.requestedScope());

        // The next trigger (tab reselect / file name re-set) must retry the load
        // instead of deduping against the dropped request.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
    }

    @Test
    public void failedLoadIsRetriedInsteadOfStickingAsPlainText() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        int generation = policy.currentGeneration();

        // TextMate load failed; the fragment applies a plain fallback. The
        // fallback must not be recorded as a satisfied request, otherwise every
        // later trigger dedup-skips and highlighting stays off for good.
        Object fallback = new Object();
        policy.onLoadFailed(generation);
        assertFalse(policy.isAppliedToEditor(fallback));
        assertEquals(XML, policy.requestedScope());
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
    }

    @Test
    public void inFlightRequestDoesNotSpawnDuplicateLoads() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        int generation = policy.currentGeneration();

        // Repeated triggers for the same request (e.g. applyLoadedText re-setting
        // the file name while the first grammar load is still parsing) await the
        // running worker rather than starting another one.
        assertEquals(SyntaxApplyPolicy.Decision.AWAIT_IN_FLIGHT, policy.onRequest(XML, false));

        // The in-flight worker still owns its generation until it lands.
        assertTrue(policy.isCurrent(generation));
        policy.onLanguageApplied(new Object(), generation);
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest(XML, true));
    }

    @Test
    public void plainScopeSkipsWhileLiveAndReappliesOnFreshView() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.APPLY_PLAIN, policy.onRequest(null, false));
        Object plain = new Object();
        policy.onLanguageApplied(plain, policy.currentGeneration());
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest(null, true));
        assertNull(policy.requestedScope());

        // Recreated view: re-apply the plain language (cheap) and refresh scheme.
        assertEquals(SyntaxApplyPolicy.Decision.APPLY_PLAIN, policy.onRequest(null, false));
    }

    @Test
    public void supersededWorkerDoesNotClobberNewerRequest() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        int xmlGeneration = policy.currentGeneration();

        // User switches to a plain-text tab while the XML grammar is parsing.
        assertEquals(SyntaxApplyPolicy.Decision.APPLY_PLAIN, policy.onRequest(null, false));
        int plainGeneration = policy.currentGeneration();
        assertFalse(policy.isCurrent(xmlGeneration));
        assertTrue(policy.isCurrent(plainGeneration));

        // The stale XML worker finishes late: bookkeeping is released, and the
        // plain request is not marked applied by it.
        policy.onResultSuperseded(xmlGeneration);
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        int xmlGeneration2 = policy.currentGeneration();
        assertTrue(policy.isCurrent(xmlGeneration2));

        // The second XML worker lands and applies.
        Object xmlLanguage = new Object();
        policy.onLanguageApplied(xmlLanguage, xmlGeneration2);
        assertTrue(policy.isAppliedToEditor(xmlLanguage));
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest(XML, true));
    }

    @Test
    public void appliedIdentityIsStrictlyInstanceScoped() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        policy.onLanguageApplied(new Object(), policy.currentGeneration());
        assertFalse(policy.isAppliedToEditor(null));
        assertFalse(policy.isAppliedToEditor(new Object()));
    }

    @Test
    public void pendingNewScopeNeverSkipsAgainstPreviouslyAppliedLanguage() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        // Scope A (XML) applied and live on the editor.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        Object xmlLanguage = new Object();
        policy.onLanguageApplied(xmlLanguage, policy.currentGeneration());
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest(XML, true));

        // Scope B requested: the editor still holds A's language, but B must load.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest("source.java", true));

        // A repeated B trigger while B loads must not skip against A's language
        // (the stale-scope bug that permanently prevented the new scope from
        // ever loading).
        assertEquals(SyntaxApplyPolicy.Decision.AWAIT_IN_FLIGHT, policy.onRequest("source.java", true));

        // B lands: from then on B is the applied scope.
        Object javaLanguage = new Object();
        policy.onLanguageApplied(javaLanguage, policy.currentGeneration());
        assertFalse(policy.isAppliedToEditor(xmlLanguage));
        assertEquals(SyntaxApplyPolicy.Decision.SKIP_APPLIED, policy.onRequest("source.java", true));
        // Back to A: must load again, not skip against B.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, true));
    }

    @Test
    public void failureFallbackAfterScopeSwitchRetriesNewScope() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        policy.onLanguageApplied(new Object(), policy.currentGeneration());

        // Switch to a new scope whose load then fails and falls back to plain.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest("source.java", true));
        int javaGeneration = policy.currentGeneration();
        Object fallback = new Object();
        policy.onLoadFailed(javaGeneration);
        assertFalse(policy.isAppliedToEditor(fallback));

        // The failed scope must retry on the next trigger, and it must not be
        // satisfied by anything left over from the previous scope.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest("source.java", false));
    }

    @Test
    public void viewDestroyedForgetsAppliedRecordSoRecreatedViewReloads() {
        SyntaxApplyPolicy policy = new SyntaxApplyPolicy();
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
        Object language = new Object();
        policy.onLanguageApplied(language, policy.currentGeneration());
        assertTrue(policy.isAppliedToEditor(language));

        // FragmentStateAdapter page switching: view destroyed, instance kept.
        policy.onViewDestroyed();
        assertFalse(policy.isAppliedToEditor(language));
        assertEquals(XML, policy.requestedScope());
        // Recreated view triggers the same scope: must reload, not skip.
        assertEquals(SyntaxApplyPolicy.Decision.LOAD_ASYNC, policy.onRequest(XML, false));
    }
}
