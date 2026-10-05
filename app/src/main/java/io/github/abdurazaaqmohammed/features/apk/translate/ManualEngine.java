package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import java.util.List;

/**
 * The manual mode: nothing is filled in automatically.
 *
 * <p>It is still a real pass rather than a no-op, because it does the one job that matters most
 * when a human is doing the work - validating what was typed. A staged translation that lost a
 * format specifier, or that still holds markup the author meant to remove, is reported so it can
 * be fixed before it is written into the arsc.
 */
public final class ManualEngine implements TranslationEngine {

    @Override
    public String id() {
        return TranslateStore.ENGINE_MANUAL;
    }

    @Override
    public boolean needsNetwork() {
        return false;
    }

    @Override
    public boolean supportsBatch() {
        return false;
    }

    @Override
    public void translate(Context context, List<TranslateRow> rows, String sourceTag, String targetTag,
                          Callback callback) {
        int done = 0;
        int problems = 0;
        for (TranslateRow row : rows) {
            if (!row.isSelected()) {
                callback.onSkipped(row, "unselected");
            } else if (row.getTranslation().trim().isEmpty()) {
                callback.onSkipped(row, "empty");
                problems++;
            } else {
                String violation = FormatGuard.firstViolation(row.current, row.getTranslation());
                if (violation != null) {
                    callback.onSkipped(row, "token:" + violation);
                    problems++;
                } else {
                    row.setTranslation(row.getTranslation(), TranslateRow.Origin.MANUAL);
                    callback.onTranslated(row);
                }
            }
            done++;
            callback.onProgress(done, rows.size());
        }
        if (problems > 0) callback.onMessage(String.valueOf(problems));
    }
}
