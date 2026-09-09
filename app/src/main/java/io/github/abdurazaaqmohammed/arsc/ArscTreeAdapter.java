package io.github.abdurazaaqmohammed.arsc;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

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

    private void notifySelection() {
        int count = 0;
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
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vPad = (int) (10 * density);
        row.setPadding(0, vPad, (int) (8 * density), vPad);
        TextView arrow = new TextView(context);
        arrow.setTextSize(14);
        arrow.setWidth((int) (28 * density));
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        CheckBox check = new CheckBox(context);
        ImageView icon = new ImageView(context);
        int iconSize = (int) (24 * density);
        icon.setLayoutParams(new ViewGroup.LayoutParams(iconSize, iconSize));
        TextView name = new TextView(context);
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = (int) (8 * density);
        row.addView(arrow);
        row.addView(check);
        row.addView(icon);
        row.addView(name, nameParams);
        return new Holder(row, arrow, check, icon, name);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ArscData.Node node = visible.get(position);
        float density = context.getResources().getDisplayMetrics().density;
        holder.row.setPadding((int) (node.depth * 22 * density), holder.row.getPaddingTop(), holder.row.getPaddingRight(), holder.row.getPaddingBottom());
        if (node.dir) {
            holder.arrow.setVisibility(View.VISIBLE);
            holder.arrow.setText(node.expanded ? "⌄" : "›");
            holder.icon.setImageResource(R.drawable.folder_24px);
        } else {
            holder.arrow.setVisibility(View.INVISIBLE);
            holder.icon.setImageResource(R.drawable.baseline_text_snippet_24);
        }
        holder.check.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(node.checked);
        holder.check.setOnCheckedChangeListener((b, checked) -> {
            if (node.checked != checked) toggleChecked(node);
        });
        holder.name.setText(node.label);
        if (!node.dir && node.checked) {
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
            if (node.dir) {
                node.expanded = !node.expanded;
                refresh();
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
        final TextView arrow;
        final CheckBox check;
        final ImageView icon;
        final TextView name;

        Holder(@NonNull View itemView, TextView arrow, CheckBox check, ImageView icon, TextView name) {
            super(itemView);
            this.row = (LinearLayout) itemView;
            this.arrow = arrow;
            this.check = check;
            this.icon = icon;
            this.name = name;
        }
    }
}
