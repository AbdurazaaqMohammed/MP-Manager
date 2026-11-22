package com.example.mpextsample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHooks;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

import java.util.Arrays;

/**
 * Sample ACTION_HOOK target: one activity serves all six stable hooks
 * (declared via META_HOOKS). The host selects the hook with
 * EXTRA_HOOK_ID; this template returns RESULT_OK with EXTRA_MESSAGE.
 */
public class HookActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String hookId = intent.getStringExtra(PluginContracts.EXTRA_HOOK_ID);
        Bundle payload = intent.getBundleExtra(PluginContracts.EXTRA_HOOK_PAYLOAD);
        PluginTheme.ThemeInfo theme = PluginTheme.from(intent);
        String entryId = PluginHooks.entryId(getPackageName(), "sample.hooks",
                getClass().getName(), hookId);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(theme.dark ? 0xFF18181C : 0xFFFFFFFF);
        TextView title = new TextView(this);
        title.setText("Sample hook: " + hookId);
        title.setTextSize(18);
        title.setTextColor(theme.dark ? 0xFFFFFFFF : 0xFF000000);
        box.addView(title);
        TextView info = new TextView(this);
        info.setText("entry=" + entryId + "\n"
                + "payloadEntries=" + (payload == null ? 0 : payload.size()) + "\n"
                + "hostHooks=" + Arrays.toString(
                        intent.getStringArrayExtra(PluginContracts.EXTRA_HOST_HOOKS)));
        info.setTextColor(theme.dark ? 0xFFCCCCCC : 0xFF333333);
        box.addView(info);
        Button done = new Button(this);
        done.setText("Return result");
        done.setTextColor(theme.hasAccent ? theme.accent : 0xFF3F51B5);
        done.setOnClickListener(v -> finish());
        box.addView(done);
        setContentView(box);

        // Result is ready immediately; finishing delivers it.
        String message;
        if (PluginContracts.HOOK_EDITOR_ACTIONS.equals(hookId)) {
            // Editor hook: uppercase the selection the host sent (if any).
            String selected = intent.getStringExtra(PluginContracts.EXTRA_SELECTED_TEXT);
            Intent result = PluginRequests.resultIntent(
                    "sample editor hook ran for " + hookId);
            result.putExtra(PluginContracts.EXTRA_REPLACE_SELECTION,
                    selected == null ? "" : selected.toUpperCase(java.util.Locale.ROOT));
            setResult(RESULT_OK, result);
            return;
        }
        message = "sample hook " + hookId + " ran (payload entries "
                + (payload == null ? 0 : payload.size()) + ")";
        setResult(RESULT_OK, PluginRequests.resultIntent(message));
    }
}
