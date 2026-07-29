package io.github.abdurazaaqmohammed.commandhelper;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager.Profile;

public class SettingsDialogFragment extends DialogFragment {

    private ProfileManager profileManager;
    private List<Profile> profiles;
    private ArrayAdapter<String> listAdapter;
    private ListView profileListView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        profileManager = new ProfileManager(getActivity());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);

        CheckBox autoCopyCheck = new CheckBox(getActivity());
        autoCopyCheck.setText(R.string.auto_copy);
        autoCopyCheck.setChecked(prefs.getBoolean("auto_copy", false));
        autoCopyCheck.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("auto_copy", isChecked).apply());
        root.addView(autoCopyCheck);

        TextView autoCopyDesc = new TextView(getActivity());
        autoCopyDesc.setText(R.string.auto_copy_desc);
        autoCopyDesc.setTextAppearance(getActivity(), android.R.style.TextAppearance_Small);
        autoCopyDesc.setPadding(dp(32), 0, 0, dp(8));
        root.addView(autoCopyDesc);

        View sep = new View(getActivity());
        sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        sep.setBackgroundColor(0x1A000000);
        root.addView(sep);

        TextView profilesTitle = new TextView(getActivity());
        profilesTitle.setText(R.string.profiles);
        profilesTitle.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        profilesTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        profilesTitle.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(profilesTitle);

        loadProfiles();
        listAdapter = new ArrayAdapter<String>(getActivity(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, getProfileNames()) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View view = super.getView(pos, convertView, parent);
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setText(profiles.get(pos).name);
                    tv.setPadding(dp(16), dp(12), dp(16), dp(12));
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

        Button addBtn = new Button(getActivity());
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

    private void showAddDialog() { showProfileDialog(-1, null, null); }

    private void showEditDialog(int index) {
        Profile p = profiles.get(index);
        showProfileDialog(index, p.name, p.command);
    }

    private void showProfileDialog(int index, String existingName, String existingCommand) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(index < 0 ? R.string.add_profile : R.string.edit_profile);
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        layout.setPadding(p, p, p, p);
        EditText nameInput = new EditText(getActivity());
        nameInput.setHint(R.string.profile_name);
        if (existingName != null) nameInput.setText(existingName);
        layout.addView(nameInput);
        EditText cmdInput = new EditText(getActivity());
        cmdInput.setHint(R.string.command_template_hint);
        cmdInput.setSingleLine(false);
        cmdInput.setLines(3);
        if (existingCommand != null) cmdInput.setText(existingCommand);
        layout.addView(cmdInput);
        builder.setView(layout);
        builder.setPositiveButton(R.string.save, (d, w) -> {
            String name = nameInput.getText().toString().trim();
            String cmd = cmdInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getActivity(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(cmd)) {
                Toast.makeText(getActivity(), "Command template cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (index < 0) profileManager.addProfile(new Profile(name, cmd));
            else profileManager.updateProfile(index, new Profile(name, cmd));
            refreshList();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showDeleteDialog(int index) {
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.delete_profile)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.save, (d, w) -> {
                    profileManager.deleteProfile(index);
                    refreshList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
