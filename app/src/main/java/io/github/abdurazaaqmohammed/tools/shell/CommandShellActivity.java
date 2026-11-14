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
        public CommandEntry(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }
    }

    private static class ShellCommandsDb {
        private static final List<CommandEntry> DB = new ArrayList<>();
        static {
            DB.add(new CommandEntry("pm list packages -3", "List all third-party installed packages"));
            DB.add(new CommandEntry("pm list packages -s", "List all system packages"));
            DB.add(new CommandEntry("pm list packages -d", "List all disabled packages"));
            DB.add(new CommandEntry("pm list packages -e", "List all enabled packages"));
            DB.add(new CommandEntry("pm path <package>", "Get APK path of specified package"));
            DB.add(new CommandEntry("pm install -r <apk_path>", "Reinstall an existing app, keeping its data"));
            DB.add(new CommandEntry("pm uninstall <package>", "Uninstall an app package"));
            DB.add(new CommandEntry("pm clear <package>", "Clear all user data of an app"));
            DB.add(new CommandEntry("pm enable <package>", "Enable a disabled package"));
            DB.add(new CommandEntry("pm disable-user <package>", "Disable a package for current user"));
            DB.add(new CommandEntry("am start -n <package/activity>", "Launch an activity"));
            DB.add(new CommandEntry("am force-stop <package>", "Force stop all processes of an app"));
            DB.add(new CommandEntry("am kill <package>", "Kill all processes associated with an app"));
            DB.add(new CommandEntry("dumpsys battery", "Show device battery diagnostics"));
            DB.add(new CommandEntry("dumpsys package <package>", "Dump full details for a package"));
            DB.add(new CommandEntry("dumpsys meminfo", "Show system memory usage information"));
            DB.add(new CommandEntry("getprop ro.build.version.release", "Get Android OS version"));
            DB.add(new CommandEntry("getprop ro.product.model", "Get device model name"));
            DB.add(new CommandEntry("setprop <key> <value>", "Set system property (requires Root)"));
            DB.add(new CommandEntry("settings get global <key>", "Get global setting value"));
            DB.add(new CommandEntry("settings put global <key> <value>", "Set global setting value"));
            DB.add(new CommandEntry("ls -la /sdcard", "List files in internal shared storage"));
            DB.add(new CommandEntry("df -h", "Show mounted filesystems disk space"));
            DB.add(new CommandEntry("uptime", "Show system uptime and load average"));
            DB.add(new CommandEntry("whoami", "Show current shell user (root or shell uid 2000)"));
            DB.add(new CommandEntry("id", "Show user and group IDs"));
        }

        static List<CommandEntry> getPopularExamples() {
            return new ArrayList<>(DB);
        }

        static List<CommandEntry> searchCommands(String query) {
            List<CommandEntry> results = new ArrayList<>();
            String lower = query.toLowerCase(Locale.ROOT);
            for (CommandEntry entry : DB) {
                if (entry.title.toLowerCase(Locale.ROOT).contains(lower) ||
                        entry.summary.toLowerCase(Locale.ROOT).contains(lower)) {
                    results.add(entry);
                }
            }
            return results;
        }
    }
}
