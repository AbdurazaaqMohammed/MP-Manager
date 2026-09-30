package io.github.abdurazaaqmohammed.packs.network;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildConnectivityHub().
 *
 * <p>The built-in buttons rebuilt the Bluetooth/NFC views in place; packs
 * cannot do that, so they open the matching tool screens instead.
 */
public class ConnectivityTool extends BaseToolPlugin {

    public ConnectivityTool() {
        super("connectivity", "Connectivity Hub", "Network, Bluetooth, NFC info", ToolCategories.NETWORK);
    }

    private static String ipToString(int ip) {
        return (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return new DecimalFormat("0.0").format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return new DecimalFormat("0.0").format(mb) + " MB";
        }
        double gb = mb / 1024.0;
        if (gb < 1024) {
            return new DecimalFormat("0.00").format(gb) + " GB";
        }
        return new DecimalFormat("0.00").format(gb / 1024.0) + " TB";
    }

    private static String readNetworkSummary(Context context) {
        StringBuilder b = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    NetworkInfo active = cm.getActiveNetworkInfo();
                    if (active != null) b.append("Active: ").append(active.getTypeName()).append(" connected=").append(active.isConnected()).append("\n");
                    else b.append("Active: none\n");
                } catch (Exception e) {
                    b.append("Active: unknown\n");
                }
            }
            try {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.isWifiEnabled()) {
                    WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        String ssid = info.getSSID() == null ? "-" : info.getSSID().replace("\"", "");
                        b.append("Wi-Fi ").append(ssid).append("  ").append(info.getRssi()).append(" dBm  ").append(info.getLinkSpeed()).append(" Mbps\n");
                        b.append("IP ").append(ipToString(info.getIpAddress()));
                    }
                } else {
                    b.append("Wi-Fi off or unavailable");
                }
            } catch (Exception e) {
                b.append("Wi-Fi: unavailable");
            }
        } catch (Exception e) {
            return "Unavailable";
        }
        return b.toString();
    }

    private static String readDataUsageSummary() {
        try {
            long mRx = TrafficStats.getMobileRxBytes();
            long mTx = TrafficStats.getMobileTxBytes();
            long tRx = TrafficStats.getTotalRxBytes();
            long tTx = TrafficStats.getTotalTxBytes();
            return "Mobile ↓ " + (mRx < 0 ? "-" : formatBytes(mRx)) + "  ↑ " + (mTx < 0 ? "-" : formatBytes(mTx)) + "\n"
                    + "Total ↓ " + (tRx < 0 ? "-" : formatBytes(tRx)) + "  ↑ " + (tTx < 0 ? "-" : formatBytes(tTx)) + "\n"
                    + "Counters reset on reboot";
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    private static void openTool(Context context, String toolId, String title) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("io.github.abdurazaaqmohammed.MPManager",
                    "io.github.abdurazaaqmohammed.tools.ToolRunnerActivity"));
            i.putExtra("tool_id", toolId);
            i.putExtra("tool_title", title);
            context.startActivity(i);
        } catch (Exception e) {
            ToolViewFactory.toast(context, "Cannot open " + title);
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Connectivity Hub");
        LinearLayout net = ToolViewFactory.container(context);
        box.addView(net);
        TextView netTitle = new TextView(context);
        netTitle.setText("Network");
        netTitle.setTextSize(16);
        netTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        net.addView(netTitle);
        TextView netText = new TextView(context);
        netText.setTextSize(14);
        netText.setTypeface(android.graphics.Typeface.MONOSPACE);
        net.addView(netText);
        LinearLayout data = ToolViewFactory.container(context);
        box.addView(data);
        TextView dataTitle = new TextView(context);
        dataTitle.setText("Data usage");
        dataTitle.setTextSize(16);
        dataTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        data.addView(dataTitle);
        TextView dataText = new TextView(context);
        dataText.setTextSize(14);
        dataText.setTypeface(android.graphics.Typeface.MONOSPACE);
        data.addView(dataText);
        final Runnable refresh = () -> {
            netText.setText(readNetworkSummary(context));
            dataText.setText(readDataUsageSummary());
        };
        refresh.run();
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton r = ToolViewFactory.makeRowButton(row, "Refresh", 1f);
        MaterialButton c = ToolViewFactory.makeRowButton(row, "Copy", 1f);
        r.setOnClickListener(v -> refresh.run());
        c.setOnClickListener(v -> ToolViewFactory.copyText(context, "connectivity",
                netText.getText() + "\n\n" + dataText.getText()));
        ToolViewFactory.addLabel(box, "Short-range radios");
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton btBtn = ToolViewFactory.makeRowButton(row2, "Bluetooth pairs", 1f);
        MaterialButton nfcBtn = ToolViewFactory.makeRowButton(row2, "NFC reader", 1f);
        btBtn.setOnClickListener(v -> openTool(context, "bluetooth", "Bluetooth Pairs"));
        nfcBtn.setOnClickListener(v -> openTool(context, "nfc", "NFC Reader"));
        return box;
    }
}
