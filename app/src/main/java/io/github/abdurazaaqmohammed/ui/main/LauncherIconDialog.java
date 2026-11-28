package io.github.abdurazaaqmohammed.ui.main;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.LauncherIcons.Choice;

/**
 * "App Icon" chooser for the Appearance settings (wired by SettingsController:
 * {@code LauncherIconDialog.show(activity)}). Lists the bundled launcher
 * variants declared as activity-aliases in AndroidManifest.xml plus a
 * "follow theme" option, and applies a selection immediately through
 * {@link LauncherIcons#setSelection}.
 *
 * <p>Plain dialog (no DialogFragment) on purpose: a single static
 * {@link #show(Activity)} entry point keeps the settings hookup one line, and
 * the choice persists instantly so losing the dialog to rotation is harmless.
 */
public final class LauncherIconDialog {

    private LauncherIconDialog() {}

    /** Builds and shows the chooser against the current active alias. */
    public static void show(Activity activity) {
        Choice[] choices = LauncherIcons.choices();
        String selection = LauncherIcons.selection(activity);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        int padHalf = dp(activity, 6);
        scroll.setPadding(pad, padHalf, pad, padHalf);
        scroll.addView(root,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(activity);
        note.setText(activity.getString(R.string.app_icon_note) + "\n\n"
                + activity.getString(R.string.app_icon_theme_note));
        note.setTextSize(12.5f);
        note.setTextColor(MaterialColors.getColor(note,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        note.setPadding(0, padHalf, 0, dp(activity, 10));
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RadioButton[] radios = new RadioButton[choices.length + 1];
        for (int i = 0; i < choices.length; i++) {
            final int index = i;
            final Choice choice = choices[i];
            radios[i] = new RadioButton(activity);
            radios[i].setChecked(choice.id.equals(selection));
            radios[i].setClickable(false); // the row handles taps
            radios[i].setFocusable(false);

            LinearLayout row = iconRow(activity, choice.iconRes, choice.labelRes,
                    radios[i], () -> {
                        if (LauncherIcons.setSelection(activity, choice.id)) {
                            checkOnly(radios, index);
                            Toast.makeText(activity, R.string.app_icon_applied,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.app_icon_apply_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            root.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // "Follow theme": the icon tracks the active theme pack's request
        // (default when the pack requests none or none is active).
        radios[choices.length] = new RadioButton(activity);
        radios[choices.length].setChecked(LauncherIcons.ID_FOLLOW_THEME.equals(selection));
        radios[choices.length].setClickable(false);
        radios[choices.length].setFocusable(false);
        LinearLayout themeRow = iconRow(activity, R.mipmap.ic_launcher,
                R.string.app_icon_follow_theme, radios[choices.length], () -> {
                    if (LauncherIcons.setSelection(activity, LauncherIcons.ID_FOLLOW_THEME)) {
                        checkOnly(radios, choices.length);
                        Toast.makeText(activity, R.string.app_icon_applied,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, R.string.app_icon_apply_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
        root.addView(themeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_icon_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void checkOnly(RadioButton[] radios, int checked) {
        for (int j = 0; j < radios.length; j++) radios[j].setChecked(j == checked);
    }

    private static LinearLayout iconRow(Activity activity, int iconRes, int labelRes,
                                        RadioButton radio, Runnable onPick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(attrSelectable(activity));

        ImageView preview = new ImageView(activity);
        preview.setImageResource(iconRes);
        int side = dp(activity, 44);

        TextView label = new TextView(activity);
        label.setText(labelRes);
        label.setTextSize(16f);
        label.setTextColor(MaterialColors.getColor(label,
                com.google.android.material.R.attr.colorOnSurface));

        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(side, side);
        previewLp.setMargins(0, dp(activity, 8), dp(activity, 16), dp(activity, 8));
        row.addView(preview, previewLp);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams radioLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        radioLp.setMarginEnd(dp(activity, 4));
        row.addView(radio, radioLp);
        row.setOnClickListener(v -> onPick.run());
        return row;
    }

    private static int attrSelectable(Activity activity) {
        android.util.TypedValue tv = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId != 0 ? tv.resourceId : 0;
    }

    private static int dp(Activity activity, int dp) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
                activity.getResources().getDisplayMetrics()));
    }
}
