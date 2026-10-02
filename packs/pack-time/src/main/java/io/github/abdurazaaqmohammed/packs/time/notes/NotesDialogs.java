package io.github.abdurazaaqmohammed.packs.time.notes;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class NotesDialogs {

    public interface OnItem {
        void pick(int index);
    }

    public interface OnText {
        void accept(String value);
    }

    public interface OnPin {
        void accept(String pin);
    }

    public interface OnColor {
        void accept(int index);
    }

    private final Context context;
    private final int accent;

    public NotesDialogs(Context context, int accent) {
        this.context = context;
        this.accent = accent;
    }

    public androidx.appcompat.app.AlertDialog items(String title, List<String> options,
                                                 final OnItem onItem) {
        String[] arr = options.toArray(new String[0]);
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(arr, (dialog, which) -> onItem.pick(which))
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void message(String title, String body) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show();
    }

    public void confirm(String title, String body, String positive, final Runnable onYes) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }

    public void prompt(String title, String hint, String initial, final OnText onText) {
        final EditText field = new EditText(context);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setText(initial == null ? "" : initial);
        LinearLayout box = NotesUi.column(context);
        box.setPadding(NotesUi.dp(context, 22), NotesUi.dp(context, 8), NotesUi.dp(context, 22), 0);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> onText.accept(field.getText().toString()))
                .show();
    }

    public void pin(String title, String message, final OnPin onPin) {
        final EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setMaxLines(1);
        field.setGravity(Gravity.CENTER);
        field.setHint("\u2022\u2022\u2022\u2022");
        field.setTextSize(22);
        field.setBackground(NotesUi.stroke(NotesUi.surfaceHigh(context),
                NotesUi.withAlpha(accent, 120), NotesUi.dp(context, 14), NotesUi.dp(context, 1)));
        LinearLayout box = NotesUi.column(context);
        box.setPadding(NotesUi.dp(context, 22), NotesUi.dp(context, 12),
                NotesUi.dp(context, 22), 0);
        if (message != null && !message.isEmpty()) {
            box.addView(NotesUi.label(context, message, 13,
                            NotesUi.withAlpha(NotesUi.onSurfaceVariant(context), 220)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> onPin.accept(field.getText().toString()))
                .show();
    }

public void colors(String title, int selected, final OnColor onColor) {
        LinearLayout grid = NotesUi.column(context);
        grid.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 8),
                NotesUi.dp(context, 18), NotesUi.dp(context, 8));
        LinearLayout rowA = NotesUi.row(context);
        LinearLayout rowB = NotesUi.row(context);
        grid.addView(rowA, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.addView(rowB, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(title)
                        .setView(grid)
                        .setNegativeButton("No colour", (d, w) -> onColor.accept(-1))
                        .setPositiveButton("Done", null)
                        .create();
        for (int i = 0; i < NotesUi.NOTE_COLORS.length; i++) {
            final int index = i;
            View swatch = new View(context);
            int size = NotesUi.dp(context, 44);
            int stroke = i == selected ? NotesUi.onSurface(context) : 0;
            int strokeWidth = i == selected ? NotesUi.dp(context, 3) : 0;
            swatch.setBackground(NotesUi.stroke(NotesUi.NOTE_COLORS[i], stroke,
                    NotesUi.dp(context, 14), strokeWidth));
            swatch.setOnClickListener(v -> {
                onColor.accept(index);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.setMargins(NotesUi.dp(context, 6), NotesUi.dp(context, 6),
                    NotesUi.dp(context, 6), NotesUi.dp(context, 6));
            (i < 5 ? rowA : rowB).addView(swatch, p);
        }
        dialog.show();
    }

    public void tags(String title, List<String> available, final OnTags onTags) {
        LinearLayout box = NotesUi.column(context);
        box.setPadding(NotesUi.dp(context, 18), NotesUi.dp(context, 6),
                NotesUi.dp(context, 18), NotesUi.dp(context, 6));
        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(title)
                        .setView(box)
                        .setNeutralButton("New tag", null)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Done", null)
                        .create();
        for (String tag : available) {
            TextView chip = NotesUi.label(context, "#" + tag, 14, accent);
            chip.setPadding(NotesUi.dp(context, 14), NotesUi.dp(context, 10),
                    NotesUi.dp(context, 14), NotesUi.dp(context, 10));
            chip.setBackground(NotesUi.round(NotesUi.withAlpha(accent, 30),
                    NotesUi.dp(context, 20)));
            chip.setOnClickListener(v -> {
                onTags.accept(tag);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = NotesUi.dp(context, 6);
            box.addView(chip, p);
        }
        dialog.setOnShowListener(d -> dialog.getButton(
                        androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> prompt("New tag", "Tag name", "", value -> {
                    onTags.accept(value);
                    dialog.dismiss();
                })));
        dialog.show();
    }

    public interface OnTags {
        void accept(String tag);
    }
}
