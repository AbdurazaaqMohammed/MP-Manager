package io.github.abdurazaaqmohammed.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class SaveSharedActivity extends AppCompatActivity {

    private final List<Uri> pendingUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(prefs.getInt("theme", dark
                ? io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark
                : io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = null;
            try {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception ignored) {
            }
            if (uri == null) uri = intent.getData();
            if (uri != null) pendingUris.add(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            try {
                ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris != null) pendingUris.addAll(uris);
            } catch (Exception ignored) {
            }
            if (pendingUris.isEmpty() && intent.getData() != null) pendingUris.add(intent.getData());
        } else if (intent != null && intent.getData() != null) {
            pendingUris.add(intent.getData());
        }
        if (pendingUris.isEmpty()) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);
        TextView info = new TextView(this);
        info.setTextSize(15);
        info.setText(getString(R.string.i_ftsave, pendingUris.size()));
        root.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        com.google.android.material.button.MaterialButton cancelBtn =
                new com.google.android.material.button.MaterialButton(this);
        cancelBtn.setText(android.R.string.cancel);
        cancelBtn.setOnClickListener(v -> finish());
        root.addView(cancelBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        pickFolder();
    }

    private void pickFolder() {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.save_to_folder));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) {
                finish();
                return;
            }
            saveToFolder(new File(files[0]), new ArrayList<>(pendingUris));
        });
        picker.show();
    }

    private String displayName(Uri uri, int fallback) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String display = cursor.getString(idx);
                    if (display != null && !display.isEmpty()) return display;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        if (last != null && !last.isEmpty()) return last;
        return "shared_file_" + fallback;
    }

    private void saveToFolder(File dir, List<Uri> uris) {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            int done = 0;
            int n = 0;
            for (Uri uri : uris) {
                n++;
                try {
                    String name = displayName(uri, n).replaceAll("[\\\\/:*?\"<>|]", "_");
                    File dest = new File(dir, name);
                    int copy = 1;
                    while (dest.exists()) {
                        int dot = name.lastIndexOf('.');
                        String base = dot > 0 ? name.substring(0, dot) : name;
                        String ext = dot > 0 ? name.substring(dot) : "";
                        dest = new File(dir, base + " (" + (++copy) + ")" + ext);
                    }
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(dest)) {
                        if (in == null) continue;
                        byte[] buf = new byte[65536];
                        int read;
                        while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
                    }
                    done++;
                } catch (Exception ignored) {
                }
            }
            pm.dismiss();
            int doneCount = done;
            int totalCount = uris.size();
            runOnUiThread(() -> {
                Extensions.showMessage(this, getString(R.string.saved_i_of_i_to_x,doneCount, totalCount, dir.getName()));
                finish();
            });
        }).start();
    }
}
