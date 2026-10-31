package io.github.abdurazaaqmohammed.adapters.main;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public final class FileMenuCustomizer {

    private FileMenuCustomizer() {
    }

    public static void show(Activity context) {
        show(context, null);
    }

    public static void show(Activity context, Runnable onDismiss) {
        DialogUtil dialogUtil = new DialogUtil(context);
        List<String> order = new ArrayList<>(FileMenuOrder.load(context));
        List<FileMenuOrder.MenuItem> items = new ArrayList<>();
        for (String id : order) {
            items.add(new FileMenuOrder.MenuItem(id, FileMenuOrder.labelFor(context, id, "->")));
        }
        Set<String> hidden = new HashSet<>(FileMenuOrder.getHiddenIds(context));

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        list.setPadding(0, pad, 0, pad);
        list.setClipToPadding(false);

        OrderAdapter adapter = new OrderAdapter(context, items, hidden);
        list.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
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
        adapter.setTouchHelper(helper);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.customize_file_menu))
                .setMessage(context.getString(R.string.customize_file_menu_hint))
                .setView(list)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    FileMenuOrder.save(context, new ArrayList<>(Arrays.asList(FileMenuOrder.DEFAULT_ORDER)));
                    FileMenuOrder.setHiddenIds(context, new HashSet<>());
                    hidden.clear();
                    items.clear();
                    for (String id : FileMenuOrder.load(context)) {
                        items.add(new FileMenuOrder.MenuItem(id, FileMenuOrder.labelFor(context, id, "->")));
                    }
                    adapter.notifyDataSetChanged();
                    Extensions.showMessage(context, R.string.menu_order_reset);
                })
                .create();
        dialog.setOnDismissListener(d -> {
            List<String> ids = new ArrayList<>();
            for (FileMenuOrder.MenuItem item : items) ids.add(item.id());
            FileMenuOrder.save(context, ids);
            FileMenuOrder.setHiddenIds(context, hidden);
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        dialogUtil.styleAlertDialog(dialog);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.95f), (int) (dm.heightPixels * 0.8f));
        }
    }

    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {
        private final Activity context;
        private final List<FileMenuOrder.MenuItem> items;
        private final Set<String> hidden;
        private ItemTouchHelper touchHelper;

        OrderAdapter(Activity context, List<FileMenuOrder.MenuItem> items, Set<String> hidden) {
            this.context = context;
            this.items = items;
            this.hidden = hidden;
        }

        void setTouchHelper(ItemTouchHelper touchHelper) {
            this.touchHelper = touchHelper;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_dropdown_option, parent, false);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FileMenuOrder.MenuItem item = items.get(position);
            holder.text.setText(item.label());
            boolean isHidden = hidden.contains(item.id());

            int iconRes = FileMenuOrder.iconFor(context, item.id(), false, false);
            holder.icon.setImageResource(isHidden ? R.drawable.visibility_off_24px : iconRes);
            holder.icon.setAlpha(isHidden ? 0.45f : 1f);
            holder.text.setAlpha(isHidden ? 0.45f : 1f);
            holder.itemView.setAlpha(isHidden ? 0.6f : 1f);

            holder.hideButton.setVisibility(View.VISIBLE);
            holder.hideButton.setImageResource(isHidden ? R.drawable.visibility_off_24px : R.drawable.ic_eye_mt);
            holder.hideButton.setContentDescription(context.getString(isHidden ? R.string.restore_sidebar_item : R.string.hide));
            holder.hideButton.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos < 0) return;
                FileMenuOrder.MenuItem cur = items.get(pos);
                if (hidden.contains(cur.id())) hidden.remove(cur.id());
                else hidden.add(cur.id());
                FileMenuOrder.setHiddenIds(context, hidden);
                notifyItemChanged(pos);
            });

            holder.dragHandle.setVisibility(View.VISIBLE);
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView text;
            final ImageButton hideButton;
            final ImageView dragHandle;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.optionIcon);
                text = itemView.findViewById(R.id.optionText);
                hideButton = itemView.findViewById(R.id.optionHideButton);
                dragHandle = itemView.findViewById(R.id.optionDragHandle);
            }
        }
    }
}
