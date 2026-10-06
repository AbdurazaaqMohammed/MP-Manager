package io.github.abdurazaaqmohammed.mcp;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.utils.DexDecryptInjector;
import io.github.abdurazaaqmohammed.utils.DexStringDecryptor;

/**
 * The tool surface of the embedded MCP server: definitions for {@code tools/list} plus the
 * implementations behind {@code tools/call}. Everything here delegates to utilities the app
 * already ships, so an AI client and the UI run the exact same code.
 */
final class ApkMcpTools {

    private static final String[] NAMES = {"apk_info", "apk_entries", "decrypt_dex_strings"};

    private ApkMcpTools() {
    }

    static boolean known(String name) {
        for (String n : NAMES) if (n.equals(name)) return true;
        return false;
    }

    static JSONArray list() throws Exception {
        JSONArray tools = new JSONArray();

        JSONObject info = new JSONObject();
        info.put("name", "apk_info");
        info.put("description", "Read basic information about an APK file: package name, "
                + "version, requested permissions, declared components, dex entries and size.");
        info.put("inputSchema", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Absolute path to the APK file")))
                .put("required", new JSONArray().put("path")));
        tools.put(info);

        JSONObject entries = new JSONObject();
        entries.put("name", "apk_entries");
        entries.put("description", "List entries inside an APK/zip archive with their sizes. "
                + "filter is an optional case-insensitive substring match on entry names; limit "
                + "caps the number of returned entries (default 200, max 2000).");
        entries.put("inputSchema", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Absolute path to the APK file"))
                        .put("filter", new JSONObject()
                                .put("type", "string")
                                .put("description", "Substring filter on entry names"))
                        .put("limit", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Maximum entries to return (default 200)")))
                .put("required", new JSONArray().put("path")));
        tools.put(entries);

        JSONObject decrypt = new JSONObject();
        decrypt.put("name", "decrypt_dex_strings");
        decrypt.put("description", "Statically decrypt obfuscated strings in every dex entry of "
                + "an APK and write a rebuilt copy next to it (name_ds.apk); the original file is "
                + "left untouched. Pass signatures (glob like com.x.y.*) to target specific "
                + "decrypt methods, or leave advanced=true (default) to try every "
                + "String-returning static call.");
        decrypt.put("inputSchema", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Absolute path to the APK file"))
                        .put("signatures", new JSONObject()
                                .put("type", "string")
                                .put("description", "Comma-separated method signatures/globs, "
                                        + "e.g. com.x.y.Helper.a*"))
                        .put("advanced", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "Try every String-returning static call "
                                        + "(default true)")))
                .put("required", new JSONArray().put("path")));
        tools.put(decrypt);

        return tools;
    }

    static String call(Context ctx, String name, JSONObject args) throws Exception {
        switch (name) {
            case "apk_info":
                return apkInfo(ctx, args);
            case "apk_entries":
                return apkEntries(args);
            case "decrypt_dex_strings":
                return decryptDexStrings(ctx, args);
            default:
                throw new IOException("Unknown tool: " + name);
        }
    }

    // ------------------------------------------------------------------ tools

    private static String apkInfo(Context ctx, JSONObject args) throws Exception {
        File apk = requireFile(args);
        JSONObject out = new JSONObject();
        out.put("path", apk.getAbsolutePath());
        out.put("size", apk.length());

        try (ZipFile zip = new ZipFile(apk)) {
            JSONArray dex = new JSONArray();
            int total = 0;
            Enumeration<? extends ZipEntry> it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                total++;
                String n = e.getName();
                if (n.matches("classes\\d*\\.dex")) dex.put(n);
            }
            out.put("entries", total);
            out.put("dex", dex);
        } catch (Exception e) {
            out.put("zip_error", String.valueOf(e.getMessage()));
        }

        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS);
            if (pi != null) {
                out.put("package", pi.packageName);
                out.put("versionName", pi.versionName == null ? "" : pi.versionName);
                out.put("versionCode", Build.VERSION.SDK_INT >= 28
                        ? pi.getLongVersionCode() : pi.versionCode);
                if (pi.requestedPermissions != null) {
                    JSONArray perms = new JSONArray();
                    for (String p : pi.requestedPermissions) perms.put(p);
                    out.put("permissions", perms);
                }
                JSONObject components = new JSONObject();
                components.put("activities", names(pi.activities));
                components.put("services", names(pi.services));
                components.put("receivers", names(pi.receivers));
                components.put("providers", names(pi.providers));
                out.put("components", components);
            } else {
                out.put("note", "manifest could not be parsed");
            }
        } catch (Exception e) {
            out.put("manifest_error", String.valueOf(e.getMessage()));
        }
        return out.toString(2);
    }

    private static JSONArray names(Object[] components) throws Exception {
        JSONArray arr = new JSONArray();
        if (components == null) return arr;
        for (Object o : components) {
            if (o instanceof ComponentInfo) {
                arr.put(((ComponentInfo) o).name);
            }
        }
        return arr;
    }

    private static String apkEntries(JSONObject args) throws Exception {
        File apk = requireFile(args);
        String filter = args.optString("filter", "").trim().toLowerCase(Locale.ROOT);
        int limit = args.optInt("limit", 200);
        if (limit < 1) limit = 1;
        if (limit > 2000) limit = 2000;

        int matched = 0;
        JSONArray shown = new JSONArray();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                if (!filter.isEmpty() && !e.getName().toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                matched++;
                if (shown.length() < limit) {
                    shown.put(new JSONObject()
                            .put("name", e.getName())
                            .put("size", e.getSize())
                            .put("compressedSize", e.getCompressedSize()));
                }
            }
        }
        JSONObject out = new JSONObject();
        out.put("path", apk.getAbsolutePath());
        out.put("total", matched);
        out.put("shown", shown.length());
        out.put("entries", shown);
        return out.toString(2);
    }

    private static String decryptDexStrings(Context ctx, JSONObject args) throws Exception {
        File apk = requireFile(args);
        String signatures = args.optString("signatures", "").trim();
        boolean advanced = args.optBoolean("advanced", true);

        List<String> log = new ArrayList<>();
        DexStringDecryptor.Listener listener = log::add;
        com.reandroid.apk.APKLogger logger = new com.reandroid.apk.APKLogger() {
            @Override
            public void logMessage(String msg) {
                log.add(msg);
            }

            @Override
            public void logError(String msg, Throwable tr) {
                log.add("error: " + msg + (tr == null ? "" : (" - " + tr.getMessage())));
            }

            @Override
            public void logVerbose(String msg) {
            }
        };

        File out = DexDecryptInjector.inject(ctx, apk,
                new DexStringDecryptor.Options(signatures, advanced, listener), logger);

        JSONObject result = new JSONObject();
        result.put("output", out.getAbsolutePath());
        result.put("log", new JSONArray(log));
        return result.toString(2);
    }

    private static File requireFile(JSONObject args) throws IOException {
        String path = args.optString("path", "").trim();
        if (path.isEmpty()) throw new IOException("Missing required argument: path");
        File f = new File(path);
        if (!f.isFile()) throw new IOException("File not found: " + path);
        return f;
    }
}
