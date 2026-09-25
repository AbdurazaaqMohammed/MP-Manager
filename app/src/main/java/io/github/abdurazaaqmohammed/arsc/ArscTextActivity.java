package io.github.abdurazaaqmohammed.arsc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.model.ResourceEntry;

import java.util.Iterator;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.activities.EditorSettingsActivity;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;
import modder.hub.dexeditor.views.TextActionWindow;

public class ArscTextActivity extends AppCompatActivity {

    static ArscData sessionData;
    static TypeBlock sessionBlock;
    static String sessionTitle;
    static String sessionHighlight;

    private ArscData data;
    private TypeBlock block;
    private MaterialToolbar toolbar;
    private UnifiedEditorFragment fragment;
    private ImageButton btnUndo;
    private ImageButton btnRedo;
    private ImageButton btnEdit;
    private ImageButton btnFile;
    private boolean applied;
    private boolean bufferDirty;
    private boolean loadingText;
    private int loadToken;
    private String currentHighlight;
    private PopupWindow idPopup;
    private final Handler selHandler = new Handler(Looper.getMainLooper());
    private boolean menuSubscribed;

    private ImageButton barButton(int icon, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(desc);
        int size = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.MATCH_PARENT);
        b.setLayoutParams(params);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        return b;
    }

    private void updateUndoRedo() {
        try {
            if (btnUndo != null && fragment != null && fragment.getEditor() != null) {
                btnUndo.setEnabled(fragment.getEditor().canUndo());
                btnRedo.setEnabled(fragment.getEditor().canRedo());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(getIntent().getIntExtra("theme", prefs.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light)));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        data = sessionData;
        block = sessionBlock;
        final String title = sessionTitle;
        final String highlight = sessionHighlight;
        if (data == null || block == null) {
            finish();
            return;
        }
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(title == null ? "Text" : title);
        toolbar.setSubtitle(data.arscFile == null ? "" : data.arscFile.getName());
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        LinearLayout barButtons = new LinearLayout(this);
        barButtons.setOrientation(LinearLayout.HORIZONTAL);
        barButtons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btnUndo = barButton(R.drawable.undo_24px, "Undo");
        btnUndo.setEnabled(false);
        btnUndo.setOnClickListener(v -> {
            try {
                if (fragment != null && fragment.getEditor() != null && fragment.getEditor().canUndo()) {
                    fragment.getEditor().undo();
                }
            } catch (Exception ignored) {
            }
            updateUndoRedo();
        });
        btnRedo = barButton(R.drawable.undo_24px, "Redo");
        btnRedo.setScaleX(-1f);
        btnRedo.setEnabled(false);
        btnRedo.setOnClickListener(v -> {
            try {
                if (fragment != null && fragment.getEditor() != null && fragment.getEditor().canRedo()) {
                    fragment.getEditor().redo();
                }
            } catch (Exception ignored) {
            }
            updateUndoRedo();
        });
        ImageButton btnSave = barButton(R.drawable.save_24px, "Save");
        btnSave.setOnClickListener(v -> applyText(null));
        btnEdit = barButton(R.drawable.edit_24px, "Edit");
        btnEdit.setOnClickListener(v -> {
            try {
                if (fragment != null) fragment.showEditMenu(btnEdit);
            } catch (Exception ignored) {
            }
        });
        btnFile = barButton(R.drawable.baseline_more_vert_24, "File options");
        btnFile.setOnClickListener(v -> {
            try {
                if (fragment != null) fragment.showFileMenu(btnFile);
            } catch (Exception ignored) {
            }
        });
        barButtons.addView(btnUndo);
        barButtons.addView(btnRedo);
        barButtons.addView(btnSave);
        barButtons.addView(btnEdit);
        barButtons.addView(btnFile);
        toolbar.addView(barButtons, new MaterialToolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        main.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout holder = new FrameLayout(this);
        holder.setId(R.id.arsc_text_editor_container);
        main.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(main);
        fragment = UnifiedEditorFragment.newInstance(null, "ArscText", null, UnifiedEditorFragment.TYPE_TEXT);
        getSupportFragmentManager().beginTransaction().replace(R.id.arsc_text_editor_container, fragment).commit();
        getSupportFragmentManager().executePendingTransactions();
        fragment.setCallback(new UnifiedEditorFragment.EditorCallback() {
            public void onContentModified(String className) {
                if (!loadingText) bufferDirty = true;
                updateUndoRedo();
            }

            public void onUndoRedoChanged(boolean canUndo, boolean canRedo) {
                if (btnUndo != null) btnUndo.setEnabled(canUndo);
                if (btnRedo != null) btnRedo.setEnabled(canRedo);
            }

            public void onSaveRequested() {
                applyText(null);
            }

            public void onCloseRequested() {
                confirmExit();
            }

            public void onPreferencesRequested() {
                try {
                    startActivity(new Intent(ArscTextActivity.this,
                            EditorSettingsActivity.class));
                } catch (Exception ignored) {
                }
            }
        });
        final TypeBlock tb = block;
        currentHighlight = highlight;
        loadBlockText();
    }

    private void loadBlockText() {
        final TypeBlock tb = block;
        final String highlight = currentHighlight;
        final int token = ++loadToken;
        Extensions.showMessage(this, R.string.loading);
        new Thread(() -> {
            String text = ArscData.typeBlockText(tb, ArscData.TEXT_ENTRY_CAP);
            int count = 0;
            try {
                count = tb.listEntries(true).size();
            } catch (Exception ignored) {
            }
            final String loaded = text;
            final int entryCount = count;
            runOnUiThread(() -> {
                if (token != loadToken || block != tb || fragment == null) return;
                loadingText = true;
                try {
                    fragment.setText(loaded);
                } catch (Exception ignored) {
                }
                loadingText = false;
                bufferDirty = false;
                ensureMenuSubscribed();
                if (highlight != null) {
                    String key = "\"" + highlight + "\"";
                    int idx = loaded.indexOf(key);
                    if (idx >= 0) {
                        int line = 0;
                        for (int i = 0; i < idx; i++) {
                            if (loaded.charAt(i) == '\n') line++;
                        }
                        int lineStart = loaded.lastIndexOf('\n', idx) + 1;
                        final int fLine = line;
                        final int fCol = idx - lineStart;
                        try {
                            fragment.navigateTo(fLine, fCol, key);
                        } catch (Exception ignored) {
                        }
                    }
                }
                Extensions.showMessage(this, entryCount + " entries");
            });
        }).start();
    }

    private void applyText(Runnable onDone) {
        if (data == null || block == null) {
            if (onDone != null) onDone.run();
            return;
        }
        String text = "";
        try {
            if (fragment != null) text = fragment.getCode();
        } catch (Exception ignored) {
        }
        final TypeBlock tb = block;
        final String content = text;
        Extensions.showMessage(this, "Applying…");
        new Thread(() -> {
            try {
                ArscData.TextApplyResult r = data.applyTypeBlockText(tb, content);
                runOnUiThread(() -> {
                    if (r.updated + r.created > 0) {
                        applied = true;
                        bufferDirty = false;
                    }
                    StringBuilder msg = new StringBuilder();
                    msg.append("Updated ").append(r.updated)
                            .append(", new ").append(r.created)
                            .append(", skipped complex ").append(r.skippedComplex)
                            .append(", invalid ").append(r.invalid);
                    if (!r.badNames.isEmpty()) {
                        msg.append(" (");
                        for (int i = 0; i < r.badNames.size(); i++) {
                            if (i > 0) msg.append(", ");
                            msg.append(r.badNames.get(i));
                        }
                        msg.append(")");
                    }
                    Extensions.showMessage(this, msg.toString());
                    if (onDone != null) onDone.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private void finishWithResult() {
        setResult(applied ? RESULT_OK : RESULT_CANCELED);
        sessionData = null;
        sessionBlock = null;
        sessionTitle = null;
        sessionHighlight = null;
        finish();
    }

    private void confirmExit() {
        if (!bufferDirty) {
            finishWithResult();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_changes)
                .setMessage(R.string.save_before_exit)
                .setPositiveButton(R.string.save, (d, w) -> applyText(this::finishWithResult))
                .setNegativeButton(R.string.discard, (d, w) -> finishWithResult())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    void openBlock(TypeBlock tb, String highlightName) {
        if (tb == null || data == null) return;
        if (bufferDirty) {
            final TypeBlock target = tb;
            final String hl = highlightName;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.unsaved_changes)
                    .setMessage(R.string.save_before_exit)
                    .setPositiveButton(R.string.save, (d, w) -> applyText(() -> openBlockNow(target, hl)))
                    .setNegativeButton(R.string.discard, (d, w) -> openBlockNow(target, hl))
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        openBlockNow(tb, highlightName);
    }

    private void openBlockNow(TypeBlock tb, String highlightName) {
        dismissIdPopup();
        block = tb;
        currentHighlight = highlightName;
        bufferDirty = false;
        applied = false;
        try {
            String label = ArscData.configLabel(tb);
            String pkg = "";
            try {
                if (tb.getPackageBlock() != null) pkg = tb.getPackageBlock().getName();
            } catch (Exception ignored) {
            }
            toolbar.setTitle(pkg.isEmpty() ? label : pkg + "/" + label);
        } catch (Exception ignored) {
        }
        loadBlockText();
    }

    public void bindSelectionMenu(TextActionWindow window) {
        if (window == null) return;
        window.setArscIdHandler(new TextActionWindow.ArscIdHandler() {
            @Override
            public boolean isArscIdAvailable(String selectedText) {
                return resolveArscEntry(selectedText) != null;
            }

            @Override
            public void onArscIdClick(String selectedText) {
                ResourceEntry re = resolveArscEntry(selectedText);
                if (re == null) return;
                try {
                    String hex = re.getHexId();
                    CopyUtil.copyToClipboard(ArscTextActivity.this, hex);
                    Extensions.showMessage(ArscTextActivity.this, "Copied " + hex);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onArscGotoIdClick(String selectedText) {
                ResourceEntry re = resolveArscEntry(selectedText);
                if (re == null) return;
                String name = normalizeArscName(selectedText);
                if (name == null) return;
                String hex;
                try {
                    hex = re.getHexId();
                } catch (Exception e) {
                    return;
                }
                List<ArscData.ConfigValue> configs = data.configValues(re);
                CodeEditor ed;
                try {
                    ed = fragment == null ? null : fragment.getEditor();
                } catch (Exception e) {
                    ed = null;
                }
                if (ed == null) return;
                int anchorLine;
                int anchorCol;
                try {
                    Cursor cursor = ed.getCursor();
                    if (cursor == null || !cursor.isSelected()) return;
                    anchorLine = cursor.getRightLine();
                    anchorCol = cursor.getRightColumn();
                } catch (Exception e) {
                    return;
                }
                showIdPopup(ed, re, name, hex, configs, anchorLine, anchorCol);
            }
        });
    }

    private String normalizeArscName(String sel) {
        if (sel == null) return null;
        String name = sel.trim();
        if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        if (name.isEmpty() || name.length() > 64 || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        return name;
    }

    private ResourceEntry resolveArscEntry(String sel) {
        String name = normalizeArscName(sel);
        if (name == null || data == null || block == null) return null;
        final String resName = name;
        ResourceEntry found = null;
        String blockType = null;
        try {
            blockType = block.getTypeName();
        } catch (Exception ignored) {
        }
        try {
            Iterator<ResourceEntry> it = data.table.getResources();
            while (it.hasNext()) {
                ResourceEntry re;
                try {
                    re = it.next();
                } catch (Exception e) {
                    continue;
                }
                if (re == null) continue;
                String rn;
                try {
                    rn = re.getName();
                } catch (Exception e) {
                    continue;
                }
                if (!resName.equals(rn)) continue;
                if (found == null) found = re;
                try {
                    if (blockType != null && blockType.equals(re.getType())) {
                        found = re;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    private void ensureMenuSubscribed() {
        if (menuSubscribed || fragment == null) return;
        CodeEditor ed;
        try {
            ed = fragment.getEditor();
        } catch (Exception e) {
            ed = null;
        }
        if (ed == null) {
            selHandler.postDelayed(this::ensureMenuSubscribed, 200);
            return;
        }
        menuSubscribed = true;
        try {
            ed.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> dismissIdPopup());
            ed.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> dismissIdPopup());
            ed.subscribeEvent(ScrollEvent.class, (event, unsubscribe) -> dismissIdPopup());
        } catch (Exception ignored) {
        }
    }

    private void dismissIdPopup() {
        if (idPopup != null) {
            try {
                idPopup.dismiss();
            } catch (Exception ignored) {
            }
            idPopup = null;
        }
    }

    private void showIdPopup(CodeEditor ed, ResourceEntry entry, String resName, String hexId,
                             List<ArscData.ConfigValue> configs, int anchorLine, int anchorCol) {
        dismissIdPopup();
        try {
            if (ed == null || !ed.getCursor().isSelected()) return;
        } catch (Exception e) {
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(4);
        list.setPadding(pad, pad, pad, pad);
        TextView idRow = menuRow("ID  " + hexId);
        list.addView(idRow);
        idRow.setOnClickListener(v -> {
            try {
                CopyUtil.copyToClipboard(this, hexId);
                Extensions.showMessage(this, "Copied " + hexId);
            } catch (Exception ignored) {
            }
            dismissIdPopup();
        });
        if (configs != null) {
            for (ArscData.ConfigValue cv : configs) {
                if (cv == null) continue;
                String label = (cv.qualifiers == null || cv.qualifiers.isEmpty()) ? "default" : cv.qualifiers;
                String value = cv.value == null ? "" : cv.value;
                if (value.length() > 60) value = value.substring(0, 60) + "…";
                TextView row = menuRow(label + "  —  " + value);
                row.setBackgroundResource(R.drawable.bg_ripple);
                final ArscData.ConfigValue item = cv;
                row.setOnClickListener(v -> {
                    dismissIdPopup();
                    gotoConfig(entry, resName, item);
                });
                list.addView(row);
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        int width = ed.getWidth() - dp(64);
        if (width <= 0) width = dp(280);
        width = Math.min(width, dp(340));
        int rows = (configs == null ? 0 : configs.size()) + 1;
        int height = rows > 6 ? dp(340) : ViewGroup.LayoutParams.WRAP_CONTENT;
        PopupWindow popup = new PopupWindow(scroll, width, height, true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        try {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, tv, true);
            popup.setBackgroundDrawable(new ColorDrawable(tv.data != 0 ? tv.data : 0xFFFFFFFF));
        } catch (Exception e) {
            popup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        }
        try {
            if (LegacyUtils.aboveSdk20) popup.setElevation(dp(8));
        } catch (Exception ignored) {
        }
        popup.setOutsideTouchable(true);
        float[] off;
        try {
            if (ed.getLayout() == null) return;
            off = ed.getLayout().getCharLayoutOffset(anchorLine, anchorCol);
        } catch (Exception e) {
            return;
        }
        if (off == null) return;
        int[] loc = new int[2];
        try {
            ed.getLocationOnScreen(loc);
        } catch (Exception e) {
            return;
        }
        int rowH;
        try {
            rowH = ed.getRowHeight();
        } catch (Exception e) {
            rowH = dp(24);
        }
        int x = loc[0] + (int) (off[0] - ed.getOffsetX());
        int y = loc[1] + (int) (off[1] - ed.getOffsetY()) + rowH + dp(4);
        int maxX = loc[0] + ed.getWidth() - width - dp(8);
        if (x > maxX) x = Math.max(loc[0] + dp(8), maxX);
        if (x < loc[0] + dp(8)) x = loc[0] + dp(8);
        int estH = rows > 6 ? dp(340) : rows * dp(52);
        if (y + estH > loc[1] + ed.getHeight() - dp(8)) {
            y = loc[1] + (int) (off[1] - ed.getOffsetY()) - estH - dp(4);
        }
        idPopup = popup;
        try {
            popup.showAtLocation(ed, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            idPopup = null;
        }
    }

    private TextView menuRow(String text) {
        TextView row = new TextView(this);
        row.setText(text == null ? "" : text);
        row.setTextSize(15);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        int h = dp(12);
        int w = dp(16);
        row.setPadding(w, h, w, h);
        try {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
        } catch (Exception ignored) {
        }
        return row;
    }

    private void gotoConfig(ResourceEntry entry, String resName, ArscData.ConfigValue cv) {
        if (entry == null || cv == null || resName == null) return;
        TypeBlock target = null;
        try {
            if (cv.entry != null) target = cv.entry.getTypeBlock();
        } catch (Exception ignored) {
        }
        if (target != null && sameBlock(target, block)) {
            jumpToNameInText(resName);
            return;
        }
        if (target != null) {
            openBlock(target, resName);
            return;
        }
        jumpToNameInText(resName);
    }

    private boolean sameBlock(TypeBlock a, TypeBlock b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            if (!ArscData.configLabel(a).equals(ArscData.configLabel(b))) return false;
            String pa = "";
            String pb = "";
            try {
                if (a.getPackageBlock() != null) pa = a.getPackageBlock().getName();
            } catch (Exception ignored) {
            }
            try {
                if (b.getPackageBlock() != null) pb = b.getPackageBlock().getName();
            } catch (Exception ignored) {
            }
            return pa.equals(pb);
        } catch (Exception e) {
            return false;
        }
    }

    private void jumpToNameInText(String resName) {
        if (fragment == null || resName == null) return;
        CodeEditor ed;
        try {
            ed = fragment.getEditor();
        } catch (Exception e) {
            return;
        }
        if (ed == null) return;
        String text;
        try {
            text = ed.getText().toString();
        } catch (Exception e) {
            return;
        }
        if (text == null) return;
        String key = "\"" + resName + "\"";
        int idx = text.indexOf(key);
        if (idx < 0) return;
        int line = 0;
        for (int i = 0; i < idx; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        int lineStart = text.lastIndexOf('\n', idx) + 1;
        try {
            fragment.navigateTo(line, idx - lineStart, key);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        dismissIdPopup();
        try {
            if (fragment != null && fragment.getEditor() != null) fragment.getEditor().release();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
