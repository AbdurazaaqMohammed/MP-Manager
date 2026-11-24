package io.github.abdurazaaqmohammed.util.misc;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/** Gives each editor its own task without breaking activity-result callers. */
public final class EditorTasks {
    public static final String PREF_SHOW_EDITOR_TASKS_IN_RECENTS = "show_editor_tasks_in_recents";
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

    public static boolean isEditor(Activity activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        return name.equals("io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.ui.editor.HexEditorActivity")
                || name.equals("modder.hub.dexeditor.activity.DexEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscEditorPlusActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscTextActivity");
    }

    public static boolean isEditor(Intent intent) {
        if (intent.getComponent() == null) return false;
        String name = intent.getComponent().getClassName();
        return name.equals("io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.ui.editor.HexEditorActivity")
                || name.equals("modder.hub.dexeditor.activity.DexEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscEditorPlusActivity")
                || name.equals("io.github.abdurazaaqmohammed.feature.apk.ArscTextActivity");
    }

    private static boolean isSecondaryTask(ActivityManager.RecentTaskInfo info) {
        if (info == null || info.baseIntent == null || info.baseIntent.getComponent() == null) {
            return false;
        }
        String cls = info.baseIntent.getComponent().getClassName();
        return !MainActivity.class.getName().equals(cls);
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
                if (info != null && info.taskId == taskId && isSecondaryTask(info)) {
                    task.setExcludeFromRecents(exclude);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static Intent prepare(Intent source, String owner, int request) {
        return prepare(null, source, owner, request);
    }

    public static Intent prepare(Context context, Intent source, String owner, int request) {
        Intent intent = new Intent(source);
        int flags = (intent.getFlags() & ~(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP))
                | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        if (!showEditorTasksInRecents(context)) {
            flags |= Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        }
        intent.setFlags(flags);
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
                        if (cls.contains("MainActivity")) {
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
