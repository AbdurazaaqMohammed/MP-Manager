package io.github.abdurazaaqmohammed.feature.files;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;

/** Lists live progress operations and lets the user reopen their progress dialog. */
public final class OperationTasksDialog {

    private static final long REFRESH_MS = 500L;

    private OperationTasksDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        LinearLayout taskList = new LinearLayout(activity);
        taskList.setOrientation(LinearLayout.VERTICAL);

        TextView emptyView = new TextView(activity);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(activity, 24), dp(activity, 36), dp(activity, 24), dp(activity, 36));
        emptyView.setText(R.string.tasks_empty);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(taskList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tasks)
                .setView(content)
                .setNegativeButton(android.R.string.ok, null)
                .create();
        Handler handler = new Handler(Looper.getMainLooper());
        Map<ProgressManager, TaskRow> rows = new IdentityHashMap<>();
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;

                List<ProgressManager.TaskSnapshot> tasks = ProgressManager.getLiveTasks();
                Set<ProgressManager> current = Collections.newSetFromMap(new IdentityHashMap<>());
                for (ProgressManager.TaskSnapshot task : tasks) {
                    ProgressManager operation = task.operation();
                    current.add(operation);
                    TaskRow row = rows.get(operation);
                    if (row == null) {
                        row = new TaskRow(activity, v -> {
                            dialog.dismiss();
                            handler.postDelayed(() -> {
                                if (!activity.isFinishing() && !activity.isDestroyed()) operation.restore();
                            }, 180L);
                        });
                        rows.put(operation, row);
                        taskList.addView(row.view, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    row.bind(task);
                }

                Iterator<Map.Entry<ProgressManager, TaskRow>> iterator = rows.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<ProgressManager, TaskRow> entry = iterator.next();
                    if (!current.contains(entry.getKey())) {
                        taskList.removeView(entry.getValue().view);
                        iterator.remove();
                    }
                }

                boolean empty = tasks.isEmpty();
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                scroll.setVisibility(empty ? View.GONE : View.VISIBLE);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(refresh));
        dialog.setOnShowListener(d -> refresh.run());
        dialog.show();
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class TaskRow {
        final LinearLayout view;
        final TextView operationLabel;
        final TextView title;
        final TextView fileName;
        final TextView status;
        final TextView progressText;
        final ProgressBar progressBar;

        TaskRow(Activity activity, View.OnClickListener onClick) {
            view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12));
            TypedValue selectable = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
            view.setBackgroundResource(selectable.resourceId);
            view.setClickable(true);
            view.setOnClickListener(onClick);

            operationLabel = new TextView(activity);
            operationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            operationLabel.setAlpha(0.7f);
            view.addView(operationLabel, lineParams());

            title = new TextView(activity);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setMaxLines(2);
            view.addView(title, lineParams());

            fileName = new TextView(activity);
            fileName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            fileName.setAlpha(0.75f);
            fileName.setMaxLines(1);
            view.addView(fileName, lineParams());

            status = new TextView(activity);
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            view.addView(status, lineParams());

            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 4));
            barParams.topMargin = dp(activity, 6);
            view.addView(progressBar, barParams);

            progressText = new TextView(activity);
            progressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            progressText.setAlpha(0.75f);
            LinearLayout.LayoutParams textParams = lineParams();
            textParams.topMargin = dp(activity, 3);
            view.addView(progressText, textParams);

            View divider = new View(activity);
            divider.setBackgroundColor(0x1F000000);
            view.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));
        }

        void bind(ProgressManager.TaskSnapshot task) {
            setLine(operationLabel, task.operationLabel());
            title.setText(task.title());
            setLine(fileName, task.fileName());
            status.setText(task.status());
            setLine(progressText, task.progressText());
            progressBar.setIndeterminate(task.indeterminate());
            if (!task.indeterminate()) progressBar.setProgress(task.percent());
        }

        private static void setLine(TextView view, String text) {
            boolean visible = !TextUtils.isEmpty(text);
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) view.setText(text);
        }

        private static LinearLayout.LayoutParams lineParams() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
