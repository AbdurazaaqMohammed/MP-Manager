package io.github.abdurazaaqmohammed.adapters.main;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.DialogAdapter;

/** Shared file-menu presentation, including the user's order and grid/sheet preference. */
public final class FileMenuDialog {
    private FileMenuDialog() {}

    public static void show(MainActivity activity, String title, List<FileMenuOrder.MenuItem> visible,
                            boolean inArchive, Set<String> disabled, Consumer<String> action) {
        List<FileMenuOrder.MenuItem> items = FileMenuOrder.sortItems(activity, visible);
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            boolean grid = FileMenuOrder.isTwoColumn(activity);
            View view = LayoutInflater.from(activity).inflate(R.layout.dialog_file_menu, null);
            ((TextView) view.findViewById(R.id.fileMenuTitle)).setText(title);
            RecyclerView list = view.findViewById(R.id.fileMenuList);
            Runnable[] dismiss = {() -> {}};
            view.findViewById(R.id.fileMenuEditButton).setOnClickListener(v -> {
                dismiss[0].run();
                FileMenuCustomizer.show(activity);
            });
            if (grid) {
                view.findViewById(R.id.fileMenuHandle).setVisibility(View.GONE);
                list.setLayoutManager(new GridLayoutManager(activity, 2));
                int edge = (int) (12 * activity.getResources().getDisplayMetrics().density + 0.5f);
                list.setPadding(edge, list.getPaddingTop(), edge, list.getPaddingBottom());
            } else list.setLayoutManager(new LinearLayoutManager(activity));
            list.setAdapter(new DialogAdapter(activity, items, inArchive, grid, disabled, position -> {
                dismiss[0].run();
                action.accept(items.get(position).id());
            }));
            if (grid) {
                AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setView(view).create();
                dismiss[0] = dialog::dismiss;
                dialog.show();
            } else {
                BottomSheetDialog sheet = new BottomSheetDialog(activity);
                sheet.setContentView(view);
                dismiss[0] = sheet::dismiss;
                sheet.show();
            }
        });
    }
}
