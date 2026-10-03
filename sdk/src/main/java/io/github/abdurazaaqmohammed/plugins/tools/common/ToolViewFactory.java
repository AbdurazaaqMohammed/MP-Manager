package io.github.abdurazaaqmohammed.plugins.tools.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.ui.UiFields;

/**
 * Shared view builders for ToolPlugins. Extracted from the private helpers
 * in ToolRunnerActivity so each tool no longer duplicates row/button code.
 * Context-only: no activity fields required.
 */
public final class ToolViewFactory {

    private ToolViewFactory() {
    }

    public static int dp(Context context, int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static LinearLayout container(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 4);
        box.setPadding(pad, pad, pad, pad);
        box.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    public static void addTitle(LinearLayout box, String text) {
        TextView title = new TextView(box.getContext());
        title.setText(text);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(MaterialColors.getColor(box.getContext(),
                com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        box.addView(title);
    }

    public static TextView makeOutput(LinearLayout box) {
        Context context = box.getContext();
        TextView output = new TextView(context);
        output.setTextSize(16);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        output.setBackgroundColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.parseColor("#14000000")));
        output.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        try {
            output.setTextIsSelectable(true);
        } catch (Exception ignored) {
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(context, 8), 0, dp(context, 4));
        box.addView(output, p);
        return output;
    }

    public static EditText makeInput(LinearLayout box, String hint, int inputType) {
        Context context = box.getContext();
        TextInputLayout layout = UiFields.box(context, hint);
        EditText input = UiFields.field(layout, inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        input.setSingleLine(false);
        input.setMinLines(1);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(context, 8));
        box.addView(layout, p);
        return input;
    }

    public static TextView addLabel(LinearLayout box, String text) {
        Context context = box.getContext();
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setAlpha(0.8f);
        t.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(context, 8), 0, dp(context, 4));
        box.addView(t, p);
        return t;
    }

    public static MaterialButton makeButton(LinearLayout box, String text) {
        Context context = box.getContext();
        MaterialButton b = new MaterialButton(context);
        b.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(context, 4), 0, dp(context, 4));
        box.addView(b, p);
        return b;
    }

    public static void toast(Context context, String msg) {
        // Plain Toast on purpose: packs cannot depend on host-only libraries.
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    public static void copyText(Context context, String label, String value) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, value));
            toast(context, context.getString(io.github.abdurazaaqmohammed.sdk.R.string.tvf_copied));
        } catch (Exception e) {
            toast(context, context.getString(io.github.abdurazaaqmohammed.sdk.R.string.tvf_copy_failed));
        }
    }

    public static LinearLayout makeRow(LinearLayout box) {
        LinearLayout row = new LinearLayout(box.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    public static EditText makeRowInput(LinearLayout row, String hint, int inputType, float weight, String def) {
        Context context = row.getContext();
        TextInputLayout layout = UiFields.box(context, hint);
        EditText e = UiFields.field(layout, inputType);
        if (def != null) e.setText(def);
        row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return e;
    }

    public static MaterialButton makeRowButton(LinearLayout row, String text, float weight) {
        MaterialButton btn = new MaterialButton(row.getContext());
        btn.setText(text);
        btn.setMinHeight(dp(row.getContext(), 56));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int m = dp(row.getContext(), 4);
        params.setMargins(m, m, m, m);
        row.addView(btn, params);
        return btn;
    }

    public static void vibrateTick(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(15);
            }
        } catch (Exception ignored) {
        }
    }
}
