package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ClockTool extends BaseToolPlugin {

    private static final int REQ_IMAGE = 7301;
    private static final int REQ_FONT = 7302;
    private static final int TAB_STOPWATCH = 1;
    private static final int TAB_TIMER = 2;
    private static final int TAB_CLOCK = 3;

    private Context host;
    private ClockView clockView;
    private LinearLayout chipsRow;
    private View chipsWrap;
    private View fsBtn;
    private View settingsBtn;
    private View exitChip;
    private FrameLayout clockHolder;
    private PagedShell shell;
    private int currentTab;

    private String clockMode = "digital";
    private int clockBg;
    private String clockImage = "";
    private String clockFontPath = "";
    private String clockFontStyle = "regular";
    private String selectedProfile;

    private int digitalTimeColor = Color.WHITE;
    private int digitalDateColor = Color.WHITE;
    private boolean showDate = true;
    private int timeFormat;
    private int dateFormat;
    private int analogFaceColor = Color.argb(220, 255, 255, 255);
    private int analogHourColor = Color.parseColor("#212121");
    private int analogMinuteColor = Color.parseColor("#212121");
    private int analogSecondColor = Color.parseColor("#E53935");

    private ToolPlugin stopwatch;
    private ToolPlugin timer;

    public ClockTool() {
        super("clock", "Clock", "Stopwatch, timer and customizable clock", ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        bottomNav = new BottomNavigationView(context);
        Menu menu = bottomNav.getMenu();
        navItem(context, menu, TAB_STOPWATCH, "Stopwatch", new String[]{"schedule_24px"}, android.R.drawable.ic_menu_today);
        navItem(context, menu, TAB_TIMER, "Timer", new String[]{"baseline_timer_24", "schedule_24px"}, android.R.drawable.ic_menu_recent_history);
        navItem(context, menu, TAB_CLOCK, "Clock", new String[]{"baseline_access_time_24", "schedule_24px"}, android.R.drawable.ic_menu_today);
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);

        List<PagedShell.Page> pages = new ArrayList<>();
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Stopwatch";
            }

            @Override
            public View build(Context ctx) {
                stopwatch = new StopwatchTool();
                View v = stopwatch.createView(ctx, null);
                ScrollView sc = new ScrollView(ctx);
                sc.addView(v, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return sc;
            }

            @Override
            public void destroy() {
                try {
                    if (stopwatch != null) stopwatch.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Timer";
            }

            @Override
            public View build(Context ctx) {
                timer = new TimerTool();
                View v = timer.createView(ctx, null);
                ScrollView sc = new ScrollView(ctx);
                sc.addView(v, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return sc;
            }

            @Override
            public void destroy() {
                try {
                    if (timer != null) timer.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Clock";
            }

            @Override
            public View build(Context ctx) {
                View v = buildClockPanel();
                v.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return v;
            }
        });
        shell = new PagedShell(context, pages, null, bottomNav, currentTab);
        bottomNav.setSelectedItemId(currentTab == 1 ? TAB_TIMER : currentTab == 2 ? TAB_CLOCK : TAB_STOPWATCH);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            shell.select(id == TAB_TIMER ? 1 : id == TAB_CLOCK ? 2 : 0);
            return true;
        });
        shell.onSelect(position -> {
            currentTab = position;
            int id = position == 1 ? TAB_TIMER : position == 2 ? TAB_CLOCK : TAB_STOPWATCH;
            if (bottomNav.getSelectedItemId() != id) bottomNav.setSelectedItemId(id);
        });
        return shell.view();
    }

    @Override
    public void onDestroy() {
        try {
            if (shell != null) shell.destroy();
        } catch (Exception ignored) {
        }
        shell = null;
    }

    private static void navItem(Context context, Menu menu, int id, String title,
                                String[] candidates, int fallback) {
        MenuItem item = menu.add(0, id, id, title);
        int res = 0;
        try {
            for (String name : candidates) {
                res = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (res != 0) break;
            }
        } catch (Exception ignored) {
        }
        try {
            item.setIcon(ContextCompat.getDrawable(context, res != 0 ? res : fallback));
        } catch (Exception ignored) {
        }
    }

    private BottomNavigationView bottomNav;

    private View buildClockPanel() {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);

        HorizontalScrollView hsv = new HorizontalScrollView(host);
        hsv.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(host);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(chipsRow);
        chipsWrap = hsv;
        renderProfileChips();
        panel.addView(hsv, rowParams());

        clockHolder = new FrameLayout(host);
        clockView = new ClockView(host);
        applyClockToView();
        clockHolder.addView(clockView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        exitChip = new Chip(host);
        ((Chip) exitChip).setText("Exit fullscreen");
        exitChip.setVisibility(View.GONE);
        exitChip.setOnClickListener(v -> setFullscreen(false));
        FrameLayout.LayoutParams ecp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        int m = ToolViewFactory.dp(host, 12);
        ecp.setMargins(0, 0, m, m);
        clockHolder.addView(exitChip, ecp);
        panel.addView(clockHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout btnRow = new LinearLayout(host);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        settingsBtn = outlined("Settings");
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        fsBtn = outlined("Fullscreen");
        fsBtn.setOnClickListener(v -> setFullscreen(true));
        btnRow.addView(settingsBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(fsBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(btnRow, rowParams());

        startClockTick();
        return panel;
    }

    private LinearLayout.LayoutParams rowParams() {
        int m = ToolViewFactory.dp(host, 4);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(m, m, m, m);
        return p;
    }

    private MaterialButton outlined(String text) {
        MaterialButton b = new MaterialButton(host, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(host);
        t.setText(text);
        t.setTextSize(13);
        t.setAlpha(0.7f);
        return t;
    }

    private void applyClockToView() {
        if (clockView == null) return;
        clockView.setMode(clockMode);
        clockView.setBackground(clockBg, clockImage);
        clockView.setFont(clockFontPath, clockFontStyle);
        clockView.setDigital(digitalTimeColor, digitalDateColor, showDate, timeFormat, dateFormat);
        clockView.setAnalog(analogFaceColor, analogHourColor, analogMinuteColor, analogSecondColor);
    }

    private void setFullscreen(boolean on) {
        int v = on ? View.GONE : View.VISIBLE;
        if (chipsWrap != null) chipsWrap.setVisibility(v);
        if (fsBtn != null) fsBtn.setVisibility(v);
        if (settingsBtn != null) settingsBtn.setVisibility(v);
        if (exitChip != null) exitChip.setVisibility(on ? View.VISIBLE : View.GONE);
        if (bottomNav != null) bottomNav.setVisibility(on ? View.GONE : View.VISIBLE);
        setAppBarVisible(!on);
    }

    private void setAppBarVisible(boolean visible) {
        try {
            View root = ((android.app.Activity) host).getWindow().getDecorView();
            setToolbarVisibleRecursive(root, visible);
        } catch (Exception ignored) {
        }
    }

    private static void setToolbarVisibleRecursive(View v, boolean visible) {
        if (v instanceof MaterialToolbar) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                setToolbarVisibleRecursive(g.getChildAt(i), visible);
            }
        }
    }

    private void touchSettings() {
        selectedProfile = null;
        renderProfileChips();
    }

    private String[] getProfiles() {
        try {
            String raw = host.getSharedPreferences("tools", Context.MODE_PRIVATE)
                    .getString("clock_profiles", "[]");
            JSONArray arr = new JSONArray(raw);
            String[] out = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) out[i] = arr.getJSONObject(i).getString("name");
            return out;
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void renderProfileChips() {
        if (chipsRow == null) return;
        chipsRow.removeAllViews();
        for (String name : getProfiles()) {
            Chip chip = new Chip(host);
            chip.setText(name);
            boolean sel = name.equals(selectedProfile);
            chip.setChipBackgroundColor(ColorStateList.valueOf(sel
                    ? MaterialColors.getColor(host, com.google.android.material.R.attr.colorPrimary, 0xFF1B73E8)
                    : MaterialColors.getColor(host, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFEEEEEE)));
            chip.setTextColor(sel ? Color.WHITE : MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            chip.setOnClickListener(v -> applyProfile(name));
            chip.setOnLongClickListener(v -> {
                deleteProfile(name);
                if (name.equals(selectedProfile)) selectedProfile = null;
                renderProfileChips();
                return true;
            });
            chipsRow.addView(chip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Chip add = new Chip(host);
        add.setText("+ Profile");
        add.setOnClickListener(v -> showSettingsDialog());
        chipsRow.addView(add);
    }

    private void applyProfile(String name) {
        try {
            String raw = host.getSharedPreferences("tools", Context.MODE_PRIVATE)
                    .getString("clock_profiles", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getString("name").equals(name)) {
                    clockBg = o.optInt("color", clockBg);
                    clockImage = o.optString("image", "");
                    clockMode = o.optString("mode", clockMode);
                    clockFontPath = o.optString("fontPath", "");
                    clockFontStyle = o.optString("fontStyle", "regular");
                    digitalTimeColor = o.optInt("timeColor", digitalTimeColor);
                    digitalDateColor = o.optInt("dateColor", digitalDateColor);
                    showDate = o.optInt("showDate", showDate ? 1 : 0) != 0;
                    timeFormat = o.optInt("timeFormat", timeFormat);
                    dateFormat = o.optInt("dateFormat", dateFormat);
                    analogFaceColor = o.optInt("faceColor", analogFaceColor);
                    analogHourColor = o.optInt("hourColor", analogHourColor);
                    analogMinuteColor = o.optInt("minuteColor", analogMinuteColor);
                    analogSecondColor = o.optInt("secondColor", analogSecondColor);
                    applyClockToView();
                    selectedProfile = name;
                    renderProfileChips();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void deleteProfile(String name) {
        try {
            String raw = host.getSharedPreferences("tools", Context.MODE_PRIVATE)
                    .getString("clock_profiles", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.getString("name").equals(name)) out.put(o);
            }
            host.getSharedPreferences("tools", Context.MODE_PRIVATE).edit()
                    .putString("clock_profiles", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveProfile(String name) {
        try {
            String raw = host.getSharedPreferences("tools", Context.MODE_PRIVATE)
                    .getString("clock_profiles", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("color", clockBg);
            o.put("image", clockImage);
            o.put("mode", clockMode);
            o.put("fontPath", clockFontPath);
            o.put("fontStyle", clockFontStyle);
            o.put("timeColor", digitalTimeColor);
            o.put("dateColor", digitalDateColor);
            o.put("showDate", showDate ? 1 : 0);
            o.put("timeFormat", timeFormat);
            o.put("dateFormat", dateFormat);
            o.put("faceColor", analogFaceColor);
            o.put("hourColor", analogHourColor);
            o.put("minuteColor", analogMinuteColor);
            o.put("secondColor", analogSecondColor);
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("name").equals(name)) {
                    arr.put(i, o);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) arr.put(o);
            host.getSharedPreferences("tools", Context.MODE_PRIVATE).edit()
                    .putString("clock_profiles", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = ToolViewFactory.dp(host, 20);
        box.setPadding(p, p, p, p);

        box.addView(label("Clock style"));
        Segmented modeSeg = new Segmented(new String[]{"Analog", "Digital"});
        modeSeg.select("analog".equals(clockMode) ? 0 : 1);
        LinearLayout analogSection = new LinearLayout(host);
        analogSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout digitalSection = new LinearLayout(host);
        digitalSection.setOrientation(LinearLayout.VERTICAL);
        box.addView(modeSeg);

        Expandable analogWrap = new Expandable("Analog options", analogSection,
                "analog".equals(clockMode));
        box.addView(analogWrap);
        analogWrap.setVisibility("analog".equals(clockMode) ? View.VISIBLE : View.GONE);
        Expandable digitalWrap = new Expandable("Digital options", digitalSection,
                "digital".equals(clockMode));
        box.addView(digitalWrap);
        digitalWrap.setVisibility("digital".equals(clockMode) ? View.VISIBLE : View.GONE);

        modeSeg.listener = index -> {
            clockMode = index == 0 ? "analog" : "digital";
            applyClockToView();
            touchSettings();
            analogWrap.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            digitalWrap.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        };

        // ---------- Analog section ----------
        analogSection.addView(sectionLabel("Face"));
        MaterialButton faceColorBtn = outlined("Face color");
        faceColorBtn.setOnClickListener(v -> pickColor("Face color", c -> {
            analogFaceColor = c;
            applyClockToView();
            touchSettings();
        }));
        analogSection.addView(faceColorBtn);
        MaterialButton hourBtn = outlined("Hour hand color");
        hourBtn.setOnClickListener(v -> pickColor("Hour hand color", c -> {
            analogHourColor = c;
            applyClockToView();
            touchSettings();
        }));
        analogSection.addView(hourBtn);
        MaterialButton minuteBtn = outlined("Minute hand color");
        minuteBtn.setOnClickListener(v -> pickColor("Minute hand color", c -> {
            analogMinuteColor = c;
            applyClockToView();
            touchSettings();
        }));
        analogSection.addView(minuteBtn);
        MaterialButton secondBtn = outlined("Second hand color");
        secondBtn.setOnClickListener(v -> pickColor("Second hand color", c -> {
            analogSecondColor = c;
            applyClockToView();
            touchSettings();
        }));
        analogSection.addView(secondBtn);

        // ---------- Digital section ----------
        digitalSection.addView(sectionLabel("Time"));
        Segmented timeFormatSeg = new Segmented(new String[]{"24h", "12h", "No sec"});
        timeFormatSeg.select(timeFormat);
        timeFormatSeg.listener = i -> {
            timeFormat = i;
            applyClockToView();
            touchSettings();
        };
        digitalSection.addView(timeFormatSeg);

        com.google.android.material.materialswitch.MaterialSwitch dateSwitch =
                new com.google.android.material.materialswitch.MaterialSwitch(host);
        dateSwitch.setText("Show date");
        dateSwitch.setChecked(showDate);
        dateSwitch.setOnCheckedChangeListener((b, on) -> {
            showDate = on;
            applyClockToView();
            touchSettings();
        });
        digitalSection.addView(dateSwitch);

        digitalSection.addView(sectionLabel("Date format"));
        Segmented dateFormatSeg = new Segmented(new String[]{"Long", "Short", "ISO"});
        dateFormatSeg.select(dateFormat);
        dateFormatSeg.listener = i -> {
            dateFormat = i;
            applyClockToView();
            touchSettings();
        };
        digitalSection.addView(dateFormatSeg);

        MaterialButton timeColorBtn = outlined("Time color");
        timeColorBtn.setOnClickListener(v -> pickColor("Time color", c -> {
            digitalTimeColor = c;
            applyClockToView();
            touchSettings();
        }));
        digitalSection.addView(timeColorBtn);
        MaterialButton dateColorBtn = outlined("Date color");
        dateColorBtn.setOnClickListener(v -> pickColor("Date color", c -> {
            digitalDateColor = c;
            applyClockToView();
            touchSettings();
        }));
        digitalSection.addView(dateColorBtn);

        // ---------- Common: font + background ----------
        analogSection.addView(sectionLabel("Font style"));
        Segmented fontSeg = new Segmented(new String[]{"Regular", "Bold", "Thin"});
        fontSeg.select("bold".equals(clockFontStyle) ? 1 : "thin".equals(clockFontStyle) ? 2 : 0);
        fontSeg.listener = index -> {
            clockFontStyle = index == 1 ? "bold" : index == 2 ? "thin" : "regular";
            if (clockView != null) clockView.setFont(clockFontPath, clockFontStyle);
            touchSettings();
        };
        analogSection.addView(fontSeg);
        digitalSection.addView(sectionLabel("Font style"));
        Segmented fontSeg2 = new Segmented(new String[]{"Regular", "Bold", "Thin"});
        fontSeg2.select("bold".equals(clockFontStyle) ? 1 : "thin".equals(clockFontStyle) ? 2 : 0);
        fontSeg2.listener = index -> {
            clockFontStyle = index == 1 ? "bold" : index == 2 ? "thin" : "regular";
            if (clockView != null) clockView.setFont(clockFontPath, clockFontStyle);
            touchSettings();
        };
        digitalSection.addView(fontSeg2);

        MaterialButton pickFont = outlined("Pick font file (.ttf/.otf)");
        pickFont.setOnClickListener(v -> pickFont());
        analogSection.addView(pickFont);
        MaterialButton pickFont2 = outlined("Pick font file (.ttf/.otf)");
        pickFont2.setOnClickListener(v -> pickFont());
        digitalSection.addView(pickFont2);

        MaterialButton bgBtn = outlined("Background color");
        bgBtn.setOnClickListener(v -> pickColor("Background color", c -> {
            clockBg = c;
            clockImage = "";
            applyClockToView();
            touchSettings();
        }));
        analogSection.addView(bgBtn);
        MaterialButton bgBtn2 = outlined("Background color");
        bgBtn2.setOnClickListener(v -> pickColor("Background color", c -> {
            clockBg = c;
            clockImage = "";
            applyClockToView();
            touchSettings();
        }));
        digitalSection.addView(bgBtn2);

        MaterialButton pickImage = outlined("Pick image background");
        pickImage.setOnClickListener(v -> pickImage());
        analogSection.addView(pickImage);
        MaterialButton pickImage2 = outlined("Pick image background");
        pickImage2.setOnClickListener(v -> pickImage());
        digitalSection.addView(pickImage2);

        EditText name = new EditText(host);
        name.setHint("Profile name");
        box.addView(name);
        MaterialButton save = new MaterialButton(host);
        save.setText("Save as profile");
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                ToolViewFactory.toast(host, "Enter a profile name");
                return;
            }
            saveProfile(n);
            selectedProfile = n;
            renderProfileChips();
            ToolViewFactory.toast(host, "Saved");
        });
        box.addView(save);

        ScrollView sc = new ScrollView(host);
        sc.addView(box);
        new MaterialAlertDialogBuilder(host)
                .setTitle("Clock settings")
                .setView(sc)
                .setPositiveButton("Close", null)
                .show();
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(host);
        t.setText(text);
        t.setTextSize(12);
        t.setAlpha(0.6f);
        t.setPadding(0, ToolViewFactory.dp(host, 10), 0, 0);
        return t;
    }

    private void pickColor(String title, java.util.function.Consumer<Integer> setter) {
        io.github.abdurazaaqmohammed.ui.views.ColorWheelView wheel =
                new io.github.abdurazaaqmohammed.ui.views.ColorWheelView(host);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = ToolViewFactory.dp(host, 16);
        root.setPadding(pad, pad, pad, 0);
        root.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(host, 300)));
        LinearLayout previewRow = new LinearLayout(host);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        View previewSwatch = new View(host);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                ToolViewFactory.dp(host, 48), ToolViewFactory.dp(host, 48));
        swatchParams.rightMargin = ToolViewFactory.dp(host, 8);
        previewRow.addView(previewSwatch, swatchParams);
        EditText hexInput = new EditText(host);
        hexInput.setHint("#RRGGBB");
        hexInput.setSingleLine(true);
        previewRow.addView(hexInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(previewRow);
        SeekBar alphaBar = new SeekBar(host);
        alphaBar.setMax(255);
        alphaBar.setProgress(255);
        root.addView(alphaBar);
        LinearLayout presetRow = new LinearLayout(host);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] presets = {0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4,
                0xFF4CAF50, 0xFFFFEB3B, 0xFFFF9800, 0xFFFFC107, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000};
        for (int color : presets) {
            TextView swatch = new TextView(host);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ToolViewFactory.dp(host, 32), 1f);
            int margin = ToolViewFactory.dp(host, 2);
            params.setMargins(margin, margin, margin, margin);
            final int presetColor = color;
            swatch.setOnClickListener(v -> wheel.setColor(presetColor));
            presetRow.addView(swatch, params);
        }
        root.addView(presetRow);

        final boolean[] syncing = new boolean[1];
        Runnable syncFromWheel = () -> {
            if (syncing[0]) return;
            syncing[0] = true;
            try {
                int color = wheel.getColor(alphaBar.getProgress());
                previewSwatch.setBackgroundColor(color);
                String hex = String.format(alphaBar.getProgress() == 255 ? "#%06X" : "#%08X",
                        alphaBar.getProgress() == 255 ? (color & 0xFFFFFF) : color);
                String cur = hexInput.getText() == null ? "" : hexInput.getText().toString();
                if (!hex.equalsIgnoreCase(cur)) hexInput.setText(hex);
            } finally {
                syncing[0] = false;
            }
        };
        wheel.setListener(syncFromWheel);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                syncFromWheel.run();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0]) return;
                try {
                    String hex = s.toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    if (hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        syncing[0] = true;
                        int parsed = (int) Long.parseLong(hex.substring(1), 16)
                                | (hex.length() == 7 ? 0xFF000000 : 0);
                        wheel.setColor(parsed);
                        alphaBar.setProgress(255);
                        previewSwatch.setBackgroundColor(parsed);
                    }
                } catch (Exception ignored) {
                } finally {
                    syncing[0] = false;
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        new MaterialAlertDialogBuilder(host)
                .setTitle(title)
                .setView(root)
                .setPositiveButton("Apply", (d, w) -> {
                    try {
                        String hex = hexInput.getText().toString().trim();
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        int rgb;
                        if (hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                            rgb = (int) Long.parseLong(hex.substring(1), 16)
                                    | (hex.length() == 7 ? 0xFF000000 : 0);
                        } else {
                            rgb = wheel.getColor(alphaBar.getProgress());
                        }
                        setter.accept(rgb);
                    } catch (Exception e) {
                        setter.accept(wheel.getColor(alphaBar.getProgress()));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class Expandable extends LinearLayout {
        Expandable(String title, View content, boolean expanded) {
            super(host);
            setOrientation(VERTICAL);
            MaterialButton header = new MaterialButton(host, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            header.setText(title + (expanded ? "  \u25B2" : "  \u25BC"));
            header.setOnClickListener(v -> {
                boolean on = content.getVisibility() == View.VISIBLE;
                content.setVisibility(on ? View.GONE : View.VISIBLE);
                header.setText(title + (on ? "  \u25BC" : "  \u25B2"));
            });
            addView(header);
            content.setVisibility(expanded ? View.VISIBLE : View.GONE);
            addView(content);
        }
    }

    private void pickImage() {
        try {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            if (host instanceof android.app.Activity) {
                ((android.app.Activity) host).startActivityForResult(pick, REQ_IMAGE);
            }
        } catch (Exception e) {
            ToolViewFactory.toast(host, "No picker");
        }
    }

    private void pickFont() {
        try {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("*/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            if (host instanceof android.app.Activity) {
                ((android.app.Activity) host).startActivityForResult(pick, REQ_FONT);
            }
        } catch (Exception e) {
            ToolViewFactory.toast(host, "No picker");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            host.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_IMAGE) {
            clockImage = uri.toString();
            applyClockToView();
            touchSettings();
        } else if (requestCode == REQ_FONT) {
            try {
                InputStream in = host.getContentResolver().openInputStream(uri);
                java.io.File dir = host.getExternalFilesDir(null);
                if (dir == null) dir = host.getCacheDir();
                java.io.File out = new java.io.File(dir, "clock_font.ttf");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.close();
                in.close();
                clockFontPath = out.getAbsolutePath();
                if (clockView != null) clockView.setFont(clockFontPath, clockFontStyle);
                touchSettings();
            } catch (Exception e) {
                ToolViewFactory.toast(host, "Could not load font");
            }
        }
    }

    private void startClockTick() {
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (clockView != null) clockView.invalidate();
                h.postDelayed(this, 1000);
            }
        };
        h.post(tick);
    }

    private class Segmented extends LinearLayout {
        ViewConsumer listener;
        final MaterialButton[] buttons;

        Segmented(String[] labels) {
            super(host);
            setOrientation(HORIZONTAL);
            buttons = new MaterialButton[labels.length];
            for (int i = 0; i < labels.length; i++) {
                MaterialButton b = new MaterialButton(host, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                b.setText(labels[i]);
                buttons[i] = b;
                final int idx = i;
                b.setOnClickListener(v -> {
                    select(idx);
                    if (listener != null) listener.onPick(idx);
                });
                addView(b, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
        }

        void select(int i) {
            int primary = MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorPrimary, 0xFF1B73E8);
            int surface = MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFEEEEEE);
            int onSurface = MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
            for (int j = 0; j < buttons.length; j++) {
                buttons[j].setBackgroundTintList(ColorStateList.valueOf(j == i ? primary : surface));
                buttons[j].setTextColor(j == i ? Color.WHITE : onSurface);
            }
        }
    }

    private interface ViewConsumer {
        void onPick(int index);
    }

    private class ClockView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String mode = "digital";
        private int bg;
        private boolean hasBg;
        private Bitmap bgBitmap;
        private Typeface font;
        private int digitalTimeColor = Color.WHITE;
        private int digitalDateColor = Color.WHITE;
        private boolean showDate = true;
        private int timeFormat;
        private int dateFormat;
        private int analogFaceColor = Color.argb(220, 255, 255, 255);
        private int analogHourColor = Color.parseColor("#212121");
        private int analogMinuteColor = Color.parseColor("#212121");
        private int analogSecondColor = Color.parseColor("#E53935");

        ClockView(Context context) {
            super(context);
            setMinimumHeight(ToolViewFactory.dp(context, 240));
            setPadding(ToolViewFactory.dp(context, 8), ToolViewFactory.dp(context, 8),
                    ToolViewFactory.dp(context, 8), ToolViewFactory.dp(context, 8));
        }

        void setMode(String m) {
            mode = m;
            invalidate();
        }

        void setBackground(int color, String imageUri) {
            bg = color;
            hasBg = color != 0;
            bgBitmap = null;
            if (imageUri != null && !imageUri.isEmpty()) {
                try {
                    InputStream in = getContext().getContentResolver()
                            .openInputStream(Uri.parse(imageUri));
                    bgBitmap = BitmapFactory.decodeStream(in);
                    if (in != null) in.close();
                } catch (Exception ignored) {
                }
            }
            invalidate();
        }

        void setDigital(int timeColor, int dateColor, boolean showDate, int timeFormat, int dateFormat) {
            digitalTimeColor = timeColor;
            digitalDateColor = dateColor;
            this.showDate = showDate;
            this.timeFormat = timeFormat;
            this.dateFormat = dateFormat;
            invalidate();
        }

        void setAnalog(int faceColor, int hourColor, int minuteColor, int secondColor) {
            analogFaceColor = faceColor;
            analogHourColor = hourColor;
            analogMinuteColor = minuteColor;
            analogSecondColor = secondColor;
            invalidate();
        }

        void setFont(String path, String style) {
            try {
                if (path != null && !path.isEmpty()) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        int weight = "bold".equals(style) ? 700 : "thin".equals(style) ? 300 : 400;
                        font = new Typeface.Builder(path).setWeight(weight).build();
                    } else {
                        font = Typeface.createFromFile(path);
                    }
                } else if ("bold".equals(style)) {
                    font = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
                } else if ("thin".equals(style)) {
                    font = Typeface.create("sans-serif-light", Typeface.NORMAL);
                } else {
                    font = Typeface.MONOSPACE;
                }
            } catch (Exception e) {
                font = Typeface.MONOSPACE;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Calendar now = Calendar.getInstance();
            int w = getWidth();
            int h = getHeight();
            int bgFill;
            if (hasBg) {
                bgFill = bg;
            } else {
                bgFill = MaterialColors.getColor(getContext(),
                        com.google.android.material.R.attr.colorSurface, 0xFF1F1F1F);
            }
            if (bgBitmap != null) {
                canvas.drawBitmap(Bitmap.createScaledBitmap(bgBitmap, w, h, true), 0, 0, paint);
            } else {
                canvas.drawColor(bgFill);
            }
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - ToolViewFactory.dp(getContext(), 16);
            if ("analog".equals(mode)) {
                float ra = r * 0.82f;
                paint.setColor(analogFaceColor);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, ra, paint);
                paint.setColor(Color.parseColor("#212121"));
                paint.setStrokeWidth(ToolViewFactory.dp(getContext(), 3));
                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians(i * 30);
                    float x1 = cx + (float) Math.sin(angle) * (ra * 0.85f);
                    float y1 = cy - (float) Math.cos(angle) * (ra * 0.85f);
                    float x2 = cx + (float) Math.sin(angle) * (ra * 0.95f);
                    float y2 = cy - (float) Math.cos(angle) * (ra * 0.95f);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
                float hour = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f;
                float minute = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
                float second = now.get(Calendar.SECOND);
                drawHand(canvas, cx, cy, (hour / 12f) * 360f, ra * 0.5f, 6, analogHourColor);
                drawHand(canvas, cx, cy, (minute / 60f) * 360f, ra * 0.72f, 4, analogMinuteColor);
                drawHand(canvas, cx, cy, (second / 60f) * 360f, ra * 0.8f, 2, analogSecondColor);
                paint.setColor(analogHourColor);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, ToolViewFactory.dp(getContext(), 6), paint);
            } else {
                if (font != null) paint.setTypeface(font);
                String timeText;
                switch (timeFormat) {
                    case 1: {
                        int h12 = now.get(Calendar.HOUR);
                        if (h12 == 0) h12 = 12;
                        timeText = String.format(Locale.US, "%d:%02d %s", h12,
                                now.get(Calendar.MINUTE), now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
                        break;
                    }
                    case 2:
                        timeText = String.format(Locale.US, "%02d:%02d",
                                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
                        break;
                    default:
                        timeText = String.format(Locale.US, "%02d:%02d:%02d",
                                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND));
                }
                float timeSize = fitTextSize(paint, timeText, w * 0.86f, h * 0.4f);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(timeSize);
                paint.setColor(digitalTimeColor);
                if (showDate) {
                    String dateText;
                    switch (dateFormat) {
                        case 1:
                            dateText = new java.text.SimpleDateFormat("d MMM yyyy", Locale.US).format(now.getTime());
                            break;
                        case 2:
                            dateText = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.getTime());
                            break;
                        default:
                            dateText = new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(now.getTime());
                    }
                    float dateSize = timeSize * 0.34f;
                    paint.setTextSize(dateSize);
                    float dateW = paint.measureText(dateText);
                    if (dateW > w * 0.9f) {
                        dateSize = dateSize * w * 0.9f / dateW;
                        paint.setTextSize(dateSize);
                    }
                    paint.setColor(digitalTimeColor);
                    paint.setTextSize(timeSize);
                    float yOffset = timeSize * 0.5f + dateSize;
                    paint.setShadowLayer(8, 0, 2, Color.BLACK);
                    canvas.drawText(timeText, cx, cy, paint);
                    paint.setShadowLayer(0, 0, 0, Color.BLACK);
                    paint.setTextSize(dateSize);
                    paint.setColor(digitalDateColor);
                    canvas.drawText(dateText, cx, cy + yOffset, paint);
                } else {
                    paint.setShadowLayer(8, 0, 2, Color.BLACK);
                    canvas.drawText(timeText, cx, cy, paint);
                    paint.setShadowLayer(0, 0, 0, Color.BLACK);
                }
            }
        }

        private float fitTextSize(Paint p, String text, float maxW, float maxH) {
            Paint m = new Paint(p);
            m.setTextSize(100f);
            float w100 = m.measureText(text);
            if (w100 <= 0) return 100f;
            float s = 100f * maxW / w100;
            return Math.max(8f, Math.min(s, maxH));
        }

        private void drawHand(Canvas canvas, float cx, float cy, float angleDeg,
                              float length, int widthDp, int color) {
            double angle = Math.toRadians(angleDeg);
            float x = cx + (float) Math.sin(angle) * length;
            float y = cy - (float) Math.cos(angle) * length;
            paint.setColor(color);
            paint.setStrokeWidth(ToolViewFactory.dp(getContext(), widthDp));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx, cy, x, y, paint);
        }
    }
}

