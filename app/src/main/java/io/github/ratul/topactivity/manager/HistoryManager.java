/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.manager;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.ratul.topactivity.App;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.extensions.GenericExtensions;
import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.repository.HistoryItem;
import io.github.ratul.topactivity.repository.ServiceState;
import io.github.ratul.topactivity.utils.DatabaseUtil;
import io.github.ratul.topactivity.utils.WindowManagerUtil;

public class HistoryManager {

    private final Context context;
    private final WindowManager windowManager;
    private View baseView;
    private HistoryAdapter historyAdapter;
    private final DataRepository.StateListener stateListener = this::onStateChanged;

    public HistoryManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isActive() {
        return baseView != null && baseView.isAttachedToWindow();
    }

    public void show() {
        if (baseView != null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.layout_activity_history, null);
        baseView = view;

        android.util.Pair<Integer, Integer> screenSize = GenericExtensions.getScreenSize(windowManager);
        int displayWidth = screenSize.first;
        double scaleFactor = PopupManager.mapPreferenceToWindowSize(DatabaseUtil.getWindowSize());
        int viewSize = (int) (displayWidth * scaleFactor);

        WindowManager.LayoutParams layoutParams = WindowManagerUtil.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.width = viewSize;

        windowManager.addView(baseView, layoutParams);

        ImageView closeBtn = view.findViewById(R.id.closeBtn);
        ImageView clearBtn = view.findViewById(R.id.clearBtn);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_history);

        ServiceState serviceState = DataRepository.getInstance().getAppState();
        int historySize = mapPreferenceToHistorySize(DatabaseUtil.getHistorySize());
        historyAdapter = new HistoryAdapter(serviceState.getHistory(), historySize);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(historyAdapter);
        recyclerView.setItemAnimator(null);

        clearBtn.setOnClickListener(v -> {
            DataRepository.getInstance().clearHistory();
            historyAdapter.clearAll();
        });
        closeBtn.setOnClickListener(v -> hide());
        view.setOnTouchListener(new DragTouchManager(windowManager, layoutParams));

        DataRepository.getInstance().addListener(stateListener);
    }

    private void hide() {
        if (baseView != null) {
            windowManager.removeView(baseView);
            baseView = null;
        }
        DataRepository.getInstance().removeListener(stateListener);
    }

    private void onStateChanged(ServiceState state) {
        if (!state.isRunning()) {
            hide();
            return;
        }
        if (!state.getHistory().isEmpty()) {
            historyAdapter.addItem(new HistoryItem(state.getPkg(), state.getCls()));
            RecyclerView recyclerView = baseView.findViewById(R.id.recycler_history);
            recyclerView.scrollToPosition(0);
        }
    }

    private int mapPreferenceToHistorySize(String value) {
        switch (value) {
            case "0":
                return 50;
            case "1":
                return 20;
            default:
                return 10;
        }
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private final List<HistoryItem> items;
        private final int historySize;

        HistoryAdapter(List<HistoryItem> initialList, int historySize) {
            this.items = new ArrayList<>(take(initialList, historySize));
            this.historySize = historySize;
        }

        private static List<HistoryItem> take(List<HistoryItem> list, int count) {
            List<HistoryItem> result = new ArrayList<>();
            int limit = Math.min(count, list.size());
            for (int i = 0; i < limit; i++) {
                result.add(list.get(i));
            }
            return result;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView pkgText;
            final TextView clsText;
            final ImageView copyPkg;
            final ImageView copyCls;

            ViewHolder(View view) {
                super(view);
                pkgText = view.findViewById(R.id.item_package_name);
                clsText = view.findViewById(R.id.item_class_name);
                copyPkg = view.findViewById(R.id.copy_pkg);
                copyCls = view.findViewById(R.id.copy_cls);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.content_activity_history_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.pkgText.setText(item.getPkg());
            holder.clsText.setText(item.getCls());

            holder.copyPkg.setOnClickListener(v -> App.copyString(
                    v.getContext(),
                    GenericExtensions.value(holder.pkgText),
                    v.getContext().getString(R.string.package_copied)
            ));

            holder.copyCls.setOnClickListener(v -> App.copyString(
                    v.getContext(),
                    GenericExtensions.value(holder.clsText),
                    v.getContext().getString(R.string.class_copied)
            ));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void addItem(HistoryItem newItem) {
            // Avoid adding duplicate items
            if (!items.isEmpty()
                    && items.get(0).getPkg().equals(newItem.getPkg())
                    && items.get(0).getCls().equals(newItem.getCls())) {
                return;
            }

            if (items.size() >= historySize) {
                int lastIndex = items.size() - 1;
                items.remove(lastIndex);
                notifyItemRemoved(lastIndex);
            }
            items.add(0, newItem);
            notifyItemInserted(0);
        }

        void clearAll() {
            int size = items.size();
            if (size > 0) {
                items.clear();
                notifyItemRangeRemoved(0, size);
            }
        }
    }
}
