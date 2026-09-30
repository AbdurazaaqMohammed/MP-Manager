package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeDialogs;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.abdurazaaqmohammed.plugins.packs.PackPrompts;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

public class ToolsHubActivity extends BaseActivity {
    private RecyclerView grid;
    private EditText searchInput;
    private ToolAdapter adapter;
    private List<ToolRegistry.ToolItem> allTools = new ArrayList<>();
    private List<PackDescriptor> catalog = new ArrayList<>();
    private MaterialToolbar toolbar;
    private String currentQuery = "";
    private final java.util.Set<String> expandedPacks = new java.util.HashSet<>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools_hub);
        toolbar = findViewById(R.id.tools_toolbar);
        toolbar.setSubtitle("Loading");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add("Theme").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add("Refresh packs").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if ("Refresh packs".contentEquals(item.getTitle())) {
                refreshCatalog();
                return true;
            }
            ThemeDialogs.showThemeChooser(ToolsHubActivity.this);
            return true;
        });
        searchInput = findViewById(R.id.tools_search);
        grid = findViewById(R.id.tools_grid);
        GridLayoutManager layout = new GridLayoutManager(this, 3);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            public int getSpanSize(int position) {
                return adapter != null && adapter.getItemViewType(position) == 1 ? 1 : 3;
            }
        });
        grid.setLayoutManager(layout);
        allTools = ToolRegistry.getTools(this);
        adapter = new ToolAdapter(displayRows());
        grid.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                adapter.setRows(displayRows());
            }
            public void afterTextChanged(Editable s) {
            }
        });
        catalog = PackCatalog.load(this);
        rebuildPacks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildPacks();
    }

    private void refreshCatalog() {
        UIKit.toast(this, "Refreshing pack catalog…");
        PackCatalog.refreshAsync(this, fresh -> runOnUiThread(() -> {
            if (fresh != null && !fresh.isEmpty()) {
                catalog = fresh;
                UIKit.toast(this, "Catalog updated");
            } else {
                UIKit.toast(this, "Could not refresh, keeping cached catalog");
            }
            rebuildPacks();
        }));
    }

    /** Single scrolling list: packs, built-in tools, then external plugins. */
    private List<Object> displayRows() {
        List<Object> rows = new ArrayList<>();
        rows.addAll(catalog);
        rows.addAll(buildRows(filterTools(currentQuery)));
        List<PluginHost.ExternalPlugin> external = externalPlugins();
        if (!external.isEmpty()) {
            rows.add(new ExtSection());
            rows.addAll(external);
        }
        rows.add(new DevSection());
        return rows;
    }

    /** External plugins installed on the device, deduped by package. */
    private List<PluginHost.ExternalPlugin> externalPlugins() {
        List<PluginHost.ExternalPlugin> out = new ArrayList<>();
        try {
            String[] actions = {
                    PluginContracts.ACTION_SIDEBAR_OPEN,
                    PluginContracts.ACTION_SETTING_CONFIG,
                    PluginContracts.ACTION_FILE_MENU,
                    PluginContracts.ACTION_EDITOR,
                    PluginContracts.ACTION_APK};
            for (String action : actions) {
                for (PluginHost.ExternalPlugin p : PluginHost.query(this, action)) {
                    boolean seen = false;
                    for (PluginHost.ExternalPlugin q : out) {
                        if (q.packageName.equals(p.packageName)) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) out.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        String q = currentQuery == null ? "" : currentQuery.trim().toLowerCase();
        if (!q.isEmpty()) {
            List<PluginHost.ExternalPlugin> filtered = new ArrayList<>();
            for (PluginHost.ExternalPlugin p : out) {
                String label = String.valueOf(p.label).toLowerCase();
                if (label.contains(q) || p.packageName.toLowerCase().contains(q)) {
                    filtered.add(p);
                }
            }
            return filtered;
        }
        return out;
    }

    private void showExternalDialog(PluginHost.ExternalPlugin ext) {
        try {
            boolean trusted = PluginHost.isTrusted(this, ext.packageName);
            String digest = PluginHost.certDigest(this, ext.packageName);
            StringBuilder msg = new StringBuilder();
            msg.append(ext.packageName);
            msg.append("\n\nCertificate (SHA-256):\n").append(shortDigest(digest));
            msg.append("\n\nStatus: ").append(trusted ? "Trusted" : "Not trusted");
            androidx.appcompat.app.AlertDialog.Builder builder =
                    UIKit.dialog(this)
                            .setTitle(String.valueOf(ext.label))
                            .setMessage(msg.toString())
                            .setNegativeButton(android.R.string.cancel, null)
                            .setNeutralButton("App info", (d, w) -> {
                                try {
                                    android.content.Intent info = new android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:" + ext.packageName));
                                    startActivity(info);
                                } catch (Exception ignored) {
                                }
                            });
            if (trusted) {
                builder.setPositiveButton("Disable", (d, w) -> {
                    PluginHost.setTrusted(this, ext.packageName,
                            PluginHost.pinnedDigest(this, ext.packageName),
                            String.valueOf(ext.label), false);
                    rebuildPacks();
                });
            } else {
                builder.setPositiveButton("Trust", (d, w) -> {
                    String fresh = PluginHost.certDigest(this, ext.packageName);
                    if (fresh != null) {
                        PluginHost.setTrusted(this, ext.packageName, fresh,
                                String.valueOf(ext.label), true);
                        rebuildPacks();
                    }
                });
            }
            builder.show();
        } catch (Exception ignored) {
        }
    }

    private static String shortDigest(String hex) {
        if (hex == null || hex.isEmpty()) return "(unavailable)";
        if (hex.length() <= 32) return hex;
        return hex.substring(0, 16) + "…" + hex.substring(hex.length() - 8);
    }

    private void rebuildPacks() {
        int installedPacks = 0;
        int installedTools = 0;
        for (PackDescriptor pack : catalog) {
            if (PackManager.isInstalled(this, pack.id)) {
                installedPacks++;
                installedTools += pack.tools.size();
            }
        }
        toolbar.setSubtitle(allTools.size() + " included · " + installedTools
                + " from " + installedPacks + "/" + catalog.size() + " packs");
        if (adapter != null) {
            adapter.setRows(displayRows());
        }
    }

    private void bindPackCard(View card, PackDescriptor pack) {
        boolean installed = PackManager.isInstalled(this, pack.id);
        TextView title = card.findViewById(R.id.pack_title);
        title.setText(pack.title + " (" + pack.tools.size() + ")  v" + pack.versionName);
        boolean expanded = expandedPacks.contains(pack.id);
        ImageButton expand = card.findViewById(R.id.pack_expand);
        expand.setImageResource(expanded
                ? R.drawable.arrow_drop_up_24px : R.drawable.arrow_drop_down_24px);
        android.view.View.OnClickListener toggle = v -> {
            if (expandedPacks.contains(pack.id)) expandedPacks.remove(pack.id);
            else expandedPacks.add(pack.id);
            bindPackCard(card, pack);
        };
        expand.setOnClickListener(toggle);
        card.setOnClickListener(toggle);
        TextView desc = card.findViewById(R.id.pack_description);
        desc.setText(pack.description);
        MaterialButton action = card.findViewById(R.id.pack_action);
        action.setText(installed
                ? (pack.version > PackManager.installedVersion(this, pack.id) ? "Update" : "Open")
                : "Get");

        LinearLayout toolsBox = card.findViewById(R.id.pack_tools);
        toolsBox.removeAllViews();
        toolsBox.setVisibility(expanded ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;
        for (PackDescriptor.ToolMeta tool : pack.tools) {
            toolsBox.addView(packToolRow(pack, tool, installed, density));
        }

        MaterialButton remove = card.findViewById(R.id.pack_remove);
        if (installed) {
            remove.setVisibility(View.VISIBLE);
            remove.setOnClickListener(v -> {
                UIKit.dialog(this)
                        .setTitle(pack.title)
                        .setMessage("Remove this pack and its " + pack.tools.size() + " tools?")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Remove", (d, w) -> {
                            PackManager.uninstallPack(this, pack.id);
                            rebuildPacks();
                        })
                        .show();
            });
        } else {
            remove.setVisibility(View.GONE);
            remove.setOnClickListener(null);
        }

        action.setOnClickListener(v -> {
            if (PackManager.isInstalled(this, pack.id)
                    && pack.version <= PackManager.installedVersion(this, pack.id)) {
                if (!pack.tools.isEmpty()) openPackTool(pack, pack.tools.get(0));
                return;
            }
            action.setEnabled(false);
            PackPrompts.downloadPack(this, pack, () -> rebuildPacks());
            action.setEnabled(true);
        });
        card.setOnLongClickListener(v -> {
            pickApkForPack(pack);
            return true;
        });
    }

    private View packToolRow(PackDescriptor pack, PackDescriptor.ToolMeta tool,
                             boolean installed, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pv = (int) (6 * density);
        row.setPadding(0, pv, 0, pv);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textsParams);
        TextView title = new TextView(this);
        title.setText(tool.title);
        title.setTextSize(14);
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText(tool.subtitle);
        sub.setTextSize(12);
        sub.setAlpha(0.6f);
        texts.addView(sub);
        row.addView(texts);
        MaterialButton open = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText(getString(R.string.plugin_open));
        open.setEnabled(installed);
        open.setOnClickListener(v -> openPackTool(pack, tool));
        row.addView(open);
        MaterialButton shortcut = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        shortcut.setIconResource(R.drawable.add_24px);
        shortcut.setIconPadding(0);
        shortcut.setContentDescription(getString(R.string.plugin_create_shortcut));
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortcutParams.setMarginStart((int) (4 * density));
        shortcut.setLayoutParams(shortcutParams);
        shortcut.setOnClickListener(v -> createToolShortcut(tool));
        row.addView(shortcut);
        return row;
    }

    private void createToolShortcut(PackDescriptor.ToolMeta tool) {
        if (android.os.Build.VERSION.SDK_INT < 26) {
            UIKit.toast(this, getString(R.string.plugin_shortcut_old_android));
            return;
        }
        try {
            android.content.pm.ShortcutManager sm =
                    getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                UIKit.toast(this, getString(R.string.plugin_shortcut_unsupported));
                return;
            }
            Intent intent = new Intent(this, ToolRunnerActivity.class);
            intent.setAction("io.github.abdurazaaqmohammed.MPManager.TOOL_" + tool.id);
            intent.putExtra("tool_id", tool.id);
            intent.putExtra("tool_title", tool.title);
            android.content.pm.ShortcutInfo info =
                    new android.content.pm.ShortcutInfo.Builder(this, "tool_" + tool.id)
                            .setShortLabel(tool.title)
                            .setLongLabel(tool.title + " — " + tool.subtitle)
                            .setIcon(android.graphics.drawable.Icon.createWithResource(
                                    this, R.drawable.tools_24px))
                            .setIntent(intent)
                            .build();
            sm.requestPinShortcut(info, null);
            UIKit.toast(this, getString(R.string.plugin_shortcut_done));
        } catch (Exception ignored) {
        }
    }

    private void pickApkForPack(PackDescriptor pack) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.root = android.os.Environment.getExternalStorageDirectory();
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Pick " + pack.id + ".apk");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File picked = new File(files[0]);
            if (!picked.getName().toLowerCase().endsWith(".apk")) {
                UIKit.toast(this, "Not an APK file");
                return;
            }
            new Thread(() -> {
                String error = PackManager.installFromFile(this, pack, picked);
                runOnUiThread(() -> {
                    if (error == null) {
                        UIKit.toast(this, pack.title + " installed");
                        rebuildPacks();
                    } else {
                        UIKit.toast(this, error, true);
                    }
                });
            }).start();
        });
        picker.show();
    }

    private void openPackTool(PackDescriptor pack, PackDescriptor.ToolMeta tool) {
        Intent intent = new Intent(this, ToolRunnerActivity.class);
        intent.putExtra("tool_id", tool.id);
        intent.putExtra("tool_title", tool.title);
        startActivity(intent);
    }

    private List<ToolRegistry.ToolItem> filterTools(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return new ArrayList<>(allTools);
        List<ToolRegistry.ToolItem> result = new ArrayList<>();
        for (ToolRegistry.ToolItem item : allTools) {
            if (item.title().toLowerCase().contains(q) || item.subtitle().toLowerCase().contains(q) || item.category().toLowerCase().contains(q)) {
                result.add(item);
            }
        }
        return result;
    }
    private List<Object> buildRows(List<ToolRegistry.ToolItem> items) {
        Map<String, List<ToolRegistry.ToolItem>> grouped = new LinkedHashMap<>();
        for (String cat : ToolRegistry.categoriesInOrder()) grouped.put(cat, new ArrayList<>());
        for (ToolRegistry.ToolItem item : items) {
            List<ToolRegistry.ToolItem> bucket = grouped.get(item.category());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(item.category(), bucket);
            }
            bucket.add(item);
        }
        List<Object> rows = new ArrayList<>();
        for (Map.Entry<String, List<ToolRegistry.ToolItem>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            rows.add(entry.getKey());
            rows.addAll(entry.getValue());
        }
        return rows;
    }
    private void openTool(ToolRegistry.ToolItem item) {
        if ("wifimanager".equals(item.id())) {
            startActivity(new Intent(this, WifiManagerActivity.class));
        } else if ("storagemanager".equals(item.id())) {
            startActivity(new Intent(this, StorageManagerActivity.class));
        } else {
            Intent intent = new Intent(this, ToolRunnerActivity.class);
            intent.putExtra("tool_id", item.id());
            intent.putExtra("tool_title", item.title());
            startActivity(intent);
        }
    }
    private class ToolAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> rows;
        ToolAdapter(List<Object> initial) {
            rows = initial;
        }
        void setRows(List<Object> next) {
            rows = next;
            notifyDataSetChanged();
        }
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof PackDescriptor) return 2;
            if (row instanceof ExtSection) return 3;
            if (row instanceof PluginHost.ExternalPlugin) return 4;
            if (row instanceof DevSection) return 5;
            return row instanceof String ? 0 : 1;
        }
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 2) {
                return new PackHolder(inflater.inflate(R.layout.item_pack, parent, false));
            }
            if (viewType == 5) {
                return new DevHolder(inflater.inflate(R.layout.item_dev_info, parent, false));
            }
            if (viewType == 0 || viewType == 3) {
                return new HeaderHolder(inflater.inflate(R.layout.item_tool_header, parent, false));
            }
            View card = inflater.inflate(R.layout.item_tool_grid, parent, false);
            return new ToolViewHolder(card,
                    card.findViewById(R.id.tool_icon),
                    card.findViewById(R.id.tool_title),
                    card.findViewById(R.id.tool_subtitle));
        }
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof DevHolder) {
                return;
            }
            if (holder instanceof PackHolder) {
                bindPackCard(holder.itemView, (PackDescriptor) row);
            } else if (holder instanceof HeaderHolder) {
                if (row instanceof ExtSection) {
                    int count = 0;
                    for (int i = position + 1; i < rows.size()
                            && rows.get(i) instanceof PluginHost.ExternalPlugin; i++) count++;
                    ((HeaderHolder) holder).label.setText("External plugins  (" + count + ")");
                } else {
                    String cat = (String) row;
                    int count = 0;
                    for (int i = position + 1; i < rows.size() && rows.get(i) instanceof ToolRegistry.ToolItem; i++) count++;
                    ((HeaderHolder) holder).label.setText(cat + "  (" + count + ")");
                }
            } else if (holder instanceof ToolViewHolder h
                    && row instanceof PluginHost.ExternalPlugin ext) {
                try {
                    h.icon.setImageDrawable(h.card.getContext().getPackageManager()
                            .getApplicationIcon(ext.packageName));
                    ImageViewCompat.setImageTintList(h.icon, null);
                } catch (Exception ignored) {
                    h.icon.setImageResource(R.drawable.tools_24px);
                }
                h.title.setText(String.valueOf(ext.label));
                boolean trusted = false;
                try {
                    trusted = PluginHost.isTrusted(h.card.getContext(), ext.packageName);
                } catch (Exception ignored) {
                }
                h.subtitle.setText(ext.packageName + "  •  " + (trusted ? "Trusted" : "Not trusted"));
                h.card.setOnClickListener(v -> showExternalDialog(ext));
            } else if (holder instanceof ToolViewHolder h) {
                ToolRegistry.ToolItem item = (ToolRegistry.ToolItem) row;
                h.icon.setImageResource(item.iconRes());
                ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(MaterialColors.getColor(h.card.getContext(), com.google.android.material.R.attr.colorPrimary, 0xFF000000)));
                h.title.setText(item.title());
                h.subtitle.setText(item.subtitle());
                h.card.setOnClickListener(v -> openTool(item));
            }
        }
        public int getItemCount() {
            return rows.size();
        }
    }
    private static class PackHolder extends RecyclerView.ViewHolder {
        PackHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
    /** Marker row starting the external-plugins section. */
    private static class ExtSection {
    }
    /** Marker row for the developer info card. */
    private static class DevSection {
    }
    private static class DevHolder extends RecyclerView.ViewHolder {
        DevHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView label;
        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            this.label = (TextView) itemView;
        }
    }
    private static class ToolViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        ToolViewHolder(@NonNull View itemView, ImageView icon, TextView title, TextView subtitle) {
            super(itemView);
            this.card = (MaterialCardView) itemView;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
