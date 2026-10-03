package io.github.abdurazaaqmohammed.features.files;

import android.text.InputType;
import android.view.View;
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
    public boolean connectAndLoad(RemoteCredentials credentials, boolean pane1) {
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
        add.setOnClickListener(v -> showCreateDialog());
        box.addView(add);

        androidx.appcompat.app.AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.remote_connections)
                .setView(box)
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
            MaterialButton row = new MaterialButton(activity);
            row.setText(activity.rss.getString(R.string.remote_profile_row,
                    c.kind().name(), c.host()));
            row.setOnLongClickListener(v -> {
                profiles.remove(c.id());
                Extensions.showMessage(activity,
                        activity.rss.getString(R.string.remote_profile_removed, c.host()));
                v.post(() -> showConnectionsDialog());
                return true;
            });
            row.setOnClickListener(v -> {
                dialog.dismiss();
                connectAndLoad(c, activity.lastPaneSelected == 1);
            });
            box.addView(row, box.getChildCount() - 1);
        }
    }

    /** Minimal new-connection form: kind, host, port, user, password, path. */
    private void showCreateDialog() {
        List<RemoteCredentials.Kind> kinds = new ArrayList<>();
        for (RemoteCredentials.Kind k : RemoteCredentials.Kind.values()) {
            if (RemoteRegistry.isSupported(k)) kinds.add(k);
        }
        List<String> kindLabels = new ArrayList<>(kinds.size());
        for (RemoteCredentials.Kind k : kinds) kindLabels.add(k.name());

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);

        Spinner kindSpinner = new Spinner(activity);
        kindSpinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, kindLabels));
        box.addView(kindSpinner);

        EditText host = addField(box, R.string.remote_host,
                InputType.TYPE_CLASS_TEXT, "dav.example.com");
        EditText port = addField(box, R.string.remote_port, InputType.TYPE_CLASS_NUMBER, null);
        EditText user = addField(box, R.string.remote_user, InputType.TYPE_CLASS_TEXT, null);
        EditText pass = addField(box, R.string.remote_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
        EditText path = addField(box, R.string.remote_path_hint,
                InputType.TYPE_CLASS_TEXT, "/");
        // S3 signatures are region-scoped, so the region has to be part of the
        // profile or every bucket outside us-east-1 answers 301/403.
        EditText region = addField(box, R.string.remote_region,
                InputType.TYPE_CLASS_TEXT, "us-east-1");

        MaterialSwitch insecure = new MaterialSwitch(activity);
        insecure.setText(R.string.remote_insecure);
        box.addView(insecure);

        activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.remote_add_profile)
                .setView(box)
                .setPositiveButton(R.string.connect, (d, w) -> {
                    int idx = kindSpinner.getSelectedItemPosition();
                    if (idx < 0 || idx >= kinds.size()) return;
                    RemoteCredentials.Kind kind = kinds.get(idx);

                    // The host field is where people paste the whole DAV URL,
                    // so split scheme/host/port/path out of it instead of
                    // assuming each box holds exactly one thing. Defaulting to
                    // cleartext here silently broke every https-only server.
                    RemoteEndpoint.Parsed parsed = RemoteEndpoint.parse(
                            host.getText().toString(),
                            port.getText().toString(),
                            path.getText().toString());

                    if (parsed.host().isEmpty()) {
                        Extensions.showMessage(activity,
                                activity.rss.getString(R.string.remote_host_required));
                        return;
                    }

                    Map<String, String> extra = new LinkedHashMap<>(RemoteEndpoint.extrasFor(parsed));
                    if (RemoteCredentials.Kind.S3 == kind) {
                        String regionText = region.getText().toString().trim();
                        extra.put("region", regionText.isEmpty() ? "us-east-1" : regionText);
                        // Path-style is what MinIO, Ceph and most compatible
                        // servers expect, and AWS still accepts it.
                        extra.put("pathStyle", "true");
                    }

                    RemoteCredentials c = new RemoteCredentials(
                            kind.name().toLowerCase(Locale.US) + "-" + System.currentTimeMillis(),
                            kind,
                            parsed.host(),
                            parsed.port(),
                            user.getText().toString(),
                            pass.getText().toString(),
                            parsed.path(),
                            insecure.isChecked(),
                            extra);
                    profiles.put(c);
                    connectAndLoad(c, activity.lastPaneSelected == 1);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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