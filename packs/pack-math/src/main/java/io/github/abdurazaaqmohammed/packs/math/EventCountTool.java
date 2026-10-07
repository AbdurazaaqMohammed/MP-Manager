package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        super("eventcount", "Event Countdown", "Countdown to events live", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_event_countdown, "Event Countdown"));
        EditText titleInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_event_name, "Event name"), InputType.TYPE_CLASS_TEXT);
        EditText dateInput = ToolViewFactory.makeDateTimeField(box, PackRes.str("math", R.string.s_date_time, "Date & time"));
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
                        output.setText(PackRes.str("math", R.string.s_enter_event_date, "Enter event date"));
                        return;
                    }
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    f.setLenient(false);
                    Date target = f.parse(raw);
                    long diff = target.getTime() - System.currentTimeMillis();
                    String name = titleInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Event";
                    }
                    long[] parts = DateTime.countdownParts(diff);
                    if (parts == null) {
                        output.setText(name + "\nHappening now or passed");
                        return;
                    }
                    output.setText(name + "\n" + parts[0] + "d " + String.format(Locale.US, "%02d:%02d:%02d", parts[1], parts[2], parts[3]));
                } catch (Exception e) {
                    output.setText(PackRes.str("math", R.string.s_use_yyyy_mm_dd_hh_mm, "Use yyyy-MM-dd HH:mm"));
                }
                if (active) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        active = true;
        handler.post(ticker);
        MaterialButton saveBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_save_event, "Save event"));
        saveBtn.setOnClickListener(v -> {
            try {
                context.getSharedPreferences("tools", Context.MODE_PRIVATE).edit()
                        .putString("event_title", titleInput.getText().toString().trim())
                        .putString("event_date", dateInput.getText().toString().trim()).apply();
                ToolViewFactory.toast(context, PackRes.str("math", R.string.s_saved, "Saved"));
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("math", R.string.s_save_failed, "Save failed"));
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
