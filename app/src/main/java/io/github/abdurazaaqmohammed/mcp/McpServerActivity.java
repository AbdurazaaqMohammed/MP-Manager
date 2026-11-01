package io.github.abdurazaaqmohammed.mcp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Settings + control panel for the MCP service: start/stop, connect URL,
 * port, home folder, bearer-token auth and the ordered path rules that decide
 * what the AI may read or write (MT Manager style: home is always read/write,
 * everything else follows the rules).
 */
public class McpServerActivity extends BaseActivity {

    private TextView status;
    private TextView url;
    private TextView requestCount;
    private MaterialButton toggle;
    private EditText portInput;
    private EditText homeInput;
    private MaterialSwitch authSwitch;
    private TextView tokenView;
    private LinearLayout rulesContainer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcp_server);
        MaterialToolbar toolbar = findViewById(R.id.mcp_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(getString(R.string.mcp_regenerate_token))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);

        status = findViewById(R.id.mcpStatus);
        url = findViewById(R.id.mcpUrl);
        requestCount = findViewById(R.id.mcpRequestCount);
        toggle = findViewById(R.id.mcpToggle);
        portInput = findViewById(R.id.mcpPort);
        homeInput = findViewById(R.id.mcpHome);
        authSwitch = findViewById(R.id.mcpAuthSwitch);
        tokenView = findViewById(R.id.mcpToken);
        rulesContainer = findViewById(R.id.mcpRulesContainer);
        MaterialButton addRule = findViewById(R.id.mcpAddRule);
        MaterialButton apply = findViewById(R.id.mcpApply);

        portInput.setText(String.valueOf(McpPrefs.getPort(this)));
        homeInput.setText(McpPrefs.getHome(this).getPath());
        authSwitch.setChecked(McpPrefs.authEnabled(this));
        tokenView.setText(getString(R.string.mcp_token_value, McpPrefs.getToken(this)));

        url.setOnClickListener(v -> copy(url.getText().toString()));
        tokenView.setOnClickListener(v -> copy(McpPrefs.getToken(this)));

        toggle.setOnClickListener(v -> {
            if (McpServerService.isRunning()) {
                stopService(new Intent(this, McpServerService.class));
            } else {
                applyAndStart();
            }
            refreshStatus();
        });
        apply.setOnClickListener(v -> {
            applySettings();
            if (McpServerService.isRunning()) {
                // Restart so the new port/home/token/policy take effect.
                stopService(new Intent(this, McpServerService.class));
                startService(new Intent(this, McpServerService.class));
            }
            Extensions.showMessage(this, getString(R.string.mcp_settings_applied));
            refreshStatus();
        });
        addRule.setOnClickListener(v -> showAddRuleDialog());
        authSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            McpPrefs.setAuthEnabled(this, isChecked);
            tokenView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        tokenView.setVisibility(McpPrefs.authEnabled(this) ? View.VISIBLE : View.GONE);
    }

    private boolean onMenuItem(MenuItem item) {
        McpPrefs.regenerateToken(this);
        tokenView.setText(getString(R.string.mcp_token_value, McpPrefs.getToken(this)));
        Extensions.showMessage(this, getString(R.string.mcp_token_regenerated));
        return true;
    }

    private void applyAndStart() {
        applySettings();
        ContextCompat.startForegroundService(this, new Intent(this, McpServerService.class));
    }

    private void applySettings() {
        try {
            int port = Integer.parseInt(portInput.getText().toString().trim());
            if (port < 1024 || port > 65535) throw new NumberFormatException();
            McpPrefs.setPort(this, port);
        } catch (NumberFormatException ignored) {
        }
        String home = homeInput.getText().toString().trim();
        if (!home.isEmpty()) {
            McpPrefs.setHome(this, home);
        }
    }

    private void refreshStatus() {
        boolean running = McpServerService.isRunning();
        int port = McpPrefs.getPort(this);
        status.setText(running ? getString(R.string.mcp_status_running) : getString(R.string.mcp_status_stopped));
        url.setText("http://" + (running ? McpServerService.currentUrl(this)
                : "127.0.0.1:" + port + "/mcp"));
        requestCount.setText(getString(R.string.mcp_request_count, McpServerService.getRequestCount()));
        toggle.setText(running ? getString(R.string.mcp_stop) : getString(R.string.mcp_start));
        if (running) {
            renderRules();
        }
    }

    private void renderRules() {
        rulesContainer.removeAllViews();
        JsonArray rules;
        try {
            rules = JsonParser.parseString(McpPrefs.getRulesJson(this)).getAsJsonArray();
        } catch (Exception e) {
            rules = new JsonArray();
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            JsonObject rule;
            try {
                rule = rules.get(i).getAsJsonObject();
            } catch (Exception e) {
                continue;
            }
            TextView row = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, rulesContainer, false);
            String mode = rule.has("mode") ? rule.get("mode").getAsString() : "?";
            String path = rule.has("path") ? rule.get("path").getAsString() : "?";
            row.setText(getString(R.string.mcp_rule_row, mode, path));
            row.setOnClickListener(v -> confirmDeleteRule(index));
            rulesContainer.addView(row);
        }
        TextView home = new TextView(this);
        home.setText(getString(R.string.mcp_rule_home, McpPrefs.getHome(this).getPath()));
        rulesContainer.addView(home, 0);
    }

    private void confirmDeleteRule(int index) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mcp_delete_rule)
                .setMessage(R.string.mcp_delete_rule_msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        JsonArray rules = JsonParser.parseString(McpPrefs.getRulesJson(this)).getAsJsonArray();
                        JsonArray updated = new JsonArray();
                        for (int i = 0; i < rules.size(); i++) {
                            if (i != index) updated.add(rules.get(i));
                        }
                        McpPrefs.setRulesJson(this, updated.toString());
                    } catch (Exception ignored) {
                    }
                    renderRules();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddRuleDialog() {
        EditText pathInput = new EditText(this);
        pathInput.setHint(R.string.mcp_rule_path_hint);
        String[] modes = {McpPathPolicy.MODE_RW, McpPathPolicy.MODE_RO, McpPathPolicy.MODE_DENY};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mcp_add_rule)
                .setView(pathInput)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String path = pathInput.getText().toString().trim();
                    if (path.isEmpty()) return;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.mcp_rule_mode_title)
                            .setItems(modes, (d2, w2) -> {
                                try {
                                    JsonArray rules = JsonParser.parseString(
                                            McpPrefs.getRulesJson(this)).getAsJsonArray();
                                    JsonObject rule = new JsonObject();
                                    rule.addProperty("path", path);
                                    rule.addProperty("mode", modes[w2]);
                                    rules.add(rule);
                                    McpPrefs.setRulesJson(this, rules.toString());
                                } catch (Exception ignored) {
                                }
                                renderRules();
                            })
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(text);
            Extensions.showMessage(this, getString(R.string.copied));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        handler.postDelayed(refresh, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }
}
