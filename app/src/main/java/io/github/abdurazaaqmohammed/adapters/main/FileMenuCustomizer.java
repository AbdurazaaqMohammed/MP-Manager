package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public final class FileMenuCustomizer {

    private FileMenuCustomizer() {
    }

    public static void show(MainActivity context) {
        DialogUtil dialogUtil = context.dialogUtil;
        List<String> order = new ArrayList<>(FileMenuOrder.load(context));
        List<FileMenuOrder.MenuItem> items = new ArrayList<>();
        for (String id : order) {
            items.add(new FileMenuOrder.MenuItem(id, FileMenuOrder.labelFor(context, id, "->")));
        }

        RecyclerView list = new RecyclerView(context);
        final boolean twoColumn = FileMenuOrder.isTwoColumn(context);
        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        if (twoColumn) {
            list.setLayoutManager(new GridLayoutManager(context, 2));
            float density = context.getResources().getDisplayMetrics().density;
            int edge = (int) (12 * density + 0.5f);
            list.setPadding(edge, pad, edge, pad);
        } else {
            list.setLayoutManager(new LinearLayoutManager(context));
            list.setPadding(0, pad, 0, pad);
        }
        list.setClipToPadding(false);
        OrderAdapter adapter = new OrderAdapter(context, items, twoColumn);
        list.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | (twoColumn ? (ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) : 0);
                return makeMovementFlags(drag, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                if (a < 0 || b < 0) return false;
                Collections.swap(items, a, b);
                adapter.notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        helper.attachToRecyclerView(list);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.customize_file_menu))
                .setMessage(context.getString(R.string.customize_file_menu_hint))
                .setView(list)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    FileMenuOrder.save(context, new ArrayList<>(Arrays.asList(FileMenuOrder.DEFAULT_ORDER)));
                    Extensions.showMessage(context, R.string.menu_order_reset);
                })
                .create();
        dialog.setOnDismissListener(d -> {
            List<String> ids = new ArrayList<>();
            for (FileMenuOrder.MenuItem item : items) ids.add(item.id());
            FileMenuOrder.save(context, ids);
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {
        private final MainActivity context;
        private final List<FileMenuOrder.MenuItem> items;
        private final boolean grid;

        OrderAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean grid) {
            this.context = context;
            this.items = items;
            this.grid = grid;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(context).inflate(grid ? R.layout.item_file_menu_action_grid : R.layout.item_file_menu_action, parent, false);
            return new Holder(row, row.findViewById(R.id.menuItemLabel));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FileMenuOrder.MenuItem item = items.get(position);
            holder.label.setText(item.label());
            Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), FileMenuOrder.iconFor(context, item.id(), false, false), null);
            if (drawable != null) {
                int i = Extensions.dp2px(context, 24);
                drawable.setBounds(0, 0, i, i);
                DrawableCompat.setTint(drawable, MaterialColors.getColor(holder.label, com.google.android.material.R.attr.colorPrimary));
            }
            holder.label.setCompoundDrawablesRelative(drawable, null, null, null);
            holder.label.setCompoundDrawablePadding(Extensions.dp2px(context, 4));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView label;

            Holder(@NonNull View itemView, TextView label) {
                super(itemView);
                this.label = label;
            }
        }
    }
}
