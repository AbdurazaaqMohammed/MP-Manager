package io.github.abdurazaaqmohammed.packs.time.notes;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class NoteEditor {

    public interface Host {
        void onClose();

        void onChanged(Note note);

        void onRequestShare(Note note);

        void onRequestDelete(Note note);

        void onRequestLock(Note note);

        void onRequestDuplicate(Note note);
    }

    private final Context context;
    private final Note note;
    private final NoteStore store;
    private final NoteSettings settings;
    private final Host host;
    private final int accent;
    private final NotesDialogs dialogs;

    private TextInputEditText titleField;
    private EditText bodyField;
    private TextView statsView;
    private TextView previewView;
    private View previewPane;
    private View checklistPane;
    private LinearLayout checklistBox;
    private LinearLayout formatBar;
    private MaterialButton pinButton;
    private boolean previewMode;
    private boolean applying;
    private boolean dirty;

    public NoteEditor(Context context, Note note, NoteStore store, NoteSettings settings,
                      Host host) {
        this.context = context;
        this.note = note;
        this.store = store;
        this.settings = settings;
        this.host = host;
        this.accent = settings.accent() >= 0 ? settings.accent() : NotesUi.primary(context);
        this.dialogs = new NotesDialogs(context, this.accent);
    }

    public Note note() {
return note;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void commit() {
        persist(false);
    }



public View build() {
        LinearLayout root = NotesUi.column(context);
        root.setBackgroundColor(NotesUi.surface(context));
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleField = new TextInputEditText(context);
        titleField.setHint("Title");
        titleField.setSingleLine(true);
        titleField.setText(note.title);
        titleField.setTextSize(22);
        titleField.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleField.setTextColor(NotesUi.onSurface(context));
        titleField.setHintTextColor(NotesUi.withAlpha(NotesUi.onSurfaceVariant(context), 140));
        titleField.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 10),
                NotesUi.dp(context, 18), NotesUi.dp(context, 6));
        titleField.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.addView(titleField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bodyField = new EditText(context);
        bodyField.setGravity(Gravity.TOP | Gravity.START);
        bodyField.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        bodyField.setText(note.plain());
        bodyField.setTextSize(16 * settings.textScale());
        bodyField.setLineSpacing(0f, settings.lineSpacing());
        bodyField.setTypeface(Typeface.create(settings.fontFamily(), Typeface.NORMAL));
        bodyField.setTextColor(NotesUi.onSurface(context));
        bodyField.setHint("Start writing\u2026");
        bodyField.setHintTextColor(NotesUi.withAlpha(NotesUi.onSurfaceVariant(context), 130));
        bodyField.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 4),
                NotesUi.dp(context, 18), NotesUi.dp(context, 120));
        bodyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(bodyField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        checklistPane = buildChecklistPane();
        checklistPane.setVisibility(View.GONE);
        root.addView(checklistPane, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        previewView = new TextView(context);
        previewView.setTextSize(16 * settings.textScale());
        previewView.setLineSpacing(0f, settings.lineSpacing());
        previewView.setTextColor(NotesUi.onSurface(context));
        previewView.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 8),
                NotesUi.dp(context, 18), NotesUi.dp(context, 120));
        previewView.setVisibility(View.GONE);
        previewPane = previewView;
        root.addView(previewView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildFormatBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statsView = NotesUi.label(context, "", 11.5f,
                NotesUi.withAlpha(NotesUi.onSurfaceVariant(context), 200));
        statsView.setGravity(Gravity.CENTER_VERTICAL);
        statsView.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 6),
                NotesUi.dp(context, 18), NotesUi.dp(context, 8));
        root.addView(statsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleField.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (applying) return;
                note.title = s.toString();
                changed();
            }
        });
        bodyField.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (applying) return;
                note.body = s.toString();
                changed();
            }
        });
        refreshStats();
        refreshPreview();
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = NotesUi.row(context);
        bar.setBackgroundColor(NotesUi.surface(context));
        bar.setPadding(NotesUi.dp(context, 6), NotesUi.dp(context, 6),
                NotesUi.dp(context, 6), NotesUi.dp(context, 6));

        bar.addView(iconButton("\u2190", () -> {
            commit();
            if (host != null) host.onClose();
        }));
        TextView label = NotesUi.label(context,
                note.locked ? "Locked" : (note.archived ? "Archived" : "Note"),
                15, NotesUi.onSurface(context));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        NotesUi.add(bar, label, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        pinButton = iconButton(note.pinned ? "\uD83D\uDCCF" : "\uD83D\uDCCC", () -> {
            note.pinned = !note.pinned;
            changed();
            refreshTopBar();
        });
        bar.addView(pinButton);
        bar.addView(iconButton("\u25CF", () -> {
            dialogs.colors("Note colour", note.color, index -> {
                note.color = index;
                store.touch(note);
            });
        }));
        bar.addView(iconButton("#", () -> {
            dialogs.tags("Add a tag", store.tags(), tag -> {
                note.addTag(tag);
                store.touch(note);
            });
        }));
        bar.addView(iconButton("\u2630", () -> showMenu()));
        return bar;
    }

    private void refreshTopBar() {
        if (pinButton == null) return;
        pinButton.setText(note.pinned ? "\uD83D\uDCCF" : "\uD83D\uDCCC");
        pinButton.setTextColor(note.pinned ? accent : NotesUi.onSurfaceVariant(context));
        syncFields();
    }

    private void syncFields() {
        if (titleField != null && !titleField.isFocused()) {
            applying = true;
            try {
                titleField.setText(note.title);
            } finally {
                applying = false;
            }
        }
        if (bodyField != null && !bodyField.isFocused()) {
            applying = true;
            try {
                bodyField.setText(note.plain());
            } finally {
                applying = false;
            }
        }
    }
    private MaterialButton iconButton(String glyph, Runnable action) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        b.setText(glyph);
        b.setTextSize(17);
        b.setTextColor(NotesUi.onSurfaceVariant(context));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(NotesUi.dp(context, 10), 0, NotesUi.dp(context, 10), 0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

private View buildFormatBar() {
        LinearLayout wrap = NotesUi.column(context);
        wrap.setBackgroundColor(NotesUi.surfaceLow(context));
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        formatBar = NotesUi.row(context);
        formatBar.setPadding(NotesUi.dp(context, 8), NotesUi.dp(context, 4),
                NotesUi.dp(context, 8), NotesUi.dp(context, 4));
        scroll.addView(formatBar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addFormat("\uD83D\uDCCC", "Heading", () -> wrapSelection("## "));
        addFormat("B", "Bold", () -> wrapSelection("**"));
        addFormat("I", "Italic", () -> wrapSelection("_"));
        addFormat("S", "Strikethrough", () -> wrapSelection("~~"));
        addFormat("`", "Code", () -> wrapSelection("`"));
        addFormat("\u2022", "Bullet list", () -> prefixLine("- "));
        addFormat("\u2610", "Checklist item", () -> prefixLine("- [ ] "));
        addFormat("\u2013", "Divider", () -> insert("\n---\n"));
        addFormat("\uD83D\uDCCE", "Date", this::insertDate);
        addFormat("\uD83D\uDCCC\uD83D\uDCCF", "List", this::toggleChecklist);
        addFormat("\uD83D\uDD0E", "Preview", () -> setPreviewMode(!previewMode));
        return wrap;
    }

    private void addFormat(String glyph, String hint, Runnable action) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(glyph);
        b.setTextSize(13);
        b.setContentDescription(hint);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(NotesUi.dp(context, 12), 0, NotesUi.dp(context, 12), 0);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, NotesUi.dp(context, 38));
        p.setMargins(NotesUi.dp(context, 3), 0, NotesUi.dp(context, 3), 0);
        formatBar.addView(b, p);
    }

    private void insertDate() {
        String stamp = NotesUi.date(context, System.currentTimeMillis(), false);
        insert("\n" + stamp + "\n");
    }

    private void toggleChecklist() {
        boolean show = checklistPane.getVisibility() != View.VISIBLE;
        checklistPane.setVisibility(show ? View.VISIBLE : View.GONE);
        if (bodyField.getVisibility() == View.VISIBLE && show) {
            bodyField.setVisibility(View.GONE);
            renderChecklist();
        } else {
            bodyField.setVisibility(View.VISIBLE);
        }
    }

private View buildChecklistPane() {
        LinearLayout pane = NotesUi.column(context);
        pane.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 6),
                NotesUi.dp(context, 18), NotesUi.dp(context, 6));
        checklistBox = NotesUi.column(context);
        pane.addView(checklistBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton add = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        add.setText("+  Add item");
        add.setTextSize(13);
        add.setOnClickListener(v -> {
            String body = note.plain();
            if (!body.isEmpty() && !body.endsWith("\n")) body += "\n";
            body += "- [ ] ";
            note.body = body;
            syncBody();
            renderChecklist();
        });
        pane.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return pane;
    }

    private void renderChecklist() {
        if (checklistBox == null) return;
        checklistBox.removeAllViews();
        String[] lines = note.plain().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!Markdown.isBullet(lines[i])) continue;
            final int index = i;
            String text = Markdown.plain(lines[i]);
            boolean done = Markdown.isDoneBullet(lines[i].trim());
            LinearLayout row = NotesUi.row(context);
            final android.widget.CheckBox box = new android.widget.CheckBox(context);
            box.setChecked(done);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
            box.setOnCheckedChangeListener((b, checked) -> {
                String[] current = note.plain().split("\n", -1);
                if (index < 0 || index >= current.length) return;
                if (!Markdown.isBullet(current[index])) return;
                current[index] = Markdown.toggleBullet(current[index], checked);
                note.body = String.join("\n", current);
                changed();
                syncBody();
            });
            row.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            EditText field = new EditText(context);
            field.setText(text);
            field.setTextSize(15 * settings.textScale());
            field.setTextColor(NotesUi.onSurface(context));
            field.setSingleLine(true);
            field.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            final boolean[] dirtyFlag = {false};
            field.addTextChangedListener(new SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (applying || dirtyFlag[0]) return;
                    dirtyFlag[0] = true;
                    String[] current = note.plain().split("\n", -1);
                    if (index >= 0 && index < current.length) {
                        current[index] = Markdown.toggleBullet(s.toString(), box.isChecked());
                        note.body = String.join("\n", current);
                        changed();
                        syncBody();
                    }
                    dirtyFlag[0] = false;
                }
            });
            NotesUi.add(row, field, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            checklistBox.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

private void wrapSelection(String marker) {
        if (bodyField == null) return;
        int start = bodyField.getSelectionStart();
        int end = bodyField.getSelectionEnd();
        Editable text = bodyField.getText();
        if (start < 0 || end < 0) return;
        String selected = text.subSequence(Math.min(start, end), Math.max(start, end)).toString();
        String replacement = marker + selected + marker;
        if (selected.isEmpty()) replacement = marker + marker;
        text.replace(Math.min(start, end), Math.max(start, end), replacement);
        if (selected.isEmpty()) {
            bodyField.setSelection(Math.min(start, end) + marker.length());
        }
    }

    private void prefixLine(String prefix) {
        if (bodyField == null) return;
        Editable text = bodyField.getText();
        int start = bodyField.getSelectionStart();
        int end = bodyField.getSelectionEnd();
        if (start < 0) return;
        int lineStart = start;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = Math.min(end, text.length());
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
        String line = text.subSequence(lineStart, lineEnd).toString();
        if (prefix.trim().equals("-") && line.startsWith("- ")) {
            text.replace(lineStart, lineEnd, line.substring(2));
            return;
        }
        text.replace(lineStart, lineEnd, prefix + line);
    }

    private void insert(String snippet) {
        if (bodyField == null) return;
        int pos = Math.max(0, bodyField.getSelectionStart());
        Editable text = bodyField.getText();
        text.insert(pos, snippet);
        bodyField.setSelection(Math.min(text.length(), pos + snippet.length()));
    }

    private void syncBody() {
        if (bodyField == null) return;
        applying = true;
        try {
            bodyField.setText(note.plain());
        } finally {
            applying = false;
        }
    }

private void changed() {
        dirty = true;
        refreshStats();
        refreshPreview();
        persist(true);
        if (host != null) host.onChanged(note);
    }

    private void persist(boolean light) {
        try {
            if (note.isBlank()) return;
            note.updated = System.currentTimeMillis();
            store.put(note);
            dirty = false;
        } catch (Exception ignored) {
        }
    }

    private void refreshStats() {
        if (statsView == null) return;
        if (!settings.showWordCount()) {
            statsView.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Markdown.words(note.plain())).append(" words  ·  ");
        sb.append(note.plain().length()).append(" characters");
        if (note.checklistTotal() > 0) {
            sb.append("  ·  ").append(note.checklistDone())
                    .append('/').append(note.checklistTotal()).append(" done");
        } else if (note.words() > 0) {
            sb.append("  ·  ").append(note.readMinutes()).append(" min read");
        }
        statsView.setText(sb.toString());
    }

    private void refreshPreview() {
        if (previewView == null || !previewMode) return;
        previewView.setText(Markdown.render(note.plain(), accent));
    }

    public void setPreviewMode(boolean on) {
        previewMode = on;
        if (previewView == null) return;
        previewView.setVisibility(on ? View.VISIBLE : View.GONE);
        bodyField.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) {
            checklistPane.setVisibility(View.GONE);
            refreshPreview();
        }
    }

    public boolean previewMode() {
        return previewMode;
    }

    public void showKeyboard() {
        if (bodyField == null || previewMode) return;
        try {
            bodyField.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(bodyField, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) {
        }
    }

private void showMenu() {
        if (host == null) return;
        final List<String> actions = new ArrayList<>();
        actions.add(note.archived ? "Unarchive" : "Archive");
        actions.add("Tags");
        actions.add(note.locked ? "Remove lock" : "Lock note");
        actions.add("Duplicate");
        actions.add(previewMode ? "Edit markdown" : "Preview");
        actions.add("Share");
        actions.add("Delete");
        new NotesDialogs(context, accent).items("Note options", actions, index -> {
            String action = actions.get(index);
            if (action.startsWith("Archive") || action.startsWith("Unarchive")) {
                note.archived = !note.archived;
                store.touch(note);
                host.onChanged(note);
                host.onClose();
            } else if (action.equals("Tags")) {
                dialogs.tags("Add a tag", store.tags(), tag -> {
                    note.addTag(tag);
                    store.touch(note);
                });
            } else if (action.startsWith("Lock") || action.startsWith("Remove lock")) {
                host.onRequestLock(note);
            } else if (action.equals("Duplicate")) {
                host.onRequestDuplicate(note);
            } else if (action.equals("Preview") || action.equals("Edit markdown")) {
                setPreviewMode(!previewMode);
                refreshTopBar();
            } else if (action.equals("Share")) {
                commit();
                host.onRequestShare(note);
            } else if (action.equals("Delete")) {
                commit();
                host.onRequestDelete(note);
            }
        }).show();
    }

    abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public abstract void afterTextChanged(Editable s);
    }
}

