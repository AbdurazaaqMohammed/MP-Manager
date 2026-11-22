package com.example.mplugtemplate;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/**
 * ACTION_FILE_MENU area: shows the shared file names and mime only. The
 * host grants a read URI, but this template never reads file content —
 * replace with your own (scoped) processing if needed.
 */
public class TemplateFileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        java.util.ArrayList<String> names =
                getIntent().getStringArrayListExtra(PluginContracts.EXTRA_FILE_NAMES);
        String mime = getIntent().getStringExtra(PluginContracts.EXTRA_MIME);
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
        title.setText("Template file info");
        title.setTextSize(18);
        title.setTextColor(text);
        box.addView(title);

        TextView info = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (names != null) {
            for (String n : names) sb.append(n).append('\n');
        }
        sb.append("mime=").append(mime);
        info.setText(sb.toString());
        info.setTextColor(text);
        box.addView(info);

        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(v -> finish());
        box.addView(done);

        setContentView(box);
        setResult(RESULT_OK, PluginRequests.resultIntent(
                "Template file info shown for " + (names == null ? 0 : names.size())
                        + " file(s)"));
    }
}
