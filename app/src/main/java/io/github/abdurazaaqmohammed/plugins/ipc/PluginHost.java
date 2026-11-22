package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ComponentInfo;
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
        return queryActivities(context, action, false);
    }

    /** Management screens also need to list explicitly disabled packages. */
    public static List<ExternalPlugin> queryIncludingDisabled(Context context, String action) {
        return queryActivities(context, action, true);
    }

    private static List<ExternalPlugin> queryActivities(Context context, String action,
                                                        boolean includeDisabled) {
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
                if (!ai.exported || !ai.enabled || ai.applicationInfo == null
                        || !ai.applicationInfo.enabled
                        || (!includeDisabled && isDisabled(context, ai.packageName))) continue;
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

    /** Explicit intent for a plugin activity. Callers must verify trust before launch. */
    public static Intent explicitIntent(ExternalPlugin plugin, String action) {
        Intent intent = new Intent(action);
        intent.setClassName(plugin.packageName, plugin.className);
        return intent;
    }

    /** Host capabilities and appearance are data, never host permissions. */
    public static Intent explicitIntent(Context context, ExternalPlugin plugin, String action) {
        Intent intent = explicitIntent(plugin, action);
        intent.putExtra(PluginContracts.EXTRA_PLUGIN_ID, plugin.pluginId);
        intent.putExtra(PluginContracts.EXTRA_CONTRACT_VERSION, PluginContracts.CONTRACT_VERSION);
        intent.putExtra(PluginContracts.EXTRA_HOST_PACKAGE, context.getPackageName());
        intent.putExtra(PluginContracts.EXTRA_HOST_HOOKS, PluginHooks.actionHooks());
        io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry
                .applyPluginThemeExtras(context, intent);
        return intent;
    }

    /** Receivers are discovered without running their code. */
    public static List<ExternalPlugin> queryEventReceivers(Context context,
                                                          boolean includeDisabled) {
        List<ExternalPlugin> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> infos = pm.queryBroadcastReceivers(
                    new Intent(PluginContracts.ACTION_EVENT), PackageManager.GET_META_DATA);
            if (infos == null) return out;
            for (ResolveInfo info : infos) {
                if (info == null || info.activityInfo == null) continue;
                ActivityInfo receiver = info.activityInfo;
                if (!receiver.exported || !receiver.enabled || receiver.applicationInfo == null
                        || !receiver.applicationInfo.enabled
                        || (!includeDisabled && isDisabled(context, receiver.packageName))) continue;
                Bundle meta = receiver.metaData;
                String pluginId = "";
                try {
                    if (meta != null) pluginId = meta.getString(PluginContracts.META_PLUGIN_ID, "");
                } catch (Exception ignored) {
                }
                CharSequence label = receiver.packageName;
                try {
                    label = info.loadLabel(pm);
                } catch (Exception ignored) {
                }
                out.add(new ExternalPlugin(receiver.packageName, receiver.name,
                        pluginId, label, meta));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Validate the explicit activity and certificate again at dispatch time. */
    public static boolean canInvoke(Context context, ExternalPlugin plugin) {
        if (context == null || plugin == null || !isTrusted(context, plugin.packageName)) return false;
        try {
            ComponentInfo info = context.getPackageManager().getActivityInfo(
                    new android.content.ComponentName(plugin.packageName, plugin.className), 0);
            return info.exported && info.enabled && info.applicationInfo != null
                    && info.applicationInfo.enabled;
        } catch (Exception ignored) {
            return false;
        }
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

    /** False only after an explicit disable; unseen plugins may still be offered. */
    public static boolean isDisabled(Context context, String pkg) {
        try {
            SharedPreferences p = prefs(context);
            return p.contains(pkg + "_enabled") && !p.getBoolean(pkg + "_enabled", false);
        } catch (Exception ignored) {
            return true;
        }
    }

    public static void setTrusted(Context context, String pkg, String digest,
                                  String label, boolean enabled) {
        try {
            String normalized = digest == null ? "" : digest.toLowerCase(java.util.Locale.ROOT);
            String oldDigest = pinnedDigest(context, pkg);
            SharedPreferences.Editor editor = prefs(context).edit()
                    .putString(pkg + "_digest", normalized)
                    .putString(pkg + "_label", label == null ? pkg : label)
                    .putBoolean(pkg + "_enabled", enabled);
            // Event consent is tied to this certificate, never transferred on re-pinning.
            if (oldDigest == null || !oldDigest.equals(normalized)) {
                editor.remove(pkg + "_events").remove(pkg + "_events_digest")
                        .remove(pkg + "_event_ids");
            }
            editor.apply();
            ExternalActions.clearAllCaches();
            HookRegistry.invalidate();
        } catch (Exception ignored) {
        }
    }

    public static boolean eventsEnabled(Context context, String pkg) {
        try {
            SharedPreferences p = prefs(context);
            String digest = p.getString(pkg + "_events_digest", "");
            return p.getBoolean(pkg + "_events", false) && !digest.isEmpty()
                    && digest.equals(pinnedDigest(context, pkg)) && isTrusted(context, pkg);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean eventAllowed(Context context, String pkg, String eventId) {
        try {
            return eventsEnabled(context, pkg) && PluginHooks.isEvent(eventId)
                    && prefs(context).getStringSet(pkg + "_event_ids",
                            java.util.Collections.emptySet()).contains(eventId);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void setEventsEnabled(Context context, String pkg, boolean enabled) {
        java.util.Set<String> interests = new java.util.HashSet<>();
        if (enabled) {
            for (ExternalPlugin receiver : queryEventReceivers(context, true)) {
                if (!pkg.equals(receiver.packageName) || !HookRegistry.supportsVersion(receiver)) continue;
                for (String event : HookRegistry.declaredHooks(receiver)) {
                    if (PluginHooks.isEvent(event)) interests.add(event);
                }
            }
        }
        setEventsEnabled(context, pkg, enabled, interests);
    }

    /** Freeze exactly the disclosed event IDs, so an update cannot expand consent. */
    public static void setEventsEnabled(Context context, String pkg, boolean enabled,
                                       java.util.Collection<String> disclosedEvents) {
        try {
            String digest = pinnedDigest(context, pkg);
            if (enabled && (!isTrusted(context, pkg) || digest == null)) return;
            java.util.Set<String> interests = new java.util.HashSet<>();
            if (enabled && disclosedEvents != null) {
                for (String event : disclosedEvents) if (PluginHooks.isEvent(event)) interests.add(event);
            }
            prefs(context).edit().putBoolean(pkg + "_events", enabled)
                    .putString(pkg + "_events_digest", enabled ? digest : "")
                    .putStringSet(pkg + "_event_ids", interests).apply();
        } catch (Exception ignored) {
        }
    }

    public static void removeTrust(Context context, String pkg) {
        try {
            SharedPreferences.Editor editor = prefs(context).edit()
                    .remove(pkg + "_digest")
                    .remove(pkg + "_label")
                    .remove(pkg + "_enabled")
                    .remove(pkg + "_events")
                    .remove(pkg + "_events_digest")
                    .remove(pkg + "_event_ids");
            editor.apply();
            android.content.SharedPreferences settings = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(context);
            android.content.SharedPreferences.Editor values = settings.edit();
            String prefix = "external_setting:" + pkg + ":";
            for (String key : settings.getAll().keySet()) if (key.startsWith(prefix)) values.remove(key);
            values.apply();
            ExternalActions.clearAllCaches();
            HookRegistry.invalidate();
            HookOrder.removePackage(context, pkg);
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
