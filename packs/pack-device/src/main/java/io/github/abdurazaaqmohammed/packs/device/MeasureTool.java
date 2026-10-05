package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

public class MeasureTool extends BaseToolPlugin {

    private Context host;
    private FrameLayout holder;
    private ToolPlugin active;

    public MeasureTool() {
        super("measure", "Measuring", "Ruler, compass and protractor", ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        MaterialButton rulerBtn = new MaterialButton(context);
        rulerBtn.setId(View.generateViewId());
        rulerBtn.setText("Ruler");
        MaterialButton protractorBtn = new MaterialButton(context);
        protractorBtn.setId(View.generateViewId());
        protractorBtn.setText("Protractor");
        for (MaterialButton b : new MaterialButton[]{rulerBtn, protractorBtn}) {
            group.addView(b, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        int m = ToolViewFactory.dp(context, 8);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMargins(m, m, m, m);
        root.addView(group, gp);
        holder = new FrameLayout(context);
        root.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            ToolPlugin tool = checkedId == rulerBtn.getId() ? new RulerTool() : new ProtractorTool();
            swap(tool);
        });
        group.check(rulerBtn.getId());
        return root;
    }

    private void swap(ToolPlugin tool) {
        try {
            if (active != null) active.onDestroy();
        } catch (Exception ignored) {
        }
        active = tool;
        holder.removeAllViews();
        try {
            View v = tool.createView(host, holder);
            if (v != null && v.getParent() == null) {
                holder.addView(v);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (active != null) active.onDestroy();
        } catch (Exception ignored) {
        }
        active = null;
    }
}
