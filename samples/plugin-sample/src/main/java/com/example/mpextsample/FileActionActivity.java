package com.example.mpextsample;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

/** Sample ACTION_FILE_MENU target: hashes the shared file, returns a message. */
public class FileActionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<String> names =
                getIntent().getStringArrayListExtra(PluginContracts.EXTRA_FILE_NAMES);
        Uri data = getIntent().getData();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("Sample file action");
        title.setTextSize(18);
        box.addView(title);
        TextView info = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (names != null) {
            for (String n : names) sb.append(n).append('\n');
        }
        if (data != null) sb.append(data.toString());
        info.setText(sb.toString());
        box.addView(info);
        Button run = new Button(this);
        run.setText("Hash first file");
        run.setOnClickListener(v -> {
            String msg;
            try {
                msg = "SHA-256: " + sha256(data);
            } catch (Exception e) {
                msg = "Hash failed";
            }
            Intent result = new Intent();
            result.putExtra(PluginContracts.EXTRA_MESSAGE, msg);
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(run);
        setContentView(box);
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
