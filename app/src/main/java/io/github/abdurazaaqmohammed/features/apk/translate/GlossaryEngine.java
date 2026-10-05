package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import java.util.List;

/**
 * Offline engine backed by a {@link Glossary}. Never touches the network, so it is the safe
 * default when the device is offline or the user does not want to send app strings anywhere.
 *
 * <p>Coverage is limited to what the dictionary knows, which is why rows it cannot improve are
 * reported as skipped instead of being blanked.
 */
public final class GlossaryEngine implements TranslationEngine {

    private final Context context;

    public GlossaryEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String id() {
        return TranslateStore.ENGINE_GLOSSARY;
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
    public void translate(Context ctx, List<TranslateRow> rows, String sourceTag, String targetTag,
                          Callback callback) {
        Glossary glossary = Glossary.load(ctx != null ? ctx : context,
                TranslateStore.pairFor(sourceTag, targetTag));
        callback.onMessage(glossary.phraseCount() + " / " + glossary.wordCount());

        int done = 0;
        for (TranslateRow row : rows) {
            if (!row.isSelected()) {
                done++;
                callback.onProgress(done, rows.size());
                continue;
            }
            if (row.getTranslation().trim().length() > 0
                    && row.getOrigin() != TranslateRow.Origin.NONE) {
                // Something is already staged, either typed by the user or a previous run.
                callback.onSkipped(row, "kept");
                done++;
                callback.onProgress(done, rows.size());
                continue;
            }
            Glossary.Hit hit = glossary.lookup(row.current);
            if (hit == null) {
                callback.onSkipped(row, "no-match");
            } else {
                row.setTranslation(hit.text, hit.phrase
                        ? TranslateRow.Origin.GLOSSARY_PHRASE
                        : TranslateRow.Origin.GLOSSARY_PARTIAL);
                callback.onTranslated(row);
            }
            done++;
            callback.onProgress(done, rows.size());
        }
    }
}
