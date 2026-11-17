package io.github.abdurazaaqmohammed.feature.system;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.privilege.impl.RootManager;

/** Built-in tool metadata shared by the hub, sidebar and launcher shortcuts. */
public final class ToolRegistry {
    public record ToolItem(String id, String title, String subtitle, int iconRes, String category) {}

    private record Definition(String id, int title, int subtitle, int icon, int category,
                              String sidebarSection, boolean rootOnly) {
        ToolItem localize(Context context) {
            return new ToolItem(id, context.getString(title), context.getString(subtitle), icon,
                    context.getString(category));
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("screen_ocr", R.string.screen_ocr_title, R.string.screen_ocr_subtitle,
                    R.drawable.screen_ocr_24px, R.string.cat_text, "tools", false),
            new Definition("extract", R.string.sidebar_extract, R.string.tool_extract_subtitle,
                    R.drawable.apk_document_24px, R.string.cat_storage, "tools", false),
            new Definition("lspatch_manager", R.string.lspatch_manager, R.string.lspatch_manager_subtitle,
                    R.drawable.tools_24px, R.string.cat_storage, null, false),
            new Definition("documents", R.string.docs_tool_title, R.string.docs_tool_subtitle,
                    R.drawable.folder_24px, R.string.cat_storage, "tools", false),
            new Definition("recycle_bin", R.string.recycle_bin, R.string.tool_recycle_subtitle,
                    R.drawable.baseline_delete_24, R.string.cat_storage, "tools", false),
            new Definition("storagemanager", R.string.storage_manager, R.string.storage_subtitle,
                    R.drawable.archive_24px, R.string.cat_storage, null, false),
            new Definition("ftp_server", R.string.ftp_server, R.string.tool_ftp_subtitle,
                    R.drawable.cloud_upload_24px, R.string.cat_network, "tools", false),
            new Definition("network_client", R.string.network_connections, R.string.tool_network_subtitle,
                    R.drawable.cloud_download_24px, R.string.cat_network, "tools", false),
            new Definition("wifi", R.string.wifi_manager, R.string.wifi_subtitle,
                    R.drawable.wifi_24px, R.string.cat_network, "plugins", false),
            new Definition("adb", R.string.adb_browser, R.string.adb_browser_subtitle,
                    R.drawable.terminal_24px, R.string.cat_network, null, false),
            new Definition("mcp_server", R.string.mcp_server, R.string.mcp_subtitle,
                    R.drawable.code_24px, R.string.cat_network, "plugins", false),
            new Definition("partition_dumper", R.string.tool_partition_dumper, R.string.tool_partition_dumper_subtitle,
                    R.drawable.ic_disk_drive_24px, R.string.cat_device, "tools", false),
            new Definition("app_ops", R.string.app_ops, R.string.app_ops_subtitle,
                    R.drawable.ic_shield_24, R.string.cat_device, "tools", false),
            new Definition("set_edit", R.string.set_edit, R.string.set_edit_subtitle,
                    R.drawable.baseline_settings_24, R.string.cat_device, "tools", false),
            new Definition("command_shell", R.string.command_shell, R.string.command_shell_subtitle,
                    R.drawable.terminal_24px, R.string.cat_device, "plugins", false),
            new Definition("logcat", R.string.sidebar_logcat, R.string.logcat_subtitle,
                    R.drawable.terminal_24px, R.string.cat_device, "plugins", false),
            new Definition("layout", R.string.sidebar_layout_inspector, R.string.tool_layout_subtitle,
                    R.drawable.ic_inspect, R.string.cat_device, "plugins", false),
            new Definition("frida_console", R.string.frida_console, R.string.frida_console_subtitle,
                    R.drawable.terminal_24px, R.string.cat_device, "plugins", true),
            new Definition("jiagu_dumper", R.string.jiagu_dumper_title, R.string.jiagu_dumper_subtitle,
                    R.drawable.terminal_24px, R.string.cat_device, null, true),
            new Definition("color_picker", R.string.color_picker, R.string.tool_color_subtitle,
                    R.drawable.colorize_24px, R.string.cat_media, "plugins", false),
            new Definition("free_style", R.string.free_style, R.string.tool_freestyle_subtitle,
                    R.drawable.image_24px, R.string.cat_media, "plugins", false));

    private ToolRegistry() {}

    public static List<ToolItem> getTools(Context context) {
        List<ToolItem> tools = new ArrayList<>();
        for (Definition definition : DEFINITIONS) tools.add(definition.localize(context));
        return tools;
    }

    public static List<ToolItem> visibleTools(Context context) {
        boolean root = RootManager.getInstance(context).isRootAvailable();
        List<ToolItem> tools = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            if (!definition.rootOnly || root) tools.add(definition.localize(context));
        }
        return tools;
    }

    /** Stable IDs keep existing custom sidebar ordering and hidden-item preferences intact. */
    public static List<String> sidebarIds(String section) {
        List<String> ids = new ArrayList<>();
        if ("tools".equals(section)) ids.add("tools");
        for (Definition definition : DEFINITIONS) {
            if (section.equals(definition.sidebarSection)) ids.add(definition.id);
        }
        return ids;
    }

    public static boolean isInSidebarSection(String id, String section) {
        if ("tools".equals(id)) return "tools".equals(section);
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(id)) return section.equals(definition.sidebarSection);
        }
        return false;
    }

    public static boolean isAvailable(Context context, String id) {
        String canonical = canonicalId(id);
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(canonical)) {
                return !definition.rootOnly || RootManager.getInstance(context).isRootAvailable();
            }
        }
        return false;
    }

    public static ToolItem findById(Context context, String id) {
        String canonical = canonicalId(id);
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(canonical)) return definition.localize(context);
        }
        return null;
    }

    /** Aliases used by older hub entries and saved network-client sidebar rows. */
    public static String canonicalId(String id) {
        if (id == null) return "";
        return switch (id) {
            case "wifimanager" -> "wifi";
            case "mcpserver" -> "mcp_server";
            case "frida" -> "frida_console";
            case "partitiondumper" -> "partition_dumper";
            case "commandshell" -> "command_shell";
            case "setedit" -> "set_edit";
            case "jiagudumper" -> "jiagu_dumper";
            case "ftp_client", "webdav_client", "smb_client" -> "network_client";
            default -> id;
        };
    }

    public static String[] categoriesInOrder(Context context) {
        return new String[]{context.getString(R.string.cat_text), context.getString(R.string.cat_storage),
                context.getString(R.string.cat_network), context.getString(R.string.cat_device),
                context.getString(R.string.cat_media), context.getString(R.string.cat_math),
                context.getString(R.string.cat_time), context.getString(R.string.cat_rand)};
    }

    public static String localizeCategory(Context context, String category) {
        int resource = switch (category) {
            case "Text & Security" -> R.string.cat_text;
            case "Storage & Apps" -> R.string.cat_storage;
            case "Wi-Fi & Network" -> R.string.cat_network;
            case "Device & Hardware" -> R.string.cat_device;
            case "Media & Sound" -> R.string.cat_media;
            case "Math & Finance" -> R.string.cat_math;
            case "Time & Productivity", "General" -> R.string.cat_time;
            case "Random" -> R.string.cat_rand;
            default -> 0;
        };
        return resource == 0 ? category : context.getString(resource);
    }
}
