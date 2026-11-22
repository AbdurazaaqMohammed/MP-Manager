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

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("restore_progress", false)) {
            ProgressManager.restoreHidden();
        }
    }

    @Override
    protected void onDestroy() {
        // A minimized editor keeps its registry entry until it really exits;
        // configuration recreations (isFinishing == false) don't count.
        if (isFinishing()) MinimizedEditors.unregister(this);
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
            super.startActivityForResult(EditorTasks.prepare(intent, editorOwnerId, requestCode), -1, options);
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

    @Override
    public void finish() {
        EditorTasks.finished(this);
        if (EditorTasks.isEditor(this)) {
            EditorTasks.returnToMain(this);
            finishAndRemoveTask();
            overridePendingTransition(0, 0);
        } else {
            super.finish();
        }
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
