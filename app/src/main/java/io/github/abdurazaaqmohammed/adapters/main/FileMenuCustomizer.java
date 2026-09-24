package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
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

        RecyclerView grid = new RecyclerView(context);
        grid.setLayoutManager(new GridLayoutManager(context, 2));
        OrderAdapter adapter = new OrderAdapter(context, items);
        grid.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
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
        helper.attachToRecyclerView(grid);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.customize_file_menu))
                .setMessage(context.getString(R.string.customize_file_menu_hint))
                .setView(grid)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    FileMenuOrder.save(context, new ArrayList<>(Arrays.asList(FileMenuOrder.DEFAULT_ORDER)));
                    Extensions.showMessage(context, R.string.menu_order_reset);
                })
                .create();
        dialog.setOnDismissListener(d -> {
            List<String> ids = new ArrayList<>();
            for (FileMenuOrder.MenuItem item : items) ids.add(item.id);
            FileMenuOrder.save(context, ids);
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {
        private final MainActivity context;
        private final List<FileMenuOrder.MenuItem> items;

        OrderAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items) {
            this.context = context;
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(24, 24, 24, 24);
            ImageView icon = new ImageView(context);
            icon.setLayoutParams(new ViewGroup.LayoutParams(72, 72));
            icon.setPadding(0, 0, 10, 0);
            TextView label = new TextView(context);
            label.setTextSize(18);
            row.addView(icon);
            row.addView(label);
            return new Holder(row, icon, label);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FileMenuOrder.MenuItem item = items.get(position);
            holder.label.setText(item.label);
            holder.icon.setImageResource(FileMenuOrder.iconFor(context, item.id, false, false));
            Drawable drawable = holder.icon.getDrawable();
            if (drawable != null) {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(
                        com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                ColorUtil.changeImageColor(drawable, typedValue.data);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            Holder(@NonNull View itemView, ImageView icon, TextView label) {
                super(itemView);
                this.icon = icon;
                this.label = label;
            }
        }
    }
}
