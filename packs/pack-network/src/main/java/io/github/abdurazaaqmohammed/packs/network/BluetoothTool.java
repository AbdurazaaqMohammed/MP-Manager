package io.github.abdurazaaqmohammed.packs.network;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extraction of ToolRunnerActivity.buildBluetooth().
 */
public class BluetoothTool extends BaseToolPlugin {

    private TextView btText;
    private ArrayAdapter<String> btAdapter;
    private final List<String> btNames = new ArrayList<>();

    public BluetoothTool() {
        super("bluetooth", R.string.bluetooth_title, R.string.bluetooth_sub,  ToolCategories.NETWORK);
    }

    private void refreshBtList(Context context) {
        if (btAdapter == null) {
            return;
        }
        btNames.clear();
        try {
            if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                try {
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 9004);
                } catch (Exception ignored) {
                }
                if (btText != null) {
                    btText.setText(btText.getContext().getString(R.string.bluetooth_bluetooth_permission_needed_th));
                }
                return;
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                if (btText != null) {
                    btText.setText(btText.getContext().getString(R.string.bluetooth_no_bluetooth_hardware));
                }
            } else if (!adapter.isEnabled()) {
                if (btText != null) {
                    btText.setText(btText.getContext().getString(R.string.bluetooth_bluetooth_is_off_turn_it_on_an));
                }
            } else {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded == null || bonded.isEmpty()) {
                    if (btText != null) {
                        btText.setText(btText.getContext().getString(R.string.bluetooth_no_paired_devices));
                    }
                } else {
                    if (btText != null) {
                        btText.setText(context.getString(R.string.bluetooth_paired_count, bonded.size()));
                    }
                    for (BluetoothDevice d : bonded) {
                        String name = d.getName();
                        btNames.add((name == null ? context.getString(R.string.bluetooth_unknown) : name) + "\n" + d.getAddress());
                    }
                }
            }
        } catch (Exception e) {
            if (btText != null) {
                btText.setText(btText.getContext().getString(R.string.bluetooth_bluetooth_unavailable));
            }
        }
        btAdapter.notifyDataSetChanged();
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.bluetooth_paired_bluetooth));
        btText = ToolViewFactory.makeOutput(box);
        ListView listView = new ListView(context);
        btNames.clear();
        btAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, btNames);
        listView.setAdapter(btAdapter);
        box.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 240)));
        MaterialButton refreshBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.bluetooth_refresh));
        refreshBtn.setOnClickListener(v -> refreshBtList(context));
        MaterialButton openBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.bluetooth_open_bluetooth_settings));
        openBtn.setOnClickListener(v -> {
            try {
                context.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.bluetooth_cannot_open_settings));
            }
        });
        refreshBtList(context);
        return box;
    }

    @Override
    public void onDestroy() {
        btText = null;
        btAdapter = null;
        btNames.clear();
    }
}
