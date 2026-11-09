package io.github.abdurazaaqmohammed.tools.appops;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

public class AppOpsDetailActivity extends BaseActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String packageName;
    private AppOpsDetailData data;

    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private NestedScrollView scrollView;
    private FloatingActionButton fabScrollTop;

    // Battery & Network
    private TextView tvStandbyBucketValue;
    private View rowStandbyBucket;
    private MaterialSwitch switchBatteryOpt;
    private MaterialSwitch switchBlockNet;

    // Containers
    private LinearLayout containerSystemPerms;
    private LinearLayout containerRuntimePerms;
    private LinearLayout containerAppOps;
    private LinearLayout containerInfo;

    // Action buttons
    private TextView btnForceStop;
    private TextView btnSuspend;
    private TextView btnDisable;
    private TextView btnClearData;
    private TextView btnUninstall;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_ops_detail);

        packageName = getIntent().getStringExtra("package_name");
        if (TextUtils.isEmpty(packageName)) {
            finish();
            return;
        }

        initViews();
        loadData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.detail_toolbar);
        toolbar.setTitle(packageName);
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView btnOpenSettings = findViewById(R.id.detail_btn_open_settings);
        btnOpenSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            } catch (Exception ignored) {}
        });

        ImageView btnRefresh = findViewById(R.id.detail_btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
                loadData();
            });
        }

        com.google.android.material.textfield.TextInputEditText searchEdit = findViewById(R.id.detail_search_edit);
        if (searchEdit != null) {
            searchEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterDetailViews(s != null ? s.toString().trim().toLowerCase() : "");
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        progressBar = findViewById(R.id.detail_progress);
        scrollView = findViewById(R.id.detail_scroll);
        fabScrollTop = findViewById(R.id.fab_scroll_top);
        fabScrollTop.setOnClickListener(v -> scrollView.smoothScrollTo(0, 0));

        rowStandbyBucket = findViewById(R.id.row_standby_bucket);
        tvStandbyBucketValue = findViewById(R.id.tv_standby_bucket_value);
        switchBatteryOpt = findViewById(R.id.switch_battery_optimization);
        switchBlockNet = findViewById(R.id.switch_block_network);

        containerSystemPerms = findViewById(R.id.container_system_perms);
        containerRuntimePerms = findViewById(R.id.container_runtime_perms);
        containerAppOps = findViewById(R.id.container_app_ops);
        containerInfo = findViewById(R.id.container_info);

        btnForceStop = findViewById(R.id.btn_action_force_stop);
        btnSuspend = findViewById(R.id.btn_action_suspend);
        btnDisable = findViewById(R.id.btn_action_disable);
        btnClearData = findViewById(R.id.btn_action_clear_data);
        btnUninstall = findViewById(R.id.btn_action_uninstall);

        setupActionListeners();
    }

    private void filterDetailViews(String query) {
        filterContainerChildren(containerSystemPerms, query);
        filterContainerChildren(containerRuntimePerms, query);
        filterContainerChildren(containerAppOps, query);
        filterContainerChildren(containerInfo, query);
    }

    private void filterContainerChildren(LinearLayout container, String query) {
        if (container == null) return;
        boolean emptyQuery = TextUtils.isEmpty(query);
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (emptyQuery) {
                child.setVisibility(View.VISIBLE);
                continue;
            }
            Object tag = child.getTag();
            String searchable = tag instanceof String ? (String) tag : "";
            if (searchable.toLowerCase().contains(query)) {
                child.setVisibility(View.VISIBLE);
            } else {
                child.setVisibility(View.GONE);
            }
        }
    }

    private void setupActionListeners() {
        rowStandbyBucket.setOnClickListener(v -> showStandbyBucketPicker());

        switchBatteryOpt.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            executor.execute(() -> {
                AppOpsEngine.setIgnoreBatteryOptimization(packageName, isChecked);
                mainHandler.post(() -> Toast.makeText(this, isChecked ? "Whitelisted from battery optimization" : "Optimized", Toast.LENGTH_SHORT).show());
            });
        });

        switchBlockNet.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            if (data == null) return;
            executor.execute(() -> {
                AppOpsEngine.setNetworkBlocked(data.uid, isChecked);
                mainHandler.post(() -> Toast.makeText(this, isChecked ? "Network access blocked" : "Network unblocked", Toast.LENGTH_SHORT).show());
            });
        });

        btnForceStop.setOnClickListener(v -> {
            executor.execute(() -> {
                AppOpsEngine.forceStopApp(packageName);
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.app_ops_action_force_stop) + " done", Toast.LENGTH_SHORT).show());
            });
        });

        btnSuspend.setOnClickListener(v -> {
            executor.execute(() -> {
                AppOpsEngine.suspendApp(packageName, true);
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.app_ops_action_suspend) + " done", Toast.LENGTH_SHORT).show());
            });
        });

        btnDisable.setOnClickListener(v -> {
            executor.execute(() -> {
                AppOpsEngine.enableDisableApp(packageName, false);
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.app_ops_action_disable) + " done", Toast.LENGTH_SHORT).show());
            });
        });

        btnClearData.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_ops_action_clear_data)
                .setMessage("Are you sure you want to clear all data for " + packageName + "?")
                .setPositiveButton(android.R.string.ok, (d, w) -> executor.execute(() -> {
                    AppOpsEngine.clearAppData(packageName);
                    mainHandler.post(() -> Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show());
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        btnUninstall.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_ops_action_uninstall)
                .setMessage("Uninstall " + packageName + "?")
                .setPositiveButton(android.R.string.ok, (d, w) -> executor.execute(() -> {
                    AppOpsEngine.uninstallApp(packageName);
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Uninstalled", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            data = AppOpsEngine.loadAppDetails(this, packageName);
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                bindData();
            });
        });
    }

    private void bindData() {
        if (data == null) return;
        toolbar.setTitle(data.label);
        toolbar.setSubtitle(data.packageName);

        // Load Cached or Async icon into toolbar navigation icon area
        Drawable cachedIcon = AppOpsCache.getIcon(packageName);
        if (cachedIcon != null) {
            // Set title with icon or custom toolbar header
        }

        // Standby & Network
        tvStandbyBucketValue.setText(data.standbyBucket);
        switchBatteryOpt.setChecked(data.isIgnoringBatteryOptimizations);
        switchBlockNet.setChecked(data.isNetworkBlocked);

        // Populate System Permissions
        containerSystemPerms.removeAllViews();
        for (AppOpsDetailData.PermissionEntry p : data.systemPermissions) {
            containerSystemPerms.addView(createPermissionRow(p, true));
        }

        // Populate Runtime Permissions (Smart Dual Verification)
        containerRuntimePerms.removeAllViews();
        if (data.runtimePermissions.isEmpty()) {
            TextView none = new TextView(this);
            none.setPadding(30, 24, 30, 24);
            none.setText("No runtime permissions declared");
            none.setTextColor(Color.GRAY);
            containerRuntimePerms.addView(none);
        } else {
            for (AppOpsDetailData.PermissionEntry p : data.runtimePermissions) {
                containerRuntimePerms.addView(createPermissionRow(p, false));
            }
        }

        // Populate Granular AppOps
        containerAppOps.removeAllViews();
        for (AppOpsDetailData.AppOpEntry op : data.appOps) {
            containerAppOps.addView(createAppOpRow(op));
        }

        // Populate App Metadata Info
        containerInfo.removeAllViews();
        populateInfoSection();
    }

    private View createPermissionRow(AppOpsDetailData.PermissionEntry p, boolean isSpecialSystem) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(28, 22, 28, 22);
        row.setTag(p.label + " " + p.name + " " + p.opName);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lparams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(p.label);
        tvTitle.setTextSize(14f);
        tvTitle.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, getTheme()));
        textCol.addView(tvTitle);

        // SMART TAG: Fake Granted (Restrained)
        if (p.isFakeGranted) {
            TextView tvFake = new TextView(this);
            tvFake.setText(getString(R.string.app_ops_tag_fake_granted));
            tvFake.setTextSize(11f);
            tvFake.setTextColor(Color.parseColor("#FF5722"));
            tvFake.setPadding(0, 4, 0, 0);
            textCol.addView(tvFake);
        }

        row.addView(textCol);

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(p.isGranted);
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            p.isGranted = isChecked;
            executor.execute(() -> {
                if (isSpecialSystem) {
                    AppOpsEngine.setAppOpMode(packageName, p.opName, isChecked ? "allow" : "ignore");
                } else {
                    AppOpsEngine.setPermissionGranted(packageName, p.name, isChecked);
                    // Also sync Op mode
                    AppOpsEngine.setAppOpMode(packageName, p.opName, isChecked ? "allow" : "ignore");
                }
                mainHandler.post(() -> {
                    p.isFakeGranted = false; // re-evaluated
                    bindData();
                });
            });
        });
        row.addView(sw);

        return row;
    }

    private View createAppOpRow(AppOpsDetailData.AppOpEntry op) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(28, 20, 28, 20);
        row.setTag(op.label + " " + op.opName + " " + op.description);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(lparams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(op.label);
        tvTitle.setTextSize(14f);
        textCol.addView(tvTitle);

        if (!TextUtils.isEmpty(op.description)) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(op.description);
            tvDesc.setTextSize(12f);
            tvDesc.setTextColor(Color.GRAY);
            tvDesc.setPadding(0, 2, 0, 0);
            textCol.addView(tvDesc);
        }

        if (!TextUtils.isEmpty(op.lastAccessTime) || !TextUtils.isEmpty(op.rejectTime)) {
            TextView tvTime = new TextView(this);
            String t = !TextUtils.isEmpty(op.lastAccessTime) ? "Last accessed: " + op.lastAccessTime : "Rejected: " + op.rejectTime;
            tvTime.setText(t);
            tvTime.setTextSize(11f);
            tvTime.setTextColor(Color.parseColor("#4CAF50"));
            textCol.addView(tvTime);
        }

        row.addView(textCol);

        // Mode badge & Chevron
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.HORIZONTAL);
        rightCol.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvMode = new TextView(this);
        tvMode.setText(formatModeLabel(op.mode));
        tvMode.setTextSize(13f);
        tvMode.setTextColor(getModeColor(op.mode));
        rightCol.addView(tvMode);

        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.chevron_right_24px);
        chev.setAlpha(0.4f);
        rightCol.addView(chev);

        row.addView(rightCol);

        row.setOnClickListener(v -> showOpModeDialog(op));
        return row;
    }

    private void showOpModeDialog(AppOpsDetailData.AppOpEntry op) {
        String[] modes = {"Default", "Allow", "Ignore", "Deny"};
        String[] cmdModes = {"default", "allow", "ignore", "deny"};

        int checked = 0;
        for (int i = 0; i < cmdModes.length; i++) {
            if (cmdModes[i].equalsIgnoreCase(op.mode)) {
                checked = i;
                break;
            }
        }

        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle(op.label)
                .setSingleChoiceItems(modes, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newMode = cmdModes[selected[0]];
                    op.mode = newMode;
                    executor.execute(() -> {
                        AppOpsEngine.setAppOpMode(packageName, op.opName, newMode);
                        mainHandler.post(this::bindData);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showStandbyBucketPicker() {
        String[] buckets = {"Active (10)", "Working (20)", "Frequent (30)", "Rare (40)", "Restricted (45)"};
        String[] bucketCmds = {"active", "working_set", "frequent", "rare", "restricted"};

        int checked = 0;
        if (data != null && data.standbyBucket != null) {
            for (int i = 0; i < buckets.length; i++) {
                if (data.standbyBucket.startsWith(buckets[i].substring(0, 4))) {
                    checked = i;
                    break;
                }
            }
        }

        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_ops_standby_bucket)
                .setSingleChoiceItems(buckets, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String chosen = bucketCmds[selected[0]];
                    executor.execute(() -> {
                        AppOpsEngine.setStandbyBucket(packageName, chosen);
                        mainHandler.post(this::loadData);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void populateInfoSection() {
        DateFormat df = DateFormat.getDateTimeInstance();
        addInfoRow("Version", data.versionName);
        addInfoRow("UID", String.valueOf(data.uid));
        addInfoRow("Target SDK", String.valueOf(data.targetSdk));
        addInfoRow("Min SDK", String.valueOf(data.minSdk));
        addInfoRow("Primary ABI", data.primaryAbi);
        addInfoRow("Data Directory", data.dataDir);
        addInfoRow("Signature SHA-256", data.signaturesSha256);
        addInfoRow("Installed From", data.installer);
        addInfoRow("Installed Date", data.firstInstallTime > 0 ? df.format(new Date(data.firstInstallTime)) : "Unknown");
        addInfoRow("Updated Date", data.lastUpdateTime > 0 ? df.format(new Date(data.lastUpdateTime)) : "Unknown");
        addInfoRow("Debuggable", data.isDebuggable ? "Yes" : "No");
        addInfoRow("Backup Allowed", data.isBackupAllowed ? "Yes" : "No");
        addInfoRow("Launcher Entry", data.hasLauncherEntry ? "Yes" : "No");
    }

    private void addInfoRow(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);
        row.setTag(title + " " + value);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(12f);
        t.setTextColor(Color.GRAY);
        row.addView(t);

        TextView v = new TextView(this);
        v.setText(TextUtils.isEmpty(value) ? "None" : value);
        v.setTextSize(13.5f);
        v.setTextIsSelectable(true);
        row.addView(v);

        containerInfo.addView(row);
    }

    private String formatModeLabel(String mode) {
        if ("allow".equalsIgnoreCase(mode)) return getString(R.string.app_ops_mode_allow);
        if ("ignore".equalsIgnoreCase(mode)) return getString(R.string.app_ops_mode_ignore);
        if ("deny".equalsIgnoreCase(mode)) return getString(R.string.app_ops_mode_deny);
        if ("foreground".equalsIgnoreCase(mode)) return getString(R.string.app_ops_mode_foreground);
        if ("ask".equalsIgnoreCase(mode)) return getString(R.string.app_ops_mode_ask);
        return getString(R.string.app_ops_mode_default);
    }

    private int getModeColor(String mode) {
        if ("allow".equalsIgnoreCase(mode)) return Color.parseColor("#4CAF50");
        if ("ignore".equalsIgnoreCase(mode)) return Color.parseColor("#FF9800");
        if ("deny".equalsIgnoreCase(mode)) return Color.parseColor("#F44336");
        return Color.GRAY;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
