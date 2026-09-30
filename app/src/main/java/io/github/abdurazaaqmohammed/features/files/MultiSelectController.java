package io.github.abdurazaaqmohammed.features.files;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.graphics.drawable.RotateDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;


import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.util.ArrayList;
import java.util.List;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * Bottom-bar multi-select mode UI extracted from MainActivity.
 */
public class MultiSelectController {

    private final MainActivity activity;
    private boolean multiSelectUIActive;
    private final ImageButton[] multiSelectButtons = new ImageButton[4];
    private RotateDrawable addButtonRotateDrawable;
    private Animator addButtonRotationAnimator;

    public MultiSelectController(MainActivity activity) {
        this.activity = activity;
    }

    public void clearPaneSelection(boolean pane1) {
        try {
            RecyclerView paneView = activity.findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
            if (paneView != null && paneView.getAdapter() instanceof MainFilesArrayAdapter a) a.clearSelection();
            else setMultiSelectModeUI(false);
        } catch (Exception ignored) {
        }
    }

    public void setMultiSelectModeUI(boolean enabled) {
        if (multiSelectUIActive == enabled) return;
        multiSelectUIActive = enabled;
        activity.handler.post(() -> {
            LinearLayout bottomBar = activity.findViewById(R.id.bottomBar);
            int[] defaultIds = {R.id.backButton, R.id.forwardButton, R.id.syncPaneButton, R.id.upButton};
            ImageView addButton = activity.findViewById(R.id.addButton);
            if (enabled) {
                if (multiSelectButtons[0] == null) buildMultiSelectButtons();
                for (int id : defaultIds) activity.findViewById(id).setVisibility(View.GONE);
                addButton.setVisibility(View.VISIBLE);
                addButton.setContentDescription(activity.rss.getString(R.string.exit));
                if (LegacyUtils.aboveSdk20) animateAddButtonRotation(10000);
                else addButton.animate().rotation(45f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
                int addIndex = bottomBar.indexOfChild(addButton);
                bottomBar.addView(multiSelectButtons[0], addIndex);
                bottomBar.addView(multiSelectButtons[1], addIndex + 1);
                addIndex = bottomBar.indexOfChild(addButton);
                bottomBar.addView(multiSelectButtons[2], addIndex + 1);
                bottomBar.addView(multiSelectButtons[3], addIndex + 2);
                for (ImageButton button : multiSelectButtons) {
                    button.setOnTouchListener(activity.bottomBarTouchListener());
                }
            } else {
                for (ImageButton button : multiSelectButtons) bottomBar.removeView(button);
                for (int id : defaultIds) activity.findViewById(id).setVisibility(View.VISIBLE);
                addButton.setVisibility(View.VISIBLE);
                addButton.setContentDescription(activity.rss.getString(R.string.newFileOrFolder));
                if (LegacyUtils.aboveSdk20) animateAddButtonRotation(0);
                else addButton.animate().rotation(0f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private RotateDrawable getAddButtonRotateDrawable() {
        if (addButtonRotateDrawable == null) {
            ImageView addButton = activity.findViewById(R.id.addButton);
            addButtonRotateDrawable = new RotateDrawable();
            addButtonRotateDrawable.setDrawable(addButton.getDrawable());
            addButtonRotateDrawable.setFromDegrees(0f);
            addButtonRotateDrawable.setToDegrees(45f);
            addButtonRotateDrawable.setLevel(0);
            addButton.setImageDrawable(addButtonRotateDrawable);
        }
        return addButtonRotateDrawable;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void animateAddButtonRotation(int targetLevel) {
        if (addButtonRotationAnimator != null) addButtonRotationAnimator.cancel();
        RotateDrawable rd = getAddButtonRotateDrawable();
        addButtonRotationAnimator = ObjectAnimator.ofInt(rd, "level", rd.getLevel(), targetLevel);
        addButtonRotationAnimator.setDuration(500);
        addButtonRotationAnimator.setInterpolator(new DecelerateInterpolator());
        addButtonRotationAnimator.start();
    }

    private void buildMultiSelectButtons() {
        int[] icons = {R.drawable.baseline_select_all_24, R.drawable.tab_inactive_24px, R.drawable.flip_24px, R.drawable.baseline_info_24};
        View.OnClickListener[] listeners = {
                v -> { for (MainFilesArrayAdapter a : activeMultiSelectAdapters()) a.selectAll(); },
                v -> { for (MainFilesArrayAdapter a : activeMultiSelectAdapters()) a.invertSelection(); },
                v -> { for (MainFilesArrayAdapter a : activeMultiSelectAdapters()) a.selectSameType(); },
                v -> UIKit.dialog(activity)
                        .setTitle(R.string.multi_select)
                        .setMessage(activity.getString(R.string.multiselect_hint))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
        };
        CharSequence[] cds = {activity.rss.getString(android.R.string.selectAll), activity.rss.getString(R.string.invert_selection), activity.rss.getString(R.string.select_same_type), activity.rss.getString(R.string.multi_select)};
        TypedValue tv = new TypedValue();
        Resources.Theme t = activity.getTheme();
        t.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
        int color = tv.data;
        t.resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        int bg = tv.resourceId;
        View.OnLongClickListener ocl = v -> {
            Extensions.showMessage(activity, v.getContentDescription());
            return false;
        };
        int ay = (int) (8 * activity.rss.getDisplayMetrics().density + 0.5f);
        for (int i = 0; i < multiSelectButtons.length; i++) {
            ImageButton button = new ImageButton(activity);
            button.setImageResource(icons[i]);
            button.setBackgroundResource(bg);
            androidx.core.graphics.drawable.DrawableCompat.setTint(button.getDrawable(), color);
            button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            button.setPadding(ay, ay, ay, ay);
            button.setOnClickListener(listeners[i]);
            button.setContentDescription(cds[i]);
            button.setOnLongClickListener(ocl);
            multiSelectButtons[i] = button;
        }
    }

    private MainFilesArrayAdapter getMainFilesAdapter(int pane) {
        RecyclerView paneView = activity.findViewById(pane == 1 ? R.id.listViewPane1 : R.id.listViewPane2);
        RecyclerView.Adapter a = paneView.getAdapter();
        return a instanceof MainFilesArrayAdapter ? (MainFilesArrayAdapter) a : null;
    }

    public void onPaneTouched(int pane) {
        MainFilesArrayAdapter a = getMainFilesAdapter(pane);
        setMultiSelectModeUI(a != null && a.isMultiSelectMode());
    }

    public boolean isActive() {
        return multiSelectUIActive;
    }

    public void exitAll() {
        for (MainFilesArrayAdapter a : activeMultiSelectAdapters()) a.exitMultiSelectMode();
    }

    private List<MainFilesArrayAdapter> activeMultiSelectAdapters() {
        List<MainFilesArrayAdapter> out = new ArrayList<>();
        for (int id : new int[]{R.id.listViewPane1, R.id.listViewPane2}) {
            RecyclerView pane = activity.findViewById(id);
            if (pane.getAdapter() instanceof MainFilesArrayAdapter adapter && adapter.isMultiSelectMode())
                out.add(adapter);
        }
        return out;
    }
}
