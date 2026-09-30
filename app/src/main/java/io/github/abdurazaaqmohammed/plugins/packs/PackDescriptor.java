package io.github.abdurazaaqmohammed.plugins.packs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One downloadable tool pack, parsed from the pack catalog.
 */
public class PackDescriptor {

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
        return new PackDescriptor(
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
    }

    public boolean hasChecksum() {
        return sha256 != null && !sha256.trim().isEmpty();
    }

    public ToolMeta tool(String toolId) {
        for (ToolMeta t : tools) {
            if (t.id.equals(toolId)) return t;
        }
        return null;
    }
}
