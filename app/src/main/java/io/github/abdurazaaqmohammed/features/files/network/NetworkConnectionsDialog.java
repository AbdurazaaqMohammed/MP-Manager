package io.github.abdurazaaqmohammed.features.files.network;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpProfile;
import io.github.abdurazaaqmohammed.data.prefs.SidebarPins;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.codehasan.colorpicker.extensions.Extensions;

/** A single connection editor. Switching protocol never loses the other tab's input. */
final class NetworkConnectionsDialog implements AutoCloseable {
    private static final String[] PROTOCOLS = {"ftp", "webdav", "smb"};
    private final MainActivity activity;
    private final NetworkController controller;
    private final AlertDialog dialog;
    private final TabLayout tabs;
    private final NestedScrollView scroll;
    private final Map<String, Form> forms = new LinkedHashMap<>();
    private final Map<String, NetworkProfile> discovered = new LinkedHashMap<>();
    private final LinearLayout results;
    private final TextView discoveryStatus;
    private NetworkDiscovery discovery;
    private String protocol = "ftp";

    NetworkConnectionsDialog(MainActivity activity, NetworkController controller) {
        this.activity = activity;
        this.controller = controller;
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_network_connections, null);
        tabs = root.findViewById(R.id.networkTabs);
        scroll = root.findViewById(R.id.networkScroll);
        // Bound the custom view on small/landscape screens; the form scrolls above the keyboard.
        scroll.getLayoutParams().height = Math.min(dp(440), activity.getResources().getDisplayMetrics().heightPixels * 3 / 5);
        LinearLayout container = root.findViewById(R.id.networkForms);
        for (int i = 0; i < PROTOCOLS.length; i++) {
            String p = PROTOCOLS[i];
            tabs.addTab(tabs.newTab().setText(new int[]{R.string.network_ftp_tab, R.string.webdav_client, R.string.network_smb_tab}[i]), false);
            Form form = new Form(p);
            forms.put(p, form);
            container.addView(form.view);
        }
        Form smb = forms.get("smb");
        TextView heading = new TextView(activity);
        heading.setText(R.string.network_discover);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        heading.setPadding(0, dp(16), 0, dp(4));
        smb.view.addView(heading, 1);
        MaterialButton scan = textButton(R.string.network_scan_smb);
        scan.setOnClickListener(v -> startDiscovery(true));
        smb.view.addView(scan, 2);
        discoveryStatus = new TextView(activity);
        discoveryStatus.setText(R.string.network_discovery_help);
        smb.view.addView(discoveryStatus, 3);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        smb.view.addView(results, 4);
        dialog = activity.dialogUtil.getDialogBuilder().setTitle(R.string.network_connections).setView(root)
                .setNegativeButton(android.R.string.cancel, null).setNeutralButton(R.string.save, null)
                .setPositiveButton(R.string.connect, null).create();
        dialog.setOnDismissListener(d -> stopDiscovery());
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit(true));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> submit(false));
            if (dialog.getWindow() != null)
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { select(PROTOCOLS[tab.getPosition()]); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    boolean isShowing() { return dialog.isShowing(); }

    void show(String protocol) {
        boolean first = !dialog.isShowing();
        if (first) dialog.show();
        int index = List.of(PROTOCOLS).indexOf(protocol);
        if (index < 0) index = 0;
        int previous = tabs.getSelectedTabPosition();
        tabs.selectTab(tabs.getTabAt(index));
        if (previous == index) select(PROTOCOLS[index]);
        if (first) reloadSaved();
    }

    private void select(String p) {
        protocol = p;
        for (Form form : forms.values()) form.view.setVisibility(form.protocol.equals(p) ? View.VISIBLE : View.GONE);
        scroll.scrollTo(0, 0);
        if (p.equals("smb")) startDiscovery(false);
        else stopDiscovery();
    }

    void loadProfile(NetworkProfile profile) {
        forms.get(profile.protocol).load(profile, true);
    }

    void loadFtpProfile(FtpProfile profile) { forms.get("ftp").load(profile); }

    private void reloadSaved() {
        List<FtpProfile> ftpProfiles = activity.getFtp().clientProfiles();
        Form ftp = forms.get("ftp");
        ftp.ftpProfiles = ftpProfiles;
        ftp.refreshSaved();
        if (!ftp.dirty && ftp.savedName == null && !ftpProfiles.isEmpty()) {
            int last = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity).getInt("lastSelectedClientProfile", 0);
            ftp.load(ftpProfiles.get(Math.max(0, Math.min(ftpProfiles.size() - 1, last))));
        }
        controller.loadSaved(profiles -> {
            if (!isShowing()) return;
            for (String p : new String[]{"webdav", "smb"}) {
                Form form = forms.get(p);
                form.profiles = new ArrayList<>();
                for (NetworkProfile profile : profiles) if (p.equals(profile.protocol)) form.profiles.add(profile);
                form.refreshSaved();
            }
        });
    }

    private void submit(boolean connect) {
        Form form = forms.get(protocol);
        boolean pin = form.pin.isChecked();
        boolean remember = form.remember.isChecked() || pin;
        if (protocol.equals("ftp")) {
            String host = form.address.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(form.port.getText().toString());
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) { form.port.setError(activity.getString(R.string.network_invalid_port)); return; }
            try { new java.net.URI("ftp", null, host, port, "/", null, null); }
            catch (Exception e) { form.address.setError(activity.getString(R.string.network_invalid_host)); return; }
            if (host.isEmpty() || host.contains("/") || host.contains("\\")) {
                form.address.setError(activity.getString(R.string.network_invalid_host)); return;
            }
            String name = form.name.getText().toString().trim();
            if (name.isEmpty()) name = host + ":" + port;
            int security = 0;
            String[] options = activity.getResources().getStringArray(R.array.ftp_security_options);
            for (int i = 0; i < options.length; i++) if (options[i].contentEquals(form.security.getText())) security = i;
            FtpProfile profile = new FtpProfile(name, host, port, form.username.getText().toString(),
                    form.password.getText().toString(), false, security);
            if (!connect || remember) {
                activity.getFtp().saveClientProfile(profile, form.savedName, pin);
                form.load(profile);
                reloadSaved();
            }
            if (connect) {
                boolean pane1 = activity.lastPaneSelected == 1;
                dialog.dismiss();
                activity.getFtp().connect(profile, pane1);
            } else Extensions.showMessage(activity, R.string.network_connection_saved);
        } else {
            NetworkProfile profile = new NetworkProfile();
            profile.protocol = protocol;
            profile.name = form.name.getText().toString().trim();
            profile.address = form.address.getText().toString().trim();
            profile.username = form.username.getText().toString();
            profile.password = form.password.getText().toString();
            profile.domain = form.domain == null ? "" : form.domain.getText().toString().trim();
            profile.previousName = form.savedName;
            try { profile.validate(); }
            catch (IllegalArgumentException e) { form.address.setError(e.getMessage()); return; }
            if (connect) {
                boolean pane1 = activity.lastPaneSelected == 1;
                dialog.dismiss();
                controller.connect(profile, remember, pin, pane1);
            } else controller.save(profile, pin, () -> {
                if (!isShowing()) return;
                form.load(profile, true);
                reloadSaved();
                Extensions.showMessage(activity, R.string.network_connection_saved);
            });
        }
    }

    private void startDiscovery(boolean rescan) {
        if (!isShowing() || !protocol.equals("smb")) return;
        if (discovery != null && !rescan) return;
        controller.withLocalNetworkPermission(() -> {
            if (!isShowing() || !protocol.equals("smb")) return;
            stopDiscovery();
            if (rescan) { discovered.clear(); results.removeAllViews(); }
            NetworkDiscovery session = new NetworkDiscovery(activity, new NetworkDiscovery.Listener() {
                @Override public void found(NetworkProfile profile) {
                    if (!isShowing()) return;
                    discovered.put(NetworkDiscovery.endpointKey(profile), profile);
                    results.removeAllViews();
                    for (NetworkProfile found : discovered.values()) {
                        MaterialButton result = textButton(0);
                        result.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        result.setText((found.protocol.equals("smb") ? "SMB • " : "WebDAV • ") + found.name + "\n" + found.address);
                        result.setOnClickListener(v -> {
                            Form target = forms.get(found.protocol);
                            target.load(found, false);
                            if (!found.protocol.equals(protocol)) show(found.protocol);
                            else scroll.scrollTo(0, 0);
                            target.address.requestFocus();
                            target.address.setSelection(target.address.length());
                        });
                        results.addView(result);
                    }
                }
                @Override public void status(String message) { discoveryStatus.setText(message); }
            });
            discovery = session;
            session.start();
            session.scanSmb();
        });
    }

    private void stopDiscovery() {
        if (discovery != null) discovery.close();
        discovery = null;
    }

    @Override public void close() { stopDiscovery(); dialog.dismiss(); }

    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f); }
    private MaterialButton textButton(int label) {
        MaterialButton button = (MaterialButton) LayoutInflater.from(activity).inflate(R.layout.item_network_connection_button, null);
        if (label != 0) button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private final class Form {
        final String protocol;
        final LinearLayout view;
        final MaterialAutoCompleteTextView saved;
        final EditText name, address, username, password, port, domain;
        final MaterialAutoCompleteTextView security;
        final MaterialCheckBox remember, pin;
        final MaterialButton delete;
        List<NetworkProfile> profiles = new ArrayList<>();
        List<FtpProfile> ftpProfiles = new ArrayList<>();
        String savedName;
        boolean loading, dirty;

        Form(String p) {
            protocol = p;
            view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setVisibility(View.GONE);
            TextView help = new TextView(activity);
            help.setText(p.equals("ftp") ? R.string.network_ftp_help
                    : p.equals("smb") ? R.string.network_smb_help : R.string.network_webdav_help);
            view.addView(help);
            TextInputLayout savedBox = UiFields.box(activity, activity.getString(R.string.network_saved_connections));
            saved = new MaterialAutoCompleteTextView(activity);
            saved.setInputType(InputType.TYPE_NULL);
            savedBox.addView(saved, new LinearLayout.LayoutParams(-1, -2));
            savedBox.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
            addBox(savedBox);
            name = field(R.string.network_connection_name, InputType.TYPE_CLASS_TEXT);
            address = field(p.equals("ftp") ? R.string.ip_address_hint
                    : p.equals("smb") ? R.string.network_smb_address : R.string.network_webdav_address,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            port = p.equals("ftp") ? field(R.string.port, InputType.TYPE_CLASS_NUMBER) : null;
            username = field(R.string.network_username, InputType.TYPE_CLASS_TEXT);
            password = field(R.string.network_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            domain = p.equals("smb") ? field(R.string.network_domain, InputType.TYPE_CLASS_TEXT) : null;
            if (p.equals("ftp")) {
                TextInputLayout box = UiFields.box(activity, activity.getString(R.string.ftp_security));
                security = new MaterialAutoCompleteTextView(activity);
                security.setInputType(InputType.TYPE_NULL);
                box.addView(security, new LinearLayout.LayoutParams(-1, -2));
                box.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
                addBox(box);
                security.setSimpleItems(activity.getResources().getStringArray(R.array.ftp_security_options));
            } else security = null;
            remember = new MaterialCheckBox(activity);
            remember.setText(p.equals("ftp") ? R.string.network_remember_ftp : R.string.network_remember);
            view.addView(remember);
            pin = new MaterialCheckBox(activity);
            pin.setText(R.string.pin_to_sidebar);
            view.addView(pin);
            pin.setOnCheckedChangeListener((button, checked) -> {
                if (checked) remember.setChecked(true);
                remember.setEnabled(!checked);
            });
            delete = textButton(R.string.network_delete_connection);
            view.addView(delete);
            delete.setOnClickListener(v -> deleteSaved());
            saved.setOnItemClickListener((parent, v, index, id) -> {
                if (index == 0) { reset(); dirty = true; }
                else if (p.equals("ftp")) load(ftpProfiles.get(index - 1));
                else load(profiles.get(index - 1), true);
            });
            reset();
            TextWatcher edits = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!loading) dirty = true; }
                @Override public void afterTextChanged(Editable s) {}
            };
            for (EditText edit : new EditText[]{name, address, port, username, password, domain, security})
                if (edit != null) edit.addTextChangedListener(edits);
        }

        private void addBox(TextInputLayout box) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            view.addView(box, params);
        }

        private EditText field(int hint, int type) {
            TextInputLayout box = UiFields.box(activity, activity.getString(hint));
            EditText edit = UiFields.field(box, type);
            edit.setSingleLine(true);
            if (hint == R.string.network_password) box.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            addBox(box);
            return edit;
        }

        void refreshSaved() {
            List<String> names = new ArrayList<>();
            names.add(activity.getString(R.string.network_new_connection));
            if (protocol.equals("ftp")) for (FtpProfile profile : ftpProfiles) names.add(profile.getName());
            else for (NetworkProfile profile : profiles) names.add(profile.name);
            saved.setSimpleItems(names.toArray(new String[0]));
            saved.setText(savedName == null ? names.get(0) : savedName, false);
            delete.setVisibility(savedName == null ? View.GONE : View.VISIBLE);
        }

        void reset() {
            loading = true;
            savedName = null;
            name.setText("");
            address.setText(protocol.equals("ftp") ? "" : protocol.equals("smb") ? "smb://" : "https://");
            username.setText(""); password.setText("");
            if (port != null) port.setText("21");
            if (domain != null) domain.setText("");
            if (security != null) security.setText(activity.getResources().getStringArray(R.array.ftp_security_options)[0], false);
            pin.setChecked(false); remember.setChecked(false);
            address.setError(null);
            dirty = false; loading = false;
            refreshSaved();
        }

        void load(NetworkProfile profile, boolean savedProfile) {
            loading = true;
            savedName = savedProfile ? profile.name : null;
            name.setText(profile.name); address.setText(profile.address);
            username.setText(profile.username); password.setText(profile.password);
            if (domain != null) domain.setText(profile.domain);
            pin.setChecked(savedProfile && SidebarPins.isPinned(activity, profile.pinId()));
            remember.setChecked(savedProfile);
            address.setError(null);
            dirty = !savedProfile; loading = false;
            refreshSaved();
        }

        void load(FtpProfile profile) {
            loading = true;
            savedName = profile.getName();
            name.setText(profile.getName()); address.setText(profile.getIp()); port.setText(String.valueOf(profile.getPort()));
            username.setText(profile.getUsername()); password.setText(profile.getPassword());
            String[] options = activity.getResources().getStringArray(R.array.ftp_security_options);
            security.setText(options[Math.max(0, Math.min(options.length - 1, profile.getSecurityType()))], false);
            pin.setChecked(SidebarPins.isPinned(activity, SidebarPins.FTP_PREFIX + profile.getName()));
            remember.setChecked(true);
            for (int i = 0; i < ftpProfiles.size(); i++) if (ftpProfiles.get(i).getName().equals(profile.getName())) {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("lastSelectedClientProfile", i).apply();
                break;
            }
            dirty = false; loading = false;
            refreshSaved();
        }

        private void deleteSaved() {
            if (savedName == null) return;
            String name = savedName;
            activity.dialogUtil.getDialogBuilder().setTitle(R.string.network_delete_connection)
                    .setMessage(activity.getString(R.string.network_delete_connection_confirm, name))
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> {
                        if (protocol.equals("ftp")) {
                            activity.getFtp().deleteClientProfile(name);
                            reset(); reloadSaved();
                        } else {
                            NetworkProfile profile = new NetworkProfile();
                            profile.protocol = protocol; profile.name = name;
                            controller.deleteProfile(profile, () -> { if (isShowing()) { reset(); reloadSaved(); } });
                        }
                    }).show();
        }
    }
}
