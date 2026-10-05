package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import java.util.List;

/**
 * A way of filling in translations for staged rows.
 *
 * <p>Implementations run on a background thread and report through {@link Callback} so the
 * list can update incrementally; a big APK has thousands of strings and a single blocking
 * call would freeze the UI with no way to show progress.
 */
public interface TranslationEngine {

    /** Stable id, persisted as the user's last choice. */
    String id();

    /**
     * Whether the engine needs the network. Used to warn before the user commits to a run
     * that will fail on a device without connectivity.
     */
    boolean needsNetwork();

    /** Whether the engine can put several strings into one request. */
    boolean supportsBatch();

    /**
     * Translates {@code rows} in place.
     *
     * @param rows      rows to fill; only selected rows are touched
     * @param sourceTag BCP-47 tag of the source language
     * @param targetTag BCP-47 tag of the target language
     * @param callback  progress sink, invoked from the calling thread
     * @throws Exception to abort the whole run; already-translated rows are kept
     */
    void translate(Context context, List<TranslateRow> rows, String sourceTag, String targetTag,
                   Callback callback) throws Exception;

    /** Incremental progress sink. */
    interface Callback {
        /** @param done number of rows finished, @param total rows in the run */
        void onProgress(int done, int total);

        /** @param row the row that just received a translation */
        void onTranslated(TranslateRow row);

        /** @param row the row the engine deliberately left alone, with the reason */
        void onSkipped(TranslateRow row, String reason);

        /** A human-readable note, e.g. "glossary had 812 entries". */
        void onMessage(String message);
    }
}
