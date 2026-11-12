package io.github.abdurazaaqmohammed.features.files;

import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Same-pane copy/move: long-pressing Copy or Move in the file menu parks the
 * selection here instead of targeting the opposite pane. The user then
 * navigates folders in the same pane, and two floating buttons at the bottom
 * right of that pane offer Paste and Cancel.
 *
 * <p>The paste target is whatever folder the pane shows when Paste is tapped,
 * so the FABs stay up across navigation. A move refuses to land inside one of
 * the moved folders themselves.
 */
public class SamePanePasteController {

    private final MainActivity activity;
    private ViewGroup pasteBar;
    private boolean active;
    private boolean move;
    private List<Object> items;
    private boolean pane1;

    public SamePanePasteController(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Binds the bar views on first use. The controller is constructed before
     * onCreate, so views cannot be resolved from the constructor.
     */
    private void ensureViews() {
        if (pasteBar != null) return;
        pasteBar = activity.findViewById(R.id.samePanePasteBar);
        if (pasteBar != null) {
            pasteBar.findViewById(R.id.samePanePasteFab).setOnClickListener(v -> paste());
            pasteBar.findViewById(R.id.samePaneCancelFab).setOnClickListener(v -> cancel());
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMove() {
        return move;
    }

    private Snackbar currentSnackbar;

    /** Starts the mode: parks {@code items} and shows the FABs over {@code pane1}. */
    public void start(boolean move, List<Object> items, boolean pane1) {
        ensureViews();
        if (items == null || items.isEmpty() || pasteBar == null) return;
        this.move = move;
        this.items = items;
        this.pane1 = pane1;
        this.active = true;
        positionBar();
        pasteBar.setVisibility(View.VISIBLE);
        String hint = activity.getString(move
                ? R.string.same_pane_move_hint : R.string.same_pane_copy_hint);
        
        dismissSnackbar();
        View anchor = activity.findViewById(R.id.bottomBar);
        View root = (anchor != null && anchor.getVisibility() == View.VISIBLE) ? anchor : activity.findViewById(android.R.id.content);
        if (root != null) {
            currentSnackbar = Snackbar.make(root, hint, Snackbar.LENGTH_LONG);
            if (anchor != null && anchor.getVisibility() == View.VISIBLE) {
                currentSnackbar.setAnchorView(anchor);
            }
            currentSnackbar.setAction(android.R.string.ok, v -> dismissSnackbar());
            currentSnackbar.getView().setOnClickListener(v -> dismissSnackbar());
            currentSnackbar.show();
        }
    }

    private void dismissSnackbar() {
        if (currentSnackbar != null) {
            try {
                currentSnackbar.dismiss();
            } catch (Exception ignored) {
            }
            currentSnackbar = null;
        }
    }

    public void cancel() {
        active = false;
        items = null;
        dismissSnackbar();
        if (pasteBar != null) pasteBar.setVisibility(View.GONE);
    }

    /** Back button hook: the mode ends first, navigation continues next press. */
    public boolean onBackPressed() {
        if (!active) return false;
        cancel();
        return true;
    }

    private void paste() {
        if (!active || items == null || items.isEmpty()) {
            cancel();
            return;
        }
        File dest = pane1 ? activity.pane1Folder : activity.pane2Folder;
        if (dest == null || !(dest.isDirectory())) {
            Extensions.showMessage(activity, R.string.same_pane_target_gone);
            return;
        }
        if (move) {
            for (Object item : items) {
                if (item instanceof File f && f.isDirectory() && isInside(dest, f)) {
                    Extensions.showMessage(activity, R.string.same_pane_invalid_target);
                    return;
                }
            }
        }
        List<Object> payload = items;
        cancel();
        io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter sourceAdapter =
                activity.paneFilesAdapter(pane1);
        if (sourceAdapter == null) return;
        if (move) sourceAdapter.getFileOperations().moveItemsToFolder(payload, dest);
        else sourceAdapter.getFileOperations().copyItemsToFolder(payload, dest);
    }

    /** True when {@code candidate} is {@code ancestor} itself or below it. */
    private static boolean isInside(File candidate, File ancestor) {
        try {
            String c = candidate.getCanonicalPath();
            String a = ancestor.getCanonicalPath();
            return c.equals(a) || c.startsWith(a + File.separator);
        } catch (Exception e) {
            String c = candidate.getAbsolutePath();
            String a = ancestor.getAbsolutePath();
            return c.equals(a) || c.startsWith(a + File.separator);
        }
    }

    /**
     * Slides the bar over the pane it belongs to. The bar is anchored to the
     * parent's end edge, so pane 1 only needs a negative X shift.
     */
    private void positionBar() {
        View pane = activity.findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        if (pane == null || pasteBar == null) return;
        pasteBar.setTranslationX(0f);
        pasteBar.post(() -> {
            if (!active || pasteBar == null) return;
            int margin = (int) (16 * activity.getResources().getDisplayMetrics().density);
            int[] paneLoc = new int[2];
            pane.getLocationOnScreen(paneLoc);
            int[] barLoc = new int[2];
            pasteBar.getLocationOnScreen(barLoc);
            float dx = (paneLoc[0] + pane.getWidth() - margin) - (barLoc[0] + pasteBar.getWidth());
            if (dx < 0) pasteBar.setTranslationX(dx);
        });
    }
}
