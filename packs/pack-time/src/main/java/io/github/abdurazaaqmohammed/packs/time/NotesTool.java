package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Extraction of ToolRunnerActivity.buildNotes().
 */
public class NotesTool extends BaseToolPlugin {

    public NotesTool() {
        super("notes", "Quick Notes", "Write and save notes", ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Quick Notes");
        Gson gson = new Gson();
        String prefsKey = "quick_notes_json";
        List<String> notes = new ArrayList<>();
        try {
            String saved = context.getSharedPreferences("tools", Context.MODE_PRIVATE)
                    .getString(prefsKey, "[]");
            List<String> loaded = gson.fromJson(saved, new TypeToken<List<String>>() {
            }.getType());
            if (loaded != null) notes.addAll(loaded);
        } catch (Exception ignored) {
        }
        EditText input = ToolViewFactory.makeInput(box, "Write a note",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        ListView listView = new ListView(context);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, notes);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 260)));
        Runnable persist = () -> {
            try {
                context.getSharedPreferences("tools", Context.MODE_PRIVATE)
                        .edit().putString(prefsKey, gson.toJson(notes)).apply();
            } catch (Exception ignored) {
            }
        };
        MaterialButton addBtn = ToolViewFactory.makeButton(box, "Save note");
        addBtn.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) {
                ToolViewFactory.toast(context, "Write something first");
                return;
            }
            notes.add(0, t);
            input.setText("");
            adapter.notifyDataSetChanged();
            persist.run();
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            notes.remove(position);
            adapter.notifyDataSetChanged();
            persist.run();
            return true;
        });
        ToolViewFactory.addLabel(box, "Long-press a note to delete it.");
        LinearLayout exportRow = ToolViewFactory.makeRow(box);
        MaterialButton exportBtn = ToolViewFactory.makeRowButton(exportRow, "Export file", 1f);
        MaterialButton shareNotesBtn = ToolViewFactory.makeRowButton(exportRow, "Share", 1f);
        MaterialButton locateNotesBtn = ToolViewFactory.makeRowButton(exportRow, "Locate file", 1f);
        File[] lastExport = new File[1];
        exportBtn.setOnClickListener(v -> {
            if (notes.isEmpty()) {
                ToolViewFactory.toast(context, "No notes to export");
                return;
            }
            try {
                File dir = new File(new File(Environment.getExternalStorageDirectory(),
                        Environment.DIRECTORY_DOCUMENTS), "Notes");
                dir.mkdirs();
                File out = new File(dir, "notes_" + System.currentTimeMillis() + ".txt");
                StringBuilder sb = new StringBuilder();
                for (String n : notes) sb.append(n).append("\n\n");
                FileWriter w = new FileWriter(out);
                w.write(sb.toString().trim());
                w.close();
                lastExport[0] = out;
                ToolViewFactory.toast(context, "Exported " + out.getName());
            } catch (Exception e) {
                ToolViewFactory.toast(context, "Export failed");
            }
        });
        shareNotesBtn.setOnClickListener(v -> {
            if (lastExport[0] != null && lastExport[0].exists()) {
                shareFile(context, lastExport[0], "text/plain");
            } else if (!notes.isEmpty()) {
                Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, TextUtils.join("\n\n", notes));
                context.startActivity(Intent.createChooser(s, "Share notes"));
            } else {
                ToolViewFactory.toast(context, "Nothing to share");
            }
        });
        locateNotesBtn.setText("Open file");
        locateNotesBtn.setOnClickListener(v -> {
            if (lastExport[0] != null && lastExport[0].exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", lastExport[0]);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "text/plain");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(i, "Open notes"));
                } catch (Exception e) {
                    ToolViewFactory.toast(context, "Open failed");
                }
            } else {
                ToolViewFactory.toast(context, "Export first");
            }
        });
        return box;
    }

    private void shareFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mime);
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, "Share"));
        } catch (Exception e) {
            ToolViewFactory.toast(context, "Share failed");
        }
    }
}
