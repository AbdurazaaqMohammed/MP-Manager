package io.github.abdurazaaqmohammed.ui.dialogs;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.ui.views.SortDirectionToggle;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

public class FilePickerDialog {

    public static final int SINGLE_MODE = 0;
    public static final int MULTI_MODE = 1;

    public static final int FILE_SELECT = 0;
    public static final int DIR_SELECT = 1;
    public static final int FILE_AND_DIR_SELECT = 2;

    private static final String PREF_SORT = "custom_picker_sort";
    private static final String PREF_REVERSE = "custom_picker_reverse";
    private static final String PREF_FILTER = "custom_picker_filter";
    private static final String PREF_LAST_PATH = "custom_picker_last_path";

    public interface OnFileSelectedListener {
        void onFileSelected(String[] files);
    }

    public static class Properties {
        public int selection_mode = SINGLE_MODE;
        public int selection_type = FILE_SELECT;
        public File root;
        public File offset;
        public String[] extensions;
        public String preferenceKey;
    }

    private final Context context;
    private final Properties props;
    private SharedPreferences settings;
    private OnFileSelectedListener listener;
    private String title;
    private AlertDialog dialog;
    private TextView pathView;
    private PickerIconLoader iconLoader;
    private PickerAdapter adapter;
    private final Set<String> selectedPaths = new LinkedHashSet<>();
    private final List<File> entries = new ArrayList<>();
    private File currentDir;
    private String nameFilter = "";

    public FilePickerDialog(Context context, Properties properties) {
        this.context = context;
        this.props = properties;
    }

    public void setDialogSelectionListener(OnFileSelectedListener listener) {
        this.listener = listener;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void show() {
        settings = PreferenceManager.getDefaultSharedPreferences(context);
        iconLoader = new PickerIconLoader(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_file_picker, null);
        pathView = view.findViewById(R.id.file_picker_path);
        ImageButton sortButton = view.findViewById(R.id.file_picker_sort);
        EditText searchView = view.findViewById(R.id.file_picker_search);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                nameFilter = s.toString().toLowerCase(java.util.Locale.ROOT);
                reloadCurrent();
            }
        });
        FastScrollerRecyclerView recyclerView = view.findViewById(R.id.file_picker_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter = new PickerAdapter());

        currentDir = resolveStartDir();

        sortButton.setOnClickListener(v -> showSortDialog());
        pathView.setOnClickListener(v -> {
            View textInputLayout = LayoutInflater.from(context).inflate(R.layout.material_edittext, null);
            EditText input = textInputLayout.findViewById(R.id.m_et_edittext);
            input.setText(((TextView) v).getText());
            AlertDialog ad = new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.path)
                    .setView(textInputLayout)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(android.R.string.paste, null) // Note: Need to set it after otherwise the dialog auto close
                    .setPositiveButton(android.R.string.ok, null).show();
            ad.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v1 -> {
                File inputPath = new File(input.getText().toString());
                if (inputPath.exists() && inputPath.isDirectory()) {
                    loadDirectory(inputPath);
                    ad.dismiss();
                } else {
                    Extensions.showMessage(ad, "Failed to navigate to " + inputPath);
                }
            });
            ad.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v2 -> {
                int selectionStart = input.getSelectionStart();
                int selectionEnd = input.getSelectionEnd();
                if (selectionStart != selectionEnd) {
                    input.getText().delete(selectionStart, selectionEnd);
                }
                CharSequence text = ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                if (TextUtils.isEmpty(text)) Extensions.showMessage(ad, context.getString(R.string.nothing_found_to_paste));
                else input.getText().insert(selectionStart, text);
            });
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : context.getString(R.string.select))
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null);
        if (props.selection_mode == MULTI_MODE) {
            builder.setPositiveButton(R.string.select, (d, w) -> returnSelection());
        } else if (props.selection_type == DIR_SELECT || props.selection_type == FILE_AND_DIR_SELECT) {
            builder.setPositiveButton(R.string.select, (d, w) -> selectAndReturn(new String[]{currentDir.getAbsolutePath()}));
        }
        dialog = builder.create();
        loadDirectory(currentDir);
        dialog.show();
    }

    private File resolveStartDir() {
        String saved = settings.getString(prefLastPath(), null);
        if (saved != null) {
            File savedDir = new File(saved);
            if (savedDir.isDirectory()) return savedDir;
        }
        if (props.offset != null && props.offset.isDirectory()) return props.offset;
        if (props.root != null && props.root.isDirectory()) return props.root;
        return Environment.getExternalStorageDirectory();
    }

    private String prefLastPath() {
        return props.preferenceKey == null ? PREF_LAST_PATH : PREF_LAST_PATH + '_' + props.preferenceKey;
    }

    private void loadDirectory(File dir) {
        currentDir = dir;
        entries.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            List<File> list = new ArrayList<>();
            for (File f : files) {
                if (f == null) continue;
                if (f.isFile() && !passesExtension(f)) continue;
                if (!matchesFilter(f)) continue;
                if (!nameFilter.isEmpty() && !f.getName().toLowerCase(java.util.Locale.ROOT).contains(nameFilter)) continue;
                list.add(f);
            }
            sortFiles(list);
            entries.addAll(list);
        }
        adapter.notifyDataSetChanged();
        pathView.setText(dir.getAbsolutePath());
        settings.edit().putString(prefLastPath(), dir.getAbsolutePath()).apply();
    }

    private boolean passesExtension(File f) {
        if (props.extensions == null || props.extensions.length == 0) return true;
        String ext = FilenameUtils.getExtension(f.getName()).toLowerCase(Locale.ROOT);
        for (String allowed : props.extensions) {
            String a = allowed.toLowerCase(Locale.ROOT);
            if (a.startsWith(".")) a = a.substring(1);
            if (ext.equals(a)) return true;
        }
        return false;
    }

    private boolean matchesFilter(File f) {
        int filter = settings.getInt(PREF_FILTER, 0); // 0 all, 1 files, 2 folders
        if (filter == 0) return true;
        return (filter == 1) ? f.isFile() : f.isDirectory();
    }

    private void sortFiles(List<File> list) {
        int sortBy = settings.getInt(PREF_SORT, 0); // 0 name, 1 size, 2 date, 3 type
        boolean reverse = settings.getBoolean(PREF_REVERSE, false);
        list.sort((f1, f2) -> {
            int result;
            switch (sortBy) {
                case 1:
                    result = Long.compare(f1.length(), f2.length());
                    break;
                case 2:
                    result = Long.compare(f1.lastModified(), f2.lastModified());
                    break;
                case 3:
                    result = extOf(f1).compareToIgnoreCase(extOf(f2));
                    if (result == 0) result = f1.getName().compareToIgnoreCase(f2.getName());
                    break;
                default:
                    result = f1.getName().compareToIgnoreCase(f2.getName());
                    break;
            }
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return reverse ? -result : result;
        });
    }

    private static String extOf(File f) {
        return FilenameUtils.getExtension(f.getName());
    }

    private void showSortDialog() {
        int sortBy = settings.getInt(PREF_SORT, 0);
        boolean reverse = settings.getBoolean(PREF_REVERSE, false);
        String[] options = {
                context.getString(R.string.sort_name),
                context.getString(R.string.sort_size),
                context.getString(R.string.sort_date),
                "Type"
        };
        RadioGroup group = new RadioGroup(context);
        group.setPadding(32, 16, 32, 16);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setText(options[i]);
            rb.setId(i);
            if (i == sortBy) rb.setChecked(true);
            group.addView(rb);
        }
        SortDirectionToggle directionToggle = new SortDirectionToggle(context);
        directionToggle.setDescending(reverse);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(group);
        content.addView(directionToggle);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sort_by)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    settings.edit()
                            .putInt(PREF_SORT, group.getCheckedRadioButtonId())
                            .putBoolean(PREF_REVERSE, directionToggle.isDescending())
                            .apply();
                    reloadCurrent();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reloadCurrent() {
        loadDirectory(currentDir);
    }

    private void handleClick(File f) {
        if (f.isDirectory()) {
            if (props.selection_mode == MULTI_MODE) toggleSelection(f);
            else loadDirectory(f);
        } else {
            if (props.selection_mode == MULTI_MODE) toggleSelection(f);
            else selectAndReturn(new String[]{f.getAbsolutePath()});
        }
    }

    private boolean handleLongClick(File f) {
        if (f.isDirectory() && props.selection_mode == SINGLE_MODE
                && (props.selection_type == DIR_SELECT || props.selection_type == FILE_AND_DIR_SELECT)) {
            selectAndReturn(new String[]{f.getAbsolutePath()});
            return true;
        }
        return false;
    }

    private void toggleSelection(File f) {
        String path = f.getAbsolutePath();
        if (!selectedPaths.add(path)) selectedPaths.remove(path);
        adapter.notifyDataSetChanged();
    }

    private void selectAndReturn(String[] paths) {
        if (listener != null) listener.onFileSelected(paths);
        dialog.dismiss();
    }

    private void returnSelection() {
        if (selectedPaths.isEmpty()) {
            Toast.makeText(context, R.string.select_none, Toast.LENGTH_SHORT).show();
            return;
        }
        if (listener != null) listener.onFileSelected(selectedPaths.toArray(new String[0]));
        dialog.dismiss();
    }

    private class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.ViewHolder> {

        @Override
        public int getItemCount() {
            return entries.size() + 1;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_file, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position == 0) {
                holder.fileNameView.setText("..");
                holder.fileDateView.setVisibility(View.INVISIBLE);
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.itemView.setOnClickListener(v -> {
                    File parent = currentDir.getParentFile();
                    if (parent != null) loadDirectory(parent);
                });
                return;
            }
            File f = entries.get(position - 1);
            iconLoader.setupFileView(f, holder.fileIconView, holder.fileDateView);
            holder.fileNameView.setText(f.getName());
            boolean selected = selectedPaths.contains(f.getAbsolutePath());
            holder.itemView.setBackgroundColor(selected ? Color.DKGRAY : Color.TRANSPARENT);
            holder.itemView.setOnClickListener(v -> handleClick(f));
            holder.itemView.setOnLongClickListener(v -> handleLongClick(f));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView fileNameView, fileDateView;
            final android.widget.ImageView fileIconView;

            ViewHolder(View v) {
                super(v);
                fileNameView = v.findViewById(R.id.fileName);
                fileIconView = v.findViewById(R.id.fileIcon);
                fileDateView = v.findViewById(R.id.fileDate);
            }
        }
    }
}