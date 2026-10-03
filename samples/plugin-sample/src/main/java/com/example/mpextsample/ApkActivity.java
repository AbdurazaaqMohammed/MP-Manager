package com.example.mpextsample;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;

/** Sample ACTION_APK target: fire-and-forget work on a shared APK. */
public class ApkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = getIntent().getStringExtra(PluginContracts.EXTRA_APK_NAME);
        Uri apk = getIntent().getData();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(getString(R.string.apk_title));
        title.setTextSize(18);
        box.addView(title);
        TextView info = new TextView(this);
        info.setText(getString(R.string.apk_info, name, apk));
        box.addView(info);
        Button done = new Button(this);
        done.setText(getString(R.string.done));
        done.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        box.addView(done);
        setContentView(box);
    }
}
