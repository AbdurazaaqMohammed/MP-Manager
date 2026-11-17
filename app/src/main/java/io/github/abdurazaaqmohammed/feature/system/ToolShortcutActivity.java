package io.github.abdurazaaqmohammed.feature.system;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/** Launcher-accessible trampoline. Internal tool activities remain unexported. */
public final class ToolShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(ToolShortcuts.EXTRA_TOOL_ID);
        if (ToolRegistry.findById(this, id) != null && !ToolRegistry.isAvailable(this, id)) {
            Toast.makeText(this, R.string.tool_requires_root,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent target = ToolLauncher.intentFor(this, id);
        if ("extract".equals(ToolRegistry.canonicalId(id))) {
            // Keep Extract APK's "open in file manager" result callback alive.
            target = new Intent(this, MainActivity.class)
                    .putExtra(ToolLauncher.EXTRA_MAIN_TOOL, "extract")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        if (target == null && id != null) {
            PackDescriptor pack = PackCatalog.packForTool(PackCatalog.load(this), id);
            if (pack != null) {
                target = new Intent(this, ToolRunnerActivity.class)
                        .putExtra("tool_id", id).putExtra("tool_title", pack.tool(id).title);
            }
        }
        try {
            if (target != null) startActivity(target);
        } catch (Exception ignored) {
            // A removed pack or unavailable destination must not crash the launcher.
        } finally {
            finish();
        }
    }
}
