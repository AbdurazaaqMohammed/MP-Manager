package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ButtonBarLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class DialogUtil {
    private final Activity context;

    public DialogUtil(Activity c) {
        this.context = c;
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return new MaterialAlertDialogBuilder(context);
    }

    /**
     * Keeps a dialog's action buttons on one horizontal row. Vertical stacking
     * is disabled globally by the app's overrides of
     * {@code mtrl_alert_dialog_actions} / {@code m3_alert_dialog_actions}
     * ({@code app:allowStacking="false"}); this helper re-asserts that at
     * runtime (covering dialogs whose theme differs) without touching button
     * sizes, so labels always keep their natural width and stay readable.
     *
     * <p>Must be called after the dialog is shown (buttons are only inflated
     * then), and it does not touch the dialog's own show listener.
     */
    public static void keepButtonsHorizontal(AlertDialog dialog) {
        if (dialog == null) return;
        View decor = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
        if (decor == null) return;
        decor.post(() -> forceButtonsHorizontal(dialog));
        decor.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                v.post(() -> forceButtonsHorizontal(dialog)));
    }

    private static void forceButtonsHorizontal(AlertDialog dialog) {
        LinearLayout panel = buttonPanel(dialog);
        if (panel == null) return;
        try {
            if (panel instanceof ButtonBarLayout bar) {
                // Cheap no-op when already false (guarded inside appcompat).
                bar.setAllowStacking(false);
            } else {
                panel.setOrientation(LinearLayout.HORIZONTAL);
            }
            Context context = panel.getContext();
            panel.setPadding(dp(context, 12), panel.getPaddingTop(), dp(context, 12), panel.getPaddingBottom());
            panel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            // Eliminate any expanding spacers between buttons to prevent huge gaps
            for (int i = 0; i < panel.getChildCount(); i++) {
                View child = panel.getChildAt(i);
                if (!(child instanceof Button)) {
                    child.setVisibility(View.GONE);
                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                    if (lp instanceof LinearLayout.LayoutParams llp) {
                        llp.weight = 0;
                        llp.width = 0;
                        child.setLayoutParams(llp);
                    }
                }
            }

            int[] buttonIds = {AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE};
            for (int id : buttonIds) {
                Button btn = dialog.getButton(id);
                if (btn != null && btn.getVisibility() != View.GONE) {
                    btn.setPadding(dp(btn.getContext(), 8), btn.getPaddingTop(), dp(btn.getContext(), 8), btn.getPaddingBottom());
                    btn.setMinWidth(0);
                    btn.setMinimumWidth(0);
                    ViewGroup.LayoutParams lp = btn.getLayoutParams();
                    if (lp instanceof LinearLayout.LayoutParams llp) {
                        llp.weight = 0;
                        llp.leftMargin = dp(btn.getContext(), 4);
                        llp.rightMargin = dp(btn.getContext(), 4);
                        btn.setLayoutParams(llp);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    /** The row that holds the action buttons, found via any visible button's parent. */
    private static LinearLayout buttonPanel(AlertDialog dialog) {
        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null && positive.getParent() instanceof LinearLayout p) return p;
        View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null && negative.getParent() instanceof LinearLayout p2) return p2;
        View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null && neutral.getParent() instanceof LinearLayout p3) return p3;
        return null;
    }

    /**
     * Shows the dialog with its buttons pinned to a single horizontal row (see
     * {@link #keepButtonsHorizontal}). Dialogs shown through this helper never
     * end up with a vertical, scroll-requiring button bar.
     */
    public void styleAlertDialog(AlertDialog ad) {
        context.runOnUiThread(() -> {
            ad.show();
            keepButtonsHorizontal(ad);
        });
    }

    /**
     * Starts a directory picker and hands the result to the given launcher.
     */
    public void pickDirectory(AppCompatActivity activity, TextInputEditText outputPathInput,
            ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Enter output directory path manually", Toast.LENGTH_SHORT).show();
        }
    }

    private static String getPathFromTreeUri(Uri treeUri) {
        if (treeUri == null) return null;
        if ("content".equals(treeUri.getScheme())) {
            // Best-effort: some providers expose a path via getData()
            return treeUri.getPath();
        }
        return treeUri.getPath();
    }
}