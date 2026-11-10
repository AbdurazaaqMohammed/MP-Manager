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
    private com.google.android.material.textfield.TextInputEditText searchEdit;

    // Battery & Network
    private TextView tvStandbyBucketValue;
    private View rowStandbyBucket;
    private MaterialSwitch switchBatteryOpt;
    private MaterialSwitch switchBlockNet;

    // Error banner shown when no elevated backend (root/Shizuku) is available
    private View bannerError;
    private TextView bannerErrorText;

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
                Toast.makeText(this, R.string.app_ops_refreshing, Toast.LENGTH_SHORT).show();
                loadData();
            });
        }

        searchEdit = findViewById(R.id.detail_search_edit);
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

        bannerError = findViewById(R.id.detail_banner_error);
        bannerErrorText = findViewById(R.id.detail_banner_error_text);

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
                boolean ok = AppOpsEngine.setIgnoreBatteryOptimization(this, packageName, isChecked);
                mainHandler.post(() -> {
                    if (ok) {
                        Toast.makeText(this, getString(isChecked
                                ? R.string.app_ops_toast_battery_whitelisted
                                : R.string.app_ops_toast_battery_optimized), Toast.LENGTH_SHORT).show();
                    } else {
                        revertSwitch(btn, isChecked);
                    }
                });
            });
        });

        switchBlockNet.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            if (data == null) return;
            executor.execute(() -> {
                boolean ok = AppOpsEngine.setNetworkBlocked(this, data.uid, isChecked);
                mainHandler.post(() -> {
                    if (ok) {
                        Toast.makeText(this, getString(isChecked
                                ? R.string.app_ops_toast_net_blocked
                                : R.string.app_ops_toast_net_unblocked), Toast.LENGTH_SHORT).show();
                    } else {
                        revertSwitch(btn, isChecked);
                    }
                });
            });
        });

        btnForceStop.setOnClickListener(v -> executor.execute(() -> {
            CommandOutcome ok = runChecked(() -> AppOpsEngine.forceStopApp(this, packageName));
            mainHandler.post(() -> {
                if (ok.success) {
                    Toast.makeText(this, R.string.app_ops_toast_force_stopped, Toast.LENGTH_SHORT).show();
                } else {
                    toastFailure(ok);
                }
            });
        }));

        // Suspend has an undo path: the button toggles between suspend and
        // unsuspend based on the package's current state.
        btnSuspend.setOnClickListener(v -> {
            boolean suspend = data == null || !data.isSuspended;
            executor.execute(() -> {
                CommandOutcome ok = runChecked(() -> AppOpsEngine.suspendApp(this, packageName, suspend));
                if (ok.success) AppOpsCache.invalidateAppList();
                mainHandler.post(() -> {
                    if (ok.success) {
                        Toast.makeText(this, getString(suspend
                                ? R.string.app_ops_toast_suspended
                                : R.string.app_ops_toast_unsuspended), Toast.LENGTH_SHORT).show();
                        loadData();
                    } else {
                        toastFailure(ok);
                    }
                });
            });
        });

        btnDisable.setOnClickListener(v -> {
            boolean enable = data != null && !data.isEnabled;
            if (!enable) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.app_ops_action_disable)
                        .setMessage(getString(R.string.app_ops_disable_confirm, packageName))
                        .setPositiveButton(android.R.string.ok, (d, w) -> runEnableDisable(false))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            runEnableDisable(true);
        });

        btnClearData.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_ops_action_clear_data)
                .setMessage(getString(R.string.app_ops_clear_data_confirm, packageName))
                .setPositiveButton(android.R.string.ok, (d, w) -> executor.execute(() -> {
                    CommandOutcome ok = runChecked(() -> AppOpsEngine.clearAppData(this, packageName));
                    if (ok.success) AppOpsCache.invalidateAppList();
                    mainHandler.post(() -> {
                        if (ok.success) {
                            Toast.makeText(this, R.string.app_ops_toast_data_cleared, Toast.LENGTH_SHORT).show();
                            loadData();
                        } else {
                            toastFailure(ok);
                        }
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        btnUninstall.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_ops_action_uninstall)
                .setMessage(getString(R.string.app_ops_uninstall_confirm, packageName))
                .setPositiveButton(android.R.string.ok, (d, w) -> executor.execute(() -> {
                    CommandOutcome ok = runChecked(() -> AppOpsEngine.uninstallApp(this, packageName));
                    if (ok.success) AppOpsCache.invalidateAppList();
                    mainHandler.post(() -> {
                        if (ok.success) {
                            Toast.makeText(this, R.string.app_ops_toast_uninstalled, Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            toastFailure(ok);
                        }
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void runEnableDisable(boolean enable) {
        executor.execute(() -> {
            CommandOutcome ok = runChecked(() -> AppOpsEngine.enableDisableApp(this, packageName, enable));
            if (ok.success) AppOpsCache.invalidateAppList();
            mainHandler.post(() -> {
                if (ok.success) {
                    Toast.makeText(this, getString(enable
                            ? R.string.app_ops_toast_enabled
                            : R.string.app_ops_toast_disabled), Toast.LENGTH_SHORT).show();
                    loadData();
                } else {
                    toastFailure(ok);
                }
            });
        });
    }

    private static final class CommandOutcome {
        final boolean success;
        final String error;

        CommandOutcome(boolean success, String error) {
            this.success = success;
            this.error = error == null ? "" : error;
        }
    }

    /**
     * Wraps one engine action with the reason it failed, so the toast says
     * something actionable ("Requires root or Shizuku", stderr detail) instead
     * of the operation failing silently as success.
     */
    private CommandOutcome runChecked(java.util.concurrent.Callable<Boolean> action) {
        try {
            boolean ok = action.call();
            if (ok) return new CommandOutcome(true, "");
            return new CommandOutcome(false, AppOpsEngine.lastError());
        } catch (Exception e) {
            return new CommandOutcome(false, e.getMessage());
        }
    }

    private void toastFailure(CommandOutcome outcome) {
        String detail = TextUtils.isEmpty(outcome.error) ? getString(R.string.app_ops_unknown) : outcome.error;
        Toast.makeText(this, getString(R.string.app_ops_action_failed, detail), Toast.LENGTH_LONG).show();
    }

    /** Puts a switch back where it was after its action failed. */
    private void revertSwitch(android.widget.CompoundButton btn, boolean attempted) {
        btn.setChecked(!attempted);
        toastFailure(new CommandOutcome(false, AppOpsEngine.lastError()));
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

        // No root/Shizuku backend: explain why the live state is empty and
        // disable every mutating control instead of letting the user press
        // placebo buttons that silently do nothing.
        boolean elevated = data.elevatedAvailable;
        if (bannerError != null) {
            bannerError.setVisibility(elevated ? View.GONE : View.VISIBLE);
            if (!elevated && bannerErrorText != null) {
                bannerErrorText.setText(R.string.app_ops_requires_elevated);
            }
        }

        // Suspend / disable buttons carry their undo path: the label always
        // reflects what the next tap will do.
        btnSuspend.setText(getString(data.isSuspended
                ? R.string.app_ops_action_unsuspend
                : R.string.app_ops_action_suspend));
        btnDisable.setText(getString(data.isEnabled
                ? R.string.app_ops_action_disable
                : R.string.app_ops_action_enable));

        btnForceStop.setEnabled(elevated);
        btnSuspend.setEnabled(elevated);
        btnDisable.setEnabled(elevated);
        btnClearData.setEnabled(elevated);
        btnUninstall.setEnabled(elevated);

        // Standby & Network
        tvStandbyBucketValue.setText(elevated ? data.standbyBucket : getString(R.string.app_ops_unknown));
        switchBatteryOpt.setEnabled(elevated);
        switchBlockNet.setEnabled(elevated);
        rowStandbyBucket.setEnabled(elevated);
        switchBatteryOpt.setChecked(elevated && data.isIgnoringBatteryOptimizations);
        switchBlockNet.setChecked(elevated && data.isNetworkBlocked);

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
            none.setText(R.string.app_ops_no_runtime_perms);
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

        // Re-apply active search filter so filtered results don't get reset when toggling switches
        if (searchEdit != null && searchEdit.getText() != null) {
            String q = searchEdit.getText().toString().trim().toLowerCase();
            if (!TextUtils.isEmpty(q)) {
                filterDetailViews(q);
            }
        }
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
        sw.setEnabled(data.elevatedAvailable);
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            executor.execute(() -> {
                boolean ok;
                if (isSpecialSystem) {
                    // These are AppOps-controlled special access entries; the
                    // package-level op mode is the real mechanism. Never touch
                    // the UID mode here: it silently affects every app sharing
                    // the uid and creates the "fake granted" state this screen
                    // warns about.
                    ok = AppOpsEngine.setAppOpMode(this, packageName, p.opName, isChecked ? "allow" : "ignore");
                } else {
                    // Runtime permissions are platform-managed: grant/revoke
                    // through pm and reset the op to "default" so an earlier
                    // forced ignore/deny does not keep silently dropping the
                    // permission after re-granting. The kernel-level dialog
                    // (long press) is the place for UID-wide modes.
                    ok = AppOpsEngine.setPermissionGranted(this, packageName, p.name, isChecked)
                            && AppOpsEngine.setAppOpMode(this, packageName, p.opName, "default");
                }
                mainHandler.post(() -> {
                    if (!ok) {
                        btn.setChecked(!isChecked);
                        toastFailure(new CommandOutcome(false, AppOpsEngine.lastError()));
                        return;
                    }
                    // Re-read from the platform instead of trusting the toggle:
                    // the command may have succeeded while the state differs.
                    loadData();
                });
            });
        });

        // HIDDEN KERNEL CONTROL: Long press switch or row to open full kernel AppOp mode dialog!
        View.OnLongClickListener longClickListener = v -> {
            showKernelOpModeDialog(p);
            return true;
        };
        sw.setOnLongClickListener(longClickListener);
        row.setOnLongClickListener(longClickListener);

        row.addView(sw);

        return row;
    }

    private void showKernelOpModeDialog(AppOpsDetailData.PermissionEntry p) {
        String[] modes = {
                getString(R.string.app_ops_mode_allow_normal),
                getString(R.string.app_ops_mode_ignore_silent),
                getString(R.string.app_ops_mode_deny_hard),
                getString(R.string.app_ops_mode_default)
        };
        String[] cmdModes = {"allow", "ignore", "deny", "default"};

        String currentEffective = !TextUtils.isEmpty(p.uidMode) && !"default".equalsIgnoreCase(p.uidMode) ? p.uidMode : p.opMode;
        int checked = 0;
        for (int i = 0; i < cmdModes.length; i++) {
            if (cmdModes[i].equalsIgnoreCase(currentEffective)) {
                checked = i;
                break;
            }
        }

        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_ops_kernel_op_title, p.label))
                .setSingleChoiceItems(modes, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newMode = cmdModes[selected[0]];
                    executor.execute(() -> {
                        // Explicit kernel-level control chosen by the user: apply
                        // to the UID layer and package layer, and verify.
                        CommandOutcome ok = runChecked(() ->
                                AppOpsEngine.setAppOpMode(this, packageName, p.opName, newMode, true));
                        mainHandler.post(() -> {
                            if (ok.success) {
                                Toast.makeText(this, getString(R.string.app_ops_toast_mode_set, p.label, newMode), Toast.LENGTH_SHORT).show();
                                loadData();
                            } else {
                                toastFailure(ok);
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
            String t = !TextUtils.isEmpty(op.lastAccessTime)
                    ? getString(R.string.app_ops_last_accessed, op.lastAccessTime)
                    : getString(R.string.app_ops_rejected, op.rejectTime);
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
        String[] modes = {
                getString(R.string.app_ops_mode_default),
                getString(R.string.app_ops_mode_allow),
                getString(R.string.app_ops_mode_ignore),
                getString(R.string.app_ops_mode_deny)
        };
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
                    executor.execute(() -> {
                        CommandOutcome ok = runChecked(() ->
                                AppOpsEngine.setAppOpMode(this, packageName, op.opName, newMode));
                        mainHandler.post(() -> {
                            if (ok.success) {
                                loadData();
                            } else {
                                toastFailure(ok);
                            }
                        });
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
                        CommandOutcome ok = runChecked(() ->
                                AppOpsEngine.setStandbyBucket(this, packageName, chosen));
                        mainHandler.post(() -> {
                            if (ok.success) {
                                loadData();
                            } else {
                                toastFailure(ok);
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void populateInfoSection() {
        DateFormat df = DateFormat.getDateTimeInstance();
        addInfoRow(getString(R.string.app_ops_info_version), data.versionName);
        addInfoRow(getString(R.string.app_ops_info_uid), String.valueOf(data.uid));
        addInfoRow(getString(R.string.app_ops_info_target_sdk), String.valueOf(data.targetSdk));
        addInfoRow(getString(R.string.app_ops_info_min_sdk), String.valueOf(data.minSdk));
        addInfoRow(getString(R.string.app_ops_info_primary_abi), data.primaryAbi);
        addInfoRow(getString(R.string.app_ops_info_data_dir), data.dataDir);
        addInfoRow(getString(R.string.app_ops_info_signature), data.signaturesSha256);
        addInfoRow(getString(R.string.app_ops_info_installed_from), data.installer);
        addInfoRow(getString(R.string.app_ops_info_installed_date),
                data.firstInstallTime > 0 ? df.format(new Date(data.firstInstallTime)) : getString(R.string.app_ops_unknown));
        addInfoRow(getString(R.string.app_ops_info_updated_date),
                data.lastUpdateTime > 0 ? df.format(new Date(data.lastUpdateTime)) : getString(R.string.app_ops_unknown));
        addInfoRow(getString(R.string.app_ops_info_debuggable),
                data.isDebuggable ? getString(R.string.app_ops_yes) : getString(R.string.app_ops_no));
        addInfoRow(getString(R.string.app_ops_info_backup_allowed),
                data.isBackupAllowed ? getString(R.string.app_ops_yes) : getString(R.string.app_ops_no));
        addInfoRow(getString(R.string.app_ops_info_launcher_entry),
                data.hasLauncherEntry ? getString(R.string.app_ops_yes) : getString(R.string.app_ops_no));
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
        v.setText(TextUtils.isEmpty(value) ? getString(R.string.app_ops_none) : value);
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
