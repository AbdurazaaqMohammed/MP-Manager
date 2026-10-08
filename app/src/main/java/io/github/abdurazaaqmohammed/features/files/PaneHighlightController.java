package io.github.abdurazaaqmohammed.features.files;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.preference.PreferenceManager;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.PaneHighlightView;

/**
 * Hosts the sliding "selected pane" highlight in {@link MainActivity}: a fill
 * layer inserted behind the pane row and an overlay layer on top of it, both
 * framed exactly like the row and animated together so the highlight sweeps
 * from one pane to the other on selection change.
 */
public class PaneHighlightController {

    private static final long DURATION_MS = 320;

    /** Preference key: whether pane switching animates the highlight. */
    public static final String ANIMATE_PREF_KEY = "pane_highlight_animate";

    private final MainActivity activity;
    private PaneHighlightView fillLayer, overlayLayer;
    private ValueAnimator animator;
    private boolean animateEnabled = true;

    public PaneHighlightController(MainActivity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (fillLayer != null) return;
        ViewGroup main = activity.findViewById(R.id.main);
        View row = main.findViewById(R.id.panesRow);
        View pane1 = main.findViewById(R.id.swipeRefreshPane1);
        View pane2 = main.findViewById(R.id.swipeRefreshPane2);
        if (row == null || pane1 == null || pane2 == null) return;

        fillLayer = new PaneHighlightView(activity, true);
        overlayLayer = new PaneHighlightView(activity, false);
        fillLayer.setTargets(row, pane1, pane2);
        overlayLayer.setTargets(row, pane1, pane2);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.BELOW, R.id.topBar);
        lp.addRule(RelativeLayout.ABOVE, R.id.bottomBar);
        main.addView(fillLayer, 0, lp);                    // behind the pane row
        main.addView(overlayLayer, main.getChildCount(), lp); // on top, pass-through touches

        reload();
        onPaneSelected(activity.lastPaneSelected, false);
    }

    /** Re-reads the style prefs (called when returning from Settings). */
    public void reload() {
        if (fillLayer == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int mode = PaneHighlightView.modeFromString(
                prefs.getString(PaneHighlightView.PREF_KEY, PaneHighlightView.DEFAULT_STYLE));
        fillLayer.setMode(mode);
        overlayLayer.setMode(mode);
        animateEnabled = prefs.getBoolean(ANIMATE_PREF_KEY, true);
    }

    /**
     * Moves the highlight to the given pane. Pass {@code animated=false} for
     * programmatic/initial selection; the animation only runs when enabled in
     * Settings and the highlight actually changes sides.
     */
    public void onPaneSelected(int pane, boolean animated) {
        if (fillLayer == null) return;
        cancelAnimator();
        float target = pane == 1 ? 0f : 1f;
        float start = overlayLayer.getPos();
        if (!animated || !animateEnabled || Math.abs(target - start) < 0.001f) {
            fillLayer.snapTo(target);
            overlayLayer.snapTo(target);
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            fillLayer.setTransition(start, target, f);
            overlayLayer.setTransition(start, target, f);
        });
        animator.start();
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
