package io.github.abdurazaaqmohammed.mcp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Notification "Stop" action: stops the MCP service. */
public class McpStopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.stopService(new Intent(context, McpServerService.class));
    }
}
