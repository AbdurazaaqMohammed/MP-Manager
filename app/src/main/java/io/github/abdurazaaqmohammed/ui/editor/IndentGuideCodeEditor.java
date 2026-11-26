package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorRenderer;
import io.github.rosemoe.sora.widget.layout.Layout;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * {@link CodeEditor} extension whose renderer draws vertical indentation guide lines
 * in both unwrapped and word-wrapped layouts.
 *
 * <p>Stock Sora skips {@code drawBlockLines} completely when word wrap is enabled
 * (falling back only to {@code drawSideBlockLine} in the gutter). Drawing wrapped
 * block guides inside {@link IndentGuideRenderer#drawSideBlockLine(Canvas)} using
 * {@link Layout#getCharLayoutOffset(int, int, float[])} ensures nested blocks
 * remain clearly framed even when long lines wrap.
 */
public class IndentGuideCodeEditor extends CodeEditor {

    public IndentGuideCodeEditor(@NonNull Context context) {
        super(context);
    }

    public IndentGuideCodeEditor(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public IndentGuideCodeEditor(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IndentGuideCodeEditor(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @NonNull
    @Override
    protected EditorRenderer onCreateRenderer() {
        return new IndentGuideRenderer(this);
    }

    /** Renderer subclass adding full vertical block guides in word-wrap mode. */
    private static final class IndentGuideRenderer extends EditorRenderer {

        private final IndentGuideCodeEditor owner;
        private final RectF guideRect = new RectF();
        private final float[] charOffsetBuf = new float[2];

        IndentGuideRenderer(@NonNull IndentGuideCodeEditor editor) {
            super(editor);
            this.owner = editor;
        }

        @Override
        protected void drawSideBlockLine(@NonNull Canvas canvas) {
            drawWrappedBlockLines(canvas);
        }

        private void drawWrappedBlockLines(@NonNull Canvas canvas) {
            Styles styles = owner.getStyles();
            List<CodeBlock> blocks = styles == null ? null : styles.blocks;
            if (blocks == null || blocks.isEmpty()) {
                return;
            }

            Content text = owner.getText();
            Layout layout = owner.getLayout();
            EditorColorScheme scheme = owner.getColorScheme();
            if (text == null || layout == null || scheme == null) {
                return;
            }

            int lineCount = text.getLineCount();
            if (lineCount <= 0) {
                return;
            }

            int firstRow = Math.max(0, owner.getFirstVisibleRow());
            int lastRow = Math.max(firstRow, owner.getLastVisibleRow());
            int firstLine = IndentGuideGeometry.clampIndex(layout.getLineNumberForRow(firstRow), 0, lineCount - 1);
            int lastLine = IndentGuideGeometry.clampIndex(layout.getLineNumberForRow(lastRow), firstLine, lineCount - 1);

            int startIdx = CodeBlock.binarySearchEndBlock(firstLine, blocks);
            if (startIdx < 0) {
                startIdx = 0;
            }

            boolean indentCountMode = styles.isIndentCountMode();
            float spaceWidth = paintGeneral.getSpaceWidth();
            int rowHeight = owner.getRowHeight();
            int offsetY = owner.getOffsetY();
            int viewHeight = owner.getHeight();
            int viewWidth = owner.getWidth();
            int suppressSwitch = styles.getSuppressSwitch();
            int currentBlock = owner.getCurrentCursorBlock();

            float halfStroke = Math.max(1f, owner.getDpUnit() * owner.getBlockLineWidth()) * 0.5f;
            float textStartX = owner.measureTextRegionOffset() - owner.getOffsetX();
            float clipLeft = Math.max(0f, textStartX - halfStroke - owner.getDpUnit());

            canvas.save();
            canvas.clipRect(clipLeft, 0f, viewWidth, viewHeight);
            try {
                boolean drawn = false;
                int skipped = 0;
                for (int i = startIdx; i < blocks.size(); i++) {
                    CodeBlock block = blocks.get(i);
                    if (block == null) {
                        continue;
                    }
                    int startLine = block.startLine;
                    int endLine = block.endLine;
                    if (startLine < 0 || endLine >= lineCount || endLine < startLine) {
                        continue;
                    }

                    if (IndentGuideGeometry.isBlockVisible(startLine, endLine, firstLine, lastLine)) {
                        try {
                            float localX = resolveBlockLocalX(
                                    block, text, layout, indentCountMode, spaceWidth, charOffsetBuf);
                            float guideX = textStartX + localX;

                            int startLineLen = Math.max(0, text.getColumnCount(startLine));
                            float startBottomY = layout.getCharLayoutOffset(startLine, startLineLen, charOffsetBuf)[0];
                            float top = IndentGuideGeometry.resolveGuideTop(startBottomY, offsetY);

                            float endTargetY;
                            if (block.toBottomOfEndLine) {
                                int endLineLen = Math.max(0, text.getColumnCount(endLine));
                                endTargetY = layout.getCharLayoutOffset(endLine, endLineLen, charOffsetBuf)[0];
                            } else {
                                float endTopRowBottom = layout.getCharLayoutOffset(endLine, 0, charOffsetBuf)[0];
                                endTargetY = endTopRowBottom - rowHeight;
                            }
                            float bottom = IndentGuideGeometry.resolveGuideBottom(endTargetY, offsetY, viewHeight);

                            if (bottom > top && guideX + halfStroke >= clipLeft && guideX - halfStroke <= viewWidth) {
                                int colorId = (i == currentBlock)
                                        ? EditorColorScheme.BLOCK_LINE_CURRENT
                                        : EditorColorScheme.BLOCK_LINE;
                                int color = IndentGuideGeometry.softenWrappedGuideColor(scheme.getColor(colorId));
                                if ((color >>> 24) != 0) {
                                    guideRect.set(guideX - halfStroke, top, guideX + halfStroke, bottom);
                                    drawColor(canvas, color, guideRect);
                                }
                            }
                        } catch (IndexOutOfBoundsException ignored) {
                        }
                        drawn = true;
                    } else if (drawn) {
                        if (skipped >= suppressSwitch) {
                            break;
                        }
                        skipped++;
                    }
                }
            } finally {
                canvas.restore();
            }
        }

        private static float resolveBlockLocalX(
                @NonNull CodeBlock block,
                @NonNull Content text,
                @NonNull Layout layout,
                boolean indentCountMode,
                float spaceWidth,
                @NonNull float[] posBuf) {
            if (indentCountMode) {
                int col = Math.min(Math.max(0, block.startColumn), Math.max(0, block.endColumn));
                return spaceWidth * col;
            }
            int startLen = Math.max(0, text.getColumnCount(block.startLine));
            int endLen = Math.max(0, text.getColumnCount(block.endLine));
            int startCol = IndentGuideGeometry.clampIndex(block.startColumn, 0, startLen);
            int endCol = IndentGuideGeometry.clampIndex(block.endColumn, 0, endLen);
            float startX = layout.getCharLayoutOffset(block.startLine, startCol, posBuf)[1];
            float endX = layout.getCharLayoutOffset(block.endLine, endCol, posBuf)[1];
            return Math.min(startX, endX);
        }
    }
}
