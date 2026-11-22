package com.example.mplugtemplate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/** ACTION_EDITOR area: transforms the selection and returns the replacement. */
public class TemplateEditorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String selected = getIntent().getStringExtra(PluginContracts.EXTRA_SELECTED_TEXT);
        if (selected == null) selected = "";
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
        title.setText("Template uppercase");
        title.setTextSize(18);
        title.setTextColor(text);
        box.addView(title);

        EditText field = new EditText(this);
        field.setText(selected.toUpperCase(java.util.Locale.ROOT));
        field.setTextColor(text);
        box.addView(field);

        Button apply = new Button(this);
        apply.setText("Apply to editor");
        apply.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(PluginContracts.EXTRA_REPLACE_SELECTION,
                    field.getText().toString());
            result.putExtra(PluginContracts.EXTRA_MESSAGE, "Template uppercase applied");
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(apply);

        setContentView(box);
    }
}
