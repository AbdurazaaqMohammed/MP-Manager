package io.github.abdurazaaqmohammed.tools;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.features.apk.frida.FridaConfig;
import io.github.abdurazaaqmohammed.features.apk.frida.FridaGadgetPatcher;
import io.github.abdurazaaqmohammed.features.apk.frida.FridaModuleDialog;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Frida console for rooted devices (frida-hook-engine Path C): injects the
 * engine into a running (or freshly launched) app with the official
 * frida-inject binary and streams the engine's log output into a console.
 *
 * <p>The engine agent is the bundle's pre-compiled libfrida-gadget.script.so
 * (frida-java-bridge compiled in, as Frida 17 no longer ships it in the
 * runtime) with an {@code rpc.exports.init(null, parameters)} call appended,
 * so the exact same module configuration drives both this tool and the APK
 * gadget patcher.</p>
 */
public class FridaConsoleActivity extends BaseActivity {

    private static final String REMOTE_DIR = "/data/local/tmp/.mpfrida";

    private MaterialAutoCompleteTextView packageView;
    private TextView moduleSummary;
    private TextView console;
    private ScrollView consoleScroll;
    private MaterialSwitch launchSwitch;
    private MaterialSwitch eternalSwitch;
    private MaterialButton startButton;
    private MaterialButton stopButton;

    private FridaConfig config;
    private Process process;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RootManager rootManager = RootManager.getInstance(this);
        if (!rootManager.isRootAvailable()) {
            Extensions.showMessage(this, getString(R.string.frida_root_required));
            finish();
            return;
        }
        config = FridaConfig.load(this);

        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextInputLayout packageLayout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        packageLayout.setHint(getString(R.string.frida_console_target_package));
        packageView = new MaterialAutoCompleteTextView(this);
        packageView.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        packageLayout.addView(packageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(packageLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton browse = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        browse.setText(R.string.frida_console_browse_apps);
        browse.setOnClickListener(v -> showAppPicker());
        LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        browseLp.topMargin = pad / 2;
        root.addView(browse, browseLp);

        LinearLayout configRow = new LinearLayout(this);
        configRow.setOrientation(LinearLayout.HORIZONTAL);
        configRow.setGravity(Gravity.CENTER_VERTICAL);
        configRow.setPadding(0, pad / 2, 0, 0);
        moduleSummary = new TextView(this);
        moduleSummary.setTextAppearance(this,
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        moduleSummary.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        configRow.addView(moduleSummary);
        MaterialButton modulesButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        modulesButton.setText(R.string.frida_modules_label);
        modulesButton.setOnClickListener(v -> FridaModuleDialog.show(this, config, this::updateSummary));
        configRow.addView(modulesButton);
        root.addView(configRow);
        updateSummary();

        launchSwitch = new MaterialSwitch(this);
        launchSwitch.setText(R.string.frida_console_launch);
        launchSwitch.setChecked(true);
        root.addView(launchSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        eternalSwitch = new MaterialSwitch(this);
        eternalSwitch.setText(R.string.frida_console_eternalize);
        root.addView(eternalSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView eternalHint = new TextView(this);
        eternalHint.setText(R.string.frida_console_eternalize_hint);
        eternalHint.setTextAppearance(this,
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        eternalHint.setAlpha(0.7f);
        root.addView(eternalHint);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, pad / 2, 0, 0);
        startButton = new MaterialButton(this);
        startButton.setText(R.string.frida_console_start);
        startButton.setOnClickListener(v -> startInjection());
        startButton.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        buttonRow.addView(startButton);
        stopButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        stopButton.setText(R.string.frida_console_stop);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopInjection(true));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stopLp.setMargins(pad / 2, 0, 0, 0);
        stopButton.setLayoutParams(stopLp);
        buttonRow.addView(stopButton);
        root.addView(buttonRow);

        console = new TextView(this);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextSize(12f);
        console.setTextIsSelectable(true);
        consoleScroll = new ScrollView(this);
        consoleScroll.addView(console);
        LinearLayout.LayoutParams consoleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        consoleLp.setMargins(0, pad, 0, 0);
        root.addView(consoleScroll, consoleLp);

        setContentView(root);
    }

    private void updateSummary() {
        moduleSummary.setText(getString(R.string.frida_modules_summary,
                config.enabledModuleCount(), FridaConfig.MODULES.length));
    }

    // ------------------------------------------------------------------
    // app picker (label + package search, icons, name/install/update sort)
    // ------------------------------------------------------------------

    private static final int SORT_NAME = 0;
    private static final int SORT_UPDATED = 1;
    private static final int SORT_INSTALLED = 2;

    private int appSortMode() {
        return getPreferences(MODE_PRIVATE).getInt("frida_console_app_sort", SORT_NAME);
    }

    private void showAppPicker() {
        new Thread(() -> {
            List<PackageInfo> apps = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                apps.addAll(pm.getInstalledPackages(0));
            } catch (Exception ignored) {
            }
            List<PackageInfo> fApps = apps;
            runOnUiThread(() -> displayAppPicker(fApps));
        }, "frida-apps").start();
    }

    private void displayAppPicker(List<PackageInfo> loaded) {
        final List<PackageInfo> visible = new ArrayList<>();
        Map<String, Drawable> icons = new HashMap<>();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, 0, pad, 0);

        EditText search = new EditText(this);
        search.setHint(R.string.frida_console_search);
        search.setSingleLine(true);
        root.addView(search, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton sortButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        sortButton.setText(sortLabel(appSortMode()));
        LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sortLp.setMargins(0, pad / 4, 0, pad / 4);
        root.addView(sortButton, sortLp);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return visible.size();
            }

            @Override
            public PackageInfo getItem(int position) {
                return visible.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                LinearLayout row;
                IconHolder holder;
                if (convertView instanceof LinearLayout && convertView.getTag() instanceof IconHolder) {
                    row = (LinearLayout) convertView;
                    holder = (IconHolder) convertView.getTag();
                } else {
                    int rpad = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
                    row = new LinearLayout(FridaConsoleActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(rpad, rpad / 2, rpad, rpad / 2);
                    row.setBackgroundResource(android.R.drawable.list_selector_background);
                    holder = new IconHolder();
                    holder.icon = new TextView(FridaConsoleActivity.this);
                    row.addView(holder.icon, new LinearLayout.LayoutParams(
                            (int) (36 * getResources().getDisplayMetrics().density + 0.5f),
                            (int) (36 * getResources().getDisplayMetrics().density + 0.5f)));
                    LinearLayout col = new LinearLayout(FridaConsoleActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    holder.label = new TextView(FridaConsoleActivity.this);
                    holder.label.setTypeface(null, Typeface.BOLD);
                    holder.label.setTextSize(15f);
                    col.addView(holder.label);
                    holder.sub = new TextView(FridaConsoleActivity.this);
                    holder.sub.setTextSize(12f);
                    holder.sub.setAlpha(0.7f);
                    col.addView(holder.sub);
                    LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    colLp.setMargins((int) (12 * getResources().getDisplayMetrics().density + 0.5f), 0, 0, 0);
                    row.addView(col, colLp);
                    row.setTag(holder);
                }
                PackageInfo info = visible.get(position);
                holder.pkg = info.packageName;
                holder.label.setText(labelOf(info));
                holder.sub.setText(info.packageName + "  •  "
                        + DateFormat.getMediumDateFormat(FridaConsoleActivity.this)
                        .format(new Date(appSortMode() == SORT_UPDATED
                                ? info.lastUpdateTime : info.firstInstallTime)));
                Drawable icon = icons.get(info.packageName);
                if (icon != null) {
                    holder.icon.setText(null);
                    holder.icon.setBackground(icon);
                } else {
                    holder.icon.setBackgroundResource(android.R.drawable.sym_def_app_icon);
                    new Thread(() -> {
                        try {
                            Drawable loaded = info.applicationInfo.loadIcon(getPackageManager());
                            icons.put(info.packageName, loaded);
                            runOnUiThread(() -> {
                                if (holder.pkg != null && holder.pkg.equals(info.packageName)) {
                                    holder.icon.setText(null);
                                    holder.icon.setBackground(loaded);
                                }
                            });
                        } catch (Exception ignored) {
                        }
                    }, "icon-" + info.packageName).start();
                }
                return row;
            }
        };

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.6)));

        Runnable resort = () -> {
            List<PackageInfo> filtered = new ArrayList<>();
            String q = search.getText() == null ? "" : search.getText().toString()
                    .trim().toLowerCase(Locale.ROOT);
            for (PackageInfo info : loaded) {
                if (info.packageName.equals(getPackageName())) continue;
                if (q.isEmpty()) {
                    filtered.add(info);
                    continue;
                }
                String label = labelOf(info).toLowerCase(Locale.ROOT);
                if (label.contains(q) || info.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(info);
                }
            }
            int mode = appSortMode();
            Comparator<PackageInfo> comparator;
            if (mode == SORT_UPDATED) {
                comparator = (a, b) -> Long.compare(b.lastUpdateTime, a.lastUpdateTime);
            } else if (mode == SORT_INSTALLED) {
                comparator = (a, b) -> Long.compare(b.firstInstallTime, a.firstInstallTime);
            } else {
                comparator = (a, b) -> labelOf(a).compareToIgnoreCase(labelOf(b));
            }
            Collections.sort(filtered, comparator);
            visible.clear();
            visible.addAll(filtered);
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                resort.run();
            }
        });
        resort.run();

        sortButton.setOnClickListener(v -> {
            int mode = appSortMode();
            String[] options = {getString(R.string.frida_console_sort_name),
                    getString(R.string.frida_console_sort_updated),
                    getString(R.string.frida_console_sort_installed)};
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.frida_console_sort)
                    .setSingleChoiceItems(options, mode, (d, which) -> {
                        getPreferences(MODE_PRIVATE).edit()
                                .putInt("frida_console_app_sort", which).apply();
                        sortButton.setText(sortLabel(which));
                        d.dismiss();
                        resort.run();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.frida_console_pick_app)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            packageView.setText(visible.get(position).packageName);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static final class IconHolder {
        TextView icon;
        TextView label;
        TextView sub;
        String pkg;
    }

    private String labelOf(PackageInfo info) {
        try {
            CharSequence label = info.applicationInfo != null
                    ? info.applicationInfo.loadLabel(getPackageManager()) : null;
            return TextUtils.isEmpty(label) ? info.packageName : label.toString();
        } catch (Exception e) {
            return info.packageName;
        }
    }

    private String sortLabel(int mode) {
        return getString(R.string.frida_console_sort) + ": " + getString(mode == SORT_UPDATED
                ? R.string.frida_console_sort_updated : mode == SORT_INSTALLED
                ? R.string.frida_console_sort_installed : R.string.frida_console_sort_name);
    }

    private String selectedPackage() {
        CharSequence text = packageView.getText();
        if (TextUtils.isEmpty(text)) return null;
        String value = text.toString().trim();
        // The dropdown shows "Label — package"; take the part after the dash.
        int dash = value.lastIndexOf(" — ");
        if (dash >= 0) value = value.substring(dash + 3).trim();
        return value;
    }

    private void startInjection() {
        String pkg = selectedPackage();
        if (pkg == null || pkg.isEmpty() || !pkg.matches("[A-Za-z0-9_.]+")) {
            Extensions.showMessage(this, getString(R.string.frida_console_pick_package));
            return;
        }
        FridaGadgetPatcher.ensureInject(this, () -> {
            config.save(this);
            try {
                runInjection(pkg);
            } catch (Exception e) {
                new ErrorUtil(this).showError(e);
            }
        });
    }

    private void runInjection(String pkg) throws Exception {
        File script = buildScriptFile();
        File runSh = buildRunScript(pkg, script);
        appendConsole("$ su -c sh " + runSh.getAbsolutePath() + "\n");
        process = new ProcessBuilder(RootManager.getInstance(this).suBinary(), "-c",
                "sh " + RootManager.quoteForSh(runSh.getAbsolutePath()))
                .redirectErrorStream(true)
                .start();
        setRunning(true);
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    appendConsole(new String(buf, 0, n));
                }
            } catch (Exception ignored) {
            }
            int code = -1;
            try {
                code = process.waitFor();
            } catch (Exception ignored) {
            }
            final int exit = code;
            runOnUiThread(() -> {
                appendConsole("\n[frida-inject exited with code " + exit + "]\n");
                setRunning(false);
            });
        }, "frida-console").start();
    }

    /** engine agent (raw code, java bridge included) + our init() call. */
    private File buildScriptFile() throws Exception {
        File agent = FridaGadgetPatcher.agentScriptFile(this);
        if (!agent.isFile()) throw new IllegalStateException("Engine bundle is not downloaded");
        File script = new File(getFilesDir(), "frida/inject-script.js");
        new File(getFilesDir(), "frida").mkdirs();
        // The bundle ships with a frida-compile header + source map wrapped
        // around the code; Gadget strips that itself, but frida-inject only
        // accepts the raw script.
        byte[] plain = FridaGadgetPatcher.plainAgentScript(agent);
        try (FileOutputStream fos = new FileOutputStream(script)) {
            fos.write(plain);
            fos.write(FridaConfig.buildInitCall(config.params()).getBytes(StandardCharsets.UTF_8));
        }
        return script;
    }

    private File buildRunScript(String pkg, File script) throws Exception {
        File dir = new File(getFilesDir(), "frida");
        dir.mkdirs();
        File runSh = new File(dir, "run.sh");
        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n")
                .append("mkdir -p ").append(REMOTE_DIR).append('\n')
                .append("cp ").append(RootManager.quoteForSh(
                        FridaGadgetPatcher.injectBinary(this).getAbsolutePath()))
                .append(' ').append(REMOTE_DIR).append("/inject\n")
                .append("chmod 755 ").append(REMOTE_DIR).append("/inject\n")
                .append("cp ").append(RootManager.quoteForSh(script.getAbsolutePath()))
                .append(' ').append(REMOTE_DIR).append("/script.js\n");
        // pidof plus a ps fallback for ROMs without toybox pidof.
        sb.append("PID=$(pidof ").append(pkg).append(" 2>/dev/null)\n")
                .append("if [ -z \"$PID\" ]; then\n")
                .append("  PID=$(ps -A 2>/dev/null | grep -w '").append(pkg)
                .append("' | head -n 1 | awk '{print $2}')\n")
                .append("fi\n");
        if (launchSwitch.isChecked()) {
            sb.append("if [ -z \"$PID\" ]; then\n")
                    .append("  echo \"[*] Launching ").append(pkg).append("...\"\n")
                    .append("  monkey -p ").append(pkg)
                    .append(" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1\n")
                    .append("  i=0\n")
                    .append("  while [ $i -lt 40 ]; do\n")
                    .append("    sleep 0.5\n")
                    .append("    PID=$(pidof ").append(pkg).append(" 2>/dev/null)\n")
                    .append("    [ -n \"$PID\" ] && break\n")
                    .append("    i=$((i+1))\n")
                    .append("  done\n")
                    .append("fi\n");
        }
        sb.append("if [ -z \"$PID\" ]; then\n")
                .append("  echo \"[!] ").append(pkg).append(" is not running\"\n")
                .append("  exit 2\n")
                .append("fi\n")
                .append("echo \"[*] Attaching to PID $PID\"\n")
                .append("EXTRA=\"\"\n")
                .append(eternalSwitch.isChecked() ? "EXTRA=\"-e\"\n" : "")
                .append("exec ").append(REMOTE_DIR).append("/inject -p \"$PID\" -s ")
                .append(REMOTE_DIR).append("/script.js $EXTRA\n");
        try (FileOutputStream fos = new FileOutputStream(runSh)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return runSh;
    }

    private void stopInjection(boolean killRemote) {
        if (process != null) {
            process.destroy();
        }
        if (killRemote) {
            // frida-inject runs as root: our destroy() only kills the su shell.
            try {
                Process pkill = Runtime.getRuntime().exec(new String[]{
                        RootManager.getInstance(this).suBinary(), "-c",
                        "pkill -f " + REMOTE_DIR + "/inject"});
                pkill.waitFor();
            } catch (Exception ignored) {
            }
        }
        setRunning(false);
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    private void appendConsole(String text) {
        runOnUiThread(() -> {
            console.append(text);
            consoleScroll.post(() -> consoleScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        stopInjection(eternalSwitch == null || !eternalSwitch.isChecked());
        super.onDestroy();
    }
}
