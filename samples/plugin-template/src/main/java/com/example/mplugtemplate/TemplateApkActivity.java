package com.example.mplugtemplate;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/**
 * ACTION_APK area: display-only APK info. The host grants a staged copy
 * URI, but this template never reads or rewrites the APK — no dangerous
 * transforms, no signing, no repackage. Replace with your own analysis if
 * needed and return EXTRA_MESSAGE / EXTRA_OUTPUT_NAME only.
 */
public class TemplateApkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String apkName = getIntent().getStringExtra(PluginContracts.EXTRA_APK_NAME);
        Uri staged = getIntent().getData();
        PluginTheme.ThemeInfo theme = PluginTheme.from(getIntent());
        int text = theme.color("colorOnSurface",
                theme.dark ? 0xFFE6E1E5 : 0xFF1C1B1F);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(theme.color("colorSurface",
                theme.dark ? 0xFF121212 : 0xFFFFFFFF));

        TextView title = new TextView(this);
        title.setText("Template APK info (read-only)");
        title.setTextSize(18);
        title.setTextColor(text);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("apk=" + apkName + "\n"
                + "staged=" + (staged != null ? staged.toString() : "-") + "\n"
                + "No bytes are read or modified by this template.");
        info.setTextColor(text);
        box.addView(info);

        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(v -> finish());
        box.addView(done);

        setContentView(box);
        setResult(RESULT_OK, PluginRequests.resultIntent(
                "Template APK info shown for " + apkName + " (no transform)"));
    }
}
