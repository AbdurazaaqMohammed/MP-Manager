package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Keeps the external-plugin trust store in sync with installs: uninstalls
 * drop the pin (a reinstall is a stranger until re-pinned); updates keep
 * the pin and are re-verified lazily on next invoke (rotation handled by
 * {@link PluginHost#isTrusted} history check, hard changes re-prompt).
 */
public class PluginPackageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null || intent.getData() == null) return;
            String action = intent.getAction();
            String pkg = intent.getData().getSchemeSpecificPart();
            if (pkg == null || pkg.isEmpty()) return;
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                PluginHost.removeTrust(context.getApplicationContext(), pkg);
            }
        } catch (Exception ignored) {
        }
    }
}
