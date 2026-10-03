package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.DateTime;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildEventCount().
 * Owns its ticker and stops it in onDestroy (the host calls it).
 */
public class EventCountTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;

    public EventCountTool() {
        super("eventcount", R.string.eventcount_title, R.string.eventcount_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.eventcount_event_countdown));
        EditText titleInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.eventcount_event_name), InputType.TYPE_CLASS_TEXT);
        EditText dateInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.eventcount_date_yyyy_mm_dd_hh_mm), InputType.TYPE_CLASS_DATETIME);
        try {
            String savedTitle = context.getSharedPreferences("tools", Context.MODE_PRIVATE).getString("event_title", "");
            String savedDate = context.getSharedPreferences("tools", Context.MODE_PRIVATE).getString("event_date", "");
            titleInput.setText(savedTitle);
            dateInput.setText(savedDate);
        } catch (Exception ignored) {
        }
        TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(24);
        output.setGravity(Gravity.CENTER);
        final Runnable ticker = new Runnable() {
            public void run() {
                if (!active) {
                    return;
                }
                try {
                    String raw = dateInput.getText().toString().trim();
                    if (raw.isEmpty()) {
                        output.setText(output.getContext().getString(R.string.eventcount_enter_event_date));
                        return;
                    }
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    f.setLenient(false);
                    Date target = f.parse(raw);
                    long diff = target.getTime() - System.currentTimeMillis();
                    String name = titleInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = output.getContext().getString(R.string.eventcount_default_name);
                    }
                    long[] parts = DateTime.countdownParts(diff);
                    if (parts == null) {
                        output.setText(name + "\n" + output.getContext().getString(R.string.eventcount_happening_now));
                        return;
                    }
                    output.setText(name + "\n" + parts[0] + "d " + String.format(Locale.US, "%02d:%02d:%02d", parts[1], parts[2], parts[3]));
                } catch (Exception e) {
                    output.setText(output.getContext().getString(R.string.eventcount_use_yyyy_mm_dd_hh_mm));
                }
                if (active) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        active = true;
        handler.post(ticker);
        MaterialButton saveBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.eventcount_save_event));
        saveBtn.setOnClickListener(v -> {
            try {
                context.getSharedPreferences("tools", Context.MODE_PRIVATE).edit()
                        .putString("event_title", titleInput.getText().toString().trim())
                        .putString("event_date", dateInput.getText().toString().trim()).apply();
                ToolViewFactory.toast(context, context.getString(R.string.eventcount_saved));
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.eventcount_save_failed));
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
    }
}
