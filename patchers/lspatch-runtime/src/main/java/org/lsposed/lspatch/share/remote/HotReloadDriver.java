package org.lsposed.lspatch.share.remote;

import android.os.Bundle;
import android.util.Log;

import java.util.Collections;
import java.util.List;

import io.github.libxposed.service.HookedProcess;
import io.github.libxposed.service.IHotReloadCallback;
import io.github.libxposed.service.IXposedService;

/**
 * What drives hot reload — the second mode-dependent seam of the service.
 *
 * <p>Manager mode implements this by keeping the {@code IProcessChannel} every host attaches and
 * playing the daemon's role (mirroring Vector's {@code FrameworkService}/{@code ModuleAppService}
 * split); embedded mode has no persistent framework and no newer generation to load, so it uses
 * {@link #UNSUPPORTED}, which answers exactly as the daemon does for a target that cannot reload.</p>
 */
public interface HotReloadDriver {

    List<HookedProcess> getRunningTargets(String modulePackageName);

    void hotReload(String modulePackageName, long targetId, Bundle data, IHotReloadCallback callback);

    /** No targets, and every reload request answered {@code HOT_RELOAD_UNSUPPORTED}. */
    HotReloadDriver UNSUPPORTED = new HotReloadDriver() {
        @Override
        public List<HookedProcess> getRunningTargets(String modulePackageName) {
            return Collections.emptyList();
        }

        @Override
        public void hotReload(String modulePackageName, long targetId, Bundle data, IHotReloadCallback callback) {
            try {
                if (callback != null) {
                    callback.onHotReloadResult(IXposedService.HOT_RELOAD_UNSUPPORTED,
                            "hot reload is unsupported for integrated modules");
                }
            } catch (Throwable t) {
                Log.w("LSPatch-HotReload", "onHotReloadResult", t);
            }
        }
    };
}
