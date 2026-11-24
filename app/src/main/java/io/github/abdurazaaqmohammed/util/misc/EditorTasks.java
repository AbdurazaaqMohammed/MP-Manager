package io.github.abdurazaaqmohammed.util.misc;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/** Gives each editor its own task without breaking activity-result callers. */
public final class EditorTasks {
    private static final String OWNER = "mp.editor.owner";
    private static final String RESULT = "mp.editor.result";
    private static final Map<String, WeakReference<BaseActivity>> owners = new HashMap<>();
    private static final Map<String, Result> results = new HashMap<>();
    private static volatile Result pendingResult;

    public static final class Result {
        public final String owner;
        public final int request;
        public int code = Activity.RESULT_CANCELED;
        public Intent data;
        public Result(String owner, int request) { this.owner = owner; this.request = request; }
    }

    public static Result getAndClearPendingResult() {
        Result res = pendingResult;
        pendingResult = null;
        return res;
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

    public static Intent prepare(Intent source, String owner, int request) {
        Intent intent = new Intent(source);
        intent.setFlags((intent.getFlags() & ~(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP))
                | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
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
        pendingResult = result;
        WeakReference<BaseActivity> ref = owners.get(result.owner);
        BaseActivity owner = ref != null ? ref.get() : null;
        if (owner != null && !owner.isFinishing() && !owner.isDestroyed()) {
            try {
                owner.getWindow().getDecorView().post(() -> {
                    if (pendingResult == result) {
                        pendingResult = null;
                        owner.dispatchEditorResult(result.request, result.code, result.data);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    public static void returnToMain(Activity activity) {
        if (activity == null) return;
        Result res = pendingResult;
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
