/*
 * Copyright (c) 2026 Ratul Hasan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */
package io.github.codehasan.colorpicker.services;

import android.annotation.SuppressLint;
import android.os.Build;
import android.service.quicksettings.Tile;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.ServiceState;

public class ColorPickerTileService extends android.service.quicksettings.TileService {

    private final ServiceState.Observer stateObserver = this::updateTile;

    @Override
    public void onStartListening() {
        super.onStartListening();
        ServiceState.getInstance().addObserver(stateObserver);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        ServiceState.getInstance().removeObserver(stateObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ServiceState.getInstance().removeObserver(stateObserver);
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onClick() {
        super.onClick();
        boolean isRunning = ServiceState.getInstance().isRunning();

        if (isRunning) {
            ServiceState.getInstance().stopColorPickerService(this);
        } else {
           /* Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("color_pick_from_tile", true);

            PendingIntentActivityWrapper wrapper = new PendingIntentActivityWrapper(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                    false
            );
            TileServiceCompat.startActivityAndCollapse(this, wrapper);*/
        }
    }

    private void updateTile(boolean isRunning) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.setStateDescription(
                    isRunning ? getString(R.string.running) : getString(R.string.stopped)
            );
        }
        tile.updateTile();
    }
}
