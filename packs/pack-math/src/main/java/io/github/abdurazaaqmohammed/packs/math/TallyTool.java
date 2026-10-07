package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

public class TallyTool extends BaseToolPlugin {

    private Context host;
    private int count;
    private int lastCount = -1;
    private TextView countView;
    private MaterialButton plusBtn;
    private MaterialButton plus10Btn;
    private MaterialButton minusBtn;
    private MaterialButton resetBtn;
    private MaterialCardView tapCard;
    private SharedPreferences prefs;
    private ToneGenerator tone;

    public TallyTool() {
        super("tally", "Tally Counter", "Tap counter with presets and haptics", ToolCategories.MATH);
    }

    @Override
    public boolean fillViewport() {
        return true;
    }

    private SharedPreferences prefs() {
        if (prefs == null) prefs = host.getSharedPreferences("tools", Context.MODE_PRIVATE);
        return prefs;
    }

    private boolean opt(String key, boolean def) {
        return prefs().getBoolean("tally_" + key, def);
    }

    private void setOpt(String key, boolean value) {
        prefs().edit().putBoolean("tally_" + key, value).apply();
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        count = prefs().getInt("tally_count", 0);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ToolViewFactory.dp(context, 8), ToolViewFactory.dp(context, 8),
                ToolViewFactory.dp(context, 8), ToolViewFactory.dp(context, 8));

        EditText nameInput = new EditText(context);
        nameInput.setHint(PackRes.str("math", R.string.s_counter_name, "Counter name"));
        nameInput.setText(prefs().getString("tally_name", ""));
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextSize(16);
        nameInput.addTextChangedListener(new SimpleWatcher(() ->
                prefs().edit().putString("tally_name", nameInput.getText().toString()).apply()));
        root.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tapCard = new MaterialCardView(context);
        tapCard.setRadius(ToolViewFactory.dp(context, 20));
        tapCard.setCardElevation(ToolViewFactory.dp(context, 2));
        tapCard.setClickable(true);
        tapCard.setFocusable(true);
        tapCard.setRippleColor(ColorStateList.valueOf(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFDDDDDD)));
        FrameLayout cardContent = new FrameLayout(context);
        countView = new TextView(context);
        countView.setGravity(Gravity.CENTER);
        countView.setTextSize(64);
        countView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        countView.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        cardContent.addView(countView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tapCard.addView(cardContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tapCard.setOnClickListener(v -> {
            if (opt("tapanywhere", true)) bump(1);
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        tapCard.setMinimumHeight((int) (screenHeight * 0.66));
        cp.setMargins(0, ToolViewFactory.dp(context, 12), 0, ToolViewFactory.dp(context, 12));
        root.addView(tapCard, cp);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        plusBtn = bigBtn(context, "+1", v -> bump(1));
        plus10Btn = bigBtn(context, "+10", v -> bump(10));
        minusBtn = bigBtn(context, "−1", v -> bump(-1));
        row1.addView(plusBtn, btnParams());
        row1.addView(plus10Btn, btnParams());
        row1.addView(minusBtn, btnParams());
        root.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        resetBtn = smallBtn(context, "Reset", v -> confirmReset());
        MaterialButton undoBtn = smallBtn(context, "Undo", v -> undo());
        MaterialButton copyBtn = smallBtn(context, "Copy", v -> copy());
        row2.addView(resetBtn, btnParams());
        row2.addView(undoBtn, btnParams());
        row2.addView(copyBtn, btnParams());
        root.addView(row2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView optsScroll = new ScrollView(context);
        LinearLayout opts = new LinearLayout(context);
        opts.setOrientation(LinearLayout.VERTICAL);
        opts.setPadding(0, ToolViewFactory.dp(context, 8), 0, 0);
        opts.addView(switchRow(context, "Tap anywhere on screen", "tapanywhere", true, null));
        opts.addView(switchRow(context, "+1 button", "show1", true, this::applyVisibility));
        opts.addView(switchRow(context, "+10 button", "show10", false, this::applyVisibility));
        opts.addView(switchRow(context, "−1 button", "showminus", true, this::applyVisibility));
        opts.addView(switchRow(context, "Reset button", "showreset", true, this::applyVisibility));
        opts.addView(switchRow(context, "Vibrate on count", "vibrate", true, null));
        opts.addView(switchRow(context, "Sound on count", "sound", false, null));
        opts.addView(switchRow(context, "Keep screen on", "keepscreen", false, this::applyKeepScreen));
        optsScroll.addView(opts);
        root.addView(optsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        applyVisibility();
        applyKeepScreen();
        render();
        return root;
    }

    private static class SimpleWatcher implements TextWatcher {

        private final Runnable r;

        SimpleWatcher(Runnable r) {
            this.r = r;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            r.run();
        }
    }


    private LinearLayout.LayoutParams btnParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = ToolViewFactory.dp(host, 4);
        p.setMargins(m, m, m, m);
        return p;
    }

    private MaterialButton bigBtn(Context context, String text, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(context);
        b.setText(text);
        b.setTextSize(20);
        b.setMinHeight(ToolViewFactory.dp(context, 64));
        b.setOnClickListener(l);
        return b;
    }

    private MaterialButton smallBtn(Context context, String text, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private View switchRow(Context context, String label, String key, boolean def, Runnable effect) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextSize(15);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked(opt(key, def));
        sw.setOnCheckedChangeListener((button, on) -> {
            setOpt(key, on);
            if (effect != null) effect.run();
        });
        row.addView(sw);
        return row;
    }

    private void applyVisibility() {
        if (plusBtn == null) return;
        plusBtn.setVisibility(opt("show1", true) ? View.VISIBLE : View.GONE);
        plus10Btn.setVisibility(opt("show10", false) ? View.VISIBLE : View.GONE);
        minusBtn.setVisibility(opt("showminus", true) ? View.VISIBLE : View.GONE);
        resetBtn.setVisibility(opt("showreset", true) ? View.VISIBLE : View.GONE);
    }

    private void applyKeepScreen() {
        try {
            if (host instanceof Activity) {
                Activity a = (Activity) host;
                if (opt("keepscreen", false)) {
                    a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void bump(int delta) {
        int next = Math.max(0, count + delta);
        if (next == count) {
            signal();
            return;
        }
        lastCount = count;
        count = next;
        prefs().edit().putInt("tally_count", count).apply();
        render();
        signal();
    }

    private void undo() {
        if (lastCount < 0) {
            ToolViewFactory.toast(host, PackRes.str("math", R.string.s_nothing_to_undo, "Nothing to undo"));
            return;
        }
        count = lastCount;
        lastCount = -1;
        prefs().edit().putInt("tally_count", count).apply();
        render();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(host)
                .setTitle(PackRes.str("math", R.string.s_reset_count, "Reset count?"))
                .setMessage(PackRes.str("math", R.string.s_this_will_set_the_counter_back_to_0, "This will set the counter back to 0."))
                .setPositiveButton(PackRes.str("math", R.string.s_reset, "Reset"), (d, w) -> {
                    lastCount = count;
                    count = 0;
                    prefs().edit().putInt("tally_count", 0).apply();
                    render();
                })
                .setNegativeButton(PackRes.str("math", R.string.s_cancel, "Cancel"), null)
                .show();
    }

    private void copy() {
        String name = prefs().getString("tally_name", "");
        ToolViewFactory.copyText(host, PackRes.str("math", R.string.s_count, "count"),
                (name == null || name.trim().isEmpty() ? "Count" : name.trim()) + ": " + count);
    }

    private void signal() {
        if (opt("vibrate", true)) ToolViewFactory.vibrateTick(host);
        if (opt("sound", false)) {
            try {
                if (tone == null) {
                    tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
                }
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 40);
            } catch (Exception ignored) {
            }
        }
    }

    private void render() {
        if (countView != null) countView.setText(String.valueOf(count));
    }

    @Override
    public void onDestroy() {
        try {
            if (tone != null) {
                tone.release();
                tone = null;
            }
            applyKeepScreenClear();
        } catch (Exception ignored) {
        }
    }

    private void applyKeepScreenClear() {
        try {
            if (host instanceof Activity) {
                ((Activity) host).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception ignored) {
        }
    }
}
