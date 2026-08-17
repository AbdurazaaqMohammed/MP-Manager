package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager.Profile;
import io.github.abdurazaaqmohammed.utils.CopyUtil;

public class CommandHelper {

    private final MainActivity context;

    public CommandHelper(MainActivity context) {
        this.context = context;
    }

    public void showCommandHelperDialog(ArrayList<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return;
        ProfileManager chPm = new ProfileManager(context);
        List<Profile> profiles = chPm.getProfiles();
        if (profiles.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.no_profiles)
                    .setMessage(R.string.no_profiles_msg)
                    .setPositiveButton(R.string.settings, (d, w) -> new CommandHelperSettingsDialog().show(context.getSupportFragmentManager(), "CommandHelperSettings"))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (filePaths.size() > 1) showMultiFileCommandDialog(profiles, filePaths, chPm);
        else buildAndShowCommandDialog(profiles, filePaths, true, chPm);
    }

    private void showMultiFileCommandDialog(List<Profile> profiles, ArrayList<String> filePaths, ProfileManager chPm) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.multiple_files);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, pad);
        MaterialCheckBox sameProfileCb = new MaterialCheckBox(context);
        sameProfileCb.setText(R.string.use_same_profile);
        sameProfileCb.setChecked(true);
        layout.addView(sameProfileCb);
        builder.setView(layout);
        builder.setPositiveButton(android.R.string.copy, (d, w) -> buildAndShowCommandDialog(profiles, filePaths, sameProfileCb.isChecked(), chPm));
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private static class CmdViewHolder {
        LinearLayout card;
        TextView fileLabel;
        Spinner spinner;
        TextView cmdText;
        MaterialButton copyBtn;
        MaterialButton termuxBtn;
        String filePath;
        boolean hasSpinner;
    }

    private void buildAndShowCommandDialog(List<Profile> profiles, ArrayList<String> filePaths, boolean sameProfile, ProfileManager chPm) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoCopy = prefs.getBoolean("auto_copy", false);
        int lastProfileIdx = Math.min(prefs.getInt("last_profile_idx", 0), profiles.size() - 1);
        if (lastProfileIdx < 0) lastProfileIdx = 0;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.command_helper);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        boolean multipleFiles = filePaths.size() > 1;
        ArrayList<Profile> mutableProfiles = new ArrayList<>(profiles);
        final Spinner[] sharedSpinnerRef = new Spinner[1];
        if (sameProfile && multipleFiles) {
            sharedSpinnerRef[0] = createProfileSpinner(mutableProfiles);
            sharedSpinnerRef[0].setSelection(lastProfileIdx);
            root.addView(sharedSpinnerRef[0]);
            root.addView(spacer(dp(8)));
        }
        List<CmdViewHolder> holders = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) {
            String fp = filePaths.get(i);
            CmdViewHolder h = new CmdViewHolder();
            h.filePath = fp;
            h.card = new LinearLayout(context);
            h.card.setOrientation(LinearLayout.VERTICAL);
            h.card.setPadding(0, dp(4), 0, dp(4));
            if (multipleFiles) {
                h.fileLabel = new TextView(context);
                h.fileLabel.setText((i + 1) + ". " + new File(fp).getName());
                h.fileLabel.setTextAppearance(context, android.R.style.TextAppearance_Small);
                h.card.addView(h.fileLabel);
            }
            boolean showIndividualSpinner = !sameProfile || !multipleFiles;
            if (showIndividualSpinner) {
                h.spinner = createProfileSpinner(mutableProfiles);
                h.spinner.setSelection(lastProfileIdx);
                h.hasSpinner = true;
                h.card.addView(h.spinner);
            }
            h.cmdText = new TextView(context);
            h.cmdText.setTextAppearance(context, android.R.style.TextAppearance_Medium);
            h.cmdText.setTypeface(Typeface.MONOSPACE);
            h.cmdText.setPadding(0, dp(4), 0, dp(4));
            h.cmdText.setTextIsSelectable(true);
            h.card.addView(h.cmdText);
            LinearLayout btnRow = new LinearLayout(context);
            btnRow.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            h.copyBtn = new MaterialButton(context);
            h.copyBtn.setText(multipleFiles ? ("Copy #" + (i + 1)) : "Copy");
            h.copyBtn.setWidth(0);
            h.copyBtn.setPadding(5, 0, 5, 0);
            h.copyBtn.setIconResource(R.drawable.ic_copy);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            h.copyBtn.setLayoutParams(lp);
            h.copyBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btnRow.addView(h.copyBtn);

            h.termuxBtn = new MaterialButton(context);
            h.termuxBtn.setText("Termux");
            h.termuxBtn.setWidth(0);
            h.termuxBtn.setPadding(5, 0, 5, 0);
            h.termuxBtn.setLayoutParams(lp);
            h.termuxBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            h.termuxBtn.setIconResource(R.drawable.terminal_24px);
            btnRow.addView(h.termuxBtn);
            h.card.addView(btnRow);
            root.addView(h.card);
            if (i < filePaths.size() - 1) root.addView(divider());
            holders.add(h);
        }
        if (multipleFiles) {
            root.addView(spacer(dp(8)));
            LinearLayout multiBtnRow = new LinearLayout(context);
            multiBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton copyAllBtn = new MaterialButton(context);
            copyAllBtn.setText(R.string.copy_all);
            multiBtnRow.addView(copyAllBtn);
            MaterialButton termuxAllBtn = new MaterialButton(context);
            termuxAllBtn.setText("Run All in Termux");
            multiBtnRow.addView(termuxAllBtn);
            root.addView(multiBtnRow);
            copyAllBtn.setOnClickListener(v -> {
                StringBuilder sb = new StringBuilder();
                for (CmdViewHolder hldr : holders) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(hldr.cmdText.getText());
                }
                CopyUtil.copyToClipboard(context, sb);
            });
            termuxAllBtn.setOnClickListener(v -> {
                for (CmdViewHolder hldr : holders) {
                    String cmd = hldr.cmdText.getText().toString();
                    if (!cmd.isEmpty()) runInTermux(cmd);
                }
            });
        }
        root.addView(spacer(dp(8)));
        MaterialButton manageBtn = new MaterialButton(context);
        manageBtn.setText(R.string.manage_profiles);
        root.addView(manageBtn);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        builder.setView(scroll);
        builder.setNegativeButton(R.string.close, null);
        AlertDialog mainDialog = builder.create();
        mainDialog.show();

        Runnable refreshCallback = () -> {
            List<Profile> freshProfiles = chPm.getProfiles();
            mutableProfiles.clear();
            mutableProfiles.addAll(freshProfiles);
            for (CmdViewHolder h : holders) {
                if (h.hasSpinner && h.spinner != null) {
                    ArrayAdapter<String> ad = (ArrayAdapter<String>) h.spinner.getAdapter();
                    ad.clear();
                    for (Profile pr : freshProfiles) ad.add(pr.name);
                    ad.notifyDataSetChanged();
                    int sel = h.spinner.getSelectedItemPosition();
                    if (sel >= freshProfiles.size()) h.spinner.setSelection(Math.max(0, freshProfiles.size() - 1));
                }
            }
            if (sharedSpinnerRef[0] != null) {
                ArrayAdapter<String> ad = (ArrayAdapter<String>) sharedSpinnerRef[0].getAdapter();
                ad.clear();
                for (Profile pr : freshProfiles) ad.add(pr.name);
                ad.notifyDataSetChanged();
                int sel = sharedSpinnerRef[0].getSelectedItemPosition();
                if (sel >= freshProfiles.size()) sharedSpinnerRef[0].setSelection(Math.max(0, freshProfiles.size() - 1));
            }
        };

        manageBtn.setOnClickListener(v -> {
            CommandHelperSettingsDialog settingsDialog = new CommandHelperSettingsDialog();
            settingsDialog.onProfilesChanged = refreshCallback;
            settingsDialog.show(context.getSupportFragmentManager(), "CommandHelperSettings");
        });

        for (int i = 0; i < holders.size(); i++) {
            CmdViewHolder h = holders.get(i);
            Profile initProfile = mutableProfiles.get(Math.min(lastProfileIdx, mutableProfiles.size() - 1));
            h.cmdText.setText(initProfile.getGeneratedCommand(h.filePath));
            if (h.hasSpinner) {
                h.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Profile p = mutableProfiles.get(position);
                        h.cmdText.setText(p.getGeneratedCommand(h.filePath));
                        prefs.edit().putInt("last_profile_idx", position).apply();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
            h.copyBtn.setOnClickListener(v -> CopyUtil.copyToClipboard(context, h.cmdText.getText()));
            h.termuxBtn.setOnClickListener(v -> {
                String cmd = h.cmdText.getText().toString();
                if (!cmd.isEmpty()) runInTermux(cmd);
            });
        }
        if (sharedSpinnerRef[0] != null) {
            sharedSpinnerRef[0].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Profile p = mutableProfiles.get(position);
                    for (CmdViewHolder hldr : holders) hldr.cmdText.setText(p.getGeneratedCommand(hldr.filePath));
                    prefs.edit().putInt("last_profile_idx", position).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        if (autoCopy && !holders.isEmpty()) {
            CopyUtil.copyToClipboard(context, mutableProfiles.get(Math.min(lastProfileIdx, mutableProfiles.size() - 1)).getGeneratedCommand(holders.get(0).filePath));
        }
    }

    private Spinner createProfileSpinner(List<Profile> profiles) {
        Spinner spinner = new Spinner(context);
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) names.add(profiles.get(i).name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void runInTermux(String command) {
        try {
            context.getPackageManager().getPackageInfo("com.termux", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.command_helper)
                    .setMessage("Warning: Termux was not found on the device")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Download Termux Now", (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")))).show();
            return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command + "; exec bash"});
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            context.startService(intent);
        } catch (SecurityException e) {
            new MaterialAlertDialogBuilder(context).setMessage("Note: You need to grant permission for MP Manager to be able to send command to Termux\n(Permissions > Additional Permissions > Run commands in Termux environment)").setTitle("Permissions").setPositiveButton(android.R.string.ok, (dialog, which) -> context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())))).setNegativeButton(android.R.string.cancel, null).show();
        } catch (IllegalStateException ise) {
            // This can happen if Termux is force stopped even if you granted draw over other apps permission
            context.startActivity(new Intent().setClassName("com.termux", "com.termux.app.TermuxActivity"));
            context.handler.postDelayed(() -> context.startService(intent), 2000);
        }
    }

    private View spacer(int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private View divider() {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(0x1A000000);
        v.setMinimumHeight(1);
        return v;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
