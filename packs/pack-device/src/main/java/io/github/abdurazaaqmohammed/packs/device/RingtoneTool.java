package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Extraction of ToolRunnerActivity.buildRingtone().
 */
public class RingtoneTool extends BaseToolPlugin {

    private Ringtone current;

    public RingtoneTool() {
        super("ringtone", R.string.ringtone_title, R.string.ringtone_sub,  ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.ringtone_ringtone_preview));
        String[] types = new String[]{context.getString(R.string.ringtone_ringtones), context.getString(R.string.ringtone_alarms), context.getString(R.string.ringtone_notifications)};
        int[] typeVals = new int[]{RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION};
        Spinner typeSpinner = new Spinner(context);
        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        box.addView(typeSpinner);
        ListView listView = new ListView(context);
        List<String> names = new ArrayList<>();
        List<Uri> uris = new ArrayList<>();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 280)));
        final Runnable load = () -> {
            names.clear();
            uris.clear();
            try {
                RingtoneManager manager = new RingtoneManager(context);
                manager.setType(typeVals[typeSpinner.getSelectedItemPosition()]);
                Cursor cursor = manager.getCursor();
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX));
                    uris.add(manager.getRingtoneUri(cursor.getPosition()));
                }
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
            adapter.notifyDataSetChanged();
        };
        load.run();
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                load.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (current != null) {
                    current.stop();
                }
                current = RingtoneManager.getRingtone(context, uris.get(position));
                current.play();
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.ringtone_play_failed));
            }
        });
        MaterialButton stopBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.ringtone_stop_preview));
        stopBtn.setOnClickListener(v -> {
            try {
                if (current != null) {
                    current.stop();
                }
            } catch (Exception ignored) {
            }
            current = null;
        });
        return box;
    }

    @Override
    public void onDestroy() {
        try {
            if (current != null) current.stop();
        } catch (Exception ignored) {
        }
        current = null;
    }
}
