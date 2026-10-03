package com.example.mpextsample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;

/** Sample ACTION_SETTING_CONFIG target (boolean type): returns EXTRA_VALUE. */
public class SettingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key = getIntent().getStringExtra(PluginContracts.EXTRA_KEY);
        boolean current = getIntent().getBooleanExtra(PluginContracts.EXTRA_VALUE, false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(getString(R.string.setting_title, key));
        title.setTextSize(18);
        box.addView(title);
        Switch toggle = new Switch(this);
        toggle.setText(getString(R.string.setting_demo_feature));
        toggle.setChecked(current);
        box.addView(toggle);
        Button save = new Button(this);
        save.setText(getString(R.string.save));
        save.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(PluginContracts.EXTRA_VALUE, toggle.isChecked());
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(save);
        setContentView(box);
    }
}
