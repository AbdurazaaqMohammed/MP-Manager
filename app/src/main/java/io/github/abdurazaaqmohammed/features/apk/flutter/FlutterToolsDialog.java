package io.github.abdurazaaqmohammed.features.apk.flutter;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.ZipCompat;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/**
 * Interactive UI dialog for inspecting, modding, bypassing SSL pinning,
 * and extracting strings/assets from Flutter apps and libraries.
 */
public final class FlutterToolsDialog {

    private FlutterToolsDialog() {
    }

    public static void showForApk(MainActivity context, File apkFile, String fileName) {
        if (apkFile == null || !apkFile.isFile()) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Analyzing Flutter app…");

        new Thread(() -> {
            FlutterInfo info = FlutterInfo.parseFromApk(apkFile);
            context.runOnUiThread(() -> {
                pm.dismiss();
                displayApkDialog(context, apkFile, fileName, info);
            });
        }).start();
    }

    public static void showForLibrary(MainActivity context, File libFile, String fileName) {
        if (libFile == null || !libFile.isFile()) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Analyzing library…");

        new Thread(() -> {
            FlutterInfo info = FlutterInfo.parseFromLibrary(libFile);
            context.runOnUiThread(() -> {
                pm.dismiss();
                displayLibraryDialog(context, libFile, fileName, info);
            });
        }).start();
    }

    private static void displayApkDialog(MainActivity context, File apkFile, String fileName, FlutterInfo info) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, pad, pad, pad);

        // Header info
        TextView title = new TextView(context);
        title.setText(fileName + " (" + FileSize.getHumanReadableFileSize(apkFile.length()) + ")");
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView details = new TextView(context);
        StringBuilder sb = new StringBuilder();
        if (!info.abis.isEmpty()) {
            sb.append("Architectures: ").append(String.join(", ", info.abis)).append("\n");
        }
        sb.append(context.getString(R.string.flutter_engine_version, truncate(info.engineCommit, 16))).append("\n");
        sb.append(context.getString(R.string.flutter_dart_version, truncate(info.dartSnapshotHash, 16))).append("\n");
        if (info.assetsCount > 0) {
            sb.append("Flutter assets: ").append(info.assetsCount).append(" files");
        }
        details.setText(sb.toString().trim());
        details.setPadding(0, Extensions.dp2px(context, 8), 0, Extensions.dp2px(context, 16));
        root.addView(details);

        // Actions
        String[] actions = {
                context.getString(R.string.flutter_ssl_unpin),
                context.getString(R.string.flutter_extract_strings),
                context.getString(R.string.flutter_extract_assets),
                context.getString(R.string.flutter_snapshot_info)
        };

        AlertDialog[] dialogHolder = new AlertDialog[1];

        ListView actionList = new ListView(context);
        actionList.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, actions));
        actionList.setOnItemClickListener((parent, view, position, id) -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            switch (position) {
                case 0: // SSL Unpinning
                    performSslBypassApk(context, apkFile);
                    break;
                case 1: // Extract strings
                    showStringsDialog(context, apkFile, true);
                    break;
                case 2: // Extract assets
                    extractAssets(context, apkFile);
                    break;
                case 3: // Snapshot info
                    showFullInfoDialog(context, info);
                    break;
            }
        });
        root.addView(actionList);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.flutter_tools_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private static void displayLibraryDialog(MainActivity context, File libFile, String fileName, FlutterInfo info) {
        boolean isEngine = fileName.equals(FlutterDetector.LIB_FLUTTER);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(fileName + " (" + FileSize.getHumanReadableFileSize(libFile.length()) + ")");
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView details = new TextView(context);
        if (isEngine) {
            details.setText(context.getString(R.string.flutter_engine_version, info.engineCommit));
        } else {
            details.setText(context.getString(R.string.flutter_dart_version, info.dartSnapshotHash) + "\nFeatures: " + info.dartFeatures);
        }
        details.setPadding(0, Extensions.dp2px(context, 8), 0, Extensions.dp2px(context, 16));
        root.addView(details);

        List<String> actions = new ArrayList<>();
        if (isEngine) {
            actions.add(context.getString(R.string.flutter_ssl_unpin));
        }
        actions.add(context.getString(R.string.flutter_extract_strings));
        actions.add(context.getString(R.string.flutter_snapshot_info));

        AlertDialog[] dialogHolder = new AlertDialog[1];
        ListView actionList = new ListView(context);
        actionList.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, actions));
        actionList.setOnItemClickListener((parent, view, position, id) -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            String chosen = actions.get(position);
            if (chosen.equals(context.getString(R.string.flutter_ssl_unpin))) {
                performSslBypassLibrary(context, libFile);
            } else if (chosen.equals(context.getString(R.string.flutter_extract_strings))) {
                showStringsDialog(context, libFile, false);
            } else {
                showFullInfoDialog(context, info);
            }
        });
        root.addView(actionList);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.flutter_tools_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private static void performSslBypassApk(MainActivity context, File apkFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.flutter_ssl_unpin));

        new Thread(() -> {
            try {
                File outApk = new File(apkFile.getParentFile(), apkFile.getName().replace(".apk", "_ssl_unpinned.apk"));
                int patched = FlutterSslPatcher.patchApk(apkFile, outApk, pm);
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    if (patched > 0) {
                        Extensions.showMessage(context, context.getString(R.string.flutter_ssl_unpin_success, outApk.getName()));
                        context.reloadCurrentFolder();
                    } else {
                        Extensions.showMessage(context, "No libflutter.so could be patched (already patched or unknown version)");
                    }
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Patch failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void performSslBypassLibrary(MainActivity context, File libFile) {
        try {
            FlutterSslPatcher.PatchResult res = FlutterSslPatcher.patchLibrary(libFile);
            if (res.success()) {
                Extensions.showMessage(context, context.getString(R.string.flutter_ssl_unpin_success, libFile.getName()) + "\n" + res.message());
            } else {
                Extensions.showMessage(context, "Patch failed: " + res.message());
            }
        } catch (Exception e) {
            Extensions.showMessage(context, "Error: " + e.getMessage());
        }
    }

    private static void showStringsDialog(MainActivity context, File targetFile, boolean isApk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Extracting strings…");

        new Thread(() -> {
            try {
                List<DartStringsExtractor.DartStringItem> items = isApk
                        ? DartStringsExtractor.extractFromApk(targetFile, 5)
                        : DartStringsExtractor.extractFromLibrary(targetFile, 5);

                context.runOnUiThread(() -> {
                    pm.dismiss();
                    displayStringsList(context, targetFile, items);
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Extraction error: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void displayStringsList(MainActivity context, File targetFile, List<DartStringsExtractor.DartStringItem> allItems) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 12);
        root.setPadding(pad, pad, pad, pad);

        EditText searchInput = new EditText(context);
        searchInput.setHint(R.string.flutter_search_hint);
        root.addView(searchInput);

        TextView countView = new TextView(context);
        countView.setText(context.getString(R.string.flutter_strings_count, allItems.size()));
        countView.setPadding(0, Extensions.dp2px(context, 4), 0, Extensions.dp2px(context, 4));
        root.addView(countView);

        List<DartStringsExtractor.DartStringItem> filtered = new ArrayList<>(allItems);
        ListView listView = new ListView(context);
        ArrayAdapter<DartStringsExtractor.DartStringItem> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_2, android.R.id.text1, filtered) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                DartStringsExtractor.DartStringItem item = getItem(position);
                if (item != null) {
                    t1.setText(item.text());
                    t2.setText(item.category());
                }
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Extensions.dp2px(context, 350)));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            DartStringsExtractor.DartStringItem item = filtered.get(position);
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Dart string", item.text()));
                Extensions.showMessage(context, R.string.flutter_copied);
            }
        });
        root.addView(listView);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                filtered.clear();
                if (q.isEmpty()) {
                    filtered.addAll(allItems);
                } else {
                    for (DartStringsExtractor.DartStringItem item : allItems) {
                        if (item.text().toLowerCase(java.util.Locale.ROOT).contains(q) || item.category().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                            filtered.add(item);
                        }
                    }
                }
                countView.setText(context.getString(R.string.flutter_strings_count, filtered.size()));
                adapter.notifyDataSetChanged();
            }
        });

        new AlertDialog.Builder(context)
                .setTitle(R.string.flutter_extract_strings)
                .setView(root)
                .setNeutralButton("Export .txt", (dialog, which) -> {
                    File out = new File(targetFile.getParentFile(), targetFile.getName() + "_strings.txt");
                    try {
                        DartStringsExtractor.exportToFile(filtered, out);
                        Extensions.showMessage(context, "Exported to " + out.getName());
                        context.reloadCurrentFolder();
                    } catch (Exception e) {
                        Extensions.showMessage(context, "Export failed: " + e.getMessage());
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void extractAssets(MainActivity context, File apkFile) {
        File outDir = new File(apkFile.getParentFile(), "flutter_assets_" + apkFile.getName().replace(".apk", ""));
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.flutter_extract_assets));

        new Thread(() -> {
            try {
                outDir.mkdirs();
                try (ZipFile zf = new ZipFile(apkFile)) {
                    for (FileHeader fh : zf.getFileHeaders()) {
                        String name = fh.getFileName();
                        if (name != null && name.startsWith(FlutterDetector.FLUTTER_ASSETS_DIR)) {
                            zf.extractFile(fh, outDir.getAbsolutePath());
                        }
                    }
                }
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Assets extracted to " + outDir.getName());
                    context.reloadCurrentFolder();
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Extract failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void showFullInfoDialog(MainActivity context, FlutterInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("• Engine Commit: \n  ").append(info.engineCommit).append("\n\n");
        sb.append("• Dart Snapshot Hash: \n  ").append(info.dartSnapshotHash).append("\n\n");
        if (!info.dartFeatures.isEmpty()) {
            sb.append("• Snapshot Features: \n  ").append(info.dartFeatures).append("\n\n");
        }
        if (!info.abis.isEmpty()) {
            sb.append("• Target ABIs: \n  ").append(String.join(", ", info.abis)).append("\n\n");
        }
        sb.append("• Assets: \n  ").append(info.assetsCount).append(" files (Manifest: ").append(info.hasManifest ? "yes" : "no").append(")");

        new AlertDialog.Builder(context)
                .setTitle(R.string.flutter_snapshot_info)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "Unknown";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
