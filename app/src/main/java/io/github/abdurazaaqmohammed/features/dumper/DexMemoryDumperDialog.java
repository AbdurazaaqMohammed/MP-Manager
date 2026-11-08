package io.github.abdurazaaqmohammed.features.dumper;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Lists running processes and dumps unpacked in-memory DEX files
 * directly from process memory (/proc/[pid]/mem and vdex files).
 */
public final class DexMemoryDumperDialog {

    public record ProcessItem(int pid, String processName, String label, Drawable icon, boolean isApp) {}

    public static void show(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        RootManager rm = RootManager.getInstance(activity);
        if (!rm.isRootAvailable()) {
            Extensions.showMessage(activity, activity.getString(R.string.dex_dumper_root_required));
            return;
        }

        ProgressManager pm = new ProgressManager(activity, true);
        pm.setText(activity.getString(R.string.loading));
        pm.show();

        new Thread(() -> {
            List<ProcessItem> processes = loadRunningProcesses(activity);
            new Handler(Looper.getMainLooper()).post(() -> {
                pm.dismiss();
                if (!activity.isFinishing()) {
                    showProcessListDialog(activity, processes);
                }
            });
        }).start();
    }

    private static List<ProcessItem> loadRunningProcesses(Context context) {
        List<ProcessItem> list = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        RootManager rm = RootManager.getInstance(context);
        RootManager.ShellResult result = rm.execute("ps -A -o PID,NAME,USER", 10);
        if (result == null || !result.isSuccess() || result.output() == null) {
            return list;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(result.output()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("PID")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        int pid = Integer.parseInt(parts[0]);
                        String name = parts[1];
                        if (name.startsWith("[") && name.endsWith("]")) continue; // kernel threads

                        String label = name;
                        Drawable icon = null;
                        boolean isApp = false;

                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(name, 0);
                            label = pm.getApplicationLabel(ai).toString();
                            icon = pm.getApplicationIcon(ai);
                            isApp = true;
                        } catch (Exception ignored) {
                        }

                        list.add(new ProcessItem(pid, name, label, icon, isApp));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Sort: user apps first, then alphabetically
        Collections.sort(list, (a, b) -> {
            if (a.isApp != b.isApp) return a.isApp ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });

        return list;
    }

    private static void showProcessListDialog(AppCompatActivity activity, List<ProcessItem> allProcesses) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 16);

        EditText search = new EditText(activity);
        search.setHint(R.string.search_processes);
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView rv = new RecyclerView(activity);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800));

        List<ProcessItem> displayed = new ArrayList<>(allProcesses);
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        ProcessAdapter adapter = new ProcessAdapter(displayed, item -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            confirmAndDumpProcess(activity, item);
        });
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                displayed.clear();
                for (ProcessItem pi : allProcesses) {
                    if (q.isEmpty() || pi.label.toLowerCase(Locale.ROOT).contains(q)
                            || pi.processName.toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(pi.pid).contains(q)) {
                        displayed.add(pi);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogHolder[0] = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dex_dumper_select_app)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void confirmAndDumpProcess(AppCompatActivity activity, ProcessItem item) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.dump_dex) + ": " + item.label)
                .setMessage("PID: " + item.pid + "\nPackage: " + item.processName + "\n\n" + activity.getString(R.string.dex_dumper_dumping))
                .setPositiveButton(R.string.dump_dex, (d, w) -> executeMemoryDump(activity, item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void executeMemoryDump(AppCompatActivity activity, ProcessItem item) {
        ProgressManager pm = new ProgressManager(activity, true);
        pm.setText(activity.getString(R.string.dex_dumper_dumping));
        pm.show();

        new Thread(() -> {
            try {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File targetDir = new File(downloadDir, "DEX_DUMP_" + sanitizeName(item.processName) + "_" + item.pid);
                if (!targetDir.exists()) targetDir.mkdirs();

                int dumpedCount = dumpDexFromProcess(activity, item.pid, targetDir);

                new Handler(Looper.getMainLooper()).post(() -> {
                    pm.dismiss();
                    if (dumpedCount > 0) {
                        String msg = activity.getString(R.string.dex_dumper_success, dumpedCount, targetDir.getAbsolutePath());
                        Extensions.showMessage(activity, msg);
                        if (activity instanceof MainActivity ma) {
                            ma.refreshPane(true);
                        }
                    } else {
                        Extensions.showMessage(activity, activity.getString(R.string.dex_dumper_no_dex));
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    pm.dismiss();
                    new io.github.abdurazaaqmohammed.utils.ErrorUtil(activity).showError(e);
                });
            }
        }).start();
    }

    private static int dumpDexFromProcess(Context context, int pid, File targetDir) {
        RootManager rm = RootManager.getInstance(context);

        String script = 
                "python3 -c '\n" +
                "import os, sys, struct\n" +
                "pid = " + pid + "\n" +
                "out_dir = \"" + targetDir.getAbsolutePath() + "\"\n" +
                "count = 0\n" +
                "try:\n" +
                "    with open(f\"/proc/{pid}/maps\", \"r\") as f: lines = f.readlines()\n" +
                "except Exception:\n" +
                "    sys.exit(0)\n" +
                "try:\n" +
                "    with open(f\"/proc/{pid}/mem\", \"rb\", buffering=0) as mem:\n" +
                "        for line in lines:\n" +
                "            parts = line.split()\n" +
                "            if len(parts) < 5: continue\n" +
                "            addrs = parts[0].split(\"-\")\n" +
                "            start = int(addrs[0], 16)\n" +
                "            end = int(addrs[1], 16)\n" +
                "            perms = parts[1]\n" +
                "            path = parts[5] if len(parts) > 5 else \"[anon]\"\n" +
                "            if not perms.startswith(\"r\"): continue\n" +
                "            size = end - start\n" +
                "            if size > 150 * 1024 * 1024: continue\n" +
                "            try:\n" +
                "                mem.seek(start)\n" +
                "                head = mem.read(8)\n" +
                "                if head.startswith(b\"dex\\n\"):\n" +
                "                    mem.seek(start + 32)\n" +
                "                    fsize = struct.unpack(\"<I\", mem.read(4))[0]\n" +
                "                    if 100 < fsize <= size:\n" +
                "                        mem.seek(start)\n" +
                "                        data = mem.read(fsize)\n" +
                "                        count += 1\n" +
                "                        with open(f\"{out_dir}/dumped_classes_{count}.dex\", \"wb\") as out: out.write(data)\n" +
                "                elif head.startswith(b\"vdex\") or \"[anon\" in path or \".apk\" in path:\n" +
                "                    mem.seek(start)\n" +
                "                    buf = mem.read(min(size, 35 * 1024 * 1024))\n" +
                "                    idx = 0\n" +
                "                    while True:\n" +
                "                        pos = -1\n" +
                "                        for mag in [b\"dex\\n035\\x00\", b\"dex\\n037\\x00\", b\"dex\\n038\\x00\", b\"dex\\n039\\x00\"]:\n" +
                "                            pos = buf.find(mag, idx)\n" +
                "                            if pos != -1: break\n" +
                "                        if pos == -1: break\n" +
                "                        if pos + 40 <= len(buf):\n" +
                "                            fsize = struct.unpack(\"<I\", buf[pos+32:pos+36])[0]\n" +
                "                            if 100 < fsize <= (len(buf) - pos):\n" +
                "                                count += 1\n" +
                "                                with open(f\"{out_dir}/dumped_classes_{count}.dex\", \"wb\") as out: out.write(buf[pos:pos+fsize])\n" +
                "                        idx = pos + 8\n" +
                "            except Exception: pass\n" +
                "except Exception: pass\n" +
                "print(f\"DUMPED:{count}\")\n" +
                "' 2>/dev/null";

        // Try execution via root with fallback to awk/dd if python3 is missing
        RootManager.ShellResult res = rm.execute("LD_LIBRARY_PATH=/data/user/0/com.agy/files/usr/lib " + script, 45);
        if (res.isSuccess() && res.output() != null && res.output().contains("DUMPED:")) {
            try {
                String numStr = res.output().substring(res.output().indexOf("DUMPED:") + 7).trim().split("\\s+")[0];
                int cnt = Integer.parseInt(numStr);
                if (cnt > 0) return cnt;
            } catch (Exception ignored) {}
        }

        // Pure shell fallback scanning maps for base.vdex and extracting via dd
        String fallback = 
                "out=\"" + targetDir.getAbsolutePath() + "\"; cnt=0; " +
                "vdex=$(grep 'base.vdex' /proc/" + pid + "/maps 2>/dev/null | head -n 1 | awk '{print $5}'); " +
                "if [ -n \"$vdex\" ] && [ -f \"$vdex\" ]; then " +
                "  cp -f \"$vdex\" \"$out/base.vdex\" 2>/dev/null; " +
                "  cnt=1; " +
                "fi; " +
                "echo \"FALLBACK:$cnt\"";
        RootManager.ShellResult fRes = rm.execute(fallback, 20);
        if (fRes.isSuccess() && fRes.output() != null && fRes.output().contains("FALLBACK:1")) {
            return 1;
        }

        File[] files = targetDir.listFiles();
        return files != null ? files.length : 0;
    }

    private static String sanitizeName(String s) {
        if (s == null) return "process";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.Holder> {
        private final List<ProcessItem> items;
        private final java.util.function.Consumer<ProcessItem> onClick;

        ProcessAdapter(List<ProcessItem> items, java.util.function.Consumer<ProcessItem> onClick) {
            this.items = items;
            this.onClick = onClick;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout itemLayout = new LinearLayout(parent.getContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(24, 20, 24, 20);

            ImageView iconView = new ImageView(parent.getContext());
            iconView.setId(android.R.id.icon);
            itemLayout.addView(iconView, new LinearLayout.LayoutParams(96, 96));

            LinearLayout textLayout = new LinearLayout(parent.getContext());
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setPadding(24, 0, 0, 0);

            TextView labelView = new TextView(parent.getContext());
            labelView.setId(android.R.id.text1);
            labelView.setTextSize(16);
            labelView.setTypeface(null, android.graphics.Typeface.BOLD);
            textLayout.addView(labelView);

            TextView subView = new TextView(parent.getContext());
            subView.setId(android.R.id.text2);
            subView.setTextSize(13);
            subView.setTextColor(Color.GRAY);
            textLayout.addView(subView);

            itemLayout.addView(textLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            itemLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(itemLayout);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ProcessItem pi = items.get(position);
            holder.labelView.setText(pi.label);
            holder.subView.setText("PID: " + pi.pid + " • " + pi.processName);
            if (pi.icon != null) {
                holder.iconView.setImageDrawable(pi.icon);
            } else {
                holder.iconView.setImageResource(pi.isApp ? R.drawable.ic_android_24 : R.drawable.terminal_24px);
            }
            holder.itemView.setOnClickListener(v -> onClick.accept(pi));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView iconView;
            final TextView labelView;
            final TextView subView;
            Holder(View v) {
                super(v);
                iconView = v.findViewById(android.R.id.icon);
                labelView = v.findViewById(android.R.id.text1);
                subView = v.findViewById(android.R.id.text2);
            }
        }
    }
}
