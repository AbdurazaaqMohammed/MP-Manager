package io.github.abdurazaaqmohammed.feature.system;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;

public final class ToolShortcuts {
    public static final String EXTRA_TOOL_ID = "tool_id";
    private ToolShortcuts() {}

    public static void pin(Activity activity, ToolRegistry.ToolItem tool) {
        pin(activity, tool.id(), tool.title(), tool.subtitle(), tool.iconRes());
    }

    public static void pin(Activity activity, PackDescriptor.ToolMeta tool) {
        pin(activity, tool.id, ToolsHubActivity.packToolTitle(tool),
                ToolsHubActivity.packToolSubtitle(tool), R.drawable.tools_24px);
    }

    private static void pin(Activity activity, String id, String title, String subtitle, int icon) {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(activity, R.string.plugin_shortcut_old_android, Toast.LENGTH_SHORT).show();
            return;
        }
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(activity, R.string.plugin_shortcut_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(activity, ToolShortcutActivity.class)
                    .setAction(Intent.ACTION_VIEW).putExtra(EXTRA_TOOL_ID, id);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "tool_" + id)
                    .setShortLabel(title).setLongLabel(title + " — " + subtitle)
                    .setIcon(Icon.createWithResource(activity, icon)).setIntent(intent).build();
            boolean requested = manager.requestPinShortcut(shortcut, null);
            Toast.makeText(activity, requested ? R.string.plugin_shortcut_done
                    : R.string.plugin_shortcut_unsupported, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, R.string.plugin_shortcut_unsupported, Toast.LENGTH_LONG).show();
        }
    }
}
