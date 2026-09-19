package io.github.abdurazaaqmohammed.tools;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import io.github.abdurazaaqmohammed.utils.DnsManager;

public class PrivateDnsTileService extends TileService {
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    public void onTileAdded() {
        super.onTileAdded();
        refresh();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        super.onClick();
        try {
            if (isLocked()) {
                unlockAndRun(this::cycle);
            } else {
                cycle();
            }
        } catch (Exception e) {
            cycle();
        }
    }

    private void cycle() {
        new Thread(() -> {
            DnsManager.DnsProfile applied;
            try {
                applied = DnsManager.applyNextProfile(this);
            } catch (Exception e) {
                applied = null;
            }
            final DnsManager.DnsProfile result = applied;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (result != null) {
                    Toast.makeText(this, "DNS " + result.name, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "DNS switch failed", Toast.LENGTH_SHORT).show();
                }
                refresh();
            });
        }).start();
    }

    private void refresh() {
        try {
            Tile tile = getQsTile();
            if (tile == null) return;
            DnsManager.DnsProfile match = null;
            try {
                match = DnsManager.matchingProfile(this);
            } catch (Exception ignored) {
            }
            if (match == null && Build.VERSION.SDK_INT >= 24) {
                String active = DnsManager.activeId(this);
                match = DnsManager.findProfile(this, active);
            }
            if (match != null) {
                tile.setLabel("DNS " + match.name);
                tile.setContentDescription("Private DNS " + match.name);
                tile.setState("off".equals(match.mode) ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
            } else {
                tile.setLabel("Private DNS");
                tile.setState(Tile.STATE_INACTIVE);
            }
            tile.updateTile();
        } catch (Exception ignored) {
        }
    }

    public static void requestUpdate(android.content.Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                TileService.requestListeningState(context, new ComponentName(context, PrivateDnsTileService.class));
            }
        } catch (Exception ignored) {
        }
    }
}
