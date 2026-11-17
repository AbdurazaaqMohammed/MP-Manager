package io.github.abdurazaaqmohammed.feature.apk.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class LSPatchPackageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < 28 || intent.getData() == null) return;
        String module = intent.getData().getSchemeSpecificPart();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                LSPatchRemoteServices services = LSPatchRemoteServices.get(context);
                if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) services.repository.moduleRemoved(module);
                } else {
                    services.push(java.util.List.of(module));
                    services.hotReload.autoReload(module);
                }
            } catch (Exception error) { Log.e("MP-LSPatch", "Module update failed", error); }
            finally { pending.finish(); }
        }, "LSPatch-package-update").start();
    }
}
