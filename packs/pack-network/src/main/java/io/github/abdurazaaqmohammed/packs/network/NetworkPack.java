package io.github.abdurazaaqmohammed.packs.network;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Downloadable network tools pack.
 */
public class NetworkPack implements ToolPack {

    @Override
    public String packId() {
        return "network";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new ConnectivityTool(),
                new NfcTool(),
                new BluetoothTool(),
                new QrGenTool(),
                new QrScanTool());
    }
}
