package io.github.abdurazaaqmohammed.arsc;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.model.ResourceEntry;

import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;

public final class ArscQuerier {

    private static View floatButton;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable snapTask;

    private ArscQuerier() {
    }

    private static boolean enabled(ArscEditorPlusActivity activity) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("arsc_querier_enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static void toggleFloat(ArscEditorPlusActivity activity) {
        if (floatButton != null && floatButton.getVisibility() == View.VISIBLE) {
            hideFloat(activity);
            return;
        }
        if (!enabled(activity)) {
            try {
                PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean("arsc_querier_enabled", true).apply();
            } catch (Exception ignored) {
            }
        }
        showFloat(activity);
    }

    public static void showFloat(ArscEditorPlusActivity activity) {
        if (!enabled(activity)) return;
        hideFloat(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        ImageButton button = new ImageButton(activity);
        button.setImageResource(R.drawable.baseline_search_24);
        button.setBackgroundResource(R.drawable.dialog_rounded_bg);
        button.setColorFilter(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorPrimary, 0xFF000000));
        int size = (int) (56 * density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.rightMargin = (int) (16 * density);
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(button, params);
        floatButton = button;
        final float[] down = new float[2];
        final int[] orig = new int[2];
        final boolean[] moved = new boolean[1];
        button.setOnTouchListener((v, event) -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                orig[0] = lp.leftMargin;
                orig[1] = lp.topMargin;
                moved[0] = false;
                cancelSnap();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int dx = (int) (event.getRawX() - down[0]);
                int dy = (int) (event.getRawY() - down[1]);
                if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved[0] = true;
                lp.gravity = Gravity.START | Gravity.TOP;
                lp.leftMargin = Math.max(0, orig[0] + dx);
                lp.topMargin = Math.max(0, orig[1] + dy);
                v.setLayoutParams(lp);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moved[0]) {
                    v.performClick();
                    showDialog(activity);
                } else {
                    scheduleSnap(activity, v);
                }
                return true;
            }
            return false;
        });
        scheduleSnap(activity, button);
    }

    private static void cancelSnap() {
        if (snapTask != null) {
            handler.removeCallbacks(snapTask);
            snapTask = null;
        }
    }

    private static void scheduleSnap(ArscEditorPlusActivity activity, View button) {
        cancelSnap();
        boolean snap;
        try {
            snap = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("arsc_querier_snap", true);
        } catch (Exception e) {
            snap = true;
        }
        if (!snap) return;
        snapTask = () -> {
            try {
                ViewGroup root = activity.findViewById(android.R.id.content);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
                lp.gravity = Gravity.START | Gravity.TOP;
                int centerX = lp.leftMargin + button.getWidth() / 2;
                lp.leftMargin = centerX < root.getWidth() / 2 ? 0 : Math.max(0, root.getWidth() - button.getWidth());
                button.setLayoutParams(lp);
            } catch (Exception ignored) {
            }
            snapTask = null;
        };
        handler.postDelayed(snapTask, 3000);
    }

    public static void hideFloat(ArscEditorPlusActivity activity) {
        cancelSnap();
        try {
            if (floatButton != null) {
                ViewGroup parent = (ViewGroup) floatButton.getParent();
                if (parent != null) parent.removeView(floatButton);
            }
        } catch (Exception ignored) {
        }
        floatButton = null;
    }

    public static void showDialog(ArscEditorPlusActivity activity) {
        if (activity.data == null) return;
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.querier_title));
        title.setTextSize(18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ImageButton settingsBtn = new ImageButton(activity);
        settingsBtn.setImageResource(R.drawable.baseline_settings_24);
        settingsBtn.setBackground(null);
        settingsBtn.setOnClickListener(v -> showSettings(activity));
        ImageButton powerBtn = new ImageButton(activity);
        powerBtn.setImageResource(R.drawable.stop_circle_24px);
        powerBtn.setBackground(null);
        powerBtn.setOnClickListener(v -> {
            try {
                PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean("arsc_querier_enabled", false).apply();
            } catch (Exception ignored) {
            }
            hideFloat(activity);
        });
        titleRow.addView(title, titleParams);
        titleRow.addView(settingsBtn);
        titleRow.addView(powerBtn);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout inputBox = UiFields.box(activity, "Query");
        EditText input = UiFields.field(inputBox, InputType.TYPE_CLASS_TEXT);
        root.addView(inputBox);
        TextView result = new TextView(activity);
        result.setTextSize(14);
        result.setTypeface(Typeface.MONOSPACE);
        result.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultParams.topMargin = pad / 2;
        root.addView(result, resultParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setCustomTitle(titleRow)
                .setView(root)
                .setNeutralButton(activity.getString(R.string.querier_search_string), null)
                .setNegativeButton(activity.getString(R.string.querier_close), null)
                .setPositiveButton(activity.getString(R.string.querier_query), null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String q = input.getText() == null ? "" : input.getText().toString();
                dialog.dismiss();
                activity.querierStringSearch(q);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String q = input.getText() == null ? "" : input.getText().toString();
                result.setText(runQuery(activity, q));
            });
        });
        dialog.show();
    }

    private static void showSettings(ArscEditorPlusActivity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, 0, pad, 0);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        CheckBox snapBox = new CheckBox(activity);
        snapBox.setText(activity.getString(R.string.querier_snap));
        snapBox.setChecked(prefs.getBoolean("arsc_querier_snap", true));
        CheckBox binBox = new CheckBox(activity);
        binBox.setText(activity.getString(R.string.querier_show_bin));
        binBox.setChecked(prefs.getBoolean("arsc_querier_bin", false));
        CheckBox octBox = new CheckBox(activity);
        octBox.setText(activity.getString(R.string.querier_show_oct));
        octBox.setChecked(prefs.getBoolean("arsc_querier_oct", false));
        root.addView(snapBox);
        root.addView(binBox);
        root.addView(octBox);
        TextView helpTitle = new TextView(activity);
        helpTitle.setText(activity.getString(R.string.querier_help_title));
        helpTitle.setTextSize(18);
        helpTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpParams.topMargin = pad;
        root.addView(helpTitle, helpParams);
        TextView help = new TextView(activity);
        help.setTextSize(13);
        help.setText(activity.getString(R.string.querier_help_body));
        root.addView(help);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.querier_title))
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        prefs.edit()
                                .putBoolean("arsc_querier_snap", snapBox.isChecked())
                                .putBoolean("arsc_querier_bin", binBox.isChecked())
                                .putBoolean("arsc_querier_oct", octBox.isChecked())
                                .apply();
                    } catch (Exception ignored) {
                    }
                }).show();
    }

    static String runQuery(ArscEditorPlusActivity activity, String input) {
        ArscData data = activity.data;
        if (data == null || data.table == null) return activity.getString(R.string.querier_no_file);
        String t = input == null ? "" : input.trim();
        if (t.isEmpty()) return activity.getString(R.string.querier_enter_query);
        if (t.startsWith("@") || t.startsWith("?")) return queryReference(activity, data, t);
        if (t.startsWith("#")) return queryColor(activity, t);
        return queryNumber(activity, data, t);
    }

    private static String queryReference(ArscEditorPlusActivity activity, ArscData data, String t) {
        String ref = t.substring(1);
        if (ref.startsWith("0x") || ref.startsWith("0X") || ref.matches("(?i)[0-9a-f]{1,8}")) {
            String hex = ref.startsWith("0x") || ref.startsWith("0X") ? ref.substring(2) : ref;
            try {
                int id = (int) (Long.parseLong(hex, 16) & 0xFFFFFFFFL);
                ResourceEntry found = data.table.getResource(id);
                if (found != null) return resourceInfo(data, found);
                return activity.getString(R.string.querier_no_res_id, hex.toUpperCase(Locale.US));
            } catch (Exception e) {
                return activity.getString(R.string.querier_bad_id, ref);
            }
        }
        int slash = ref.indexOf('/');
        if (slash <= 0) return activity.getString(R.string.querier_use_format);
        String type = ref.substring(0, slash);
        String name = ref.substring(slash + 1);
        if (type.startsWith("?")) type = type.substring(1);
        if (type.contains(":")) type = type.substring(type.indexOf(':') + 1);
        if (type.equals("attr") && t.startsWith("?")) {
            type = "attr";
        }
        for (PackageBlock pkg : data.table.listPackages()) {
            try {
                int id = data.table.resolveResourceId(pkg.getName(), type, name);
                if (id != 0) {
                    ResourceEntry found = data.table.getResource(id);
                    if (found != null) return resourceInfo(data, found);
                }
            } catch (Exception ignored) {
            }
        }
        return activity.getString(R.string.querier_not_found, ref);
    }

    static String resourceInfo(ArscData data, ResourceEntry re) {
        return String.format(Locale.US, "0x%08X", re.getResourceId()) + '\n' +
                re.getPackageName() + '/' + re.getType() + '/' + re.getName() + '\n' +
                data.entryDisplay(re);
    }

    private static String queryColor(ArscEditorPlusActivity activity, String t) {
        try {
            int argb = ArscData.parseColor(t);
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            return "A=" + a + " R=" + r + " G=" + g + " B=" + b + "\n"
                    + String.format(Locale.US, "#%08X", argb) + "\nint: " + argb;
        } catch (Exception e) {
            return activity.getString(R.string.querier_bad_color);
        }
    }

    private static String queryNumber(ArscEditorPlusActivity activity, ArscData data, String t) {
        String clean = t.replace("_", "").replace(" ", "");
        boolean negative = clean.startsWith("-");
        String body = negative ? clean.substring(1) : clean;
        String format;
        long value;
        try {
            if (body.startsWith("0b") || body.startsWith("0B")) {
                format = "binary";
                value = Long.parseLong(body.substring(2), 2);
            } else if (body.startsWith("0x") || body.startsWith("0X")) {
                format = "hex";
                value = Long.parseLong(body.substring(2), 16);
            } else if (body.length() > 1 && body.startsWith("0") && body.matches("[0-7]+")) {
                format = "octal";
                value = Long.parseLong(body, 8);
            } else if (body.matches("[0-9]+")) {
                format = "decimal";
                value = Long.parseLong(body, 10);
            } else if (body.matches("(?i)[0-9a-f]+")) {
                format = "hex";
                value = Long.parseLong(body, 16);
            } else {
                return activity.getString(R.string.querier_not_number);
            }
        } catch (Exception e) {
            return activity.getString(R.string.querier_out_of_range);
        }
        if (negative) {
            if (!format.equals("decimal")) return activity.getString(R.string.querier_only_decimal_negative);
            value = -value;
        }
        int intValue = (int) (value & 0xFFFFFFFFL);
        boolean showBin = false;
        boolean showOct = false;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            showBin = prefs.getBoolean("arsc_querier_bin", false);
            showOct = prefs.getBoolean("arsc_querier_oct", false);
        } catch (Exception ignored) {
        }
        StringBuilder sb = new StringBuilder();
        sb.append(format).append(": ").append(value).append('\n');
        sb.append("dec: ").append(intValue).append('\n');
        sb.append(String.format(Locale.US, "hex: 0x%08X", intValue)).append('\n');
        if (showOct) sb.append("oct: 0").append(Integer.toOctalString(intValue)).append('\n');
        if (showBin) sb.append("bin: 0b").append(Integer.toBinaryString(intValue)).append('\n');
        if (value >= 0x7F000000L && value <= 0x7FFFFFFFL) {
            try {
                ResourceEntry found = data.table.getResource(intValue);
                if (found != null) {
                    sb.append("---\n").append(resourceInfo(data, found));
                }
            } catch (Exception ignored) {
            }
        }
        return sb.toString().trim();
    }
}
