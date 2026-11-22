package com.example.mplugtemplate;

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

/**
 * ACTION_HOOK area: one activity serves every declared stable hook (see
 * META_HOOKS in the manifest). The host selects the hook with
 * EXTRA_HOOK_ID, attaches a small primitive Bundle payload in
 * EXTRA_HOOK_PAYLOAD, and reads RESULT_OK + EXTRA_MESSAGE.
 */
public class HookActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String hookId = intent.getStringExtra(PluginContracts.EXTRA_HOOK_ID);
        Bundle payload = intent.getBundleExtra(PluginContracts.EXTRA_HOOK_PAYLOAD);
        PluginTheme.ThemeInfo theme = PluginTheme.from(intent);
        String entryId = PluginHooks.entryId(getPackageName(), "template.hooks",
                getClass().getName(), hookId);
        int text = theme.color("colorOnSurface",
                theme.dark ? 0xFFE6E1E5 : 0xFF1C1B1F);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(theme.color("colorSurface",
                theme.dark ? 0xFF121212 : 0xFFFFFFFF));

        TextView title = new TextView(this);
        title.setText("Template hook: " + hookId);
        title.setTextSize(18);
        title.setTextColor(text);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("entry=" + entryId + "\n"
                + "payloadEntries=" + (payload == null ? 0 : payload.size()) + "\n"
                + "file_name=" + payloadString(payload,
                        PluginContracts.PAYLOAD_KEY_FILE_NAME) + "\n"
                + "mime=" + payloadString(payload, PluginContracts.PAYLOAD_KEY_MIME));
        info.setTextColor(text);
        box.addView(info);

        Button done = new Button(this);
        done.setText("Return result");
        done.setOnClickListener(v -> finish());
        box.addView(done);

        setContentView(box);
        setResult(RESULT_OK, buildResult(intent, hookId, payload));
    }

    /** Hook-specific result; the host decides how to consume each area. */
    private static Intent buildResult(Intent intent, String hookId, Bundle payload) {
        if (PluginContracts.HOOK_EDITOR_ACTIONS.equals(hookId)) {
            // Editor hook: uppercase the selection the host sent (if any).
            String selected = intent.getStringExtra(PluginContracts.EXTRA_SELECTED_TEXT);
            Intent result = PluginRequests.resultIntent(
                    "template editor hook ran for " + hookId);
            result.putExtra(PluginContracts.EXTRA_REPLACE_SELECTION,
                    selected == null ? "" : selected.toUpperCase(java.util.Locale.ROOT));
            return result;
        }
        return PluginRequests.resultIntent("template hook " + hookId
                + " ran (payloadEntries=" + (payload == null ? 0 : payload.size()) + ")");
    }

    /** Primitive payload value reader (no reflection, no file access). */
    private static String payloadString(Bundle payload, String key) {
        if (payload == null || !payload.containsKey(key)) return null;
        try {
            return payload.getString(key);
        } catch (ClassCastException ignored) {
            return null;
        }
    }
}
