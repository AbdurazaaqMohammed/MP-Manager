package io.github.abdurazaaqmohammed.packs.notes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

public class NotesTool extends BaseToolPlugin {

    private Context context;
    private NoteStore store;
    private NoteSettings settings;
    private NotesDialogs dialogs;
    private NoteAdapter adapter;
    private RecyclerView list;
    private View emptyState;
    private TextView countLabel;
    private LinearLayout chipRow;
    private ExtendedFloatingActionButton fab;
    private LinearLayout selectionBar;
    private TextView selectionLabel;
    private TextInputEditText searchField;
    private FrameLayout editorHolder;
    private NoteEditor editor;
    private FrameLayout root;
    private int filter = NoteStore.FILTER_ALL;
    private String activeTag;
    private String query = "";
    private int accent;
    private int requestCode = 4100;

    public NotesTool() {
        super("notes", "Notes", "Markdown notes, checklists, tags and backups",
                ToolCategories.GENERAL);
    }

    @Override
    public boolean fillViewport() {
        return true;
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        this.context = context;
        store = NoteStore.get(context);
        settings = NoteSettings.get(context);
        accent = settings.accent() >= 0 ? settings.accent() : NotesUi.primary(context);
        dialogs = new NotesDialogs(context, accent);
        if (settings.autoPurgeDays() > 0) {
            store.purgeOlderThan(settings.autoPurgeDays() * 24L * 60L * 60L * 1000L);
        }
        root = new FrameLayout(context);
        root.setBackgroundColor(NotesUi.surface(context));
        root.addView(buildListPage(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editorHolder = new FrameLayout(context);
        editorHolder.setVisibility(View.GONE);
        editorHolder.setBackgroundColor(NotesUi.surface(context));
        root.addView(editorHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (NoteCrypto.hasPin(context)) showLockScreen();
        refresh();
        return root;
    }

    private View buildListPage() {
        LinearLayout page = NotesUi.column(context);
        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout body = new FrameLayout(context);
        page.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new NoteAdapter(context, store, settings);
        adapter.setListener(new NoteAdapter.Listener() {
            @Override
            public void onOpen(Note note) {
                openNote(note);
            }

            @Override
            public void onLongPress(Note note) {
                adapter.toggleSelection(note);
            }

            @Override
            public void onPinToggle(Note note) {
                refresh();
            }

            @Override
            public void onSelectedChanged() {
                updateSelectionBar();
            }
        });
        list = new RecyclerView(context);
        list.setLayoutManager(settings.grid() ? new GridLayoutManager(context, 2)
                : new LinearLayoutManager(context));
        list.setPadding(NotesUi.dp(context, 14), NotesUi.dp(context, 4),
                NotesUi.dp(context, 14), NotesUi.dp(context, 96));
        list.setClipToPadding(false);
        list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = buildEmptyState();
        body.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fab = new ExtendedFloatingActionButton(context);
        fab.setText("New note");
        fab.setBackgroundTintList(ColorStateList.valueOf(accent));
        fab.setTextColor(NotesUi.onPrimary(context));
        fab.setOnClickListener(v -> createNote());
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.gravity = Gravity.BOTTOM | Gravity.END;
        fp.setMargins(0, 0, NotesUi.dp(context, 18), NotesUi.dp(context, 22));
        body.addView(fab, fp);

        selectionBar = buildSelectionBar();
        FrameLayout.LayoutParams sbp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbp.gravity = Gravity.BOTTOM;
        selectionBar.setVisibility(View.GONE);
        body.addView(selectionBar, sbp);
        return page;
    }

    private View buildHeader() {
        LinearLayout header = NotesUi.column(context);
        header.setPadding(NotesUi.dp(context, 16), NotesUi.dp(context, 10),
                NotesUi.dp(context, 16), NotesUi.dp(context, 2));

        LinearLayout titleRow = NotesUi.row(context);
        TextView title = NotesUi.label(context, "Notes", 26, NotesUi.onSurface(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        NotesUi.add(titleRow, title, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(iconButton("\u2261", "Sort and view", this::showSortMenu));
        titleRow.addView(iconButton("\u2699", "Settings", this::showSettings));
        header.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        countLabel = NotesUi.label(context, "", 12.5f,
                NotesUi.withAlpha(NotesUi.onSurfaceVariant(context), 220));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = NotesUi.dp(context, 2);
        header.addView(countLabel, cp);

        TextInputLayout searchBox = new TextInputLayout(context);
        searchBox.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        searchBox.setHint("Search notes, tags, text");
        searchBox.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        searchField = new TextInputEditText(searchBox.getContext());
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchBox.addView(searchField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchField.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                refresh();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = NotesUi.dp(context, 12);
        header.addView(searchBox, sp);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        chipRow = NotesUi.row(context);
        scroll.addView(chipRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildFilterChips();
        return header;
    }

    private void buildFilterChips() {
        chipRow.removeAllViews();
        String[] labels = {"Notes", "Pinned", "Tags", "Archived", "Trash"};
        int[] values = {NoteStore.FILTER_ALL, NoteStore.FILTER_PINNED, NoteStore.FILTER_TAGGED,
                NoteStore.FILTER_ARCHIVED, NoteStore.FILTER_TRASH};
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            final String label = labels[i];
            Chip chip = new Chip(context);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setTextSize(13);
            boolean on = filter == value;
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    on ? accent : NotesUi.surfaceHigh(context)));
            chip.setTextColor(on ? NotesUi.onPrimary(context) : NotesUi.onSurfaceVariant(context));
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(v -> {
                if (value == NoteStore.FILTER_TAGGED && store.tags().isEmpty()) {
                    toast("Tag a note to see it here");
                    return;
                }
                filter = value;
                activeTag = null;
                buildFilterChips();
                refresh();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, NotesUi.dp(context, 10), NotesUi.dp(context, 8), 0);
            chipRow.addView(chip, p);
        }
        if (filter != NoteStore.FILTER_TAGGED) return;
        List<String> tags = store.tags();
        if (activeTag == null && !tags.isEmpty()) activeTag = tags.get(0);
        for (String tag : tags) {
            Chip chip = new Chip(context);
            chip.setText("#" + tag);
            chip.setCheckable(true);
            chip.setChecked(tag.equals(activeTag));
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    tag.equals(activeTag) ? NotesUi.withAlpha(accent, 70)
                            : NotesUi.surfaceHigh(context)));
            chip.setTextColor(tag.equals(activeTag) ? NotesUi.onPrimary(context)
                    : NotesUi.onSurfaceVariant(context));
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(v -> {
                activeTag = tag;
                buildFilterChips();
                refresh();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, NotesUi.dp(context, 10), NotesUi.dp(context, 8), 0);
            chipRow.addView(chip, p);
        }
    }

    private View buildEmptyState() {
        LinearLayout box = NotesUi.column(context);
        box.setGravity(Gravity.CENTER);
        box.setPadding(NotesUi.dp(context, 40), NotesUi.dp(context, 40),
                NotesUi.dp(context, 40), NotesUi.dp(context, 80));
        TextView glyph = NotesUi.label(context, "\uD83D\uDCDD", 40, accent);
        glyph.setGravity(Gravity.CENTER);
        box.addView(glyph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView head = NotesUi.label(context, emptyTitle(), 18, NotesUi.onSurface(context));
        head.setGravity(Gravity.CENTER);
        head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = NotesUi.dp(context, 14);
        box.addView(head, hp);
        TextView sub = NotesUi.label(context, emptySubtitle(), 14,
                NotesUi.onSurfaceVariant(context));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subp.topMargin = NotesUi.dp(context, 6);
        box.addView(sub, subp);
        MaterialButton cta = new MaterialButton(context);
        cta.setText(emptyAction());
        cta.setOnClickListener(v -> {
            if (filter == NoteStore.FILTER_TRASH) {
                emptyTrash();
            } else {
                createNote();
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.topMargin = NotesUi.dp(context, 20);
        box.addView(cta, cp);
        return box;
    }

    private String emptyTitle() {
        if (!query.trim().isEmpty()) return "No matches";
        switch (filter) {
            case NoteStore.FILTER_PINNED:
                return "No pinned notes";
            case NoteStore.FILTER_TAGGED:
                return "No tagged notes";
            case NoteStore.FILTER_ARCHIVED:
                return "Archive is empty";
            case NoteStore.FILTER_TRASH:
                return "Trash is empty";
            default:
                return "Start writing";
        }
    }

    private String emptySubtitle() {
        if (!query.trim().isEmpty()) return "Try a different word or clear the search field.";
        switch (filter) {
            case NoteStore.FILTER_PINNED:
                return "Pin the notes you want at the top of the list.";
            case NoteStore.FILTER_TAGGED:
                return "Open a note and add tags from the toolbar.";
            case NoteStore.FILTER_ARCHIVED:
                return "Archived notes are hidden from your main list.";
            case NoteStore.FILTER_TRASH:
                return "Deleted notes wait here before they are removed forever.";
            default:
                return "Markdown, checklists, tags and colours are all supported.";
        }
    }

    private String emptyAction() {
        if (filter == NoteStore.FILTER_TRASH) return "Nothing to delete";
        return "Create a note";
    }

    private LinearLayout buildSelectionBar() {
        LinearLayout bar = NotesUi.row(context);
        bar.setBackgroundColor(NotesUi.surfaceHighest(context));
        bar.setPadding(NotesUi.dp(context, 8), NotesUi.dp(context, 6),
                NotesUi.dp(context, 8), NotesUi.dp(context, 6));
        selectionLabel = NotesUi.label(context, "", 14, NotesUi.onSurface(context));
        selectionLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        NotesUi.add(bar, selectionLabel, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(textButton("Tag", this::tagSelection));
        bar.addView(textButton("Pin", () -> {
            for (Note n : adapter.selectedItems()) n.pinned = true;
            store.save();
            afterBulk("Pinned");
        }));
        bar.addView(textButton("Delete", this::deleteSelection));
        bar.addView(textButton("\u2715", () -> adapter.clearSelection()));
        return bar;
    }

    private View textButton(String text, Runnable action) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setTextSize(13);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private View iconButton(String glyph, String hint, Runnable action) {
        MaterialButton b = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        b.setText(glyph);
        b.setTextSize(18);
        b.setContentDescription(hint);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(NotesUi.dp(context, 12), 0, NotesUi.dp(context, 12), 0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void updateSelectionBar() {
        if (selectionBar == null || fab == null) return;
        int count = adapter.selectedCount();
        selectionBar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        fab.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
        selectionLabel.setText(count + " selected");
    }

    private void refresh() {
        if (adapter == null) return;
        List<Note> notes = store.query(filter, activeTag, query, settings.sort(),
                settings.ascending());
        adapter.submit(notes);
        if (emptyState != null) {
            emptyState.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
            list.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (countLabel != null) countLabel.setText(summary(notes.size()));
    }

    private String summary(int shown) {
        StringBuilder sb = new StringBuilder();
        if (!query.trim().isEmpty()) {
            sb.append(shown).append(" result").append(shown == 1 ? "" : "s")
                    .append(" for \"").append(query.trim()).append("\"");
            return sb.toString();
        }
        switch (filter) {
            case NoteStore.FILTER_PINNED:
                sb.append(NotesUi.plural(shown, "pinned note", "pinned notes"));
                break;
            case NoteStore.FILTER_TAGGED:
                sb.append(activeTag == null ? "Tagged notes"
                        : "#" + activeTag + " \u00b7 " + NotesUi.plural(shown, "note", "notes"));
                break;
            case NoteStore.FILTER_ARCHIVED:
                sb.append(NotesUi.plural(shown, "archived note", "archived notes"));
                break;
            case NoteStore.FILTER_TRASH:
                sb.append(NotesUi.plural(shown, "note in trash", "notes in trash"));
                break;
            default:
                sb.append(NotesUi.plural(shown, "note", "notes"));
                sb.append(" \u00b7 ").append(NotesUi.plural(store.pinnedCount(), "pinned", "pinned"));
                break;
        }
        return sb.toString();
    }

    private void createNote() {
        Note note = store.create();
        refresh();
        openNote(note);
    }

    private void openNote(Note note) {
        if (note == null) return;
        if (note.locked && !sessionUnlocked(note)) {
            toast("Locked note");
            return;
        }
        editor = new NoteEditor(context, note, store, settings, new EditorHost());
        editorHolder.removeAllViews();
        editorHolder.addView(editor.build(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editorHolder.setVisibility(View.VISIBLE);
        editor.showKeyboard();
    }

    private boolean sessionUnlocked(Note note) {
        if (!note.locked) return true;
        dialogs.pin("Unlock note", "Enter your notes PIN to read this note.", pin -> {
            if (NoteCrypto.verifyPin(context, pin)) {
                unlockInto(note);
            } else {
                toast("Wrong PIN");
            }
        });
        return false;
    }

    private void unlockInto(Note note) {
        String plain = NoteCrypto.decrypt(context, sessionPin, note.cipher);
        if (plain == null) {
            toast("Could not decrypt");
            return;
        }
        note.body = plain;
        note.cipher = "";
        refresh();
        openNote(note);
    }

    private String sessionPin = "";

    private void closeEditor() {
        if (editor != null) editor.commit();
        editor = null;
        editorHolder.removeAllViews();
        editorHolder.setVisibility(View.GONE);
        refresh();
    }

    private void afterBulk(String message) {
        adapter.clearSelection();
        refresh();
        snack(message);
    }

    private void toast(String message) {
        try {
            Toast.makeText(context, message,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void snack(String message) {
        try {
            if (root != null) Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void snackWithUndo(String message, Runnable undo) {
        try {
            Snackbar bar = Snackbar.make(root, message, Snackbar.LENGTH_LONG);
            bar.setAction("Undo", v -> undo.run());
            bar.show();
        } catch (Exception ignored) {
        }
    }

    private class EditorHost implements NoteEditor.Host {

        @Override
        public void onClose() {
            closeEditor();
        }

        @Override
        public void onChanged(Note note) {
            if (countLabel != null) countLabel.setText(summary(adapter.getItemCount()));
        }

        @Override
        public void onRequestShare(Note note) {
            shareNote(note);
        }

        @Override
        public void onRequestDelete(Note note) {
            deleteNote(note);
        }

        @Override
        public void onRequestLock(Note note) {
            toggleLock(note);
        }

        @Override
        public void onRequestDuplicate(Note note) {
            Note copy = note.copy();
            store.put(copy);
            closeEditor();
            snack("Duplicated");
        }
    }

    private void toggleLock(Note note) {
        if (note.locked) {
            if (sessionPin.isEmpty()) {
                toast("Unlock the app first");
                return;
            }
            String cipher = NoteCrypto.encrypt(context, sessionPin, note.plain());
            if (cipher == null) {
                toast("Could not lock note");
                return;
            }
            note.cipher = cipher;
            note.body = "";
            note.locked = true;
            store.touch(note);
            closeEditor();
            snack("Note locked");
            return;
        }
        if (!NoteCrypto.hasPin(context)) {
            dialogs.pin("Create a notes PIN", "Choose a 4 digit PIN. It protects locked notes.",
                    pin -> {
                        if (pin.length() < 4) {
                            toast("Use at least 4 digits");
                            return;
                        }
                        NoteCrypto.setPin(context, pin);
                        sessionPin = pin;
                        toggleLock(note);
                    });
            return;
        }
        dialogs.pin("Enter notes PIN", "Confirm your PIN to lock this note.", pin -> {
            if (!NoteCrypto.verifyPin(context, pin)) {
                toast("Wrong PIN");
                return;
            }
            sessionPin = pin;
            toggleLock(note);
        });
    }

    private void showLockScreen() {
        sessionPin = "";
        dialogs.pin("Notes locked", "Enter your notes PIN.", pin -> {
            if (NoteCrypto.verifyPin(context, pin)) {
                sessionPin = pin;
                refresh();
            } else {
                toast("Wrong PIN");
                showLockScreen();
            }
        });
    }

    private void deleteNote(Note note) {
        if (note.trashed) {
            store.delete(note);
            closeEditor();
            snackWithUndo("Deleted permanently", () -> store.put(note));
            return;
        }
        if (settings.confirmDelete()) {
            dialogs.confirm("Delete note", "This moves the note to the trash where it can be restored.",
                    "Move to trash", () -> {
                        store.trash(note);
                        closeEditor();
                        snackWithUndo("Moved to trash", () -> store.restore(note));
                    });
            return;
        }
        store.trash(note);
        closeEditor();
        snackWithUndo("Moved to trash", () -> store.restore(note));
    }

    private void deleteSelection() {
        List<Note> chosen = adapter.selectedItems();
        if (chosen.isEmpty()) return;
        Runnable run = () -> {
            for (Note n : chosen) store.trash(n);
            afterBulk(NotesUi.plural(chosen.size(), "note", "notes") + " moved to trash");
        };
        if (settings.confirmDelete()) {
            dialogs.confirm("Move to trash",
                    NotesUi.plural(chosen.size(), "note", "notes") + " will be moved to the trash.",
                    "Move", run);
            return;
        }
        run.run();
    }

    private void tagSelection() {
        List<Note> chosen = adapter.selectedItems();
        if (chosen.isEmpty()) return;
        dialogs.tags("Add a tag", store.tags(), tag -> {
            for (Note n : chosen) n.addTag(tag);
            afterBulk("Tagged " + NotesUi.plural(chosen.size(), "note", "notes"));
        });
    }

    private void emptyTrash() {
        if (store.trashCount() == 0) {
            toast("Trash is already empty");
            return;
        }
        dialogs.confirm("Empty trash", "All " + store.trashCount()
                + " notes in the trash will be deleted forever.", "Delete all", () -> {
                    store.emptyTrash();
                    refresh();
                    snack("Trash emptied");
                });
    }

    private void shareNote(Note note) {
        try {
            StringBuilder sb = new StringBuilder();
            String title = note.displayTitle();
            if (!title.isEmpty()) sb.append(title).append("\n\n");
            sb.append(Markdown.plainText(note.plain()));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, title);
            send.putExtra(Intent.EXTRA_TEXT, sb.toString());
            context.startActivity(Intent.createChooser(send, "Share note"));
        } catch (Exception e) {
            toast("Nothing to share");
        }
    }

    private void showSortMenu() {
        List<String> options = new ArrayList<>();
        String[] names = {"Last edited", "Date created", "Title"};
        for (int i = 0; i < names.length; i++) {
            options.add(names[i] + (settings.sort() == i ? "  \u2713" : ""));
        }
        options.add(settings.ascending() ? "Ascending  \u2713" : "Descending");
        options.add(settings.grid() ? "Grid layout  \u2713" : "List layout");
        options.add("Statistics");
        options.add("Export all as Markdown");
        options.add("Export backup (JSON)");
        options.add("Import backup");
        options.add("Empty trash");
        dialogs.items("Sort and view", options, index -> {
            if (index < 3) {
                settings.setSort(index);
                refresh();
            } else if (index == 3) {
                settings.setAscending(!settings.ascending());
                refresh();
            } else if (index == 4) {
                settings.setGrid(!settings.grid());
                rebuildList();
            } else if (index == 5) {
                showStats();
            } else if (index == 6) {
                exportMarkdown();
            } else if (index == 7) {
                exportBackup();
            } else if (index == 8) {
                importBackup();
            } else {
                emptyTrash();
            }
        });
    }

    private void rebuildList() {
        if (root == null) return;
        root.removeAllViews();
        root.addView(buildListPage(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editorHolder = new FrameLayout(context);
        editorHolder.setVisibility(View.GONE);
        editorHolder.setBackgroundColor(NotesUi.surface(context));
        root.addView(editorHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    private void showStats() {
        LinearLayout box = NotesUi.column(context);
        box.setPadding(NotesUi.dp(context, 24), NotesUi.dp(context, 18),
                NotesUi.dp(context, 24), NotesUi.dp(context, 18));
        statRow(box, "Notes", String.valueOf(store.allCount() + store.archivedCount()));
        statRow(box, "Pinned", String.valueOf(store.pinnedCount()));
        statRow(box, "Archived", String.valueOf(store.archivedCount()));
        statRow(box, "In trash", String.valueOf(store.trashCount()));
        statRow(box, "Words", String.valueOf(store.totalWords()));
        statRow(box, "Characters", String.valueOf(store.totalCharacters()));
        int items = store.totalChecklistItems();
        if (items > 0) {
            statRow(box, "Checklist items", store.totalChecklistDone() + " / " + items);
        }
        statRow(box, "Created this week", String.valueOf(store.createdThisWeek()));
        Note oldest = store.oldest();
        if (oldest != null) {
            statRow(box, "Oldest note", NotesUi.date(context, oldest.created, false));
        }
        Note longest = store.longest();
        if (longest != null && !longest.displayTitle().isEmpty()) {
            statRow(box, "Longest note", longest.displayTitle());
        }
        List<String> tags = store.tags();
        if (!tags.isEmpty()) {
            sectionTitle(box, "Tags");
            for (String tag : tags) {
                statRow(box, "#" + tag, String.valueOf(store.countForTag(tag)));
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context);
        builder.setTitle("Statistics");
        builder.setView(scroll);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void statRow(LinearLayout box, String label, String value) {
        LinearLayout row = NotesUi.row(context);
        TextView left = NotesUi.label(context, label, 14, NotesUi.onSurfaceVariant(context));
        NotesUi.add(row, left, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView right = NotesUi.label(context, value, 14, NotesUi.onSurface(context));
        right.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        NotesUi.add(row, right, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = NotesUi.dp(context, 8);
        box.addView(row, p);
    }

    private void sectionTitle(LinearLayout box, String text) {
        TextView title = NotesUi.label(context, text, 13, accent);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = NotesUi.dp(context, 20);
        box.addView(title, p);
    }

    private void showSettings() {
        List<String> options = new ArrayList<>();
        String[] names = {"Theme", "Font", "Text size", "Line spacing", "Preview lines",
                "Accent colour", "Sort order", "Layout", "Timestamps", "Relative dates",
                "Word count", "Confirm delete", "Auto delete trash", "Markdown shortcuts",
                "Change PIN", "Remove PIN"};
        for (String name : names) options.add(name);
        dialogs.items("Settings", options, index -> onSetting(index));
    }

    private void onSetting(int index) {
        switch (index) {
            case 0:
                pickTheme();
                break;
            case 1:
                pickFont();
                break;
            case 2:
                pickTextScale();
                break;
            case 3:
                pickLineSpacing();
                break;
            case 4:
                pickPreviewLines();
                break;
            case 5:
                pickAccent();
                break;
            case 6:
                pickSort();
                break;
            case 7:
                settings.setGrid(!settings.grid());
                rebuildList();
                break;
            case 8:
                settings.setShowTimestamps(!settings.showTimestamps());
                refresh();
                break;
            case 9:
                settings.setRelativeDates(!settings.relativeDates());
                refresh();
                break;
            case 10:
                settings.setShowWordCount(!settings.showWordCount());
                break;
            case 11:
                settings.setConfirmDelete(!settings.confirmDelete());
                break;
            case 12:
                pickAutoPurge();
                break;
            case 13:
                settings.setMarkdownShortcuts(!settings.markdownShortcuts());
                break;
            case 14:
                dialogs.pin("Change PIN", "Enter a new 4 digit PIN.", pin -> {
                    if (pin.length() < 4) {
                        toast("Use at least 4 digits");
                        return;
                    }
                    NoteCrypto.setPin(context, pin);
                    sessionPin = pin;
                    toast("PIN updated");
                });
                break;
            default:
                if (!NoteCrypto.hasPin(context)) {
                    toast("No PIN set");
                    return;
                }
                dialogs.confirm("Remove PIN", "Locked notes will be decrypted and unlocked.",
                        "Remove", () -> {
                            for (Note n : store.all()) {
                                if (!n.locked) continue;
                                String plain = NoteCrypto.decrypt(context, sessionPin, n.cipher);
                                if (plain != null) {
                                    n.body = plain;
                                    n.cipher = "";
                                    n.locked = false;
                                }
                            }
                            NoteCrypto.clearPin(context);
                            sessionPin = "";
                            store.save();
                            refresh();
                            snack("PIN removed");
                        });
                break;
        }
    }

    private void pickTheme() {
        List<String> options = new ArrayList<>();
        String[] names = {"Follow app theme", "Light", "Dark"};
        for (int i = 0; i < names.length; i++) {
            options.add(names[i] + (settings.theme() == i ? "  \u2713" : ""));
        }
        dialogs.items("Theme", options, index -> {
            settings.setTheme(index);
            if (index == NoteSettings.THEME_LIGHT || index == NoteSettings.THEME_DARK) {
                try {
                    AppCompatDelegate.setDefaultNightMode(
                            index == NoteSettings.THEME_DARK
                                    ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_NO);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void pickFont() {
        List<String> options = new ArrayList<>();
        for (int i = 0; i < NoteSettings.FONT_NAMES.length; i++) {
            options.add(NoteSettings.FONT_NAMES[i] + (settings.font() == i ? "  \u2713" : ""));
        }
        dialogs.items("Font", options, index -> {
            settings.setFont(index);
            rebuildList();
        });
    }

    private void pickTextScale() {
        List<String> options = new ArrayList<>();
        float[] steps = {0.9f, 1f, 1.1f, 1.2f, 1.35f, 1.5f};
        for (float step : steps) {
            options.add(Math.round(step * 100) + "%"
                    + (Math.abs(settings.textScale() - step) < 0.01f ? "  \u2713" : ""));
        }
        dialogs.items("Text size", options, index -> {
            settings.setTextScale(steps[index]);
            rebuildList();
        });
    }

    private void pickLineSpacing() {
        List<String> options = new ArrayList<>();
        float[] steps = {1f, 1.2f, 1.35f, 1.5f, 1.8f, 2.1f};
        for (float step : steps) {
            options.add(String.valueOf(step) + "\u00d7"
                    + (Math.abs(settings.lineSpacing() - step) < 0.01f ? "  \u2713" : ""));
        }
        dialogs.items("Line spacing", options, index -> {
            settings.setLineSpacing(steps[index]);
            rebuildList();
        });
    }

    private void pickPreviewLines() {
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            options.add(i + (i == 1 ? " line" : " lines")
                    + (settings.previewLines() == i ? "  \u2713" : ""));
        }
        dialogs.items("Preview lines", options, index -> {
            settings.setPreviewLines(index + 1);
            refresh();
        });
    }

    private void pickSort() {
        List<String> options = new ArrayList<>();
        String[] names = {"Last edited", "Date created", "Title"};
        for (int i = 0; i < names.length; i++) {
            options.add(names[i] + (settings.sort() == i ? "  \u2713" : ""));
        }
        options.add(settings.ascending() ? "Ascending  \u2713" : "Descending");
        dialogs.items("Sort order", options, index -> {
            if (index < 3) settings.setSort(index);
            else settings.setAscending(!settings.ascending());
            refresh();
        });
    }

    private void pickAutoPurge() {
        List<String> options = new ArrayList<>();
        int[] days = {0, 7, 14, 30, 60, 90, 365};
        for (int day : days) {
            options.add(day == 0 ? "Never" : day + " days"
                    + (settings.autoPurgeDays() == day ? "  \u2713" : ""));
        }
        dialogs.items("Delete trashed notes after", options, index -> {
            settings.setAutoPurgeDays(days[index]);
            toast("Saved");
        });
    }

    private void pickAccent() {
        LinearLayout grid = NotesUi.column(context);
        grid.setPadding(NotesUi.dp(context, 22), NotesUi.dp(context, 8),
                NotesUi.dp(context, 22), NotesUi.dp(context, 8));
        LinearLayout rowA = NotesUi.row(context);
        LinearLayout rowB = NotesUi.row(context);
        grid.addView(rowA, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.addView(rowB, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context);
        AlertDialog dialog = builder.setTitle("Accent colour")
                .setView(grid).setNegativeButton("App default", (d, w) -> {
                    settings.setAccent(-1);
                    applyAccent();
                }).setPositiveButton("Close", null).create();
        for (int i = 0; i < NotesUi.ACCENTS.length; i++) {
            final int index = i;
            View swatch = new View(context);
            int size = NotesUi.dp(context, 46);
            boolean on = settings.accent() == NotesUi.ACCENTS[i];
            swatch.setBackground(NotesUi.stroke(NotesUi.ACCENTS[i],
                    on ? NotesUi.onSurface(context) : 0, NotesUi.dp(context, 14),
                    on ? NotesUi.dp(context, 3) : 0));
            swatch.setOnClickListener(v -> {
                settings.setAccent(NotesUi.ACCENTS[index]);
                applyAccent();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.setMargins(NotesUi.dp(context, 6), NotesUi.dp(context, 6),
                    NotesUi.dp(context, 6), NotesUi.dp(context, 6));
            (i < 4 ? rowA : rowB).addView(swatch, p);
        }
        dialog.show();
    }

    private void applyAccent() {
        accent = settings.accent() >= 0 ? settings.accent() : NotesUi.primary(context);
        dialogs = new NotesDialogs(context, accent);
        rebuildList();
    }

    private File writeExport(String name, String content) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(),
                    Environment.DIRECTORY_DOCUMENTS);
            File notesDir = new File(dir, "Notes");
            if (!notesDir.exists() && !notesDir.mkdirs()) return null;
            File out = new File(notesDir, name);
            FileOutputStream stream = new FileOutputStream(out);
            OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8");
            writer.write(content);
            writer.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void offerFile(File file, String mime, String title) {
        if (file == null) {
            dialogs.message(title, "Could not write the file. Check storage permission.");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider", file);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(view, "Open " + file.getName()));
        } catch (Exception e) {
            toast("Saved to " + file.getAbsolutePath());
        }
    }

    private void exportMarkdown() {
        String stamp = String.valueOf(System.currentTimeMillis());
        File file = writeExport("notes_" + stamp + ".md", store.exportAllMarkdown());
        offerFile(file, "text/markdown", "Export failed");
    }

    private void exportBackup() {
        String stamp = String.valueOf(System.currentTimeMillis());
        File file = writeExport("notes_backup_" + stamp + ".json", store.exportJson());
        offerFile(file, "application/json", "Export failed");
    }

    private void importBackup() {
        try {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("*/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(pick, requestCode);
            } else {
                toast("Open the notes tool first");
            }
        } catch (Exception e) {
            toast("No file picker available");
        }
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        if (reqCode != requestCode || data == null || data.getData() == null) return;
        try {
            InputStream in = context.getContentResolver()
                    .openInputStream(data.getData());
            if (in == null) return;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            in.close();
            String text = buffer.toString("UTF-8");
            boolean json = text.trim().startsWith("{") || text.trim().startsWith("[");
            dialogs.items("Import notes", Arrays.asList(
                    json ? "Merge into current notes" : "Add as a new note",
                    json ? "Replace all notes" : "Add as a new note"), index -> {
                        if (json) {
                            int added = store.importJson(text, index == 1);
                            refresh();
                            snack("Imported " + NotesUi.plural(added, "note", "notes"));
                        } else {
                            Note note = store.importText(text, "Imported note");
                            refresh();
                            if (note != null) openNote(note);
                        }
                    });
        } catch (Exception e) {
            toast("Could not read that file");
        }
    }

    @Override
    public void onDestroy() {
        if (editor != null) editor.commit();
        editor = null;
    }

    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public abstract void afterTextChanged(Editable s);
    }
}
