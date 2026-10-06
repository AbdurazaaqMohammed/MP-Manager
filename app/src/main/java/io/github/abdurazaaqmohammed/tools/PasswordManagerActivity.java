package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.ArchivePasswordStore;
import io.github.abdurazaaqmohammed.vault.BwApi;
import io.github.abdurazaaqmohammed.vault.BwTotp;
import io.github.abdurazaaqmohammed.vault.BwVault;

/**
 * Keeps the passwords worth remembering for archives, in the order they should
 * be tried, and shows which one last worked. Also carries the Bitwarden section:
 * connect to a self-hosted server (NodeWarden first), unlock and sync the vault.
 */
public class PasswordManagerActivity extends BaseActivity {

    private static final String BW_PREFS = "bitwarden";
    private static final String BW_SESSION = "session";
    private static final String BW_DEVICE = "device_id";
    private static final String BW_LAST_SERVER = "last_server";
    private static final String BW_LAST_EMAIL = "last_email";

    private final List<String> items = new ArrayList<>();
    private Adapter adapter;
    private TextView count;
    private TextView empty;
    private TextView recent;

    private TextView bwStatus;
    private MaterialButton bwAction;
    private MaterialButton bwLogout;
    private final List<BwVault.Entry> bwEntries = new ArrayList<>();
    private final VaultAdapter bwAdapter = new VaultAdapter();
    private BwVault.Session bwSession;
    private BwVault.Result bwVault;
    private boolean bwBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_manager);

        MaterialToolbar toolbar = findViewById(R.id.pw_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        count = findViewById(R.id.pw_count);
        empty = findViewById(R.id.pw_empty);
        recent = findViewById(R.id.pw_recent);

        RecyclerView list = findViewById(R.id.pw_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        MaterialButton add = findViewById(R.id.pw_add);
        add.setOnClickListener(v -> promptAdd());

        bwStatus = findViewById(R.id.bw_status);
        bwAction = findViewById(R.id.bw_action);
        bwLogout = findViewById(R.id.bw_logout);
        RecyclerView bwList = findViewById(R.id.bw_list);
        bwList.setLayoutManager(new LinearLayoutManager(this));
        bwList.setAdapter(bwAdapter);

        bwSession = BwVault.Session.fromJSON(
                bwPrefs().getString(BW_SESSION, null));
        bwAction.setOnClickListener(v -> onBwAction());
        bwLogout.setOnClickListener(v -> confirmBwLogout());
        updateBwUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(ArchivePasswordStore.list(this));
        adapter.notifyDataSetChanged();
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        findViewById(R.id.pw_list).setVisibility(none ? View.GONE : View.VISIBLE);
        count.setText(getString(R.string.password_manager_count, items.size()));

        ArchivePasswordStore.Match m = ArchivePasswordStore.lastMatch(this);
        if (m == null) {
            recent.setText(getString(R.string.password_manager_no_match) + "\n"
                    + getString(R.string.password_manager_no_match_detail));
        } else {
            CharSequence rel = DateUtils.getRelativeTimeSpanString(m.time,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            recent.setText(getString(R.string.password_manager_recent_value, m.archiveName,
                    m.index + 1, Math.max(m.total, m.index + 1), rel.toString()));
        }
    }

    private void promptAdd() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.password_manager_add_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.password_manager_add, (d, w) -> {
                    String pw = input.getText() == null ? "" : input.getText().toString();
                    if (pw.isEmpty()) return;
                    if (ArchivePasswordStore.add(this, pw)) {
                        Toast.makeText(this, R.string.password_manager_added, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.password_manager_duplicate, Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .show();
    }

    private void confirmDelete(int index) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.password_manager_delete_title)
                .setMessage(R.string.password_manager_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    ArchivePasswordStore.remove(this, items.get(index));
                    Toast.makeText(this, R.string.password_manager_delete_any, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    // ------------------------------------------------------------------ Bitwarden

    private android.content.SharedPreferences bwPrefs() {
        return getSharedPreferences(BW_PREFS, Context.MODE_PRIVATE);
    }

    private String bwDeviceId() {
        String id = bwPrefs().getString(BW_DEVICE, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            bwPrefs().edit().putString(BW_DEVICE, id).apply();
        }
        return id;
    }

    private void onBwAction() {
        if (bwBusy) return;
        if (bwVault != null) bwSync();
        else if (bwSession != null) promptBwUnlock();
        else promptBwConnect();
    }

    private void updateBwUi() {
        if (bwVault != null) {
            bwStatus.setText(bwEntries.isEmpty()
                    ? R.string.bw_empty
                    : getString(R.string.bw_unlocked, bwEntries.size()));
            bwAction.setText(R.string.bw_sync);
            bwLogout.setVisibility(View.VISIBLE);
        } else if (bwSession != null) {
            bwStatus.setText(getString(R.string.bw_locked, bwSession.server));
            bwAction.setText(R.string.bw_unlock);
            bwLogout.setVisibility(View.VISIBLE);
        } else {
            bwStatus.setText(R.string.bw_not_connected);
            bwAction.setText(R.string.bw_connect);
            bwLogout.setVisibility(View.GONE);
        }
        findViewById(R.id.bw_list).setVisibility(
                bwVault != null ? View.VISIBLE : View.GONE);
        bwAdapter.notifyDataSetChanged();
    }

    private void promptBwConnect() {
        LinearLayout form = bwForm(2);
        EditText server = form.findViewById(R.id.bw_field_server);
        EditText email = form.findViewById(R.id.bw_field_email);
        EditText password = form.findViewById(R.id.bw_field_password);
        server.setText(bwPrefs().getString(BW_LAST_SERVER, "http://127.0.0.1:8080"));
        email.setText(bwPrefs().getString(BW_LAST_EMAIL, ""));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bw_section)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bw_connect, (d, w) -> bwLogin(
                        server.getText().toString().trim(),
                        email.getText().toString().trim(),
                        password.getText().toString(), null))
                .show();
    }

    private void promptBwUnlock() {
        LinearLayout form = bwForm(0);
        EditText password = form.findViewById(R.id.bw_field_password);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bw_unlock)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bw_unlock, (d, w) ->
                        bwUnlock(password.getText().toString()))
                .show();
    }

    private void promptBwTwoFactor(String server, String email, String password) {
        final EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint(R.string.bw_two_factor);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bw_two_factor)
                .setView(code)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> bwLogin(server, email,
                        password, code.getText().toString().trim()))
                .show();
    }

    /** Stacked input fields; topFields is how many of server/email appear above the password. */
    private LinearLayout bwForm(int fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        addBwField(box, R.id.bw_field_server, R.string.bw_server,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, fields >= 1);
        addBwField(box, R.id.bw_field_email, R.string.bw_email,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                fields >= 2);
        addBwField(box, R.id.bw_field_password, R.string.bw_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
        return box;
    }

    private void addBwField(LinearLayout box, int id, int hint, int type, boolean wanted) {
        if (!wanted) return;
        EditText field = new EditText(this);
        field.setId(id);
        field.setHint(hint);
        field.setInputType(type);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 4;
        field.setLayoutParams(lp);
        box.addView(field);
    }

    private void bwLogin(String server, String email, String password, String twoFactor) {
        if (server.isEmpty() || email.isEmpty() || password.isEmpty()) return;
        setBwBusy(true, R.string.bw_connecting);
        new Thread(() -> {
            try {
                BwVault.Result result =
                        BwVault.login(server, email, password, bwDeviceId(), twoFactor);
                bwPrefs().edit()
                        .putString(BW_LAST_SERVER, server)
                        .putString(BW_LAST_EMAIL, email)
                        .putString(BW_SESSION, result.session.toJSON())
                        .apply();
                finishBw(result, null);
            } catch (BwApi.LoginFailed e) {
                if (e.twoFactorRequired && twoFactor == null) {
                    runOnUiThread(() -> {
                        setBwBusy(false, null);
                        promptBwTwoFactor(server, email, password);
                    });
                } else {
                    finishBw(null, e);
                }
            } catch (Exception e) {
                finishBw(null, e);
            }
        }, "bw-login").start();
    }

    private void bwUnlock(String password) {
        if (password.isEmpty()) return;
        setBwBusy(true, R.string.bw_connecting);
        new Thread(() -> {
            try {
                BwVault.Result result = BwVault.unlock(bwSession, password);
                bwPrefs().edit().putString(BW_SESSION, result.session.toJSON()).apply();
                finishBw(result, null);
            } catch (Exception e) {
                finishBw(null, e);
            }
        }, "bw-unlock").start();
    }

    private void bwSync() {
        setBwBusy(true, R.string.bw_syncing);
        new Thread(() -> {
            try {
                BwVault.Result result = BwVault.resync(bwVault.session, bwVault.userKey);
                bwPrefs().edit().putString(BW_SESSION, result.session.toJSON()).apply();
                finishBw(result, null);
            } catch (Exception e) {
                finishBw(null, e);
            }
        }, "bw-sync").start();
    }

    /** Applies a background result on the UI thread: state first, then the visible text. */
    private void finishBw(BwVault.Result result, Exception error) {
        runOnUiThread(() -> {
            setBwBusy(false, null);
            if (result != null) {
                bwSession = result.session;
                bwVault = result;
                bwEntries.clear();
                bwEntries.addAll(result.entries);
            } else {
                String message = error == null ? "?" : error.getMessage();
                if ("wrong master password".equals(message)) {
                    Toast.makeText(this, R.string.bw_wrong_password,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.bw_failed, message),
                            Toast.LENGTH_LONG).show();
                }
            }
            updateBwUi();
        });
    }

    private void setBwBusy(boolean busy, Integer text) {
        bwBusy = busy;
        bwAction.setEnabled(!busy);
        if (busy && text != null) bwAction.setText(text);
    }

    private void confirmBwLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bw_logout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bw_logout, (d, w) -> {
                    bwPrefs().edit().remove(BW_SESSION).apply();
                    bwSession = null;
                    bwVault = null;
                    bwEntries.clear();
                    updateBwUi();
                })
                .show();
    }

    private void showBwEntry(BwVault.Entry entry) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(entry.name)
                .setNegativeButton(android.R.string.cancel, null);
        if (entry.password != null) {
            dialog.setPositiveButton(R.string.bw_copy_password, (d, w) -> {
                copy(entry.password);
                Toast.makeText(this, R.string.bw_copied, Toast.LENGTH_SHORT).show();
            });
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        if (entry.username != null) {
            TextView u = new TextView(this);
            u.setText(entry.username);
            u.setTextSize(15);
            u.setGravity(Gravity.START);
            u.setOnClickListener(v -> {
                copy(entry.username);
                Toast.makeText(this, R.string.bw_copied_username, Toast.LENGTH_SHORT).show();
            });
            box.addView(u);
        }
        if (entry.password != null) {
            TextView p = new TextView(this);
            final boolean[] revealed = {false};
            p.setText("••••••••");
            p.setTextSize(15);
            p.setPadding(0, 8, 0, 0);
            p.setGravity(Gravity.START);
            p.setOnClickListener(v -> {
                revealed[0] = !revealed[0];
                p.setText(revealed[0] ? entry.password : "••••••••");
            });
            box.addView(p);
        }
        String code = BwTotp.code(entry.totp, System.currentTimeMillis());
        if (code != null) {
            TextView t = new TextView(this);
            t.setText(code);
            t.setTextSize(15);
            t.setPadding(0, 8, 0, 0);
            t.setGravity(Gravity.START);
            t.setOnClickListener(v -> {
                copy(code);
                Toast.makeText(this, R.string.bw_copied_code, Toast.LENGTH_SHORT).show();
            });
            box.addView(t);
        }
        dialog.setView(box).show();
    }

    private void copy(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text));
    }

    private class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView username;
            final ImageButton copyButton;

            Holder(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.bv_name);
                username = v.findViewById(R.id.bv_username);
                copyButton = v.findViewById(R.id.bv_copy);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vault, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final BwVault.Entry entry = bwEntries.get(position);
            holder.name.setText(entry.name);
            if (entry.username == null || entry.username.isEmpty()) {
                holder.username.setVisibility(View.GONE);
            } else {
                holder.username.setVisibility(View.VISIBLE);
                holder.username.setText(entry.username);
            }
            holder.itemView.setOnClickListener(v -> showBwEntry(entry));
            holder.copyButton.setOnClickListener(v -> {
                if (entry.password == null) return;
                copy(entry.password);
                Toast.makeText(PasswordManagerActivity.this,
                        R.string.bw_copied, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return bwEntries.size();
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView value;
            final TextView position;
            final ImageButton up;
            final ImageButton down;
            final ImageButton delete;

            Holder(@NonNull View v) {
                super(v);
                value = v.findViewById(R.id.pw_value);
                position = v.findViewById(R.id.pw_position);
                up = v.findViewById(R.id.pw_up);
                down = v.findViewById(R.id.pw_down);
                delete = v.findViewById(R.id.pw_delete);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_password, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final int index = position;
            holder.value.setText("••••••");
            holder.value.setOnClickListener(v -> {
                // Tap to reveal: these are the user's own passwords, and masking
                // every entry makes the list unreadable at a glance.
                holder.value.setText(items.get(index));
                holder.value.setOnClickListener(v2 ->
                        holder.value.setText("••••••"));
            });
            holder.position.setText(getString(R.string.password_manager_position,
                    index + 1, items.size()));
            holder.up.setEnabled(index > 0);
            holder.up.setOnClickListener(v -> {
                ArchivePasswordStore.moveUp(PasswordManagerActivity.this, index);
                refresh();
            });
            holder.down.setEnabled(index < items.size() - 1);
            holder.down.setOnClickListener(v -> {
                ArchivePasswordStore.moveDown(PasswordManagerActivity.this, index);
                refresh();
            });
            holder.delete.setOnClickListener(v -> confirmDelete(index));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
