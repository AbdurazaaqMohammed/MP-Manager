package io.github.abdurazaaqmohammed.adapters.main;

import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager.Profile;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class CommandHelperSettingsDialog extends DialogFragment {
    private ProfileManager profileManager;
    private List<Profile> profiles;
    private ArrayAdapter<String> listAdapter;
    private ListView profileListView;
    public Runnable onProfilesChanged;

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onProfilesChanged != null) onProfilesChanged.run();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        profileManager = new ProfileManager(getActivity());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialCheckBox autoCopyCheck = new MaterialCheckBox(getActivity());
        autoCopyCheck.setText(R.string.auto_copy);
        autoCopyCheck.setChecked(prefs.getBoolean("auto_copy", false));
        autoCopyCheck.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("auto_copy", isChecked).apply());
        root.addView(autoCopyCheck);

        TextView autoCopyDesc = new TextView(getActivity());
        autoCopyDesc.setText(R.string.auto_copy_desc);
        autoCopyDesc.setTextAppearance(getActivity(), android.R.style.TextAppearance_Small);
        autoCopyDesc.setPadding(dp2(32), 0, 0, dp2(8));
        root.addView(autoCopyDesc);

        View sep = new View(getActivity());
        sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        sep.setBackgroundColor(0x1A000000);
        root.addView(sep);

        TextView profilesTitle = new TextView(getActivity());
        profilesTitle.setText(R.string.profiles);
        profilesTitle.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        profilesTitle.setTypeface(null, Typeface.BOLD);
        profilesTitle.setPadding(dp2(16), dp2(16), dp2(16), dp2(8));
        root.addView(profilesTitle);

        loadProfiles();
        listAdapter = new ArrayAdapter<>(getActivity(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, getProfileNames()) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View view = super.getView(pos, convertView, parent);
                if (view instanceof TextView tv) {
                    tv.setText(profiles.get(pos).name);
                    tv.setPadding(dp2(16), dp2(12), dp2(16), dp2(12));
                }
                return view;
            }
        };
        profileListView = new ListView(getActivity());
        profileListView.setAdapter(listAdapter);
        profileListView.setOnItemClickListener((parent, view, position, id) -> showEditDialog(position));
        profileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            showDeleteDialog(position);
            return true;
        });
        root.addView(profileListView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        MaterialButton addBtn = new MaterialButton(getActivity());
        addBtn.setText(R.string.add_profile);
        addBtn.setOnClickListener(v -> showAddDialog());
        root.addView(addBtn);

        return new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.settings)
                .setView(root)
                .setPositiveButton(R.string.close, null)
                .create();
    }

    private void loadProfiles() { profiles = profileManager.getProfiles(); }

    private List<String> getProfileNames() {
        List<String> names = new ArrayList<>();
        for (Profile p : profiles) names.add(p.name);
        return names;
    }

    private void refreshList() {
        loadProfiles();
        listAdapter.clear();
        for (String name : getProfileNames()) listAdapter.add(name);
        listAdapter.notifyDataSetChanged();
    }

    private void showAddDialog() {
        showProfileDialog(-1, null, null);
    }

    private void showEditDialog(int index) {
        Profile p = profiles.get(index);
        showProfileDialog(index, p.name, p.command);
    }

    private void showProfileDialog(int index, String existingName, String existingCommand) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(index < 0 ? R.string.add_profile : R.string.edit_profile);
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp2(16);
        layout.setPadding(p, p, p, p);
        TextInputLayout nameLayout = new TextInputLayout(getActivity());
        TextInputEditText nameInput = new TextInputEditText(getActivity());
        nameInput.setHint(R.string.profile_name);
        if (existingName != null) nameInput.setText(existingName);
        nameLayout.addView(nameInput);
        layout.addView(nameLayout);
        TextInputLayout cmdLayout = new TextInputLayout(getActivity());
        TextInputEditText cmdInput = new TextInputEditText(getActivity());
        cmdInput.setHint(R.string.command_template_hint);
        cmdInput.setSingleLine(false);
        cmdInput.setLines(3);
        if (existingCommand != null) cmdInput.setText(existingCommand);
        cmdLayout.addView(cmdInput);
        layout.addView(cmdLayout);
        MaterialButton placeholderHelpBtn = new MaterialButton(getActivity());
        placeholderHelpBtn.setText(R.string.placeholder_help);
        placeholderHelpBtn.setIconResource(R.drawable.baseline_info_24);
        placeholderHelpBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        layout.addView(placeholderHelpBtn);
        placeholderHelpBtn.setOnClickListener(v -> {
            Context ctx = getContext();
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(ctx);
            dialogBuilder.setTitle(R.string.command_template_placeholders);

            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (ctx.getResources().getDisplayMetrics().density * 12);
            container.setPadding(pad, pad, pad, pad);

            String[][] items = new String[][]{
                    {"%FPATH%", "%FPATH% - "  + getString(R.string.full_file_path_safe)},
                    {"%FILE%",  "%FILE% - " + getString(R.string.full_file_path_raw)},
                    {"%FNAME%", "%FNAME% - " + getString(R.string.fn_with_extension)},
                    {"%NAME%",  "%NAME% - " + getString(R.string.fn_without_extension)},
                    {"%EXT%",   "%EXT% - " + getString(R.string.extension_only)},
            };

            for (String[] item : items) {
                String token = item[0];
                String desc = item[1];

                LinearLayout row = new LinearLayout(ctx);
                row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView tv = new TextView(ctx);
                LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tv.setLayoutParams(tvLp);
                tv.setText(desc);

                MaterialButton copyBtn = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                copyBtn.setText(android.R.string.copy);
                copyBtn.setOnClickListener(view -> {
                    ((android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("placeholder", token));
                    Extensions.showMessage((AppCompatActivity) getActivity(), getString(R.string.copied_to_clipboard, token));
                });

                row.addView(tv);
                row.addView(copyBtn);
                container.addView(row);
            }

            TextView example = new TextView(ctx);
            example.setText(getString(R.string.example_x, "java -jar tool.jar -i %FPATH% -o %NAME%_modified.%EXT%"));
            example.setPadding(0, pad, 0, 0);
            container.addView(example);

            dialogBuilder.setView(container);
            dialogBuilder.setPositiveButton(R.string.close, null);
            dialogBuilder.show();
        });

        builder.setView(layout);
        builder.setPositiveButton(R.string.save, (d, w) -> {
            String name = nameInput.getText().toString().trim();
            String cmd = cmdInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Extensions.showMessage(getActivity(), "Name cannot be empty");

                return;
            }
            if (TextUtils.isEmpty(cmd)) {
                Extensions.showMessage(getActivity(), "Command template cannot be empty");
                return;
            }
            if (index < 0) profileManager.addProfile(new Profile(name, cmd));
            else profileManager.updateProfile(index, new Profile(name, cmd));
            refreshList();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        if(index >= 0) builder.setNeutralButton(R.string.delete, (dialog, which) -> showDeleteDialog(index));
        builder.show();
    }

    private void showDeleteDialog(int index) {
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.delete_profile)
                .setMessage(getContext().getString(R.string.delete_confirm, profiles.get(index).name))
                .setPositiveButton(R.string.save, (d, w) -> {
                    profileManager.deleteProfile(index);
                    refreshList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp2(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
