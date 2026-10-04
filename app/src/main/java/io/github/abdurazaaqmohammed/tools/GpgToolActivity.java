package io.github.abdurazaaqmohammed.tools;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.GpgCrypto;

/**
 * GPG encryption and decryption, driven from the tools hub.
 *
 * <p>Lives here rather than on the file's long-press menu so the crypto sits with
 * the other file tools instead of being mixed into every file's context menu.
 * A file is chosen through the system picker and staged into the cache, since a
 * document URI is not a path the OpenPGP code can read.
 *
 * <p>Results are written into the app's own files directory rather than back to
 * shared storage: writing there needs permissions this screen does not ask for.
 * The path is shown when finished so the file can be acted on.
 */
public class GpgToolActivity extends BaseActivity {

    private TextView status;
    private ProgressBar progress;
    private ActivityResultLauncher<String[]> launcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpg_tool);

        MaterialToolbar toolbar = findViewById(R.id.gpg_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        status = findViewById(R.id.gpg_status);
        progress = findViewById(R.id.gpg_progress);
        progress.setVisibility(View.GONE);

        launcher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::onPicked);

        findViewById(R.id.gpg_encrypt).setOnClickListener(v -> {
            pendingEncrypt = true;
            launcher.launch(new String[]{"*/*"});
        });
        findViewById(R.id.gpg_decrypt).setOnClickListener(v -> {
            pendingEncrypt = false;
            launcher.launch(new String[]{"*/*"});
        });
    }

    /** Whether the pick was for encryption; a decrypt pick sniffs the file instead. */
    private boolean pendingEncrypt = true;

    private void onPicked(Uri uri) {
        if (uri == null) return;
        boolean encrypt = pendingEncrypt;
        String name = ToolFilePicker.displayName(this, uri);
        File staged = ToolFilePicker.stage(this, uri, name);
        if (staged == null) {
            toast(R.string.gpg_pick_failed);
            return;
        }
        if (encrypt) askEncryptMode(staged, name);
        else askPassword(staged, name);
    }

    private void askEncryptMode(File src, String name) {
        String[] modes = {
                getString(R.string.gpg_encrypt_password),
                getString(R.string.gpg_encrypt_pubkey)};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setItems(modes, (d, which) -> {
                    if (which == 0) {
                        askPassword(src, name);
                    } else {
                        askKeyPath(src, name);
                    }
                })
                .show();
    }

    private void askPassword(File src, String name) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.enter_password_gpg);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String pw = input.getText() == null ? "" : input.getText().toString();
                        if (pw.isEmpty()) return;
                        run(src, name, true, pw.toCharArray(), null);
                    })
                .show();
    }

    private void askKeyPath(File src, String name) {
        final EditText input = new EditText(this);
        input.setHint(R.string.gpg_key_path_hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String path = input.getText() == null ? "" : input.getText().toString().trim();
                        if (path.isEmpty()) return;
                        run(src, name, true, null, path);
                    })
                .show();
    }

    private void run(File src, String name, boolean encrypt, char[] password, String keyPath) {
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.gpg_working);
        new Thread(() -> {
            String result = null;
            String error = null;
            File out = new File(getFilesDir(), encrypt ? name + ".gpg" : stripSuffix(name));
            try {
                if (encrypt) {
                    if (password != null) {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
                             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                            GpgCrypto.encrypt(in, os, password);
                        }
                    } else {
                        org.bouncycastle.openpgp.PGPPublicKey key;
                        try (InputStream k = new BufferedInputStream(new FileInputStream(keyPath))) {
                            key = GpgCrypto.loadEncryptionKey(k);
                        }
                        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
                             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                            GpgCrypto.encryptForKey(in, out, key);
                        }
                    }
                } else {
                    // A password-encrypted file needs a password; a public-key one
                    // needs a secret key. Ask for whichever this file actually is.
                    if (keyPath != null) {
                        // Asked for a secret key: use it.
                        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
                             InputStream key = new BufferedInputStream(new FileInputStream(keyPath));
                             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                            GpgCrypto.decryptWithKey(in, os, key, password == null ? new char[0] : password);
                        }
                    } else {
                        boolean publicKey;
                        try (InputStream probe = new BufferedInputStream(new FileInputStream(src))) {
                            publicKey = GpgCrypto.isPublicKeyEncrypted(probe);
                        }
                        if (publicKey) {
                            error = "NEEDS_KEY";
                        } else if (password == null) {
                            error = "NEEDS_PASSWORD";
                        } else {
                            try (InputStream in = new BufferedInputStream(new FileInputStream(src));
                                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                                GpgCrypto.decrypt(in, os, password);
                            }
                        }
                    }
                }
                if (error == null) result = out.getAbsolutePath();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
            final String r = result;
            final String err = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (r != null) {
                    status.setText(getString(R.string.gpg_done_at, r));
                } else if ("NEEDS_PASSWORD".equals(err)) {
                    askPassword(src, name);
                } else if ("NEEDS_KEY".equals(err)) {
                    askSecretKey(src, name);
                } else {
                    status.setText(getString(R.string.gpg_failed, String.valueOf(err)));
                }
            });
        }).start();
    }

    private void askSecretKey(File src, String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        final EditText keyPath = new EditText(this);
        keyPath.setHint(R.string.gpg_key_path_hint);
        final EditText pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setHint(R.string.gpg_passphrase_hint);
        box.addView(keyPath);
        box.addView(pass);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String path = keyPath.getText() == null ? "" : keyPath.getText().toString().trim();
                        String pw = pass.getText() == null ? "" : pass.getText().toString();
                        if (!path.isEmpty()) run(src, name, false, null, path);
                    })
                .show();
    }

    private static String stripSuffix(String name) {
        String lower = name.toLowerCase(java.util.Locale.ENGLISH);
        for (String s : new String[]{".gpg", ".asc", ".pgp"}) {
            if (lower.endsWith(s) && name.length() > s.length()) {
                return name.substring(0, name.length() - s.length());
            }
        }
        return name + ".out";
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}