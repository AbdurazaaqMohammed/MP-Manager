package io.github.abdurazaaqmohammed.data.prefs;


import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.data.DexWorkspaceStore;
import io.github.abdurazaaqmohammed.feature.apk.ArscTextActivity;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.util.misc.EditorTasks;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Registry of editor and standalone secondary-task sessions parked via
 * "Minimize" (or silently on abnormal leave of standalone secondary tasks).
 *
 * In single-task mode (default), minimizing captures live in-process state
 * ({@link ParkedState}) and pops cleanly back down to {@link MainActivity};
 * in multi-task mode (or notification-launched standalone tasks), live tasks stay
 * parked and remain recoverable via {@link Item#reopenIntent} even if OS destroys
 * the backgrounded excluded activity.
 *
 * Entries persist for {@link #EXPIRY_MS} (1 hour) unless the user restores and
 * closes them officially, discards them manually from the list, or the MP Manager
 * process is killed.
 */
public final class MinimizedEditors {

    /** Process-held snapshot for an activity minimized off a single task stack. */
    public interface ParkedState {
        void discard();
    }

    /** Retention window before an unresumed minimized entry expires automatically. */
    public static final long EXPIRY_MS = TimeUnit.HOURS.toMillis(1);
    private static final String EXTRA_DEX_WORKSPACE = "dexEditorWorkspace";
    private static final String EXTRA_RESTORE_PROGRESS = "restore_progress";

    /** One parked editor or standalone secondary task session. */
    public static class Item {
        public WeakReference<Activity> ref;
        public final int taskId;
        public final String token;
        public final boolean singleTaskMode;
        public final int requestCode;
        public String title;
        /** Launch intent used to restore the activity when not held as a live background task. */
        public Intent reopenIntent;
        public ParkedState parkedState;
        public long time;

        Item(Activity activity, String title, boolean singleTaskMode,
             ParkedState parkedState, long now) {
            this.ref = new WeakReference<>(activity);
            this.taskId = activity.getTaskId();
            this.token = UUID.randomUUID().toString();
            this.singleTaskMode = singleTaskMode;
            Intent intent = activity.getIntent();
            this.requestCode = intent != null
                    ? intent.getIntExtra(EditorTasks.EXTRA_SINGLE_TASK_REQUEST, -1) : -1;
            this.title = title;
            this.reopenIntent = copyReopenIntent(intent);
            this.parkedState = parkedState;
            this.time = now;
        }

        /** Constructor for unit tests without an Android Activity instance. */
        Item(int taskId, String title, Intent reopenIntent, long time) {
            this.ref = new WeakReference<>(null);
            this.taskId = taskId;
            this.token = UUID.randomUUID().toString();
            this.singleTaskMode = false;
            this.requestCode = -1;
            this.title = title;
            this.reopenIntent = copyReopenIntent(reopenIntent);
            this.time = time;
        }

        boolean isAlive() {
            Activity a = ref.get();
            return a != null && !a.isDestroyed() && !a.isFinishing();
        }

        boolean canReopen() {
            return reopenIntent != null && reopenIntent.getComponent() != null;
        }

        boolean isExpired(long now) {
            return now - time >= EXPIRY_MS;
        }

        boolean isRetainable(long now) {
            return !isExpired(now) && (isAlive() || parkedState != null || canReopen());
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    private static final Map<String, ParkedState> PENDING_RESTORE = new HashMap<>();

    private MinimizedEditors() {}

    /**
     * Composes a descriptive label: "fileName - parentName", e.g.
     * "AndroidManifest.xml - myApp.apk" or "classes.dex - Download".
     */
    public static String formatTitle(String fileName, String parentOrArchiveName) {
        if (TextUtils.isEmpty(fileName)) return parentOrArchiveName != null ? parentOrArchiveName : "";
        if (TextUtils.isEmpty(parentOrArchiveName)) return fileName;
        return fileName + " - " + parentOrArchiveName;
    }

    /** Formats a localized tool/editor label paired with an optional target detail. */
    public static String composeLabel(Context context, int labelRes, String detail) {
        if (context == null || labelRes == 0) return detail != null ? detail : "";
        String label = context.getString(labelRes);
        return TextUtils.isEmpty(detail) ? label : label + " – " + detail;
    }

    /** Extracts "entry/file - apk/parent" detail from standard editor intent extras. */
    public static String archiveOrFileDetail(Intent intent) {
        if (intent == null) return null;
        String parent = null;
        String apk = intent.getStringExtra("apkPath");
        if (apk == null) apk = intent.getStringExtra("zipFilePath");
        if (apk != null && !apk.isEmpty()) parent = new File(apk).getName();

        String name = intent.getStringExtra("zipEntryPath");
        if (name == null) {
            String path = intent.getStringExtra("path");
            if (path != null) {
                File f = new File(path);
                name = f.getName();
                if (parent == null && f.getParentFile() != null) {
                    parent = f.getParentFile().getName();
                }
            }
        }
        return formatTitle(name, parent);
    }

    private static Intent copyReopenIntent(Intent source) {
        if (source == null) return null;
        Intent copy = new Intent(source);
        copy.removeExtra(EXTRA_RESTORE_PROGRESS);
        copy.removeExtra(EditorTasks.EXTRA_RESTORED_FROM_MINIMIZED);
        copy.removeExtra(EditorTasks.EXTRA_MINIMIZE_TOKEN);
        return copy;
    }

    private static String resolveTitle(Activity activity, String title) {
        if (!TextUtils.isEmpty(title)) return title;
        CharSequence fallback = activity.getTitle();
        return fallback != null ? fallback.toString() : "";
    }

    /** Convenience overload composing "Editor name – file name [- parent/archive]". */
    public static void minimize(Activity activity, int editorLabelRes, String fileName) {
        minimize(activity, editorLabelRes, fileName, null);
    }

    public static void minimize(Activity activity, int editorLabelRes, String fileName, String parentOrArchiveName) {
        minimize(activity, composeLabel(activity, editorLabelRes, formatTitle(fileName, parentOrArchiveName)));
    }

    /**
     * Parks {@code activity}: when running inside {@link MainActivity}'s single task,
     * saves live state and returns directly down to {@link MainActivity}; when running in
     * an independent task, keeps the task in background and brings {@link MainActivity} forward.
     */
    public static void minimize(Activity activity, String title) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || activity instanceof MainActivity) {
            return;
        }
        if (EditorTasks.isSecondaryTaskRoot(activity)) {
            if (register(activity, title, false, null) == null) return;
            Intent main = new Intent(activity, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                activity.startActivity(main);
            } catch (Exception e) {
                // Never lose the editor over a launch failure.
                unregister(activity);
            }
        } else {
            ParkedState state = (activity instanceof BaseActivity ba)
                    ? ba.createParkedStateForMinimize() : null;
            if (register(activity, title, true, state) == null) {
                if (state != null) state.discard();
                return;
            }
            if (activity instanceof BaseActivity ba) {
                ba.finishMinimizedToParent();
            } else {
                activity.finish();
            }
        }
    }

    /**
     * Silently registers a standalone secondary-task {@code activity} in the registry
     * without switching the foreground app (used when leaving standalone tasks via Home/Recents).
     */
    public static void minimizeSilently(Activity activity, String title) {
        register(activity, title, false, null);
    }

    private static Item register(Activity activity, String title,
                                 boolean singleTaskMode, ParkedState parkedState) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || activity instanceof MainActivity) {
            return null;
        }
        long now = System.currentTimeMillis();
        String resolvedTitle = resolveTitle(activity, title);
        unregisterMatchingWithoutDiscard(activity);
        Item item = new Item(activity, resolvedTitle, singleTaskMode, parkedState, now);
        ITEMS.add(0, item);
        return item;
    }

    /** Removes entries bound to {@code activity} without calling discard on live handoffs. */
    public static void unregister(Activity activity) {
        unregisterMatchingWithoutDiscard(activity);
    }

    private static void unregisterMatchingWithoutDiscard(Activity activity) {
        if (activity == null || activity instanceof MainActivity) return;
        int taskId = activity.getTaskId();
        ComponentName component = activity.getComponentName();
        Intent intent = activity.getIntent();
        String token = intent != null ? intent.getStringExtra(EditorTasks.EXTRA_MINIMIZE_TOKEN) : null;
        Iterator<Item> it = ITEMS.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            Activity a = item.ref.get();
            if (a == activity) {
                it.remove();
            } else if (item.singleTaskMode) {
                if (token != null && token.equals(item.token)) it.remove();
            } else if (taskId != -1 && item.taskId == taskId
                    && item.reopenIntent != null
                    && component.equals(item.reopenIntent.getComponent())) {
                it.remove();
            }
        }
    }

    /** Keep a multi-task item associated with its replacement after in-place recreation. */
    public static void rebind(Activity activity) {
        if (activity == null || activity instanceof MainActivity) return;
        ComponentName component = activity.getComponentName();
        int taskId = activity.getTaskId();
        for (Item item : ITEMS) {
            if (!item.singleTaskMode && item.taskId == taskId && item.reopenIntent != null
                    && component.equals(item.reopenIntent.getComponent())) {
                item.ref = new WeakReference<>(activity);
                item.reopenIntent = copyReopenIntent(activity.getIntent());
            }
        }
    }

    /** Claims once the {@link ParkedState} handed over to a restored activity. */
    public static ParkedState takePendingParkedState(Intent intent) {
        if (intent == null) return null;
        String token = intent.getStringExtra(EditorTasks.EXTRA_MINIMIZE_TOKEN);
        if (TextUtils.isEmpty(token)) return null;
        intent.removeExtra(EditorTasks.EXTRA_MINIMIZE_TOKEN);
        return PENDING_RESTORE.remove(token);
    }

    /** Active or recoverable entries within the 1-hour retention window, newest first. */
    public static List<Item> getItems() {
        return getItems(System.currentTimeMillis());
    }

    static List<Item> getItems(long now) {
        Iterator<Item> it = ITEMS.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (!item.isRetainable(now)) {
                it.remove();
                discardDetachedStateIfDead(item);
            }
        }
        return new ArrayList<>(ITEMS);
    }

    public static boolean isEmpty() {
        return getItems().isEmpty();
    }

    /** Brings the parked editor back to the front, keeping or restoring progress. */
    public static void restore(Context context, Item item) {
        if (context == null || item == null) return;
        try {
            if (!item.singleTaskMode) {
                Activity live = item.ref.get();
                if (live != null && !live.isDestroyed() && !live.isFinishing()) {
                    ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (manager != null) {
                        for (ActivityManager.AppTask task : manager.getAppTasks()) {
                            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                            if (EditorTasks.taskIdOf(info) == item.taskId) {
                                ITEMS.remove(item);
                                task.moveToFront();
                                return;
                            }
                        }
                    }
                }
            }
            if (item.reopenIntent != null) {
                Intent launch = new Intent(item.reopenIntent);
                launch.putExtra(EditorTasks.EXTRA_MINIMIZE_TOKEN, item.token);
                launch.putExtra(EditorTasks.EXTRA_RESTORED_FROM_MINIMIZED, true);
                if (item.parkedState != null) {
                    PENDING_RESTORE.put(item.token, item.parkedState);
                }
                if (!EditorTasks.showEditorTasksInRecents(context) && context instanceof Activity host) {
                    EditorTasks.applySingleTaskFlags(launch);
                    if (item.requestCode >= 0) {
                        launch.putExtra(EditorTasks.EXTRA_SINGLE_TASK_REQUEST, item.requestCode);
                    }
                    host.startActivityForResult(launch, item.requestCode >= 0 ? item.requestCode : -1);
                } else {
                    EditorTasks.applyTaskFlags(context, launch);
                    context.startActivity(launch);
                }
                item.parkedState = null;
                ITEMS.remove(item);
            } else {
                ITEMS.remove(item);
                if (item.parkedState != null) {
                    item.parkedState.discard();
                    item.parkedState = null;
                }
            }
        } catch (Exception error) {
            PENDING_RESTORE.remove(item.token);
            Toast.makeText(context, error.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /** Popup listing all minimized entries; tap to restore, button or "Close all" to discard. */
    public static void showListDialog(Activity activity) {
        List<Item> items = new ArrayList<>(getItems());
        if (items.isEmpty()) return;

        int padH = dp(activity, 16);
        int padV = dp(activity, 12);
        int btnSize = dp(activity, 48);
        int textColor = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int iconColor = MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        TypedValue rippleBg = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rippleBg, true);
        TypedValue borderlessBg = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, borderlessBg, true);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(activity, 0, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                LinearLayout row;
                TextView titleView;
                ImageButton closeBtn;
                if (convertView instanceof LinearLayout existing && existing.getChildCount() >= 2) {
                    row = existing;
                    titleView = (TextView) row.getChildAt(0);
                    closeBtn = (ImageButton) row.getChildAt(1);
                } else {
                    row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(padH, 0, dp(activity, 4), 0);

                    titleView = new TextView(activity);
                    titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    titleView.setTextColor(textColor);
                    titleView.setMaxLines(2);
                    titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    titleView.setPadding(0, padV, dp(activity, 8), padV);
                    if (rippleBg.resourceId != 0) titleView.setBackgroundResource(rippleBg.resourceId);
                    row.addView(titleView, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    closeBtn = new ImageButton(activity);
                    closeBtn.setImageResource(R.drawable.close_24px);
                    closeBtn.setImageTintList(ColorStateList.valueOf(iconColor));
                    closeBtn.setContentDescription(activity.getString(R.string.close));
                    if (borderlessBg.resourceId != 0) {
                        closeBtn.setBackgroundResource(borderlessBg.resourceId);
                    }
                    row.addView(closeBtn, new LinearLayout.LayoutParams(btnSize, btnSize));
                }

                Item item = getItem(position);
                if (item == null) return row;
                titleView.setText(item.title);
                titleView.setOnClickListener(v -> {
                    AlertDialog dlg = dialogRef[0];
                    if (dlg != null) dlg.dismiss();
                    restore(activity, item);
                    refreshMainDrawer(activity);
                });
                closeBtn.setOnClickListener(v -> {
                    closeItem(item);
                    remove(item);
                    notifyDataSetChanged();
                    refreshMainDrawer(activity);
                    if (isEmpty() && dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                });
                return row;
            }
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.minimized_processes)
                .setAdapter(adapter, null)
                .setNegativeButton(R.string.minimized_close_all, (d, which) -> {
                    closeAll();
                    refreshMainDrawer(activity);
                })
                .create();
        dialogRef[0] = dialog;
        dialog.show();
    }

    private static void refreshMainDrawer(Activity activity) {
        if (activity instanceof MainActivity main) {
            main.refreshRightMenuDrawer();
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Finishes a single minimized entry and discards its retained workspace/session if dead. */
    public static void closeItem(Item item) {
        if (item == null) return;
        ITEMS.remove(item);
        finishParkedItem(item);
    }

    /** Finishes every minimized entry (discards unsaved state and removes dead entries). */
    public static void closeAll() {
        List<Item> snapshot = new ArrayList<>(ITEMS);
        ITEMS.clear();
        for (Item item : snapshot) {
            finishParkedItem(item);
        }
    }

    private static void finishParkedItem(Item item) {
        Activity a = item.ref.get();
        if (a != null && !a.isDestroyed() && !a.isFinishing()) {
            item.parkedState = null;
            if (a instanceof BaseActivity ba) {
                ba.finishQuietlyFromMinimized();
            } else {
                a.finishAndRemoveTask();
            }
        } else {
            discardDetachedStateIfDead(item);
        }
    }

    private static void discardDetachedStateIfDead(Item item) {
        if (item == null || item.isAlive()) return;
        if (item.parkedState != null) {
            item.parkedState.discard();
            item.parkedState = null;
        }
        if (item.reopenIntent == null) return;
        ArscTextActivity.discardSession(item.reopenIntent);
        String ws = item.reopenIntent.getStringExtra(EXTRA_DEX_WORKSPACE);
        if (!TextUtils.isEmpty(ws)) {
            DexWorkspaceStore.deleteWhenUnused(new File(ws));
        }
    }

    static void clearForTest() {
        ITEMS.clear();
        PENDING_RESTORE.clear();
    }

    static Item addForTest(int taskId, String title, ParkedState state, long time) {
        Item item = new Item(taskId, title, null, time);
        item.parkedState = state;
        ITEMS.add(0, item);
        return item;
    }

    static List<Item> getItemsForTest(long now) {
        return getItems(now);
    }
}
