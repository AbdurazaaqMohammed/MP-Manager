package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.abdurazaaqmohammed.plugins.packs.PackPrompts;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.rosemoe.sora.util.Chars;

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
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        CharSequence loadError;
        try {
            ToolPlugin custom = PluginRegistry.findCustom(toolId);
            if (custom != null) {
                View content = custom.createView(this, box);
                if (content != null) {
                    box.addView(content, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                    trackPlugin(custom);
                    boolean fill = false;
                    try {
                        fill = custom.fillViewport();
                    } catch (Exception ignored) {
                    }
                    if (fill) {
                        fitViewport(scroll, box, content, pad);
                    }
                    return;
                }
                loadError = "The tool opened but did not return a view.";
            } else {
                loadError = "The installed pack does not provide the \"" + toolId + "\" tool.";
            }
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            final String mainErr = e.toString();
            StringBuilder stackTrace = new StringBuilder(mainErr).append('\n');
            for (StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
            loadError = stackTrace;
            //loadError = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "no details");
        }
        final String id = toolId;
        List<PackDescriptor> catalog = PackCatalog.load(this);
        PackDescriptor pack = PackCatalog.packForTool(catalog, id);
        if (pack == null || !PackManager.isInstalled(this, pack.id)) {
            PackPrompts.showForTool(this, box, id, () -> {
                try {
                    recreate();
                } catch (Exception ignored) {
                }
            });
            return;
        }
        // The pack is installed but this tool failed to build its UI:
        // surface the real reason instead of the "not installed" prompt.
        box.removeAllViews();
        LinearLayout err = new LinearLayout(this);
        err.setOrientation(LinearLayout.VERTICAL);
        err.setPadding(pad, pad, pad, pad);
        TextView head = new TextView(this);
        head.setText("Tool failed to load");
        head.setTextSize(18);
        err.addView(head);
        TextView msg = new TextView(this);
        msg.setText(loadError);
        msg.setTextSize(14);
        err.addView(msg);
        MaterialButton retry = new MaterialButton(this);
        retry.setText("Retry");
        retry.setOnClickListener(v -> recreate());
        err.addView(retry);
        box.addView(err);
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

    private void fitViewport(android.widget.ScrollView scroll, LinearLayout box,
                             View content, int pad) {
        try {
            scroll.setFillViewport(true);
            try {
                ViewGroup.LayoutParams initialContent = content.getLayoutParams();
                if (initialContent == null) {
                    initialContent = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    content.setLayoutParams(initialContent);
                } else {
                    initialContent.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    content.setLayoutParams(initialContent);
                }
            } catch (Exception ignored) {
            }
            final int[] tries = new int[]{0};
            Runnable fit = new Runnable() {
                @Override
                public void run() {
                    try {
                        int viewport = scroll.getHeight();
                        if (viewport > 0) {
                            try {
                                ViewGroup.LayoutParams blp = box.getLayoutParams();
                                if (blp == null) {
                                    blp = new FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT);
                                    box.setLayoutParams(blp);
                                } else if (blp.height != viewport) {
                                    blp.height = viewport;
                                    box.setLayoutParams(blp);
                                }
                            } catch (Exception ignored) {
                            }
                            try {
                                ViewGroup.LayoutParams clp = content.getLayoutParams();
                                if (clp == null) {
                                    clp = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT);
                                    content.setLayoutParams(clp);
                                } else if (clp.height != ViewGroup.LayoutParams.MATCH_PARENT
                                        && clp.height != Math.max(0, viewport - pad * 2)) {
                                    clp.height = Math.max(0, viewport - pad * 2);
                                    content.setLayoutParams(clp);
                                }
                            } catch (Exception ignored) {
                            }
                            try {
                                content.requestLayout();
                                box.requestLayout();
                            } catch (Exception ignored) {
                            }
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    if (tries[0] < 25) {
                        tries[0]++;
                        try {
                            scroll.postDelayed(this, 200);
                        } catch (Exception ignored) {
                        }
                    }
                }
            };
            scroll.post(fit);
        } catch (Exception ignored) {
        }
    }
}
