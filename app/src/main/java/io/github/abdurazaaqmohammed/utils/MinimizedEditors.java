package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Registry of editors parked with "Minimize" from their unsaved-changes dialog.
 *
 * Editors live in separate tasks (see EditorTasks). Restore uses the task ID,
 * never a class-only intent, so two editors of the same type remain distinct.
 *
 * Entries are removed automatically: {@code BaseActivity.onDestroy()} unregisters
 * any activity that is destroyed while finishing, so Save / Discard / exit paths
 * clear the list without extra wiring.
 */
public final class MinimizedEditors {

    /** One parked editor session. */
    public static class Item {
        public WeakReference<Activity> ref;
        public final int taskId;
        public final String title;
        /** Fallback launch intent, used only if the live instance is gone. */
        public final Intent reopenIntent;
        public final long time;

        Item(Activity activity, String title) {
            this.ref = new WeakReference<>(activity);
            this.taskId = activity.getTaskId();
            this.title = title;
            Intent intent = activity.getIntent();
            this.reopenIntent = intent != null ? new Intent(intent) : null;
            this.time = System.currentTimeMillis();
        }

        boolean isAlive() {
            Activity a = ref.get();
            return a != null && !a.isDestroyed() && !a.isFinishing();
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();

    private MinimizedEditors() {}

    /** Convenience overload composing "Editor name – file name". */
    public static void minimize(Activity activity, int editorLabelRes, String fileName) {
        String label = activity.getString(editorLabelRes);
        minimize(activity, TextUtils.isEmpty(fileName) ? label : label + " – " + fileName);
    }

    /**
     * Registers {@code activity} (deduped) and brings the file manager forward
     * without finishing the editor, keeping all progress in memory.
     */
    public static void minimize(Activity activity, String title) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        unregister(activity);
        ITEMS.add(0, new Item(activity, !TextUtils.isEmpty(title)
                ? title : activity.getTitle().toString()));
        Intent main = new Intent(activity, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            activity.startActivity(main);
        } catch (Exception e) {
            // Should not happen; never lose the editor over a launch failure.
            unregister(activity);
        }
    }

    /** Removes entries pointing at {@code activity}. */
    public static void unregister(Activity activity) {
        if (activity == null) return;
        Iterator<Item> it = ITEMS.iterator();
        while (it.hasNext()) {
            Activity a = it.next().ref.get();
            if (a == activity) it.remove();
        }
    }

    /** Keep the selected task associated with its replacement after recreation. */
    public static void rebind(Activity activity) {
        for (Item item : ITEMS) {
            if (item.taskId == activity.getTaskId() && item.reopenIntent != null
                    && item.reopenIntent.getComponent().equals(activity.getComponentName())) {
                item.ref = new WeakReference<>(activity);
            }
        }
    }

    /** Live entries, oldest pruned first. */
    public static List<Item> getItems() {
        Iterator<Item> it = ITEMS.iterator();
        while (it.hasNext()) {
            if (!it.next().isAlive()) it.remove();
        }
        return new ArrayList<>(ITEMS);
    }

    public static boolean isEmpty() {
        return getItems().isEmpty();
    }

    /** Brings the parked editor back to the front, keeping all progress. */
    public static void restore(Context context, Item item) {
        if (item == null) return;
        try {
            Activity live = item.ref.get();
            if (live != null && !live.isDestroyed() && !live.isFinishing()) {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager == null) return;
                for (ActivityManager.AppTask task : manager.getAppTasks()) {
                    ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    int taskId = android.os.Build.VERSION.SDK_INT >= 29 ? info.taskId : info.id;
                    if (taskId == item.taskId) {
                        task.moveToFront();
                        unregister(live);
                        return;
                    }
                }
            } else if (item.reopenIntent != null) {
                context.startActivity(item.reopenIntent);
            }
        } catch (Exception error) {
            android.widget.Toast.makeText(context, error.toString(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Popup listing all minimized editors; tap to restore, "Close all" to discard. */
    public static void showListDialog(Activity activity) {
        List<Item> items = new ArrayList<>(getItems());
        if (items.isEmpty()) return;
        String[] titles = new String[items.size()];
        for (int i = 0; i < items.size(); i++) titles[i] = items.get(i).title;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.minimized_processes)
                .setItems(titles, (dialog, which) -> restore(activity, items.get(which)))
                .setNegativeButton(R.string.minimized_close_all, (dialog, which) -> closeAll())
                .show();
    }

    /** Finishes every live minimized editor (discards their unsaved state). */
    public static void closeAll() {
        for (Item item : getItems()) {
            Activity a = item.ref.get();
            if (a != null && !a.isDestroyed() && !a.isFinishing()) a.finish();
        }
    }
}
