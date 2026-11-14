package modder.hub.dexeditor.views;

import android.graphics.Rect;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.rosemoe.sora.event.EditorReleaseEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;

/** Fits suggestions to the visible editor, including edge-to-edge and floating IMEs. */
public final class ImeAwareCompletion extends EditorAutoCompletion {
    private int availableHeight;
    private int x, cursorTop, cursorBottom, visibleTop, visibleBottom, gap;
    private boolean above, positioned;

    public ImeAwareCompletion(CodeEditor editor) {
        super(editor);
        getPopup().setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        getPopup().setClippingEnabled(true);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            if (isShowing()) updateCompletionWindowPosition(false);
        };
        editor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        editor.subscribeEvent(EditorReleaseEvent.class, (event, unsubscribe) -> {
            if (editor.getViewTreeObserver().isAlive())
                editor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        });
    }

    @Override public void updateCompletionWindowPosition(boolean ignored) {
        CodeEditor editor = getEditor();
        if (!editor.isAttachedToWindow() || editor.getWidth() <= 0 || editor.getHeight() <= 0) return;
        int[] location = new int[2];
        editor.getLocationOnScreen(location);
        Rect frame = new Rect();
        editor.getWindowVisibleDisplayFrame(frame);
        visibleTop = Math.max(0, frame.top - location[1]);
        visibleBottom = Math.min(editor.getHeight(), frame.bottom - location[1]);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(editor);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            int[] rootLocation = new int[2];
            editor.getRootView().getLocationOnScreen(rootLocation);
            int imeTop = rootLocation[1] + editor.getRootView().getHeight()
                    - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            visibleBottom = Math.min(visibleBottom, imeTop - location[1]);
        }
        var cursor = editor.getCursor();
        float[] offset = editor.getLayout().getCharLayoutOffset(cursor.getRightLine(), cursor.getRightColumn());
        cursorBottom = (int) offset[0] - editor.getOffsetY();
        cursorTop = cursorBottom - editor.getRowHeight();
        gap = Math.max(2, Math.round(4 * editor.getDpUnit()));
        int belowSpace = Math.max(0, visibleBottom - cursorBottom - gap);
        int aboveSpace = Math.max(0, cursorTop - visibleTop - gap);
        above = belowSpace < Math.round(100 * editor.getDpUnit()) && aboveSpace > belowSpace;
        availableHeight = Math.min(Math.round(200 * editor.getDpUnit()), above ? aboveSpace : belowSpace);
        maxHeight = availableHeight;
        int width = Math.min(editor.getWidth() * 7 / 8, Math.round(360 * editor.getDpUnit()));
        x = Math.max(0, Math.min((int) editor.updateCursorAnchor(), editor.getWidth() - width));
        positioned = true;
        if (availableHeight < editor.getRowHeight()) { hide(); return; }
        // Sora only attaches the adapter to this component when the first
        // candidates arrive. During initial layout it has no Context yet.
        int desired = adapter == null || adapter.getCount() == 0 ? getHeight()
                : adapter.getItemHeight() * adapter.getCount();
        setSize(width, desired);
    }

    // Sora resizes again after publishing candidates; respect the same IME bound.
    @Override public void setSize(int width, int height) {
        if (!positioned) { super.setSize(width, height); return; }
        int fitted = Math.max(0, Math.min(height, availableHeight));
        super.setSize(width, fitted);
        int y = above ? cursorTop - gap - fitted : cursorBottom + gap;
        super.setLocationAbsolutely(x, Math.max(visibleTop, Math.min(y, visibleBottom - fitted)));
    }

    @Override public void show() {
        if (positioned && availableHeight < getEditor().getRowHeight()) return;
        super.show();
    }
}
