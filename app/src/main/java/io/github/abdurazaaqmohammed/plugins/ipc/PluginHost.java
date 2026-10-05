package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Bundle;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Trust core for external (out-of-process) plugins.
 *
 * <p>Discovery: plugins expose exported activities with the
 * {@link PluginContracts} actions; the host queries them and always invokes
 * via explicit intents. Trust: the user pins each plugin package's signing
 * certificate digest on first use; the pin is re-verified on every invoke
 * and a changed certificate forces a re-prompt (rotation is accepted only
 * when the old digest is still in the certificate history).
 */
public final class PluginHost {

    private static final String PREFS = "plugin_trust";
    public static final String APK_MIME = "application/vnd.android.package-archive";

    private PluginHost() {
    }

    /** One discovered plugin activity. */
    public static final class ExternalPlugin {
        public final String packageName;
        public final String className;
        public final String pluginId;
        public final CharSequence label;
        public final Bundle meta;

        ExternalPlugin(String packageName, String className, String pluginId,
                       CharSequence label, Bundle meta) {
            this.packageName = packageName;
            this.className = className;
            this.pluginId = pluginId;
            this.label = label;
            this.meta = meta;
        }
    }

    /**
     * All installed activities handling the action, with their declared
     * plugin id (or "" when the meta-data is missing).
     */
    public static List<ExternalPlugin> query(Context context, String action) {
        List<ExternalPlugin> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent queryIntent = new Intent(action);
            if (PluginContracts.ACTION_APK.equals(action)) {
                queryIntent.setType(PluginHost.APK_MIME);
            }
            List<ResolveInfo> infos = pm.queryIntentActivities(
                    queryIntent, PackageManager.MATCH_DEFAULT_ONLY
                            | PackageManager.GET_META_DATA);
            if (infos == null) return out;
            for (ResolveInfo info : infos) {
                if (info == null || info.activityInfo == null) continue;
                ActivityInfo ai = info.activityInfo;
                Bundle meta = null;
                String pluginId = "";
                try {
                    meta = ai.metaData;
                    if (meta != null) {
                        String v = meta.getString(PluginContracts.META_PLUGIN_ID);
                        if (v != null) pluginId = v;
                    }
                } catch (Exception ignored) {
                }
                CharSequence label = null;
                try {
                    label = info.loadLabel(pm);
                } catch (Exception ignored) {
                }
                if (label == null) label = ai.packageName;
                out.add(new ExternalPlugin(ai.packageName, ai.name, pluginId, label, meta));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Explicit intent for a trusted plugin activity. */
    public static Intent explicitIntent(ExternalPlugin plugin, String action) {
        Intent intent = new Intent(action);
        intent.setClassName(plugin.packageName, plugin.className);
        return intent;
    }

    /** Current signing-cert digest (lowercase hex SHA-256), or null. */
    public static String certDigest(Context context, String pkg) {
        List<String> history = certHistory(context, pkg);
        return history.isEmpty() ? null : history.get(0);
    }

    /**
     * Certificate history, newest first (rotation lineage). Empty on failure.
     */
    public static List<String> certHistory(Context context, String pkg) {
        List<String> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(pkg,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (info == null || info.signingInfo == null) return out;
                SigningInfo si = info.signingInfo;
                if (si.hasMultipleSigners()) {
                    addDigests(out, si.getApkContentsSigners());
                } else {
                    addDigests(out, si.getSigningCertificateHistory());
                }
            } else {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (info == null || info.signatures == null) return out;
                addDigests(out, info.signatures);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void addDigests(List<String> out, Signature[] sigs) {
        try {
            if (sigs == null) return;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) {
                if (s == null) continue;
                md.reset();
                byte[] hash = md.digest(s.toByteArray());
                StringBuilder sb = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    String h = Integer.toHexString(0xFF & b);
                    if (h.length() == 1) sb.append('0');
                    sb.append(h);
                }
                String hex = sb.toString();
                if (!out.contains(hex)) out.add(hex);
            }
        } catch (Exception ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String pinnedDigest(Context context, String pkg) {
        try {
            return prefs(context).getString(pkg + "_digest", null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * True when the package is pinned AND currently signed with the pinned
     * certificate (or a rotation that still contains it in history).
     */
    public static boolean isTrusted(Context context, String pkg) {
        try {
            SharedPreferences p = prefs(context);
            if (!p.getBoolean(pkg + "_enabled", false)) return false;
            String pinned = p.getString(pkg + "_digest", null);
            if (pinned == null || pinned.isEmpty()) return false;
            List<String> history = certHistory(context, pkg);
            return history.contains(pinned.toLowerCase());
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * True when the package was seen before under a different certificate
     * that is NOT a rotation of the pinned one — the user must re-prompt.
     */
    public static boolean needsReprompt(Context context, String pkg) {
        try {
            String pinned = pinnedDigest(context, pkg);
            if (pinned == null || pinned.isEmpty()) return true;
            List<String> history = certHistory(context, pkg);
            if (history.isEmpty()) return true;
            if (history.contains(pinned.toLowerCase())) return false;
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    public static void setTrusted(Context context, String pkg, String digest,
                                  String label, boolean enabled) {
        try {
            prefs(context).edit()
                    .putString(pkg + "_digest", digest == null ? "" : digest.toLowerCase())
                    .putString(pkg + "_label", label == null ? pkg : label)
                    .putBoolean(pkg + "_enabled", enabled)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void removeTrust(Context context, String pkg) {
        try {
            prefs(context).edit()
                    .remove(pkg + "_digest")
                    .remove(pkg + "_label")
                    .remove(pkg + "_enabled")
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static String trustedLabel(Context context, String pkg) {
        try {
            String label = prefs(context).getString(pkg + "_label", null);
            return label == null || label.isEmpty() ? pkg : label;
        } catch (Exception ignored) {
            return pkg;
        }
    }
}
