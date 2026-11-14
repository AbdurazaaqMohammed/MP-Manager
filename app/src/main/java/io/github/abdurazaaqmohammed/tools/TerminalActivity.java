package io.github.abdurazaaqmohammed.tools;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * High-performance embedded terminal emulator for MPlite.
 * Powered by Termux terminal-view & terminal-emulator engine with full PTY support,
 * interactive ANSI colors, control keys (Ctrl, Alt, Esc, Tab, Arrow keys),
 * dedicated bottom input bar for real-time script interaction (MT Manager style),
 * and automatic root/shizuku/local shell detection.
 */
public class TerminalActivity extends BaseActivity implements TerminalSessionClient, TerminalViewClient {

    public static final String EXTRA_INITIAL_COMMAND = "initial_command";
    public static final String EXTRA_SCRIPT_PATH = "script_path";
    public static final String EXTRA_SCRIPT_NAME = "script_name";
    public static final String EXTRA_WORKING_DIR = "working_dir";
    public static final String EXTRA_RUN_AS_ROOT = "run_as_root";

    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private ScrollView scriptScrollView;
    private TextView scriptOutputView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean ctrlDown = false;
    private boolean altDown = false;
    private MaterialButton ctrlBtn;
    private MaterialButton altBtn;
    private EditText cmdInput;

    private boolean isScriptMode = false;
    private boolean isRootSession = false;
    private float currentTextSizeSp = 11.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        Intent intent = getIntent();
        String scriptName = intent != null ? intent.getStringExtra(EXTRA_SCRIPT_NAME) : null;
        String scriptPath = intent != null ? intent.getStringExtra(EXTRA_SCRIPT_PATH) : null;
        isRootSession = intent != null && intent.getBooleanExtra(EXTRA_RUN_AS_ROOT, false);
        isScriptMode = (scriptPath != null && !scriptPath.isEmpty());

        float density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF181818);

        Toolbar toolbar = new Toolbar(this);
        if (scriptName != null && !scriptName.isEmpty()) {
            toolbar.setTitle(scriptName);
        } else if (scriptPath != null && !scriptPath.isEmpty()) {
            toolbar.setTitle(new File(scriptPath).getName());
        } else {
            toolbar.setTitle(getString(R.string.terminal_title));
        }

        if (isScriptMode) {
            toolbar.setSubtitle(getString(isRootSession ? R.string.terminal_running_root : R.string.terminal_running_normal));
        } else {
            toolbar.setSubtitle(getString(isRootSession ? R.string.terminal_running_root : R.string.terminal_subtitle));
        }
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFB0B0B0);
        toolbar.setBackgroundColor(0xFF252526);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout terminalContainer = new FrameLayout(this);
        terminalContainer.setBackgroundColor(0xFF181818);
        int padH = (int) (8 * density);
        int padV = (int) (6 * density);
        terminalContainer.setPadding(padH, padV, padH, padV);

        if (isScriptMode) {
            scriptScrollView = new ScrollView(this);
            scriptScrollView.setFillViewport(true);
            scriptOutputView = new TextView(this);
            scriptOutputView.setTextColor(0xFFEAEAEA);
            scriptOutputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            scriptOutputView.setTypeface(Typeface.DEFAULT);
            scriptOutputView.setLineSpacing(0f, 1.15f);
            scriptOutputView.setTextIsSelectable(true);
            scriptScrollView.addView(scriptOutputView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            terminalContainer.addView(scriptScrollView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            terminalView = new TerminalView(this, null);
            int textSizePx = (int) (currentTextSizeSp * density);
            terminalView.setTextSize(textSizePx);
            terminalView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            terminalView.setTerminalViewClient(this);

            terminalContainer.addView(terminalView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout.LayoutParams termLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(terminalContainer, termLp);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF333333);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));

        View inputBar = buildInputBar();
        root.addView(inputBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View extraKeysBar = buildExtraKeysBar();
        root.addView(extraKeysBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        View contentView = getWindow().getDecorView().findViewById(android.R.id.content);
        final View insetTarget = contentView != null ? contentView : root;
        if (contentView != null) {
            root.setPadding(0, 0, 0, 0);
        }
        ViewCompat.setOnApplyWindowInsetsListener(insetTarget, (v, windowInsets) -> {
            Insets sys = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            int topPad = Math.max(0, sys.top - loc[1]);
            int bottom = Math.max(sys.bottom, ime.bottom);
            v.setPadding(sys.left, topPad, sys.right, bottom);
            if (isScriptMode && scriptScrollView != null && ime.bottom > 0) {
                scriptScrollView.post(() -> scriptScrollView.fullScroll(View.FOCUS_DOWN));
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.setWindowInsetsAnimationCallback(insetTarget, new WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                 @NonNull java.util.List<WindowInsetsAnimationCompat> runningAnimations) {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int[] loc = new int[2];
                insetTarget.getLocationInWindow(loc);
                int topPad = Math.max(0, sys.top - loc[1]);
                insetTarget.setPadding(sys.left, topPad, sys.right, Math.max(sys.bottom, ime.bottom));
                return insets;
            }
        });
        insetTarget.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastBottomPad = -1;

            @Override
            public void onGlobalLayout() {
                int[] loc = new int[2];
                insetTarget.getLocationInWindow(loc);
                WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(insetTarget);
                if (rootInsets != null) {
                    Insets sys = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    Insets ime = rootInsets.getInsets(WindowInsetsCompat.Type.ime());
                    int topPad = Math.max(0, sys.top - loc[1]);
                    if (ime.bottom > 0) {
                        int target = Math.max(sys.bottom, ime.bottom);
                        if (insetTarget.getPaddingTop() != topPad || insetTarget.getPaddingBottom() != target) {
                            insetTarget.setPadding(sys.left, topPad, sys.right, target);
                        }
                        lastBottomPad = target;
                        return;
                    }
                    if (insetTarget.getPaddingTop() != topPad) {
                        insetTarget.setPadding(sys.left, topPad, sys.right, sys.bottom);
                    }
                }
                Rect r = new Rect();
                insetTarget.getWindowVisibleDisplayFrame(r);
                int targetBottom = loc[1] + insetTarget.getHeight();
                int overlap = targetBottom - r.bottom;
                if (overlap > (int) (100 * density)) {
                    if (lastBottomPad != overlap) {
                        lastBottomPad = overlap;
                        insetTarget.setPadding(insetTarget.getPaddingLeft(), insetTarget.getPaddingTop(), insetTarget.getPaddingRight(), overlap);
                        if (isScriptMode && scriptScrollView != null) {
                            scriptScrollView.post(() -> scriptScrollView.fullScroll(View.FOCUS_DOWN));
                        }
                    }
                } else if (lastBottomPad > 0) {
                    lastBottomPad = 0;
                    ViewCompat.requestApplyInsets(insetTarget);
                }
            }
        });
        ViewCompat.requestApplyInsets(insetTarget);

        initTerminalSession();
    }

    private View buildInputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF252526);
        int padH = (int) (6 * getResources().getDisplayMetrics().density);
        int padV = (int) (4 * getResources().getDisplayMetrics().density);
        bar.setPadding(padH, padV, padH, padV);

        cmdInput = new EditText(this);
        cmdInput.setHint(R.string.terminal_input_hint);
        cmdInput.setHintTextColor(0xFF888888);
        cmdInput.setTextColor(Color.WHITE);
        cmdInput.setTextSize(13f);
        cmdInput.setSingleLine(true);
        cmdInput.setImeOptions(EditorInfo.IME_ACTION_SEND);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF1E1E1E);
        inputBg.setCornerRadius(4 * getResources().getDisplayMetrics().density);
        inputBg.setStroke((int) (1 * getResources().getDisplayMetrics().density), 0xFF3E3E42);
        cmdInput.setBackground(inputBg);

        int editPadH = (int) (8 * getResources().getDisplayMetrics().density);
        int editPadV = (int) (6 * getResources().getDisplayMetrics().density);
        cmdInput.setPadding(editPadH, editPadV, editPadH, editPadV);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputLp.setMargins(0, 0, (int) (4 * getResources().getDisplayMetrics().density), 0);
        bar.addView(cmdInput, inputLp);

        MaterialButton sendBtn = createBarButton(getString(R.string.terminal_send), 0xFF007ACC, v -> sendCurrentInput());
        bar.addView(sendBtn);

        MaterialButton enterBtn = createBarButton("↵", 0xFF3E3E42, v -> {
            if (terminalSession != null) {
                terminalSession.write("\r");
            }
        });
        bar.addView(enterBtn);

        MaterialButton interruptBtn = createBarButton(getString(R.string.terminal_interrupt), 0xFF5A1E1E, v -> {
            if (terminalSession != null) {
                terminalSession.write(new byte[]{3}, 0, 1); // Ctrl+C
            }
        });
        interruptBtn.setTextColor(0xFFFF7B72);
        bar.addView(interruptBtn);

        cmdInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendCurrentInput();
                return true;
            }
            return false;
        });

        return bar;
    }

    private void sendCurrentInput() {
        if (cmdInput == null) return;
        String text = cmdInput.getText().toString();
        if (terminalSession != null) {
            terminalSession.write(text + "\r");
        }
        cmdInput.setText("");
    }

    private MaterialButton createBarButton(String text, int bgColor, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(bgColor);
        btn.setCornerRadius((int) (4 * getResources().getDisplayMetrics().density));
        int padH = (int) (10 * getResources().getDisplayMetrics().density);
        int padV = (int) (4 * getResources().getDisplayMetrics().density);
        btn.setPadding(padH, padV, padH, padV);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        btn.setMinimumHeight(0);
        btn.setMinHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (2 * getResources().getDisplayMetrics().density), 0, (int) (2 * getResources().getDisplayMetrics().density), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private View buildExtraKeysBar() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(0xFF2D2D30);
        int pad = (int) (4 * density);
        container.setPadding(pad, pad, pad, pad);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        ctrlBtn = createKeyButton("CTRL", v -> {
            ctrlDown = !ctrlDown;
            updateKeyToggleState(ctrlBtn, ctrlDown);
        });
        altBtn = createKeyButton("ALT", v -> {
            altDown = !altDown;
            updateKeyToggleState(altBtn, altDown);
        });

        row1.addView(createKeyButton("ESC", v -> sendKey(KeyEvent.KEYCODE_ESCAPE)));
        row1.addView(createKeyButton("TAB", v -> sendKey(KeyEvent.KEYCODE_TAB)));
        row1.addView(ctrlBtn);
        row1.addView(altBtn);
        row1.addView(createKeyButton("\u2191", v -> sendKey(KeyEvent.KEYCODE_DPAD_UP)));
        row1.addView(createKeyButton("\u2193", v -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)));

        row2.addView(createKeyButton("\u2190", v -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)));
        row2.addView(createKeyButton("\u2192", v -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)));
        row2.addView(createKeyButton("Ctrl+C", v -> {
            if (terminalSession != null) terminalSession.write(new byte[]{3}, 0, 1);
        }));
        row2.addView(createKeyButton("Ctrl+D", v -> {
            if (terminalSession != null) terminalSession.write(new byte[]{4}, 0, 1);
        }));
        row2.addView(createKeyButton("Ctrl+L", v -> {
            if (terminalSession != null) terminalSession.write(new byte[]{12}, 0, 1);
        }));

        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Lp.setMargins(0, 0, 0, (int) (3 * density));
        container.addView(row1, row1Lp);
        container.addView(row2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return container;
    }

    private MaterialButton createKeyButton(String text, View.OnClickListener listener) {
        float density = getResources().getDisplayMetrics().density;
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFF3E3E42);
        btn.setCornerRadius((int) (5 * density));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        int padH = (int) (6 * density);
        int padV = (int) (7 * density);
        btn.setPadding(padH, padV, padH, padV);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        btn.setMinimumHeight((int) (36 * density));
        btn.setMinHeight((int) (36 * density));
        btn.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        int marginH = (int) (2 * density);
        lp.setMargins(marginH, 0, marginH, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void updateKeyToggleState(MaterialButton btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.setBackgroundColor(0xFF007ACC);
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackgroundColor(0xFF3E3E42);
            btn.setTextColor(Color.WHITE);
        }
    }

    private void sendKey(int keyCode) {
        if (terminalView != null && terminalSession != null) {
            long now = System.currentTimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
            terminalView.onKeyDown(keyCode, down);
            terminalView.onKeyUp(keyCode, up);
        }
    }

    private void initTerminalSession() {
        Intent intent = getIntent();
        String initialCmd = intent != null ? intent.getStringExtra(EXTRA_INITIAL_COMMAND) : null;
        String scriptPath = intent != null ? intent.getStringExtra(EXTRA_SCRIPT_PATH) : null;
        String workingDir = intent != null ? intent.getStringExtra(EXTRA_WORKING_DIR) : null;
        boolean asRoot = intent != null && intent.getBooleanExtra(EXTRA_RUN_AS_ROOT, false);

        if (workingDir == null || !new File(workingDir).isDirectory()) {
            if (scriptPath != null) {
                File sf = new File(scriptPath);
                if (sf.getParentFile() != null && sf.getParentFile().isDirectory()) {
                    workingDir = sf.getParent();
                }
            }
        }
        if (workingDir == null || !new File(workingDir).isDirectory()) {
            File ext = Environment.getExternalStorageDirectory();
            workingDir = (ext != null && ext.exists()) ? ext.getAbsolutePath() : getFilesDir().getAbsolutePath();
        }

        String execScriptPath = scriptPath;
        if (scriptPath != null) {
            try {
                File sf = new File(scriptPath);
                if (sf.exists() && sf.canRead()) {
                    byte[] head = new byte[(int) Math.min(sf.length(), 65536)];
                    try (FileInputStream fis = new FileInputStream(sf)) {
                        int read = fis.read(head);
                        boolean isBinary = read >= 4 && head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
                        boolean hasCr = false;
                        for (int i = 0; i < read && !isBinary; i++) {
                            if (head[i] == 0) {
                                isBinary = true;
                                break;
                            }
                            if (head[i] == '\r') {
                                hasCr = true;
                            }
                        }
                        if (hasCr && !isBinary) {
                            File sanitizedDir = new File(getCacheDir(), "sanitized_scripts");
                            sanitizedDir.mkdirs();
                            File sanitizedFile = new File(sanitizedDir, sf.getName());
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sf), StandardCharsets.UTF_8));
                                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sanitizedFile), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    writer.write(line);
                                    writer.write("\n");
                                }
                            }
                            sanitizedFile.setReadable(true, false);
                            sanitizedFile.setExecutable(true, false);
                            execScriptPath = sanitizedFile.getAbsolutePath();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        String shellExecutable = "/system/bin/sh";
        String[] shellArgs;

        if (scriptPath != null && !scriptPath.isEmpty()) {
            String quotedScript = RootManager.quoteForSh(execScriptPath);
            String scriptCmd = "if cd " + RootManager.quoteForSh(workingDir) + " 2>/dev/null; then :; else cd /sdcard 2>/dev/null || cd /; fi; "
                    + "_s=" + quotedScript + "; "
                    + "if [ \"$(dd if=\"$_s\" bs=4 count=1 2>/dev/null)\" = \"$(printf '\\177ELF')\" ]; then "
                    + "chmod +x \"$_s\" 2>/dev/null; "
                    + "if [ -x \"$_s\" ]; then ( \"$_s\" ); _ret=$?; else _ret=126; fi; "
                    + "if [ $_ret -eq 126 ]; then "
                    + "_tmp=\"/data/local/tmp/.mp_exec_$$\"; "
                    + "if cp \"$_s\" \"$_tmp\" 2>/dev/null && chmod 755 \"$_tmp\" 2>/dev/null; then ( \"$_tmp\" ); _ret=$?; rm -f \"$_tmp\" 2>/dev/null; fi; "
                    + "fi; "
                    + "else "
                    + "_sh=/system/bin/sh; "
                    + "_sb=$(head -n 1 \"$_s\" 2>/dev/null); "
                    + "case \"$_sb\" in \\#*bash*) command -v bash >/dev/null 2>&1 && _sh=bash;; \\#*zsh*) command -v zsh >/dev/null 2>&1 && _sh=zsh;; esac; "
                    + "( \"$_sh\" \"$_s\" ); _ret=$?; "
                    + "fi; "
                    + "printf '\\n\\033[1;32m--------------------------------\\033[0m\\n\\033[1;33m[进程已结束，退出代码: %s]\\033[0m\\n[按回车键或点击退出按钮关闭]\\n' \"$_ret\"; read _done";

            if (asRoot && RootManager.getInstance(this).isRootAvailable()) {
                shellExecutable = RootManager.getInstance(this).suBinary();
                shellArgs = new String[]{shellExecutable, "-c", scriptCmd};
            } else {
                shellExecutable = "/system/bin/sh";
                shellArgs = new String[]{shellExecutable, "-c", scriptCmd};
            }
        } else {
            if (asRoot && RootManager.getInstance(this).isRootAvailable()) {
                shellExecutable = RootManager.getInstance(this).suBinary();
                if (initialCmd != null && !initialCmd.isEmpty()) {
                    shellArgs = new String[]{shellExecutable, "-c", "cd " + RootManager.quoteForSh(workingDir) + " && " + initialCmd + "; exec /system/bin/sh"};
                } else {
                    shellArgs = new String[]{shellExecutable};
                }
            } else {
                shellExecutable = "/system/bin/sh";
                if (initialCmd != null && !initialCmd.isEmpty()) {
                    shellArgs = new String[]{shellExecutable, "-c", "cd " + RootManager.quoteForSh(workingDir) + " && " + initialCmd + "; exec /system/bin/sh"};
                } else {
                    shellArgs = new String[]{shellExecutable, "-i"};
                }
            }
        }

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("HOME", getFilesDir().getAbsolutePath());
        env.put("TMPDIR", getCacheDir().getAbsolutePath());
        if (!env.containsKey("LANG")) {
            env.put("LANG", "C.UTF-8");
        }
        String basePath = System.getenv("PATH");
        String extraPath = "/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin:/system/bin:/system/xbin:/vendor/bin:/data/local/tmp";
        env.put("PATH", (basePath != null && !basePath.isEmpty()) ? (basePath + ":" + extraPath) : extraPath);
        if (scriptPath != null) {
            env.put("MP_SCRIPT", scriptPath);
        }

        String[] envArray = new String[env.size()];
        int idx = 0;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            envArray[idx++] = entry.getKey() + "=" + entry.getValue();
        }

        String safeCwd = workingDir;
        try {
            File wd = new File(workingDir);
            if (!wd.isDirectory() || !wd.canRead()) {
                safeCwd = getFilesDir().getAbsolutePath();
            }
        } catch (Exception ignored) {
            safeCwd = getFilesDir().getAbsolutePath();
        }

        try {
            terminalSession = new TerminalSession(
                    shellExecutable,
                    safeCwd,
                    shellArgs,
                    envArray,
                    null,
                    this
            );
            if (isScriptMode) {
                terminalSession.initializeEmulator(120, 40);
            } else if (terminalView != null) {
                terminalView.attachSession(terminalSession);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Terminal launch failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem exitItem = menu.add(0, 5, 0, R.string.exit);
        exitItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 1, 1, R.string.terminal_clear);
        menu.add(0, 2, 2, R.string.terminal_copy_all);
        menu.add(0, 3, 3, R.string.terminal_paste);
        menu.add(0, 4, 4, R.string.terminal_toggle_ime);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home || id == 5) {
            finish();
            return true;
        } else if (id == 1) {
            if (terminalSession != null) {
                terminalSession.reset();
            }
            if (scriptOutputView != null) {
                scriptOutputView.setText("");
            }
            return true;
        } else if (id == 2) {
            copyAllOutput();
            return true;
        } else if (id == 3) {
            onPasteTextFromClipboard(terminalSession);
            return true;
        } else if (id == 4) {
            toggleKeyboard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View target = terminalView != null ? terminalView : cmdInput;
        if (imm != null && target != null) {
            target.requestFocus();
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    private void copyAllOutput() {
        String fullText = null;
        if (isScriptMode && scriptOutputView != null) {
            fullText = scriptOutputView.getText().toString();
        } else if (terminalSession != null && terminalSession.getEmulator() != null) {
            fullText = terminalSession.getEmulator().getScreen().getTranscriptTextWithFullLinesJoined();
        }
        if (fullText != null && !fullText.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", fullText));
                Toast.makeText(this, android.R.string.copy, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (terminalSession != null) {
            try {
                terminalSession.finishIfRunning();
            } catch (Throwable ignored) {
            }
        }
    }

    // --- TerminalSessionClient ---
    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (terminalView != null) terminalView.onScreenUpdated();
        if (isScriptMode && scriptOutputView != null && changedSession != null && changedSession.getEmulator() != null) {
            final String text = changedSession.getEmulator().getScreen().getTranscriptTextWithFullLinesJoined();
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed() && scriptOutputView != null) {
                    scriptOutputView.setText(text != null ? text.replaceFirst("\\s+$", "") : "");
                    if (scriptScrollView != null) {
                        scriptScrollView.post(() -> scriptScrollView.fullScroll(View.FOCUS_DOWN));
                    }
                }
            });
        }
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        if (changedSession != null && changedSession.getTitle() != null && getSupportActionBar() != null && !isScriptMode) {
            getSupportActionBar().setTitle(changedSession.getTitle());
        }
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        mainHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                if (getSupportActionBar() != null) {
                    String baseStatus = getString(isRootSession ? R.string.terminal_running_root : R.string.terminal_running_normal);
                    getSupportActionBar().setSubtitle(baseStatus + " [" + getString(R.string.terminal_finished) + "]");
                }
                Toast.makeText(this, R.string.terminal_session_finished, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && text != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text));
            Toast.makeText(this, android.R.string.copy, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null && session != null) {
                session.write(text.toString());
            }
        }
    }

    @Override
    public void onBell(TerminalSession session) {}

    @Override
    public void onColorsChanged(TerminalSession session) {}

    @Override
    public void onTerminalCursorStateChange(boolean state) {}

    @Override
    public Integer getTerminalCursorStyle() {
        return 0; // block cursor
    }

    @Override
    public void logError(String tag, String message) {}
    @Override
    public void logWarn(String tag, String message) {}
    @Override
    public void logInfo(String tag, String message) {}
    @Override
    public void logDebug(String tag, String message) {}
    @Override
    public void logVerbose(String tag, String message) {}
    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {}
    @Override
    public void logStackTrace(String tag, Exception e) {}

    // --- TerminalViewClient ---
    @Override
    public float onScale(float scale) {
        if (terminalView == null) return 1.0f;
        currentTextSizeSp = Math.max(8.0f, Math.min(24.0f, currentTextSizeSp * scale));
        int px = (int) (currentTextSizeSp * getResources().getDisplayMetrics().density);
        terminalView.setTextSize(px);
        return 1.0f;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        toggleKeyboard();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return false;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        boolean val = ctrlDown;
        if (ctrlDown) {
            ctrlDown = false;
            updateKeyToggleState(ctrlBtn, false);
        }
        return val;
    }

    @Override
    public boolean readAltKey() {
        boolean val = altDown;
        if (altDown) {
            altDown = false;
            updateKeyToggleState(altBtn, false);
        }
        return val;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false;
    }

    @Override
    public void onEmulatorSet() {}
}
