package io.github.abdurazaaqmohammed.features.files;

import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map;

import androidx.recyclerview.widget.RecyclerView;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.RemoteFilesArrayAdapter;
import io.github.abdurazaaqmohammed.data.remote.RemoteEndpoint;
import io.github.abdurazaaqmohammed.data.remote.RemoteProfiles;
import io.github.abdurazaaqmohammed.data.remote.RemoteRegistry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;
import io.github.abdurazaaqmohammed.domain.remote.RemoteListing;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Drives one pane over a {@link RemoteFileSystem}.
 *
 * <p>This is the piece that decides whether the abstraction was worth building:
 * it fills a pane's adapter, keeps the path label in sync and synthesises the
 * parent row, exactly as FtpController does for FTP -- but without knowing which
 * protocol is underneath.
 *
 * <p>Every network call runs on a worker thread; only the adapter swap and the
 * two labels touch the UI. Operations that need per-protocol handling
 * (delete/mkdir/rename/download) are deliberately not offered yet rather than
 * stubbed to a no-op.
 */
public class RemotePaneController {

    private final MainActivity activity;
    private final RemoteProfiles profiles;

    private RemoteFileSystem fs;
    private String currentPath = "/";
    private RemoteCredentials connectedCredentials;
    private int activePane = 1;

    public RemotePaneController(MainActivity activity) {
        this.activity = activity;
        this.profiles = new RemoteProfiles(activity);
    }

    // ------------------------------------------------------------- connecting

    /**
     * Open a profile and take over one pane.
     *
     * @return true when the pane is now showing the remote listing
     */
    /** True when the remote pane is already showing this saved profile. */
    public boolean isConnectedTo(RemoteCredentials credentials) {
        return fs != null && connectedCredentials != null
                && connectedCredentials.id().equals(credentials.id());
    }

    /** Drops a saved profile and, when it was the live one, closes the remote pane. */
    public void removeProfile(RemoteCredentials credentials) {
        profiles.remove(credentials.id());
        if (isConnectedTo(credentials)) {
            connectedCredentials = null;
            if (fs != null) {
                try {
                    fs.disconnect();
                    fs.close();
                } catch (Exception ignored) {
                }
                fs = null;
            }
        }
        Extensions.showMessage(activity,
                activity.rss.getString(R.string.remote_profile_removed, credentials.host()));
    }

    public boolean connectAndLoad(RemoteCredentials credentials, boolean pane1) {
        // FTP goes to the application's own client: it already handles
        // transfers, file operations and server mode, all of which this
        // abstraction deliberately leaves out.
        if (isFtpKind(credentials.kind())) {
            Extensions.showMessage(activity,
                    activity.rss.getString(R.string.remote_ftp_handoff));
            activity.showFtpClientDialog();
            return true;
        }
        if (!RemoteRegistry.isSupported(credentials.kind())) {
            Extensions.showMessage(activity, activity.rss.getString(
                    R.string.remote_kind_unsupported, credentials.kind().name()));
            return false;
        }
        activePane = pane1 ? 1 : 2;
        Extensions.showMessage(activity,
                activity.rss.getString(R.string.remote_connecting, credentials.host()));
        new Thread(() -> {
            RemoteFileSystem opened = null;
            try {
                opened = profiles.create(credentials);
                opened.connect(credentials);
                RemoteFileSystem ready = opened;
                List<RemoteEntry> entries = RemoteListing.sort(ready.list(ready.root()), "name", false);
                activity.runOnUiThread(() -> {
                    if (fs != null && fs != ready) {
                        // A newer connection replaced this one while it was
                        // still loading; drop it rather than fight it.
                        ready.disconnect();
                        ready.close();
                        return;
                    }
                    fs = ready;
                    connectedCredentials = credentials;
                    currentPath = ready.root();
                    applyListing(entries);
                });
            } catch (RemoteException | RuntimeException e) {
                if (opened != null) {
                    try {
                        opened.disconnect();
                        opened.close();
                    } catch (Exception ignored) {
                        // Already failing; nothing further to salvage.
                    }
                }
                String message = e.getMessage();
                activity.runOnUiThread(() -> Extensions.showMessage(activity,
                        activity.rss.getString(R.string.remote_connect_failed,
                                message == null ? e.getClass().getSimpleName() : message)));
            }
        }).start();
        return true;
    }

    public void disconnect() {
        RemoteFileSystem old = fs;
        fs = null;
        if (old == null) return;
        new Thread(() -> {
            // disconnect() and close() declare no checked exception; a backend
            // that fails here is already unusable, so only guard the runtime
            // failures a teardown can plausibly throw.
            try {
                old.disconnect();
            } catch (RuntimeException ignored) {
                // Best effort.
            }
            try {
                old.close();
            } catch (RuntimeException ignored) {
                // Best effort.
            }
        }).start();
    }

    public boolean isConnected() {
        return fs != null && fs.isConnected();
    }

    // -------------------------------------------------------------- browsing

    public void openDirectory(String path, boolean pane1) {
        if (fs == null) return;
        activePane = pane1 ? 1 : 2;
        RemoteFileSystem backend = fs;
        new Thread(() -> {
            try {
                List<RemoteEntry> entries =
                        RemoteListing.sort(backend.list(path), "name", false);
                activity.runOnUiThread(() -> {
                    if (fs != backend) return;
                    currentPath = path;
                    applyListing(entries);
                });
            } catch (RemoteException e) {
                activity.runOnUiThread(() -> Extensions.showMessage(activity,
                        activity.rss.getString(R.string.remote_list_failed,
                                String.valueOf(e.getMessage()))));
            }
        }).start();
    }

    public void navigateUp(boolean pane1) {
        if (fs == null) return;
        activePane = pane1 ? 1 : 2;
        if (!RemoteListing.wantsParentLink(currentPath)) return;
        openDirectory(parentOf(currentPath), pane1);
    }

    public String currentPath() {
        return currentPath;
    }

    private void applyListing(List<RemoteEntry> entries) {
        boolean wantsParent = RemoteListing.wantsParentLink(currentPath);
        List<RemoteEntry> rows = new ArrayList<>(entries.size() + 1);
        if (wantsParent) {
            rows.add(RemoteEntry.directory(parentOf(currentPath),
                    RemoteFilesArrayAdapter.PARENT_NAME, 0L));
        }
        rows.addAll(entries);

        RecyclerView pane = activity.findViewById(
                activePane == 1 ? R.id.listViewPane1 : R.id.listViewPane2);
        pane.setAdapter(new RemoteFilesArrayAdapter(
                activity, rows, activePane == 1, callbacks()));

        TextView pathLabel = activity.findViewById(R.id.currentFolderPath);
        pathLabel.setText(activity.rss.getString(R.string.remote_path_label, currentPath));
        activity.uiHelper.scrollTextView(pathLabel);

        int folders = 0;
        for (RemoteEntry e : entries) {
            if (e.directory()) folders++;
        }
        TextView counts = activity.findViewById(R.id.folderCount);
        counts.setText(activity.rss.getString(R.string.folder_file_count,
                folders, entries.size() - folders));

        Extensions.showMessage(activity,
                activity.rss.getString(R.string.remote_loaded, rows.size()));
    }

    /** Parent of a normalised absolute path; "/" is its own parent. */
    static String parentOf(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return "/";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');
        if (slash <= 0) return "/";
        return p.substring(0, slash);
    }

    private RemoteFilesArrayAdapter.Callbacks callbacks() {
        return new RemoteFilesArrayAdapter.Callbacks() {
            @Override
            public void onEnterDirectory(RemoteEntry entry, boolean pane1) {
                openDirectory(entry.path(), pane1);
            }

            @Override
            public void onNavigateUp(boolean pane1) {
                navigateUp(pane1);
            }

            @Override
            public void onEntryMenu(RemoteEntry entry, View anchor, boolean pane1) {
                // Delete/mkdir/rename/download need per-backend handling that is
                // not built yet; saying so beats a menu of dead items.
                Extensions.showMessage(activity,
                        activity.rss.getString(R.string.remote_ops_pending, entry.name()));
            }
        };
    }

    // --------------------------------------------------------------- dialogs

    /** Entry point for the sidebar item: pick a saved profile, or create one. */
    public void showConnectionsDialog() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);

        MaterialButton add = new MaterialButton(activity);
        add.setText(R.string.remote_add_profile);
        add.setOnClickListener(v -> showFormDialog(null));
        box.addView(add);

        // Profiles are added below, and several rows overflow a phone screen; a
        // bare LinearLayout in a dialog is clipped rather than scrolled.
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(box);

        androidx.appcompat.app.AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.remote_connections)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        // Rows are added after show() so each one can dismiss the dialog: the
        // listing renders into a pane behind this window, so leaving it up hides
        // the result of a successful connection.
        List<RemoteCredentials> saved = profiles.load();
        if (saved.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.remote_no_profiles);
            box.addView(empty, 0);
            return;
        }
        for (RemoteCredentials c : saved) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);

            MaterialButton open = new MaterialButton(activity);
            open.setText(activity.rss.getString(R.string.remote_profile_row,
                    c.kind().name(), c.host()));
            open.setOnClickListener(v -> {
                dialog.dismiss();
                connectAndLoad(c, activity.lastPaneSelected == 1);
            });
            open.setOnLongClickListener(v -> {
                dialog.dismiss();
                confirmDelete(c);
                return true;
            });
            row.addView(open);

            MaterialButton edit = new MaterialButton(activity);
            edit.setText(R.string.remote_edit);
            edit.setOnClickListener(v -> {
                dialog.dismiss();
                showFormDialog(c);
            });
            row.addView(edit);

            box.addView(row, box.getChildCount() - 1);
        }
    }

    /** Long-press a profile row in the list to delete it. */
    private void confirmDelete(RemoteCredentials c) {
        profiles.remove(c.id());
        Extensions.showMessage(activity,
                activity.rss.getString(R.string.remote_profile_removed, c.host()));
        showConnectionsDialog();
    }

    /**
     * Protocols this form can create a profile for.
     *
     * <p>FTP is creatable even though it is not served here: the settings live
     * in one place, and opening the profile hands off to the built-in client.
     * Omitting it would mean FTP could only be configured from the old dialog,
     * which is the duplication this screen was meant to remove.
     */
    private static List<RemoteCredentials.Kind> creatableKinds() {
        List<RemoteCredentials.Kind> kinds = new ArrayList<>();
        for (RemoteCredentials.Kind k : RemoteCredentials.Kind.values()) {
            if (RemoteRegistry.isSupported(k) || isFtpKind(k)) kinds.add(k);
        }
        return kinds;
    }

    /** Readable protocol names; the raw enum names mean nothing to a user. */
    private String kindLabel(RemoteCredentials.Kind kind) {
        switch (kind) {
            case FTP: return activity.getString(R.string.remote_kind_ftp);
            case FTPS_EXPLICIT: return activity.getString(R.string.remote_kind_ftps_explicit);
            case FTPS_IMPLICIT: return activity.getString(R.string.remote_kind_ftps_implicit);
            case WEBDAV: return activity.getString(R.string.remote_kind_webdav);
            case S3: return activity.getString(R.string.remote_kind_s3);
            case SFTP: return activity.getString(R.string.remote_kind_sftp);
            default: return kind.name();
        }
    }

    /** Fields of the connection form, rebuilt whenever the protocol changes. */
    private static final class Form {
        EditText host;
        EditText port;
        EditText user;
        EditText pass;
        EditText path;
        EditText region;
        EditText privateKey;
        MaterialSwitch insecure;
    }

    /**
     * Add or edit a profile.
     *
     * @param existing the profile to edit, or null to create a new one
     */
    public void showFormDialog(RemoteCredentials existing) {
        List<RemoteCredentials.Kind> kinds = creatableKinds();

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);

        List<String> kindLabels = new ArrayList<>(kinds.size());
        for (RemoteCredentials.Kind k : kinds) kindLabels.add(kindLabel(k));

        Spinner kindSpinner = new Spinner(activity);
        kindSpinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, kindLabels));
        int initial = existing == null ? 0 : Math.max(0, kinds.indexOf(existing.kind()));
        kindSpinner.setSelection(initial);
        box.addView(kindSpinner);

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        box.addView(form);

        MaterialButton test = new MaterialButton(activity);
        test.setText(R.string.remote_test_connection);
        box.addView(test);

        Form f = new Form();
        buildFields(form, f, kinds.get(initial), existing);

        // Rebuilding on every selection would wipe what the user just typed the
        // first time the spinner fires, so the initial pass is skipped.
        final boolean[] first = {true};
        kindSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (first[0]) {
                    first[0] = false;
                    return;
                }
                if (pos < 0 || pos >= kinds.size()) return;
                form.removeAllViews();
                buildFields(form, f, kinds.get(pos), null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        androidx.appcompat.app.AlertDialog.Builder builder =
                activity.dialogUtil.getDialogBuilder()
                        .setTitle(existing == null
                                ? R.string.remote_add_profile : R.string.remote_edit)
                        .setView(box)
                        .setPositiveButton(R.string.connect, (d, w) -> {
                            int pos = kindSpinner.getSelectedItemPosition();
                            if (pos < 0 || pos >= kinds.size()) return;
                            RemoteCredentials built = assemble(kinds.get(pos), f, existing);
                            if (built == null) return;
                            profiles.put(built);
                            connectAndLoad(built, activity.lastPaneSelected == 1);
                        })
                        .setNegativeButton(android.R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.remote_delete, (d, w) -> confirmDelete(existing));
        }
        builder.show();

        test.setOnClickListener(v -> {
            int pos = kindSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= kinds.size()) return;
            RemoteCredentials probe = assemble(kinds.get(pos), f, existing);
            if (probe == null) return;
            if (isFtpKind(probe.kind())) {
                // There is no backend to probe: FTP is served by the built-in
                // client, which reports its own errors when it connects.
                Extensions.showMessage(activity,
                        activity.rss.getString(R.string.remote_ftp_handoff));
                return;
            }
            testConnection(probe);
        });
    }

    /**
     * Adds only the fields that protocol uses, so the form does not show an S3
     * region box to someone configuring WebDAV.
     */
    private void buildFields(LinearLayout form, Form f,
                             RemoteCredentials.Kind kind, RemoteCredentials existing) {
        boolean s3 = kind == RemoteCredentials.Kind.S3;
        boolean sftp = kind == RemoteCredentials.Kind.SFTP;

        f.host = addField(form, s3 ? R.string.remote_endpoint : R.string.remote_host,
                InputType.TYPE_CLASS_TEXT,
                existing == null ? defaultHost(kind) : existing.host());
        f.port = addField(form, R.string.remote_port, InputType.TYPE_CLASS_NUMBER, null);
        if (existing != null && existing.port() > 0) {
            f.port.setText(String.valueOf(existing.port()));
        } else {
            f.port.setHint(defaultPort(kind));
        }
        f.user = addField(form, s3 ? R.string.remote_access_key : R.string.remote_user,
                InputType.TYPE_CLASS_TEXT, existing == null ? null : existing.username());
        f.pass = addField(form, s3 ? R.string.remote_secret_key : R.string.remote_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                existing == null ? null : existing.password());
        f.path = addField(form, s3 ? R.string.remote_bucket : R.string.remote_path_hint,
                InputType.TYPE_CLASS_TEXT,
                existing == null ? (s3 ? null : "/") : existing.path());

        if (s3) {
            f.region = addField(form, R.string.remote_region, InputType.TYPE_CLASS_TEXT,
                    existing == null ? "us-east-1" : existing.extra("region", "us-east-1"));
        }
        if (sftp) {
            f.privateKey = addField(form, R.string.remote_private_key, InputType.TYPE_CLASS_TEXT,
                    existing == null ? null : existing.extra("privateKey", ""));
        }

        f.insecure = new MaterialSwitch(activity);
        f.insecure.setText(R.string.remote_insecure);
        if (existing != null) f.insecure.setChecked(existing.insecure());
        form.addView(f.insecure);
    }

    /** Turns the form into credentials, or reports the first missing value. */
    private RemoteCredentials assemble(RemoteCredentials.Kind kind, Form f,
                                       RemoteCredentials existing) {
        RemoteEndpoint.Parsed parsed = RemoteEndpoint.parse(
                text(f.host), text(f.port), text(f.path));
        if (parsed.host().isEmpty()) {
            Extensions.showMessage(activity,
                    activity.rss.getString(R.string.remote_host_required));
            return null;
        }
        Map<String, String> extra = new LinkedHashMap<>(RemoteEndpoint.extrasFor(parsed));
        if (kind == RemoteCredentials.Kind.SFTP && f.privateKey != null) {
            String key = text(f.privateKey);
            if (!key.isEmpty()) extra.put("privateKey", key);
        }
        if (kind == RemoteCredentials.Kind.S3) {
            String region = f.region == null ? "" : text(f.region);
            extra.put("region", region.isEmpty() ? "us-east-1" : region);
            // Path-style is what MinIO, Ceph and most compatible servers expect,
            // and AWS still accepts it.
            extra.put("pathStyle", "true");
        }
        String id = existing != null ? existing.id()
                : kind.name().toLowerCase(Locale.US) + "-" + System.currentTimeMillis();
        return new RemoteCredentials(id, kind, parsed.host(), parsed.port(),
                text(f.user), text(f.pass), parsed.path(),
                f.insecure != null && f.insecure.isChecked(), extra);
    }

    /**
     * Opens a second connection just to prove the settings work.
     *
     * <p>The probe is always closed, so an established listing is never left
     * holding a second session to the same server.
     */
    private void testConnection(RemoteCredentials probe) {
        Extensions.showMessage(activity,
                activity.rss.getString(R.string.remote_testing, probe.host()));
        new Thread(() -> {
            RemoteFileSystem test = null;
            String result;
            try {
                test = profiles.create(probe);
                test.connect(probe);
                int count = test.list(test.root()).size();
                result = activity.rss.getString(R.string.remote_test_ok, count);
            } catch (Exception e) {
                String message = e.getMessage();
                result = activity.rss.getString(R.string.remote_test_failed,
                        message == null ? e.getClass().getSimpleName() : message);
            } finally {
                if (test != null) {
                    try {
                        test.disconnect();
                        test.close();
                    } catch (Exception ignored) {
                        // best effort
                    }
                }
            }
            final String message = result;
            activity.runOnUiThread(() -> Extensions.showMessage(activity, message));
        }).start();
    }

    static boolean isFtpKind(RemoteCredentials.Kind kind) {
        return kind == RemoteCredentials.Kind.FTP
                || kind == RemoteCredentials.Kind.FTPS_EXPLICIT
                || kind == RemoteCredentials.Kind.FTPS_IMPLICIT;
    }

    private static String text(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String defaultHost(RemoteCredentials.Kind kind) {
        switch (kind) {
            case S3: return "s3.amazonaws.com";
            case WEBDAV: return "dav.example.com";
            default: return null;
        }
    }

    /** Shown as the port placeholder, so it reads as "leave blank for this". */
    private static String defaultPort(RemoteCredentials.Kind kind) {
        switch (kind) {
            case SFTP: return "22";
            case S3: return "443";
            case WEBDAV: return "443";
            default: return "21";
        }
    }

    /**
     * Adds a labelled field to {@code box} and returns the editable part.
     *
     * <p>Adds the {@link TextInputLayout} itself, never {@code field.getParent()}:
     * TextInputLayout.addView() hands an added EditText to setEditText(), which
     * re-parents it into an internal container. That container is already a child
     * of the layout, so adding it to {@code box} fails with "child already has a
     * parent".
     */
    private EditText addField(LinearLayout box, int labelRes, int inputType, String initial) {
        TextInputLayout layout = UiFields.box(activity, activity.rss.getString(labelRes));
        EditText field = UiFields.field(layout, inputType);
        if (initial != null && !initial.isEmpty()) field.setText(initial);
        box.addView(layout);
        return field;
    }

}