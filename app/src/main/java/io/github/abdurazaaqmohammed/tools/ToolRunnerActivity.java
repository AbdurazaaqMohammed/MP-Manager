package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.core.ui.base.scaffold.ToolbarPage;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.packs.PackPrompts;

/**
 * Thin host for toolkit screens.
 *
 * <p>All tools are downloadable packs. This activity only builds the toolbar
 * scaffold, renders the installed {@link ToolPlugin} for the requested id, or
 * shows the {@link PackPrompts} install prompt when the pack is missing.
 */
public class ToolRunnerActivity extends ToolbarPage {
    private final List<ToolPlugin> activePlugins = new ArrayList<>();
    private String toolId;

    protected CharSequence pageTitle() {
        String toolTitle = getIntent().getStringExtra("tool_title");
        if (toolTitle == null || toolTitle.isEmpty()) {
            ToolRegistry.ToolItem found = ToolRegistry.findById(this, toolId);
            toolTitle = found == null ? "Tool" : found.title();
        }
        return toolTitle;
    }

    protected void onCreate(Bundle savedInstanceState) {
        toolId = getIntent().getStringExtra("tool_id");
        super.onCreate(savedInstanceState);
        if (toolId == null) {
            toolId = "calc";
        }
        LinearLayout box = contentBox();
        try {
            ToolPlugin custom = PluginRegistry.findCustom(toolId);
            if (custom != null) {
                android.view.View content = custom.createView(this, box);
                if (content != null) {
                    box.addView(content);
                    trackPlugin(custom);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        PackPrompts.showForTool(this, box, toolId, () -> {
            try {
                recreate();
            } catch (Exception ignored) {
            }
        });
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        for (ToolPlugin plugin : activePlugins) {
            try {
                plugin.onNewIntent(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void trackPlugin(ToolPlugin plugin) {
        try {
            activePlugins.add(plugin);
        } catch (Exception ignored) {
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        for (ToolPlugin plugin : activePlugins) {
            try {
                plugin.onDestroy();
            } catch (Exception ignored) {
            }
        }
        activePlugins.clear();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (ToolPlugin plugin : activePlugins) {
            try {
                plugin.onActivityResult(requestCode, resultCode, data);
            } catch (Exception ignored) {
            }
        }
    }
}
