package io.github.abdurazaaqmohammed.plugins.tools.common;

import android.content.ClipData;
import android.content.ClipboardManager;
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

import com.google.android.material.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

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
                R.attr.colorOnSurface, 0xFF000000));
        box.addView(title);
    }

    public static TextView makeOutput(LinearLayout box) {
        Context context = box.getContext();
        TextView output = new TextView(context);
        output.setTextSize(16);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        output.setBackgroundColor(MaterialColors.getColor(context,
                R.attr.colorSurfaceContainerHigh, Color.parseColor("#14000000")));
        output.setTextColor(MaterialColors.getColor(context,
                R.attr.colorOnSurface, Color.BLACK));
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

    public static EditText makeDateField(LinearLayout box, String hint) {
        EditText input = makeInput(box, hint, InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setCursorVisible(false);
        input.setClickable(true);
        input.setOnClickListener(v -> {
            try {
                MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTitleText(hint).build();
                picker.addOnPositiveButtonClickListener(selection -> {
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    cal.setTimeInMillis(selection == null ? 0 : selection);
                    input.setText(String.format(Locale.US, "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));
                });
                androidx.fragment.app.FragmentActivity fa = (androidx.fragment.app.FragmentActivity) box.getContext();
                picker.show(fa.getSupportFragmentManager(), "date");
            } catch (Exception ignored) {
            }
        });
        return input;
    }

    public static EditText makeDateTimeField(LinearLayout box, String hint) {
        EditText input = makeInput(box, hint, InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setCursorVisible(false);
        input.setClickable(true);
        input.setOnClickListener(v -> {
            try {
                MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTitleText(hint).build();
                picker.addOnPositiveButtonClickListener(selection -> {
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    cal.setTimeInMillis(selection == null ? 0 : selection);
                    String date = String.format(Locale.US, "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
                    try {
                        MaterialTimePicker time = new MaterialTimePicker.Builder()
                                .setTimeFormat(TimeFormat.CLOCK_24H).setTitleText("Time").build();
                        time.addOnPositiveButtonClickListener(t2 -> input.setText(date + String.format(Locale.US, " %02d:%02d", time.getHour(), time.getMinute())));
                        androidx.fragment.app.FragmentActivity fa2 = (androidx.fragment.app.FragmentActivity) box.getContext();
                        time.show(fa2.getSupportFragmentManager(), "time");
                    } catch (Exception ignored) {
                        input.setText(date + " 09:00");
                    }
                });
                androidx.fragment.app.FragmentActivity fa = (androidx.fragment.app.FragmentActivity) box.getContext();
                picker.show(fa.getSupportFragmentManager(), "date");
            } catch (Exception ignored) {
            }
        });
        return input;
    }

    public static EditText makeTimeField(LinearLayout box, String hint) {
        EditText input = makeInput(box, hint, InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setCursorVisible(false);
        input.setClickable(true);
        input.setOnClickListener(v -> {
            try {
                MaterialTimePicker time = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H).setTitleText(hint).build();
                time.addOnPositiveButtonClickListener(t2 -> input.setText(String.format(Locale.US, "%02d:%02d", time.getHour(), time.getMinute())));
                androidx.fragment.app.FragmentActivity fa = (androidx.fragment.app.FragmentActivity) box.getContext();
                time.show(fa.getSupportFragmentManager(), "time");
            } catch (Exception ignored) {
            }
        });
        return input;
    }

    public static TextView addLabel(LinearLayout box, String text) {
        Context context = box.getContext();
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setAlpha(0.8f);
        t.setTextColor(MaterialColors.getColor(context,
                R.attr.colorOnSurfaceVariant, Color.GRAY));
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

    public static void copyText(Context context, String label, CharSequence value) {
        try {
            ClipboardManager cm =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            toast(context, "Copied");
        } catch (Exception e) {
            toast(context, "Copy failed");
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
