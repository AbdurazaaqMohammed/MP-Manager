package io.github.abdurazaaqmohammed.features.editor;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.InputStreamReader;
import java.io.Reader;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

/**
 * A delegating Language wrapper that injects color preview badges via ColorPreviewAnalyzeManager.
 */
public class ColorPreviewLanguageWrapper implements Language {

    private final Language delegate;
    private final CodeEditor editor;
    private final ColorPreviewAnalyzeManager analyzeManager;

    public ColorPreviewLanguageWrapper(@NonNull Language delegate, @NonNull CodeEditor editor) {
        this.delegate = delegate;
        this.editor = editor;
        this.analyzeManager = new ColorPreviewAnalyzeManager(delegate.getAnalyzeManager(), editor);
    }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        return analyzeManager;
    }

    @Override
    public int getInterleavedAnalyzeAdvance() {
        return delegate.getInterleavedAnalyzeAdvance();
    }

    @Override
    public boolean useInterleavedAnalyzeProcessor() {
        return delegate.useInterleavedAnalyzeProcessor();
    }

    @NonNull
    @Override
    public SymbolPairMatch getSymbolPairs() {
        return delegate.getSymbolPairs();
    }

    @NonNull
    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return delegate.getNewlineHandlers();
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position, @NonNull CompletionPublisher publisher, @NonNull Bundle extraArguments) {
        delegate.requireAutoComplete(content, position, publisher, extraArguments);
    }

    @Override
    public void destroy() {
        analyzeManager.destroy();
        delegate.destroy();
    }
}
