package io.github.abdurazaaqmohammed.feature.system;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.apk.APKExtractorActivity;
import io.github.abdurazaaqmohammed.feature.apk.JiaguDumpDialog;
import io.github.abdurazaaqmohammed.feature.dumper.PartitionDumperDialog;
import io.github.abdurazaaqmohammed.feature.freestyle.FreeStylePickerActivity;
import io.github.abdurazaaqmohammed.feature.system.appops.AppOpsActivity;
import io.github.abdurazaaqmohammed.feature.system.ocr.ScreenOcrActivity;
import io.github.abdurazaaqmohammed.feature.system.shell.CommandShellActivity;
import io.github.abdurazaaqmohammed.mcp.McpServerActivity;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/** One launch path for built-in tools, regardless of where the user opens them. */
public final class ToolLauncher {
    public static final String EXTRA_MAIN_TOOL = "mp_main_tool";
    public static final String EXTRA_HUB_TOOL = "mp_hub_tool";

    private ToolLauncher() {}

    public static boolean open(Activity activity, String toolId) {
        String id = ToolRegistry.canonicalId(toolId);
        if ("tools".equals(id)) {
            activity.startActivity(new Intent(activity, ToolsHubActivity.class));
            return true;
        }
        if (ToolRegistry.findById(activity, id) == null) return false;
        if (!ToolRegistry.isAvailable(activity, id)) {
            Toast.makeText(activity, R.string.tool_requires_root, Toast.LENGTH_LONG).show();
            return true;
        }
        if (activity instanceof AppCompatActivity host) {
            if ("partition_dumper".equals(id)) {
                PartitionDumperDialog.show(host);
                return true;
            }
            if ("jiagu_dumper".equals(id)) {
                JiaguDumpDialog.show(host);
                return true;
            }
        }
        if (activity instanceof MainActivity main && main.openBuiltInSidebarTool(id)) return true;
        Intent intent = intentFor(activity, id);
        if (intent != null) {
            if ("extract".equals(id)) {
                activity.startActivityForResult(intent, 11);
            } else {
                activity.startActivity(intent);
            }
        }
        return true;
    }

    /** Explicit, allowlisted destinations; also used by the exported shortcut entry point. */
    public static Intent intentFor(Context context, String toolId) {
        String id = ToolRegistry.canonicalId(toolId);
        Class<?> destination = switch (id) {
            case "screen_ocr" -> ScreenOcrActivity.class;
            case "extract" -> APKExtractorActivity.class;
            case "documents" -> DocumentsBrowserActivity.class;
            case "storagemanager" -> StorageManagerActivity.class;
            case "wifi" -> WifiManagerActivity.class;
            case "adb" -> AdbBrowserActivity.class;
            case "mcp_server" -> McpServerActivity.class;
            case "logcat" -> LogcatActivity.class;
            case "frida_console" -> FridaConsoleActivity.class;
            case "command_shell" -> CommandShellActivity.class;
            case "app_ops" -> AppOpsActivity.class;
            case "set_edit" -> SetEditActivity.class;
            case "free_style" -> FreeStylePickerActivity.class;
            case "tools", "partition_dumper", "jiagu_dumper" -> ToolsHubActivity.class;
            case "recycle_bin", "ftp_server", "network_client", "layout", "color_picker" -> MainActivity.class;
            default -> null;
        };
        if (destination == null) return null;
        Intent intent = new Intent(context, destination);
        if (destination == MainActivity.class) {
            intent.putExtra(EXTRA_MAIN_TOOL, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else if (destination == ToolsHubActivity.class && !"tools".equals(id)) {
            intent.putExtra(EXTRA_HUB_TOOL, id);
        } else if (destination == LogcatActivity.class || destination == FridaConsoleActivity.class) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }
}
