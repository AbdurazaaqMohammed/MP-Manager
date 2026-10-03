package com.example.mpextsample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;

/** Sample ACTION_SIDEBAR_OPEN target: own process, own permissions. */
public class SidebarActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pluginId = getIntent().getStringExtra(PluginContracts.EXTRA_PLUGIN_ID);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(getString(R.string.sidebar_title));
        title.setTextSize(20);
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText(getString(R.string.sidebar_sub, pluginId));
        box.addView(sub);
        Button close = new Button(this);
        close.setText(getString(R.string.close));
        close.setOnClickListener(v -> finish());
        box.addView(close);
        setContentView(box);
    }
}
