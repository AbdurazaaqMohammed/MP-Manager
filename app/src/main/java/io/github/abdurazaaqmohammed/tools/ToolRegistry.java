package io.github.abdurazaaqmohammed.tools;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ToolRegistry {

    public record ToolItem(String id, String title, String subtitle, int iconRes, String category) {
    }

    public static List<ToolItem> getTools(Context context) {
        List<ToolItem> tools = new ArrayList<>();
        int pkg = 0;
        try {
            pkg = context.getResources().getIdentifier("hex_keyboard_24px", "drawable", context.getPackageName());
        } catch (Exception ignored) {
        }
        tools.add(new ToolItem("wifimanager", context.getString(R.string.wifi_manager),
                context.getString(R.string.wifi_subtitle),
                resId(context, "wifi_24px", pkg), context.getString(R.string.cat_network)));
        tools.add(new ToolItem("storagemanager", context.getString(R.string.storage_manager),
                context.getString(R.string.storage_subtitle),
                resId(context, "archive_24px", pkg), context.getString(R.string.cat_storage)));
        tools.add(new ToolItem("adb", context.getString(R.string.adb_browser),
                context.getString(R.string.adb_browser_subtitle),
                resId(context, "archive_24px", pkg), context.getString(R.string.cat_storage)));
        tools.add(new ToolItem("mcpserver", context.getString(R.string.mcp_server),
                context.getString(R.string.mcp_subtitle),
                resId(context, "code_24px", pkg), context.getString(R.string.cat_network)));
        tools.add(new ToolItem("logcat", context.getString(R.string.sidebar_logcat),
                context.getString(R.string.logcat_subtitle),
                resId(context, "terminal_24px", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("frida", context.getString(R.string.frida_console),
                context.getString(R.string.frida_console_subtitle),
                resId(context, "terminal_24px", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("documents", context.getString(R.string.docs_tool_title),
                context.getString(R.string.docs_tool_subtitle),
                resId(context, "folder_24px", pkg), context.getString(R.string.cat_storage)));
        tools.add(new ToolItem("partitiondumper", context.getString(R.string.tool_partition_dumper),
                context.getString(R.string.tool_partition_dumper_subtitle),
                resId(context, "ic_disk_drive_24px", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("app_ops", context.getString(R.string.app_ops),
                context.getString(R.string.app_ops_subtitle),
                resId(context, "ic_shield_24", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("commandshell", context.getString(R.string.command_shell),
                context.getString(R.string.command_shell_subtitle),
                resId(context, "terminal_24px", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("setedit", context.getString(R.string.set_edit),
                context.getString(R.string.set_edit_subtitle),
                resId(context, "baseline_settings_24", pkg), context.getString(R.string.cat_device)));
        tools.add(new ToolItem("jiagudumper", context.getString(R.string.jiagu_dumper_title),
                context.getString(R.string.jiagu_dumper_subtitle),
                resId(context, "terminal_24px", pkg), context.getString(R.string.cat_device)));
        return tools;
    }

    public static String[] categoriesInOrder(Context context) {
        return new String[]{
                context.getString(R.string.cat_network),
                context.getString(R.string.cat_storage),
                context.getString(R.string.cat_device),
                context.getString(R.string.cat_math),
                context.getString(R.string.cat_time),
                context.getString(R.string.cat_text),
                context.getString(R.string.cat_media),
                context.getString(R.string.cat_rand)};
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
            if (id.equals(item.id())) {
                return item;
            }
        }
        return null;
    }
}
