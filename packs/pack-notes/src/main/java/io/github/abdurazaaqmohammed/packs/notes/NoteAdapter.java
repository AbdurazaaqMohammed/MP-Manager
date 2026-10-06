package io.github.abdurazaaqmohammed.packs.notes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.Holder> {

    public interface Listener {
        void onOpen(Note note);

        void onLongPress(Note note);

        void onPinToggle(Note note);

        void onSelectedChanged();
    }

    private final Context context;
    private final NoteStore store;
    private final NoteSettings settings;
    private final List<Note> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private Listener listener;
    private boolean selectionMode;
    private int accent;

    public NoteAdapter(Context context, NoteStore store, NoteSettings settings) {
        this.context = context;
        this.store = store;
        this.settings = settings;
        this.accent = settings.accent() >= 0 ? settings.accent() : NotesUi.primary(context);
    }

    public int accent() {
        return accent;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void submit(List<Note> notes) {
        items.clear();
        if (notes != null) items.addAll(notes);
        Set<String> ids = new HashSet<>();
        for (Note n : items) ids.add(n.id);
        selected.retainAll(ids);
        notifyDataSetChanged();
    }

    public List<Note> items() {
        return new ArrayList<>(items);
    }

    public boolean selectionMode() {
        return selectionMode;
    }

    public int selectedCount() {
        return selected.size();
    }

    public void clearSelection() {
        selected.clear();
        selectionMode = false;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectedChanged();
    }

    public void toggleSelection(Note note) {
        if (!selected.remove(note.id)) selected.add(note.id);
        selectionMode = !selected.isEmpty();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectedChanged();
    }

    public List<Note> selectedItems() {
        List<Note> out = new ArrayList<>();
        for (Note n : items) {
            if (selected.contains(n.id)) out.add(n);
        }
        return out;
    }

    public void selectAll() {
        for (Note n : items) selected.add(n.id);
        selectionMode = true;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectedChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(buildCard(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Note note = items.get(position);
        holder.bind(note, settings, accent, selectionMode, selected.contains(note.id));
        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(note);
            } else if (listener != null) {
                listener.onOpen(note);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(note);
            return true;
        });
        holder.pin.setOnClickListener(v -> {

note.pinned = !note.pinned;
            store.touch(note);
            if (listener != null) listener.onPinToggle(note);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private View buildCard(Context ctx) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(NotesUi.dp(ctx, 18));
        card.setCardElevation(0f);
        card.setCardBackgroundColor(NotesUi.surfaceLow(ctx));
        card.setStrokeWidth(NotesUi.dp(ctx, 1));
        card.setStrokeColor(NotesUi.withAlpha(NotesUi.outline(ctx), 60));
        card.setUseCompatPadding(false);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout frame = NotesUi.row(ctx);
        frame.setGravity(Gravity.CENTER_VERTICAL);

        View stripe = new View(ctx);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                NotesUi.dp(ctx, 4), ViewGroup.LayoutParams.MATCH_PARENT);
        sp.setMargins(NotesUi.dp(ctx, 14), NotesUi.dp(ctx, 4), NotesUi.dp(ctx, 10), NotesUi.dp(ctx, 4));
        frame.addView(stripe, sp);

        LinearLayout root = NotesUi.column(ctx);
        root.setPadding(0, NotesUi.dp(ctx, 12), NotesUi.dp(ctx, 14), NotesUi.dp(ctx, 12));
        frame.addView(root, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(frame, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = NotesUi.row(ctx);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CheckBox pin = new CheckBox(ctx);
        pin.setButtonTintList(ColorStateList.valueOf(accent));
        pin.setClickable(true);
        pin.setFocusable(false);
        head.addView(pin, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = NotesUi.label(ctx, "", 16, NotesUi.onSurface(ctx));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        NotesUi.add(head, title, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView badge = NotesUi.label(ctx, "", 11,
                NotesUi.withAlpha(NotesUi.onSurfaceVariant(ctx), 200));
        NotesUi.add(head, badge, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f);

TextView preview = NotesUi.label(ctx, "", 13.5f, NotesUi.onSurfaceVariant(ctx));
        preview.setMaxLines(settings.previewLines());
        preview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = NotesUi.dp(ctx, 4);
        root.addView(preview, pp);

        LinearProgressIndicator progress = new LinearProgressIndicator(ctx);
        progress.setTrackThickness(NotesUi.dp(ctx, 3));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = NotesUi.dp(ctx, 8);
        root.addView(progress, lp);

        LinearLayout meta = NotesUi.row(ctx);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = NotesUi.dp(ctx, 8);
        root.addView(meta, mp);

        TextView tags = NotesUi.label(ctx, "", 11.5f, accent);
        NotesUi.add(meta, tags, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView stamp = NotesUi.label(ctx, "", 11.5f,
                NotesUi.withAlpha(NotesUi.onSurfaceVariant(ctx), 190));
        NotesUi.add(meta, stamp, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f);
        return card;
    }

static class Holder extends RecyclerView.ViewHolder {
        final CheckBox pin;
        final TextView title;
        final TextView preview;
        final TextView badge;
        final TextView tags;
        final TextView stamp;
        final View stripe;
        final LinearProgressIndicator progress;
        final MaterialCardView card;

        Holder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            LinearLayout frame = (LinearLayout) card.getChildAt(0);
            stripe = frame.getChildAt(0);
            LinearLayout root = (LinearLayout) frame.getChildAt(1);
            LinearLayout head = (LinearLayout) root.getChildAt(0);
            pin = (CheckBox) head.getChildAt(0);
            title = (TextView) head.getChildAt(1);
            badge = (TextView) head.getChildAt(2);
            preview = (TextView) root.getChildAt(1);
            progress = (LinearProgressIndicator) root.getChildAt(2);
            LinearLayout meta = (LinearLayout) root.getChildAt(3);
            tags = (TextView) meta.getChildAt(0);
            stamp = (TextView) meta.getChildAt(1);
        }

        void bind(Note note, NoteSettings settings, int accent, boolean selectionMode,
                  boolean isSelected) {
            Context ctx = itemView.getContext();
            pin.setChecked(note.pinned);
            pin.setVisibility(note.pinned || selectionMode ? View.VISIBLE : View.INVISIBLE);
            title.setText(note.displayTitle().isEmpty() ? "Untitled note" : note.displayTitle());
            title.setTextColor(isSelected ? accent : NotesUi.onSurface(ctx));
            preview.setText(note.preview(settings.previewLines()));
            preview.setVisibility(preview.getText().length() == 0 ? View.GONE : View.VISIBLE);
            int total = note.checklistTotal();
            if (total > 0 && !note.locked) {
                progress.setVisibility(View.VISIBLE);
                progress.setIndicatorColor(accent);
                progress.setTrackColor(NotesUi.withAlpha(accent, 40));
                progress.setMax(total);
                progress.setProgress(note.checklistDone());
                badge.setText(note.checklistDone() + "/" + total);
                badge.setTextColor(accent);
            } else {
                progress.setVisibility(View.GONE);
                badge.setText(note.locked ? "LOCKED" : "");
                badge.setTextColor(NotesUi.withAlpha(NotesUi.onSurfaceVariant(ctx), 200));
            }
            StringBuilder tagText = new StringBuilder();
            if (note.tags != null) {
                for (String t : note.tags) {
                    if (t == null || t.trim().isEmpty()) continue;
                    if (tagText.length() > 0) tagText.append("  ");
                    tagText.append('#').append(t.trim());
                }
            }
            tags.setText(tagText.toString());
            tags.setVisibility(tagText.length() == 0 ? View.GONE : View.VISIBLE);
            stamp.setVisibility(settings.showTimestamps() ? View.VISIBLE : View.GONE);
            stamp.setText(NotesUi.date(ctx, note.updated, settings.relativeDates()));
            if (note.color >= 0 && note.color < NotesUi.NOTE_COLORS.length) {
                stripe.setBackground(NotesUi.round(NotesUi.NOTE_COLORS[note.color],
                        NotesUi.dp(ctx, 2)));
                stripe.setVisibility(View.VISIBLE);
            } else {
                stripe.setVisibility(View.INVISIBLE);
            }
            card.setStrokeWidth(NotesUi.dp(ctx, isSelected ? 2 : 1));
            card.setStrokeColor(isSelected ? accent
                    : NotesUi.withAlpha(NotesUi.outline(ctx), 60));
            card.setCardBackgroundColor(isSelected
                    ? NotesUi.withAlpha(accent, 26) : NotesUi.surfaceLow(ctx));
        }
    }
}

