package io.github.abdurazaaqmohammed.tools;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.SslUnpinningLauncher;

/**
 * Standalone SSL pinning bypass tool: pick an APK, patch its dex bytecode so
 * certificate pinning checks are neutralized, optionally sign, and save the
 * result next to the input as {@code <name>-unpinned.apk}.
 */
public class SslUnpinningActivity extends AppCompatActivity {

    private File picked;
    private TextView pickedView;
    private MaterialButton runButton;
    private MaterialSwitch signSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssl_unpinning);
        MaterialToolbar toolbar = findViewById(R.id.ssl_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        pickedView = findViewById(R.id.sslPicked);
        runButton = findViewById(R.id.sslRun);
        signSwitch = findViewById(R.id.sslSignSwitch);
        signSwitch.setChecked(true);
        runButton.setEnabled(false);
        runButton.setOnClickListener(v -> {
            if (picked != null) {
                confirmAndRun();
            }
        });
        findViewById(R.id.sslPick).setOnClickListener(v -> pickApk());
    }

    private void pickApk() {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.extensions = new String[]{".apk"};
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.ssl_unpin_pick));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            picked = new File(files[0]);
            pickedView.setText(picked.getPath());
            runButton.setEnabled(true);
        });
        picker.show();
    }

    private void confirmAndRun() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ssl_unpin_title)
                .setMessage(getString(R.string.ssl_unpin_confirm, picked.getName()))
                .setPositiveButton(R.string.ssl_unpin_run,
                        (d, w) -> SslUnpinningLauncher.run(this, picked, signSwitch.isChecked()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
