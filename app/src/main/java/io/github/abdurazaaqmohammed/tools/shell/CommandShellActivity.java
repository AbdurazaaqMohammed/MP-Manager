package io.github.abdurazaaqmohammed.tools.shell;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.NestedScrollView;
import android.widget.HorizontalScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * Full Command Shell Activity honoring Root and Shizuku modes,
 * with complete aShell-matched interface, tabs, command bookmarks,
 * history, auto-completion, search filter, intelligent command translation,
 * and .sh script execution.
 */
public class CommandShellActivity extends BaseActivity {

    private static final String PREF_NAME = "command_shell_prefs";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";
    private static final int REQUEST_CODE_PICK_FILE = 2026;

    private TabLayout tabLayout;
    private View shellTabView;
    private View filesTabView;

    // Shell tab components
    private MaterialButton historyBtn, bookmarksBtn, clearBtn, copyAllBtn, searchBtn;
    private MaterialCardView searchCard;
    private TextInputEditText searchWordEdit;
    private TextInputEditText commandEdit;
    private AppCompatImageButton bookmarkStarBtn;
    private MaterialButton sendBtn;
    private RecyclerView commandsRv;
    private NestedScrollView outputVerticalScroll;
    private HorizontalScrollView outputHorizontalScroll;
    private TextView terminalOutputTv;
    private MaterialButton topBtn, bottomBtn;
    private MaterialButton saveBtn;

    // Files tab components
    private MaterialButton filesBackBtn;
    private MaterialButton filesPickExternalBtn;
    private RecyclerView breadcrumbsRv;
    private RecyclerView foldersRv;
    private RecyclerView filesRv;

    // Adapters & Data
    private final List<String> outputLines = new CopyOnWriteArrayList<>();
    private final List<String> filteredLines = new ArrayList<>();
    private CommandSuggestionAdapter suggestionAdapter;
    private BreadcrumbAdapter breadcrumbAdapter;
    private FolderAdapter folderAdapter;
    private FileAdapter fileAdapter;

    private final List<String> currentPathSegments = new ArrayList<>();
    private String currentDirectoryPath = "/sdcard";

    private SharedPreferences prefs;
    private final Set<String> bookmarkSet = new HashSet<>();
    private final List<String> historyList = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Process activeProcess = null;
    private boolean isProcessRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_shell);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadBookmarksAndHistory();

        initViews();
        setupTabs();
        setupShellActions();
        setupFilesTab();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("script_path")) {
            String scriptPath = intent.getStringExtra("script_path");
            if (scriptPath != null && !scriptPath.trim().isEmpty()) {
                commandEdit.setText("sh " + scriptPath);
                commandEdit.setSelection(commandEdit.getText().length());
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchCard.getVisibility() == View.VISIBLE) {
                    hideSearchCard();
                    return;
                }
                if (isProcessRunning) {
                    new MaterialAlertDialogBuilder(CommandShellActivity.this)
                            .setTitle(R.string.command_shell)
                            .setMessage(R.string.clear_all_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.yes, (d, w) -> stopActiveProcess())
                            .show();
                    return;
                }
                finish();
            }
        });
    }

    private void loadBookmarksAndHistory() {
        Set<String> saved = prefs.getStringSet(KEY_BOOKMARKS, null);
        if (saved != null) {
            bookmarkSet.addAll(saved);
        }
        String hist = prefs.getString(KEY_HISTORY, "");
        if (!TextUtils.isEmpty(hist)) {
            String[] arr = hist.split("\n");
            for (String h : arr) {
                if (!h.trim().isEmpty() && !historyList.contains(h.trim())) {
                    historyList.add(h.trim());
                }
            }
        }
    }

    private void saveBookmarks() {
        prefs.edit().putStringSet(KEY_BOOKMARKS, new HashSet<>(bookmarkSet)).apply();
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, historyList.size() - 100);
        for (int i = start; i < historyList.size(); i++) {
            sb.append(historyList.get(i)).append("\n");
        }
        prefs.edit().putString(KEY_HISTORY, sb.toString()).apply();
    }

    private void initViews() {
        findViewById(R.id.shell_back_btn).setOnClickListener(v -> finish());
        findViewById(R.id.settings_button).setOnClickListener(v -> showModeDialog());

        tabLayout = findViewById(R.id.tab_layout);
        shellTabView = findViewById(R.id.shell_tab_view);
        filesTabView = findViewById(R.id.files_tab_view);

        historyBtn = findViewById(R.id.history);
        bookmarksBtn = findViewById(R.id.bookmarks);
        clearBtn = findViewById(R.id.clear);
        copyAllBtn = findViewById(R.id.copy_all);
        searchBtn = findViewById(R.id.search);

        searchCard = findViewById(R.id.search_card);
        searchWordEdit = findViewById(R.id.search_word);

        commandEdit = findViewById(R.id.shell_command);
        bookmarkStarBtn = findViewById(R.id.bookmark);
        sendBtn = findViewById(R.id.send);

        commandsRv = findViewById(R.id.recycler_view_commands);
        commandsRv.setLayoutManager(new LinearLayoutManager(this));
        suggestionAdapter = new CommandSuggestionAdapter(this::applySuggestedCommand);
        commandsRv.setAdapter(suggestionAdapter);

        outputVerticalScroll = findViewById(R.id.output_vertical_scroll);
        outputHorizontalScroll = findViewById(R.id.output_horizontal_scroll);
        terminalOutputTv = findViewById(R.id.terminal_output_tv);

        topBtn = findViewById(R.id.top);
        bottomBtn = findViewById(R.id.bottom);
        saveBtn = findViewById(R.id.save_button);

        topBtn.setOnClickListener(v -> {
            outputVerticalScroll.fullScroll(View.FOCUS_UP);
            outputHorizontalScroll.fullScroll(View.FOCUS_LEFT);
        });
        bottomBtn.setOnClickListener(v -> outputVerticalScroll.fullScroll(View.FOCUS_DOWN));
        saveBtn.setOnClickListener(v -> saveOutputLog());

        updateQuickButtonsState();
    }

    private void setupTabs() {
        TabLayout.Tab shellTab = tabLayout.newTab().setText(R.string.tab_shell);
        TabLayout.Tab filesTab = tabLayout.newTab().setText(R.string.tab_files);
        tabLayout.addTab(shellTab);
        tabLayout.addTab(filesTab);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    shellTabView.setVisibility(View.VISIBLE);
                    filesTabView.setVisibility(View.GONE);
                } else {
                    shellTabView.setVisibility(View.GONE);
                    filesTabView.setVisibility(View.VISIBLE);
                    loadDirectory(currentDirectoryPath);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupShellActions() {
        historyBtn.setOnClickListener(v -> showHistoryDialog());
        bookmarksBtn.setOnClickListener(v -> showBookmarksDialog());
        clearBtn.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.clear_all)
                    .setMessage(R.string.clear_all_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.yes, (d, w) -> clearOutput())
                    .show();
        });
        copyAllBtn.setOnClickListener(v -> copyAllOutput());
        searchBtn.setOnClickListener(v -> {
            searchCard.setVisibility(View.VISIBLE);
            searchWordEdit.requestFocus();
        });

        searchWordEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterOutput(s != null ? s.toString().trim() : "");
            }
        });

        commandEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String cmd = s != null ? s.toString() : "";
                if (cmd.contains("\n")) {
                    String clean = cmd.replace("\n", "").trim();
                    commandEdit.setText(clean);
                    if (!clean.isEmpty()) executeCommand(clean);
                    return;
                }
                updateCommandBoxState(cmd);
            }
        });

        bookmarkStarBtn.setOnClickListener(v -> {
            String cmd = getEnteredCommand();
            if (!cmd.isEmpty()) {
                toggleBookmark(cmd);
            }
        });

        commandEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                String cmd = getEnteredCommand();
                if (!cmd.isEmpty() && !isProcessRunning) {
                    executeCommand(cmd);
                    return true;
                }
            }
            return false;
        });

        sendBtn.setOnClickListener(v -> {
            if (isProcessRunning) {
                stopActiveProcess();
                return;
            }
            String cmd = getEnteredCommand();
            if (cmd.isEmpty()) {
                showExamplesDialog();
            } else {
                executeCommand(cmd);
            }
        });
    }

    private String getEnteredCommand() {
        return commandEdit.getText() != null ? commandEdit.getText().toString().trim() : "";
    }

    private void updateCommandBoxState(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            sendBtn.setIconResource(R.drawable.ic_help);
            sendBtn.setContentDescription(getString(R.string.examples));
            bookmarkStarBtn.setVisibility(View.GONE);
            commandsRv.setVisibility(View.GONE);
        } else {
            sendBtn.setIconResource(R.drawable.ic_send);
            sendBtn.setContentDescription(getString(R.string.command_title));
            bookmarkStarBtn.setVisibility(View.VISIBLE);
            boolean isBookmarked = bookmarkSet.contains(trimmed);
            bookmarkStarBtn.setImageResource(isBookmarked ? R.drawable.ic_starred : R.drawable.ic_star);
            int primaryColor = MaterialColors.getColor(this, android.R.attr.colorPrimary, 0xFF00ADB5);
            ImageViewCompat.setImageTintList(bookmarkStarBtn, ColorStateList.valueOf(primaryColor));

            if (isProcessRunning) {
                commandsRv.setVisibility(View.GONE);
                return;
            }

            // Suggestions: only show while user is actively entering text and suggestions are not hidden
            List<CommandEntry> matches = ShellCommandsDb.searchCommands(trimmed);
            if (!matches.isEmpty()) {
                suggestionAdapter.setItems(matches);
                commandsRv.setVisibility(View.VISIBLE);
            } else {
                commandsRv.setVisibility(View.GONE);
            }
        }
    }

    private void applySuggestedCommand(String cmd) {
        String clean = cmd.contains("<") ? cmd.split("<")[0].trim() : cmd;
        commandsRv.setVisibility(View.GONE);
        suggestionAdapter.setItems(Collections.emptyList());
        commandEdit.setText(clean);
        commandEdit.setSelection(clean.length());
        commandsRv.setVisibility(View.GONE);
    }

    private void toggleBookmark(String cmd) {
        if (bookmarkSet.contains(cmd)) {
            bookmarkSet.remove(cmd);
            Toast.makeText(this, getString(R.string.bookmark_removed_message, cmd), Toast.LENGTH_SHORT).show();
        } else {
            bookmarkSet.add(cmd);
            Toast.makeText(this, getString(R.string.bookmark_added_message, cmd), Toast.LENGTH_SHORT).show();
        }
        saveBookmarks();
        updateCommandBoxState(commandEdit.getText() != null ? commandEdit.getText().toString() : "");
        updateQuickButtonsState();
    }

    private void updateQuickButtonsState() {
        bookmarksBtn.setEnabled(!bookmarkSet.isEmpty());
        historyBtn.setEnabled(!historyList.isEmpty());
        clearBtn.setEnabled(!outputLines.isEmpty());
        copyAllBtn.setEnabled(!outputLines.isEmpty());
        searchBtn.setEnabled(!outputLines.isEmpty());
        saveBtn.setVisibility(outputLines.isEmpty() ? View.GONE : View.VISIBLE);
        boolean showArrows = outputLines.size() > 25;
        topBtn.setVisibility(showArrows ? View.VISIBLE : View.GONE);
        bottomBtn.setVisibility(showArrows ? View.VISIBLE : View.GONE);
    }

    private void copyAllOutput() {
        if (filteredLines.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filteredLines.size(); i++) {
            sb.append(filteredLines.get(i));
            if (i < filteredLines.size() - 1) {
                sb.append('\n');
            }
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Shell Output", sb.toString()));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void clearOutput() {
        outputLines.clear();
        filteredLines.clear();
        terminalOutputTv.setText("");
        updateQuickButtonsState();
    }

    private void hideSearchCard() {
        searchWordEdit.setText("");
        searchCard.setVisibility(View.GONE);
        filterOutput("");
    }

    private void filterOutput(String query) {
        filteredLines.clear();
        if (TextUtils.isEmpty(query)) {
            filteredLines.addAll(outputLines);
        } else {
            String lower = query.toLowerCase(Locale.ROOT);
            for (String line : outputLines) {
                if (line.toLowerCase(Locale.ROOT).contains(lower)) {
                    filteredLines.add(line);
                }
            }
        }
        renderTerminalText();
    }

    private void renderTerminalText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filteredLines.size(); i++) {
            sb.append(filteredLines.get(i));
            if (i < filteredLines.size() - 1) {
                sb.append('\n');
            }
        }
        terminalOutputTv.setText(sb.toString());
    }

    /**
     * Translates PC/ADB styled commands into native on-device Android shell commands.
     * E.g. "adb shell pm list packages" -> "pm list packages"
     * "adb -s <id> shell cmd" -> "cmd"
     * "su -c 'cmd'" -> "cmd"
     */
    public static String translateToDeviceShell(String rawCommand) {
        if (rawCommand == null) return "";
        String cmd = rawCommand.trim();

        // Strip leading adb variations
        if (cmd.startsWith("adb ")) {
            cmd = cmd.substring(4).trim();
            // Handle optional device selectors like -d, -e, -s <serial>
            while (cmd.startsWith("-")) {
                if (cmd.startsWith("-s ") || cmd.startsWith("-t ")) {
                    int nextSpace = cmd.indexOf(' ', 3);
                    if (nextSpace != -1) {
                        cmd = cmd.substring(nextSpace + 1).trim();
                    } else {
                        cmd = "";
                        break;
                    }
                } else if (cmd.startsWith("-d ") || cmd.startsWith("-e ")) {
                    cmd = cmd.substring(3).trim();
                } else {
                    break;
                }
            }
            if (cmd.startsWith("shell ")) {
                cmd = cmd.substring(6).trim();
            } else if (cmd.equals("shell")) {
                cmd = "whoami";
            }
        } else if (cmd.startsWith("adb\t")) {
            cmd = cmd.substring(4).trim();
            if (cmd.startsWith("shell")) {
                cmd = cmd.substring(5).trim();
            }
        }

        // Strip unwrapped su -c wrappers if user typed them manually
        if (cmd.startsWith("su -c ")) {
            cmd = cmd.substring(6).trim();
            if ((cmd.startsWith("\"") && cmd.endsWith("\"")) || (cmd.startsWith("'") && cmd.endsWith("'"))) {
                if (cmd.length() >= 2) {
                    cmd = cmd.substring(1, cmd.length() - 1);
                }
            }
        } else if (cmd.equals("su")) {
            cmd = "id";
        }

        return cmd.trim();
    }

    private void executeCommand(String cmd) {
        commandsRv.setVisibility(View.GONE);
        suggestionAdapter.setItems(Collections.emptyList());
        if (!historyList.contains(cmd)) {
            historyList.add(cmd);
            saveHistory();
        }
        updateQuickButtonsState();

        final String nativeCmd = translateToDeviceShell(cmd);
        appendOutputLine("$ " + cmd);
        if (!nativeCmd.equals(cmd)) {
            appendOutputLine("[Auto-translated] > " + nativeCmd);
        }

        isProcessRunning = true;
        sendBtn.setIconResource(R.drawable.ic_cancel);

        executor.execute(() -> {
            RootManager rm = RootManager.getInstance(this);
            boolean rootAvailable = rm.isRootAvailable();
            boolean rootMode = rm.isRootMode();
            boolean shizukuAvailable = ShizukuShell.isAvailable();
            boolean shizukuMode = rm.isShizukuMode();

            try {
                // If Root mode is enabled OR root is directly available, execute with Root su
                if (rootMode || rootAvailable) {
                    executeRootProcess(nativeCmd);
                } else if (shizukuMode || shizukuAvailable) {
                    executeShizukuCommand(nativeCmd);
                } else {
                    executeStandardProcess(nativeCmd);
                }
            } catch (Exception e) {
                appendOutputLine("Error: " + e.getMessage());
            } finally {
                mainHandler.post(() -> {
                    isProcessRunning = false;
                    commandsRv.setVisibility(View.GONE);
                    suggestionAdapter.setItems(Collections.emptyList());
                    updateCommandBoxState(getEnteredCommand());
                    commandsRv.setVisibility(View.GONE);
                    updateQuickButtonsState();
                });
            }
        });
    }

    private void executeRootProcess(String cmd) throws Exception {
        RootManager rm = RootManager.getInstance(this);
        String suBinary = rm.suBinary();
        if (suBinary == null || suBinary.trim().isEmpty()) {
            suBinary = "su";
        }

        ProcessBuilder pb = new ProcessBuilder(suBinary, "-c", cmd);
        pb.redirectErrorStream(true);
        activeProcess = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendOutputLine(line);
            }
        }
        int code = activeProcess.waitFor();
        appendOutputLine("[Process completed with exit code " + code + "]");
    }

    private void executeShizukuCommand(String cmd) {
        ShizukuShell.Result result = ShizukuShell.exec(cmd, 300);
        if (result != null) {
            if (!TextUtils.isEmpty(result.stdout)) {
                String[] lines = result.stdout.split("\n");
                for (String l : lines) appendOutputLine(l);
            }
            if (!TextUtils.isEmpty(result.stderr)) {
                String[] errs = result.stderr.split("\n");
                for (String e : errs) appendOutputLine("[stderr] " + e);
            }
            appendOutputLine("[Shizuku completed with exit code " + result.exitCode + "]");
        } else {
            appendOutputLine("[Shizuku returned null result]");
        }
    }

    private void executeStandardProcess(String cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
        pb.redirectErrorStream(true);
        activeProcess = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendOutputLine(line);
            }
        }
        int code = activeProcess.waitFor();
        appendOutputLine("[Process completed with exit code " + code + "]");
    }

    private void stopActiveProcess() {
        if (activeProcess != null) {
            try {
                activeProcess.destroy();
            } catch (Exception ignored) {}
        }
        isProcessRunning = false;
        appendOutputLine("[Process stopped by user]");
        commandsRv.setVisibility(View.GONE);
        suggestionAdapter.setItems(Collections.emptyList());
        updateCommandBoxState(getEnteredCommand());
        commandsRv.setVisibility(View.GONE);
        updateQuickButtonsState();
    }

    private void appendOutputLine(String line) {
        outputLines.add(line);
        mainHandler.post(() -> {
            filteredLines.add(line);
            CharSequence current = terminalOutputTv.getText();
            if (TextUtils.isEmpty(current)) {
                terminalOutputTv.setText(line);
            } else {
                terminalOutputTv.append("\n" + line);
            }
            outputVerticalScroll.post(() -> outputVerticalScroll.fullScroll(View.FOCUS_DOWN));
            updateQuickButtonsState();
        });
    }

    private void saveOutputLog() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File logFile = new File(downloadDir, "shell_output_" + time + ".txt");
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                for (String line : outputLines) {
                    fos.write((line + "\n").getBytes());
                }
            }
            Toast.makeText(this, "Saved to " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showModeDialog() {
        RootManager rm = RootManager.getInstance(this);
        String currentMode = rm.isRootMode() ? "Root (su)" :
                (rm.isShizukuMode() ? "Shizuku (rish)" : "Auto (Root if available, else Shizuku)");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.command_shell)
                .setMessage(getString(R.string.shell_mode_status, currentMode) + "\n\n" +
                        "Root Available: " + rm.isRootAvailable() + "\n" +
                        "Shizuku Available: " + ShizukuShell.isAvailable())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showBookmarksDialog() {
        List<String> list = new ArrayList<>(bookmarkSet);
        Collections.sort(list);
        showListDialog(getString(R.string.bookmarks), R.drawable.ic_bookmarks, list, (cmd, delete) -> {
            if (delete) {
                bookmarkSet.remove(cmd);
                saveBookmarks();
                updateQuickButtonsState();
                updateCommandBoxState(getEnteredCommand());
            } else {
                commandEdit.setText(cmd);
                commandEdit.setSelection(cmd.length());
            }
        });
    }

    private void showHistoryDialog() {
        List<String> list = new ArrayList<>(historyList);
        Collections.reverse(list);
        showListDialog(getString(R.string.history), R.drawable.ic_history, list, (cmd, delete) -> {
            if (delete) {
                historyList.remove(cmd);
                saveHistory();
                updateQuickButtonsState();
            } else {
                commandEdit.setText(cmd);
                commandEdit.setSelection(cmd.length());
            }
        });
    }

    private void showExamplesDialog() {
        List<CommandEntry> examples = ShellCommandsDb.getPopularExamples();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_shell_list_dialog, null);
        TextView titleTv = view.findViewById(R.id.title);
        AppCompatImageButton iconIv = view.findViewById(R.id.icon);
        RecyclerView rv = view.findViewById(R.id.recycler_view);
        MaterialButton cancelBtn = view.findViewById(R.id.cancel);

        titleTv.setText(R.string.examples);
        iconIv.setImageResource(R.drawable.ic_help);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .create();

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        rv.setLayoutManager(new LinearLayoutManager(this));
        CommandSuggestionAdapter adapter = new CommandSuggestionAdapter(cmd -> {
            applySuggestedCommand(cmd);
            dialog.dismiss();
        });
        adapter.setItems(examples);
        rv.setAdapter(adapter);

        dialog.show();
    }

    private void showListDialog(String title, int iconRes, List<String> items, DialogItemCallback callback) {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_shell_list_dialog, null);
        TextView titleTv = view.findViewById(R.id.title);
        AppCompatImageButton iconIv = view.findViewById(R.id.icon);
        RecyclerView rv = view.findViewById(R.id.recycler_view);
        MaterialButton cancelBtn = view.findViewById(R.id.cancel);

        titleTv.setText(title);
        iconIv.setImageResource(iconRes);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .create();

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        rv.setLayoutManager(new LinearLayoutManager(this));

        ListDialogAdapter adapter = new ListDialogAdapter(items, new ListDialogAdapter.Listener() {
            @Override
            public void onSelect(String item) {
                callback.onAction(item, false);
                dialog.dismiss();
            }

            @Override
            public void onDelete(String item) {
                callback.onAction(item, true);
                dialog.dismiss();
            }
        });
        rv.setAdapter(adapter);
        dialog.show();
    }

    private interface DialogItemCallback {
        void onAction(String item, boolean delete);
    }

    // ==========================================
    // Files Tab Implementation (.sh scripts)
    // ==========================================
    private void setupFilesTab() {
        filesBackBtn = findViewById(R.id.files_back_btn);
        filesPickExternalBtn = findViewById(R.id.files_pick_external);
        breadcrumbsRv = findViewById(R.id.recycler_view_title);
        foldersRv = findViewById(R.id.recycler_view_folders);
        filesRv = findViewById(R.id.recycler_view_files);

        breadcrumbsRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        breadcrumbAdapter = new BreadcrumbAdapter(this::onBreadcrumbClicked);
        breadcrumbsRv.setAdapter(breadcrumbAdapter);

        foldersRv.setLayoutManager(new LinearLayoutManager(this));
        folderAdapter = new FolderAdapter(this::onFolderClicked);
        foldersRv.setAdapter(folderAdapter);

        filesRv.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(this::onFileClicked);
        filesRv.setAdapter(fileAdapter);

        filesBackBtn.setOnClickListener(v -> navigateUpFolder());
        filesPickExternalBtn.setOnClickListener(v -> pickExternalScriptFile());
    }

    private void loadDirectory(String path) {
        currentDirectoryPath = path;
        updateBreadcrumbs(path);

        executor.execute(() -> {
            File dir = new File(path);
            File[] all = dir.listFiles();
            List<File> folders = new ArrayList<>();
            List<File> files = new ArrayList<>();

            if (all != null) {
                for (File f : all) {
                    if (f.isDirectory()) {
                        folders.add(f);
                    } else {
                        files.add(f);
                    }
                }
            }
            Collections.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            mainHandler.post(() -> {
                folderAdapter.setItems(folders);
                fileAdapter.setItems(files);
            });
        });
    }

    private void updateBreadcrumbs(String path) {
        currentPathSegments.clear();
        String[] parts = path.split("/");
        for (String p : parts) {
            if (!p.trim().isEmpty()) currentPathSegments.add(p);
        }
        breadcrumbAdapter.setSegments(currentPathSegments);
    }

    private void onBreadcrumbClicked(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= index; i++) {
            sb.append("/").append(currentPathSegments.get(i));
        }
        loadDirectory(sb.toString());
    }

    private void navigateUpFolder() {
        File f = new File(currentDirectoryPath);
        File parent = f.getParentFile();
        if (parent != null && parent.canRead()) {
            loadDirectory(parent.getAbsolutePath());
        }
    }

    private void onFolderClicked(File folder) {
        loadDirectory(folder.getAbsolutePath());
    }

    private void onFileClicked(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sh") || name.endsWith(".bash") || !file.getName().contains(".")) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.run_script)
                    .setMessage(getString(R.string.run_script_confirm, file.getAbsolutePath()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.run_script, (d, w) -> {
                        tabLayout.selectTab(tabLayout.getTabAt(0));
                        executeCommand("sh " + file.getAbsolutePath());
                    })
                    .show();
        } else {
            Toast.makeText(this, file.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickExternalScriptFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_file)), REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String path = uri.getPath();
            if (path != null) {
                tabLayout.selectTab(tabLayout.getTabAt(0));
                executeCommand("sh " + path);
            }
        }
    }

    // ==========================================
    // Inner Adapters
    // ==========================================

    private static class CommandSuggestionAdapter extends RecyclerView.Adapter<CommandSuggestionAdapter.VH> {
        private final List<CommandEntry> items = new ArrayList<>();
        private final SuggestionClick listener;

        interface SuggestionClick {
            void onClick(String command);
        }

        CommandSuggestionAdapter(SuggestionClick listener) {
            this.listener = listener;
        }

        void setItems(List<CommandEntry> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shell_command, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CommandEntry entry = items.get(position);
            holder.title.setText(entry.title);
            holder.summary.setText(entry.summary);
            holder.itemView.setOnClickListener(v -> listener.onClick(entry.title));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView title, summary;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                summary = v.findViewById(R.id.summary);
            }
        }
    }

    private static class ListDialogAdapter extends RecyclerView.Adapter<ListDialogAdapter.VH> {
        private final List<String> items;
        private final Listener listener;

        interface Listener {
            void onSelect(String item);
            void onDelete(String item);
        }

        ListDialogAdapter(List<String> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shell_command, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String item = items.get(position);
            holder.title.setText(item);
            holder.summary.setText("Click to select, long-press to remove");
            holder.itemView.setOnClickListener(v -> listener.onSelect(item));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onDelete(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView title, summary;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                summary = v.findViewById(R.id.summary);
            }
        }
    }

    private static class BreadcrumbAdapter extends RecyclerView.Adapter<BreadcrumbAdapter.VH> {
        private final List<String> segments = new ArrayList<>();
        private final BreadcrumbClick listener;

        interface BreadcrumbClick {
            void onClick(int index);
        }

        BreadcrumbAdapter(BreadcrumbClick listener) {
            this.listener = listener;
        }

        void setSegments(List<String> list) {
            segments.clear();
            segments.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shell_breadcrumb, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.btn.setText(segments.get(position));
            holder.btn.setOnClickListener(v -> listener.onClick(position));
        }

        @Override
        public int getItemCount() {
            return segments.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            VH(View v) {
                super(v);
                btn = v.findViewById(R.id.breadcrumb_item);
            }
        }
    }

    private static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        private final List<File> items = new ArrayList<>();
        private final ItemClick<File> listener;

        FolderAdapter(ItemClick<File> listener) {
            this.listener = listener;
        }

        void setItems(List<File> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shell_folder, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            File f = items.get(position);
            holder.name.setText(f.getName());
            holder.itemView.setOnClickListener(v -> listener.onClick(f));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
            }
        }
    }

    private static class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final List<File> items = new ArrayList<>();
        private final ItemClick<File> listener;

        FileAdapter(ItemClick<File> listener) {
            this.listener = listener;
        }

        void setItems(List<File> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shell_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            File f = items.get(position);
            holder.name.setText(f.getName());
            holder.size.setText(formatSize(f.length()));
            holder.itemView.setOnClickListener(v -> listener.onClick(f));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String formatSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024.0);
            return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name, size;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                size = v.findViewById(R.id.size);
            }
        }
    }

    private interface ItemClick<T> {
        void onClick(T item);
    }

    public static class CommandEntry {
        public final String title;
        public final String summary;
        public final String example;
        public CommandEntry(String title, String summary) {
            this(title, summary, null);
        }
        public CommandEntry(String title, String summary, String example) {
            this.title = title;
            this.summary = summary;
            this.example = example;
        }
    }

    private static class ShellCommandsDb {
        private static final List<CommandEntry> DB = new ArrayList<>();
        static {
            DB.add(new CommandEntry("am force-stop <package>", "Completely stop a given package", "am force-stop com.android.package"));
            DB.add(new CommandEntry("am kill <package>", "Kill all background processes associated with a given package", "am kill com.android.package"));
            DB.add(new CommandEntry("am kill-all", "Kill all processes that are safe to kill (cached, etc)"));
            DB.add(new CommandEntry("am start -n <package>/<activity>", "Launch an app’s activity directly by component name", "am start -n com.android.package/com.android.package.exampleActivity"));
            DB.add(new CommandEntry("appops get <package>", "Show ops for a package", "appops get com.android.package"));
            DB.add(new CommandEntry("appops set <package> <Operation> <Mode>", "Change an app’s permission mode (Modes: allow, ignore, deny, default)", "appops set com.android.package OPERATION_NAME deny"));
            DB.add(new CommandEntry("cat <file_path>", "Display the contents of a text file", "cat /system/build.prop"));
            DB.add(new CommandEntry("cmd appops get <package>", "Show ops for a package", "cmd appops get com.android.package"));
            DB.add(new CommandEntry("cmd appops set <package> <Operation> <Mode>", "Change an app’s permission mode (Modes: allow, ignore, deny, default)", "cmd appops set com.android.package OPERATION_NAME deny"));
            DB.add(new CommandEntry("cmd package resolve-activity --brief <package>", "Resolve the default launchable activity for the given package", "cmd package resolve-activity --brief com.android.package"));
            DB.add(new CommandEntry("cmd package install-existing <package>", "Reinstall (enable) a package that is present on the system but uninstalled for the user", "cmd package install-existing com.android.package"));
            DB.add(new CommandEntry("cmd role add-role-holder android.app.role.SMS <package>", "Assign the SMS role to an app, making it the default SMS handler", "cmd role add-role-holder android.app.role.SMS com.android.package"));
            DB.add(new CommandEntry("clear", "Clear terminal screen"));
            DB.add(new CommandEntry("cp <from> <to>", "Copy a file", "cp /system/build.prop /sdcard"));
            DB.add(new CommandEntry("cp -r <from> <to>", "Copy a file or directory", "cp -r /system/app /sdcard"));
            DB.add(new CommandEntry("dpm set-device-owner <package>/<receiver>", "Set the given app as device owner (requires a fresh device or factory reset)", "dpm set-device-owner com.android.package/com.android.package.exampleDeviceAdminReceiver"));
            DB.add(new CommandEntry("dumpsys activity", "Print activity info"));
            DB.add(new CommandEntry("dumpsys battery", "Print battery stats"));
            DB.add(new CommandEntry("dumpsys battery set level <n>", "Change the level from 0 to 100", "dumpsys battery set level 95"));
            DB.add(new CommandEntry("dumpsys battery set status <n>", "Change the level to unknown, charging, discharging, not charging or full", "dumpsys battery set status 0"));
            DB.add(new CommandEntry("dumpsys battery reset", "Reset battery"));
            DB.add(new CommandEntry("dumpsys display", "Print display stats"));
            DB.add(new CommandEntry("dumpsys iphonesybinfo", "Get IMEI"));
            DB.add(new CommandEntry("echo <message>", "Display message on screen", "echo Hallo World"));
            DB.add(new CommandEntry("exit", "Exit the shell"));
            DB.add(new CommandEntry("file <file_path>", "Determine file type", "file /system/build.prop"));
            DB.add(new CommandEntry("grep", "Search file(s) for lines that match a given pattern"));
            DB.add(new CommandEntry("kill <pid>", "Kill a process by specifying its PID"));
            DB.add(new CommandEntry("logcat", "Display real-time log of system messages, including stack traces"));
            DB.add(new CommandEntry("ls", "List contents of a directory", "ls /system"));
            DB.add(new CommandEntry("ls -R", "List subdirectories recursively", "ls -R /system"));
            DB.add(new CommandEntry("ls -s", "Print size of each file", "ls -s /system"));
            DB.add(new CommandEntry("mkdir <file_path>", "Create a directory", "mkdir /sdcard/abc"));
            DB.add(new CommandEntry("mv <from> <to>", "Move a file or directory", "mv /system/app /sdcard"));
            DB.add(new CommandEntry("netstat", "List TCP connectivity"));
            DB.add(new CommandEntry("ping", "Test a network connection"));
            DB.add(new CommandEntry("pm clear <package>", "Delete all data associated with an app", "pm clear com.android.package"));
            DB.add(new CommandEntry("pm clear --cache-only <package>", "Only clear cache data associated with an app", "pm clear --cache-only com.android.package"));
            DB.add(new CommandEntry("pm clear --user <user_id> <package>", "Only delete the data associated with a given user", "pm clear --user 0 com.android.package"));
            DB.add(new CommandEntry("pm disable <package/component>", "Disable a given package or component (written as package/class", "pm disable com.android.package/com.android.package.exampleActivity"));
            DB.add(new CommandEntry("pm dump <package>", "List info of one app", "pm dump com.android.package"));
            DB.add(new CommandEntry("pm dump package packages", "List info of all apps"));
            DB.add(new CommandEntry("pm enable <package/component>", "Enable a given package or component (written as package/class", "pm enable com.android.package/com.android.package.exampleActivity"));
            DB.add(new CommandEntry("pm grant <package> <Permission>", "Grant a permission to an app", "pm grant com.android.package android.permission.WRITE_EXTERNAL_STORAGE"));
            DB.add(new CommandEntry("pm install <apk_path>", "Install an apk file", "pm install /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -d <apk_path>", "Allow version code downgrade (debuggable packages only)", "pm install -d /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -f <apk_path>", "Install application on internal flash", "pm install -f /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -g <apk_path>", "Grant all runtime permissions", "pm install -g /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -i <installer> <apk_path>", "Specify package name of installer owning the app", "pm install -i com.google.android.packageinstaller /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -p <split_apk_path>", "Partial application install (new split on top of existing pkg)", "pm install -p /data/local/tmp/base.apk"));
            DB.add(new CommandEntry("pm install -R <apk_path>", "Update an existing apps, but disallow replacement of existing one", "pm install -R /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install -t <apk_path>", "Allow installing test packages", "pm install -t /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --abi <apk_path>", "Override the default ABI of the platform", "pm install --abi /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --dont-kill <apk_path>", "Install a new feature split without killing the running app", "pm install --dont-kill /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --full <apk_path>", "Cause the app to be installed as a non-ephemeral full app", "pm install --full /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --install-location <location> <apk_path>", "Force the install location (Options, 0=auto, 1=internal only, 2=prefer external)", "pm install --install-location 1 /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --install-reason <reason> <apk_path>", "Indicates why the app is being installed (Options, 0=unknown, 1=admin policy, 2=device restore,3=device setup, 4=user request)", "install --install-reason 2 /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --instant <apk_path>", "Cause the app to be installed as an ephemeral install app", "pm install --instant /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --restrict-permissions <apk_path>", "Don't whitelist restricted permissions at install", "pm install --restrict-permissions /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --pkg <package> <apk_path>", "Specify expected package name of app being installed", "pm install --pkg in.sunilpaulmathew.ashell /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install --user <user_id> <apk_path>", "Install for a given user", "pm install --user0 /data/local/tmp/aShell.apk"));
            DB.add(new CommandEntry("pm install-abandon <session_id>", "Delete the given active install session", "pm install-abandon 01234567"));
            DB.add(new CommandEntry("pm install-commit <session_id>", "Commit the given active install session, installing the app", "pm install-commit 01234567"));
            DB.add(new CommandEntry("pm install-create", "Create an install session"));
            DB.add(new CommandEntry("pm install-existing", "Installs an existing application for a new user", "pm install-existing com.android.package"));
            DB.add(new CommandEntry("pm install-existing --full <package>", "Install as a full app", "pm install-existing --full com.android.package"));
            DB.add(new CommandEntry("pm install-existing --instant <package>", "Install as an instant app", "pm install-existing --instant com.android.package"));
            DB.add(new CommandEntry("pm install-existing --restrict-permissions <package>", "Don't whitelist restricted permissions", "pm install-existing --restrict-permissions com.android.package"));
            DB.add(new CommandEntry("pm install-existing --user <user_id> <package>", "Install for a given user", "pm install-existing --user 0 com.android.package"));
            DB.add(new CommandEntry("pm install-existing --wait <package>", "Wait until the package is installed", "pm install-existing --wait com.android.package"));
            DB.add(new CommandEntry("pm install-remove <session_id> <split_name>", "Mark SPLIT(s) as removed in the given install session", "pm install-remove 01234567 /data/local/tmp/base.apk"));
            DB.add(new CommandEntry("pm install-write <size> <session_id> <split_name> <split_path>", "Write an apk into the given install session", "pm install-write 123 01234567 base.apk /data/local/tmp/base.apk"));
            DB.add(new CommandEntry("pm list features", "List phone features"));
            DB.add(new CommandEntry("pm list libraries", "List all system libraries"));
            DB.add(new CommandEntry("pm list packages", "List package names"));
            DB.add(new CommandEntry("pm list packages -3", "List only third party packages"));
            DB.add(new CommandEntry("pm list packages -a", "List all known packages, but excluding APEXes"));
            DB.add(new CommandEntry("pm list packages -d", "List all disabled packages"));
            DB.add(new CommandEntry("pm list packages -e", "List all enabled packages"));
            DB.add(new CommandEntry("pm list packages -f", "List package names along with their associated file"));
            DB.add(new CommandEntry("pm list packages -i", "List package names along with their installer"));
            DB.add(new CommandEntry("pm list packages -s", "List only system packages"));
            DB.add(new CommandEntry("pm list packages --show-versioncode", "List package names along with their version code"));
            DB.add(new CommandEntry("pm list packages -u", "List package names of all apps including the uninstalled ones"));
            DB.add(new CommandEntry("pm list packages -U", "List package names along with their package UID"));
            DB.add(new CommandEntry("pm list permissions", "Print all known permission groups"));
            DB.add(new CommandEntry("pm list permissions -d", "Print only dangerous permissions"));
            DB.add(new CommandEntry("pm list permissions -f", "Print all information about the known permissions"));
            DB.add(new CommandEntry("pm list permissions -g", "Organize all known permission by group"));
            DB.add(new CommandEntry("pm list permissions -s", "Print a short summary about the known permissions"));
            DB.add(new CommandEntry("pm list permissions -u", "Print only permissions that users will see"));
            DB.add(new CommandEntry("pm list users", "List all user names"));
            DB.add(new CommandEntry("pm path <package>", "Show apk file path of an app", "pm path com.android.package"));
            DB.add(new CommandEntry("pm revoke <package> <Permission>", "Revoke a permission from an app", "pm revoke com.android.package android.permission.WRITE_EXTERNAL_STORAGE"));
            DB.add(new CommandEntry("pm reset-permissions -p <package>", "Reset permissions of an app", "pm reset-permissions -p com.android.package"));
            DB.add(new CommandEntry("pm uninstall <package>", "Uninstall an app", "pm uninstall com.android.package"));
            DB.add(new CommandEntry("pm uninstall -k <package>", "Uninstall an app but keep the data and cache untouched", "pm uninstall -k com.android.package"));
            DB.add(new CommandEntry("pm uninstall --user <user_id> <package>", "Uninstall an app from a given user", "pm uninstall --user 0 com.android.package"));
            DB.add(new CommandEntry("pm uninstall --versionCode <version_code> <package>", "Only uninstall if the app has the given version code", "pm uninstall --versionCode 123 com.android.package"));
            DB.add(new CommandEntry("pm uninstall-system-updates", "Removes updates to all system applications and falls back to its system version"));
            DB.add(new CommandEntry("pm uninstall-system-updates <package>", "Removes updates to a given system application and falls back to its system version", "pm uninstall-system-updates com.android.package"));
            DB.add(new CommandEntry("ps", "Print process status"));
            DB.add(new CommandEntry("pwd", "Print current working directory"));
            DB.add(new CommandEntry("reboot", "Reboot device"));
            DB.add(new CommandEntry("reboot -p", "Shutdown device"));
            DB.add(new CommandEntry("reboot recovery", "Reboot device into recovery mode"));
            DB.add(new CommandEntry("reboot fastboot", "Reboot device into fastboot"));
            DB.add(new CommandEntry("reboot bootloader", "Reboot device into bootloader"));
            DB.add(new CommandEntry("rm <file_path>", "Delete a file", "rm /sdcard/example.txt"));
            DB.add(new CommandEntry("rm -r <file_path>", "Delete a file or directory", "rm -r /sdcard/abc"));
            DB.add(new CommandEntry("service list", "List all services"));
            DB.add(new CommandEntry("settings get secure default_input_method", "Get the package name of the current default keyboard"));
            DB.add(new CommandEntry("settings put secure enabled_accessibility_services <package>/<service>", "Enable an accessibility service for a given package", "settings put secure enabled_accessibility_services com.android.package/com.android.package.exampleAccessibilityService"));
            DB.add(new CommandEntry("sleep <second>", "Delay for a specified time", "sleep 5"));
            DB.add(new CommandEntry("sync", "Synchronize data on disk with memory"));
            DB.add(new CommandEntry("top", "List processes running on the system"));
            DB.add(new CommandEntry("top -n <number>", "Update display <number> times, then exit", "top -n1"));
            DB.add(new CommandEntry("whoami", "Print the current user id and name"));
            DB.add(new CommandEntry("wm density", "Displays current screen density"));
            DB.add(new CommandEntry("wm density reset", "Reset screen density to default"));
            DB.add(new CommandEntry("wm size", "Displays the current screen resolution"));
            DB.add(new CommandEntry("wm size reset", "Reset screen resolution to default"));
            DB.add(new CommandEntry("dumpsys meminfo", "Show memory usage and RAM consumption statistics", "dumpsys meminfo com.android.package"));
            DB.add(new CommandEntry("dumpsys diskstats", "Show disk and internal flash storage stats"));
            DB.add(new CommandEntry("dumpsys package <package>", "Dump detailed system package configuration and components", "dumpsys package com.android.package"));
            DB.add(new CommandEntry("dumpsys netstats detail", "Print detailed device network traffic statistics"));
            DB.add(new CommandEntry("dumpsys alarm", "Display alarms and wakelocks set by applications"));
            DB.add(new CommandEntry("dumpsys procstats --hours 3", "Display application memory and runtime stats for the last 3 hours"));
            DB.add(new CommandEntry("getprop", "List all Android system properties and build configuration values", "getprop ro.build.version.release"));
            DB.add(new CommandEntry("getprop ro.build.version.release", "Display the Android OS version"));
            DB.add(new CommandEntry("getprop ro.product.model", "Display the device model name"));
            DB.add(new CommandEntry("getprop ro.product.manufacturer", "Display the device manufacturer"));
            DB.add(new CommandEntry("setprop <key> <value>", "Set a system property (requires Root access)", "setprop debug.stagefright.omx 1"));
            DB.add(new CommandEntry("settings get system <key>", "Get a system setting value (e.g. screen brightness)", "settings get system screen_brightness"));
            DB.add(new CommandEntry("settings put system <key> <value>", "Set a system setting value", "settings put system screen_brightness 128"));
            DB.add(new CommandEntry("settings get global <key>", "Get a global system setting value", "settings get global airplane_mode_on"));
            DB.add(new CommandEntry("settings put global <key> <value>", "Set a global system setting value", "settings put global airplane_mode_on 1"));
            DB.add(new CommandEntry("settings get secure <key>", "Get a secure setting value", "settings get secure android_id"));
            DB.add(new CommandEntry("settings put secure <key> <value>", "Set a secure system setting value"));
            DB.add(new CommandEntry("cmd overlay list", "List all Runtime Resource Overlays (RROs) and themes"));
            DB.add(new CommandEntry("cmd overlay enable <package>", "Enable an overlay package", "cmd overlay enable com.example.overlay"));
            DB.add(new CommandEntry("cmd overlay disable <package>", "Disable an overlay package", "cmd overlay disable com.example.overlay"));
            DB.add(new CommandEntry("cmd package compile -m speed -f <package>", "Force dexopt compilation of an app to maximum speed", "cmd package compile -m speed -f com.android.package"));
            DB.add(new CommandEntry("cmd package compile --reset <package>", "Reset dexopt optimization of an app to default state", "cmd package compile --reset com.android.package"));
            DB.add(new CommandEntry("cmd appops reset <package>", "Reset all appops permissions of a package to defaults", "cmd appops reset com.android.package"));
            DB.add(new CommandEntry("cmd power reboot", "Safely reboot the system through the PowerManager service"));
            DB.add(new CommandEntry("cmd power shutdown", "Safely shut down the device"));
            DB.add(new CommandEntry("cmd connectivity airplane-mode enable", "Turn on airplane mode"));
            DB.add(new CommandEntry("cmd connectivity airplane-mode disable", "Turn off airplane mode"));
            DB.add(new CommandEntry("cmd wifi status", "Display current Wi-Fi interface status"));
            DB.add(new CommandEntry("df -h", "Display free and used disk space of all mounted partitions"));
            DB.add(new CommandEntry("du -sh <path>", "Display the disk usage size of a file or directory", "du -sh /sdcard/Download"));
            DB.add(new CommandEntry("uptime", "Display how long the device has been running and load averages"));
            DB.add(new CommandEntry("uname -a", "Print kernel name, architecture (ARM64), and build details"));
            DB.add(new CommandEntry("id", "Print current effective user and group identities (UID, GID, groups)"));
            DB.add(new CommandEntry("free -m", "Display total, used, and available physical memory in megabytes"));
            DB.add(new CommandEntry("stat <file_path>", "Display detailed inode status, timestamps, and permissions of a file", "stat /system/build.prop"));
            DB.add(new CommandEntry("chmod <mode> <file_path>", "Change file access permissions", "chmod 755 /data/local/tmp/script.sh"));
            DB.add(new CommandEntry("chown <user:group> <file_path>", "Change file ownership (requires Root)", "chown root:root /data/local/tmp/file"));
            DB.add(new CommandEntry("ln -s <target> <link_name>", "Create a symbolic link pointing to a file or directory", "ln -s /sdcard /data/local/tmp/sdcard_link"));
            DB.add(new CommandEntry("tar -czvf <archive.tar.gz> <dir>", "Create a compressed gzip tar archive from a directory", "tar -czvf /sdcard/backup.tar.gz /sdcard/MyFolder"));
            DB.add(new CommandEntry("tar -xzvf <archive.tar.gz> -C <dir>", "Extract a compressed gzip tar archive into destination directory", "tar -xzvf /sdcard/backup.tar.gz -C /sdcard/Extracted"));
            DB.add(new CommandEntry("ip route", "Show network routing table"));
            DB.add(new CommandEntry("ip addr show", "Display IP addresses and network interfaces (wlan0, rmnet)"));
            DB.add(new CommandEntry("printenv", "List all current environment variables (PATH, HOME, etc)"));
            DB.add(new CommandEntry("which <binary>", "Locate an executable binary in PATH", "which su"));
        }

        static List<CommandEntry> getPopularExamples() {
            return new ArrayList<>(DB);
        }

        static List<CommandEntry> searchCommands(String query) {
            List<CommandEntry> results = new ArrayList<>();
            String lower = query.toLowerCase(Locale.ROOT);
            for (CommandEntry entry : DB) {
                if (entry.title.toLowerCase(Locale.ROOT).contains(lower) ||
                        entry.summary.toLowerCase(Locale.ROOT).contains(lower) ||
                        (entry.example != null && entry.example.toLowerCase(Locale.ROOT).contains(lower))) {
                    results.add(entry);
                }
            }
            return results;
        }
    }
}
