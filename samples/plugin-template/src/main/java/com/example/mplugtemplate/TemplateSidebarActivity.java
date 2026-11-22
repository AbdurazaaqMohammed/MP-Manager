package com.example.mplugtemplate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/** ACTION_SIDEBAR_OPEN area: renders with the host theme, own process. */
public class TemplateSidebarActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pluginId = getIntent().getStringExtra(PluginContracts.EXTRA_PLUGIN_ID);
        PluginTheme.ThemeInfo theme = PluginTheme.from(getIntent());
        int background = theme.color("colorSurface",
                theme.dark ? 0xFF121212 : 0xFFFFFFFF);
        int text = theme.color("colorOnSurface",
                theme.dark ? 0xFFE6E1E5 : 0xFF1C1B1F);
        int accent = theme.hasAccent ? theme.accent
                : theme.color("colorPrimary", 0xFF3F51B5);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(background);

        TextView title = new TextView(this);
        title.setText("Template plugin");
        title.setTextSize(20);
        title.setTextColor(text);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sidebar screen in this plugin's own process.\n"
                + "plugin=" + pluginId + "\n"
                + "theme=" + theme.themeId + " dark=" + theme.dark + "\n"
                + "host=" + getIntent().getStringExtra(PluginContracts.EXTRA_HOST_PACKAGE));
        sub.setTextColor(text);
        box.addView(sub);

        Button applyTheme = new Button(this);
        applyTheme.setText("Request host theme");
        applyTheme.setTextColor(accent);
        applyTheme.setOnClickListener(v -> {
            // Plugin -> host request returned as the activity result; the
            // host decides whether to honor it.
            Intent result = PluginRequests.resultIntent(
                    "Template requests theme " + theme.themeId);
            PluginTheme.requestApplyTheme(result, theme.themeId);
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(applyTheme);

        Button close = new Button(this);
        close.setText("Close");
        close.setTextColor(accent);
        close.setOnClickListener(v -> finish());
        box.addView(close);

        setContentView(box);
        setResult(RESULT_OK, PluginRequests.resultIntent("Template sidebar shown"));
    }
}
