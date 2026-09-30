package com.example.mpextsample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;

/** Sample ACTION_EDITOR target: edits text, returns a replacement. */
public class EditorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String selected = getIntent().getStringExtra(PluginContracts.EXTRA_SELECTED_TEXT);
        if (selected == null) selected = "";
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        EditText field = new EditText(this);
        field.setText(selected.toUpperCase());
        box.addView(field);
        Button apply = new Button(this);
        apply.setText("Apply to editor");
        apply.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(PluginContracts.EXTRA_REPLACE_SELECTION,
                    field.getText().toString());
            setResult(RESULT_OK, result);
            finish();
        });
        box.addView(apply);
        setContentView(box);
    }
}
