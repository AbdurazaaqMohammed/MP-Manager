package io.github.abdurazaaqmohammed.tools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.AdbFileService;
import io.github.abdurazaaqmohammed.utils.AdbSync;

/**
 * Walks the device over a local ADB session, reaching past this app's sandbox.
 *
 * <p>An ordinary app is sealed off from shared system locations by its sandbox.
 * The ADB server, when wireless debugging is on, will lend a {@code shell}
 * (uid 2000) session to any local client that speaks the protocol — and that
 * shell sits outside this app's sandbox, so it can reach directories such as
 * {@code /data/local/tmp}, {@code /sdcard} and {@code /system} that the app
 * itself cannot. This screen is a small file browser driven by that session:
 * open a folder, tap a child to descend, jump with a typed path, go back up.
 *
 * <p>What this is <em>not</em>: root. The session runs as {@code shell}, so
 * SELinux still applies the shell policy — on production builds that usually
 * means other apps' private {@code /data/data/<pkg>} trees stay off-limits even
 * here. Reaching those requires a root session (see {@code RootManager}), which
 * is a separate path this screen does not take.
 *
 * <p>Every ADB exchange runs on a background thread; the UI only paints results.
 */
public class AdbBrowserActivity extends BaseActivity {

    /** Where the browser lands when it first opens. */
    private static final String START_PATH = "/data/local/tmp";

    /** Cap on rendered rows so a directory of tens of thousands cannot freeze the UI. */
    private static final int MAX_ROWS = 500;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private EditText pathInput;
    private TextView status;
    private LinearLayout listContainer;
    private ScrollView scroll;
    private MaterialToolbar toolbar;

    private AdbFileService service;
    private String currentPath = START_PATH;

    /** Bumped per navigation so a slow reply for an abandoned path is dropped. */
    private int requestSeq;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        toolbar = new MaterialToolbar(this);
        toolbar.setTitle("ADB Browser");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(8);
        controls.setPadding(pad, pad, pad, pad);
        pathInput = new EditText(this);
        pathInput.setSingleLine(true);
        pathInput.setHint("path, e.g. /data");
        pathInput.setText(START_PATH);
        pathInput.setTextSize(14);
        controls.addView(pathInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton go = new MaterialButton(this);
        go.setText("Go");
        go.setOnClickListener(v -> navigate(pathInput.getText().toString().trim()));
        controls.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setPadding(pad, pad, pad, 0);
        status.setTextSize(13);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(pad, 0, pad, pad);
        scroll.addView(listContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        connectAndLoad(START_PATH);
    }

    /** Open the ADB session once, then list the starting path. */
    private void connectAndLoad(String path) {
        final int seq = ++requestSeq;
        status.setText("Connecting to ADB server on 127.0.0.1:5037…");
        io.execute(() -> {
            try {
                if (service == null) {
                    service = AdbFileService.open();
                }
                List<AdbSync.Entry> entries = service.listFiles(path);
                publish(path, entries, null, seq);
            } catch (Exception e) {
                publish(path, null, e, seq);
            }
        });
    }

    /** List a path on the background thread. */
    private void navigate(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        if (!path.startsWith("/")) {
            path = currentPath + "/" + path;
        }
        final String target = path;
        final int seq = ++requestSeq;
        status.setText("Listing " + target + "…");
        io.execute(() -> {
            try {
                if (service == null) {
                    throw new java.io.IOException("not connected to the ADB server");
                }
                List<AdbSync.Entry> entries = service.listFiles(target);
                publish(target, entries, null, seq);
            } catch (Exception e) {
                publish(target, null, e, seq);
            }
        });
    }

    /** Hand a result back to the UI thread, dropping it if the user moved on. */
    private void publish(String path, List<AdbSync.Entry> entries, Exception error, int seq) {
        runOnUiThread(() -> {
            if (seq != requestSeq) {
                return; // a newer navigation superseded this reply
            }
            if (error != null) {
                status.setText("Error at " + path + ": " + friendly(error));
                return;
            }
            currentPath = path;
            pathInput.setText(path);
            toolbarTitle(path);
            render(path, entries);
        });
    }

    private void render(String path, List<AdbSync.Entry> entries) {
        listContainer.removeAllViews();
        status.setText(entries.size() + " entries in " + path);

        if (!"/".equals(path)) {
            listContainer.addView(row("..", "up one level", v -> {
                String parent = path.substring(0, path.lastIndexOf('/'));
                navigate(parent.isEmpty() ? "/" : parent);
            }));
        }
        int shown = 0;
        for (AdbSync.Entry entry : entries) {
            if (shown >= MAX_ROWS) {
                TextView more = new TextView(this);
                more.setText("… " + (entries.size() - shown) + " more not shown");
                more.setTextSize(13);
                more.setPadding(0, dp(8), 0, 0);
                listContainer.addView(more);
                break;
            }
            boolean isDir = entry.isDirectory();
            String child = ("/".equals(path) ? "" : path) + "/" + entry.name;
            String detail = (isDir ? "dir" : entry.size + " bytes");
            if (isDir) {
                listContainer.addView(row(entry.name, detail, v -> navigate(child)));
            } else {
                listContainer.addView(row(entry.name, detail, v -> showFile(child, entry)));
            }
            shown++;
        }
    }

    /** Show a file's stat details without pulling its bytes. */
    private void showFile(String path, AdbSync.Entry entry) {
        status.setText(path + "  ·  mode " + Integer.toOctalString(entry.mode & 07777)
                + "  ·  " + entry.size + " bytes  ·  mtime " + entry.mtime);
    }

    private View row(String title, String detail, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnClickListener(onClick);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.START);
        row.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = new TextView(this);
        sub.setText(detail);
        sub.setTextSize(12);
        sub.setAlpha(0.7f);
        row.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void toolbarTitle(String path) {
        toolbar.setTitle(path);
    }

    /** Turn the two failure modes a user will actually hit into plain language. */
    private String friendly(Exception e) {
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("Connection refused")) {
            return "no ADB server — turn on Wireless debugging (Android 11+) first";
        }
        if (msg.contains("Permission denied")) {
            return "shell user cannot read this path";
        }
        if (msg.contains("timed out") || msg.contains("Timeout")) {
            return "connection timed out";
        }
        return msg;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.execute(() -> {
            if (service != null) {
                service.close();
            }
        });
        io.shutdown();
    }
}
