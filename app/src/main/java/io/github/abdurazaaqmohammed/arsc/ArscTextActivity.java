package io.github.abdurazaaqmohammed.arsc;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.arsc.chunk.TypeBlock;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

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
                    startActivity(new android.content.Intent(ArscTextActivity.this,
                            io.github.abdurazaaqmohammed.ui.activities.EditorSettingsActivity.class));
                } catch (Exception ignored) {
                }
            }
        });
        final TypeBlock tb = block;
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
                if (block != tb || fragment == null) return;
                loadingText = true;
                try {
                    fragment.setText(loaded);
                } catch (Exception ignored) {
                }
                loadingText = false;
                bufferDirty = false;
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
                            fragment.getEditor().post(() -> {
                                try {
                                    fragment.getEditor().jumpToLine(fLine);
                                    fragment.getEditor().getCursor().set(fLine, fCol);
                                } catch (Exception ignored) {
                                }
                            });
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
                .setTitle("Unsaved changes")
                .setMessage("Save before exit?")
                .setPositiveButton("Save", (d, w) -> applyText(this::finishWithResult))
                .setNegativeButton("Discard", (d, w) -> finishWithResult())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    @Override
    protected void onDestroy() {
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
