package io.github.abdurazaaqmohammed.plugins.packs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import android.os.Build;
import android.os.Process;

/**
 * One downloadable tool pack, parsed from the pack catalog.
 */
public class PackDescriptor {

    public record Download(String url, String sha256) {}
    private final Map<String, Download> abiDownloads = new LinkedHashMap<>();
    private int minSdk = 19;

    public static class ToolMeta {
        public final String id;
        public final String title;
        public final String subtitle;

        ToolMeta(String id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public final String id;
    public final String title;
    public final String description;
    public final String category;
    public final int version;
    public final String versionName;
    public final String apkUrl;
    public final String sha256;
    public final String entryClass;
    public final List<ToolMeta> tools;

    PackDescriptor(String id, String title, String description, String category,
            int version, String versionName, String apkUrl, String sha256,
            String entryClass, List<ToolMeta> tools) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.version = version;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.entryClass = entryClass;
        this.tools = tools;
    }

    static PackDescriptor parse(JSONObject o) throws Exception {
        List<ToolMeta> tools = new ArrayList<>();
        JSONArray arr = o.optJSONArray("tools");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                tools.add(new ToolMeta(
                        t.optString("id", ""),
                        t.optString("title", ""),
                        t.optString("subtitle", "")));
            }
        }
        PackDescriptor pack = new PackDescriptor(
                o.optString("id", ""),
                o.optString("title", ""),
                o.optString("description", ""),
                o.optString("category", ""),
                o.optInt("version", 0),
                o.optString("versionName", ""),
                o.optString("apkUrl", ""),
                o.optString("sha256", ""),
                o.optString("entryClass", ""),
                tools);
        JSONObject variants = o.optJSONObject("abiDownloads");
        pack.minSdk = o.optInt("minSdk", 19);
        if (variants != null) {
            for (String abi : new String[]{"arm64-v8a", "armeabi-v7a", "x86_64", "x86"}) {
                JSONObject variant = variants.optJSONObject(abi);
                if (variant != null) pack.abiDownloads.put(abi, new Download(
                        variant.optString("apkUrl", ""), variant.optString("sha256", "")));
            }
        }
        return pack;
    }

    public boolean hasChecksum() {
        return sha256 != null && sha256.trim().matches("[a-fA-F0-9]{64}");
    }

    public boolean supportsDevice() { return Build.VERSION.SDK_INT >= minSdk; }

    public int minSdk() { return minSdk; }

    /** Native packs download only the ABI the host process can actually load. */
    public Download download() {
        if (!abiDownloads.isEmpty()) {
            boolean is64 = Process.is64Bit();
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi.contains("64") != is64) continue;
                Download variant = abiDownloads.get(abi);
                if (variant != null && !variant.url().isEmpty() && variant.sha256().matches("[a-fA-F0-9]{64}")) {
                    return variant;
                }
            }
        }
        return new Download(apkUrl, sha256);
    }

    public ToolMeta tool(String toolId) {
        for (ToolMeta t : tools) {
            if (t.id.equals(toolId)) return t;
        }
        return null;
    }
}
