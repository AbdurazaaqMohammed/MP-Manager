package io.github.abdurazaaqmohammed.features.editor;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.StyleReceiver;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * An AnalyzeManager decorator that intercepts Styles emitted by the underlying language analyzer
 * and decorates Hex color codes (#RGB, #ARGB, #RRGGBB, #AARRGGBB) with color preview backgrounds
 * and high-contrast foreground text.
 * Uses dynamic Proxy for StyleReceiver to remain fully compatible across sora-editor versions.
 */
public class ColorPreviewAnalyzeManager implements AnalyzeManager {

    private final AnalyzeManager delegate;
    private final CodeEditor editor;
    private volatile ContentReference textReference;

    public ColorPreviewAnalyzeManager(@NonNull AnalyzeManager delegate, @Nullable CodeEditor editor) {
        this.delegate = delegate;
        this.editor = editor;
    }

    @Override
    public void setReceiver(@Nullable StyleReceiver receiver) {
        if (receiver == null) {
            delegate.setReceiver(null);
            return;
        }

        StyleReceiver proxy = (StyleReceiver) Proxy.newProxyInstance(
                receiver.getClass().getClassLoader(),
                new Class<?>[]{StyleReceiver.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxyObj, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if (args != null) {
                            // Intercept setStyles / updateStyles calls to modify Styles
                            for (Object arg : args) {
                                if (arg instanceof Styles) {
                                    applyColorHighlights((Styles) arg);
                                }
                            }
                            // Replace sourceManager parameter if passed
                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof AnalyzeManager) {
                                    args[i] = ColorPreviewAnalyzeManager.this;
                                }
                            }
                        }
                        return method.invoke(receiver, args);
                    }
                }
        );

        delegate.setReceiver(proxy);
    }

    @Override
    public void reset(@NonNull ContentReference content, @NonNull Bundle extraArguments) {
        this.textReference = content;
        delegate.reset(content, extraArguments);
    }

    @Override
    public void insert(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence insertedContent) {
        delegate.insert(start, end, insertedContent);
    }

    @Override
    public void delete(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence deletedContent) {
        delegate.delete(start, end, deletedContent);
    }

    @Override
    public void rerun() {
        delegate.rerun();
    }

    @Override
    public void destroy() {
        delegate.destroy();
        this.textReference = null;
    }

    private void applyColorHighlights(@Nullable Styles styles) {
        if (styles == null || styles.spans == null || textReference == null) return;
        Content content = textReference.getReference();
        if (content == null) return;

        EditorColorScheme scheme = editor != null ? editor.getColorScheme() : null;
        if (scheme != null) {
            ColorPreviewHelper.initPredefinedColors(scheme);
        }

        Spans spans = styles.spans;
        if (!spans.supportsModify()) return;
        Spans.Modifier modifier = spans.modify();
        Spans.Reader reader = spans.read();

        int lineCount = Math.min(content.getLineCount(), spans.getLineCount());
        for (int lineIdx = 0; lineIdx < lineCount; lineIdx++) {
            CharSequence lineSeq = content.getLine(lineIdx);
            if (lineSeq == null || lineSeq.length() == 0) continue;
            String lineStr = lineSeq.toString();

            Matcher matcher = ColorPreviewHelper.getHexColorPattern().matcher(lineStr);
            if (!matcher.find()) continue;

            List<Span> originalSpans = reader.getSpansOnLine(lineIdx);
            if (originalSpans == null || originalSpans.isEmpty()) continue;

            List<Span> decoratedSpans = decorateLineSpans(lineStr, originalSpans, scheme);
            if (decoratedSpans != null) {
                modifier.setSpansOnLine(lineIdx, decoratedSpans);
            }
        }
    }

    private List<Span> decorateLineSpans(String lineStr, List<Span> originalSpans, EditorColorScheme scheme) {
        class ColorRegion {
            int start;
            int end;
            int parsedColor;
        }

        List<ColorRegion> regions = new ArrayList<>();
        Matcher matcher = ColorPreviewHelper.getHexColorPattern().matcher(lineStr);
        while (matcher.find()) {
            String hex = matcher.group();
            Integer parsed = ColorPreviewHelper.parseHexColor(hex);
            if (parsed != null) {
                ColorRegion r = new ColorRegion();
                r.start = matcher.start();
                r.end = matcher.end();
                r.parsedColor = parsed;
                regions.add(r);
            }
        }

        if (regions.isEmpty()) return null;

        List<Span> result = new ArrayList<>();
        for (Span s : originalSpans) {
            result.add(s.copy());
        }
        Collections.sort(result, Comparator.comparingInt(Span::getColumn));

        for (ColorRegion cr : regions) {
            int bgId = ColorPreviewHelper.getOrCreateColorId(cr.parsedColor, scheme);
            int fgId = ColorPreviewHelper.getContrastTextColorId(cr.parsedColor);
            long colorStyle = TextStyle.makeStyle(fgId, bgId, true, false, false);

            long styleAfter = EditorColorScheme.TEXT_NORMAL;
            for (Span s : result) {
                if (s.getColumn() <= cr.end) {
                    styleAfter = s.getStyle();
                } else {
                    break;
                }
            }

            List<Span> updated = new ArrayList<>();
            for (Span s : result) {
                if (s.getColumn() > cr.start && s.getColumn() < cr.end) {
                    continue;
                }
                if (s.getColumn() == cr.start) {
                    s.setStyle(colorStyle);
                    updated.add(s);
                    continue;
                }
                updated.add(s);
            }
            result = updated;

            boolean hasStart = false;
            for (Span s : result) {
                if (s.getColumn() == cr.start) {
                    hasStart = true;
                    break;
                }
            }
            if (!hasStart) {
                result.add(SpanFactory.obtain(cr.start, colorStyle));
            }

            if (cr.end < lineStr.length()) {
                boolean hasEnd = false;
                for (Span s : result) {
                    if (s.getColumn() == cr.end) {
                        hasEnd = true;
                        break;
                    }
                }
                if (!hasEnd) {
                    result.add(SpanFactory.obtain(cr.end, styleAfter));
                }
            }
        }

        Collections.sort(result, Comparator.comparingInt(Span::getColumn));
        return result;
    }
}
