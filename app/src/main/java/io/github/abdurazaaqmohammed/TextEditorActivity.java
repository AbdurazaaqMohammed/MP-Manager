package io.github.abdurazaaqmohammed;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.apk.axml.aXMLEncoder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.LineSeparator;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;

public class TextEditorActivity extends AppCompatActivity {

    private CodeEditor editor;
    private DrawerLayout drawerLayout;
    private LinearLayout searchPanel, replacePanel;
    private EditText searchInput, replaceInput;
    private MaterialButton btnReplaceAll;
    private View btnReplaceToggle, btnFind;
    private ImageButton btnUndo, btnRedo, btnSave, btnEdit, btnFile, btnSearchMenu;
    private LinearLayout bottomBarLayout;

    private Uri currentFileUri;
    private File currentFile;
    private String currentCharset = "UTF-8";

    private boolean matchCase = false;
    private boolean regex = false;
    private boolean wholeWord = false;
    private boolean replaceMode = false;

    private final List<int[]> navigationHistory = new ArrayList<>();
    private int historyPointer = -1;
    private boolean isNavigating = false;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);

        initViews();
        setupListeners();
        applyPreferences();

        handleIntent(getIntent());

        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> {
            btnRedo.setEnabled(editor.canRedo());
            btnUndo.setEnabled(editor.canUndo());
            if (!isNavigating) {
                recordPosition(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
            }
        });
    }

    private void recordPosition(int line, int col) {
        if (navigationHistory.isEmpty() ||
                Math.abs(navigationHistory.get(historyPointer)[0] - line) > 2 ||
                Math.abs(navigationHistory.get(historyPointer)[1] - col) > 5) {

            // Remove future history if we were in the middle
            while (navigationHistory.size() > historyPointer + 1) {
                navigationHistory.remove(navigationHistory.size() - 1);
            }

            navigationHistory.add(new int[] { line, col });
            historyPointer++;

            // Limit history size
            if (navigationHistory.size() > 50) {
                navigationHistory.remove(0);
                historyPointer--;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBottomBarFunctions();
        applyPreferences();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        editor = findViewById(R.id.editor);
        searchPanel = findViewById(R.id.search_panel);
        replacePanel = findViewById(R.id.replace_panel);
        searchInput = findViewById(R.id.search_input);
        replaceInput = findViewById(R.id.replace_input);
        btnReplaceToggle = findViewById(R.id.btn_replace_toggle);
        btnFind = findViewById(R.id.btn_find);
        btnReplaceAll = findViewById(R.id.btn_replace_all);
        btnSearchMenu = findViewById(R.id.btn_search_menu);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);
        btnEdit = findViewById(R.id.btn_edit);
        btnFile = findViewById(R.id.btn_file);
        bottomBarLayout = findViewById(R.id.bottom_bar_layout);
        View bottomBar = findViewById(R.id.bottom_bar_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setTranslationY(-ime.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(bottomBar);

        findViewById(R.id.btn_stop_search).setOnClickListener(v -> {
            editor.getSearcher().stopSearch();
            searchPanel.setVisibility(View.GONE);
        });

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void setupListeners() {
        btnUndo.setOnClickListener(v -> {
            if (editor.canUndo()) editor.undo();
        });

        btnRedo.setOnClickListener(v -> {
            if (editor.canRedo()) editor.redo();
        });

        btnSave.setOnClickListener(v -> saveFile());

        btnEdit.setOnClickListener(v -> showEditMenu());

        btnFile.setOnClickListener(v -> showFileMenu());

        // Search Panel Listeners
        btnFind.setOnClickListener(v -> performSearch());

        btnReplaceToggle.setOnClickListener(v -> {
            if (!replaceMode) {
                replaceMode = true;
                replacePanel.setVisibility(View.VISIBLE);
            } else {
                performReplace();
            }
        });

        btnReplaceAll.setOnClickListener(v -> performReplaceAll());

        btnSearchMenu.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, btnSearchMenu);
            popupMenu.getMenu().add(0, 1, 0, "Regex").setCheckable(true).setChecked(regex);
            popupMenu.getMenu().add(0, 2, 0, "Whole words").setCheckable(true).setChecked(wholeWord);
            popupMenu.getMenu().add(0, 3, 0, "Match case").setCheckable(true).setChecked(matchCase);
            popupMenu.setOnMenuItemClickListener(item -> {
                item.setChecked(!item.isChecked());
                switch (item.getItemId()) {
                    case 1:
                        regex = item.isChecked();
                        break;
                    case 2:
                        wholeWord = item.isChecked();
                        break;
                    case 3:
                        matchCase = item.isChecked();
                        break;
                }
                return true;
            });
            popupMenu.show();
        });
    }

    private void applyPreferences() {
        sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String colorScheme = sharedPreferences.getString("pref_theme", "drac");
        EditorColorScheme ecs = null;
        switch (colorScheme) {
            case "drac":
                ecs = (new SchemeDarcula());
                break;
            case "ecl":
                ecs = (new SchemeEclipse());
                break;
            case "vs":
                ecs = (new SchemeVS2019());
                break;
            case "gh":
                ecs = (new SchemeGitHub());
                break;
            case "np":
                ecs = new SchemeNotepadXX();
                break;
        }
        if (ecs != null) editor.setColorScheme(ecs);
        float fontSize = Float.parseFloat(sharedPreferences.getString("pref_font_size", "14"));
        editor.setTextSize(fontSize);

        int tabSize = Integer.parseInt(sharedPreferences.getString("pref_tab_size", "4"));
        editor.setTabWidth(tabSize);

        boolean showLineNumbers = sharedPreferences.getBoolean("pref_show_line_numbers", true);
        editor.setLineNumberEnabled(showLineNumbers);

        boolean wordWrap = sharedPreferences.getBoolean("pref_word_wrap", false);
        editor.setWordwrap(wordWrap);

        boolean syntaxHighlight = sharedPreferences.getBoolean("pref_syntax_highlight", true);
        // Apply syntax highlight if needed
    }

    @Override
    public void onDestroy() {
        if(editor != null) {
            editor.release();
            if(!manualFinish && editor.canUndo()) {
                boolean isFromFile = currentFile != null;
                if(isFromFile) try (OutputStream os = FileUtils.getOutputStream(new File(getCacheDir(), currentFile.getPath().replace(File.separator, ".")))) {
                    os.write(editor.getText().toString().getBytes(Charset.forName(currentCharset)));
                } catch (Exception ignored) { }
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(searchPanel.getVisibility() == View.VISIBLE) {
            editor.getSearcher().stopSearch();
            searchPanel.setVisibility(View.GONE);
        }
        if(editor != null && editor.canUndo()) {
            new MaterialAlertDialogBuilder(this).setTitle("Changes Made")
                    .setPositiveButton("Save and Exit", (dialog, which) -> {
                        manualFinish = true;
                        saveFile();
                        currentFileUri = Uri.fromFile(currentFile);
                        Intent resultIntent = new Intent();
                        resultIntent.setData(currentFileUri);
                        resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        setResult(757, resultIntent);
                        finish();
                    })
                    .setNegativeButton("Don't Save", (dialog, which) -> {
                        manualFinish = true;
                        finish();
                    })
                    .setNeutralButton("Cancel", null)
                    .setMessage("Do you want to save " + currentFile.getName() + '?').show();
        }
        else super.onBackPressed();
    }

    private void loadBottomBarFunctions() {
        bottomBarLayout.removeAllViews();

        String json = sharedPreferences.getString("pref_bottom_bar_buttons", "[]");
        if (json.equals("[]") || json.equals("Search,Copy,Cut,Paste")) {
            // Setup some defaults if it's the old string or empty
            try {
                org.json.JSONArray array = new org.json.JSONArray();
                array.put(new org.json.JSONObject().put("action", "Search").put("label", "Search"));
                array.put(new org.json.JSONObject().put("action", "Copy selection").put("label", "Copy"));
                array.put(new org.json.JSONObject().put("action", "Cut selection").put("label", "Cut"));
                array.put(new org.json.JSONObject().put("action", "Paste selection").put("label", "Paste"));
                json = array.toString();
                sharedPreferences.edit().putString("pref_bottom_bar_buttons", json).apply();
            } catch (Exception ignored) { }
        }

        try {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(3);
            params.setMarginStart(3);
            org.json.JSONArray array = new org.json.JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                String action = obj.getString("action");
                String label = obj.optString("label", action);

                MaterialButton btn = new MaterialButton(this);
                btn.setText(label);

                btn.setLayoutParams(params);
                btn.setOnClickListener(v -> executeBottomBarFunction(obj, false));
                btn.setOnLongClickListener(v -> {
                    executeBottomBarFunction(obj, true);
                    return true;
                });
                bottomBarLayout.addView(btn);
            }
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void executeBottomBarFunction(org.json.JSONObject obj, boolean isLongPress) {
        String actionKey = isLongPress ? "longAction" : "action";
        String data1Key = isLongPress ? "longData1" : "data1";
        String data2Key = isLongPress ? "longData2" : "data2";

        String action = obj.optString(actionKey);
        if (TextUtils.isEmpty(action)) return;

        if (action.equalsIgnoreCase("Search")) {
            searchPanel.setVisibility(View.VISIBLE);
            return;
        }

        Cursor cursor = editor.getCursor();
        Content content = editor.getText();
        int line = cursor.getLeftLine();
        String lineText = content.getLineString(line);

        try {
            switch (action) {
                case "Insert text":
                    String text = obj.optString(data1Key);
                    content.insert(cursor.getLeftLine(), cursor.getLeftColumn(), text);
                    break;
                case "Regex find and replace":
                    String findRegex = obj.optString(data1Key);
                    String replaceStr = obj.optString(data2Key);
                    String allText = content.toString();
                    String newText = allText.replaceAll(findRegex, replaceStr);
                    if (!allText.equals(newText)) {
                        content.beginBatchEdit();
                        content.replace(0, 0, content.getLineCount() - 1,
                                content.getColumnCount(content.getLineCount() - 1),
                                newText);
                        content.endBatchEdit();
                    }
                    break;
                case "Copy selection":
                    copySelection();
                    break;
                case "Cut selection":
                    cutSelection();
                    break;
                case "Paste selection":
                    pasteSelection();
                    break;
                case "Copy line":
                    setClipboard(lineText);
                    break;
                case "Cut line":
                    setClipboard(lineText);
                    content.delete(line, 0, line, content.getColumnCount(line));
                    break;
                case "Delete line":
                    content.delete(line, 0, line, content.getColumnCount(line));
                    if (line < content.getLineCount() - 1)
                        content.delete(line, content.getColumnCount(line), line + 1, 0);
                    break;
                case "Empty line":
                    content.delete(line, 0, line, content.getColumnCount(line));
                    break;
                case "Replace line":
                    CharSequence clip = getClipboard();
                    if (clip != null) {
                        content.replace(line, 0, line, content.getColumnCount(line), clip);
                    }
                    break;
            }
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean axml;

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (intent.hasExtra("axml")) {
            axml = true;
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                editor.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
            }
            currentFile = new File(intent.getStringExtra("path"));
            currentFileUri = Uri.fromFile(currentFile);
        } else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)
                || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
             if (uri != null) {
                currentFileUri = uri;
                if ("file".equals(uri.getScheme())) currentFile = new File(uri.getPath());
                loadFile();
            } else if (intent.hasExtra("path")) {
                currentFile = new File(intent.getStringExtra("path"));
                currentFileUri = Uri.fromFile(currentFile);
                loadFile();
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                 editor.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
             }
        } else if (intent.hasExtra("path")) {
            currentFile = new File(intent.getStringExtra("path"));
            currentFileUri = Uri.fromFile(currentFile);
            loadFile();
        }
    }

    private void loadFile() {
        if (currentFileUri == null && currentFile == null)
            return;
        boolean isFromFile = currentFile != null;
        File cachedFile = new File(getCacheDir(), (currentFile.getPath()).replace(File.separator, "."));
        if(isFromFile && cachedFile.exists() && cachedFile.length() != currentFile.length()) {
            new MaterialAlertDialogBuilder(this).setMessage("Do you want to restore them?").setTitle("Unsaved changes found")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        try {
                            FileUtils.copyFile(cachedFile, currentFile);
                            loadEditorText();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .setNegativeButton("No", (dialog, which) -> loadEditorText()).show();
        } else loadEditorText();
    }

    private void loadEditorText() {
        boolean isFromFile = currentFile != null;
        try (InputStream is = isFromFile ? FileUtils.getInputStream(currentFile) : getContentResolver().openInputStream(currentFileUri);
             InputStreamReader isr = new InputStreamReader(is, Charset.forName(currentCharset));
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            editor.setText(sb);
            Toast.makeText(this, "File loaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            Toast.makeText(this, "Failed to load file", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFile() {
        if (currentFileUri == null && currentFile == null) {
            Toast.makeText(this, "No file to save", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = getIntent();
        boolean inZip = intent.hasExtra("zf");

        try (OutputStream os = (currentFile == null ? getContentResolver().openOutputStream(currentFileUri, "wt") : FileUtils.getOutputStream(currentFile))) {
            final String text = editor.getText().toString();
            os.write(axml ? new aXMLEncoder().encodeString(text, this) : text.getBytes(Charset.forName(currentCharset)));
            if(inZip) {
                /*File zipFile = new File(intent.getStringExtra("zf"));
                File tempZipFile = new File(zipFile.getParentFile(), zipFile.getName() + ".tmp");
                try (ZipFile sourceZip = new ZipFile(zipFile);
                     OutputStream os2 = FileUtils.getOutputStream(tempZipFile);
                     ZipOutputStream zos = new ZipOutputStream(os2)) {
                    Enumeration<? extends ZipEntry> entries = sourceZip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        zos.putNextEntry(new ZipEntry(name));
                        if (!entry.isDirectory()) {
                            if(name.equals(currentFile.getName())) FileUtils.copyFile(currentFile, zos);
                            else try (InputStream is = sourceZip.getInputStream(entry)) {
                                FileUtils.copyFile(is, zos);
                            }
                        }
                        zos.closeEntry();
                    }
                    if( tempZipFile.renameTo(zipFile)) Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                }*/
            } else Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void showEditMenu() {
        PopupMenu popupMenu = new PopupMenu(this, btnEdit);
        forceShowIcons(popupMenu);

        String[] options = { "Copy line", "Cut line", "Delete line", "Empty line", "Replace line (with clipboard)",
                "Duplicate line", "Convert to uppercase", "Convert to lowercase", "Convert to sentence case",
                "Convert to camelcase", "Increase indent", "Decrease indent" };

        int[] icons = {
                R.drawable.baseline_content_copy_24, R.drawable.baseline_content_cut_24,
                R.drawable.baseline_delete_24, R.drawable.baseline_remove_circle_24,
                R.drawable.reset_focus_24px, R.drawable.control_point_duplicate_24px,
                R.drawable.uppercase_24px, R.drawable.lowercase_24px,
                R.drawable.match_case_24px, R.drawable.match_case_off_24px,
                R.drawable.horizontal_align_right_24px, R.drawable.horizontal_align_left_24px
        };

        for (int i = 0; i < options.length; i++) {
            popupMenu.getMenu().add(0, i, 0, options[i]).setIcon(icons[i]);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            Cursor cursor = editor.getCursor();
            int line = cursor.getLeftLine();
            int rightLine = cursor.getRightLine();
            Content content = editor.getText();
            String lineText = content.getLineString(line);

            switch (which) {
                case 0: // Copy line
                    setClipboard(lineText);
                    break;
                case 1: // Cut line
                    setClipboard(lineText);
                    content.delete(line, 0, line, content.getColumnCount(line));
                    break;
                case 2: // Delete line
                    content.delete(line, 0, line, content.getColumnCount(line));
                    if (line < content.getLineCount() - 1) {
                        content.delete(line, content.getColumnCount(line), line + 1, 0); // remove newline
                    }
                    break;
                case 3: // Empty line
                    content.delete(line, 0, line, content.getColumnCount(line));
                    break;
                case 4: // Replace line
                    CharSequence clip = getClipboard();
                    if (clip != null) content.replace(line, 0, line, content.getColumnCount(line), clip);
                    break;
                case 5: // Duplicate line
                    content.insert(line, content.getColumnCount(line), new StringBuilder().append('\n').append(lineText));
                    break;
                case 6: // Uppercase
                    if (line == rightLine) content.replace(line, 0, line, content.getColumnCount(line), lineText.toUpperCase());
                    else  content.replace(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn(), content.subContent(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn()).toString().toUpperCase());
                    break;
                case 7: // Lowercase
                    if (line == rightLine) content.replace(line, 0, line, content.getColumnCount(line), lineText.toLowerCase());
                    else content.replace(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn(), content.subContent(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn()).toString().toLowerCase());
                    break;
                case 8: // Sentence case
                    if (!lineText.isEmpty()) {
                        content.replace(line, 0, line, content.getColumnCount(line), lineText.substring(0, 1).toUpperCase() + lineText.substring(1).toLowerCase());
                    }
                    break;
                case 9: // CamelCase
                    StringBuilder camel = new StringBuilder();
                    boolean nextUpper = false;
                    for (char c : lineText.toCharArray()) {
                        if (c == ' ' || c == '_' || c == '-') {
                            nextUpper = true;
                        } else {
                            camel.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                            nextUpper = false;
                        }
                    }
                    content.replace(line, 0, line, content.getColumnCount(line), camel);
                    break;
                case 10: // Increase indent
                    if (line == rightLine) increaseIndent(content, line);
                    else {
                        content.beginBatchEdit();
                        for(int i = line; i <= rightLine; i++) increaseIndent(content, i);
                        content.endBatchEdit();
                     }
                    break;
                case 11: // Decrease indent 
                    if (line == rightLine) decreaseIntent(lineText, content, line);
                    else {
                        content.beginBatchEdit();
                        for(int i = line; i <= rightLine; i++) decreaseIntent(content.getLineString(i), content, i);
                        content.endBatchEdit();
                    }
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    private static void increaseIndent(Content content, int i) {
        content.insert(i, 0, "    ");
    }

    private static void decreaseIntent(String lineText, Content content, int line) {
        if (lineText.startsWith("    ")) content.delete(line, 0, line, 4);
        else if (lineText.startsWith("\t")) content.delete(line, 0, line, 1);
    }

    private void showFileMenu() {
        PopupMenu popupMenu = new PopupMenu(this, btnFile);
        forceShowIcons(popupMenu);

        String[] options = { "File", "Search", "Syntax", "Previous position", "Next position",
                "Jump to line", "Start of line", "End of line", "Word wrap", "Read only", "Preferences", "Close file" };

        int[] icons = {
                R.drawable.baseline_insert_drive_file_24, R.drawable.baseline_search_24,
                R.drawable.baseline_text_snippet_24, R.drawable.keyboard_double_arrow_left_24px,
                R.drawable.keyboard_double_arrow_right_24px, R.drawable.jump_to_element_24px,
                R.drawable.text_select_jump_to_beginning_24px, R.drawable.text_select_jump_to_end_24px,
                R.drawable.wrap_text_24px, R.drawable.edit_off_24px,
                R.drawable.baseline_settings_24, R.drawable.baseline_exit_to_app_24
        };

        for (int i = 0; i < options.length; i++) {
            MenuItem item = popupMenu.getMenu().add(0, i, 0, options[i]);
            item.setIcon(icons[i]);
            if ((i == 4 && !(historyPointer < navigationHistory.size() - 1)) || (i == 3 && !(historyPointer > 0))) item.setEnabled(false);
            else if (i == 8) {
                item.setCheckable(true);
                item.setChecked(editor.isWordwrap());
            } else if (i == 9) {
                item.setCheckable(true);
                item.setChecked(!editor.isEditable());
            }
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    showSubFileMenu();
                    break;
                case 1: // Search
                    searchPanel.setVisibility(View.VISIBLE);
                    break;
                case 2: // Syntax
                    showSyntaxDialog();
                    break;
                case 3: // Prev pos
                    navigateHistory(false);
                    break;
                case 4: // Next pos
                    navigateHistory(true);
                    break;
                case 5: // Jump to line
                    showJumpToLineDialog();
                    break;
                case 6:
                    Cursor cursor = editor.getCursor();
                    int ll = cursor.getLeftLine();
                    recordPosition(ll, cursor.getLeftColumn());
                    editor.setSelection(ll, 0);
                    break;
                case 7:
                    Cursor c = editor.getCursor();
                    int leftLine = c.getLeftLine();
                    recordPosition(leftLine, c.getLeftColumn());
                    editor.setSelection(leftLine, editor.getText().getColumnCount(leftLine));
                    break;
                case 8: // Word wrap toggle
                    boolean current = editor.isWordwrap();
                    editor.setWordwrap(!current);
                    sharedPreferences.edit().putBoolean("pref_word_wrap", !current).apply();
                    break;
                case 9: // Read only
                    editor.setEditable(!editor.isEditable());
                    break;
                case 10: // Preferences
                    startActivity(new Intent(this, EditorSettingsActivity.class));
                    break;
                case 11: // Close file
                    if(editor != null && editor.canUndo()) new MaterialAlertDialogBuilder(this).setTitle("Changes Made")
                                .setPositiveButton("Save and Exit", (dialog, which) -> {
                                    saveFile();
                                    manualFinish = true;
                                    finish();
                                })
                                .setNegativeButton("Don't Save", (dialog, which) -> {
                                    manualFinish = true;
                                    finish();
                                })
                                .setNeutralButton("Cancel", null)
                                .setMessage(getString(R.string.save_confirm, currentFile.getName())).show();
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    boolean manualFinish;

    private void navigateHistory(boolean forward) {
        if (forward) {
            if (historyPointer < navigationHistory.size() - 1) {
                historyPointer++;
                jumpToRecordedPosition();
            }
        } else {
            if (historyPointer > 0) {
                historyPointer--;
                jumpToRecordedPosition();
            }
        }
    }

    private void jumpToRecordedPosition() {
        isNavigating = true;
        int[] pos = navigationHistory.get(historyPointer);
        editor.setSelection(pos[0], pos[1]);
        isNavigating = false;
    }

    private void showJumpToLineDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Jump to Line")
                .setView(input)
                .setPositiveButton("Go", (dialog, which) -> {
                    CharSequence val = input.getText();
                    if (!TextUtils.isEmpty(val)) {
                        int line = Integer.parseInt(val, 0, val.length(), 10) - 1;
                        if (line >= 0 && line < editor.getLineCount()) {
                            recordPosition(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
                            editor.setSelection(line, 0);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSubFileMenu() {
        PopupMenu popupMenu = new PopupMenu(this, btnFile);
        forceShowIcons(popupMenu);

        String[] options = { "Reload file", "Reload with charset", "Set encoding", "Set linebreak type", "Statistics" };
        int[] icons = {
                R.drawable.baseline_refresh_24, R.drawable.baseline_refresh_24,
                R.drawable.baseline_settings_24, R.drawable.baseline_swap_horiz_24,
                R.drawable.baseline_info_24
        };

        for (int i = 0; i < options.length; i++) {
            popupMenu.getMenu().add(0, i, 0, options[i]).setIcon(icons[i]);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    loadFile();
                    break;
                case 1:
                    showCharsetDialog(true);
                    break;
                case 2:
                    showCharsetDialog(false);
                    break;
                case 3:
                    showLinebreakDialog();
                    break;
                case 4:
                    showStatistics();
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    private void showCharsetDialog(boolean reload) {
        String[] charsets = { "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "US-ASCII", "ISO-8859-1", "GBK", "Big5" };
        new MaterialAlertDialogBuilder(this)
                .setTitle(reload ? "Reload with Charset" : "Set Encoding")
                .setItems(charsets, (dialog, which) -> {
                    currentCharset = charsets[which];
                    if (reload) loadFile();
                    else Toast.makeText(this, "Encoding set to " + currentCharset, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showLinebreakDialog() {
        String[] types = { "LF (\\n)", "CRLF (\\r\\n)", "CR (\\r)" };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Set Linebreak Type")
                .setItems(types, (dialog, which) -> {
                    LineSeparator lineSeparator = which == 0 ? LineSeparator.LF : which == 1 ? LineSeparator.CRLF : LineSeparator.CR;
                    editor.setLineSeparator(lineSeparator);
                    Toast.makeText(this, "Linebreak type set to " + types[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showSyntaxDialog() {
        String[] syntaxes = { "Plain Text", "Java", "C", "C++", "Python", "JavaScript", "HTML", "CSS", "Markdown",
                "Import Syntax..." };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose Syntax")
                .setItems(syntaxes, (dialog, which) -> {
                    if (which == syntaxes.length - 1) {
                        Toast.makeText(this, "Syntax import functionality coming soon", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Syntax set to " + syntaxes[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showStatistics() {
        String text = editor.getText().toString();
        int bytes = text.getBytes().length;
        int chars = text.length();
        int words = text.isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = editor.getLineCount();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Statistics")
                .setMessage(new StringBuilder().append("Bytes: ").append(bytes).append('\n').append("Characters: ").append(chars).append('\n').append("Words: ").append(words).append('\n').append("Lines: ").append(lines))
                .setPositiveButton("OK", null)
                .show();
    }

    private void performSearch() {
        CharSequence query = searchInput.getText();
        if (!TextUtils.isEmpty(query)) try {
            EditorSearcher searcher = editor.getSearcher();
            searcher.search(query.toString(), new EditorSearcher.SearchOptions(!matchCase, regex));
            searcher.gotoNext();
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void performReplace() {
        if (!TextUtils.isEmpty(searchInput.getText())) try {
            editor.getSearcher().replaceCurrentMatch(replaceInput.getText().toString());
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void performReplaceAll() {
        if (!TextUtils.isEmpty(searchInput.getText())) try {
            editor.getSearcher().replaceAll(replaceInput.getText().toString());
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void setClipboard(CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("editor_text", text);
        clipboard.setPrimaryClip(clip);
    }

    private CharSequence getClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            return clipboard.getPrimaryClip().getItemAt(0).getText();
        }
        return null;
    }

    private void copySelection() {
        Cursor cursor = editor.getCursor();
        if (cursor.isSelected()) {
            Content text = editor.getText();
            StringBuilder sb = new StringBuilder();
            int startLine = cursor.getLeftLine();
            int startCol = cursor.getLeftColumn();
            int endLine = cursor.getRightLine();
            int endCol = cursor.getRightColumn();
            for (int i = startLine; i <= endLine; i++) {
                String lineStr = text.getLineString(i);
                if (startLine == endLine) {
                    sb.append(lineStr.substring(startCol, endCol));
                } else if (i == startLine) {
                    sb.append(lineStr.substring(startCol)).append('\n');
                } else if (i == endLine) {
                    sb.append(lineStr.substring(0, endCol));
                } else {
                    sb.append(lineStr).append('\n');
                }
            }
            setClipboard(sb);
        }
    }

    private void cutSelection() {
        Cursor cursor = editor.getCursor();
        if (cursor.isSelected()) {
            Content text = editor.getText();
            StringBuilder sb = new StringBuilder();
            int startLine = cursor.getLeftLine();
            int startCol = cursor.getLeftColumn();
            int endLine = cursor.getRightLine();
            int endCol = cursor.getRightColumn();
            for (int i = startLine; i <= endLine; i++) {
                String lineStr = text.getLineString(i);
                if (startLine == endLine) {
                    sb.append(lineStr.substring(startCol, endCol));
                } else if (i == startLine) {
                    sb.append(lineStr.substring(startCol)).append('\n');
                } else if (i == endLine) {
                    sb.append(lineStr.substring(0, endCol));
                } else {
                    sb.append(lineStr).append('\n');
                }
            }
            setClipboard(sb);
            text.delete(startLine, startCol, endLine, endCol);
        }
    }

    private void pasteSelection() {
        CharSequence clip = getClipboard();
        if (clip != null) {
            Cursor cursor = editor.getCursor();
            Content text = editor.getText();
            if (cursor.isSelected()) {
                text.replace(cursor.getLeftLine(), cursor.getLeftColumn(), cursor.getRightLine(),
                        cursor.getRightColumn(), clip);
            } else {
                text.insert(cursor.getLeftLine(), cursor.getLeftColumn(), clip);
            }
        }
    }

    private void forceShowIcons(PopupMenu popupMenu) {
        try {
            java.lang.reflect.Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);
            java.lang.reflect.Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon",
                    boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }
}
