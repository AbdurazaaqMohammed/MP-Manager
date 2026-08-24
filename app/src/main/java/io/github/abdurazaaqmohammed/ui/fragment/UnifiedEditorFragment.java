package io.github.abdurazaaqmohammed.ui.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.LineSeparator;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import modder.hub.dexeditor.activity.DexEditorActivity;
import modder.hub.dexeditor.fragment.SettingsFragment;
import modder.hub.dexeditor.fragment.SmaliMethodFieldListFragment;
import modder.hub.dexeditor.smali.SmaliCursorUtils;
import modder.hub.dexeditor.smali.SmaliHelper;
import modder.hub.dexeditor.smali.SmaliInstructionHelper;
import modder.hub.dexeditor.utils.CustomAutoComplete;
import modder.hub.dexeditor.utils.EditorPositionManager;
import modder.hub.dexeditor.utils.Notify_MT;
import modder.hub.dexeditor.utils.SmaliLabelDialog;
import modder.hub.dexeditor.views.SmaliInstructionsDialog;
import modder.hub.dexeditor.views.TextActionWindow;

public class UnifiedEditorFragment extends Fragment implements SmaliMethodFieldListFragment.DialogLineNumberListener {

    public static final int TYPE_TEXT = -1;
    public static final int TYPE_SMALI = 0;
    public static final int TYPE_JAVA = 1;

    private static final String[] SYNTAXES = { "Plain Text", "Java", "C", "C++", "Python", "JavaScript", "HTML", "CSS", "Markdown" };
    private static final String[] CHARSETS = { "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "US-ASCII", "ISO-8859-1", "GBK", "Big5" };
    private static final String[] LINEBREAKS = { "LF (\\n)", "CRLF (\\r\\n)", "CR (\\r)" };

    public static final String[] SYMBOLS = {
            "->", "{", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
    };

    public static final String[] SYMBOL_INSERT_TEXT = {
            "\t", "{}", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
    };

    private CodeEditor editor;
    private LinearLayout bottomBarLayout, searchPanel, replacePanel, linearHeader;
    private EditText searchInput, replaceInput;
    private View btnFind, btnReplaceToggle, btnSearchMenu, btnStopSearch;
    private MaterialButton btnReplaceAll;
    //private SymbolInputView symbolInput;
    private ProgressBar loadingProgress;
    private TextView textviewLineNo, methodName, textviewLeft;
    private View bottomBarScroll;
    //private View symbolInputContainer;

    private String className = "";
    private String title = "";
    private int type = TYPE_TEXT;
    private boolean isSmali = false;
    private String initialContentText;
    private String currentCharset = "UTF-8";
    private boolean saved = true;
    private boolean axml = false;

    private boolean matchCase = false, regex = false, wholeWord = false, replaceMode = false;
    private String lastSearchQuery;
    private Integer lastSearchType;
    private boolean lastIgnoreCase;
    private final List<int[]> navigationHistory = new ArrayList<>();
    private int historyPointer = -1;
    private boolean isNavigating = false;

    private boolean isClosing = false, isReload = false, isInitializing = true;
    private EditorPositionManager positionManager;
    private SharedPreferences editorPrefs, sharedPreferences;
    private SharedPreferences.Editor preferencesEditor;
    private PackageManager packageManager;
    private String savedFont = "normal";
    private SmaliCursorUtils.MethodInfo currentMethodInfo;
    private String tempSmaliPath;

    private static boolean tmRegistered = false;
    private static Language cachedSmaliLanguage;
    private static TextMateColorScheme cachedColorScheme;
    private static String[] cachedInstructions;

    public interface EditorCallback {
        void onContentModified(String className);
        void onUndoRedoChanged(boolean canUndo, boolean canRedo);
        void onSaveRequested();
        void onCloseRequested();
        void onPreferencesRequested();
    }
    private EditorCallback callback;

    public static UnifiedEditorFragment newInstance(String className, String title, String content, int type) {
        UnifiedEditorFragment fragment = new UnifiedEditorFragment();
        Bundle args = new Bundle();
        args.putString("className", className != null ? className : "");
        args.putString("title", title != null ? title : "");
        args.putString("content", content);
        args.putInt("type", type);
        fragment.setArguments(args);
        return fragment;
    }

    public void setCallback(EditorCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            className = getArguments().getString("className", "");
            title = getArguments().getString("title", "");
            initialContentText = getArguments().getString("content");
            type = getArguments().getInt("type", TYPE_TEXT);
        }
        isSmali = (type == TYPE_SMALI);
        tempSmaliPath = requireContext().getFilesDir() + "/tmp_" + className.hashCode() + ".smali";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_unified_editor, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeLogic();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBottomBarFunctions();
        applyPreferences();
        Activity activity = getActivity();
        if (isSmali && activity instanceof DexEditorActivity) {
            DexEditorActivity.EditorTab tab = ((DexEditorActivity) activity).getTabForClassName(className);
            if (tab != null && editor != null) {
                if (type == TYPE_JAVA) editor.setEditable(false);
                else editor.setEditable(!tab.isReadOnly);
            }
        }
    }

    private void initViews(View view) {
        editor = view.findViewById(R.id.editor);
        bottomBarLayout = view.findViewById(R.id.bottom_bar_layout);
        bottomBarScroll = view.findViewById(R.id.bottom_bar_scroll);
        searchPanel = view.findViewById(R.id.search_panel);
        replacePanel = view.findViewById(R.id.replace_panel);
        searchInput = view.findViewById(R.id.search_input);
        replaceInput = view.findViewById(R.id.replace_input);
        btnFind = view.findViewById(R.id.btn_find);
        btnReplaceToggle = view.findViewById(R.id.btn_replace_toggle);
        btnReplaceAll = view.findViewById(R.id.btn_replace_all);
        btnSearchMenu = view.findViewById(R.id.btn_search_menu);
        btnStopSearch = view.findViewById(R.id.btn_stop_search);
//        symbolInput = view.findViewById(R.id.symbol_input);
//        symbolInputContainer = view.findViewById(R.id.symbol_input_container);
        loadingProgress = view.findViewById(R.id.loading_progress);
        linearHeader = view.findViewById(R.id.linear_header);
        textviewLineNo = view.findViewById(R.id.textview_lineNo);
        methodName = view.findViewById(R.id.methodName);
        textviewLeft = view.findViewById(R.id.textview_left);

        positionManager = EditorPositionManager.getInstance(requireContext());
        editorPrefs = requireContext().getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);
        sharedPreferences = requireContext().getSharedPreferences("SelectedTranslationPackageName", 0);
        preferencesEditor = sharedPreferences.edit();
        packageManager = requireContext().getPackageManager();

        ViewCompat.setOnApplyWindowInsetsListener(bottomBarScroll, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setTranslationY(-ime.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(bottomBarScroll);

        if (textviewLeft != null) textviewLeft.setText(!TextUtils.isEmpty(title) ? title : "...");

        setupHeaderListeners();
        setupSearchListeners();
    }

    private void setupHeaderListeners() {
        if (linearHeader == null) return;
        View linearLeft = linearHeader.findViewById(R.id.linear_left);
        View linearRight = linearHeader.findViewById(R.id.linear_right);

        View.OnLongClickListener selectMethodListener = v -> {
            if (isSmali && currentMethodInfo != null && currentMethodInfo.startLine != -1 && currentMethodInfo.endLine != -1) {
                editor.setSelectionRegion(currentMethodInfo.startLine, 0, currentMethodInfo.endLine,
                        editor.getText().getColumnCount(currentMethodInfo.endLine));
                return true;
            }
            return false;
        };

        if (linearLeft != null) {
            linearLeft.setOnClickListener(v -> {
                Activity act = getActivity();
                if (act instanceof DexEditorActivity) ((DexEditorActivity) act).toggleDrawer();
            });
            linearLeft.setOnLongClickListener(v -> {
                if (getContext() == null) return false;
                PopupMenu popupMenu = new PopupMenu(requireContext(), v);
                Menu menu = popupMenu.getMenu();
                menu.add(1, 1, 1, title);
                menu.add(2, 2, 2, className.replace('/', '.'));
                menu.add(3, 3, 3, className);
                menu.add(4, 4, 4, "L" + className + ";");
                menu.add(5, 5, 5, "Locate");
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    int id = menuItem.getItemId();
                    if (id == 5) {
                        Activity act = getActivity();
                        if (act instanceof DexEditorActivity activity) activity.locateClass(className);
                    } else {
                        CopyUtil.copyToClipboard(requireActivity(), Objects.requireNonNull(menuItem.getTitle()).toString());
                    }
                    return true;
                });
                popupMenu.show();
                return true;
            });
        }

        if (linearRight != null) {
            linearRight.setOnClickListener(v -> showMethodFieldList());
            linearRight.setOnLongClickListener(selectMethodListener);
        }
        if (textviewLineNo != null) textviewLineNo.setOnLongClickListener(selectMethodListener);
    }

    private void setupSearchListeners() {
        btnFind.setOnClickListener(v -> performSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            performSearch();
            return true;
        });
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
            PopupMenu popupMenu = new PopupMenu(requireContext(), btnSearchMenu);
            popupMenu.getMenu().add(0, 1, 0, "Regex").setCheckable(true).setChecked(regex);
            popupMenu.getMenu().add(0, 2, 0, "Whole words").setCheckable(true).setChecked(wholeWord);
            popupMenu.getMenu().add(0, 3, 0, "Match case").setCheckable(true).setChecked(matchCase);
            popupMenu.setOnMenuItemClickListener(item -> {
                item.setChecked(!item.isChecked());
                switch (item.getItemId()) {
                    case 1: regex = item.isChecked(); break;
                    case 2: wholeWord = item.isChecked(); break;
                    case 3: matchCase = item.isChecked(); break;
                }
                return true;
            });
            popupMenu.show();
        });
        btnStopSearch.setOnClickListener(v -> {
            editor.getSearcher().stopSearch();
            searchPanel.setVisibility(View.GONE);
            replaceMode = false;
            replacePanel.setVisibility(View.GONE);
        });
    }

    private void initializeLogic() {
        savedFont = SettingsFragment.getFontType(requireContext());
        editor.setLineNumberEnabled(SettingsFragment.showLineNumbers(requireContext()));
        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (isInitializing) return;
            saved = false;
            Activity act = getActivity();
            if (act instanceof DexEditorActivity) {
                ((DexEditorActivity) act).onContentModified(className);
                ((DexEditorActivity) act).handleUndoRedo();
            }
            if (!isReload && !isClosing) {
                int position = editor.getCursor().getLeftLine();
                positionManager.savePosition(className, position, editor.getCursor().getLeftColumn());
            }
            isReload = false;
            if (callback != null) {
                callback.onContentModified(className);
                callback.onUndoRedoChanged(editor.canUndo(), editor.canRedo());
            }
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> {
            updateUndoRedoButtons();
            if (callback != null) callback.onUndoRedoChanged(editor.canUndo(), editor.canRedo());
            if (!isNavigating) {
                recordPosition(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
            }
            if (isSmali) updateSmaliCursorInfo();
        });

        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded()) return;
            updateEditorUI();
            loadEditorSettings(true);
//            if (isSmali && symbolInput != null) {
//                symbolInput.bindEditor(editor);
//                symbolInput.addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT);
//            }
            if (initialContentText != null) {
                editor.setText(initialContentText);
                postInitialize(false);
            } else if (isSmali) {
                loadSmaliInBackground();
            }
        });
    }

    private void loadSmaliInBackground() {
        Activity activity = getActivity();
        if (!(activity instanceof DexEditorActivity)) return;
        if (loadingProgress != null) loadingProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                DexEditorActivity dexActivity = (DexEditorActivity) activity;
                String smaliCode = DexEditorActivity.classTree.getSmaliByType(
                        Objects.requireNonNull(DexEditorActivity.classTree.classMap.get(className)));
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
                    initialContentText = smaliCode;
                    editor.setText(smaliCode);
                    DexEditorActivity.EditorTab tab = dexActivity.getTabForClassName(className);
                    if (tab != null) tab.content = smaliCode;
                    postInitialize(false);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void updateSmaliCursorInfo() {
        Cursor cursor = editor.getCursor();
        Content text = editor.getText();
        int line = cursor.getLeftLine() + 1;
        int column = cursor.getLeftColumn() + 1;
        if (!isReload && !isInitializing && !isClosing) {
            positionManager.savePosition(className, cursor.getLeftLine(), cursor.getLeftColumn());
        }
        currentMethodInfo = SmaliCursorUtils.getMethodInfo(text, cursor.getLeftLine());
        StringBuilder positionText = new StringBuilder();
        positionText.append(String.format("%d:%d", line, column));
        if (currentMethodInfo != null && currentMethodInfo.startLine != -1 && currentMethodInfo.endLine != -1) {
            positionText.append(" [").append(currentMethodInfo.startLine + 1).append("-").append(currentMethodInfo.endLine + 1).append("]");
        }
        if (cursor.isSelected()) {
            String selectedText = text.subSequence(cursor.getLeft(), cursor.getRight()).toString();
            positionText.append(" (").append(selectedText.length()).append(")");
        }
        if (textviewLineNo != null) textviewLineNo.setText(positionText.toString());
        String currentElement;
        if (currentMethodInfo != null && currentMethodInfo.name != null) {
            currentElement = currentMethodInfo.getDisplayName() + "()";
        } else {
            currentElement = SmaliCursorUtils.getCurrentMethodOrFieldName(text, cursor.getLeftLine());
        }
        if (methodName != null) methodName.setText(currentElement != null ? currentElement : "...");
    }

    private void updateUndoRedoButtons() {
        Activity act = getActivity();
        if (act == null) return;
        if (act instanceof TextEditorActivity) {
            ((TextEditorActivity) act).updateUndoRedo(editor.canUndo(), editor.canRedo());
        } else if (act instanceof DexEditorActivity) {
            ((DexEditorActivity) act).handleUndoRedo();
        }
    }

    private void postInitialize(boolean skipRestorePosition) {
        Activity activity = getActivity();
        if (isSmali && activity instanceof DexEditorActivity) {
            DexEditorActivity.EditorTab tab = ((DexEditorActivity) activity).getTabForClassName(className);
            if (tab != null) {
                if (type == TYPE_JAVA) editor.setEditable(false);
                else editor.setEditable(!tab.isReadOnly);
            }
        }
        new Handler(Looper.getMainLooper()).post(() -> isInitializing = false);
        if (!skipRestorePosition && positionManager != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    EditorPositionManager.Position pos = positionManager.getPosition(className);
                    if (pos != null && pos.lineno >= 0 && pos.lineno < editor.getText().getLineCount()) {
                        editor.jumpToLine(pos.lineno);
                        editor.getCursor().set(pos.lineno, pos.column);
                    }
                } catch (Exception ignored) {}
            }, 100);
        }
    }

    public void loadEditorSettings(boolean loadTypeface) {
        editor.setTextSize(SettingsFragment.getFontSize(requireContext()));
        editor.setLineNumberEnabled(SettingsFragment.showLineNumbers(requireContext()));
        editor.setLineSpacing(2.0f, 1.1f);
        editor.setLineNumberMarginLeft(2f);
        editor.setWordwrap(editorPrefs.getBoolean("wrap_text", false));
        if (loadTypeface) {
            Typeface typeface = savedFont.equals("normal") ? Typeface.DEFAULT : Typeface.MONOSPACE;
            editor.setTypefaceText(typeface);
            editor.setTypefaceLineNumber(typeface);
        } else {
            if (!savedFont.equals(SettingsFragment.getFontType(requireContext()))) {
                Typeface typeface = savedFont.equals("normal") ? Typeface.MONOSPACE : Typeface.DEFAULT;
                editor.setTypefaceText(typeface);
                editor.setTypefaceLineNumber(typeface);
                savedFont = SettingsFragment.getFontType(requireContext());
                isReload = true;
                reloadText();
            }
        }
        editor.replaceComponent(EditorTextActionWindow.class, new TextActionWindow(editor, new TextActionCallback(className)));
    }

    private void reloadText() {
        String code = editor.getText().toString();
        editor.setText(code);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                EditorPositionManager.Position pos = positionManager.getPosition(className);
                if (pos != null) editor.jumpToLine(pos.lineno);
            } catch (Exception ignored) {}
        }, 200);
    }

    private void updateEditorUI() {
        if (isSmali) {
            try {
                ensureLanguageInitialized(requireContext().getApplicationContext());
                editor.setEditorLanguage(new CustomAutoComplete(editor, cachedInstructions, cachedSmaliLanguage));
                editor.setColorScheme(cachedColorScheme);
            } catch (Exception e) {
                Log.e("UnifiedEditor", "Error setting smali language", e);
                editor.setEditorLanguage(new EmptyLanguage());
            }
            //if (symbolInputContainer != null) symbolInputContainer.setVisibility(View.VISIBLE);
            if (linearHeader != null) linearHeader.setVisibility(View.VISIBLE);
            editor.setEditable(true);
        } else if (type == TYPE_JAVA) {
            editor.setEditorLanguage(new JavaLanguage());
            //if (symbolInputContainer != null) symbolInputContainer.setVisibility(View.GONE);
            if (linearHeader != null) linearHeader.setVisibility(View.GONE);
            editor.setEditable(false);
        } else {
            //if (symbolInputContainer != null) symbolInputContainer.setVisibility(View.GONE);
            if (linearHeader != null) linearHeader.setVisibility(View.GONE);
        }
        applyPreferences();
    }

    public void applyPreferences() {
        SharedPreferences editorPrefs = requireContext().getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);
        if (!isSmali) {
            String colorScheme = editorPrefs.getString("pref_theme", "drac");
            EditorColorScheme ecs = switch (colorScheme) {
                case "drac" -> new SchemeDarcula();
                case "ecl" -> new SchemeEclipse();
                case "vs" -> new SchemeVS2019();
                case "gh" -> new SchemeGitHub();
                case "np" -> new SchemeNotepadXX();
                default -> null;
            };
            if (ecs != null) editor.setColorScheme(ecs);
        }
        String fontType = editorPrefs.getString("font_type", "normal");
        Typeface typeface = fontType.equals("normal") ? Typeface.DEFAULT : Typeface.MONOSPACE;
        editor.setTypefaceText(typeface);
        editor.setTypefaceLineNumber(typeface);
        savedFont = fontType;
        String fontSizeStr = editorPrefs.getString("font_size", editorPrefs.getString("pref_font_size", "14"));
        try { editor.setTextSize(Float.parseFloat(fontSizeStr)); } catch (Exception ignored) {}
        int tabSize = Integer.parseInt(editorPrefs.getString("pref_tab_size", "4"));
        editor.setTabWidth(tabSize);
        boolean showLineNumbers = editorPrefs.getBoolean("show_line_numbers", editorPrefs.getBoolean("pref_show_line_numbers", true));
        editor.setLineNumberEnabled(showLineNumbers);
        boolean wordWrap = editorPrefs.getBoolean("pref_word_wrap", false);
        editor.setWordwrap(wordWrap);
    }

    public CodeEditor getEditor() { return editor; }
    public String getCode() { return editor.getText().toString(); }
    public String getClassName() { return className; }
    public int getType() { return type; }
    public boolean isSmaliFile() { return isSmali; }
    public void setClosing(boolean closing) { this.isClosing = closing; }

    public void setText(CharSequence text) {
        if (editor != null) {
            editor.setText(text);
            isInitializing = false;
        } else {
            initialContentText = text != null ? text.toString() : null;
        }
    }

    public void setAxml(boolean axml) { this.axml = axml; }

    public boolean isSaved() { return saved; }
    public void markSaved() { saved = true; }

    // ===== Navigation History =====
    private void recordPosition(int line, int col) {
        if (navigationHistory.isEmpty() ||
                Math.abs(navigationHistory.get(historyPointer)[0] - line) > 2 ||
                Math.abs(navigationHistory.get(historyPointer)[1] - col) > 5) {
            while (navigationHistory.size() > historyPointer + 1) {
                navigationHistory.remove(navigationHistory.size() - 1);
            }
            navigationHistory.add(new int[]{line, col});
            historyPointer++;
            if (navigationHistory.size() > 50) {
                navigationHistory.remove(0);
                historyPointer--;
            }
        }
    }

    public boolean canGoBack() { return historyPointer > 0; }
    public boolean canGoForward() { return historyPointer < navigationHistory.size() - 1; }

    public void navigateHistory(boolean forward) {
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

    // ===== Search/Replace =====
    public void showSearchPanel() { searchPanel.setVisibility(View.VISIBLE); }

    private void performSearch() {
        CharSequence query = searchInput.getText();
        if (TextUtils.isEmpty(query)) return;
        try {
            EditorSearcher searcher = editor.getSearcher();
            int type = regex ? EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    : wholeWord ? EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    : EditorSearcher.SearchOptions.TYPE_NORMAL;
            startSearch(query.toString(), type, !matchCase);
        } catch (Exception e) {
            if (getContext() != null) new ErrorUtil(getActivity()).showError(e);
        }
    }

    private void startSearch(String query, int type, boolean ignoreCase) {
        try {
            EditorSearcher searcher = editor.getSearcher();
            searcher.setCyclicJumping(true); // Find wraps around instead of dying at the last match
            lastSearchQuery = query;
            lastSearchType = type;
            lastIgnoreCase = ignoreCase;
            searcher.search(query, new EditorSearcher.SearchOptions(type, ignoreCase));
            jumpWhenReady(searcher, 0);
        } catch (Exception e) {
            if (getContext() != null) new ErrorUtil(getActivity()).showError(e);
        }
    }

    private void goToNextMatch() {
        if (lastSearchType == null || TextUtils.isEmpty(lastSearchQuery)) {
            searchPanel.setVisibility(View.VISIBLE);
            return;
        }
        EditorSearcher searcher = editor.getSearcher();
        if (searcher.hasQuery() && searcher.gotoNext()) return;
        startSearch(lastSearchQuery, lastSearchType, lastIgnoreCase);
    }

    private void jumpWhenReady(EditorSearcher searcher, int attempt) {
        if (!isAdded() || editor.getSearcher() != searcher) return;
        if (searcher.gotoNext()) return;
        if (attempt >= 12) {
            Extensions.showMessage(requireActivity(), "No matches found");
            return;
        }
        editor.postDelayed(() -> jumpWhenReady(searcher, attempt + 1), 80);
    }

    private void performReplace() {
        if (!TextUtils.isEmpty(searchInput.getText())) try {
            editor.getSearcher().replaceCurrentMatch(replaceInput.getText().toString());
        } catch (Exception e) {
            if (getContext() != null) new ErrorUtil(getActivity()).showError(e);
        }
    }

    private void performReplaceAll() {
        if (!TextUtils.isEmpty(searchInput.getText())) try {
            editor.getSearcher().replaceAll(replaceInput.getText().toString());
        } catch (Exception e) {
            if (getContext() != null) new ErrorUtil(getActivity()).showError(e);
        }
    }

    // ===== Bottom Bar =====
    public void loadBottomBarFunctions() {
        if (bottomBarLayout == null) return;
        bottomBarLayout.removeAllViews();
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String json = prefs.getString("pref_bottom_bar_buttons", "[]");
        if (json.equals("[]") || json.equals("Search,Copy,Cut,Paste")) {
            try {
                JSONArray array = new JSONArray();
                array.put(new JSONObject().put("action", "Search").put("label", "Search"));
                array.put(new JSONObject().put("action", "Copy selection").put("label", "Copy"));
                array.put(new JSONObject().put("action", "Cut selection").put("label", "Cut"));
                array.put(new JSONObject().put("action", "Paste selection").put("label", "Paste"));
                json = array.toString();
                prefs.edit().putString("pref_bottom_bar_buttons", json).apply();
            } catch (Exception ignored) {}
        }
        try {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(3);
            params.setMarginStart(3);
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String action = obj.getString("action");
                String label = obj.optString("label", action);
                MaterialButton btn = new MaterialButton(requireContext());
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
            if (getContext() != null) new ErrorUtil(getActivity()).showError(e);
        }
    }

    private void executeBottomBarFunction(JSONObject obj, boolean isLongPress) {
        String actionKey = isLongPress ? "longAction" : "action";
        String data1Key = isLongPress ? "longData1" : "data1";
        String data2Key = isLongPress ? "longData2" : "data2";
        String action = obj.optString(actionKey);
        if (TextUtils.isEmpty(action)) return;
        if (action.equalsIgnoreCase("Search")) {
            goToNextMatch();
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
                case "Regex find and replace": {
                    String findRegex = obj.optString(data1Key);
                    String replaceStr = obj.optString(data2Key);
                    if (TextUtils.isEmpty(findRegex)) break;
                    String allText = content.toString();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(findRegex).matcher(allText);
                    int cursorOffset = Math.min(cursor.getLeft(), allText.length());
                    boolean found = false;
                    while (m.find()) {
                        if (m.end() > cursorOffset) { found = true; break; }
                    }
                    if (!found) {
                        Extensions.showMessage(requireActivity(), "No matches found");
                        break;
                    }
                    StringBuffer sb = new StringBuffer();
                    m.appendReplacement(sb, replaceStr == null ? "" : replaceStr);
                    String replacement = sb.substring(m.start()); // strip the untouched prefix
                    io.github.rosemoe.sora.text.CharPosition s =
                            content.getIndexer().getCharPosition(m.start());
                    io.github.rosemoe.sora.text.CharPosition e =
                            content.getIndexer().getCharPosition(m.end());
                    content.beginBatchEdit();
                    content.replace(s.line, s.column, e.line, e.column, replacement);
                    content.endBatchEdit();
                    break;
                }
                case "Copy selection": copySelection(); break;
                case "Cut selection": cutSelection(); break;
                case "Paste selection": pasteSelection(); break;
                case "Copy line": setClipboard(lineText); break;
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
                    if (clip != null)
                        content.replace(line, 0, line, content.getColumnCount(line), clip);
                    break;
            }
        } catch (Exception e) {
            new ErrorUtil(getActivity()).showError(e);
        }
    }

    public void showEditMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        forceShowIcons(popupMenu);
        String[] baseOptions = { "Copy line", "Cut line", "Delete line", "Empty line", "Replace line (with clipboard)",
                "Duplicate line", "Convert to uppercase", "Convert to lowercase", "Convert to sentence case",
                "Convert to camelcase", "Increase indent", "Decrease indent" };
        int[] baseIcons = {
                R.drawable.baseline_content_copy_24, R.drawable.baseline_content_cut_24,
                R.drawable.baseline_delete_24, R.drawable.baseline_remove_circle_24,
                R.drawable.reset_focus_24px, R.drawable.control_point_duplicate_24px,
                R.drawable.uppercase_24px, R.drawable.lowercase_24px,
                R.drawable.match_case_24px, R.drawable.match_case_off_24px,
                R.drawable.horizontal_align_right_24px, R.drawable.horizontal_align_left_24px };
        for (int i = 0; i < baseOptions.length; i++) {
            popupMenu.getMenu().add(0, i, 0, baseOptions[i]).setIcon(baseIcons[i]);
        }
        if (isSmali) {
            popupMenu.getMenu().add(0, 12, 0, "Toggle comment").setIcon(R.drawable.ic_hash_mt);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            executeEditAction(item.getItemId());
            return true;
        });
        popupMenu.show();
    }

    private void executeEditAction(int which) {
        Cursor cursor = editor.getCursor();
        int line = cursor.getLeftLine();
        int rightLine = cursor.getRightLine();
        Content content = editor.getText();
        String lineText = content.getLineString(line);
        switch (which) {
            case 0: setClipboard(lineText); break;
            case 1:
                setClipboard(lineText);
                content.delete(line, 0, line, content.getColumnCount(line));
                break;
            case 2:
                content.delete(line, 0, line, content.getColumnCount(line));
                if (line < content.getLineCount() - 1)
                    content.delete(line, content.getColumnCount(line), line + 1, 0);
                break;
            case 3: content.delete(line, 0, line, content.getColumnCount(line)); break;
            case 4:
                CharSequence clip = getClipboard();
                if (clip != null) content.replace(line, 0, line, content.getColumnCount(line), clip);
                break;
            case 5: content.insert(line, content.getColumnCount(line), "\n" + lineText); break;
            case 6:
                if (line == rightLine) content.replace(line, 0, line, content.getColumnCount(line), lineText.toUpperCase());
                else content.replace(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn(),
                        content.subContent(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn()).toString().toUpperCase());
                break;
            case 7:
                if (line == rightLine) content.replace(line, 0, line, content.getColumnCount(line), lineText.toLowerCase());
                else content.replace(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn(),
                        content.subContent(line, cursor.getLeftColumn(), rightLine, cursor.getRightColumn()).toString().toLowerCase());
                break;
            case 8:
                if (!lineText.isEmpty()) content.replace(line, 0, line, content.getColumnCount(line),
                        lineText.substring(0, 1).toUpperCase() + lineText.substring(1).toLowerCase());
                break;
            case 9:
                StringBuilder camel = new StringBuilder();
                boolean nextUpper = false;
                for (char c : lineText.toCharArray()) {
                    if (c == ' ' || c == '_' || c == '-') nextUpper = true;
                    else { camel.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c)); nextUpper = false; }
                }
                content.replace(line, 0, line, content.getColumnCount(line), camel);
                break;
            case 10:
                if (line == rightLine) content.insert(line, 0, "    ");
                else { content.beginBatchEdit(); for (int i = line; i <= rightLine; i++) content.insert(i, 0, "    "); content.endBatchEdit(); }
                break;
            case 11:
                if (line == rightLine) decreaseIndent(content, line);
                else { content.beginBatchEdit(); for (int i = line; i <= rightLine; i++) decreaseIndent(content, i); content.endBatchEdit(); }
                break;
            case 12:
                toggleComment();
                break;
        }
    }

    private void toggleComment() {
        Content content = editor.getText();
        Cursor cursor = editor.getCursor();
        int line = cursor.getLeftLine();
        int rightLine = cursor.getRightLine();
        for (int i = line; i <= rightLine; i++) {
            String lt = content.getLineString(i);
            String trimmed = lt.trim();
            if (trimmed.startsWith("#")) {
                int idx = lt.indexOf('#');
                content.delete(i, idx, i, idx + 1);
            } else {
                int indent = lt.length() - lt.trim().length();
                content.insert(i, indent > 0 ? indent : 0, "#");
            }
        }
    }

    private void decreaseIndent(Content content, int line) {
        String lt = content.getLineString(line);
        if (lt.startsWith("    ")) content.delete(line, 0, line, 4);
        else if (lt.startsWith("\t")) content.delete(line, 0, line, 1);
    }

    // ===== File operations menu =====
    public void showFileMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        forceShowIcons(popupMenu);
        java.util.List<String> optionsList = new java.util.ArrayList<>();
        java.util.List<Integer> iconsList = new java.util.ArrayList<>();
        optionsList.add("File"); iconsList.add(R.drawable.baseline_insert_drive_file_24);
        optionsList.add("Search"); iconsList.add(R.drawable.baseline_search_24);
        optionsList.add("Syntax"); iconsList.add(R.drawable.baseline_text_snippet_24);
        optionsList.add("Previous position"); iconsList.add(R.drawable.keyboard_double_arrow_left_24px);
        optionsList.add("Next position"); iconsList.add(R.drawable.keyboard_double_arrow_right_24px);
        optionsList.add("Jump to line"); iconsList.add(R.drawable.jump_to_element_24px);
        optionsList.add("Start of line"); iconsList.add(R.drawable.text_select_jump_to_beginning_24px);
        optionsList.add("End of line"); iconsList.add(R.drawable.text_select_jump_to_end_24px);
        optionsList.add("Word wrap"); iconsList.add(R.drawable.wrap_text_24px);
        optionsList.add("Read only"); iconsList.add(R.drawable.edit_off_24px);
        if (isSmali) {
            optionsList.add("Smali to Java"); iconsList.add(R.drawable.ic_java_mt);
            optionsList.add("Instructions query"); iconsList.add(R.drawable.ic_instruction_query_mt);
            optionsList.add("Method/Field list"); iconsList.add(R.drawable.ic_navigation);
        }
        optionsList.add("Preferences"); iconsList.add(R.drawable.baseline_settings_24);
        optionsList.add("Close file"); iconsList.add(R.drawable.baseline_exit_to_app_24);
        for (int i = 0; i < optionsList.size(); i++) {
            MenuItem item = popupMenu.getMenu().add(0, i, 0, optionsList.get(i));
            item.setIcon(iconsList.get(i));
            if (i == 3) item.setEnabled(canGoBack());
            else if (i == 4) item.setEnabled(canGoForward());
            else if (i == 8) { item.setCheckable(true); item.setChecked(editor.isWordwrap()); }
            else if (i == 9) { item.setCheckable(true); item.setChecked(!editor.isEditable()); }
        }
        int smaliOffset = isSmali ? 3 : 0;
        int prefIndex = 10 + smaliOffset;
        int closeIndex = 11 + smaliOffset;
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 0) { showSubFileMenu(anchor); }
            else if (id == 1) { searchPanel.setVisibility(View.VISIBLE); }
            else if (id == 2) { showSyntaxDialog(); }
            else if (id == 3) { navigateHistory(false); }
            else if (id == 4) { navigateHistory(true); }
            else if (id == 5) { showJumpToLineDialog(); }
            else if (id == 6) {
                Cursor c = editor.getCursor();
                recordPosition(c.getLeftLine(), c.getLeftColumn());
                editor.setSelection(c.getLeftLine(), 0);
            } else if (id == 7) {
                Cursor c2 = editor.getCursor();
                recordPosition(c2.getLeftLine(), c2.getLeftColumn());
                editor.setSelection(c2.getLeftLine(), editor.getText().getColumnCount(c2.getLeftLine()));
            } else if (id == 8) {
                boolean ww = !editor.isWordwrap();
                editor.setWordwrap(ww);
                requireContext().getSharedPreferences("editor_prefs", Context.MODE_PRIVATE).edit().putBoolean("wrap_text", ww).apply();
            } else if (id == 9) {
                editor.setEditable(!editor.isEditable());
            } else if (isSmali && id == 10) {
                smali2java();
            } else if (isSmali && id == 11) {
                showInstructionsQuery();
            } else if (isSmali && id == 12) {
                showMethodFieldList();
            } else if (id == prefIndex) {
                if (callback != null) {
                    callback.onPreferencesRequested();
                } else {
                    Activity act = getActivity();
                    if (act != null) act.startActivity(new Intent(act, io.github.abdurazaaqmohammed.ui.activities.EditorSettingsActivity.class));
                }
            } else if (id == closeIndex) {
                if (callback != null) callback.onCloseRequested();
            }
            return true;
        });
        popupMenu.show();
    }

    private void showSubFileMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        forceShowIcons(popupMenu);
        String[] options = { "Reload file", "Reload with charset", "Set encoding", "Set linebreak type", "Statistics" };
        int[] icons = {
                R.drawable.baseline_refresh_24, R.drawable.baseline_refresh_24,
                R.drawable.baseline_settings_24, R.drawable.baseline_swap_horiz_24,
                R.drawable.baseline_info_24 };
        for (int i = 0; i < options.length; i++) {
            popupMenu.getMenu().add(0, i, 0, options[i]).setIcon(icons[i]);
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: if (callback != null) callback.onSaveRequested(); break;
                case 1: showCharsetDialog(true); break;
                case 2: showCharsetDialog(false); break;
                case 3: showLinebreakDialog(); break;
                case 4: showStatistics(); break;
            }
            return true;
        });
        popupMenu.show();
    }

    public void showSyntaxDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.choose_syntax)
                .setItems(SYNTAXES, (dialog, which) -> Extensions.showMessage(requireActivity(), getString(R.string.syntax_set_to, SYNTAXES[which])))
                .show();
    }

    public void showJumpToLineDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.jump_to_line)
                .setView(input)
                .setPositiveButton(R.string.go, (dialog, which) -> {
                    CharSequence val = input.getText();
                    if (!TextUtils.isEmpty(val)) {
                        int line = Integer.parseInt(val.toString()) - 1;
                        if (line >= 0 && line < editor.getLineCount()) {
                            recordPosition(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
                            editor.setSelection(line, 0);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCharsetDialog(boolean reload) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(reload ? R.string.reload_with_charset : R.string.set_encoding))
                .setItems(CHARSETS, (dialog, which) -> {
                    currentCharset = CHARSETS[which];
                    Extensions.showMessage(requireActivity(), getString(R.string.encoding_set_to, currentCharset));
                })
                .show();
    }

    private void showLinebreakDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_linebreak_type)
                .setItems(LINEBREAKS, (dialog, which) -> {
                    LineSeparator ls = which == 0 ? LineSeparator.LF : which == 1 ? LineSeparator.CRLF : LineSeparator.CR;
                    editor.setLineSeparator(ls);
                    Extensions.showMessage(requireActivity(), getString(R.string.linebreak_type_set_to, LINEBREAKS[which]));
                })
                .show();
    }

    public void showStatistics() {
        String text = editor.getText().toString();
        int bytes = text.getBytes().length;
        int chars = text.length();
        int words = text.isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = editor.getLineCount();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.stats)
                .setMessage(getString(R.string.statss, bytes, chars, words, lines))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ===== Clipboard operations =====
    private void copySelection() {
        Cursor cursor = editor.getCursor();
        if (cursor.isSelected()) {
            Content text = editor.getText();
            StringBuilder sb = new StringBuilder();
            int startLine = cursor.getLeftLine(), startCol = cursor.getLeftColumn();
            int endLine = cursor.getRightLine(), endCol = cursor.getRightColumn();
            for (int i = startLine; i <= endLine; i++) {
                String lineStr = text.getLineString(i);
                if (startLine == endLine) sb.append(lineStr.substring(startCol, endCol));
                else if (i == startLine) sb.append(lineStr.substring(startCol)).append('\n');
                else if (i == endLine) sb.append(lineStr.substring(0, endCol));
                else sb.append(lineStr).append('\n');
            }
            setClipboard(sb);
        }
    }

    private void cutSelection() {
        Cursor cursor = editor.getCursor();
        if (cursor.isSelected()) {
            Content text = editor.getText();
            StringBuilder sb = new StringBuilder();
            int startLine = cursor.getLeftLine(), startCol = cursor.getLeftColumn();
            int endLine = cursor.getRightLine(), endCol = cursor.getRightColumn();
            for (int i = startLine; i <= endLine; i++) {
                String lineStr = text.getLineString(i);
                if (startLine == endLine) sb.append(lineStr.substring(startCol, endCol));
                else if (i == startLine) sb.append(lineStr.substring(startCol)).append('\n');
                else if (i == endLine) sb.append(lineStr.substring(0, endCol));
                else sb.append(lineStr).append('\n');
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
            if (cursor.isSelected())
                text.replace(cursor.getLeftLine(), cursor.getLeftColumn(), cursor.getRightLine(), cursor.getRightColumn(), clip);
            else text.insert(cursor.getLeftLine(), cursor.getLeftColumn(), clip);
        }
    }

    private void setClipboard(CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("editor_text", text));
    }

    private CharSequence getClipboard() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0)
            return clipboard.getPrimaryClip().getItemAt(0).getText();
        return null;
    }

    // ===== Smali-specific features =====
    public void navigateTo(int lineNum, String query) { navigateTo(lineNum, -1, query); }

    public void navigateTo(final int lineNum, final int column, final String query) {
        if (editor == null) return;
        if (editor.getText().getLineCount() <= lineNum) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> navigateTo(lineNum, column, query), 100);
            return;
        }
        try {
            if (lineNum >= 0 && lineNum < editor.getText().getLineCount()) {
                editor.jumpToLine(lineNum);
                if (column >= 0 && column < editor.getText().getColumnCount(lineNum))
                    editor.getCursor().set(lineNum, column);
                String lineText = editor.getText().getLineString(lineNum);
                if (query != null && !query.isEmpty() && !query.contains("\n")) {
                    int start = lineText.toLowerCase().indexOf(query.toLowerCase());
                    if (start != -1) {
                        editor.setSelectionRegion(lineNum, start, lineNum, start + query.length());
                        dismissEditorWindow(editor);
                        return;
                    }
                }
                if (isSmali && lineText.contains("const-string")) {
                    int[] positions = SmaliHelper.getOuterQuotePositions(lineText);
                    if (positions[0] != -1 && positions[1] != -1) {
                        editor.setSelectionRegion(lineNum, positions[0] + 1, lineNum, positions[1]);
                        dismissEditorWindow(editor);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void showMethodFieldList() {
        if (!isSmali) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                saveSmaliCodeToFile(editor.getText().toString(), tempSmaliPath, path -> {
                    if (getActivity() instanceof DexEditorActivity)
                        ((DexEditorActivity) getActivity()).showSmaliNavigation(path, title, editor.getCursor().getLeftLine());
                });
            } catch (Exception e) {
                if (getContext() != null)
                    Notify_MT.Notify(getContext(), getString(R.string.error), e.toString(), getString(R.string.close));
            }
        });
    }

    private void saveSmaliCodeToFile(String content, String filePath, FileSaveCallback callback) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
        }
        if (callback != null) callback.onFileSaved(filePath);
    }

    private interface FileSaveCallback { void onFileSaved(String filePath); }

    public void extractMethodFieldInfo(final String target) {
        if (!isSmali) return;
        new ExtractMethodFieldInfoTask(this, target).execute();
    }

    @Override
    public void _updateEditorLineNumber(String lineNumber) {
        if (lineNumber == null || lineNumber.isEmpty()) return;
        try {
            int lineNum = (int) Math.floor(Double.parseDouble(lineNumber));
            navigateTo(lineNum, null);
        } catch (Exception e) {
           Extensions.showMessage(requireActivity(), getString(R.string.invalid_line_number, lineNumber));
        }
    }

    public void smali2java() {
        Activity act = getActivity();
        if (act instanceof modder.hub.dexeditor.activity.DexEditorActivity) {
            ((modder.hub.dexeditor.activity.DexEditorActivity) act).smali2java(UnifiedEditorFragment.this);
        }
    }

    public void showInstructionsQuery() {
        String instruction = getCurrentLineSmaliInstruction();
        if (instruction != null) {
            new SmaliInstructionsDialog(requireContext(), "smali_instructions.txt", instruction).show();
        } else {
            new SmaliInstructionsDialog(requireContext(), "smali_instructions.txt").show();
        }
    }

    private class TextActionCallback implements TextActionWindow.ItemClickCallBack {
        private final String currentClassName;
        TextActionCallback(String className) { this.currentClassName = className; }

        @Override
        public void onClickGoTo(View view, String text) {
            if (text.startsWith(":")) {
                showLabelsCompletion(text.replace("}", ""));
            } else {
                Activity activity = getActivity();
                if (activity instanceof DexEditorActivity)
                    ((DexEditorActivity) activity).goTo(text, currentClassName);
            }
        }

        @Override
        public void onClickTranslate(View view, String text) {
            if (!sharedPreferences.contains("selectedPackage")) {
                Activity _context = requireActivity();
                Extensions.showMessage(_context, R.string.sel_tl);
                showAvailableTranslationDlg();
                return;
            }
            try {
                String packageName = sharedPreferences.getString("selectedPackage", "");
                packageManager.getPackageInfo(packageName, 0);
                Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
                intent.setType("text/plain");
                intent.putExtra("android.intent.extra.PROCESS_TEXT", text);
                intent.putExtra("android.intent.extra.PROCESS_TEXT_READONLY", true);
                intent.setPackage(packageName);
                startActivity(intent);
            } catch (PackageManager.NameNotFoundException e) {
                preferencesEditor.remove("selectedPackage");
                preferencesEditor.apply();
                showAvailableTranslationDlg();
            }
        }

        @Override
        public void onLongClickTranslate(View view) { showAvailableTranslationDlg(); }
    }

    private void showLabelsCompletion(final String query) {
        int editorLineNumber = editor.getCursor().getLeftLine();
        List<String> labelList = SmaliCursorUtils.extractAllLabelLines(editor.getText(), currentMethodInfo);
        if (labelList.isEmpty()) labelList = SmaliCursorUtils.extractAllLabelLines(editor.getText(), editorLineNumber);
        SmaliLabelDialog dialog = new SmaliLabelDialog(requireContext(), labelList, query, editorLineNumber);
        dialog.setOnLabelClickListener(selectedLabel -> {
            int lineNumber = Integer.parseInt(selectedLabel.substring(1, selectedLabel.indexOf(']'))) - 1;
            String lineContent = editor.getText().getLineString(lineNumber);
            int columnPos = lineContent.indexOf(query);
            if (columnPos >= 0) {
                editor.setSelection(lineNumber, columnPos);
                editor.ensurePositionVisible(lineNumber, columnPos);
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showAvailableTranslationDlg() {
        Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setType("text/plain");
        final List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.available_tl)
                .setSingleChoiceItems(resolveInfoList.stream()
                        .map(ri -> ri.activityInfo.applicationInfo.loadLabel(packageManager) + " - " + ri.loadLabel(packageManager))
                        .toArray(String[]::new),
                        -1, (dialog, which) -> {})
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    // handled via listview
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ===== Static language/theme caching =====
    public static void clearCache() {
        cachedSmaliLanguage = null;
        cachedColorScheme = null;
        cachedInstructions = null;
    }

    public static synchronized void ensureLanguageInitialized(Context context) {
        if (cachedSmaliLanguage != null) return;
        try {
            initTMStatic(context);
            ThemeRegistry registry = ThemeRegistry.getInstance();
            boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int theme = PreferenceManager.getDefaultSharedPreferences(context).getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light);
            String themeName = theme == R.style.Theme_MyApp_Light ? "light.json" : "dark.json";
            IThemeSource themeSource = null;
            try {
                themeSource = IThemeSource.fromInputStream(context.getAssets().open( "themes/" + themeName), themeName, null);
                registry.loadTheme(themeSource);
                registry.setTheme(themeName);
            } catch (Exception e) { Log.e("UnifiedEditor", "Theme load error", e); }
            try {
                cachedSmaliLanguage = TextMateLanguage.create(
                        IGrammarSource.fromInputStream(context.getAssets().open("smali/syntaxes/smali.tmLanguage.json"), "smali.tmLanguage.json", null),
                        new InputStreamReader(context.getAssets().open("smali/language-configuration.json")),
                        themeSource);
            } catch (Exception e) { Log.e("UnifiedEditor", "Smali language load error", e); }
            cachedColorScheme = TextMateColorScheme.create(registry);
            cachedInstructions = SmaliInstructionHelper.getAllSmaliInstructions();
        } catch (Exception e) { Log.e("UnifiedEditor", "Static init error", e); }
    }

    private static void initTMStatic(Context context) {
        if (tmRegistered) return;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(context.getAssets()));
            tmRegistered = true;
        } catch (Exception ignored) {}
    }

    public static void dismissEditorWindow(final CodeEditor smaliEditor) {
        if (smaliEditor == null) return;
        smaliEditor.postDelayedInLifecycle(() -> {
            try { smaliEditor.hideEditorWindows(); } catch (Exception ignored) {}
        }, 50);
    }

    public String getCurrentLineSmaliInstruction() {
        if (!isSmali) return null;
        Cursor cursor = editor.getCursor();
        Content content = editor.getText();
        int line = cursor.getLeftLine();
        String lineText = content.getLineString(line);
        String trimmed = lineText.trim();
        if (trimmed.isEmpty()) return null;
        int endOfFirstWord = 0;
        while (endOfFirstWord < trimmed.length()) {
            char c = trimmed.charAt(endOfFirstWord);
            if (Character.isWhitespace(c) || c == '{' || c == '}' || c == ';') break;
            endOfFirstWord++;
        }
        String firstWord = trimmed.substring(0, endOfFirstWord);
        return SmaliInstructionHelper.isSmaliInstruction(firstWord) ? firstWord : null;
    }

    // ===== ExtractMethodFieldInfoTask =====
    private static class ExtractMethodFieldInfoTask {
        private final WeakReference<UnifiedEditorFragment> fragmentRef;
        private final String target;
        private final Content text;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        ExtractMethodFieldInfoTask(UnifiedEditorFragment fragment, String target) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.target = target;
            this.text = fragment.editor.getText();
        }

        void execute() {
            new Thread(() -> {
                final TextLocation location = doInBackground();
                mainHandler.post(() -> onPostExecute(location));
            }).start();
        }

        protected TextLocation doInBackground() {
            try {
                if (target.contains(":")) return findFieldLocation(text, target);
                else return findMethodLocation(text, target);
            } catch (Exception e) { return null; }
        }

        protected void onPostExecute(TextLocation location) {
            UnifiedEditorFragment fragment = fragmentRef.get();
            if (fragment != null && fragment.isAdded() && location != null) {
                int lineNumber = location.lineNumber - 1;
                fragment.editor.jumpToLine(lineNumber);
                mainHandler.postDelayed(() -> {
                    try {
                        fragment.editor.setSelectionRegion(lineNumber, location.startColumn, lineNumber, location.endColumn);
                        dismissEditorWindow(fragment.editor);
                    } catch (Exception ignored) {}
                }, 100);
            }
        }

        private TextLocation findMethodLocation(Content text, String methodName) {
            for (int i = 0; i < text.getLineCount(); i++) {
                String line = text.getLineString(i);
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty()) {
                    String[] parts = trimmedLine.split(" ");
                    if (parts.length > 0 && ".method".equals(parts[0]) && parts[parts.length - 1].equals(methodName)) {
                        int startIndex = line.indexOf(methodName);
                        int endIndex = (methodName.contains("(") ? methodName.indexOf("(") : methodName.length()) + startIndex;
                        return new TextLocation(i + 1, startIndex, endIndex);
                    }
                }
            }
            return null;
        }

        private TextLocation findFieldLocation(Content text, String fieldName) {
            for (int i = 0; i < text.getLineCount(); i++) {
                String line = text.getLineString(i);
                if (line.trim().startsWith(".field") && line.contains(fieldName)) {
                    int startIndex = line.indexOf(fieldName);
                    int endIndex = (fieldName.contains(":") ? fieldName.indexOf(":") : fieldName.length()) + startIndex;
                    return new TextLocation(i + 1, startIndex, endIndex);
                }
            }
            return null;
        }
    }

    private static class TextLocation {
        int lineNumber, startColumn, endColumn;
        TextLocation(int lineNumber, int startColumn, int endColumn) {
            this.lineNumber = lineNumber; this.startColumn = startColumn; this.endColumn = endColumn;
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
            new ErrorUtil(getActivity()).showError(e);
        }
    }
}
