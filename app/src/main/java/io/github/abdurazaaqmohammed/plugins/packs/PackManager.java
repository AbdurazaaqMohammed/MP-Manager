package io.github.abdurazaaqmohammed.plugins.packs;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import dalvik.system.DexClassLoader;

import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.ext.AppExtension;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs, verifies, loads and removes downloadable tool packs.
 *
 * <p>Trust model: packs run in the host process with host permissions.
 * Catalog entries SHOULD carry a SHA-256; files with a known checksum are
 * rejected on mismatch. Entries without a checksum install only after the
 * user confirms in ToolsHub (sideload path, e.g. local test builds).
 */
public final class PackManager {

    private static final String PREFS = "tool_packs";
    private static final String KEY_PREFIX = "pack_";

    /** packId -> loaded tool ids, for unregister on remove/update. */
    private static final Map<String, List<String>> LOADED = new HashMap<>();

    /** packId -> version reported by the loaded pack APK itself. */
    private static final Map<String, Integer> PACK_VERSIONS = new HashMap<>();

    /** packId -> loaded extensions, for unregister on remove/update. */
    private static final Map<String, List<AppExtension>> LOADED_EXT = new HashMap<>();

    /** Set when loadPack throws; consumed by installDownloadedPack. */
    private static String sLoadError;

    private PackManager() {
    }

    public static File packsDir(Context context) {
        File dir = new File(context.getFilesDir(), "packs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File codeCacheDir(Context context) {
        File dir = new File(context.getCodeCacheDir(), "packs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------- install state ----------

    public static boolean isInstalled(Context context, String packId) {
        return installedVersion(context, packId) > 0 && packFile(context, packId).exists();
    }

    public static int installedVersion(Context context, String packId) {
        try {
            return prefs(context).getInt(KEY_PREFIX + packId + "_version", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String installedEntry(Context context, String packId) {
        try {
            return prefs(context).getString(KEY_PREFIX + packId + "_entry", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static File packFile(Context context, String packId) {
        return new File(packsDir(context), packId + ".apk");
    }

    // ---------- loading ----------

    /** Load every installed pack. Called once from Application.onCreate. */
    public static void loadInstalledPacks(Context context) {
        for (PackDescriptor p : PackCatalog.load(context)) {
            if (isInstalled(context, p.id)) {
                loadPack(context, packFile(context, p.id), p.entryClass, p.id);
            }
        }
    }

    /**
     * Load one pack APK and register its tools. Returns the tool ids loaded.
     */
    public static synchronized List<String> loadPack(Context context, File apk, String entryClass, String packId) {
        List<String> loaded = new ArrayList<>();
        if (apk == null || !apk.exists() || entryClass == null || entryClass.isEmpty()) return loaded;
        // Same-signer gate: in-process code inherits every host permission
        // (root, Shizuku, all files, network), so only first-party packs may
        // load here. Third-party code must ship as an external plugin.
        // Debuggable builds skip the gate for local pack development.
        if (!PackSignatures.isDebuggable(context)
                && !PackSignatures.isSameSignerAsHost(context, apk)) {
            PackSignatures.refuse("Pack is not signed by the app developer;"
                    + " in-process packs must be first-party."
                    + " Third-party plugins must be installed as separate apps.");
            return loaded;
        }
        try {
            if (packId != null && !packId.isEmpty()) {
                // Load the pack's own resources.arsc so its strings.xml is
                // available to the tools for translation. Failure is tolerated:
                // tools fall back to their inline English literals.
                PackRes.register(packId, PackRes.load(context, apk.getAbsolutePath()));
            }
            DexClassLoader loader = new DexClassLoader(
                    apk.getAbsolutePath(),
                    codeCacheDir(context).getAbsolutePath(),
                    null,
                    context.getClassLoader());
            Class<?> clazz = Class.forName(entryClass, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ToolPack)) return loaded;
            ToolPack pack = (ToolPack) instance;
            if (packId != null && !packId.isEmpty() && !packId.equals(pack.packId())) return loaded;
            if (packId != null && !packId.isEmpty()) {
                PACK_VERSIONS.put(packId, pack.version());
            }
            for (ToolPlugin tool : pack.tools()) {
                try {
                    if (tool == null || tool.id() == null) continue;
                    PluginRegistry.register(tool);
                    loaded.add(tool.id());
                } catch (Exception ignored) {
                }
            }
            List<AppExtension> extensions = new ArrayList<>();
            try {
                List<AppExtension> contributed = pack.extensions();
                if (contributed != null) {
                    for (AppExtension ext : contributed) {
                        if (ext == null) continue;
                        ExtensionRegistry.register(ext);
                        extensions.add(ext);
                    }
                }
            } catch (Exception ignored) {
            }
            if (packId != null && !packId.isEmpty()) {
                LOADED.put(packId, loaded);
                LOADED_EXT.put(packId, extensions);
            }
        } catch (Throwable t) {
            sLoadError = t.getMessage() != null ? t.getMessage() : t.toString();
        }
        return loaded;
    }

    /** Why the last loadPack failed; consumed by the installer for its error. */
    static synchronized String takeLoadError() {
        String r = sLoadError;
        sLoadError = null;
        return r;
    }

    // ---------- download / install / remove ----------

    /**
     * Enqueue a pack download with the system DownloadManager.
     * Returns the download id, or -1 when the URL is empty.
     */
    public static long enqueueDownload(Context context, PackDescriptor pack) {
        if (pack == null || pack.apkUrl == null || pack.apkUrl.isEmpty()) return -1;
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(pack.apkUrl));
            req.setTitle(pack.title);
            req.setDescription("Tool pack download");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    "pack-" + pack.id + ".apk");
            return dm.enqueue(req);
        } catch (Exception e) {
            return -1;
        }
    }

    public static File downloadOutput(Context context, String packId) {
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "pack-" + packId + ".apk");
    }

    /**
     * Verify (checksum when known) and install a downloaded APK file.
     * Returns null on success, otherwise an error message.
     */
    public static synchronized String installDownloadedPack(Context context, PackDescriptor pack, File downloaded) {
        return installDownloadedPack(context, pack, downloaded, true);
    }

    /**
     * @param requireChecksum when true, packs without a catalog checksum are
     *                        rejected. Download path always requires it;
     *                        sideload relaxes it on debuggable builds only.
     *
     * <p>The previous APK is backed up first; if the new build fails to
     * load, reports a different version than the catalog, or is missing
     * tools the catalog lists, the previous APK is restored so a bad
     * update can never leave a pack half-installed.
     */
    public static synchronized String installDownloadedPack(Context context, PackDescriptor pack, File downloaded, boolean requireChecksum) {
        if (pack == null || downloaded == null || !downloaded.exists()) {
            return "Downloaded file is missing";
        }
        if (requireChecksum && !pack.hasChecksum()) {
            return "Pack has no checksum; refusing install."
                    + " The catalog entry must pin a SHA-256.";
        }
        if (pack.hasChecksum()) {
            String actual = sha256(downloaded);
            if (actual == null || !actual.equalsIgnoreCase(pack.sha256.trim())) {
                return "Checksum mismatch, pack rejected";
            }
        }
        File dest = packFile(context, pack.id);
        File backup = new File(dest.getParentFile(), pack.id + ".apk.bak");
        boolean hadPrevious = dest.exists();
        int previousVersion = installedVersion(context, pack.id);
        String previousEntry = installedEntry(context, pack.id);
        try {
            if (hadPrevious) {
                backup.delete();
                copy(dest, backup);
            }
            // The runtime refuses to load a dex from a file the app can still
            // write to ("Writable dex file ... is not allowed"), so the
            // installed APK has to end up read-only. Grant write back first:
            // the previous APK is already read-only and would not be
            // overwriteable.
            if (hadPrevious && !dest.setWritable(true)) dest.delete();
            copy(downloaded, dest);
            dest.setWritable(false);
            dest.setReadable(true, false);
            prefs(context).edit()
                    .putInt(KEY_PREFIX + pack.id + "_version", pack.version)
                    .putString(KEY_PREFIX + pack.id + "_entry", pack.entryClass)
                    .apply();
            unloadPack(pack.id);
            loadPack(context, dest, pack.entryClass, pack.id);
            String refusal = PackSignatures.takeRefusal();
            if (refusal != null) {
                throw new IllegalStateException(refusal);
            }
            Integer loadedVersion = PACK_VERSIONS.get(pack.id);
            if (loadedVersion == null) {
                String why = takeLoadError();
                throw new IllegalStateException(why != null
                        ? "The pack APK failed to load: " + why
                        : "The pack APK failed to load (entry class "
                                + pack.entryClass + " not found).");
            }
            if (loadedVersion != pack.version) {
                throw new IllegalStateException("The pack APK is version "
                        + loadedVersion + " but the catalog expects " + pack.version
                        + ". The hosted APK is outdated — upload the latest build to the pack URL.");
            }
            List<String> missing = new ArrayList<>();
            for (PackDescriptor.ToolMeta tool : pack.tools) {
                if (PluginRegistry.findCustom(tool.id) == null) missing.add(tool.id);
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("The pack build is missing tools: "
                        + missing + ". The hosted APK does not match the catalog.");
            }
            backup.delete();
            try {
                downloaded.delete();
            } catch (Exception ignored) {
            }
            return null;
        } catch (Exception e) {
            try {
                dest.delete();
            } catch (Exception ignored) {
            }
            if (hadPrevious && backup.exists()) {
                try {
                    copy(backup, dest);
                    dest.setWritable(false);
                    prefs(context).edit()
                            .putInt(KEY_PREFIX + pack.id + "_version", previousVersion)
                            .putString(KEY_PREFIX + pack.id + "_entry", previousEntry)
                            .apply();
                    unloadPack(pack.id);
                    loadPack(context, dest, previousEntry, pack.id);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    prefs(context).edit()
                            .remove(KEY_PREFIX + pack.id + "_version")
                            .remove(KEY_PREFIX + pack.id + "_entry")
                            .apply();
                } catch (Exception ignored) {
                }
                unloadPack(pack.id);
            }
            try {
                backup.delete();
            } catch (Exception ignored) {
            }
            return e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }

    /**
     * Sideload path: install a user-picked APK file for a catalog pack.
     * Release builds require a catalog checksum like downloads; debuggable
     * builds may sideload local test builds without one.
     */
    public static String installFromFile(Context context, PackDescriptor pack, File picked) {
        boolean requireChecksum = !PackSignatures.isDebuggable(context);
        return installDownloadedPack(context, pack, picked, requireChecksum);
    }

    public static synchronized void uninstallPack(Context context, String packId) {
        unloadPack(packId);
        try {
            packFile(context, packId).delete();
        } catch (Exception ignored) {
        }
        try {
            prefs(context).edit()
                    .remove(KEY_PREFIX + packId + "_version")
                    .remove(KEY_PREFIX + packId + "_entry")
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized void unloadPack(String packId) {
        PackRes.unregister(packId);
        PACK_VERSIONS.remove(packId);
        List<String> ids = LOADED.remove(packId);
        if (ids != null) {
            for (String id : ids) {
                try {
                    PluginRegistry.unregister(id);
                } catch (Exception ignored) {
                }
            }
        }
        List<AppExtension> extensions = LOADED_EXT.remove(packId);
        if (extensions != null) {
            for (AppExtension ext : extensions) {
                try {
                    ExtensionRegistry.unregister(ext);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static PackDescriptor packForTool(List<PackDescriptor> catalog, String toolId) {
        return PackCatalog.packForTool(catalog, toolId);
    }

    // ---------- helpers ----------

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String sha256(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) digest.update(buf, 0, n);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void copy(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}
