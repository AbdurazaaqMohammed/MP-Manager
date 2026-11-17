package io.github.abdurazaaqmohammed.patcherfixture.modern;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

public final class FixtureModule extends XposedModule {
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        Log.i("MP-Patcher-Fixture", "MODERN_LOADED=" + getRemotePreferences("fixture").getString("value", "unset"));
        getRemotePreferences("fixture").registerOnSharedPreferenceChangeListener((preferences, key) ->
                Log.i("MP-Patcher-Fixture", "MODERN_PREF=" + preferences.getString("value", "unset")));
    }

    @Override public boolean onHotReloading(HotReloadingParam param) {
        Log.i("MP-Patcher-Fixture", "MODERN_RELOADING");
        return true;
    }

    @Override public void onHotReloaded(HotReloadedParam param) {
        Log.i("MP-Patcher-Fixture", "MODERN_RELOADED");
    }
}
