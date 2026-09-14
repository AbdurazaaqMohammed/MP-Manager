package io.github.abdurazaaqmohammed.arsc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.reandroid.arsc.chunk.TypeBlock;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ArscTreeAdapter extends RecyclerView.Adapter<ArscTreeAdapter.Holder> {

    public interface Listener {
        void onNodeClick(ArscData.Node node);

        void onNodeLongClick(ArscData.Node node, View anchor);
    }

    private final Context context;
    private final Listener listener;
    private List<ArscData.Node> roots = new ArrayList<>();
    private final List<ArscData.Node> visible = new ArrayList<>();
    private boolean batchMode = false;
    private int batchSelected = 0;
    private Runnable selectionWatcher;
    private ArscData.Node animatedNode;
    private GradientDrawable folderBg;
    private GradientDrawable codeBg;
    private Drawable chevronDrawable;
    private Drawable codeDrawable;

    private void ensureRowDrawables(float density) {
        if (folderBg == null) {
            folderBg = new GradientDrawable();
            folderBg.setCornerRadius(5 * density);
            folderBg.setColor(0xFF252525);
        }
        if (codeBg == null) {
            codeBg = new GradientDrawable();
            codeBg.setCornerRadius(100 * density);
            codeBg.setColor(0xFF3860AF);
        }
        if (chevronDrawable == null) {
            Drawable d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_chevron_right, context.getTheme()).mutate();
            DrawableCompat.setTint(d, MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.GRAY));
            chevronDrawable = d;
        }
        if (codeDrawable == null) {
            Drawable d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.code_24px, context.getTheme()).mutate();
            DrawableCompat.setTint(d, Color.WHITE);
            codeDrawable = d;
        }
    }

    public ArscTreeAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setRoots(List<ArscData.Node> newRoots) {
        roots = newRoots == null ? new ArrayList<>() : newRoots;
        batchSelected = 0;
        refresh();
    }

    public void setBatchMode(boolean batch) {
        batchMode = batch;
        if (!batch) {
            clearChecks(roots);
            batchSelected = 0;
        }
        refresh();
        notifySelection();
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void setSelectionWatcher(Runnable watcher) {
        selectionWatcher = watcher;
    }

    private void clearChecks(List<ArscData.Node> nodes) {
        for (ArscData.Node n : nodes) {
            n.checked = false;
            clearChecks(n.children);
        }
    }

    public void selectAll(boolean select) {
        setAllChecked(roots, select);
        refresh();
        notifySelection();
    }

    private void setAllChecked(List<ArscData.Node> nodes, boolean select) {
        for (ArscData.Node n : nodes) {
            n.checked = select;
            setAllChecked(n.children, select);
        }
    }

    public List<ArscData.Node> getCheckedLeaves() {
        List<ArscData.Node> out = new ArrayList<>();
        collectChecked(roots, out);
        return out;
    }

    private void collectChecked(List<ArscData.Node> nodes, List<ArscData.Node> out) {
        for (ArscData.Node n : nodes) {
            if (n.checked && !n.dir) out.add(n);
            collectChecked(n.children, out);
        }
    }

    public List<ArscData.Node> getCheckedDirs() {
        List<ArscData.Node> out = new ArrayList<>();
        collectCheckedDirs(roots, out);
        return out;
    }

    public List<ArscData.Node> getCheckedNodes() {
        List<ArscData.Node> out = new ArrayList<>();
        collectCheckedNodes(roots, out);
        return out;
    }

    private void collectCheckedNodes(List<ArscData.Node> nodes, List<ArscData.Node> out) {
        for (ArscData.Node n : nodes) {
            if (n.checked) out.add(n);
            collectCheckedNodes(n.children, out);
        }
    }

    private void collectCheckedDirs(List<ArscData.Node> nodes, List<ArscData.Node> out) {
        for (ArscData.Node n : nodes) {
            if (n.checked && n.dir) out.add(n);
            collectCheckedDirs(n.children, out);
        }
    }

    public void refresh() {
        visible.clear();
        flatten(roots);
        notifyDataSetChanged();
    }

    private void flatten(List<ArscData.Node> nodes) {
        for (ArscData.Node n : nodes) {
            visible.add(n);
            if (n.dir && n.expanded) flatten(n.children);
        }
    }

    public void toggle(ArscData.Node node) {
        int pos = visible.indexOf(node);
        if (pos < 0) {
            node.expanded = !node.expanded;
            animatedNode = node;
            refresh();
            return;
        }
        animatedNode = node;
        if (node.expanded) {
            node.expanded = false;
            int count = 0;
            while (pos + 1 + count < visible.size() && isDescendant(node, visible.get(pos + 1 + count))) count++;
            for (int i = 0; i < count; i++) visible.remove(pos + 1);
            if (count > 0) notifyItemRangeRemoved(pos + 1, count);
            notifyItemChanged(pos);
        } else {
            node.expanded = true;
            List<ArscData.Node> toAdd = new ArrayList<>();
            collectVisibleChildren(node, toAdd);
            visible.addAll(pos + 1, toAdd);
            if (!toAdd.isEmpty()) notifyItemRangeInserted(pos + 1, toAdd.size());
            notifyItemChanged(pos);
        }
    }

    private static boolean isDescendant(ArscData.Node parent, ArscData.Node node) {
        ArscData.Node p = node.parent;
        while (p != null) {
            if (p == parent) return true;
            p = p.parent;
        }
        return false;
    }

    private static void collectVisibleChildren(ArscData.Node parent, List<ArscData.Node> out) {
        for (ArscData.Node child : parent.children) {
            out.add(child);
            if (child.dir && child.expanded) collectVisibleChildren(child, out);
        }
    }

    private void notifySelection() {
        int count;
        count = countChecked(roots);
        batchSelected = count;
        if (selectionWatcher != null) selectionWatcher.run();
    }

    private int countChecked(List<ArscData.Node> nodes) {
        int c = 0;
        for (ArscData.Node n : nodes) {
            if (n.checked) c++;
            c += countChecked(n.children);
        }
        return c;
    }

    public int getBatchSelected() {
        return batchSelected;
    }

    private void toggleChecked(ArscData.Node node) {
        node.checked = !node.checked;
        setAllChecked(node.children, node.checked);
        refresh();
        notifySelection();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        float density = context.getResources().getDisplayMetrics().density;
        ensureRowDrawables(density);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setMinimumHeight((int) (32 * density));
        int vPad = (int) (6 * density);
        row.setPadding(0, vPad, (int) (8 * density), vPad);
        ImageView arrow = new ImageView(context);
        int arrowSize = (int) (14 * density);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(arrowSize, arrowSize));
        CheckBox check = new MaterialCheckBox(context);
        FrameLayout iconBg = new FrameLayout(context);
        int bgSize = (int) (20 * density);
        LinearLayout.LayoutParams bgParams = new LinearLayout.LayoutParams(bgSize, bgSize);
        bgParams.gravity = Gravity.CENTER_VERTICAL;
        int iconMargin = (int) (4 * density);
        bgParams.leftMargin = iconMargin;
        bgParams.rightMargin = iconMargin;
        iconBg.setLayoutParams(bgParams);
        ImageView icon = new ImageView(context);
        int iconSize = (int) (12 * density);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconBg.addView(icon);
        TextView name = new TextView(context);
        name.setTextSize(14);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = (int) (6 * density);
        row.addView(arrow);
        row.addView(check);
        row.addView(iconBg);
        row.addView(name, nameParams);
        return new Holder(row, arrow, check, iconBg, icon, name);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ArscData.Node node = visible.get(position);
        Resources resources = context.getResources();
        float density = resources.getDisplayMetrics().density;
        ensureRowDrawables(density);
        holder.row.setPadding((int) (node.depth * 22 * density), holder.row.getPaddingTop(), holder.row.getPaddingRight(), holder.row.getPaddingBottom());
        boolean isConfig = node.dir && node.tag instanceof TypeBlock;
        holder.arrow.animate().cancel();
        if (isConfig) {
            holder.arrow.setVisibility(View.GONE);
            holder.iconBg.setBackground(codeBg);
            holder.icon.setImageDrawable(codeDrawable);
        } else if (node.dir) {
            holder.arrow.setVisibility(View.VISIBLE);
            holder.arrow.setImageDrawable(chevronDrawable);
            if (node == animatedNode) {
                animatedNode = null;
                holder.arrow.setRotation(node.expanded ? 0f : 45f);
                holder.arrow.animate().rotation(node.expanded ? 45f : 0f)
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                holder.arrow.setRotation(node.expanded ? 45f : 0f);
            }
            holder.iconBg.setBackground(folderBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) holder.iconBg.setElevation(5 * density);
            holder.icon.setImageResource(R.drawable.ic_folder_mt);
        } else {
            holder.arrow.setVisibility(View.GONE);
            holder.iconBg.setBackground(codeBg);
            holder.icon.setImageDrawable(codeDrawable);
        }
        holder.check.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(node.checked);
        holder.check.setOnCheckedChangeListener((b, checked) -> {
            if (node.checked != checked) toggleChecked(node);
        });
        holder.name.setText(node.label);
        if (node.checked && (!node.dir || node.tag instanceof TypeBlock)) {
            holder.name.setTextColor(0xFF4CAF50);
            holder.name.setTypeface(null, Typeface.BOLD);
        } else {
            holder.name.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            holder.name.setTypeface(null, Typeface.NORMAL);
        }
        holder.row.setOnClickListener(v -> {
            if (batchMode) {
                toggleChecked(node);
                return;
            }
            if (node.dir && !node.children.isEmpty()) {
                toggle(node);
            }
            listener.onNodeClick(node);
        });
        holder.row.setOnLongClickListener(v -> {
            listener.onNodeLongClick(node, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final ImageView arrow;
        final CheckBox check;
        final FrameLayout iconBg;
        final ImageView icon;
        final TextView name;

        Holder(@NonNull View itemView, ImageView arrow, CheckBox check, FrameLayout iconBg, ImageView icon, TextView name) {
            super(itemView);
            this.row = (LinearLayout) itemView;
            this.arrow = arrow;
            this.check = check;
            this.iconBg = iconBg;
            this.icon = icon;
            this.name = name;
        }
    }
}
