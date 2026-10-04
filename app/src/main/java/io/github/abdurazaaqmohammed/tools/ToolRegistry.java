package io.github.abdurazaaqmohammed.tools;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ToolRegistry {
    public static final String CAT_NETWORK = "Wi-Fi & Network";
    public static final String CAT_STORAGE = "Storage & Apps";
    public static final String CAT_DEVICE = "Device & Hardware";
    public static final String CAT_MATH = "Math & Finance";
    public static final String CAT_TIME = "Time & Productivity";
    public static final String CAT_TEXT = "Text & Security";
    public static final String CAT_MEDIA = "Media & Sound";
    public static final String CAT_RAND = "Random";

    public record ToolItem(String id, String title, String subtitle, int iconRes, String category) {
            public ToolItem(String id, String title, String subtitle, int iconRes, String category) {
                this.id = id;
                this.title = title;
                this.subtitle = subtitle;
                this.iconRes = iconRes;
                this.category = category == null ? CAT_DEVICE : category;
            }
        }
    public static List<ToolItem> getTools(Context context) {
        List<ToolItem> tools = new ArrayList<>();
        int pkg = 0;
        try {
            pkg = context.getResources().getIdentifier("hex_keyboard_24px", "drawable", context.getPackageName());
        } catch (Exception ignored) {
        }
        tools.add(new ToolItem("wifimanager", "Wi-Fi Manager", "DNS profiles, passwords, usage", resId(context, "wifi_24px", pkg), CAT_NETWORK));
        tools.add(new ToolItem("storagemanager", "Storage Manager", "Largest files, clear cache", resId(context, "archive_24px", pkg), CAT_STORAGE));
        return tools;
    }
    public static String[] categoriesInOrder() {
        return new String[]{CAT_NETWORK, CAT_STORAGE, CAT_DEVICE, CAT_MATH, CAT_TIME, CAT_TEXT, CAT_MEDIA, CAT_RAND};
    }
    private static int resId(Context context, String name, int fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id != 0) {
                return id;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
    public static ToolItem findById(Context context, String id) {
        if (id == null) {
            return null;
        }
        for (ToolItem item : getTools(context)) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }
}
