package com.example.mplugtemplate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/**
 * ACTION_SETTING_CONFIG area: boolean toggle. The host persists the
 * returned EXTRA_VALUE under the declared META_SETTING_KEY.
 */
public class TemplateSettingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key = getIntent().getStringExtra(PluginContracts.EXTRA_KEY);
        boolean current = getIntent().getBooleanExtra(PluginContracts.EXTRA_VALUE, false);
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
        title.setText("Template setting");
        title.setTextSize(18);
        title.setTextColor(text);
        box.addView(title);

        Switch toggle = new Switch(this);
        toggle.setText("Demo enabled");
        toggle.setChecked(current);
        toggle.setTextColor(text);
        box.addView(toggle);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(PluginContracts.EXTRA_VALUE, toggle.isChecked());
            result.putExtra(PluginContracts.EXTRA_MESSAGE,
                    "template setting " + key + " -> " + toggle.isChecked());
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(save);

        setContentView(box);
    }
}
