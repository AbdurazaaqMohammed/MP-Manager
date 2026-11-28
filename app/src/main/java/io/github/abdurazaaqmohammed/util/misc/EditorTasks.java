package io.github.abdurazaaqmohammed.util.misc;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.main.LauncherIcons;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/** Gives each editor its own task without breaking activity-result callers. */
public final class EditorTasks {
    public static final String PREF_SHOW_EDITOR_TASKS_IN_RECENTS = "show_editor_tasks_in_recents";
    public static final String EXTRA_RESTORED_FROM_MINIMIZED = "mp.editor.restored_from_minimized";
    public static final String EXTRA_SINGLE_TASK_REQUEST = "mp.editor.single_task_request";
    public static final String EXTRA_MINIMIZE_TOKEN = "mp.editor.minimize_token";
    private static final String OWNER = "mp.editor.owner";
    private static final String RESULT = "mp.editor.result";
    private static final Map<String, WeakReference<BaseActivity>> owners = new HashMap<>();
    private static final Map<String, Result> results = new HashMap<>();
    private static final Map<Integer, Result> pendingResults = new LinkedHashMap<>();

    public static final class Result {
        public final String owner;
        public final int request;
        public int code = Activity.RESULT_CANCELED;
        public Intent data;
        public Result(String owner, int request) { this.owner = owner; this.request = request; }
    }

    public static Result getAndClearPendingResult() {
        synchronized (pendingResults) {
            if (pendingResults.isEmpty()) return null;
            Result latest = null;
            Integer latestKey = null;
            for (Map.Entry<Integer, Result> entry : pendingResults.entrySet()) {
                latestKey = entry.getKey();
                latest = entry.getValue();
            }
            if (latestKey != null) pendingResults.remove(latestKey);
            return latest;
        }
    }

    public static Result getAndClearPendingResult(int request) {
        synchronized (pendingResults) {
            return pendingResults.remove(request);
        }
    }

    private EditorTasks() {}

    public static String bind(BaseActivity activity, Bundle state) {
        String id = state != null ? state.getString(OWNER) : null;
        if (id == null) id = UUID.randomUUID().toString();
        owners.put(id, new WeakReference<>(activity));
        return id;
    }

    public static void saveOwner(Bundle state, String id) { state.putString(OWNER, id); }

    public static int taskIdOf(ActivityManager.RecentTaskInfo info) {
        if (info == null) return -1;
        return Build.VERSION.SDK_INT >= 29 ? info.taskId : info.id;
    }

    private static boolean isExcludedTransient(String cls) {
        return MainActivity.class.getName().equals(cls)
                // Tasks started from the launcher record the activity-ALIAS
                // component in baseIntent (icon switching aliases, see
                // LauncherIcons), not MainActivity itself.
                || LauncherIcons.isLauncherAliasClassName(cls)
                || "io.github.abdurazaaqmohammed.ui.editor.PickFileActivity".equals(cls)
                || "io.github.abdurazaaqmohammed.ui.viewer.SharedEntryActivity".equals(cls)
                || "io.github.abdurazaaqmohammed.feature.system.ToolShortcutActivity".equals(cls)
                || "io.github.ratul.topactivity.ui.ClipboardActivity".equals(cls);
    }

    private static boolean isEditorClassName(String name) {
        return "io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity".equals(name)
                || "io.github.abdurazaaqmohammed.ui.editor.HexEditorActivity".equals(name)
                || "modder.hub.dexeditor.activity.DexEditorActivity".equals(name)
                || "io.github.abdurazaaqmohammed.feature.apk.ArscEditorActivity".equals(name)
                || "io.github.abdurazaaqmohammed.feature.apk.ArscEditorPlusActivity".equals(name)
                || "io.github.abdurazaaqmohammed.feature.apk.ArscTextActivity".equals(name);
    }

    public static boolean isEditor(Activity activity) {
        return activity != null && isEditorClassName(activity.getClass().getName());
    }

    public static boolean isEditor(Intent intent) {
        return intent != null && intent.getComponent() != null
                && isEditorClassName(intent.getComponent().getClassName());
    }

    private static boolean isSecondaryTask(ActivityManager.RecentTaskInfo info) {
        if (info == null || info.baseIntent == null || info.baseIntent.getComponent() == null) {
            return false;
        }
        return !isExcludedTransient(info.baseIntent.getComponent().getClassName());
    }

    /**
     * True only when {@code activity} is actually the root of a standalone task
     * outside {@link MainActivity}'s back stack (e.g. multi-task mode enabled or
     * launched standalone from a notification/shortcut), excluding transient
     * entry/picker helpers and activities pushed onto an existing task.
     */
    public static boolean isSecondaryTaskRoot(Activity activity) {
        if (activity == null) return false;
        String cls = activity.getClass().getName();
        if (isExcludedTransient(cls) || !activity.isTaskRoot()) return false;
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            int taskId = activity.getTaskId();
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info != null && taskIdOf(info) == taskId && isSecondaryTask(info)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean showEditorTasksInRecents(Context context) {
        if (context == null) return false;
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_SHOW_EDITOR_TASKS_IN_RECENTS, false);
    }

    public static void updateRecentsVisibility(Context context, boolean showInRecents) {
        if (context == null) return;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info != null && info.baseIntent != null
                        && (isEditor(info.baseIntent) || isSecondaryTask(info))) {
                    task.setExcludeFromRecents(!showInRecents);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void syncActivityTaskRecents(Activity activity) {
        if (activity == null || activity instanceof MainActivity) return;
        boolean exclude = !showEditorTasksInRecents(activity);
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            int taskId = activity.getTaskId();
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info != null && taskIdOf(info) == taskId && isSecondaryTask(info)) {
                    task.setExcludeFromRecents(exclude);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Configures standalone task and Recents visibility flags without altering result tokens. */
    public static void applyTaskFlags(Context context, Intent intent) {
        if (intent == null) return;
        int flags = (intent.getFlags() & ~(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP))
                | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        if (!showEditorTasksInRecents(context)) {
            flags |= Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        }
        intent.setFlags(flags);
    }

    /** Clears standalone task flags so an editor launches cleanly inside the caller's task stack. */
    public static void applySingleTaskFlags(Intent intent) {
        if (intent == null) return;
        intent.setFlags(intent.getFlags() & ~(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
    }

    public static Intent prepareSingleTask(Intent source, int requestCode) {
        Intent intent = new Intent(source);
        applySingleTaskFlags(intent);
        intent.removeExtra(RESULT);
        intent.removeExtra(EXTRA_RESTORED_FROM_MINIMIZED);
        intent.removeExtra(EXTRA_MINIMIZE_TOKEN);
        if (requestCode >= 0) {
            intent.putExtra(EXTRA_SINGLE_TASK_REQUEST, requestCode);
        } else {
            intent.removeExtra(EXTRA_SINGLE_TASK_REQUEST);
        }
        return intent;
    }

    public static Intent prepare(Intent source, String owner, int request) {
        return prepare(null, source, owner, request);
    }

    public static Intent prepare(Context context, Intent source, String owner, int request) {
        Intent intent = new Intent(source);
        applyTaskFlags(context, intent);
        // NEW_TASK cancels Android's native result link. Keep a process-local link
        // instead, including ActivityResultRegistry's generated request codes.
        intent.removeExtra(RESULT);
        if (request >= 0) {
            String key = UUID.randomUUID().toString();
            results.put(key, new Result(owner, request));
            intent.putExtra(RESULT, key);
        }
        return intent;
    }

    public static void setResult(Activity activity, int code, Intent data) {
        Result result = results.get(activity.getIntent().getStringExtra(RESULT));
        if (result != null) { result.code = code; result.data = data; }
    }

    public static void finished(Activity activity) {
        if (activity == null || activity.getIntent() == null) return;
        String key = activity.getIntent().getStringExtra(RESULT);
        Result result = key != null ? results.remove(key) : null;
        if (result == null) return;
        synchronized (pendingResults) {
            pendingResults.put(result.request, result);
        }
        WeakReference<BaseActivity> ref = owners.get(result.owner);
        BaseActivity owner = ref != null ? ref.get() : null;
        if (owner != null && !owner.isFinishing() && !owner.isDestroyed()) {
            try {
                owner.getWindow().getDecorView().post(() -> {
                    boolean shouldDispatch;
                    synchronized (pendingResults) {
                        shouldDispatch = pendingResults.get(result.request) == result;
                        if (shouldDispatch) {
                            pendingResults.remove(result.request);
                        }
                    }
                    if (shouldDispatch) {
                        owner.dispatchEditorResult(result.request, result.code, result.data);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    public static void returnToMain(Activity activity) {
        if (activity == null) return;
        Result res = null;
        synchronized (pendingResults) {
            Iterator<Result> it = pendingResults.values().iterator();
            while (it.hasNext()) {
                res = it.next();
            }
        }
        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (ActivityManager.AppTask task : am.getAppTasks()) {
                    ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    if (info != null && info.baseIntent != null && info.baseIntent.getComponent() != null) {
                        String cls = info.baseIntent.getComponent().getClassName();
                        // Launcher-tapped tasks carry the icon alias, not MainActivity.
                        if (cls.contains("MainActivity")
                                || LauncherIcons.isLauncherAliasClassName(cls)) {
                            task.moveToFront();
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // Fallback if AppTask not found
        try {
            Intent main = new Intent(activity, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (res != null && res.data != null) {
                main.putExtra("pending_editor_request", res.request);
                main.putExtra("pending_editor_code", res.code);
                main.putExtra("pending_editor_data", res.data);
            }
            activity.startActivity(main);
        } catch (Exception ignored) {
        }
    }

    public static void unbind(String id, BaseActivity activity) {
        WeakReference<BaseActivity> ref = owners.get(id);
        if (ref != null && ref.get() == activity) owners.remove(id);
        if (activity.isFinishing()) results.values().removeIf(result -> result.owner.equals(id));
    }
}
