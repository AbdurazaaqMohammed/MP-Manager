package io.github.abdurazaaqmohammed.adapters.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        list.setPadding(0, pad, 0, pad);
        OrderAdapter adapter = new OrderAdapter(context, items);
        list.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
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

        OrderAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items) {
            this.context = context;
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_file_menu_action, parent, false);
            return new Holder(row, row.findViewById(R.id.menuItemIcon), row.findViewById(R.id.menuItemLabel));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FileMenuOrder.MenuItem item = items.get(position);
            holder.label.setText(item.label());
            holder.icon.setImageResource(FileMenuOrder.iconFor(context, item.id(), false, false));
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
