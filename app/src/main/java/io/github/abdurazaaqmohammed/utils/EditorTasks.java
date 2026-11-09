package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

/** Gives each editor its own task without breaking activity-result callers. */
public final class EditorTasks {
    private static final String OWNER = "mp.editor.owner";
    private static final String RESULT = "mp.editor.result";
    private static final Map<String, WeakReference<BaseActivity>> owners = new HashMap<>();
    private static final Map<String, Result> results = new HashMap<>();

    private static final class Result {
        final String owner;
        final int request;
        int code = Activity.RESULT_CANCELED;
        Intent data;
        Result(String owner, int request) { this.owner = owner; this.request = request; }
    }

    private EditorTasks() {}

    public static String bind(BaseActivity activity, Bundle state) {
        String id = state != null ? state.getString(OWNER) : null;
        if (id == null) id = UUID.randomUUID().toString();
        owners.put(id, new WeakReference<>(activity));
        return id;
    }

    public static void saveOwner(Bundle state, String id) { state.putString(OWNER, id); }

    public static boolean isEditor(Intent intent) {
        if (intent.getComponent() == null) return false;
        String name = intent.getComponent().getClassName();
        return name.equals("io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity")
                || name.equals("modder.hub.dexeditor.activity.DexEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.arsc.ArscEditorActivity")
                || name.equals("io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity")
                || name.equals("io.github.abdurazaaqmohammed.arsc.ArscTextActivity");
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
        Result result = results.remove(activity.getIntent().getStringExtra(RESULT));
        if (result == null) return;
        WeakReference<BaseActivity> ref = owners.get(result.owner);
        BaseActivity owner = ref != null ? ref.get() : null;
        if (owner != null && !owner.isFinishing() && !owner.isDestroyed()) {
            owner.getWindow().getDecorView().post(() ->
                    owner.dispatchEditorResult(result.request, result.code, result.data));
        }
    }

    public static void unbind(String id, BaseActivity activity) {
        WeakReference<BaseActivity> ref = owners.get(id);
        if (ref != null && ref.get() == activity) owners.remove(id);
        if (activity.isFinishing()) results.values().removeIf(result -> result.owner.equals(id));
    }
}
