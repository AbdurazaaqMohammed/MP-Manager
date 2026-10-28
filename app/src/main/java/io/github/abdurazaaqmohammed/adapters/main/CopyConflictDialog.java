package io.github.abdurazaaqmohammed.adapters.main;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

/**
 * Dialogs shown when a copy would overwrite an existing name. Offers the useful
 * answers (replace, skip, keep both under a numbered name) and, for multi-item
 * copies, lists every conflict up front so one choice can be applied to all of
 * them instead of prompting once per file.
 *
 * <p>Calls block on the caller's worker thread while the dialog is up, matching
 * the rest of the file-operation code.
 */
public final class CopyConflictDialog {

    public static final int ACTION_REPLACE = 0;
    public static final int ACTION_SKIP = 1;
    public static final int ACTION_KEEP_BOTH = 2;
    public static final int ACTION_REVIEW_EACH = 3;
    public static final int ACTION_CANCEL = 4;

    private CopyConflictDialog() {
    }

    /** One name collision, with everything needed to describe it to the user. */
    public static final class Conflict {
        public final Object source;
        public final String sourceName;
        public final long sourceSize;
        public final long sourceTime;
        public final String existingName;
        public final long existingSize;
        public final long existingTime;
        public final String suggestedName;

        public Conflict(Object source, String sourceName, long sourceSize, long sourceTime,
                        String existingName, long existingSize, long existingTime,
                        String suggestedName) {
            this.source = source;
            this.sourceName = sourceName;
            this.sourceSize = sourceSize;
            this.sourceTime = sourceTime;
            this.existingName = existingName;
            this.existingSize = existingSize;
            this.existingTime = existingTime;
            this.suggestedName = suggestedName;
        }
    }

    public static final class Decision {
        public final int action;
        public final boolean applyToAll;

        public Decision(int action, boolean applyToAll) {
            this.action = action;
            this.applyToAll = applyToAll;
        }
    }

    public static Decision askConflict(MainActivity context, DialogUtil dialogUtil, int singleTitleRes,
                                       int allTitleRes, List<Conflict> conflicts, Runnable onDismissed) {
        if (conflicts == null || conflicts.isEmpty()) return new Decision(ACTION_CANCEL, false);
        final CountDownLatch latch = new CountDownLatch(1);
        final Decision[] result = new Decision[]{new Decision(ACTION_CANCEL, false)};
        final MaterialCheckBox[] cbHolder =
                new MaterialCheckBox[1];

        context.handler.post(() -> {
            View view;
            int titleRes;
            if (conflicts.size() == 1) {
                titleRes = singleTitleRes;
                view = buildSingleConflictView(context, conflicts.get(0), cbHolder);
            } else {
                titleRes = allTitleRes;
                view = buildConflictList(context, conflicts, cbHolder);
            }
            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(titleRes)
                    .setView(view)
                    .setPositiveButton(R.string.copy_conflict_replace,
                            (d, w) -> {
                                boolean all = cbHolder[0] != null && cbHolder[0].isChecked();
                                result[0] = new Decision(ACTION_REPLACE, all);
                                latch.countDown();
                            })
                    .setNeutralButton(R.string.keep,
                            (d, w) -> {
                                boolean all = cbHolder[0] != null && cbHolder[0].isChecked();
                                result[0] = new Decision(ACTION_KEEP_BOTH, all);
                                latch.countDown();
                            })
                    .setNegativeButton(R.string.copy_conflict_skip,
                            (d, w) -> {
                                boolean all = cbHolder[0] != null && cbHolder[0].isChecked();
                                result[0] = new Decision(ACTION_SKIP, all);
                                latch.countDown();
                            })
                    .setOnCancelListener(d -> latch.countDown())
                    .create());
        });
        await(latch);
        if (onDismissed != null) onDismissed.run();
        return result[0];
    }

    /**
     * Asks about one conflict. {@code onDismissed} runs once the dialog is gone
     * so the caller can bring its progress dialog back.
     */
    public static int askSingle(MainActivity context, DialogUtil dialogUtil, Conflict c,
                                Runnable onDismissed) {
        return askSingle(context, dialogUtil, R.string.copy_conflict_title, c, onDismissed);
    }

    /**
     * Asks about one conflict. {@code onDismissed} runs once the dialog is gone
     * so the caller can bring its progress dialog back.
     */
    public static int askSingle(MainActivity context, DialogUtil dialogUtil, int titleRes,
                                Conflict c, Runnable onDismissed) {
        Decision d = askConflict(context, dialogUtil, titleRes, titleRes,
                Collections.singletonList(c), onDismissed);
        return d.action;
    }

    /**
     * Lists every conflict in the batch and asks for one decision to apply to
     * all of them. Returns {@link #ACTION_REVIEW_EACH} when the user wants to
     * decide per file, or {@link #ACTION_CANCEL} to abort the whole copy.
     */
    public static int askBulk(MainActivity context, DialogUtil dialogUtil, List<Conflict> conflicts,
                              Runnable onDismissed) {
        return askBulk(context, dialogUtil, R.string.copy_conflict_title,
                R.string.copy_conflict_title_all, conflicts, onDismissed);
    }

    /**
     * Lists every conflict in the batch and asks for one decision to apply to
     * all of them. Returns {@link #ACTION_REVIEW_EACH} when the user wants to
     * decide per file, or {@link #ACTION_CANCEL} to abort the whole operation.
     */
    public static int askBulk(MainActivity context, DialogUtil dialogUtil, int singleTitleRes,
                              int allTitleRes, List<Conflict> conflicts, Runnable onDismissed) {
        Decision d = askConflict(context, dialogUtil, singleTitleRes, allTitleRes, conflicts, onDismissed);
        return d.action;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describe(MainActivity context, Conflict c) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.copy_conflict_source,
                c.sourceName, formatSize(context, c.sourceSize), formatDate(context, c.sourceTime)));
        sb.append('\n').append(context.getString(R.string.copy_conflict_arrow)).append('\n');
        sb.append(context.getString(R.string.copy_conflict_existing,
                c.existingName, formatSize(context, c.existingSize), formatDate(context, c.existingTime)));
        sb.append('\n').append(context.getString(R.string.copy_conflict_suggested, c.suggestedName));
        return sb.toString();
    }

    private static View buildSingleConflictView(MainActivity context, Conflict c,
                                                            MaterialCheckBox[] outCheckBox) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad / 2, pad, 0);

        TextView tv = new TextView(context);
        tv.setText(describe(context, c));
        tv.setTextSize(14f);
        root.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialCheckBox cb = new MaterialCheckBox(context);
        cb.setText(R.string.all_checkbox);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.setMargins(0, pad / 2, 0, pad / 4);
        cb.setLayoutParams(cbLp);
        root.addView(cb);
        if (outCheckBox != null && outCheckBox.length > 0) outCheckBox[0] = cb;

        return root;
    }

    private static View buildConflictList(MainActivity context, List<Conflict> conflicts,
                                                      MaterialCheckBox[] outCheckBox) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(context);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad / 4);
        for (Conflict c : conflicts) {
            TextView tv = new TextView(context);
            tv.setTextSize(13f);
            tv.setPadding(0, pad / 4, 0, pad / 4);
            tv.setText(context.getString(R.string.copy_conflict_row,
                    c.sourceName, formatSize(context, c.sourceSize),
                    c.existingName, formatSize(context, c.existingSize)));
            box.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(box);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MaterialCheckBox cb = new MaterialCheckBox(context);
        cb.setText(R.string.all_checkbox);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.setMargins(pad, pad / 4, pad, pad / 4);
        cb.setLayoutParams(cbLp);
        root.addView(cb);
        if (outCheckBox != null && outCheckBox.length > 0) outCheckBox[0] = cb;

        return root;
    }

    private static String formatSize(MainActivity context, long bytes) {
        if (bytes < 0) return context.getString(android.R.string.unknownName);
        return FileSize.getHumanReadableFileSize(bytes);
    }

    private static String formatDate(MainActivity context, long millis) {
        if (millis <= 0) return "";
        String date = UiPrefs.formatDate(context, millis);
        return TextUtils.isEmpty(date) ? "" : date;
    }

    /** True when a choice applies to every remaining conflict without asking. */
    public static boolean isBulkAction(int action) {
        return action == ACTION_REPLACE || action == ACTION_SKIP || action == ACTION_KEEP_BOTH;
    }
}