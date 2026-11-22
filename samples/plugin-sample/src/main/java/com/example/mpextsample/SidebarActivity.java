package com.example.mpextsample;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHooks;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/** Sample ACTION_SIDEBAR_OPEN target: own process, own permissions. */
public class SidebarActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pluginId = getIntent().getStringExtra(PluginContracts.EXTRA_PLUGIN_ID);
        // Runtime SDK helpers read the host's theme extras; the SDK is
        // bundled (implementation, not compileOnly) so these classes ship in
        // the APK and the plugin renders itself in host style.
        PluginTheme.ThemeInfo theme = PluginTheme.from(getIntent());
        int accent = theme.color("colorPrimary", 0xFF3F51B5);
        boolean dark = theme.dark;
        String entryId = PluginHooks.entryId(getPackageName(), pluginId,
                getClass().getName(), PluginContracts.HOOK_SIDEBAR_FOOTER);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(dark ? 0xFF18181C : Color.WHITE);
        TextView title = new TextView(this);
        title.setText("Sample external plugin");
        title.setTextSize(20);
        title.setTextColor(dark ? Color.WHITE : Color.BLACK);
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Launched from the host sidebar as a separate app"
                + " (plugin=" + pluginId + ", dark=" + dark
                + ", entry=" + entryId + ").");
        sub.setTextColor(dark ? Color.WHITE : Color.BLACK);
        box.addView(sub);
        Button close = new Button(this);
        close.setText("Close");
        close.setTextColor(accent);
        close.setOnClickListener(v -> finish());
        box.addView(close);
        setContentView(box);
        setResult(RESULT_OK, PluginRequests.resultIntent("Sample sidebar shown"));
    }
}
