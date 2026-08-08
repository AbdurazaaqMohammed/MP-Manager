package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

public class ProfileHelper {
    private MainActivity context;
    private final TextView portInput, userInput, passInput, ipInput;
    private MaterialAutoCompleteTextView profileSpinner;
    private ImageButton profileManageButton;
    private ProfileManager profileManager;
    public ProfileHelper(MainActivity context, TextView ipInput, TextView portInput, TextView userInput, TextView passInput, MaterialAutoCompleteTextView profileSpinner, ImageButton profileManageButton) {
        this.context = context;
        this.portInput = portInput;
        this.userInput = userInput;
        this.ipInput = ipInput;
        this.passInput = passInput;
        this.profileSpinner = profileSpinner;
        this.profileManageButton = profileManageButton;
    }

    public void setupProfileSpinner(boolean isServer) {
        setupProfileSpinner(isServer, null);
    }

    public void setupProfileSpinner(boolean isServer, FtpProfile toLoad) {
        if(profileManager == null) profileManager = new ProfileManager(context);
        List<FtpProfile> profiles = profileManager.getProfiles().stream()
                .filter(p -> p.isServerProfile() == isServer)
                .toList();

        String[] values = new String[profiles.size()];
        for (int i = 0, profilesSize = profiles.size(); i < profilesSize; i++) {
            FtpProfile profile = profiles.get(i);
            values[i] = profile.getName();
         }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, R.layout.dropdownitem, values) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null)
                    convertView = LayoutInflater.from(context).inflate(R.layout.dropdownitem, parent, false);
                TextView view = (TextView) convertView;
                view.setTextColor(context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                view.setText(values[position]);
                view.setOnClickListener(v -> {
                    profileSpinner.dismissDropDown();
                    settings.edit().putInt(isServer ? "lastSelectedServerProfile" : "lastSelectedClientProfile", position).apply();
                    FtpProfile profile = profiles.get(position);
                    if (profile != null) {
                        setupProfileSpinner(isServer, profile);
                        profileSpinner.dismissDropDown();
                    }
                });
                return convertView;
            }
        };

        if (!profiles.isEmpty()) {
            FtpProfile profile;
            if(toLoad == null) {
                int lastSelectedProfile = settings.getInt(isServer ? "lastSelectedServerProfile" : "lastSelectedClientProfile", 0);
                profileSpinner.setText(values[lastSelectedProfile]);
                profileSpinner.setAdapter(adapter);
                profile = profiles.get(lastSelectedProfile);
            } else {
                profileSpinner.setText(toLoad.getName());
                profileSpinner.setAdapter(adapter);
                profile = toLoad;
            }
            loadProfileIntoFields(profile);
        }

        profileManageButton.setOnClickListener(v -> showProfileManagementDialog(isServer));
    }

    private void loadProfileIntoFields(FtpProfile profile) {
        if(ipInput != null) ipInput.setText(profile.getIp());
        portInput.setText(String.valueOf(profile.getPort()));
        userInput.setText(profile.getUsername());
        passInput.setText(profile.getPassword());
    }

    private void showProfileManagementDialog(boolean isServer) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("Manage Profiles");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_profile_management, null);
        RecyclerView recyclerView = view.findViewById(R.id.profile_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        List<FtpProfile> profiles = profileManager.getProfiles().stream()
                .filter(p -> p.isServerProfile() == isServer)
                .toList();

        builder.setView(view).setPositiveButton("Close", null);
        AlertDialog ad = builder.show();
        ProfileAdapter adapter = new ProfileAdapter(profiles, profile -> {
            ad.dismiss();
            showEditProfileDialog(profiles.size() > 1, profile);
        }, () -> {
            ad.dismiss();
            showProfileDialog(true,null, isServer);
        });

        recyclerView.setAdapter(adapter);
    }

    private void showEditProfileDialog(boolean allowDelete, FtpProfile profile) {
        showProfileDialog(allowDelete, profile, profile.isServerProfile());
    }

    private void showProfileDialog(boolean allowDelete, FtpProfile profile, boolean isServer) {
        boolean isEdit = profile != null;
        String dialogTitle = isEdit ? "Edit Profile" : "Create Profile";

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_profile_edit, null);

        EditText nameInput = view.findViewById(R.id.profile_name);
        EditText ipInput = view.findViewById(R.id.profile_ip);
        if(isServer) ipInput.setVisibility(View.GONE);
        EditText portInput = view.findViewById(R.id.profile_port);
        EditText userInput = view.findViewById(R.id.profile_user);
        EditText passInput = view.findViewById(R.id.profile_pass);

        if (isEdit) {
            nameInput.setText(profile.getName());
            ipInput.setText(profile.getIp());
            portInput.setText(String.valueOf(profile.getPort()));
            userInput.setText(profile.getUsername());
            passInput.setText(profile.getPassword());
        } else {
            // Get current entered values
            nameInput.setText("New Profile");
            if(!isServer) ipInput.setText(this.ipInput.getText());
            portInput.setText(this.portInput.getText());
            userInput.setText(this.userInput.getText());
            passInput.setText(this.passInput.getText());
        }

        builder.setTitle(dialogTitle)
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String ip = ipInput.getText().toString().trim();
                    int port = Integer.parseInt(portInput.getText().toString());
                    String user = userInput.getText().toString().trim();
                    String pass = passInput.getText().toString();

                    FtpProfile newProfile = new FtpProfile(name, ip, port, user, pass, isServer);

                    if (isEdit) {
                        int i = profileManager.getProfiles().indexOf(profile);
                        profileManager.updateProfile(i, newProfile);
                    } else {
                        profileManager.addProfile(newProfile);
                    }

                    setupProfileSpinner(isServer, newProfile);
                })
                .setNegativeButton(android.R.string.cancel, null);

        if (isEdit) {
            builder.setNeutralButton("Delete", allowDelete ? (dialog, which) ->  {
                int i = profileManager.getProfiles().indexOf(profile);
                profileManager.deleteProfile(i);
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                String key = isServer ? "lastSelectedServerProfile" : "lastSelectedClientProfile";
                if(settings.getInt(key, 0) == i) {
                    settings.edit().putInt(key, 0).apply();
                }
                setupProfileSpinner(profile.isServerProfile());
            } : null);
        }

        builder.show().getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(allowDelete);
    }
}