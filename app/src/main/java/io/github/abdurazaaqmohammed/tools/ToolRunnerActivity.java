package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
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
public class ToolRunnerActivity extends BaseActivity {
    private final List<ToolPlugin> activePlugins = new ArrayList<>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String toolId = getIntent().getStringExtra("tool_id");
        String toolTitle = getIntent().getStringExtra("tool_title");
        if (toolTitle == null || toolTitle.isEmpty()) {
            ToolRegistry.ToolItem found = ToolRegistry.findById(this, toolId);
            toolTitle = found == null ? "Tool" : found.title();
        }
        if (toolId == null) {
            toolId = "calc";
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(toolTitle);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
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
        final String id = toolId;
        PackPrompts.showForTool(this, box, id, () -> {
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
