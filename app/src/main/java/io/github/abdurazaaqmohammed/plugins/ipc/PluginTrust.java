package io.github.abdurazaaqmohammed.plugins.ipc;

import android.R;
import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost.ExternalPlugin;

/**
 * First-seen / rotation consent for external plugins. Shows who is asking
 * (label, package, certificate fingerprint) and pins the certificate on
 * allow. Every invoke path must go through {@link #ensureTrusted}.
 */
public final class PluginTrust {

    private PluginTrust() {
    }

    /**
     * Runs onTrust immediately when the plugin is trusted, otherwise prompts
     * to pin its certificate first. Safe to call from any thread (dialog is
     * posted to the UI thread).
     */
    public static void ensureTrusted(final Activity activity,
                                     final ExternalPlugin plugin,
                                     final Runnable onTrust) {
        if (activity == null || plugin == null) return;
        try {
            if (PluginHost.isTrusted(activity, plugin.packageName)) {
                onTrust.run();
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            activity.runOnUiThread(() -> showPrompt(activity, plugin, onTrust));
        } catch (Exception ignored) {
        }
    }

    private static void showPrompt(Activity activity, ExternalPlugin plugin,
                                   Runnable onTrust) {
        try {
            String digest = PluginHost.certDigest(activity, plugin.packageName);
            boolean rotation = digest != null
                    && PluginHost.pinnedDigest(activity, plugin.packageName) != null;
            String title = rotation ? "Plugin certificate changed" : "Allow external plugin?";
            StringBuilder msg = new StringBuilder();
            msg.append(plugin.label).append('\n').append(plugin.packageName);
            if (rotation) {
                msg.append("\n\nThis plugin updated with a NEW certificate.")
                        .append(" Allow only if you trust the update source.");
            } else {
                msg.append("\n\nThis plugin runs as a separate app with its own"
                        + " permissions. Allow it to integrate with MP-Manager?");
            }
            msg.append("\n\nCertificate (SHA-256):\n").append(fingerprint(digest));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setMessage(msg.toString())
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Allow", (d, w) -> {
                        try {
                            String fresh = PluginHost.certDigest(activity, plugin.packageName);
                            if (fresh == null) return;
                            PluginHost.setTrusted(activity, plugin.packageName, fresh,
                                    String.valueOf(plugin.label), true);
                            onTrust.run();
                        } catch (Exception ignored) {
                        }
                    })
                    .show();
        } catch (Exception ignored) {
        }
    }

    private static String fingerprint(String hex) {
        if (hex == null || hex.isEmpty()) return "(unavailable)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (sb.length() > 0) sb.append(i % 32 == 0 ? '\n' : ':');
            sb.append(hex.substring(i, Math.min(i + 2, hex.length())));
        }
        return sb.toString();
    }
}
