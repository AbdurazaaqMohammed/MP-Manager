package io.github.abdurazaaqmohammed.feature.system;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.codehasan.colorpicker.extensions.Extensions;

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
            Extensions.showMessage(activity, R.string.plugin_shortcut_old_android);
            return;
        }
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Extensions.showMessage(activity, R.string.plugin_shortcut_unsupported);
            return;
        }
        try {
            Intent intent = new Intent(activity, ToolShortcutActivity.class)
                    .setAction(Intent.ACTION_VIEW).putExtra(EXTRA_TOOL_ID, id);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "tool_" + id)
                    .setShortLabel(title).setLongLabel(title + " — " + subtitle)
                    .setIcon(Icon.createWithResource(activity, icon)).setIntent(intent).build();
            boolean requested = manager.requestPinShortcut(shortcut, null);
            Extensions.showMessage(activity, requested ? R.string.plugin_shortcut_done
                    : R.string.plugin_shortcut_unsupported);
        } catch (Exception error) {
            Extensions.showMessage(activity, R.string.plugin_shortcut_unsupported);
        }
    }
}
