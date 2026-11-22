package io.github.abdurazaaqmohammed.feature.system;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.NonNull;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import androidx.preference.PreferenceManager;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.util.ThemeDialogs;
import io.github.abdurazaaqmohammed.ui.main.SidebarAdapter;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHooks;
import io.github.abdurazaaqmohammed.plugins.ipc.HookRegistry;
import io.github.abdurazaaqmohammed.plugins.ipc.HookOrder;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.abdurazaaqmohammed.plugins.packs.PackPrompts;
import io.github.abdurazaaqmohammed.plugins.res.PackRes;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionIcons;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ToolsHubActivity extends BaseActivity {
    private RecyclerView grid;
    private EditText searchInput;
    private ToolAdapter adapter;
    private List<ToolRegistry.ToolItem> allTools = new ArrayList<>();
    private List<PackDescriptor> catalog = new ArrayList<>();
    private MaterialToolbar toolbar;
    private String currentQuery = "";
    private final Set<String> expandedPacks = new HashSet<>();
    private final List<PluginHost.ExternalPlugin> installedExternal = new ArrayList<>();
    private TextView emptyState;
    private int spanCount;

    private record PackTool(PackDescriptor pack, PackDescriptor.ToolMeta tool) {}
    private record Header(String title, int count) {}

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools_hub);
        toolbar = findViewById(R.id.tools_toolbar);
        toolbar.setTitle(R.string.sidebar_tools);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, R.string.tools_hub_theme).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add(0, 2, 1, R.string.tools_hub_refresh_packs).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add(0, 3, 2, R.string.plugin_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 2) {
                refreshCatalog();
                return true;
            }
            if (item.getItemId() == 3) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.plugin_info)
                        .setMessage(R.string.plugin_infos).setPositiveButton(android.R.string.ok, null).show();
                return true;
            }
            ThemeDialogs.showThemeChooser(ToolsHubActivity.this);
            return true;
        });
        searchInput = findViewById(R.id.tools_search);
        emptyState = findViewById(R.id.tools_empty);
        grid = findViewById(R.id.tools_grid);
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        spanCount = Math.max(1, Math.min(5, (int) (widthDp / (164 * fontScale))));
        GridLayoutManager layout = new GridLayoutManager(this, spanCount);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            public int getSpanSize(int position) {
                int type = adapter == null ? 0 : adapter.getItemViewType(position);
                return type == 1 || type == 4 ? 1 : spanCount;
            }
        });
        grid.setLayoutManager(layout);
        allTools = ToolRegistry.visibleTools(this);
        if (savedInstanceState != null) {
            ArrayList<String> expanded = savedInstanceState.getStringArrayList("expanded_packs");
            if (expanded != null) expandedPacks.addAll(expanded);
        }
        adapter = new ToolAdapter(displayRows());
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                updateRows();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        catalog = PackCatalog.load(this);
        rebuildPacks();
        String requested = getIntent().getStringExtra(ToolLauncher.EXTRA_HUB_TOOL);
        if (requested != null) {
            getIntent().removeExtra(ToolLauncher.EXTRA_HUB_TOOL);
            grid.post(() -> ToolLauncher.open(this, requested));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildPacks();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putStringArrayList("expanded_packs", new ArrayList<>(expandedPacks));
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 11 || resultCode != RESULT_OK || data == null) return;
        String path = data.getStringExtra("dirToLoad");
        if (path == null && data.getData() != null) path = data.getData().getPath();
        if (path != null) startActivity(new Intent(this, MainActivity.class)
                .putExtra("locatePath", path)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void refreshCatalog() {
        Toast.makeText(this, getString(R.string.tools_hub_refreshing), Toast.LENGTH_SHORT).show();
        PackCatalog.refreshAsync(this, fresh -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (fresh != null && !fresh.isEmpty()) {
                catalog = PackCatalog.load(this);
                Toast.makeText(this, getString(R.string.tools_hub_updated), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.tools_hub_refresh_failed), Toast.LENGTH_SHORT).show();
            }
            rebuildPacks();
        }));
    }

    /** Ready-to-use tools first; optional downloads and plugin management below. */
    private List<Object> displayRows() {
        List<Object> tools = new ArrayList<>();
        for (ToolRegistry.ToolItem tool : allTools) {
            if (matches(tool.id(), tool.title(), tool.subtitle(), tool.category())) tools.add(tool);
        }
        for (PackDescriptor pack : catalog) {
            if (!PackManager.isInstalled(this, pack.id)) continue;
            for (PackDescriptor.ToolMeta tool : pack.tools) {
                if (ToolRegistry.findById(this, tool.id) != null) continue;
                if (matches(tool.id, packToolTitle(tool), packToolSubtitle(tool), pack.title,
                        ToolRegistry.localizeCategory(this, pack.category))) {
                    tools.add(new PackTool(pack, tool));
                }
            }
        }
        for (ToolPlugin plugin : PluginRegistry.customPlugins()) {
            if (ToolRegistry.findById(this, plugin.id()) != null
                    || PackCatalog.packForTool(catalog, plugin.id()) != null) continue;
            if (matches(plugin.id(), plugin.title(this), plugin.subtitle(this), plugin.category(this))) tools.add(plugin);
        }
        for (SidebarAction extension : ExtensionRegistry.sidebarActions()) {
            if (matches(extension.id(), extension.title())) tools.add(extension);
        }
        List<Object> rows = buildRows(tools);
        List<PackDescriptor> filteredPacks = new ArrayList<>();
        for (PackDescriptor pack : catalog) {
            boolean matching = matches(pack.id, pack.title, pack.description,
                    ToolRegistry.localizeCategory(this, pack.category));
            for (PackDescriptor.ToolMeta tool : pack.tools) {
                matching |= matches(tool.id, packToolTitle(tool), packToolSubtitle(tool));
            }
            if (matching) filteredPacks.add(pack);
        }
        if (!filteredPacks.isEmpty()) {
            rows.add(new Header(getString(R.string.tools_hub_tool_packs), filteredPacks.size()));
            rows.addAll(filteredPacks);
        }
        List<PluginHost.ExternalPlugin> external = externalPlugins();
        if (!external.isEmpty()) {
            rows.add(new Header(getString(R.string.tools_hub_advanced_plugins), external.size()));
            rows.addAll(external);
        }
        return rows;
    }

    private boolean matches(String... values) {
        String query = currentQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private void updateRows() {
        List<Object> rows = displayRows();
        adapter.setRows(rows);
        emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** External plugins installed on the device, deduped by package. */
    private List<PluginHost.ExternalPlugin> externalPlugins() {
        List<PluginHost.ExternalPlugin> out = new ArrayList<>();
        for (PluginHost.ExternalPlugin plugin : installedExternal) {
            if (matches(String.valueOf(plugin.label), plugin.packageName)) out.add(plugin);
        }
        return out;
    }

    private void refreshExternalPlugins() {
        installedExternal.clear();
        try {
            String[] actions = {
                    PluginContracts.ACTION_SIDEBAR_OPEN,
                    PluginContracts.ACTION_SETTING_CONFIG,
                    PluginContracts.ACTION_FILE_MENU,
                    PluginContracts.ACTION_EDITOR,
                    PluginContracts.ACTION_APK,
                    PluginContracts.ACTION_HOOK};
            for (String action : actions) {
                for (PluginHost.ExternalPlugin p : PluginHost.queryIncludingDisabled(this, action)) {
                    addExternalPlugin(p);
                }
            }
            for (PluginHost.ExternalPlugin receiver : PluginHost.queryEventReceivers(this, true)) {
                addExternalPlugin(receiver);
            }
        } catch (Exception ignored) {
        }
    }

    private void addExternalPlugin(PluginHost.ExternalPlugin plugin) {
        for (PluginHost.ExternalPlugin installed : installedExternal) {
            if (installed.packageName.equals(plugin.packageName)) return;
        }
        installedExternal.add(plugin);
    }

    private List<String> externalEvents(String packageName) {
        List<String> events = new ArrayList<>();
        for (PluginHost.ExternalPlugin receiver : PluginHost.queryEventReceivers(this, true)) {
            if (!packageName.equals(receiver.packageName)
                    || !HookRegistry.supportsVersion(receiver)) continue;
            for (String hook : PluginHooks.parse(
                    io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions.metaString(
                            receiver, PluginContracts.META_HOOKS, ""))) {
                if (PluginHooks.isEvent(hook) && !events.contains(hook)) events.add(hook);
            }
        }
        return events;
    }

    private void showExternalDialog(PluginHost.ExternalPlugin ext) {
        try {
            boolean trusted = PluginHost.isTrusted(this, ext.packageName);
            String digest = PluginHost.certDigest(this, ext.packageName);
            StringBuilder msg = new StringBuilder();
            msg.append(ext.packageName);
            msg.append("\n\n").append(getString(R.string.tools_hub_certificate)).append("\n").append(shortDigest(digest));
            msg.append("\n\n").append(getString(R.string.tools_hub_status)).append(" ")
                    .append(getString(trusted ? R.string.tools_hub_trusted : R.string.tools_hub_not_trusted));
            AlertDialog.Builder builder =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(String.valueOf(ext.label))
                            .setMessage(msg.toString())
                            .setNegativeButton(android.R.string.cancel, null)
                            .setNeutralButton(R.string.tools_hub_app_info, (d, w) -> {
                                try {
                                    Intent info = new Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + ext.packageName));
                                    startActivity(info);
                                } catch (Exception ignored) {
                                }
                            });
            if (trusted) {
                builder.setPositiveButton(R.string.tools_hub_disable, (d, w) -> {
                    PluginHost.setTrusted(this, ext.packageName,
                            PluginHost.pinnedDigest(this, ext.packageName),
                            String.valueOf(ext.label), false);
                    rebuildPacks();
                });
            } else {
                builder.setPositiveButton(R.string.tools_hub_trust, (d, w) -> {
                    String fresh = PluginHost.certDigest(this, ext.packageName);
                    if (fresh != null && fresh.equals(digest)) {
                        PluginHost.setTrusted(this, ext.packageName, fresh,
                                String.valueOf(ext.label), true);
                        rebuildPacks();
                    }
                });
            }
            boolean hasHooks = !HookRegistry.packageEntries(this, ext.packageName).isEmpty();
            List<String> events = externalEvents(ext.packageName);
            if (hasHooks || !events.isEmpty()) {
                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.VERTICAL);
                int padding = Math.round(24 * getResources().getDisplayMetrics().density);
                controls.setPadding(padding, 0, padding, 0);
                if (hasHooks) {
                    MaterialButton actions = new MaterialButton(this);
                    actions.setText(R.string.plugin_hook_actions);
                    actions.setOnClickListener(v -> HookOrder.showManager(this, ext.packageName));
                    controls.addView(actions);
                }
                if (!events.isEmpty()) {
                    MaterialButton notifications = new MaterialButton(this);
                    notifications.setText(PluginHost.eventsEnabled(this, ext.packageName)
                            ? R.string.plugin_events_disable : R.string.plugin_events_enable);
                    notifications.setOnClickListener(v -> showEventConsent(ext, events));
                    controls.addView(notifications);
                }
                builder.setView(controls);
            }
            builder.show();
        } catch (Exception ignored) {
        }
    }

    private void showEventConsent(PluginHost.ExternalPlugin ext, List<String> events) {
        if (PluginHost.eventsEnabled(this, ext.packageName)) {
            PluginHost.setEventsEnabled(this, ext.packageName, false);
            rebuildPacks();
            return;
        }
        if (!PluginHost.isTrusted(this, ext.packageName)) {
            Toast.makeText(this, R.string.plugin_events_trust_first, Toast.LENGTH_SHORT).show();
            return;
        }
        String digest = PluginHost.certDigest(this, ext.packageName);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.plugin_events_enable)
                .setMessage(getString(R.string.plugin_events_consent,
                        ext.packageName, String.join(", ", events)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (digest != null && digest.equals(PluginHost.certDigest(this, ext.packageName))) {
                        PluginHost.setEventsEnabled(this, ext.packageName, true, events);
                        rebuildPacks();
                    }
                }).show();
    }

    private String shortDigest(String hex) {
        if (hex == null || hex.isEmpty()) return getString(R.string.tools_hub_cert_unavailable);
        if (hex.length() <= 32) return hex;
        return hex.substring(0, 16) + "…" + hex.substring(hex.length() - 8);
    }

    private void rebuildPacks() {
        catalog = PackCatalog.load(this);
        allTools = ToolRegistry.visibleTools(this);
        refreshExternalPlugins();
        int installedPacks = 0;
        int installedTools = 0;
        for (PackDescriptor pack : catalog) {
            if (PackManager.isInstalled(this, pack.id)) {
                installedPacks++;
                for (PackDescriptor.ToolMeta tool : pack.tools) {
                    if (ToolRegistry.findById(this, tool.id) == null) installedTools++;
                }
            }
        }
        toolbar.setSubtitle(getString(R.string.tools_hub_summary_compact, allTools.size() + installedTools,
                installedPacks));
        if (adapter != null) {
            updateRows();
        }
    }

    private void bindPackCard(View card, PackDescriptor pack) {
        boolean installed = PackManager.isInstalled(this, pack.id);
        TextView title = card.findViewById(R.id.pack_title);
        title.setText(pack.title);
        boolean update = installed && pack.version > PackManager.installedVersion(this, pack.id);
        TextView metadata = card.findViewById(R.id.pack_metadata);
        metadata.setText(getString(R.string.tools_hub_pack_metadata, pack.tools.size(), pack.versionName,
                getString(update ? R.string.tools_hub_pack_update_available : installed
                        ? R.string.tools_hub_pack_installed : R.string.tools_hub_pack_available)));
        boolean expanded = expandedPacks.contains(pack.id);
        ImageButton expand = card.findViewById(R.id.pack_expand);
        expand.setImageResource(expanded
                ? R.drawable.arrow_drop_up_24px : R.drawable.arrow_drop_down_24px);
        expand.setContentDescription(getString(expanded ? R.string.sidebar_collapse : R.string.plugin_expand));
        View.OnClickListener toggle = v -> {
            if (expandedPacks.contains(pack.id)) expandedPacks.remove(pack.id);
            else expandedPacks.add(pack.id);
            bindPackCard(card, pack);
        };
        expand.setOnClickListener(toggle);
        card.setOnClickListener(toggle);
        TextView desc = card.findViewById(R.id.pack_description);
        desc.setText(pack.description);
        desc.setMaxLines(expanded ? Integer.MAX_VALUE : 2);
        MaterialButton action = card.findViewById(R.id.pack_action);
        action.setText(getString(installed
                ? (pack.version > PackManager.installedVersion(this, pack.id) ? R.string.tools_hub_update : R.string.tools_hub_open)
                : R.string.tools_hub_get));
        action.setEnabled(pack.supportsDevice() && !PackPrompts.isDownloading(pack.id));
        if (!pack.supportsDevice()) metadata.setText(getString(R.string.tools_hub_min_sdk, pack.minSdk()));

        LinearLayout toolsBox = card.findViewById(R.id.pack_tools);
        toolsBox.removeAllViews();
        toolsBox.setVisibility(expanded ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;
        if (expanded) for (PackDescriptor.ToolMeta tool : pack.tools) {
            toolsBox.addView(packToolRow(pack, tool, installed, density));
        }

        MaterialButton remove = card.findViewById(R.id.pack_remove);
        if (installed && expanded) {
            remove.setVisibility(View.VISIBLE);
            remove.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(pack.title)
                        .setMessage(getString(R.string.tools_hub_remove_msg, pack.tools.size()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.tools_hub_remove, (d, w) -> {
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
                if (pack.tools.size() == 1) openPackTool(pack, pack.tools.get(0));
                else {
                    expandedPacks.add(pack.id);
                    bindPackCard(card, pack);
                }
                return;
            }
            PackPrompts.downloadPack(this, pack, this::rebuildPacks, action);
            action.setEnabled(!PackPrompts.isDownloading(pack.id));
        });
        card.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, card);
            menu.getMenu().add(R.string.tools_hub_install_apk);
            menu.setOnMenuItemClickListener(item -> {
                pickApkForPack(pack);
                return true;
            });
            menu.show();
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
        title.setText(packToolTitle(tool));
        title.setTextSize(14);
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText(packToolSubtitle(tool));
        sub.setTextSize(12);
        sub.setAlpha(0.6f);
        texts.addView(sub);
        row.addView(texts);
        MaterialButton open = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText(getString(R.string.plugin_open));
        open.setText(installed ? R.string.tools_hub_open : R.string.tools_hub_get);
        open.setEnabled(pack.supportsDevice() && !PackPrompts.isDownloading(pack.id));
        open.setOnClickListener(v -> {
            if (installed) openPackTool(pack, tool);
            else PackPrompts.downloadPack(this, pack, this::rebuildPacks);
        });
        row.addView(open);
        MaterialButton shortcut = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        shortcut.setIconResource(R.drawable.add_24px);
        shortcut.setIconPadding(0);
        shortcut.setIconSize((int) (20 * density));
        shortcut.setInsetTop(0);
        shortcut.setInsetBottom(0);
        shortcut.setMinWidth(0);
        shortcut.setMinimumWidth(0);
        shortcut.setMinHeight(0);
        shortcut.setMinimumHeight(0);
        shortcut.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
        String cs = getString(R.string.plugin_create_shortcut);
        shortcut.setContentDescription(cs);
        shortcut.setOnLongClickListener(v -> {
            Extensions.showMessage(this, cs);
            return false;
        });
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortcutParams.setMarginStart((int) (4 * density));
        shortcut.setLayoutParams(shortcutParams);
        shortcut.setOnClickListener(v -> ToolShortcuts.pin(this, tool));
        row.addView(shortcut);
        return row;
    }

    private void pickApkForPack(PackDescriptor pack) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.tools_hub_pick_apk, pack.id));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File picked = new File(files[0]);
            if (!picked.getName().toLowerCase().endsWith(".apk")) {
                Toast.makeText(this, getString(R.string.tools_hub_not_apk), Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                String error = PackManager.installFromFile(this, pack, picked);
                runOnUiThread(() -> {
                    if (error == null) {
                        Toast.makeText(this, getString(R.string.tools_hub_installed_toast, pack.title), Toast.LENGTH_SHORT).show();
                        rebuildPacks();
                    } else {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
        picker.show();
    }

    private void openPackTool(PackDescriptor pack, PackDescriptor.ToolMeta tool) {
        if (ToolLauncher.open(this, tool.id)) return;
        Intent intent = new Intent(this, ToolRunnerActivity.class);
        intent.putExtra("tool_id", tool.id);
        intent.putExtra("tool_title", packToolTitle(tool));
        startActivity(intent);
    }

    /** Localized tool title from the owning pack's strings.xml, else catalog text. */
    static String packToolTitle(PackDescriptor.ToolMeta tool) {
        return PackRes.str("title_" + tool.id, tool.title);
    }

    /** Localized tool subtitle from the owning pack's strings.xml, else catalog text. */
    static String packToolSubtitle(PackDescriptor.ToolMeta tool) {
        return PackRes.str("subtitle_" + tool.id, tool.subtitle);
    }

    private List<Object> buildRows(List<Object> items) {
        Map<String, List<Object>> grouped = new LinkedHashMap<>();
        for (String cat : ToolRegistry.categoriesInOrder(this)) grouped.put(cat, new ArrayList<>());
        for (Object item : items) {
            String category = item instanceof ToolRegistry.ToolItem tool ? tool.category()
                    : item instanceof PackTool tool ? ToolRegistry.localizeCategory(this, tool.pack.category)
                    : item instanceof ToolPlugin plugin ? plugin.category(this)
                    : getString(R.string.tools_hub_advanced_plugins);
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(item);
        }
        List<Object> rows = new ArrayList<>();
        for (Map.Entry<String, List<Object>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            rows.add(new Header(entry.getKey(), entry.getValue().size()));
            rows.addAll(entry.getValue());
        }
        return rows;
    }
    private void openTool(ToolRegistry.ToolItem item) {
        ToolLauncher.open(this, item.id());
    }

    /** Canonical ids of hub tools the user explicitly added to the sidebar. */
    private Set<String> addedSidebarTools() {
        return SidebarToolSelection.sanitizeAdded(PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet(SidebarToolSelection.PREF_ADDED_TOOLS, java.util.Collections.emptySet()));
    }

    private boolean isSidebarAdded(String sidebarId) {
        return SidebarToolSelection.isAdded(addedSidebarTools(), sidebarId);
    }

    /** True when the row carries the same hide key organize mode writes. */
    private boolean isSidebarHidden(String sidebarId) {
        Set<String> hidden = PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet("sidebar_hidden_items", java.util.Collections.emptySet());
        return hidden != null && hidden.contains(SidebarToolSelection.hiddenKey(sidebarId));
    }

    private void setHiddenSidebar(String sidebarId, boolean hidden) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> keys = new HashSet<>(prefs.getStringSet(
                "sidebar_hidden_items", java.util.Collections.emptySet()));
        String key = SidebarToolSelection.hiddenKey(sidebarId);
        if (hidden) keys.add(key);
        else keys.remove(key);
        prefs.edit().putStringSet("sidebar_hidden_items", keys).apply();
    }

    private boolean hasExplicitMembership(String sidebarId) {
        boolean registryTool = ToolRegistry.findById(this, sidebarId) != null;
        return SidebarToolSelection.membershipIsExplicit(sidebarId, registryTool,
                registryTool && ToolRegistry.isDefaultSidebarTool(sidebarId));
    }

    /** The state the sidebar actually renders (membership plus organize hides). */
    private boolean isSidebarPresent(String sidebarId) {
        boolean autoShown;
        if (ToolRegistry.findById(this, sidebarId) != null) {
            autoShown = ToolRegistry.isDefaultSidebarTool(sidebarId);
        } else if (sidebarId.startsWith(SidebarAdapter.PACK_TOOL_PREFIX)) {
            autoShown = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("sidebar_show_plugins", false);
        } else {
            autoShown = true; // custom rows need explicit adds; extensions always render
        }
        return SidebarToolSelection.isRendered(hasExplicitMembership(sidebarId),
                isSidebarAdded(sidebarId), autoShown, isSidebarHidden(sidebarId));
    }

    /**
     * Add/restore: clears the hide key and, for rows whose membership is
     * explicit (hub-only registry tools, pack and custom tools), keeps/adds
     * the added id — never a blind toggle.
     */
    private void addSidebarTool(String sidebarId) {
        if (hasExplicitMembership(sidebarId)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> added = addedSidebarTools();
            if (SidebarToolSelection.ensureAdded(added, sidebarId)) {
                prefs.edit().putStringSet(SidebarToolSelection.PREF_ADDED_TOOLS, added).apply();
            }
        }
        setHiddenSidebar(sidebarId, false);
    }

    /**
     * Remove: hides through the organize-hide key, so the row disappears
     * regardless of shipped defaults, the plugins setting or pack state;
     * membership and saved order stay intact for a later restore.
     */
    private void removeSidebarTool(String sidebarId) {
        setHiddenSidebar(sidebarId, true);
    }

    private void toggleSidebarPresence(String sidebarId) {
        if (isSidebarPresent(sidebarId)) removeSidebarTool(sidebarId);
        else addSidebarTool(sidebarId);
        Extensions.showMessage(this, getString(isSidebarPresent(sidebarId)
                ? R.string.sidebar_tool_added : R.string.sidebar_tool_removed));
    }

    /**
     * Long-press menu on a hub tool card: add/remove the sidebar entry and
     * (when supported) pin a launcher shortcut. {@code sidebarId} is the
     * sidebar row id — a canonical registry id, "packtool:<pack>.<tool>",
     * "custom:<pluginId>" or an extension action id. The label mirrors what
     * the sidebar renders: an organize-hidden row (or an un-added hub-only
     * tool) offers Add, a visible row offers Remove.
     */
    private void showToolMenu(View anchor, String sidebarId, Runnable shortcut) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (sidebarId != null) {
            menu.getMenu().add(0, 1, 0, isSidebarPresent(sidebarId)
                    ? R.string.sidebar_remove_tool : R.string.sidebar_add_tool);
        }
        if (shortcut != null) menu.getMenu().add(0, 2, 1, R.string.plugin_create_shortcut);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1 && sidebarId != null) {
                toggleSidebarPresence(sidebarId);
                return true;
            }
            if (item.getItemId() == 2 && shortcut != null) {
                shortcut.run();
                return true;
            }
            return false;
        });
        menu.show();
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
            if (row instanceof Header) return 0;
            if (row instanceof PluginHost.ExternalPlugin) return 4;
            return 1;
        }
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 2) {
                return new PackHolder(inflater.inflate(R.layout.item_pack, parent, false));
            }
            if (viewType == 0) {
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
            if (holder instanceof PackHolder) {
                bindPackCard(holder.itemView, (PackDescriptor) row);
            } else if (holder instanceof HeaderHolder) {
                Header header = (Header) row;
                ((HeaderHolder) holder).label.setText(header.title + "  ·  " + header.count);
            } else if (holder instanceof ToolViewHolder h
                    && row instanceof PluginHost.ExternalPlugin ext) {
                h.badge.setVisibility(View.GONE);
                h.card.setOnLongClickListener(null);
                try {
                    h.icon.setImageDrawable(h.card.getContext().getPackageManager()
                            .getApplicationIcon(ext.packageName));
                    ImageViewCompat.setImageTintList(h.icon, null);
                } catch (Exception ignored) {
                    h.icon.setImageResource(R.drawable.tools_24px);
                }
                ImageViewCompat.setImageTintList(h.icon, null);
                h.title.setText(String.valueOf(ext.label));
                boolean trusted = false;
                try {
                    trusted = PluginHost.isTrusted(h.card.getContext(), ext.packageName);
                } catch (Exception ignored) {
                }
                h.subtitle.setText(ext.packageName + "  •  " + ToolsHubActivity.this.getString(
                        trusted ? R.string.tools_hub_trusted : R.string.tools_hub_not_trusted));
                h.card.setOnClickListener(v -> showExternalDialog(ext));
            } else if (holder instanceof ToolViewHolder h && row instanceof PackTool entry) {
                ToolPlugin plugin = PluginRegistry.findCustom(entry.tool.id);
                h.icon.setImageResource(plugin == null ? R.drawable.tools_24px : plugin.iconRes(ToolsHubActivity.this));
                ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(
                        MaterialColors.getColor(h.card, android.R.attr.colorPrimary)));
                h.title.setText(packToolTitle(entry.tool));
                h.subtitle.setText(packToolSubtitle(entry.tool));
                h.badge.setVisibility(View.GONE);
                h.card.setOnClickListener(v -> openPackTool(entry.pack, entry.tool));
                h.card.setOnLongClickListener(v -> {
                    showToolMenu(v, SidebarAdapter.PACK_TOOL_PREFIX + entry.pack.id + "." + entry.tool.id,
                            () -> ToolShortcuts.pin(ToolsHubActivity.this, entry.tool));
                    return true;
                });
            } else if (holder instanceof ToolViewHolder h && row instanceof SidebarAction extension) {
                h.icon.setImageResource(ExtensionIcons.resId(ToolsHubActivity.this, extension.iconName(), R.drawable.tools_24px));
                ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(
                        MaterialColors.getColor(h.card, android.R.attr.colorPrimary)));
                h.title.setText(extension.title());
                h.subtitle.setText(extension.id());
                h.badge.setVisibility(View.GONE);
                h.card.setOnLongClickListener(v -> {
                    showToolMenu(v, extension.id(), null);
                    return true;
                });
                h.card.setOnClickListener(v -> extension.open(ToolsHubActivity.this));
            } else if (holder instanceof ToolViewHolder h && row instanceof ToolPlugin plugin) {
                h.icon.setImageResource(plugin.iconRes(ToolsHubActivity.this));
                ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(MaterialColors.getColor(h.card, android.R.attr.colorPrimary)));
                h.title.setText(plugin.title(ToolsHubActivity.this));
                h.subtitle.setText(plugin.subtitle(ToolsHubActivity.this));
                h.badge.setVisibility(View.GONE);
                h.card.setOnLongClickListener(v -> {
                    showToolMenu(v, SidebarToolSelection.CUSTOM_TOOL_PREFIX + plugin.id(),
                            () -> ToolShortcuts.pin(ToolsHubActivity.this, new ToolRegistry.ToolItem(
                                    plugin.id(), plugin.title(ToolsHubActivity.this),
                                    plugin.subtitle(ToolsHubActivity.this),
                                    plugin.iconRes(ToolsHubActivity.this),
                                    plugin.category(ToolsHubActivity.this))));
                    return true;
                });
                h.card.setOnClickListener(v -> startActivity(new Intent(ToolsHubActivity.this, ToolRunnerActivity.class)
                        .putExtra("tool_id", plugin.id()).putExtra("tool_title", plugin.title(ToolsHubActivity.this))));
            } else if (holder instanceof ToolViewHolder h) {
                ToolRegistry.ToolItem item = (ToolRegistry.ToolItem) row;
                h.icon.setImageResource(item.iconRes());
                ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(MaterialColors.getColor(h.card.getContext(), android.R.attr.colorPrimary, 0xFF000000)));
                h.title.setText(item.title());
                h.subtitle.setText(item.subtitle());
                boolean needsPack = "screen_ocr".equals(item.id())
                        && !PackManager.isInstalled(ToolsHubActivity.this, "ocr");
                h.badge.setText(R.string.tools_hub_download_badge);
                h.badge.setVisibility(needsPack ? View.VISIBLE : View.GONE);
                h.card.setOnClickListener(v -> openTool(item));
                h.card.setOnLongClickListener(v -> {
                    showToolMenu(v, item.id(), () -> ToolShortcuts.pin(ToolsHubActivity.this, item));
                    return true;
                });
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
        final TextView badge;
        ToolViewHolder(@NonNull View itemView, ImageView icon, TextView title, TextView subtitle) {
            super(itemView);
            this.card = (MaterialCardView) itemView;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = itemView.findViewById(R.id.tool_badge);
        }
    }
}
