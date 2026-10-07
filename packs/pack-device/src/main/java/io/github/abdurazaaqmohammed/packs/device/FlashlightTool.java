package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;

public class FlashlightTool extends BaseToolPlugin {

    private static final int TAB_TORCH = 1;
    private static final int TAB_SCREEN = 2;
    private static final int TAB_STROBE = 3;

    private static final String[] PATTERNS = {"Strobe", "SOS", "Beacon", "Morse text"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context host;
    private BottomNavigationView nav;
    private PagedShell shell;

    private CameraManager cameraManager;
    private String torchCameraId;
    private int torchMaxStrength = 1;

    private boolean torchOn;
    private MaterialButton torchPower;
    private SeekBar torchBrightBar;
    private TextView torchBrightLabel;
    private TextView torchInfo;
    private Runnable torchTimer;

    private int screenColor = Color.WHITE;
    private float screenBright = 1.0f;
    private Dialog screenDialog;
    private View screenPreview;
    private Runnable screenTimer;

    private boolean blinkRunning;
    private int[] timeline = new int[0];
    private int step;
    private int strobeHz = 4;
    private int strobeDuty = 50;
    private int patternIndex = 0;
    private String morseText = "SOS";
    private MaterialButton strobePower;
    private TextView strobeState;
    private LinearLayout morseRow;

    public FlashlightTool() {
        super("flashlight", "Flashlight", "Torch, screen light and strobe", ToolCategories.DEVICE);
    }

    @Override
    public boolean fillViewport() {
        return true;
    }

    private SharedPreferences prefs() {
        return host.getSharedPreferences("flashlight", Context.MODE_PRIVATE);
    }

    private void findTorchCamera() {
        torchCameraId = null;
        torchMaxStrength = 1;
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            cameraManager = (CameraManager) host.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) return;
            for (String id : cameraManager.getCameraIdList()) {
                try {
                    Boolean flash = cameraManager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) {
                        torchCameraId = id;
                        Integer facing = cameraManager.getCameraCharacteristics(id)
                                .get(CameraCharacteristics.LENS_FACING);
                        if (facing != null && facing == 0) break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        if (torchCameraId != null) {
            try {
                Object v = CameraManager.class.getMethod("getTorchStrengthLevel", String.class)
                        .invoke(cameraManager, torchCameraId);
                if (v instanceof Integer && (Integer) v > 1) torchMaxStrength = (Integer) v;
            } catch (Exception ignored) {
            }
        }
    }

    private boolean hasCameraPerm() {
        try {
            return ActivityCompat.checkSelfPermission(host, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void askCameraPerm() {
        try {
            ActivityCompat.requestPermissions((Activity) host,
                    new String[]{Manifest.permission.CAMERA}, 9001);
        } catch (Exception ignored) {
        }
        ToolViewFactory.toast(host, PackRes.str("device", R.string.s_camera_permission_needed_then_tap_again, "Camera permission needed, then tap again"));
    }

    private void setTorchSilent(boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && cameraManager != null && torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, on);
                torchOn = on;
            }
        } catch (Exception ignored) {
        }
    }

    private void setTorchStrength(int level) {
        try {
            CameraManager.class.getMethod("setTorchStrengthLevel", String.class, int.class)
                    .invoke(cameraManager, torchCameraId, level);
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        try {
            findTorchCamera();
        } catch (Throwable t) {
            try {
                Log.e("FlashlightTool", "findTorchCamera failed", t);
            } catch (Exception ignored) {
            }
            torchCameraId = null;
            torchMaxStrength = 1;
        }
        try {
            strobeHz = Math.max(1, Math.min(20, prefs().getInt("strobe_hz", 4)));
        } catch (Throwable ignored) {
            strobeHz = 4;
        }
        try {
            strobeDuty = Math.max(10, Math.min(90, prefs().getInt("strobe_duty", 50)));
        } catch (Throwable ignored) {
            strobeDuty = 50;
        }
        try {
            patternIndex = Math.max(0, Math.min(3, prefs().getInt("strobe_pattern", 0)));
        } catch (Throwable ignored) {
            patternIndex = 0;
        }
        try {
            screenColor = prefs().getInt("screen_color", Color.WHITE);
        } catch (Throwable ignored) {
            screenColor = Color.WHITE;
        }
        try {
            screenBright = prefs().getFloat("screen_bright", 1.0f);
        } catch (Throwable ignored) {
            screenBright = 1.0f;
        }
        try {
            morseText = prefs().getString("morse_text", "SOS");
        } catch (Throwable ignored) {
            morseText = "SOS";
        }
        if (morseText == null || morseText.isEmpty()) morseText = "SOS";
        nav = new BottomNavigationView(context);
        Menu menu = nav.getMenu();
        navItem(context, menu, TAB_TORCH, "Torch",
                new String[]{"torch_24px", "flashlight_24px", "colorize_24px"},
                android.R.drawable.ic_menu_info_details);
        navItem(context, menu, TAB_SCREEN, "Screen",
                new String[]{"fullscreen_24px", "screen_24px"},
                android.R.drawable.ic_menu_gallery);
        navItem(context, menu, TAB_STROBE, "Strobe",
                new String[]{"strobe_24px", "bolt_24px", "colorize_24px"},
                android.R.drawable.ic_menu_help);
        nav.setLabelVisibilityMode(
                NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setSelectedItemId(TAB_TORCH);
        List<PagedShell.Page> pages = new ArrayList<>();
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Torch";
            }

            @Override
            public View build(Context ctx) {
                try {
                    return wrap(buildTorchPage(ctx));
                } catch (Throwable t) {
                    try {
                        Log.e("FlashlightTool", "torch page failed", t);
                    } catch (Exception ignored) {
                    }
                    return errorPage(ctx, "Torch", t);
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Screen";
            }

            @Override
            public View build(Context ctx) {
                try {
                    return wrap(buildScreenPage(ctx));
                } catch (Throwable t) {
                    try {
                        Log.e("FlashlightTool", "screen page failed", t);
                    } catch (Exception ignored) {
                    }
                    return errorPage(ctx, "Screen", t);
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Strobe";
            }

            @Override
            public View build(Context ctx) {
                try {
                    return wrap(buildStrobePage(ctx));
                } catch (Throwable t) {
                    try {
                        Log.e("FlashlightTool", "strobe page failed", t);
                    } catch (Exception ignored) {
                    }
                    return errorPage(ctx, "Strobe", t);
                }
            }

            @Override
            public void hidden() {
                stopBlink();
            }
        });
        shell = new PagedShell(context, pages, null, nav, 0);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showTab(id == TAB_SCREEN ? 1 : id == TAB_STROBE ? 2 : 0);
            return true;
        });
        shell.onSelect(position -> {
            try {
                int id = position == 1 ? TAB_SCREEN : position == 2 ? TAB_STROBE : TAB_TORCH;
                if (nav.getSelectedItemId() != id) nav.setSelectedItemId(id);
            } catch (Exception ignored) {
            }
        });
        showTab(0);
        return shell.view();
    }

    private void showTab(int tab) {
        try {
            if (shell != null) shell.select(tab);
        } catch (Throwable t) {
            try {
                Log.e("FlashlightTool", "showTab failed", t);
            } catch (Exception ignored) {
            }
        }
    }

    private View wrap(View inner) {
        ScrollView sc = new ScrollView(host);
        sc.setFillViewport(true);
        sc.addView(inner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private static void navItem(Context context, Menu menu, int id, String title,
                                String[] candidates, int fallback) {
        MenuItem item = menu.add(0, id, id, title);
        int res = 0;
        try {
            for (String name : candidates) {
                res = context.getResources().getIdentifier(name, "drawable",
                        context.getPackageName());
                if (res != 0) break;
            }
        } catch (Exception ignored) {
        }
        try {
            item.setIcon(ContextCompat.getDrawable(context, res != 0 ? res : fallback));
        } catch (Exception ignored) {
        }
    }

    private View errorPage(Context context, String tab, Throwable t) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = ToolViewFactory.dp(context, 16);
        page.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(context);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setText(tab + " failed to load");
        page.addView(title);
        TextView body = new TextView(context);
        body.setTextSize(13);
        String msg;
        try {
            msg = t == null ? "unknown error" : String.valueOf(t);
        } catch (Exception e) {
            msg = "unknown error";
        }
        body.setText(msg);
        page.addView(body);
        return wrapRaw(page);
    }

    private View wrapRaw(View inner) {
        ScrollView sc = new ScrollView(host != null ? host : inner.getContext());
        sc.setFillViewport(true);
        sc.addView(inner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    // ---------- torch tab ----------

    private View buildTorchPage(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = ToolViewFactory.dp(context, 16);
        page.setPadding(pad, pad, pad, pad);
        ToolViewFactory.addLabel(page, PackRes.str("device", R.string.s_camera_flash_torch, "Camera flash torch"));
        torchPower = new MaterialButton(context);
        torchPower.setTextSize(20);
        torchPower.setAllCaps(false);
        torchPower.setCornerRadius(ToolViewFactory.dp(context, 56));
        torchPower.setMinHeight(ToolViewFactory.dp(context, 112));
        torchPower.setOnClickListener(v -> toggleTorch());
        page.addView(torchPower, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        paintTorchButton();
        torchInfo = new TextView(context);
        torchInfo.setTextSize(13);
        torchInfo.setAlpha(0.75f);
        page.addView(torchInfo);
        refreshTorchInfo();
        if (torchMaxStrength > 1) {
            torchBrightLabel = new TextView(context);
            torchBrightLabel.setTextSize(14);
            page.addView(torchBrightLabel);
            torchBrightBar = new SeekBar(context);
            torchBrightBar.setMax(torchMaxStrength - 1);
            int level = Math.max(1, Math.min(torchMaxStrength,
                    prefs().getInt("torch_level", torchMaxStrength)));
            torchBrightBar.setProgress(level - 1);
            updateTorchBrightLabel(level);
            torchBrightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    int lvl = progress + 1;
                    updateTorchBrightLabel(lvl);
                    try {
                        prefs().edit().putInt("torch_level", lvl).apply();
                    } catch (Exception ignored) {
                    }
                    if (torchOn && fromUser) setTorchStrength(lvl);
                }

                public void onStartTrackingTouch(SeekBar s) {
                }

                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            page.addView(torchBrightBar);
        }
        minutesRow(page, "Auto-off (minutes, 0 = off)",
                prefs().getInt("torch_timer", 0), v -> {
                    try {
                        prefs().edit().putInt("torch_timer", v).apply();
                    } catch (Exception ignored) {
                    }
                    scheduleTorchTimer();
                });
        return page;
    }

    private void updateTorchBrightLabel(int level) {
        if (torchBrightLabel != null) {
            torchBrightLabel.setText("Brightness " + level + " / " + torchMaxStrength);
        }
    }

    private void paintTorchButton() {
        if (torchPower == null) return;
        torchPower.setText(torchOn ? "ON — tap to turn off" : "OFF — tap to turn on");
        try {
            torchPower.setBackgroundTintList(ColorStateList.valueOf(
                    torchOn ? Color.parseColor("#0F9D58") : Color.parseColor("#5F6368")));
            torchPower.setTextColor(Color.WHITE);
        } catch (Exception ignored) {
        }
    }

    private void refreshTorchInfo() {
        if (torchInfo == null) return;
        if (torchCameraId == null) {
            torchInfo.setText(PackRes.str("device", R.string.s_no_flash_found_on_this_device, "No flash found on this device"));
        } else {
            torchInfo.setText("Flash: camera " + torchCameraId
                    + (torchMaxStrength > 1 ? "  •  " + torchMaxStrength + " brightness levels" : ""));
        }
    }

    private void toggleTorch() {
        if (torchOn) {
            setTorchSilent(false);
            cancelTorchTimer();
            paintTorchButton();
            setKeepScreen(false);
            return;
        }
        if (Build.VERSION.SDK_INT < 23 || torchCameraId == null) {
            ToolViewFactory.toast(host, PackRes.str("device", R.string.s_flash_not_available, "Flash not available"));
            return;
        }
        if (!hasCameraPerm()) {
            askCameraPerm();
            return;
        }
        stopBlink();
        setTorchSilent(true);
        if (torchMaxStrength > 1 && torchBrightBar != null) {
            setTorchStrength(torchBrightBar.getProgress() + 1);
        }
        paintTorchButton();
        setKeepScreen(true);
        scheduleTorchTimer();
    }

    private void scheduleTorchTimer() {
        cancelTorchTimer();
        int mins = 0;
        try {
            mins = prefs().getInt("torch_timer", 0);
        } catch (Exception ignored) {
        }
        if (!torchOn || mins <= 0) return;
        torchTimer = () -> {
            setTorchSilent(false);
            paintTorchButton();
            setKeepScreen(false);
        };
        handler.postDelayed(torchTimer, mins * 60000L);
    }

    private void cancelTorchTimer() {
        try {
            if (torchTimer != null) handler.removeCallbacks(torchTimer);
        } catch (Exception ignored) {
        }
        torchTimer = null;
    }

    private void setKeepScreen(boolean on) {
        try {
            if (!(host instanceof Activity)) return;
            Window w = ((Activity) host).getWindow();
            if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {
        }
    }

    // ---------- screen tab ----------

    private View buildScreenPage(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int spad = ToolViewFactory.dp(context, 16);
        page.setPadding(spad, spad, spad, spad);
        ToolViewFactory.addLabel(page, PackRes.str("device", R.string.s_whole_display_becomes_the_light, "Whole display becomes the light"));
        screenPreview = new View(context);
        screenPreview.setBackgroundColor(screenColor);
        screenPreview.setMinimumHeight(ToolViewFactory.dp(context, 72));
        page.addView(screenPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 72)));
        int[] colors = {Color.WHITE, 0xFFFFD9A0, 0xFFCCE6FF, 0xFFFF2222, 0xFFFFEB3B, 0xFF000000};
        LinearLayout swatches = new LinearLayout(context);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER);
        page.addView(swatches);
        for (int color : colors) {
            MaterialButton sw = new MaterialButton(context);
            sw.setText("");
            sw.setCornerRadius(ToolViewFactory.dp(context, 24));
            sw.setMinHeight(ToolViewFactory.dp(context, 48));
            sw.setMinimumWidth(ToolViewFactory.dp(context, 48));
            try {
                sw.setBackgroundTintList(
                        ColorStateList.valueOf(color));
            } catch (Exception ignored) {
                sw.setBackgroundColor(color);
            }
            final int picked = color;
            sw.setOnClickListener(v -> {
                screenColor = picked;
                try {
                    prefs().edit().putInt("screen_color", picked).apply();
                } catch (Exception ignored) {
                }
                if (screenPreview != null) screenPreview.setBackgroundColor(picked);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ToolViewFactory.dp(context, 48), ToolViewFactory.dp(context, 48));
            int m = ToolViewFactory.dp(context, 6);
            p.setMargins(m, m, m, m);
            swatches.addView(sw, p);
        }
        final TextView brightLabel = new TextView(context);
        brightLabel.setTextSize(14);
        page.addView(brightLabel);
        SeekBar brightBar = new SeekBar(context);
        brightBar.setMax(90);
        brightBar.setProgress(Math.round((screenBright - 0.1f) * 100));
        Runnable paintBright = () -> brightLabel.setText(
                "Brightness " + Math.round(screenBright * 100) + "%");
        paintBright.run();
        brightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                screenBright = 0.1f + progress / 100f;
                paintBright.run();
                try {
                    prefs().edit().putFloat("screen_bright", screenBright).apply();
                } catch (Exception ignored) {
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        page.addView(brightBar);
        minutesRow(page, "Auto-off (minutes, 0 = off)",
                prefs().getInt("screen_timer", 0), v -> {
                    try {
                        prefs().edit().putInt("screen_timer", v).apply();
                    } catch (Exception ignored) {
                    }
                });
        MaterialButton show = new MaterialButton(context);
        show.setText(PackRes.str("device", R.string.s_show_full_screen_light, "Show full-screen light"));
        show.setMinHeight(ToolViewFactory.dp(context, 64));
        show.setOnClickListener(v -> showScreenDialog());
        page.addView(show, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return page;
    }

    private void showScreenDialog() {
        dismissScreenDialog();
        final int color = screenColor;
        final float bright = screenBright;
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(color);
        root.setClickable(true);
        TextView pill = new TextView(host);
        pill.setText(PackRes.str("device", R.string.s_tap_anywhere_to_turn_off, "Tap anywhere to turn off"));
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(14);
        pill.setGravity(Gravity.CENTER);
        try {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ToolViewFactory.dp(host, 24));
            bg.setColor(Color.parseColor("#CC000000"));
            pill.setBackground(bg);
        } catch (Exception ignored) {
        }
        int pad = ToolViewFactory.dp(host, 16);
        pill.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.bottomMargin = ToolViewFactory.dp(host, 64);
        root.addView(pill, pp);
        Dialog d = new Dialog(host, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.setOnDismissListener(di -> {
            if (screenDialog == d) screenDialog = null;
            cancelScreenTimer();
        });
        root.setOnClickListener(v -> {
            try {
                d.dismiss();
            } catch (Exception ignored) {
            }
        });
        screenDialog = d;
        d.show();
        try {
            Window w = d.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(color));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = Math.max(0.1f, Math.min(1f, bright));
            w.setAttributes(lp);
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    WindowInsetsController ic = w.getInsetsController();
                    if (ic != null) {
                        ic.hide(WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars());
                        ic.setSystemBarsBehavior(WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                } catch (Exception ignored) {
                }
            } else {
                w.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Exception ignored) {
        }
        int mins = 0;
        try {
            mins = prefs().getInt("screen_timer", 0);
        } catch (Exception ignored) {
        }
        if (mins > 0) {
            screenTimer = () -> dismissScreenDialog();
            handler.postDelayed(screenTimer, mins * 60000L);
        }
    }

    private void dismissScreenDialog() {
        cancelScreenTimer();
        try {
            if (screenDialog != null && screenDialog.isShowing()) screenDialog.dismiss();
        } catch (Exception ignored) {
        }
        screenDialog = null;
    }

    private void cancelScreenTimer() {
        try {
            if (screenTimer != null) handler.removeCallbacks(screenTimer);
        } catch (Exception ignored) {
        }
        screenTimer = null;
    }

    // ---------- strobe tab ----------

    private View buildStrobePage(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int tpad = ToolViewFactory.dp(context, 16);
        page.setPadding(tpad, tpad, tpad, tpad);
        TextView warn = new TextView(context);
        warn.setText("Warning: flashing lights can trigger seizures. "
                + "Never point it at people, and look away if you feel unwell.");
        warn.setTextSize(13);
        warn.setAlpha(0.8f);
        page.addView(warn);
        ToolViewFactory.addLabel(page, PackRes.str("device", R.string.s_pattern, "Pattern"));
        TextInputLayout layout =
                new TextInputLayout(context, null,
                        com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        layout.setHint(PackRes.str("device", R.string.s_pattern, "Pattern"));
        MaterialAutoCompleteTextView field =
                new MaterialAutoCompleteTextView(
                        layout.getContext());
        ArrayAdapter<String> ad = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, PATTERNS);
        field.setAdapter(ad);
        field.setText(PATTERNS[patternIndex], false);
        field.setOnItemClickListener((p, v, pos, id) -> {
            patternIndex = pos;
            try {
                prefs().edit().putInt("strobe_pattern", pos).apply();
            } catch (Exception ignored) {
            }
            if (morseRow != null) {
                morseRow.setVisibility(pos == 3 ? View.VISIBLE : View.GONE);
            }
        });
        layout.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(layout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView freqLabel = new TextView(context);
        freqLabel.setTextSize(14);
        page.addView(freqLabel);
        SeekBar freqBar = new SeekBar(context);
        freqBar.setMax(19);
        freqBar.setProgress(strobeHz - 1);
        Runnable paintFreq = () -> freqLabel.setText("Speed " + strobeHz + " Hz (strobe pattern)");
        paintFreq.run();
        freqBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                strobeHz = progress + 1;
                paintFreq.run();
                try {
                    prefs().edit().putInt("strobe_hz", strobeHz).apply();
                } catch (Exception ignored) {
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        page.addView(freqBar);
        final TextView dutyLabel = new TextView(context);
        dutyLabel.setTextSize(14);
        page.addView(dutyLabel);
        SeekBar dutyBar = new SeekBar(context);
        dutyBar.setMax(80);
        dutyBar.setProgress(strobeDuty - 10);
        Runnable paintDuty = () -> dutyLabel.setText("On-time " + strobeDuty + "% (strobe pattern)");
        paintDuty.run();
        dutyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                strobeDuty = progress + 10;
                paintDuty.run();
                try {
                    prefs().edit().putInt("strobe_duty", strobeDuty).apply();
                } catch (Exception ignored) {
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        page.addView(dutyBar);
        morseRow = new LinearLayout(context);
        morseRow.setOrientation(LinearLayout.VERTICAL);
        morseRow.setVisibility(patternIndex == 3 ? View.VISIBLE : View.GONE);
        TextInputLayout ml =
                new TextInputLayout(context);
        ml.setHint(PackRes.str("device", R.string.s_text_to_flash_a_z_0_9, "Text to flash (A-Z 0-9)"));
        TextInputEditText me =
                new TextInputEditText(ml.getContext());
        me.setText(morseText);
        me.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                try {
                    String t = s == null ? "" : s.toString().toUpperCase();
                    if (t == null || t.isEmpty()) t = "SOS";
                    morseText = t;
                } catch (Exception ignored) {
                }
                try {
                    prefs().edit().putString("morse_text", morseText).apply();
                } catch (Exception ignored) {
                }
            }
        });
        ml.addView(me, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        morseRow.addView(ml);
        page.addView(morseRow);
        strobePower = new MaterialButton(context);
        strobePower.setTextSize(18);
        strobePower.setAllCaps(false);
        strobePower.setMinHeight(ToolViewFactory.dp(context, 72));
        strobePower.setOnClickListener(v -> {
            if (blinkRunning) stopBlink();
            else startBlink();
        });
        paintStrobeButton();
        page.addView(strobePower, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        strobeState = new TextView(context);
        strobeState.setTextSize(13);
        strobeState.setAlpha(0.75f);
        page.addView(strobeState);
        return page;
    }

    private void paintStrobeButton() {
        if (strobePower == null) return;
        strobePower.setText(blinkRunning ? "Stop" : "Start flashing");
        try {
            strobePower.setBackgroundTintList(ColorStateList.valueOf(
                    blinkRunning ? Color.parseColor("#B3261E") : Color.parseColor("#0F9D58")));
            strobePower.setTextColor(Color.WHITE);
        } catch (Exception ignored) {
        }
    }

    private void startBlink() {
        if (Build.VERSION.SDK_INT < 23 || torchCameraId == null) {
            ToolViewFactory.toast(host, PackRes.str("device", R.string.s_flash_not_available, "Flash not available"));
            return;
        }
        if (!hasCameraPerm()) {
            askCameraPerm();
            return;
        }
        if (torchOn) {
            setTorchSilent(false);
            paintTorchButton();
        }
        timeline = buildTimeline();
        if (timeline.length == 0) {
            ToolViewFactory.toast(host, PackRes.str("device", R.string.s_nothing_to_flash, "Nothing to flash"));
            return;
        }
        blinkRunning = true;
        step = 0;
        paintStrobeButton();
        if (strobeState != null) strobeState.setText("Flashing " + PATTERNS[patternIndex] + "…");
        handler.post(blinkTick);
    }

    private void stopBlink() {
        blinkRunning = false;
        try {
            handler.removeCallbacks(blinkTick);
        } catch (Exception ignored) {
        }
        setTorchSilent(false);
        paintStrobeButton();
        if (strobeState != null) strobeState.setText("");
    }

    private final Runnable blinkTick = new Runnable() {
        @Override
        public void run() {
            if (!blinkRunning || timeline.length == 0) return;
            setTorchSilent(step % 2 == 0);
            handler.postDelayed(this, Math.max(30, timeline[step]));
            step = (step + 1) % timeline.length;
        }
    };

    private int[] buildTimeline() {
        if (patternIndex == 1) return morseTimeline("SOS", 180);
        if (patternIndex == 2) return new int[]{100, 100, 100, 900};
        if (patternIndex == 3) {
            String text = morseText == null ? "" : morseText.trim();
            if (text.isEmpty()) return new int[0];
            int[] line = morseTimeline(text, 150);
            if (line.length == 0) return line;
            int[] withPause = new int[line.length + 1];
            System.arraycopy(line, 0, withPause, 0, line.length);
            withPause[line.length] = 1500;
            return withPause;
        }
        long period = 1000L / Math.max(1, strobeHz);
        int on = (int) Math.max(20, period * strobeDuty / 100);
        int off = (int) Math.max(20, period - on);
        return new int[]{on, off};
    }

    private static int[] morseTimeline(String text, int unit) {
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                bits.append("       ");
                continue;
            }
            String code = morseCode(c);
            if (code.isEmpty()) continue;
            for (int j = 0; j < code.length(); j++) {
                bits.append(code.charAt(j) == '.' ? "1" : "111");
                bits.append("0");
            }
            bits.append("00");
        }
        while (bits.length() > 0 && bits.charAt(bits.length() - 1) == '0') {
            bits.setLength(bits.length() - 1);
        }
        if (bits.length() == 0) return new int[0];
        List<Integer> runs = new ArrayList<>();
        char cur = bits.charAt(0);
        int len = 0;
        for (int i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) == cur) {
                len++;
            } else {
                runs.add(len * unit);
                cur = bits.charAt(i);
                len = 1;
            }
        }
        runs.add(len * unit);
        if (bits.charAt(0) == '0') runs.add(0, 0);
        int[] out = new int[runs.size()];
        for (int i = 0; i < runs.size(); i++) out[i] = runs.get(i);
        return out;
    }

    private static String morseCode(char c) {
        switch (c) {
            case 'A':
                return ".-";
            case 'B':
                return "-...";
            case 'C':
                return "-.-.";
            case 'D':
                return "-..";
            case 'E':
                return ".";
            case 'F':
                return "..-.";
            case 'G':
                return "--.";
            case 'H':
                return "....";
            case 'I':
                return "..";
            case 'J':
                return ".---";
            case 'K':
                return "-.-";
            case 'L':
                return ".-..";
            case 'M':
                return "--";
            case 'N':
                return "-.";
            case 'O':
                return "---";
            case 'P':
                return ".--.";
            case 'Q':
                return "--.-";
            case 'R':
                return ".-.";
            case 'S':
                return "...";
            case 'T':
                return "-";
            case 'U':
                return "..-";
            case 'V':
                return "...-";
            case 'W':
                return ".--";
            case 'X':
                return "-..-";
            case 'Y':
                return "-.--";
            case 'Z':
                return "--..";
            case '0':
                return "-----";
            case '1':
                return ".----";
            case '2':
                return "..---";
            case '3':
                return "...--";
            case '4':
                return "....-";
            case '5':
                return ".....";
            case '6':
                return "-....";
            case '7':
                return "--...";
            case '8':
                return "---..";
            case '9':
                return "----.";
            default:
                return "";
        }
    }

    // ---------- shared rows ----------

    private interface MinutesPick {
        void pick(int minutes);
    }

    private void minutesRow(LinearLayout box, String label, int current,
                            MinutesPick pick) {
        TextInputLayout layout =
                new TextInputLayout(host);
        layout.setHint(label);
        TextInputEditText field =
                new TextInputEditText(layout.getContext());
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(Math.max(0, current)));
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                try {
                    int v = Integer.parseInt(s.toString().trim());
                    if (v >= 0 && v <= 1440) pick.pick(v);
                } catch (Exception ignored) {
                }
            }
        });
        layout.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ToolViewFactory.dp(host, 4);
        box.addView(layout, lp);
    }

    @Override
    public void onDestroy() {
        stopBlink();
        cancelTorchTimer();
        cancelScreenTimer();
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) {
        }
        setTorchSilent(false);
        torchOn = false;
        setKeepScreen(false);
        try {
            if (screenDialog != null && screenDialog.isShowing()) screenDialog.dismiss();
        } catch (Exception ignored) {
        }
        screenDialog = null;
        shell = null;
    }
}
