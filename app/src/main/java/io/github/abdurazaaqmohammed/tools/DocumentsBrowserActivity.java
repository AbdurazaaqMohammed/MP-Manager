package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.SafFs;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * The "Documents providers" screen: it collects Storage Access Framework
 * locations and nothing else. Tapping one hands it to {@link MainActivity},
 * which browses it in a pane like any other folder.
 *
 * <p>Only the system picker can grant a location. That is not a shortcut being
 * skipped: a provider that keeps its data behind
 * {@code android.permission.MANAGE_DOCUMENTS} (the one MP-Manager injects into
 * a patched APK, and the ones other apps ship for their own data) can be bound
 * by the system Documents UI alone. So the list is exactly what the user has
 * granted through the picker — the same set the system Files app offers —
 * rather than a guess at which providers exist.</p>
 */
public class DocumentsBrowserActivity extends BaseActivity {

    private static final String PREFS = "documents_browser";
    private static final String KEY_TREES = "granted_trees";
    private static final int REQ_PICK_TREE = 4711;

    private static final String[] ROOT_PROJECTION = {
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>();

    private ListView list;
    private TextView emptyView;
    private ProgressBar progress;
    private RowAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_documents_browser);
        MaterialToolbar toolbar = findViewById(R.id.docs_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.docs_list);
        emptyView = findViewById(R.id.docs_empty);
        progress = findViewById(R.id.docs_progress);
        MaterialButton add = findViewById(R.id.docs_add);
        add.setOnClickListener(v -> pickTree());
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openInPane(rows.get(position).treeUri));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(row.title)
                    .setItems(new String[]{getString(R.string.docs_forget)},
                            (d, which) -> forgetTree(row.treeUri))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        SafFs.init(this);
        loadLocations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLocations();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private Set<String> grantedTrees() {
        // A copy: the set handed out by getStringSet must not be mutated.
        return new LinkedHashSet<>(prefs().getStringSet(KEY_TREES, Collections.emptySet()));
    }

    private void loadLocations() {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Row> out = new ArrayList<>();
            for (String value : grantedTrees()) {
                Uri uri;
                try {
                    uri = Uri.parse(value);
                } catch (Exception e) {
                    continue;
                }
                Row row = new Row();
                row.treeUri = uri;
                row.title = displayName(uri);
                row.subtitle = uri.getAuthority();
                out.add(row);
            }
            handler.post(() -> {
                progress.setVisibility(View.GONE);
                rows.clear();
                rows.addAll(out);
                adapter.notifyDataSetChanged();
                boolean empty = rows.isEmpty();
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                list.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    /** Root title from the provider, falling back to its root summary and authority. */
    private String displayName(Uri treeUri) {
        Cursor cursor = null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            cursor = getContentResolver().query(
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
        // The root summary names the location ("App data (internal,
        // /data/data)") when the document query is not allowed.
        try {
            Cursor roots = getContentResolver().query(
                    DocumentsContract.buildRootsUri(treeUri.getAuthority()), ROOT_PROJECTION, null, null, null);
            if (roots != null) {
                try {
                    if (roots.moveToFirst()) {
                        String summary = roots.getString(1);
                        if (!TextUtils.isEmpty(summary)) return summary;
                    }
                } finally {
                    roots.close();
                }
            }
        } catch (Exception ignored) {
        }
        return treeUri.getAuthority();
    }

    private void pickTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_TREE);
        } catch (Exception e) {
            Extensions.showMessage(this, getString(R.string.docs_no_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // Not persistable: the tree still works for this session, it just
            // will not be listed after a restart.
        }
        Set<String> granted = grantedTrees();
        granted.add(uri.toString());
        prefs().edit().putStringSet(KEY_TREES, granted).apply();
        loadLocations();
    }

    private void forgetTree(Uri uri) {
        Set<String> granted = grantedTrees();
        if (!granted.remove(uri.toString())) return;
        prefs().edit().putStringSet(KEY_TREES, granted).apply();
        try {
            getContentResolver().releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        loadLocations();
    }

    /** Hands the location to the file manager: it browses it in a pane. */
    private void openInPane(Uri treeUri) {
        if (treeUri == null) return;
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra("safTree", treeUri.toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private static final class Row {
        Uri treeUri;
        String title;
        String subtitle;
    }

    private final class RowAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(DocumentsBrowserActivity.this);

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Row getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView
                    : inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
            Row row = getItem(position);
            ((TextView) view.findViewById(android.R.id.text1)).setText(row.title);
            TextView subtitle = view.findViewById(android.R.id.text2);
            boolean hasSubtitle = !TextUtils.isEmpty(row.subtitle);
            subtitle.setVisibility(hasSubtitle ? View.VISIBLE : View.GONE);
            subtitle.setText(row.subtitle);
            return view;
        }
    }
}
