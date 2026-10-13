package io.github.abdurazaaqmohammed.terminal;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * A small built-in terminal: a scrollback view showing what the shell prints,
 * and a single input line that sends whatever is typed into it.
 *
 * <p>The shell runs on a background thread, so long output cannot stall the UI.
 * The prompt and colours come straight from the shell, so it looks like the
 * shell the user already uses over adb.
 */
public class TerminalActivity extends BaseActivity {

    private static final int MAX_SCROLLBACK = 400_000;

    private TextView screen;
    private ScrollView scroll;
    private TextInputEditText input;
    private TerminalSession session;
    private MaterialButton rootToggle;
    private final StringBuilder backlog = new StringBuilder();
    private boolean root;

    private final TerminalSession.Output output = new TerminalSession.Output() {
        @Override
        public void onOutput(String text) {
            runOnUiThread(() -> append(text));
        }

        @Override
        public void onExit(int code) {
            runOnUiThread(() -> {
                append("\n[process exited: " + code + "]\n");
                Toast.makeText(TerminalActivity.this,
                        R.string.terminal_exited, Toast.LENGTH_SHORT).show();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        MaterialToolbar toolbar = findViewById(R.id.terminal_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        screen = findViewById(R.id.terminal_screen);
        scroll = findViewById(R.id.terminal_scroll);
        input = findViewById(R.id.terminal_input);
        rootToggle = findViewById(R.id.terminal_root);

        root = RootManager.getInstance(this).isRootMode();
        rootToggle.setText(root ? R.string.terminal_root_on : R.string.terminal_root_off);
        rootToggle.setOnClickListener(v -> restart(!root));

        MaterialButton send = findViewById(R.id.terminal_send);
        send.setOnClickListener(v -> submit());
        MaterialButton ctrlC = findViewById(R.id.terminal_ctrl_c);
        ctrlC.setOnClickListener(v -> {
            if (session != null) {
                session.sendControl(3);
            }
        });

        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submit();
                return true;
            }
            return false;
        });

        append(getString(R.string.terminal_starting) + "\n");
        start();
    }

    private void restart(boolean asRoot) {
        root = asRoot;
        rootToggle.setText(asRoot ? R.string.terminal_root_on : R.string.terminal_root_off);
        if (session != null) {
            session.close();
        }
        backlog.setLength(0);
        screen.setText("");
        start();
    }

    private void start() {
        new Thread(() -> {
            TerminalSession started = TerminalSession.start(
                    root, RootManager.getInstance(this).suBinary());
            runOnUiThread(() -> {
                if (started == null) {
                    append("[failed to start shell]\n");
                    return;
                }
                session = started;
                session.addListener(output);
                append(getString(root ? R.string.terminal_root_banner
                        : R.string.terminal_user_banner) + "\n$ ");
            });
        }, "terminal-start").start();
    }

    private void submit() {
        String command = input.getText() == null ? "" : input.getText().toString();
        input.setText("");
        if (session == null || !session.isRunning()) {
            append("\n[shell is not running]\n");
            return;
        }
        append(command + "\n");
        session.send(command);
    }

    private void append(String text) {
        backlog.append(text);
        if (backlog.length() > MAX_SCROLLBACK) {
            backlog.delete(0, backlog.length() - MAX_SCROLLBACK);
        }
        screen.setText(backlog.toString());
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.removeListener(output);
            session.close();
        }
        super.onDestroy();
    }
}
