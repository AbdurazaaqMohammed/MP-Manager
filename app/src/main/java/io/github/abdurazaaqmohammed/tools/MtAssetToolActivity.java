package io.github.abdurazaaqmohammed.tools;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.MtAssetCipher;

/**
 * Decrypts and encrypts the small asset container that protected APKs use to
 * hide extra dex, arsc and native payloads in their assets folder.
 *
 * <p>Pick a file: if it starts with the container magic it is decrypted and the
 * inner payload is written next to it, named after the format that payload's own
 * integrity fields agreed with. Any other file is wrapped in the container
 * instead, which is how such a payload would be built.
 */
public class MtAssetToolActivity extends BaseActivity {

    private TextView status;
    private MaterialButton decryptButton;
    private MaterialButton encryptButton;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mt_asset_tool);

        MaterialToolbar toolbar = findViewById(R.id.mt_asset_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        status = findViewById(R.id.mt_asset_status);
        decryptButton = findViewById(R.id.mt_asset_decrypt);
        encryptButton = findViewById(R.id.mt_asset_encrypt);

        decryptButton.setOnClickListener(v -> pick(MtAssetToolActivity.this::runDecrypt));
        encryptButton.setOnClickListener(v -> pick(MtAssetToolActivity.this::runEncrypt));
    }

    private interface Job {
        void run(File file) throws Exception;
    }

    private void pick(Job job) {
        if (busy) {
            return;
        }
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.root = android.os.Environment.getExternalStorageDirectory();
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.mt_asset_pick_title));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) {
                return;
            }
            File picked = new File(files[0]);
            busy = true;
            setButtonsEnabled(false);
            status.setText(R.string.mt_asset_working);
            new Thread(() -> {
                try {
                    job.run(picked);
                } catch (Exception e) {
                    post(getString(R.string.mt_asset_failed, String.valueOf(e.getMessage())));
                } finally {
                    runOnUiThread(() -> {
                        busy = false;
                        setButtonsEnabled(true);
                    });
                }
            }, "mt-asset").start();
        });
        picker.show();
    }

    private void runDecrypt(File file) throws Exception {
        byte[] container = MtAssetCipher.readAll(file);
        if (!MtAssetCipher.looksLikeContainer(container)) {
            post(getString(R.string.mt_asset_not_container, file.getName()));
            return;
        }
        MtAssetCipher.Result result = MtAssetCipher.decrypt(container);
        if (!result.ok) {
            post(getString(R.string.mt_asset_failed, result.detail));
            return;
        }
        File out = MtAssetCipher.unusedSibling(file, MtAssetCipher.extensionFor(result.format));
        MtAssetCipher.writeAll(out, result.data);
        post(getString(R.string.mt_asset_decrypted, out.getName(), result.format, result.key & 0xff));
    }

    private void runEncrypt(File file) throws Exception {
        byte[] plain = MtAssetCipher.readAll(file);
        if (plain.length == 0) {
            post(getString(R.string.mt_asset_failed, getString(R.string.mt_asset_empty)));
            return;
        }
        if (MtAssetCipher.looksLikeContainer(plain)) {
            post(getString(R.string.mt_asset_already, file.getName()));
            return;
        }
        byte[] container = MtAssetCipher.encrypt(plain);
        File out = MtAssetCipher.unusedSibling(file, ".mtasset");
        MtAssetCipher.writeAll(out, container);
        String format = MtAssetCipher.sniff(plain);
        post(getString(R.string.mt_asset_encrypted, out.getName(), format == null ? "?" : format));
    }

    private void post(String message) {
        runOnUiThread(() -> {
            status.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        decryptButton.setEnabled(enabled);
        encryptButton.setEnabled(enabled);
    }
}
