package io.github.abdurazaaqmohammed.features.payload;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.boot.BootInspectManager;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

/**
 * Material 3 interactive dialog for inspecting and extracting Android OTA payload.bin files.
 */
public final class PayloadDialog {

    private PayloadDialog() {
    }

    public static void showForPayload(MainActivity context, File payloadFile, boolean pane1) {
        if (payloadFile == null || !payloadFile.isFile()) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.extracting));

        new Thread(() -> {
            try {
                PayloadParser.PayloadInfo info = PayloadParser.parse(payloadFile);
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    displayDialog(context, payloadFile, info, pane1);
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Failed to parse payload: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void displayDialog(MainActivity context, File payloadFile,
                                      PayloadParser.PayloadInfo info, boolean pane1) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 14);
        root.setPadding(pad, pad, pad, pad);

        // Header summary
        TextView summary = new TextView(context);
        summary.setText(context.getString(R.string.payload_partitions_count,
                info.partitions.size(), FileSize.getHumanReadableFileSize(info.getTotalUncompressedSize())));
        summary.setTextSize(13f);
        summary.setPadding(0, 0, 0, Extensions.dp2px(context, 8));
        root.addView(summary);

        // Search filter
        EditText searchInput = new EditText(context);
        searchInput.setHint(R.string.payload_search_hint);
        searchInput.setTextSize(14f);
        root.addView(searchInput);

        // Partitions list with FastScroller
        List<PayloadEntry> allItems = new ArrayList<>(info.partitions);
        List<PayloadEntry> filtered = new ArrayList<>(allItems);

        FastScrollerRecyclerView recyclerView = new FastScrollerRecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Extensions.dp2px(context, 380)));

        class PartitionViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;

            PartitionViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(android.R.id.icon);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }

        RecyclerView.Adapter<PartitionViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public PartitionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout itemLayout = new LinearLayout(context);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                int pVer = Extensions.dp2px(context, 8);
                int pHor = Extensions.dp2px(context, 8);
                itemLayout.setPadding(pHor, pVer, pHor, pVer);
                itemLayout.setBackgroundResource(android.R.drawable.list_selector_background);

                ImageView iconView = new ImageView(context);
                iconView.setId(android.R.id.icon);
                int iconSize = Extensions.dp2px(context, 32);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.rightMargin = Extensions.dp2px(context, 10);
                iconView.setLayoutParams(iconLp);
                iconView.setImageResource(R.drawable.ic_disk_drive_24px);
                iconView.setColorFilter(0xFF455A64);
                itemLayout.addView(iconView);

                LinearLayout textContainer = new LinearLayout(context);
                textContainer.setOrientation(LinearLayout.VERTICAL);
                textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView t1 = new TextView(context);
                t1.setId(android.R.id.text1);
                t1.setTextSize(14f);
                t1.setTypeface(null, android.graphics.Typeface.BOLD);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                t1.setMaxLines(1);

                TextView t2 = new TextView(context);
                t2.setId(android.R.id.text2);
                t2.setTextSize(12f);
                t2.setAlpha(0.68f);

                textContainer.addView(t1);
                textContainer.addView(t2);
                itemLayout.addView(textContainer);

                return new PartitionViewHolder(itemLayout);
            }

            @Override
            public void onBindViewHolder(@NonNull PartitionViewHolder holder, int position) {
                PayloadEntry item = filtered.get(position);
                holder.title.setText(item.getFileName());

                String comp = String.join(", ", item.compressionTypes);
                String sub = item.getDisplaySize() + (comp.isEmpty() ? "" : (" • " + comp))
                        + " • " + item.operations.size() + " ops";
                holder.subtitle.setText(sub);

                holder.itemView.setOnClickListener(v -> showPartitionActionDialog(context, payloadFile, info, item, pane1));
            }

            @Override
            public int getItemCount() {
                return filtered.size();
            }
        };

        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                if (q.isEmpty()) {
                    filtered.addAll(allItems);
                } else {
                    for (PayloadEntry item : allItems) {
                        if (item.name.toLowerCase(Locale.ROOT).contains(q)) {
                            filtered.add(item);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(payloadFile.getName())
                .setIcon(R.drawable.ic_disk_drive_24px)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.payload_extract_all, (d, which) -> {
                    extractAllPartitions(context, payloadFile, info);
                })
                .setNeutralButton("Info", (d, which) -> {
                    showPayloadInfo(context, payloadFile, info);
                })
                .create();

        new DialogUtil(context).styleAlertDialog(dialog);
    }

    private static void showPartitionActionDialog(MainActivity context, File payloadFile,
                                                  PayloadParser.PayloadInfo info, PayloadEntry entry,
                                                  boolean pane1) {
        List<String> options = new ArrayList<>();
        options.add(context.getString(R.string.payload_extract_partition));
        if (entry.isBootTypeImage()) {
            options.add(context.getString(R.string.payload_open_boot_inspector));
        }
        options.add(context.getString(R.string.payload_copy_hash));

        new MaterialAlertDialogBuilder(context)
                .setTitle(entry.getFileName() + " (" + entry.getDisplaySize() + ")")
                .setIcon(R.drawable.ic_disk_drive_24px)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    int idx = 0;
                    if (which == idx++) {
                        // Extract partition
                        extractSinglePartition(context, payloadFile, info, entry, false, pane1);
                    } else if (entry.isBootTypeImage() && which == idx++) {
                        // Extract and open Boot Inspector
                        extractSinglePartition(context, payloadFile, info, entry, true, pane1);
                    } else {
                        // Copy hash
                        String hash = entry.getSha256Hex();
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("SHA-256", hash));
                            Extensions.showMessage(context, R.string.flutter_copied);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void extractSinglePartition(MainActivity context, File payloadFile,
                                               PayloadParser.PayloadInfo info, PayloadEntry entry,
                                               boolean openInBootInspector, boolean pane1) {
        File outputDir = payloadFile.getParentFile();
        if (outputDir == null) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.payload_extracting, entry.getFileName()));

        new Thread(() -> {
            try {
                File extracted = PayloadExtractor.extractPartition(payloadFile, info, entry, outputDir, pm);
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, context.getString(R.string.payload_extract_success, extracted.getName()));
                    context.reloadCurrentFolder();
                    if (openInBootInspector && extracted.exists()) {
                        BootInspectManager.openBootImage(context, extracted, pane1);
                    }
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Extraction failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void extractAllPartitions(MainActivity context, File payloadFile,
                                             PayloadParser.PayloadInfo info) {
        File parent = payloadFile.getParentFile();
        if (parent == null) return;
        String baseName = payloadFile.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot != -1) baseName = baseName.substring(0, dot);
        File outputDir = new File(parent, baseName + "_extracted");

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Extracting all partitions…");

        new Thread(() -> {
            try {
                outputDir.mkdirs();
                int total = info.partitions.size();
                for (int i = 0; i < total; i++) {
                    if (pm.isCancelling()) break;
                    PayloadEntry entry = info.partitions.get(i);
                    final String name = entry.getFileName();
                    final int idx = i + 1;
                    context.runOnUiThread(() -> pm.setText("Extracting [" + idx + "/" + total + "] " + name + "…"));
                    PayloadExtractor.extractPartition(payloadFile, info, entry, outputDir, pm);
                }
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, context.getString(R.string.payload_extract_all_success, total));
                    context.reloadCurrentFolder();
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, "Extraction error: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void showPayloadInfo(MainActivity context, File payloadFile,
                                        PayloadParser.PayloadInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("• File: ").append(payloadFile.getName()).append("\n");
        sb.append("• Size: ").append(FileSize.getHumanReadableFileSize(payloadFile.length())).append("\n");
        sb.append("• Total Uncompressed: ").append(FileSize.getHumanReadableFileSize(info.getTotalUncompressedSize())).append("\n");
        sb.append("• OTA Version: ").append(info.version).append(info.version >= 2 ? " (Full A/B OTA)" : " (Legacy)").append("\n");
        sb.append("• Block Size: ").append(info.blockSize).append(" bytes\n");
        sb.append("• Partitions: ").append(info.partitions.size()).append("\n");
        sb.append("• Manifest Size: ").append(FileSize.getHumanReadableFileSize(info.manifestSize));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Payload Manifest Info")
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
