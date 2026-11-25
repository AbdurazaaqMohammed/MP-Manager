package io.github.abdurazaaqmohammed.ui.common.base;


import io.github.abdurazaaqmohammed.util.misc.EditorTasks;
import io.github.abdurazaaqmohammed.data.prefs.MinimizedEditors;

import android.R;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.common.util.AppFont;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;

/**
 * Single place for activity-wide UI behaviour.
 * All feature activities should extend this instead of AppCompatActivity
 * so theme switching, dynamic colors and edge-to-edge change in one file.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private int appliedFontEpoch = -1;
    private int appliedThemeEpoch = -1;
    private String editorOwnerId;
    private Runnable themeListener;
    private volatile boolean resumed;
    private boolean explicitFinish;
    private boolean minimizedFinish;
    private MinimizedEditors.ParkedState pendingParkedState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        // Stacked accent overlay (also before super.onCreate).
        ThemeRegistry.applyAccent(this);
        // Font factory must also wrap AppCompat's, so install before super.onCreate.
        AppFont.installFactory(this);
        appliedFontEpoch = AppFont.currentEpoch();
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            pendingParkedState = MinimizedEditors.takePendingParkedState(getIntent());
        }
        // Capture the shared theme revision once the theme is fully applied;
        // later bumps (selection change, import/refresh, accent) recreate us.
        appliedThemeEpoch = ThemeRegistry.currentEpoch();
        // Weakly held by the registry; unregistered in onDestroy. The
        // listener only acts while foregrounded — backgrounded activities
        // catch up through the resume epoch check below.
        themeListener = this::handleThemeChanged;
        ThemeRegistry.addActivityListener(themeListener);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        editorOwnerId = EditorTasks.bind(this, savedInstanceState);
        EditorTasks.syncActivityTaskRecents(this);
        MinimizedEditors.rebind(this);
        View content = getWindow().getDecorView().findViewById(R.id.content);
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(content);
        }
        // System bars + scoped wallpaper background for every themed
        // activity; activities with their own wrapper override
        // themeAppliesSystemBars() (MainActivity keeps its wrapper).
        if (themeAppliesSystemBars()) {
            ThemeRegistry.applySystemBars(this);
        }
        ThemeRegistry.applyActivityBackground(this);
    }

    /**
     * Foreground-only theme change entry point. The shared revision is
     * refreshed BEFORE the hook so a recreate triggered here cannot re-trigger
     * itself through the resume check.
     */
    private void handleThemeChanged() {
        if (!resumed || isFinishing() || isDestroyed()
                || appliedThemeEpoch == ThemeRegistry.currentEpoch()) return;
        appliedThemeEpoch = ThemeRegistry.currentEpoch();
        onThemeChanged();
    }

    /**
     * Called when the applied theme changed while this activity was
     * foregrounded. Default recreates; editors may override for an in-place
     * refresh (only where state restoration is verified).
     */
    protected void onThemeChanged() {
        recreate();
    }

    /**
     * Override to false when the activity applies system bar colors itself
     * (MainActivity's wrapper) so tinting is not applied twice.
     */
    protected boolean themeAppliesSystemBars() {
        return true;
    }

    protected final boolean wasExplicitlyFinished() {
        return explicitFinish;
    }

    protected final boolean shouldDiscardStateOnDestroy() {
        return !minimizedFinish && explicitFinish;
    }

    protected final <T extends MinimizedEditors.ParkedState> T takeParkedState(Class<T> type) {
        if (type != null && type.isInstance(pendingParkedState)) {
            T state = type.cast(pendingParkedState);
            pendingParkedState = null;
            return state;
        }
        return null;
    }

    public MinimizedEditors.ParkedState createParkedStateForMinimize() {
        return null;
    }

    protected String getMinimizeTitle() {
        CharSequence title = getTitle();
        return title != null ? title.toString() : null;
    }

    protected boolean shouldAutoMinimize() {
        return EditorTasks.isSecondaryTaskRoot(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        EditorTasks.syncActivityTaskRecents(this);
        MinimizedEditors.unregister(this);
        if (getIntent() != null && getIntent().getBooleanExtra("restore_progress", false)) {
            getIntent().removeExtra("restore_progress");
            ProgressManager.restoreHidden();
        }
        // Pick up a font changed in Settings while this activity was in background.
        if (appliedFontEpoch != AppFont.currentEpoch()) recreate();
        // Pick up a theme change that happened while backgrounded (the live
        // listener only fires while foregrounded).
        if (appliedThemeEpoch != ThemeRegistry.currentEpoch()) {
            handleThemeChanged();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        int previousNight = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        super.onConfigurationChanged(newConfig);
        ThemeRegistry.reload(this);
        if (previousNight != (newConfig.uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)) handleThemeChanged();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!explicitFinish && !minimizedFinish && !isFinishing()
                && !isChangingConfigurations() && shouldAutoMinimize()) {
            MinimizedEditors.minimizeSilently(this, getMinimizeTitle());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("restore_progress", false)) {
            ProgressManager.restoreHidden();
        }
    }

    @Override
    protected void onDestroy() {
        // Explicit user exits unregister immediately; OS background evictions keep
        // reopenIntent entries intact, and minimized pops keep their parked state.
        if (explicitFinish && !minimizedFinish) MinimizedEditors.unregister(this);
        if (!isChangingConfigurations() && pendingParkedState != null) {
            pendingParkedState.discard();
            pendingParkedState = null;
        }
        EditorTasks.unbind(editorOwnerId, this);
        ThemeRegistry.removeActivityListener(themeListener);
        themeListener = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        EditorTasks.saveOwner(state, editorOwnerId);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        if (EditorTasks.isEditor(intent)) {
            if (EditorTasks.showEditorTasksInRecents(this)) {
                super.startActivityForResult(EditorTasks.prepare(this, intent, editorOwnerId, requestCode), -1, options);
            } else {
                super.startActivityForResult(EditorTasks.prepareSingleTask(intent, requestCode), requestCode, options);
            }
        } else {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    public final void dispatchEditorResult(int request, int result, Intent data) {
        onActivityResult(request, result, data);
    }

    public final void setEditorResult(int code, Intent data) {
        setResult(code, data);
        EditorTasks.setResult(this, code, data);
    }

    public final void setEditorResult(int code) { setEditorResult(code, null); }

    /** Pops a minimized activity back down to its caller inside the same task without discarding state. */
    public final void finishMinimizedToParent() {
        minimizedFinish = true;
        setResult(RESULT_CANCELED);
        super.finish();
        overridePendingTransition(0, 0);
    }

    /** Closes a background minimized task silently without bringing MainActivity over itself. */
    public final void finishQuietlyFromMinimized() {
        explicitFinish = true;
        MinimizedEditors.unregister(this);
        EditorTasks.finished(this);
        if (EditorTasks.isSecondaryTaskRoot(this)) {
            super.finishAndRemoveTask();
            overridePendingTransition(0, 0);
        } else {
            super.finish();
        }
    }

    @Override
    public void finish() {
        if (minimizedFinish) {
            super.finish();
            return;
        }
        explicitFinish = true;
        MinimizedEditors.unregister(this);
        EditorTasks.finished(this);
        if (EditorTasks.isSecondaryTaskRoot(this)) {
            boolean shouldReturnToMain = EditorTasks.isEditor(this)
                    || (getIntent() != null && getIntent().getBooleanExtra(EditorTasks.EXTRA_RESTORED_FROM_MINIMIZED, false));
            if (shouldReturnToMain) EditorTasks.returnToMain(this);
            super.finishAndRemoveTask();
            overridePendingTransition(0, 0);
        } else {
            super.finish();
        }
    }

    @Override
    public void finishAndRemoveTask() {
        if (!minimizedFinish) {
            explicitFinish = true;
            MinimizedEditors.unregister(this);
        }
        super.finishAndRemoveTask();
    }

    @Override
    public void finishAffinity() {
        if (!minimizedFinish) {
            explicitFinish = true;
            MinimizedEditors.unregister(this);
        }
        super.finishAffinity();
    }

    @Override
    public void finishAfterTransition() {
        if (!minimizedFinish) {
            explicitFinish = true;
            MinimizedEditors.unregister(this);
        }
        super.finishAfterTransition();
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
