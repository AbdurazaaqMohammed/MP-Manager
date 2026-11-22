package io.github.abdurazaaqmohammed.feature.arm64;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import io.github.abdurazaaqmohammed.feature.arm64.util.CryptoHelper;
import java.security.MessageDigest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.abdurazaaqmohammed.feature.apk.nativelib.ElfParser;
import io.github.abdurazaaqmohammed.feature.arm64.decoder.ArmDisasmDecoder;
import io.github.abdurazaaqmohammed.feature.arm64.decoder.AsmStatement;
import io.github.abdurazaaqmohammed.feature.arm64.util.CppDemangler;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.editor.HexEditorActivity;
import io.github.abdurazaaqmohammed.util.io.FileUtils;

/**
 * Modern, in-app ARM64 (AArch64) Native Disassembler & Patcher:
 * - Pure Java instruction decoding powered by Jarmo
 * - Complete ELF64 symbol and section parsing (.text, .dynsym, .symtab)
 * - Real-time function search and filtering (JNI, Exports, Locals)
 * - Instruction viewer with syntax coloring and raw hex inspection
 * - Quick-patch presets (Return 1/True, Return 0/False, NOP, RET, Custom Hex)
 * - Direct in-place binary patching with root/elevated writeback
 */
public class Arm64DisassemblerActivity extends BaseActivity {

    public static final String EXTRA_SO_PATH = "extra_so_path";
    public static final String EXTRA_ORIGINAL_PATH = "extra_original_path";

    private String soPath;
    private String originalPath;
    private String zipFilePath;
    private String zipEntryPath;
    private File soFile;

    private Toolbar toolbar;
    private MaterialButton btnSave;
    private MaterialButton btnReview;
    private MaterialButton btnGoTo;
    private ProgressBar progressBar;
    private TextView tvStatusInfo;

    private static class NavLocation {
        final Arm64Function function;
        final long address;

        NavLocation(Arm64Function function, long address) {
            this.function = function;
            this.address = address;
        }
    }

    private final ArrayDeque<NavLocation> navHistory = new ArrayDeque<>();
    private final Map<Long, String> symbolAddressMap = new HashMap<>();

    // View Containers (Master-Detail)
    private LinearLayout functionsContainer;
    private LinearLayout disassemblyContainer;
    private EditText etSearch;
    private RecyclerView rvFunctions;
    private RecyclerView rvInstructions;

    private TextView tvDisasmHeader;
    private MaterialButton btnBackToFunctions;
    private MaterialButton btnLoadMore;

    private static final int DISASM_CHUNK_SIZE = 65536; // 64 KB per chunk (16,384 instructions)
    private int loadedInstructionBytes = 0;

    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearchRunnable;

    private FunctionsAdapter functionsAdapter;
    private InstructionsAdapter instructionsAdapter;

    private final List<Arm64Function> allFunctions = new ArrayList<>();
    private final List<Arm64Function> filteredFunctions = new ArrayList<>();
    private final List<Arm64Instruction> currentInstructions = new ArrayList<>();

    // Pending patches: fileOffset -> 4 modified bytes
    private final Map<Long, byte[]> pendingPatches = new LinkedHashMap<>();

    private final ArmDisasmDecoder decoder = new ArmDisasmDecoder();
    private Arm64Function currentFunction = null;
    private boolean isDisassemblyMode = false;

    private long textSecAddr = 0;
    private long textSecOffset = 0;
    private long textSecSize = 0;

    // Tab Layout & Multi-tab Containers
    private TabLayout tabLayout;
    private LinearLayout stringsContainer;
    private LinearLayout hexContainer;
    private LinearLayout treeContainer;

    // Strings Tab
    private EditText etStringsSearch;
    private TextView tvStringsCount;
    private RecyclerView rvStrings;
    private StringsAdapter stringsAdapter;
    private final List<ElfParser.ElfString> allStrings = new ArrayList<>();
    private final List<ElfParser.ElfString> filteredStrings = new ArrayList<>();
    private Runnable pendingStringsSearchRunnable;

    // HexView Tab
    private RecyclerView rvHex;
    private HexAdapter hexAdapter;
    private TextView tvHexStatus;
    private EditText etHexSearch;
    private MaterialButton btnPatchModeToggle;
    private MaterialButton btnHexColsToggle;
    private RandomAccessFile hexRaf;
    private int hexBytesPerRow = 16;
    private boolean isPatchMode = false;
    private long hexFileSize = 0;

    // ELF Tree Tab
    private TextView tvTreeProgramme;
    private TextView tvTreeImports;
    private TextView tvTreeExports;
    private EditText etTreeSearch;
    private RecyclerView rvElfTree;
    private ElfTreeAdapter elfTreeAdapter;
    private final List<ElfTreeItem> allTreeItems = new ArrayList<>();
    private final List<ElfTreeItem> filteredTreeItems = new ArrayList<>();
    private Runnable pendingTreeSearchRunnable;

    // Metadata & Hashes for File Info
    private int programmeCount = 0;
    private int importsCount = 0;
    private int exportsCount = 0;
    private long elfEntryPoint = 0;
    private String elfExportHash = "N/A";
    private String elfBuildId = "N/A";
    private String hashMd5 = "";
    private String hashSha1 = "";
    private String hashSha256 = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        soPath = getIntent().getStringExtra(EXTRA_SO_PATH);
        originalPath = getIntent().getStringExtra(EXTRA_ORIGINAL_PATH);
        if (TextUtils.isEmpty(originalPath)) originalPath = soPath;
        zipFilePath = getIntent().getStringExtra("zipFilePath");
        zipEntryPath = getIntent().getStringExtra("zipEntryPath");

        if (TextUtils.isEmpty(soPath) || !(soFile = new File(soPath)).exists()) {
            Toast.makeText(this, "ELF file not found: " + soPath, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUi();
        setupBackHandler();
        loadElfData();
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tabLayout != null && tabLayout.getSelectedTabPosition() == 0 && isDisassemblyMode) {
                    if (!popNavigationHistory()) {
                        showFunctionsViewUi();
                    }
                } else if (tabLayout != null && tabLayout.getSelectedTabPosition() != 0) {
                    tabLayout.getTabAt(0).select();
                } else if (!pendingPatches.isEmpty()) {
                    showDiscardConfirmDialog();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hexRaf != null) {
            try { hexRaf.close(); } catch (Exception ignored) {}
            hexRaf = null;
        }
    }

    private void initUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int bgColor = MaterialColors.getColor(root, android.R.attr.colorBackground, 0xFF121212);
        root.setBackgroundColor(bgColor);

        // Toolbar
        toolbar = new Toolbar(this);
        toolbar.setTitle(new File(originalPath).getName());
        toolbar.setSubtitle("ARM64 Disassembler");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> {
            if (tabLayout != null && tabLayout.getSelectedTabPosition() == 0 && isDisassemblyMode) {
                if (!popNavigationHistory()) {
                    showFunctionsViewUi();
                }
            } else if (tabLayout != null && tabLayout.getSelectedTabPosition() != 0) {
                tabLayout.getTabAt(0).select();
            } else if (!pendingPatches.isEmpty()) {
                showDiscardConfirmDialog();
            } else {
                finish();
            }
        });

        // Top Actions inside Toolbar
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        MaterialButton btnCrypto = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCrypto.setText("Crypto");
        btnCrypto.setOnClickListener(v -> showCryptoDialog());
        actionLayout.addView(btnCrypto);

        MaterialButton btnInfo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnInfo.setText("Info");
        btnInfo.setOnClickListener(v -> showFileInfoDialog());
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.leftMargin = dp(6);
        btnInfo.setLayoutParams(infoParams);
        actionLayout.addView(btnInfo);

        btnGoTo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGoTo.setText("Go To");
        btnGoTo.setOnClickListener(v -> showGoToDialog());
        LinearLayout.LayoutParams goToParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        goToParams.leftMargin = dp(6);
        btnGoTo.setLayoutParams(goToParams);
        actionLayout.addView(btnGoTo);

        btnReview = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnReview.setText("Diff");
        btnReview.setVisibility(View.GONE);
        btnReview.setOnClickListener(v -> showReviewPatchesDialog());
        LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reviewParams.leftMargin = dp(6);
        btnReview.setLayoutParams(reviewParams);
        actionLayout.addView(btnReview);

        btnSave = new MaterialButton(this);
        btnSave.setText("Save");
        btnSave.setVisibility(View.GONE);
        btnSave.setOnClickListener(v -> saveAllPatches());
        btnSave.setOnLongClickListener(v -> {
            showReviewPatchesDialog();
            return true;
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.leftMargin = dp(6);
        btnSave.setLayoutParams(saveParams);
        actionLayout.addView(btnSave);

        toolbar.addView(actionLayout, new Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));
        root.addView(toolbar);

        // Status / Info Bar
        tvStatusInfo = new TextView(this);
        tvStatusInfo.setTextSize(12);
        tvStatusInfo.setPadding(dp(12), dp(4), dp(12), dp(4));
        tvStatusInfo.setTypeface(Typeface.MONOSPACE);
        tvStatusInfo.setTextColor(0xFF888888);
        tvStatusInfo.setText("Loading ELF metadata...");
        root.addView(tvStatusInfo);

        // Progress Bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        root.addView(progressBar);

        // Modern Tab Navigation Bar
        tabLayout = new TabLayout(this);
        tabLayout.addTab(tabLayout.newTab().setText("Functions"));
        tabLayout.addTab(tabLayout.newTab().setText("Strings"));
        tabLayout.addTab(tabLayout.newTab().setText("HexView"));
        tabLayout.addTab(tabLayout.newTab().setText("ELF Tree"));
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        tabLayout.setSelectedTabIndicatorColor(0xFF22C55E);
        tabLayout.setTabTextColors(0xFF888888, 0xFF22C55E);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                functionsContainer.setVisibility(pos == 0 && !isDisassemblyMode ? View.VISIBLE : View.GONE);
                disassemblyContainer.setVisibility(pos == 0 && isDisassemblyMode ? View.VISIBLE : View.GONE);
                stringsContainer.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                hexContainer.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
                treeContainer.setVisibility(pos == 3 ? View.VISIBLE : View.GONE);

                if (pos == 2 && hexAdapter != null) {
                    hexAdapter.notifyDataSetChanged();
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        root.addView(tabLayout);

        // Main Frame containing all views
        FrameLayout contentFrame = new FrameLayout(this);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // 1. Functions Container
        functionsContainer = new LinearLayout(this);
        functionsContainer.setOrientation(LinearLayout.VERTICAL);
        functionsContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        etSearch = new EditText(this);
        etSearch.setHint("Search functions (e.g. check, root, license)...");
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF222222));
        searchBg.setCornerRadius(dp(8));
        etSearch.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(dp(10), dp(6), dp(10), dp(6));
        etSearch.setLayoutParams(searchLp);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearchRunnable != null) {
                    searchHandler.removeCallbacks(pendingSearchRunnable);
                }
                String query = s != null ? s.toString() : "";
                pendingSearchRunnable = () -> filterFunctionsAsync(query);
                searchHandler.postDelayed(pendingSearchRunnable, 150);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        functionsContainer.addView(etSearch);

        rvFunctions = new RecyclerView(this);
        rvFunctions.setLayoutManager(new LinearLayoutManager(this));
        functionsAdapter = new FunctionsAdapter();
        rvFunctions.setAdapter(functionsAdapter);
        functionsContainer.addView(rvFunctions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        contentFrame.addView(functionsContainer);

        // 2. Disassembly Container
        disassemblyContainer = new LinearLayout(this);
        disassemblyContainer.setOrientation(LinearLayout.VERTICAL);
        disassemblyContainer.setVisibility(View.GONE);
        disassemblyContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout disasmHeaderBar = new LinearLayout(this);
        disasmHeaderBar.setOrientation(LinearLayout.HORIZONTAL);
        disasmHeaderBar.setGravity(Gravity.CENTER_VERTICAL);
        disasmHeaderBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        disasmHeaderBar.setBackgroundColor(0x18888888);

        btnBackToFunctions = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnBackToFunctions.setText("← Functions");
        btnBackToFunctions.setOnClickListener(v -> showFunctionsViewUi());
        disasmHeaderBar.addView(btnBackToFunctions);

        tvDisasmHeader = new TextView(this);
        tvDisasmHeader.setTextSize(13);
        tvDisasmHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvDisasmHeader.setSingleLine(true);
        tvDisasmHeader.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tvDisasmHeader.setPadding(dp(10), 0, dp(4), 0);
        disasmHeaderBar.addView(tvDisasmHeader, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        btnLoadMore = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnLoadMore.setText("Load More");
        btnLoadMore.setVisibility(View.GONE);
        btnLoadMore.setOnClickListener(v -> loadMoreInstructions());
        LinearLayout.LayoutParams pLoadMore = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLoadMore.rightMargin = dp(6);
        btnLoadMore.setLayoutParams(pLoadMore);
        disasmHeaderBar.addView(btnLoadMore);

        MaterialButton btnExportAsm = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnExportAsm.setText("Export");
        btnExportAsm.setOnClickListener(v -> showExportOptionsDialog());
        disasmHeaderBar.addView(btnExportAsm);

        disassemblyContainer.addView(disasmHeaderBar);

        rvInstructions = new RecyclerView(this);
        rvInstructions.setLayoutManager(new LinearLayoutManager(this));
        instructionsAdapter = new InstructionsAdapter();
        rvInstructions.setAdapter(instructionsAdapter);
        disassemblyContainer.addView(rvInstructions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        contentFrame.addView(disassemblyContainer);

        // 3. Strings Container
        stringsContainer = new LinearLayout(this);
        stringsContainer.setOrientation(LinearLayout.VERTICAL);
        stringsContainer.setVisibility(View.GONE);
        stringsContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout stringsTopBar = new LinearLayout(this);
        stringsTopBar.setOrientation(LinearLayout.HORIZONTAL);
        stringsTopBar.setGravity(Gravity.CENTER_VERTICAL);
        stringsTopBar.setPadding(dp(10), dp(6), dp(10), dp(6));

        etStringsSearch = new EditText(this);
        etStringsSearch.setHint("Search strings (e.g. http, token, root)...");
        etStringsSearch.setTextSize(14);
        etStringsSearch.setSingleLine(true);
        etStringsSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable strSearchBg = new GradientDrawable();
        strSearchBg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF222222));
        strSearchBg.setCornerRadius(dp(8));
        etStringsSearch.setBackground(strSearchBg);
        etStringsSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingStringsSearchRunnable != null) {
                    searchHandler.removeCallbacks(pendingStringsSearchRunnable);
                }
                String q = s != null ? s.toString() : "";
                pendingStringsSearchRunnable = () -> filterStringsAsync(q);
                searchHandler.postDelayed(pendingStringsSearchRunnable, 150);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        stringsTopBar.addView(etStringsSearch, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        tvStringsCount = createBadgeChip("0", 0xFF22C55E);
        LinearLayout.LayoutParams pStrCount = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pStrCount.leftMargin = dp(8);
        tvStringsCount.setLayoutParams(pStrCount);
        stringsTopBar.addView(tvStringsCount);

        stringsContainer.addView(stringsTopBar);

        rvStrings = new RecyclerView(this);
        rvStrings.setLayoutManager(new LinearLayoutManager(this));
        stringsAdapter = new StringsAdapter();
        rvStrings.setAdapter(stringsAdapter);
        stringsContainer.addView(rvStrings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        contentFrame.addView(stringsContainer);

        // 4. HexView Container
        hexContainer = new LinearLayout(this);
        hexContainer.setOrientation(LinearLayout.VERTICAL);
        hexContainer.setVisibility(View.GONE);
        hexContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout hexControls = new LinearLayout(this);
        hexControls.setOrientation(LinearLayout.HORIZONTAL);
        hexControls.setGravity(Gravity.CENTER_VERTICAL);
        hexControls.setPadding(dp(10), dp(4), dp(10), dp(4));
        hexControls.setBackgroundColor(0x18888888);

        btnPatchModeToggle = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnPatchModeToggle.setText("PATCH: OFF");
        btnPatchModeToggle.setTextSize(11);
        btnPatchModeToggle.setOnClickListener(v -> {
            isPatchMode = !isPatchMode;
            btnPatchModeToggle.setText(isPatchMode ? "PATCH: ON" : "PATCH: OFF");
            btnPatchModeToggle.setTextColor(isPatchMode ? 0xFF22C55E : 0xFF888888);
            Toast.makeText(this, isPatchMode ? "Patch mode enabled (tap a row to edit bytes)" : "Patch mode disabled", Toast.LENGTH_SHORT).show();
        });
        hexControls.addView(btnPatchModeToggle);

        MaterialButton btnHexGoTo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnHexGoTo.setText("Go To");
        btnHexGoTo.setTextSize(11);
        btnHexGoTo.setOnClickListener(v -> showHexGoToDialog());
        LinearLayout.LayoutParams pHexGo = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pHexGo.leftMargin = dp(6);
        btnHexGoTo.setLayoutParams(pHexGo);
        hexControls.addView(btnHexGoTo);

        MaterialButton btnHexUndo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnHexUndo.setText("Undo");
        btnHexUndo.setTextSize(11);
        btnHexUndo.setOnClickListener(v -> undoLastPatch());
        LinearLayout.LayoutParams pHexUndo = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pHexUndo.leftMargin = dp(6);
        btnHexUndo.setLayoutParams(pHexUndo);
        hexControls.addView(btnHexUndo);

        btnHexColsToggle = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnHexColsToggle.setText("16 COLS");
        btnHexColsToggle.setTextSize(11);
        btnHexColsToggle.setOnClickListener(v -> {
            hexBytesPerRow = (hexBytesPerRow == 16) ? 32 : 16;
            btnHexColsToggle.setText(hexBytesPerRow + " COLS");
            if (hexAdapter != null) hexAdapter.notifyDataSetChanged();
        });
        LinearLayout.LayoutParams pHexCols = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pHexCols.leftMargin = dp(6);
        btnHexColsToggle.setLayoutParams(pHexCols);
        hexControls.addView(btnHexColsToggle);

        hexContainer.addView(hexControls);

        etHexSearch = new EditText(this);
        etHexSearch.setHint("Search hex (e.g. 1f 20 03 d5) or ASCII text (Press Enter)...");
        etHexSearch.setTextSize(13);
        etHexSearch.setSingleLine(true);
        etHexSearch.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable hexSearchBg = new GradientDrawable();
        hexSearchBg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF222222));
        hexSearchBg.setCornerRadius(dp(6));
        etHexSearch.setBackground(hexSearchBg);
        LinearLayout.LayoutParams hexSearchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexSearchLp.setMargins(dp(10), dp(4), dp(10), dp(4));
        etHexSearch.setLayoutParams(hexSearchLp);
        etHexSearch.setOnEditorActionListener((v, actionId, event) -> {
            performHexSearch(etHexSearch.getText().toString());
            return true;
        });
        hexContainer.addView(etHexSearch);

        rvHex = new RecyclerView(this);
        rvHex.setLayoutManager(new LinearLayoutManager(this));
        hexAdapter = new HexAdapter();
        rvHex.setAdapter(hexAdapter);
        hexContainer.addView(rvHex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        tvHexStatus = new TextView(this);
        tvHexStatus.setTextSize(11);
        tvHexStatus.setTypeface(Typeface.MONOSPACE);
        tvHexStatus.setTextColor(0xFF888888);
        tvHexStatus.setPadding(dp(12), dp(4), dp(12), dp(4));
        tvHexStatus.setBackgroundColor(0x10888888);
        hexContainer.addView(tvHexStatus);

        contentFrame.addView(hexContainer);

        // 5. ELF Tree Container
        treeContainer = new LinearLayout(this);
        treeContainer.setOrientation(LinearLayout.VERTICAL);
        treeContainer.setVisibility(View.GONE);
        treeContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout statsBar = new LinearLayout(this);
        statsBar.setOrientation(LinearLayout.HORIZONTAL);
        statsBar.setGravity(Gravity.CENTER_VERTICAL);
        statsBar.setPadding(dp(10), dp(8), dp(10), dp(8));

        tvTreeProgramme = createBadgeChip("Programme: 0", 0xFF22C55E);
        statsBar.addView(tvTreeProgramme);

        tvTreeImports = createBadgeChip("Imports: 0", 0xFF3B82F6);
        LinearLayout.LayoutParams pImp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pImp.leftMargin = dp(8);
        tvTreeImports.setLayoutParams(pImp);
        statsBar.addView(tvTreeImports);

        tvTreeExports = createBadgeChip("Exports: 0", 0xFFA855F7);
        LinearLayout.LayoutParams pExp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pExp.leftMargin = dp(8);
        tvTreeExports.setLayoutParams(pExp);
        statsBar.addView(tvTreeExports);

        treeContainer.addView(statsBar);

        etTreeSearch = new EditText(this);
        etTreeSearch.setHint("Search name or address (0x...)...");
        etTreeSearch.setTextSize(14);
        etTreeSearch.setSingleLine(true);
        etTreeSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable treeSearchBg = new GradientDrawable();
        treeSearchBg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF222222));
        treeSearchBg.setCornerRadius(dp(8));
        etTreeSearch.setBackground(treeSearchBg);
        LinearLayout.LayoutParams treeSearchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        treeSearchLp.setMargins(dp(10), dp(4), dp(10), dp(4));
        etTreeSearch.setLayoutParams(treeSearchLp);
        etTreeSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingTreeSearchRunnable != null) {
                    searchHandler.removeCallbacks(pendingTreeSearchRunnable);
                }
                String q = s != null ? s.toString() : "";
                pendingTreeSearchRunnable = () -> filterTreeAsync(q);
                searchHandler.postDelayed(pendingTreeSearchRunnable, 150);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        treeContainer.addView(etTreeSearch);

        rvElfTree = new RecyclerView(this);
        rvElfTree.setLayoutManager(new LinearLayoutManager(this));
        elfTreeAdapter = new ElfTreeAdapter();
        rvElfTree.setAdapter(elfTreeAdapter);
        treeContainer.addView(rvElfTree, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        contentFrame.addView(treeContainer);

        root.addView(contentFrame);
        setContentView(root);
    }

    private void loadElfData() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(soFile, "r");
                 ElfParser elf = new ElfParser(raf)) {

                if (!elf.is64 || elf.machine != 0xB7) {
                    final boolean is64 = elf.is64;
                    final int machine = elf.machine;
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        showUnsupportedElfDialog(is64, machine);
                    });
                    return;
                }

                ElfParser.Section textSec = elf.section(".text");
                if (textSec != null) {
                    textSecAddr = textSec.addr;
                    textSecOffset = textSec.offset;
                    textSecSize = textSec.size;
                }

                List<ElfParser.Symbol> rawSymbols = new ArrayList<>();
                try {
                    rawSymbols.addAll(elf.symbols(".dynsym"));
                } catch (Exception ignored) {}
                try {
                    List<ElfParser.Symbol> symtab = elf.symbols(".symtab");
                    if (symtab != null) rawSymbols.addAll(symtab);
                } catch (Exception ignored) {}

                Map<Long, Arm64Function> funcMap = new TreeMap<>();
                for (ElfParser.Symbol s : rawSymbols) {
                    if (TextUtils.isEmpty(s.name) || s.value == 0) continue;
                    boolean isFunc = (s.type == 2 || s.type == 10);
                    boolean inText = (textSec != null && s.value >= textSec.addr && s.value < textSec.addr + textSec.size);
                    if (isFunc || inText) {
                        long offset = elf.addrToOffset(s.value);
                        if (offset >= 0) {
                            Arm64Function f = funcMap.get(s.value);
                            if (f == null) {
                                f = new Arm64Function();
                                f.vaddr = s.value;
                                f.fileOffset = offset;
                                f.name = s.name;
                                f.demangledName = CppDemangler.demangle(s.name);
                                f.size = s.size;
                                f.bindName = s.bindName();
                                f.isExported = s.name.startsWith("Java_") || s.name.contains("JNI_") || "GLOBAL".equals(s.bindName());
                                funcMap.put(s.value, f);
                            } else {
                                if (s.size > f.size) f.size = s.size;
                                if (s.name.startsWith("Java_") || !f.name.startsWith("Java_")) {
                                    f.name = s.name;
                                    f.demangledName = CppDemangler.demangle(s.name);
                                }
                            }
                        }
                    }
                }

                List<Arm64Function> list = new ArrayList<>(funcMap.values());
                for (int i = 0; i < list.size(); i++) {
                    Arm64Function f = list.get(i);
                    if (f.size <= 0) {
                        if (i + 1 < list.size()) {
                            long nextAddr = list.get(i + 1).vaddr;
                            f.size = Math.min(nextAddr - f.vaddr, 4096);
                        } else if (textSec != null) {
                            f.size = Math.min((textSec.addr + textSec.size) - f.vaddr, 4096);
                        }
                        if (f.size <= 0 || f.size > 8192) f.size = 128;
                    }
                }

                if (list.isEmpty() && textSec != null && textSecSize > 0) {
                    Arm64Function f = new Arm64Function();
                    f.name = ".text (Code Section)";
                    f.vaddr = textSec.addr;
                    f.fileOffset = textSec.offset;
                    f.size = textSec.size;
                    f.bindName = "SECTION";
                    f.isExported = true;
                    list.add(f);
                }

                synchronized (allFunctions) {
                    allFunctions.clear();
                    allFunctions.addAll(list);
                    filteredFunctions.clear();
                    filteredFunctions.addAll(list);
                }

                synchronized (symbolAddressMap) {
                    symbolAddressMap.clear();
                    for (Arm64Function f : list) {
                        String existing = symbolAddressMap.get(f.vaddr);
                        if (existing == null || f.isExported || (f.demangledName != null && !f.demangledName.equals(f.name))) {
                            symbolAddressMap.put(f.vaddr, f.getDisplayName());
                        }
                    }
                }

                // Metadata, Hashes & Structure extraction
                elfEntryPoint = elf.entry;
                hexFileSize = soFile.length();
                if (hexRaf == null) {
                    try { hexRaf = new RandomAccessFile(soFile, "r"); } catch (Exception ignored) {}
                }
                elfBuildId = elf.buildId();
                elfExportHash = elf.exportHash();

                // 1. Strings extraction
                List<ElfParser.ElfString> strList = elf.allStrings(4, 30000);
                synchronized (allStrings) {
                    allStrings.clear();
                    allStrings.addAll(strList);
                    filteredStrings.clear();
                    filteredStrings.addAll(strList);
                }

                // 2. Dynamic symbols count & breakdown
                List<ElfParser.Symbol> dynsym = new ArrayList<>();
                try {
                    List<ElfParser.Symbol> ds = elf.symbols(".dynsym");
                    if (ds != null) dynsym.addAll(ds);
                } catch (Exception ignored) {}

                int impCount = 0;
                int expCount = 0;
                List<ElfTreeItem> exportItems = new ArrayList<>();
                List<ElfTreeItem> importItems = new ArrayList<>();

                for (ElfParser.Symbol s : dynsym) {
                    if (TextUtils.isEmpty(s.name)) continue;
                    if (s.shndx == 0) {
                        impCount++;
                        if (importItems.size() < 200) {
                            long off = elf.addrToOffset(s.value);
                            importItems.add(ElfTreeItem.symbol(s.name, "Import · " + s.typeName(), s.value, off));
                        }
                    } else if (s.bind == 1 || s.bind == 2) {
                        expCount++;
                        if (exportItems.size() < 200) {
                            long off = elf.addrToOffset(s.value);
                            exportItems.add(ElfTreeItem.symbol(s.name, "Export · " + s.bindName(), s.value, off));
                        }
                    }
                }
                importsCount = impCount;
                exportsCount = expCount;
                programmeCount = list.size();

                // 3. Tree Items (Segments, Code Sections, Data Sections, Symbols)
                List<ElfTreeItem> treeList = new ArrayList<>();
                ElfParser.ProgramHeader[] phs = elf.segments();
                if (phs != null && phs.length > 0) {
                    treeList.add(ElfTreeItem.header("Segments (Program Headers)", phs.length + " items"));
                    for (ElfParser.ProgramHeader ph : phs) {
                        treeList.add(ElfTreeItem.segment(ph.typeName(), ph.flagsToString(), ph.vaddr, ph.offset, ph.filesz));
                    }
                }

                ElfParser.Section[] secs = elf.sections();
                if (secs != null && secs.length > 0) {
                    List<ElfTreeItem> codeSecs = new ArrayList<>();
                    List<ElfTreeItem> roSecs = new ArrayList<>();
                    List<ElfTreeItem> dataSecs = new ArrayList<>();
                    for (ElfParser.Section sec : secs) {
                        if (sec.size == 0 || TextUtils.isEmpty(sec.name)) continue;
                        ElfTreeItem item = ElfTreeItem.section(sec.name, sec.flagsToString(), sec.addr, sec.offset, sec.size);
                        if ((sec.flags & 4) != 0 || sec.name.contains("text") || sec.name.contains("plt")) {
                            codeSecs.add(item);
                        } else if ((sec.flags & 1) == 0 && (sec.flags & 2) != 0) {
                            roSecs.add(item);
                        } else {
                            dataSecs.add(item);
                        }
                    }
                    if (!codeSecs.isEmpty()) {
                        treeList.add(ElfTreeItem.header("Code Sections", codeSecs.size() + " blocks"));
                        treeList.addAll(codeSecs);
                    }
                    if (!roSecs.isEmpty()) {
                        treeList.add(ElfTreeItem.header("Read-Only Data", roSecs.size() + " blocks"));
                        treeList.addAll(roSecs);
                    }
                    if (!dataSecs.isEmpty()) {
                        treeList.add(ElfTreeItem.header("Data & Relocations", dataSecs.size() + " blocks"));
                        treeList.addAll(dataSecs);
                    }
                }

                if (!exportItems.isEmpty()) {
                    treeList.add(ElfTreeItem.header("Exported Symbols (.dynsym)", exportItems.size() + " symbols"));
                    treeList.addAll(exportItems);
                }
                if (!importItems.isEmpty()) {
                    treeList.add(ElfTreeItem.header("Imported Symbols", importItems.size() + " symbols"));
                    treeList.addAll(importItems);
                }

                synchronized (allTreeItems) {
                    allTreeItems.clear();
                    allTreeItems.addAll(treeList);
                    filteredTreeItems.clear();
                    filteredTreeItems.addAll(treeList);
                }

                // 4. File Hashes (MD5, SHA-1, SHA-256)
                try (java.io.InputStream is = new java.io.FileInputStream(soFile)) {
                    byte[] b = new byte[65536];
                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                    int r;
                    while ((r = is.read(b)) > 0) {
                        md5.update(b, 0, r);
                        sha1.update(b, 0, r);
                        sha256.update(b, 0, r);
                    }
                    hashMd5 = CryptoHelper.bytesToHex(md5.digest());
                    hashSha1 = CryptoHelper.bytesToHex(sha1.digest());
                    hashSha256 = CryptoHelper.bytesToHex(sha256.digest());
                } catch (Exception ignored) {}

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    String info = String.format(Locale.ROOT, "ELF64 AArch64 | %d functions | .text: 0x%X - 0x%X",
                            allFunctions.size(), textSecAddr, textSecAddr + textSecSize);
                    tvStatusInfo.setText(info);
                    if (tvTreeProgramme != null) tvTreeProgramme.setText("Programme: " + programmeCount);
                    if (tvTreeImports != null) tvTreeImports.setText("Imports: " + importsCount);
                    if (tvTreeExports != null) tvTreeExports.setText("Exports: " + exportsCount);
                    if (tvStringsCount != null) tvStringsCount.setText(String.valueOf(allStrings.size()));
                    if (tvHexStatus != null) {
                        tvHexStatus.setText(String.format(Locale.US, "%s · %s · %d bytes",
                                new File(originalPath).getName(), ElfTreeItem.formatSize(hexFileSize), hexFileSize));
                    }
                    functionsAdapter.notifyDataSetChanged();
                    if (stringsAdapter != null) stringsAdapter.notifyDataSetChanged();
                    if (hexAdapter != null) hexAdapter.notifyDataSetChanged();
                    if (elfTreeAdapter != null) elfTreeAdapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showElfErrorDialog("ELF Parse Failed", "Failed to parse ELF binary:\n" + e.getMessage() + "\n\nFile: " + soPath);
                });
            }
        }).start();
    }

    private void showUnsupportedElfDialog(boolean is64, int machine) {
        String archName = ElfParser.machineName(machine);
        String bitStr = is64 ? "64-bit" : "32-bit";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unsupported Architecture")
                .setMessage(String.format(Locale.ROOT,
                        "This library is %s %s (Machine: 0x%X).\n\nThe ARM64 Disassembler currently supports 64-bit ARM64 (AArch64) binaries.\n\nYou can still inspect the raw file in the Hex Editor.",
                        bitStr, archName, machine))
                .setPositiveButton("Open Hex Editor", (d, w) -> {
                    Intent i = new Intent(this, HexEditorActivity.class).putExtra("path", soFile.getAbsolutePath());
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Close", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showElfErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton("Copy Error", (d, w) -> copyToClipboard(message, "ELF Error"))
                .setPositiveButton("Open Hex Editor", (d, w) -> {
                    Intent i = new Intent(this, HexEditorActivity.class).putExtra("path", soFile.getAbsolutePath());
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Close", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void filterFunctionsAsync(String query) {
        new Thread(() -> {
            List<Arm64Function> temp = new ArrayList<>();
            synchronized (allFunctions) {
                if (TextUtils.isEmpty(query)) {
                    temp.addAll(allFunctions);
                } else {
                    String q = query.toLowerCase(Locale.ROOT).trim();
                    boolean filterJni = "jni".equals(q);
                    boolean filterExport = "export".equals(q) || "exported".equals(q);
                    boolean filterGlobal = "global".equals(q);
                    boolean filterLocal = "local".equals(q);

                    for (Arm64Function f : allFunctions) {
                        if (filterJni && (f.name.startsWith("Java_") || f.name.contains("JNI_"))) {
                            temp.add(f);
                        } else if (filterExport && f.isExported) {
                            temp.add(f);
                        } else if (filterGlobal && "GLOBAL".equalsIgnoreCase(f.bindName)) {
                            temp.add(f);
                        } else if (filterLocal && "LOCAL".equalsIgnoreCase(f.bindName)) {
                            temp.add(f);
                        } else if (f.name.toLowerCase(Locale.ROOT).contains(q) ||
                                (f.demangledName != null && f.demangledName.toLowerCase(Locale.ROOT).contains(q)) ||
                                f.getFormattedAddress().toLowerCase(Locale.ROOT).contains(q) ||
                                f.getFormattedOffset().toLowerCase(Locale.ROOT).contains(q) ||
                                (f.bindName != null && f.bindName.toLowerCase(Locale.ROOT).contains(q))) {
                            temp.add(f);
                        }
                    }
                }
            }
            runOnUiThread(() -> {
                synchronized (filteredFunctions) {
                    filteredFunctions.clear();
                    filteredFunctions.addAll(temp);
                }
                functionsAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void disassembleFunction(Arm64Function func) {
        disassembleFunction(func, null);
    }

    private void disassembleFunction(Arm64Function func, Runnable onComplete) {
        currentFunction = func;
        loadedInstructionBytes = 0;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
                List<Arm64Instruction> instrs = new ArrayList<>();
                long startOffset = func.fileOffset;
                long startVaddr = func.vaddr;
                int numBytes = (int) Math.min(func.size, DISASM_CHUNK_SIZE);
                if (numBytes <= 0) numBytes = 128;
                numBytes = (numBytes / 4) * 4;

                byte[] buffer = new byte[numBytes];
                raf.seek(startOffset);
                int read = raf.read(buffer);
                if (read < 4) read = 0;

                for (int i = 0; i + 3 < read; i += 4) {
                    long curOffset = startOffset + i;
                    long curVaddr = startVaddr + i;

                    byte[] bytes = pendingPatches.containsKey(curOffset)
                            ? pendingPatches.get(curOffset)
                            : new byte[]{buffer[i], buffer[i + 1], buffer[i + 2], buffer[i + 3]};

                    int op = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8) | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);

                    Arm64Instruction inst = new Arm64Instruction();
                    inst.address = curVaddr;
                    inst.fileOffset = curOffset;
                    inst.opcode = op;
                    inst.rawBytes = bytes;
                    inst.originalBytes = new byte[]{buffer[i], buffer[i + 1], buffer[i + 2], buffer[i + 3]};
                    inst.isModified = pendingPatches.containsKey(curOffset);

                    try {
                        AsmStatement stmt = decoder.decode(curVaddr, op);
                        inst.mnemonic = stmt.opcode != null ? stmt.opcode.toUpperCase(Locale.ROOT) : ".INST";
                        inst.fullAsm = stmt.format();
                    } catch (Throwable t) {
                        inst.mnemonic = ".INST";
                        inst.fullAsm = String.format(Locale.ROOT, ".inst 0x%08X", op);
                    }
                    instrs.add(inst);
                }

                loadedInstructionBytes = (read / 4) * 4;

                synchronized (currentInstructions) {
                    currentInstructions.clear();
                    currentInstructions.addAll(instrs);
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showDisassemblyViewUi();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Disassembly Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadMoreInstructions() {
        if (currentFunction == null) return;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
                List<Arm64Instruction> instrs = new ArrayList<>();
                long startOffset = currentFunction.fileOffset + loadedInstructionBytes;
                long startVaddr = currentFunction.vaddr + loadedInstructionBytes;
                long remaining = currentFunction.size - loadedInstructionBytes;
                int numBytes = (int) Math.min(remaining, DISASM_CHUNK_SIZE);
                if (numBytes <= 0) return;
                numBytes = (numBytes / 4) * 4;

                byte[] buffer = new byte[numBytes];
                raf.seek(startOffset);
                int read = raf.read(buffer);
                if (read < 4) read = 0;

                for (int i = 0; i + 3 < read; i += 4) {
                    long curOffset = startOffset + i;
                    long curVaddr = startVaddr + i;

                    byte[] bytes = pendingPatches.containsKey(curOffset)
                            ? pendingPatches.get(curOffset)
                            : new byte[]{buffer[i], buffer[i + 1], buffer[i + 2], buffer[i + 3]};

                    int op = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8) | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);

                    Arm64Instruction inst = new Arm64Instruction();
                    inst.address = curVaddr;
                    inst.fileOffset = curOffset;
                    inst.opcode = op;
                    inst.rawBytes = bytes;
                    inst.originalBytes = new byte[]{buffer[i], buffer[i + 1], buffer[i + 2], buffer[i + 3]};
                    inst.isModified = pendingPatches.containsKey(curOffset);

                    try {
                        AsmStatement stmt = decoder.decode(curVaddr, op);
                        inst.mnemonic = stmt.opcode != null ? stmt.opcode.toUpperCase(Locale.ROOT) : ".INST";
                        inst.fullAsm = stmt.format();
                    } catch (Throwable t) {
                        inst.mnemonic = ".INST";
                        inst.fullAsm = String.format(Locale.ROOT, ".inst 0x%08X", op);
                    }
                    instrs.add(inst);
                }

                int addedBytes = (read / 4) * 4;
                loadedInstructionBytes += addedBytes;

                final int oldSize;
                synchronized (currentInstructions) {
                    oldSize = currentInstructions.size();
                    currentInstructions.addAll(instrs);
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    instructionsAdapter.notifyItemRangeInserted(oldSize, instrs.size());
                    updateDisassemblyHeader();
                    updateLoadMoreButton();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Load More Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateDisassemblyHeader() {
        if (currentFunction == null) return;
        toolbar.setSubtitle(currentFunction.getDisplayName());
        int totalInstrs = currentFunction.getInstructionCount();
        if (totalInstrs > currentInstructions.size()) {
            tvDisasmHeader.setText(String.format(Locale.ROOT, "%s  [%s | %d/%d instrs]",
                    currentFunction.getDisplayName(), currentFunction.getFormattedAddress(),
                    currentInstructions.size(), totalInstrs));
        } else {
            tvDisasmHeader.setText(String.format(Locale.ROOT, "%s  [%s - 0x%X]",
                    currentFunction.getDisplayName(), currentFunction.getFormattedAddress(),
                    currentFunction.vaddr + currentFunction.size));
        }
    }

    private void updateLoadMoreButton() {
        if (currentFunction == null || btnLoadMore == null) return;
        boolean hasMore = loadedInstructionBytes < currentFunction.size && (currentFunction.fileOffset + loadedInstructionBytes < soFile.length());
        if (hasMore) {
            int remainingInstrs = (int) ((currentFunction.size - loadedInstructionBytes) / 4);
            int nextBatch = Math.min(16384, remainingInstrs);
            btnLoadMore.setText("Load More (+" + nextBatch + ")");
            btnLoadMore.setVisibility(View.VISIBLE);
        } else {
            btnLoadMore.setVisibility(View.GONE);
        }
    }

    private void showDisassemblyViewUi() {
        hideKeyboard();
        isDisassemblyMode = true;
        if (tabLayout != null && tabLayout.getSelectedTabPosition() != 0) {
            tabLayout.getTabAt(0).select();
        }
        if (stringsContainer != null) stringsContainer.setVisibility(View.GONE);
        if (hexContainer != null) hexContainer.setVisibility(View.GONE);
        if (treeContainer != null) treeContainer.setVisibility(View.GONE);
        functionsContainer.setVisibility(View.GONE);
        disassemblyContainer.setVisibility(View.VISIBLE);
        updateDisassemblyHeader();
        updateLoadMoreButton();
        instructionsAdapter.notifyDataSetChanged();
    }

    private void showFunctionsViewUi() {
        isDisassemblyMode = false;
        navHistory.clear();
        if (tabLayout != null && tabLayout.getSelectedTabPosition() != 0) {
            tabLayout.getTabAt(0).select();
        }
        disassemblyContainer.setVisibility(View.GONE);
        if (stringsContainer != null) stringsContainer.setVisibility(View.GONE);
        if (hexContainer != null) hexContainer.setVisibility(View.GONE);
        if (treeContainer != null) treeContainer.setVisibility(View.GONE);
        functionsContainer.setVisibility(View.VISIBLE);
        toolbar.setSubtitle("ARM64 Disassembler");
    }

    private void showGoToDialog() {
        EditText input = new EditText(this);
        input.setHint("Hex Address or Offset (e.g. 1A400 or 0x1A400)");
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE);

        String msg = "Enter a virtual address or file offset to inspect and disassemble:";
        if (textSecSize > 0) {
            msg += String.format(Locale.ROOT, "\n.text Code Section: 0x%X - 0x%X (Offset: 0x%X)",
                    textSecAddr, textSecAddr + textSecSize, textSecOffset);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Go to Address / Offset")
                .setMessage(msg)
                .setView(wrapDialogView(input))
                .setPositiveButton("Go", (d, w) -> {
                    String text = input.getText().toString().trim().replace("0x", "").replace("0X", "");
                    if (TextUtils.isEmpty(text)) return;
                    try {
                        long target = Long.parseLong(text, 16);
                        jumpToAddressOrOffset(target);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid hex number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);

        if (textSecSize > 0) {
            builder.setNeutralButton("Jump to .text", (d, w) -> jumpToAddressOrOffset(textSecAddr));
        }

        builder.show();
    }

    private void jumpToAddressOrOffset(long target) {
        recordCurrentNavLocation();
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(soFile, "r");
                 ElfParser elf = new ElfParser(raf)) {

                long vaddr = target;
                long offset = elf.addrToOffset(target);
                if (offset < 0) {
                    long mappedVaddr = elf.offsetToAddr(target);
                    if (mappedVaddr >= 0) {
                        offset = target;
                        vaddr = mappedVaddr;
                    } else {
                        offset = target;
                    }
                }
                offset = (offset / 4) * 4;
                vaddr = (vaddr / 4) * 4;

                if (offset < 0 || offset >= soFile.length()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Target offset out of bounds: 0x" + Long.toHexString(target), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                Arm64Function f = new Arm64Function();
                f.name = String.format(Locale.ROOT, "loc_%08X", vaddr);
                f.vaddr = vaddr;
                f.fileOffset = offset;
                f.size = 256;
                f.bindName = "GOTO";
                f.isExported = false;

                disassembleFunction(f);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private long extractBranchTarget(Arm64Instruction inst) {
        if (inst == null || inst.mnemonic == null || inst.fullAsm == null) return -1;
        String m = inst.mnemonic;
        boolean isBranch = m.equals("B") || m.startsWith("B.") || m.equals("BL")
                || m.startsWith("CB") || m.startsWith("TB");
        if (!isBranch) return -1;

        String full = inst.fullAsm;
        int idx = 0;
        while ((idx = full.indexOf("0x", idx)) != -1) {
            // Ensure not preceded by '#'
            if (idx == 0 || full.charAt(idx - 1) != '#') {
                int start = idx + 2;
                int end = start;
                while (end < full.length() && Character.digit(full.charAt(end), 16) != -1) {
                    end++;
                }
                if (end > start) {
                    try {
                        return Long.parseLong(full.substring(start, end), 16);
                    } catch (NumberFormatException ignored) {}
                }
            }
            idx += 2;
        }
        return -1;
    }

    private void recordCurrentNavLocation() {
        if (currentFunction == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) rvInstructions.getLayoutManager();
        int pos = lm != null ? lm.findFirstVisibleItemPosition() : 0;
        long curAddr = (pos >= 0 && pos < currentInstructions.size())
                ? currentInstructions.get(pos).address
                : currentFunction.vaddr;
        navHistory.push(new NavLocation(currentFunction, curAddr));
    }

    private boolean popNavigationHistory() {
        if (navHistory.isEmpty()) return false;
        NavLocation prev = navHistory.pop();
        if (prev.function != null && prev.function != currentFunction) {
            disassembleFunction(prev.function, () -> {
                int targetPos = findInstructionPosition(prev.address);
                if (targetPos >= 0) {
                    ((LinearLayoutManager) rvInstructions.getLayoutManager()).scrollToPositionWithOffset(targetPos, dp(30));
                }
            });
        } else {
            int targetPos = findInstructionPosition(prev.address);
            if (targetPos >= 0) {
                ((LinearLayoutManager) rvInstructions.getLayoutManager()).scrollToPositionWithOffset(targetPos, dp(30));
            }
        }
        Toast.makeText(this, "<- Back to 0x" + Long.toHexString(prev.address).toUpperCase(Locale.ROOT), Toast.LENGTH_SHORT).show();
        return true;
    }

    private int findInstructionPosition(long addr) {
        synchronized (currentInstructions) {
            for (int i = 0; i < currentInstructions.size(); i++) {
                if (currentInstructions.get(i).address == addr) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void navigateToAddress(long targetAddr) {
        if (targetAddr <= 0) return;
        recordCurrentNavLocation();

        // 1. Check if target is already loaded in currentInstructions
        int pos = findInstructionPosition(targetAddr);
        if (pos >= 0) {
            ((LinearLayoutManager) rvInstructions.getLayoutManager()).scrollToPositionWithOffset(pos, dp(30));
            instructionsAdapter.notifyItemChanged(pos);
            Toast.makeText(this, String.format(Locale.ROOT, "Jumped to 0x%08X (line %d)", targetAddr, pos + 1), Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. If not in currentInstructions, check if it belongs to a known function
        for (Arm64Function f : allFunctions) {
            if (targetAddr >= f.vaddr && targetAddr < f.vaddr + f.size) {
                disassembleFunction(f, () -> {
                    int p = findInstructionPosition(targetAddr);
                    if (p >= 0) {
                        ((LinearLayoutManager) rvInstructions.getLayoutManager()).scrollToPositionWithOffset(p, dp(30));
                    }
                });
                Toast.makeText(this, "Jumped to: " + f.getDisplayName(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 3. Fallback: jump to target address directly
        jumpToAddressOrOffset(targetAddr);
    }

    private void showPatchDialog(Arm64Instruction inst, int position) {
        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView tvInfo = new TextView(this);
        tvInfo.setTypeface(Typeface.MONOSPACE);
        tvInfo.setTextSize(13);
        tvInfo.setText(String.format(Locale.ROOT, "Address: 0x%08X (Offset: 0x%08X)\nOriginal: %s  (%s)\nCurrent:  %s  (%s)",
                inst.address, inst.fileOffset,
                decodeAsmString(inst.address, inst.originalBytes), inst.getOriginalHexBytes(),
                inst.fullAsm, inst.getHexBytes()));
        layout.addView(tvInfo);

        final long branchTarget = extractBranchTarget(inst);
        if (branchTarget > 0) {
            String symName = symbolAddressMap.get(branchTarget);
            String label = (symName != null && !symName.isEmpty())
                    ? String.format(Locale.ROOT, "Follow Branch -> %s (0x%08X)", symName, branchTarget)
                    : String.format(Locale.ROOT, "Follow Branch Target (0x%08X) ->", branchTarget);

            MaterialButton btnJumpTarget = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnJumpTarget.setText(label);
            LinearLayout.LayoutParams lpJump = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpJump.topMargin = dp(8);
            btnJumpTarget.setLayoutParams(lpJump);
            btnJumpTarget.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                navigateToAddress(branchTarget);
            });
            layout.addView(btnJumpTarget);
        }

        // Preset Section 1: Function Exit (MOV W0, #x + RET)
        boolean hasRoomForRet = (position + 1 < currentInstructions.size());
        TextView tvFuncExit = new TextView(this);
        if (hasRoomForRet) {
            tvFuncExit.setText("Function Return (MOV + RET):");
        } else {
            tvFuncExit.setText("Function Return (MOV + RET) - [Last instruction, needs 2 slots]:");
            tvFuncExit.setTextColor(0xFF888888);
        }
        tvFuncExit.setTypeface(Typeface.DEFAULT_BOLD);
        tvFuncExit.setPadding(0, dp(12), 0, dp(4));
        layout.addView(tvFuncExit);

        LinearLayout rowFunc = new LinearLayout(this);
        rowFunc.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton btnFuncTrue = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnFuncTrue.setText("Return 1 (MOV+RET)");
        btnFuncTrue.setEnabled(hasRoomForRet);
        btnFuncTrue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        MaterialButton btnFuncFalse = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnFuncFalse.setText("Return 0 (MOV+RET)");
        btnFuncFalse.setEnabled(hasRoomForRet);
        LinearLayout.LayoutParams pFuncFalse = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        pFuncFalse.leftMargin = dp(8);
        btnFuncFalse.setLayoutParams(pFuncFalse);

        rowFunc.addView(btnFuncTrue);
        rowFunc.addView(btnFuncFalse);
        layout.addView(rowFunc);

        // Preset Section 2: Single Instruction Presets
        TextView tvPresets = new TextView(this);
        tvPresets.setText("Single Instruction Presets:");
        tvPresets.setTypeface(Typeface.DEFAULT_BOLD);
        tvPresets.setPadding(0, dp(10), 0, dp(4));
        layout.addView(tvPresets);

        LinearLayout presetsRow1 = new LinearLayout(this);
        presetsRow1.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton btnRet1 = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRet1.setText("MOV W0, #1");
        btnRet1.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        MaterialButton btnRet0 = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRet0.setText("MOV W0, #0");
        LinearLayout.LayoutParams p0 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p0.leftMargin = dp(8);
        btnRet0.setLayoutParams(p0);

        presetsRow1.addView(btnRet1);
        presetsRow1.addView(btnRet0);
        layout.addView(presetsRow1);

        LinearLayout presetsRow2 = new LinearLayout(this);
        presetsRow2.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow2.setPadding(0, dp(4), 0, 0);

        MaterialButton btnNop = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnNop.setText("NOP");
        btnNop.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        MaterialButton btnRet = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRet.setText("RET");
        LinearLayout.LayoutParams pRet = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        pRet.leftMargin = dp(8);
        btnRet.setLayoutParams(pRet);

        presetsRow2.addView(btnNop);
        presetsRow2.addView(btnRet);
        layout.addView(presetsRow2);

        TextView tvCustom = new TextView(this);
        tvCustom.setText("Custom Hex (4 Bytes, Little-Endian):");
        tvCustom.setTypeface(Typeface.DEFAULT_BOLD);
        tvCustom.setPadding(0, dp(14), 0, dp(4));
        layout.addView(tvCustom);

        EditText etHex = new EditText(this);
        etHex.setText(inst.getHexBytes().replace(" ", ""));
        etHex.setHint("e.g. 20008052 or 1F2003D5");
        etHex.setTypeface(Typeface.MONOSPACE);
        etHex.setTextSize(14);
        etHex.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(8),
                new android.text.InputFilter.AllCaps()
        });
        layout.addView(etHex);

        TextView tvHexPreview = new TextView(this);
        tvHexPreview.setTypeface(Typeface.MONOSPACE);
        tvHexPreview.setTextSize(12);
        tvHexPreview.setTextColor(0xFF4FC3F7);
        tvHexPreview.setPadding(0, dp(4), 0, dp(8));
        tvHexPreview.setText("Decoded: " + inst.fullAsm);
        layout.addView(tvHexPreview);

        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String clean = s != null ? s.toString().replaceAll("[^0-9a-fA-F]", "") : "";
                if (clean.length() == 8) {
                    byte[] parsed = parseHexInput(clean);
                    String decoded = decodeAsmString(inst.address, parsed);
                    if (decoded.startsWith(".inst")) {
                        tvHexPreview.setTextColor(0xFFFFA726); // Orange warning for raw opcode
                        tvHexPreview.setText("Decoded: " + decoded + " (Raw/Unallocated opcode)");
                    } else {
                        tvHexPreview.setTextColor(0xFF4FC3F7); // Cyan
                        tvHexPreview.setText("Decoded: " + decoded);
                    }
                } else {
                    tvHexPreview.setTextColor(0xFF888888);
                    tvHexPreview.setText("Enter 8 hex digits (" + clean.length() + "/8)");
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Preset click handlers fill etHex
        btnRet1.setOnClickListener(v -> etHex.setText("20008052")); // MOV W0, #1
        btnRet0.setOnClickListener(v -> etHex.setText("00008052")); // MOV W0, #0
        btnNop.setOnClickListener(v -> etHex.setText("1F2003D5"));  // NOP
        btnRet.setOnClickListener(v -> etHex.setText("C0035FD6"));  // RET

        btnFuncTrue.setOnClickListener(v -> applyReturnFunctionPatch(inst, position, true, dialogRef[0]));
        btnFuncFalse.setOnClickListener(v -> applyReturnFunctionPatch(inst, position, false, dialogRef[0]));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Patch Instruction")
                .setView(layout)
                .setPositiveButton("Apply", (d, w) -> {
                    byte[] parsed = parseHexInput(etHex.getText().toString());
                    if (parsed == null || parsed.length != 4) {
                        Toast.makeText(this, "Invalid hex bytes! Must be 8 characters.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    applyInstructionPatch(inst, parsed, position);
                })
                .setNegativeButton(android.R.string.cancel, null);

        if (inst.isModified) {
            builder.setNeutralButton("Revert", (d, w) -> revertInstructionPatch(inst, position));
        }

        dialogRef[0] = builder.show();
    }

    private void exportCurrentFunctionDisassembly() {
        if (currentFunction == null || currentInstructions.isEmpty()) return;
        new Thread(() -> {
            try {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String safeName = currentFunction.name.replaceAll("[^a-zA-Z0-9_.-]", "_");
                File outFile = new File(downloadDir, safeName + ".asm");
                int counter = 1;
                while (outFile.exists()) {
                    outFile = new File(downloadDir, safeName + "_" + counter + ".asm");
                    counter++;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("// =====================================================================\n");
                sb.append("// Function: ").append(currentFunction.name).append("\n");
                sb.append("// Address: ").append(currentFunction.getFormattedAddress())
                  .append(" - 0x").append(Long.toHexString(currentFunction.vaddr + currentFunction.size)).append("\n");
                sb.append("// File Offset: ").append(currentFunction.getFormattedOffset()).append("\n");
                sb.append("// Size: ").append(currentFunction.size).append(" bytes (")
                  .append(currentInstructions.size()).append(" instructions)\n");
                sb.append("// Binding: ").append(currentFunction.bindName).append("\n");
                sb.append("// =====================================================================\n\n");

                synchronized (currentInstructions) {
                    for (Arm64Instruction inst : currentInstructions) {
                        String opStr = inst.fullAsm.contains(" ") ? inst.fullAsm.substring(inst.fullAsm.indexOf(' ') + 1) : "";
                        sb.append(String.format(Locale.ROOT, "0x%08X: [%s]  %-10s %s%s\n",
                                inst.address, inst.getHexBytes(), inst.mnemonic, opStr,
                                (inst.isModified ? " // [MODIFIED]" : "")));
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }

                final File finalOut = outFile;
                runOnUiThread(() -> Toast.makeText(this, "Exported: " + finalOut.getName() + " to Download", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void applyReturnFunctionPatch(Arm64Instruction inst, int position, boolean returnTrue, androidx.appcompat.app.AlertDialog dialog) {
        if (position + 1 >= currentInstructions.size()) {
            Toast.makeText(this, "Cannot apply Return preset: needs 2 instruction slots (MOV+RET), but instruction is at the end of section.", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] movBytes = parseHexInput(returnTrue ? "20008052" : "00008052");
        byte[] retBytes = parseHexInput("C0035FD6");

        applyInstructionPatch(inst, movBytes, position, false);

        Arm64Instruction nextInst = currentInstructions.get(position + 1);
        applyInstructionPatch(nextInst, retBytes, position + 1, false);
        instructionsAdapter.notifyItemRangeChanged(position, 2);

        if (dialog != null) dialog.dismiss();
        Toast.makeText(this, returnTrue ? "Patched: Return 1 (MOV W0, #1; RET)" : "Patched: Return 0 (MOV W0, #0; RET)", Toast.LENGTH_SHORT).show();
    }

    private void applyInstructionPatch(Arm64Instruction inst, byte[] newBytes, int position) {
        applyInstructionPatch(inst, newBytes, position, true);
    }

    private void applyInstructionPatch(Arm64Instruction inst, byte[] newBytes, int position, boolean showToast) {
        pendingPatches.put(inst.fileOffset, newBytes);
        inst.rawBytes = newBytes.clone();
        inst.opcode = (newBytes[0] & 0xFF) | ((newBytes[1] & 0xFF) << 8) | ((newBytes[2] & 0xFF) << 16) | ((newBytes[3] & 0xFF) << 24);
        try {
            AsmStatement stmt = decoder.decode(inst.address, inst.opcode);
            inst.mnemonic = stmt.opcode != null ? stmt.opcode.toUpperCase(Locale.ROOT) : ".INST";
            inst.fullAsm = stmt.format();
        } catch (Throwable t) {
            inst.mnemonic = ".INST";
            inst.fullAsm = String.format(Locale.ROOT, ".inst 0x%08X", inst.opcode);
        }
        inst.isModified = true;

        instructionsAdapter.notifyItemChanged(position);
        updateSaveButtonUi();
        if (showToast) {
            Toast.makeText(this, "Patch applied: " + inst.fullAsm, Toast.LENGTH_SHORT).show();
        }
    }

    private void revertInstructionPatch(Arm64Instruction inst, int position) {
        pendingPatches.remove(inst.fileOffset);
        inst.rawBytes = inst.originalBytes.clone();
        inst.opcode = (inst.rawBytes[0] & 0xFF) | ((inst.rawBytes[1] & 0xFF) << 8) | ((inst.rawBytes[2] & 0xFF) << 16) | ((inst.rawBytes[3] & 0xFF) << 24);
        try {
            AsmStatement stmt = decoder.decode(inst.address, inst.opcode);
            inst.mnemonic = stmt.opcode != null ? stmt.opcode.toUpperCase(Locale.ROOT) : ".INST";
            inst.fullAsm = stmt.format();
        } catch (Throwable t) {
            inst.mnemonic = ".INST";
            inst.fullAsm = String.format(Locale.ROOT, ".inst 0x%08X", inst.opcode);
        }
        inst.isModified = false;

        instructionsAdapter.notifyItemChanged(position);
        updateSaveButtonUi();
        Toast.makeText(this, "Reverted to original", Toast.LENGTH_SHORT).show();
    }

    private void updateSaveButtonUi() {
        int count = pendingPatches.size();
        if (count > 0) {
            btnSave.setVisibility(View.VISIBLE);
            btnSave.setText(String.format(Locale.ROOT, "Save (%d)", count));
            if (btnReview != null) {
                btnReview.setVisibility(View.VISIBLE);
                btnReview.setText(String.format(Locale.ROOT, "Diff (%d)", count));
            }
        } else {
            btnSave.setVisibility(View.GONE);
            if (btnReview != null) {
                btnReview.setVisibility(View.GONE);
            }
        }
    }

    private void showReviewPatchesDialog() {
        if (pendingPatches.isEmpty()) {
            Toast.makeText(this, "No pending patches to review", Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView tvHeader = new TextView(this);
        tvHeader.setText(String.format(Locale.ROOT, "Total Pending Patches: %d", pendingPatches.size()));
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextSize(14);
        tvHeader.setPadding(0, 0, 0, dp(10));
        content.addView(tvHeader);

        final androidx.appcompat.app.AlertDialog[] reviewDialog = new androidx.appcompat.app.AlertDialog[1];

        for (Map.Entry<Long, byte[]> entry : pendingPatches.entrySet()) {
            final long fileOff = entry.getKey();
            final byte[] patchedBytes = entry.getValue();

            Arm64Instruction foundInst = null;
            synchronized (currentInstructions) {
                for (Arm64Instruction inst : currentInstructions) {
                    if (inst.fileOffset == fileOff) {
                        foundInst = inst;
                        break;
                    }
                }
            }

            final Arm64Instruction inst = foundInst;
            final long addr = inst != null ? inst.address : (textSecAddr + (fileOff - textSecOffset));
            byte[] origBytes = inst != null ? inst.originalBytes : null;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setColor(0x18888888);
            itemBg.setCornerRadius(dp(6));
            item.setBackground(itemBg);

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.bottomMargin = dp(8);
            item.setLayoutParams(itemLp);

            TextView tvTitle = new TextView(this);
            tvTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvTitle.setTextSize(13);
            tvTitle.setTextColor(0xFF4FC3F7);
            tvTitle.setText(String.format(Locale.ROOT, "Offset: 0x%08X  |  Addr: 0x%08X", fileOff, addr));
            item.addView(tvTitle);

            TextView tvDiff = new TextView(this);
            tvDiff.setTypeface(Typeface.MONOSPACE);
            tvDiff.setTextSize(12);
            String origAsm = (origBytes != null) ? decodeAsmString(addr, origBytes) : "original";
            String patchedAsm = decodeAsmString(addr, patchedBytes);
            String origHex = (origBytes != null) ? formatBytesHex(origBytes) : "??";
            String patchedHex = formatBytesHex(patchedBytes);
            tvDiff.setText(String.format(Locale.ROOT, "- Orig: %s  [%s]\n+ Mod:  %s  [%s]",
                    origAsm, origHex, patchedAsm, patchedHex));
            tvDiff.setPadding(0, dp(4), 0, dp(6));
            item.addView(tvDiff);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            MaterialButton btnJump = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnJump.setText("View");
            btnJump.setTextSize(11);
            btnJump.setOnClickListener(v -> {
                if (reviewDialog[0] != null) reviewDialog[0].dismiss();
                navigateToAddress(addr);
            });
            btnRow.addView(btnJump);

            MaterialButton btnDrop = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnDrop.setText("Revert");
            btnDrop.setTextSize(11);
            btnDrop.setTextColor(0xFFEF5350);
            LinearLayout.LayoutParams dropLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dropLp.leftMargin = dp(8);
            btnDrop.setLayoutParams(dropLp);
            btnDrop.setOnClickListener(v -> {
                pendingPatches.remove(fileOff);
                if (inst != null) {
                    inst.rawBytes = inst.originalBytes.clone();
                    inst.opcode = (inst.rawBytes[0] & 0xFF) | ((inst.rawBytes[1] & 0xFF) << 8) | ((inst.rawBytes[2] & 0xFF) << 16) | ((inst.rawBytes[3] & 0xFF) << 24);
                    try {
                        AsmStatement stmt = decoder.decode(inst.address, inst.opcode);
                        inst.mnemonic = stmt.opcode != null ? stmt.opcode.toUpperCase(Locale.ROOT) : ".INST";
                        inst.fullAsm = stmt.format();
                    } catch (Throwable t) {
                        inst.mnemonic = ".INST";
                        inst.fullAsm = String.format(Locale.ROOT, ".inst 0x%08X", inst.opcode);
                    }
                    inst.isModified = false;
                    instructionsAdapter.notifyDataSetChanged();
                }
                updateSaveButtonUi();
                if (reviewDialog[0] != null) reviewDialog[0].dismiss();
                if (!pendingPatches.isEmpty()) {
                    showReviewPatchesDialog();
                } else {
                    Toast.makeText(this, "All patches reverted", Toast.LENGTH_SHORT).show();
                }
            });
            btnRow.addView(btnDrop);

            item.addView(btnRow);
            content.addView(item);
        }

        scroll.addView(content);

        reviewDialog[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("Review Pending Patches")
                .setView(scroll)
                .setPositiveButton("Save All", (d, w) -> saveAllPatches())
                .setNeutralButton("Discard All", (d, w) -> {
                    for (Arm64Instruction i : currentInstructions) {
                        if (i.isModified) {
                            i.rawBytes = i.originalBytes.clone();
                            i.isModified = false;
                        }
                    }
                    pendingPatches.clear();
                    updateSaveButtonUi();
                    instructionsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Discarded all patches", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showExportOptionsDialog() {
        String[] options = new String[] {
            "Export Function Disassembly (.asm)",
            "Export Patched Library (.so) to Download"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Export Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        exportCurrentFunctionDisassembly();
                    } else if (which == 1) {
                        exportPatchedSoToDownload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String formatBytesHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    private void saveAllPatches() {
        saveAllPatches(null);
    }

    private void saveAllPatches(Runnable onComplete) {
        if (pendingPatches.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                // 1. Write in-place to the local .so copy
                long fileLen = soFile.length();
                try (RandomAccessFile raf = new RandomAccessFile(soFile, "rw")) {
                    for (Map.Entry<Long, byte[]> entry : pendingPatches.entrySet()) {
                        long off = entry.getKey();
                        if (off < 0 || off + 4 > fileLen) {
                            throw new IOException("Offset out of bounds: 0x" + Long.toHexString(off));
                        }
                        raf.seek(off);
                        raf.write(entry.getValue());
                    }
                } catch (Exception writeEx) {
                    // Fallback: create patched copy in private cache and overwrite via PrivilegeManager
                    File tmp = new File(getCacheDir(), "patch_tmp_" + System.currentTimeMillis() + ".so");
                    FileUtils.copyFile(soFile, tmp);
                    try (RandomAccessFile rafTmp = new RandomAccessFile(tmp, "rw")) {
                        for (Map.Entry<Long, byte[]> entry : pendingPatches.entrySet()) {
                            long off = entry.getKey();
                            if (off >= 0 && off + 4 <= fileLen) {
                                rafTmp.seek(off);
                                rafTmp.write(entry.getValue());
                            }
                        }
                    }
                    PrivilegeManager.Files.copyFile(this, tmp.getAbsolutePath(), soFile.getAbsolutePath(), true);
                    tmp.delete();
                }

                // 2. If staged from a root/system/external path, copy back
                if (!TextUtils.isEmpty(originalPath) && !originalPath.equals(soFile.getAbsolutePath())) {
                    PrivilegeManager.Files.copyFile(this, soFile.getAbsolutePath(), originalPath, true);
                }

                // 3. Update original bytes cache for current instructions
                synchronized (currentInstructions) {
                    for (Arm64Instruction inst : currentInstructions) {
                        if (pendingPatches.containsKey(inst.fileOffset)) {
                            inst.originalBytes = inst.rawBytes.clone();
                            inst.isModified = false;
                        }
                    }
                }

                int count = pendingPatches.size();
                pendingPatches.clear();

                Intent resultData = new Intent();
                resultData.setData(Uri.fromFile(new File(originalPath != null ? originalPath : soPath)));
                if (zipFilePath != null) resultData.putExtra("zipFilePath", zipFilePath);
                if (zipEntryPath != null) resultData.putExtra("zipEntryPath", zipEntryPath);
                setResult(757, resultData);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSaveButtonUi();
                    instructionsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Saved " + count + " patch(es) successfully!", Toast.LENGTH_SHORT).show();
                    if (onComplete != null) onComplete.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showSaveFailedDialog(e);
                });
            }
        }).start();
    }

    private void showSaveFailedDialog(Exception ex) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Save Failed")
                .setMessage("Could not save changes directly to the binary:\n" + ex.getMessage() +
                        "\n\nTarget path:\n" + (originalPath != null ? originalPath : soPath) +
                        "\n\nWould you like to export a patched copy of this library to your Download folder so your patches are not lost?")
                .setPositiveButton("Export to Download", (d, w) -> exportPatchedSoToDownload())
                .setNeutralButton("Copy Error", (d, w) -> copyToClipboard(ex.getMessage(), "Save Error"))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportPatchedSoToDownload() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String baseName = new File(originalPath != null ? originalPath : soPath).getName();
                String prefix = baseName.endsWith(".so") ? baseName.substring(0, baseName.length() - 3) : baseName;
                File outFile = new File(downloadDir, prefix + "_patched.so");
                int counter = 1;
                while (outFile.exists()) {
                    outFile = new File(downloadDir, prefix + "_patched_" + counter + ".so");
                    counter++;
                }

                FileUtils.copyFile(soFile, outFile);
                try (RandomAccessFile rafOut = new RandomAccessFile(outFile, "rw")) {
                    for (Map.Entry<Long, byte[]> entry : pendingPatches.entrySet()) {
                        rafOut.seek(entry.getKey());
                        rafOut.write(entry.getValue());
                    }
                }

                final File finalOut = outFile;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Patched Library Exported")
                            .setMessage("Successfully saved patched ELF to:\n" + finalOut.getAbsolutePath())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showDiscardConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have " + pendingPatches.size() + " unsaved patch(es). Exit without saving?")
                .setPositiveButton("Discard", (d, w) -> {
                    pendingPatches.clear();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Save First", (d, w) -> saveAllPatches(this::finish))
                .show();
    }

    private String decodeAsmString(long addr, byte[] bytes) {
        if (bytes == null || bytes.length < 4) return ".inst ??";
        int op = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8) | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
        try {
            AsmStatement stmt = decoder.decode(addr, op);
            return stmt.format();
        } catch (Throwable t) {
            return String.format(Locale.ROOT, ".inst 0x%08X", op);
        }
    }

    private byte[] parseHexInput(String hex) {
        if (hex == null) return null;
        String clean = hex.replaceAll("[^0-9a-fA-F]", "");
        if (clean.length() != 8) return null;
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private void copyToClipboard(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private View wrapDialogView(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(dp(20), dp(10), dp(20), dp(10));
        l.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // Functions Adapter
    // =========================================================================
    private class FunctionsAdapter extends RecyclerView.Adapter<FunctionsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(parent.getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            item.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(android.R.id.text1);
            tvName.setTextSize(14);
            tvName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvName.setSingleLine(true);
            tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            item.addView(tvName);

            TextView tvSubtitle = new TextView(parent.getContext());
            tvSubtitle.setId(android.R.id.text2);
            tvSubtitle.setTextSize(12);
            tvSubtitle.setTextColor(0xFF888888);
            tvSubtitle.setTypeface(Typeface.MONOSPACE);
            tvSubtitle.setSingleLine(true);
            tvSubtitle.setEllipsize(TextUtils.TruncateAt.END);
            tvSubtitle.setPadding(0, dp(2), 0, 0);
            item.addView(tvSubtitle);

            return new ViewHolder(item, tvName, tvSubtitle);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Arm64Function f = filteredFunctions.get(position);
            holder.tvName.setText(f.getDisplayName());
            holder.tvSubtitle.setText(f.getSubtitle());

            if (f.name.startsWith("Java_") || f.name.contains("JNI_")) {
                holder.tvName.setTextColor(0xFF4FC3F7); // JNI blue
            } else if (f.demangledName != null && !f.demangledName.equals(f.name)) {
                holder.tvName.setTextColor(0xFF80CBC4); // Demangled teal
            } else if (f.isExported) {
                holder.tvName.setTextColor(0xFF81C784); // Exported green
            } else {
                holder.tvName.setTextColor(MaterialColors.getColor(holder.tvName, android.R.attr.textColorPrimary, 0xFFFFFFFF));
            }

            holder.itemView.setOnClickListener(v -> disassembleFunction(f));
            holder.itemView.setOnLongClickListener(v -> {
                copyToClipboard(f.getDisplayName(), "Function Name");
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return filteredFunctions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvSubtitle;

            ViewHolder(View itemView, TextView tvName, TextView tvSubtitle) {
                super(itemView);
                this.tvName = tvName;
                this.tvSubtitle = tvSubtitle;
            }
        }
    }

    // =========================================================================
    // Instructions Adapter
    // =========================================================================
    private class InstructionsAdapter extends RecyclerView.Adapter<InstructionsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tvAddr = new TextView(parent.getContext());
            tvAddr.setTextSize(12);
            tvAddr.setTypeface(Typeface.MONOSPACE);
            tvAddr.setTextColor(0xFF80CBC4);
            tvAddr.setSingleLine(true);
            tvAddr.setLayoutParams(new LinearLayout.LayoutParams(dp(85), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tvAddr);

            TextView tvHex = new TextView(parent.getContext());
            tvHex.setTextSize(11);
            tvHex.setTypeface(Typeface.MONOSPACE);
            tvHex.setTextColor(0xFF888888);
            tvHex.setSingleLine(true);
            tvHex.setLayoutParams(new LinearLayout.LayoutParams(dp(95), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tvHex);

            TextView tvMnemonic = new TextView(parent.getContext());
            tvMnemonic.setTextSize(13);
            tvMnemonic.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvMnemonic.setSingleLine(true);
            tvMnemonic.setMinimumWidth(dp(65));
            tvMnemonic.setPadding(0, 0, dp(6), 0);
            tvMnemonic.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tvMnemonic);

            TextView tvAsm = new TextView(parent.getContext());
            tvAsm.setTextSize(13);
            tvAsm.setTypeface(Typeface.MONOSPACE);
            tvAsm.setSingleLine(true);
            tvAsm.setEllipsize(TextUtils.TruncateAt.END);
            tvAsm.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            row.addView(tvAsm);

            return new ViewHolder(row, tvAddr, tvHex, tvMnemonic, tvAsm);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Arm64Instruction inst = currentInstructions.get(position);
            holder.tvAddr.setText(String.format(Locale.ROOT, "%08X", inst.address));
            holder.tvHex.setText(inst.getHexBytes());
            holder.tvMnemonic.setText(inst.mnemonic);

            // Mnemonic coloring based on ARM64 instruction semantics
            String m = inst.mnemonic;
            if (m.startsWith("B") || m.startsWith("CB") || m.startsWith("TB")) {
                holder.tvMnemonic.setTextColor(0xFFFFA726); // Branch orange
            } else if (m.equals("RET")) {
                holder.tvMnemonic.setTextColor(0xFFEF5350); // Return red
            } else if (m.startsWith("LDR") || m.startsWith("STR") || m.startsWith("STP") || m.startsWith("LDP")) {
                holder.tvMnemonic.setTextColor(0xFF42A5F5); // Load/store blue
            } else if (m.equals("MOV") || m.equals("MVN") || m.equals("NOP")) {
                holder.tvMnemonic.setTextColor(0xFF66BB6A); // Mov/Nop green
            } else if (m.startsWith("ADD") || m.startsWith("SUB") || m.startsWith("CMP") || m.startsWith("AND") || m.startsWith("ORR")) {
                holder.tvMnemonic.setTextColor(0xFFBA68C8); // Arithmetic purple
            } else {
                holder.tvMnemonic.setTextColor(MaterialColors.getColor(holder.tvMnemonic, android.R.attr.textColorPrimary, 0xFFFFFFFF));
            }

            // Extract operands from full format (after mnemonic)
            String full = inst.fullAsm;
            int spaceIdx = full.indexOf(' ');
            String operands = (spaceIdx >= 0 && spaceIdx + 1 < full.length()) ? full.substring(spaceIdx + 1) : "";

            long branchTarget = extractBranchTarget(inst);
            if (branchTarget > 0 && symbolAddressMap.containsKey(branchTarget)) {
                String symName = symbolAddressMap.get(branchTarget);
                operands += "  <" + symName + ">";
            }
            holder.tvAsm.setText(operands);

            // Highlight modified instructions
            if (inst.isModified) {
                holder.itemView.setBackgroundColor(0x304CAF50); // Translucent green
                holder.tvHex.setTextColor(0xFF81C784);
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.tvHex.setTextColor(0xFF888888);
            }

            holder.itemView.setOnClickListener(v -> showPatchDialog(inst, position));
            holder.itemView.setOnLongClickListener(v -> {
                copyToClipboard(String.format(Locale.ROOT, "0x%08X: %s [%s]", inst.address, inst.fullAsm, inst.getHexBytes()), "Instruction");
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return currentInstructions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvAddr;
            final TextView tvHex;
            final TextView tvMnemonic;
            final TextView tvAsm;

            ViewHolder(View itemView, TextView tvAddr, TextView tvHex, TextView tvMnemonic, TextView tvAsm) {
                super(itemView);
                this.tvAddr = tvAddr;
                this.tvHex = tvHex;
                this.tvMnemonic = tvMnemonic;
                this.tvAsm = tvAsm;
            }
        }
    }

    private class StringsAdapter extends RecyclerView.Adapter<StringsAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(Arm64DisassemblerActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(8), dp(14), dp(8));

            LinearLayout top = new LinearLayout(Arm64DisassemblerActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTag = new TextView(Arm64DisassemblerActivity.this);
            tvTag.setText("str");
            tvTag.setTextSize(10);
            tvTag.setTypeface(Typeface.DEFAULT_BOLD);
            tvTag.setTextColor(0xFF22C55E);
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(0x2222C55E);
            tagBg.setCornerRadius(dp(4));
            tvTag.setBackground(tagBg);
            tvTag.setPadding(dp(6), dp(2), dp(6), dp(2));
            top.addView(tvTag);

            TextView tvStr = new TextView(Arm64DisassemblerActivity.this);
            tvStr.setTextSize(13);
            tvStr.setTypeface(Typeface.MONOSPACE);
            tvStr.setTextColor(MaterialColors.getColor(row, android.R.attr.textColorPrimary, 0xFFFFFFFF));
            tvStr.setSingleLine(true);
            tvStr.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams pStr = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            pStr.leftMargin = dp(8);
            tvStr.setLayoutParams(pStr);
            top.addView(tvStr);

            row.addView(top);

            TextView tvSub = new TextView(Arm64DisassemblerActivity.this);
            tvSub.setTextSize(11);
            tvSub.setTypeface(Typeface.MONOSPACE);
            tvSub.setTextColor(0xFF888888);
            tvSub.setPadding(dp(30), dp(2), 0, 0);
            row.addView(tvSub);

            return new ViewHolder(row, tvStr, tvSub);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ElfParser.ElfString item;
            synchronized (filteredStrings) {
                if (position < 0 || position >= filteredStrings.size()) return;
                item = filteredStrings.get(position);
            }
            holder.tvStr.setText(item.value);
            holder.tvSub.setText(String.format("0x%X  ·  Offset: 0x%X  ·  UTF-8  ·  %dB", item.vaddr, item.fileOffset, item.length));

            holder.itemView.setOnClickListener(v -> showStringOptionsDialog(item));
            holder.itemView.setOnLongClickListener(v -> {
                copyToClipboard(item.value, "String");
                return true;
            });
        }

        @Override
        public int getItemCount() {
            synchronized (filteredStrings) {
                return filteredStrings.size();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvStr;
            final TextView tvSub;
            ViewHolder(View itemView, TextView tvStr, TextView tvStrSub) {
                super(itemView);
                this.tvStr = tvStr;
                this.tvSub = tvStrSub;
            }
        }
    }

    private class HexAdapter extends RecyclerView.Adapter<HexAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(Arm64DisassemblerActivity.this);
            tv.setTextSize(12);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(dp(8), dp(3), dp(8), dp(3));
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            long rowOffset = (long) position * hexBytesPerRow;
            int countToRead = (int) Math.min(hexBytesPerRow, hexFileSize - rowOffset);
            if (countToRead <= 0) {
                holder.textView.setText("");
                return;
            }

            byte[] rowData = new byte[countToRead];
            synchronized (Arm64DisassemblerActivity.this) {
                if (hexRaf != null) {
                    try {
                        hexRaf.seek(rowOffset);
                        hexRaf.readFully(rowData);
                    } catch (Exception ignored) {}
                }
            }

            boolean hasPatch = false;
            for (int i = 0; i < countToRead; i++) {
                long bOff = rowOffset + i;
                if (pendingPatches.containsKey(bOff)) {
                    hasPatch = true;
                    byte[] patch = pendingPatches.get(bOff);
                    if (patch != null && patch.length > 0) {
                        rowData[i] = patch[0];
                    }
                }
            }

            StringBuilder sb = new StringBuilder(128);
            sb.append(String.format(Locale.US, "%08X: ", rowOffset));

            // Hex columns
            for (int i = 0; i < hexBytesPerRow; i++) {
                if (i < countToRead) {
                    sb.append(String.format("%02X ", rowData[i]));
                } else {
                    sb.append("   ");
                }
                if (i == 7 || i == 15 || i == 23) sb.append(" ");
            }

            // ASCII column
            sb.append(" |");
            for (int i = 0; i < countToRead; i++) {
                byte b = rowData[i];
                if (b >= 0x20 && b <= 0x7E) {
                    sb.append((char) b);
                } else {
                    sb.append('.');
                }
            }
            sb.append("|");

            holder.textView.setText(sb.toString());
            if (hasPatch) {
                holder.textView.setBackgroundColor(0x3022C55E);
                holder.textView.setTextColor(0xFF81C784);
            } else {
                holder.textView.setBackgroundColor(Color.TRANSPARENT);
                holder.textView.setTextColor(MaterialColors.getColor(holder.textView, android.R.attr.textColorPrimary, 0xFFE0E0E0));
            }

            holder.itemView.setOnClickListener(v -> {
                if (isPatchMode) {
                    showHexByteEditDialog(rowOffset, countToRead, rowData);
                } else {
                    showHexRowActionDialog(rowOffset, countToRead, rowData);
                }
            });
        }

        @Override
        public int getItemCount() {
            if (hexFileSize <= 0) return 0;
            return (int) Math.min(Integer.MAX_VALUE, (hexFileSize + hexBytesPerRow - 1) / hexBytesPerRow);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;
            ViewHolder(TextView itemView) {
                super(itemView);
                this.textView = itemView;
            }
        }
    }

    private class ElfTreeAdapter extends RecyclerView.Adapter<ElfTreeAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(Arm64DisassemblerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));

            TextView tvTag = new TextView(Arm64DisassemblerActivity.this);
            tvTag.setTextSize(10);
            tvTag.setTypeface(Typeface.DEFAULT_BOLD);
            tvTag.setPadding(dp(6), dp(2), dp(6), dp(2));
            row.addView(tvTag);

            LinearLayout center = new LinearLayout(Arm64DisassemblerActivity.this);
            center.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams pCenter = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            pCenter.leftMargin = dp(10);
            center.setLayoutParams(pCenter);

            TextView tvTitle = new TextView(Arm64DisassemblerActivity.this);
            tvTitle.setTextSize(13);
            tvTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvTitle.setTextColor(MaterialColors.getColor(row, android.R.attr.textColorPrimary, 0xFFFFFFFF));
            center.addView(tvTitle);

            TextView tvSub = new TextView(Arm64DisassemblerActivity.this);
            tvSub.setTextSize(11);
            tvSub.setTypeface(Typeface.MONOSPACE);
            tvSub.setTextColor(0xFF888888);
            center.addView(tvSub);

            row.addView(center);

            TextView tvPerm = new TextView(Arm64DisassemblerActivity.this);
            tvPerm.setTextSize(11);
            tvPerm.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvPerm.setPadding(dp(6), dp(2), dp(6), dp(2));
            row.addView(tvPerm);

            return new ViewHolder(row, tvTag, tvTitle, tvSub, tvPerm);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ElfTreeItem item;
            synchronized (filteredTreeItems) {
                if (position < 0 || position >= filteredTreeItems.size()) return;
                item = filteredTreeItems.get(position);
            }

            if (item.itemType == ElfTreeItem.ItemType.HEADER) {
                holder.itemView.setBackgroundColor(0x18888888);
                holder.tvTag.setVisibility(View.GONE);
                holder.tvPerm.setVisibility(View.GONE);
                holder.tvTitle.setText(item.title);
                holder.tvTitle.setTextColor(0xFF22C55E);
                holder.tvSub.setText(item.subtitle);
                holder.itemView.setClickable(false);
                return;
            }

            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTitle.setText(item.title);
            holder.tvTitle.setTextColor(MaterialColors.getColor(holder.tvTitle, android.R.attr.textColorPrimary, 0xFFFFFFFF));
            holder.tvSub.setText(item.subtitle);

            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setCornerRadius(dp(4));

            if (item.itemType == ElfTreeItem.ItemType.SEGMENT) {
                holder.tvTag.setText("S");
                holder.tvTag.setTextColor(0xFFA855F7);
                tagBg.setColor(0x22A855F7);
            } else if (item.itemType == ElfTreeItem.ItemType.SECTION) {
                if (item.title.contains("text") || item.title.contains("plt")) {
                    holder.tvTag.setText("C");
                    holder.tvTag.setTextColor(0xFF22C55E);
                    tagBg.setColor(0x2222C55E);
                } else if (item.title.contains("rodata")) {
                    holder.tvTag.setText("R");
                    holder.tvTag.setTextColor(0xFF38BDF8);
                    tagBg.setColor(0x2238BDF8);
                } else {
                    holder.tvTag.setText("D");
                    holder.tvTag.setTextColor(0xFFFB923C);
                    tagBg.setColor(0x22FB923C);
                }
            } else {
                holder.tvTag.setText("f");
                holder.tvTag.setTextColor(0xFFFBBF24);
                tagBg.setColor(0x22FBBF24);
            }
            holder.tvTag.setBackground(tagBg);

            if (item.permissions != null && !item.permissions.isEmpty()) {
                holder.tvPerm.setVisibility(View.VISIBLE);
                holder.tvPerm.setText(item.permissions);
                if (item.permissions.contains("X")) {
                    holder.tvPerm.setTextColor(0xFF22C55E);
                } else if (item.permissions.contains("W")) {
                    holder.tvPerm.setTextColor(0xFFFB923C);
                } else {
                    holder.tvPerm.setTextColor(0xFF888888);
                }
            } else {
                holder.tvPerm.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> onTreeItemClicked(item));
        }

        @Override
        public int getItemCount() {
            synchronized (filteredTreeItems) {
                return filteredTreeItems.size();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvTag, tvTitle, tvSub, tvPerm;
            ViewHolder(View itemView, TextView tvTag, TextView tvTitle, TextView tvSub, TextView tvPerm) {
                super(itemView);
                this.tvTag = tvTag;
                this.tvTitle = tvTitle;
                this.tvSub = tvSub;
                this.tvPerm = tvPerm;
            }
        }
    }

    private TextView createBadgeChip(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(color);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((color & 0x00FFFFFF) | 0x22000000);
        bg.setCornerRadius(dp(12));
        tv.setBackground(bg);
        return tv;
    }

    private void onTreeItemClicked(ElfTreeItem item) {
        if (item.address >= 0) {
            if (item.permissions != null && item.permissions.contains("X")) {
                tabLayout.getTabAt(0).select();
                navigateToAddress(item.address);
            } else if (item.fileOffset >= 0) {
                tabLayout.getTabAt(2).select();
                jumpToHexOffset(item.fileOffset);
            }
        } else if (item.fileOffset >= 0) {
            tabLayout.getTabAt(2).select();
            jumpToHexOffset(item.fileOffset);
        }
    }

    private void showStringOptionsDialog(ElfParser.ElfString str) {
        List<String> options = new ArrayList<>();
        options.add("Jump to HexView (Offset: 0x" + Long.toHexString(str.fileOffset) + ")");
        if (str.vaddr >= textSecAddr && str.vaddr < textSecAddr + textSecSize) {
            options.add("Jump to Disassembly (Addr: 0x" + Long.toHexString(str.vaddr) + ")");
        }
        options.add("Send to Crypto Tool (Decrypt)");
        options.add("Copy String");
        options.add("Copy Virtual Address");
        options.add("Copy File Offset");

        new MaterialAlertDialogBuilder(this)
                .setTitle(str.value)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    String selected = options.get(which);
                    if (selected.startsWith("Jump to HexView")) {
                        tabLayout.getTabAt(2).select();
                        jumpToHexOffset(str.fileOffset);
                    } else if (selected.startsWith("Jump to Disassembly")) {
                        tabLayout.getTabAt(0).select();
                        navigateToAddress(str.vaddr);
                    } else if (selected.equals("Send to Crypto Tool (Decrypt)")) {
                        showCryptoDialog(str.value, true);
                    } else if (selected.equals("Copy String")) {
                        copyToClipboard(str.value, "String");
                    } else if (selected.equals("Copy Virtual Address")) {
                        copyToClipboard(String.format("0x%X", str.vaddr), "Virtual Address");
                    } else if (selected.equals("Copy File Offset")) {
                        copyToClipboard(String.format("0x%X", str.fileOffset), "File Offset");
                    }
                })
                .show();
    }

    private void jumpToHexOffset(long offset) {
        if (rvHex == null || hexBytesPerRow <= 0) return;
        int targetRow = (int) (offset / hexBytesPerRow);
        LinearLayoutManager lm = (LinearLayoutManager) rvHex.getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(targetRow, dp(40));
        }
    }

    private void showHexRowActionDialog(long rowOffset, int count, byte[] rowData) {
        String[] options = {
                "Edit Byte at Offset...",
                "Copy Row Hex Bytes",
                "Copy Row ASCII Text",
                "Copy Offset (0x" + Long.toHexString(rowOffset) + ")"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(String.format("Offset 0x%08X", rowOffset))
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            showHexByteEditDialog(rowOffset, count, rowData);
                            break;
                        case 1:
                            StringBuilder hexSb = new StringBuilder();
                            for (int i = 0; i < count; i++) {
                                hexSb.append(String.format("%02X ", rowData[i]));
                            }
                            copyToClipboard(hexSb.toString().trim(), "Row Hex");
                            break;
                        case 2:
                            StringBuilder ascSb = new StringBuilder();
                            for (int i = 0; i < count; i++) {
                                byte b = rowData[i];
                                ascSb.append(b >= 0x20 && b <= 0x7E ? (char) b : '.');
                            }
                            copyToClipboard(ascSb.toString(), "Row ASCII");
                            break;
                        case 3:
                            copyToClipboard(String.format("0x%X", rowOffset), "Offset");
                            break;
                    }
                })
                .show();
    }

    private void showHexByteEditDialog(long rowOffset, int count, byte[] rowData) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("Select Byte Column (0 to " + (count - 1) + "):");
        tvPrompt.setTextColor(0xFF888888);
        l.addView(tvPrompt);

        String[] byteOptions = new String[count];
        for (int i = 0; i < count; i++) {
            byteOptions[i] = String.format("+%02X (0x%02X: '%c')", i, rowData[i],
                    (rowData[i] >= 0x20 && rowData[i] <= 0x7E) ? (char) rowData[i] : '.');
        }
        Spinner spByteCol = new Spinner(this);
        spByteCol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, byteOptions));
        l.addView(spByteCol);

        TextView tvNewHex = new TextView(this);
        tvNewHex.setText("New Hex Value (e.g. 00, 90, FF):");
        tvNewHex.setTextColor(0xFF22C55E);
        tvNewHex.setPadding(0, dp(10), 0, dp(2));
        l.addView(tvNewHex);

        EditText etNewHex = new EditText(this);
        etNewHex.setHint("Hex byte (2 chars)");
        etNewHex.setTextSize(14);
        etNewHex.setTypeface(Typeface.MONOSPACE);
        etNewHex.setSingleLine(true);
        etNewHex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        l.addView(etNewHex);

        new MaterialAlertDialogBuilder(this)
                .setTitle(String.format("Patch Byte at 0x%08X", rowOffset))
                .setView(wrapDialogView(l))
                .setPositiveButton("Apply Patch", (d, w) -> {
                    int col = spByteCol.getSelectedItemPosition();
                    String hex = etNewHex.getText().toString().trim();
                    if (hex.length() == 1) hex = "0" + hex;
                    if (hex.length() == 2) {
                        try {
                            byte b = (byte) Integer.parseInt(hex, 16);
                            long targetOffset = rowOffset + col;
                            pendingPatches.put(targetOffset, new byte[]{b});
                            updateSaveButtonUi();
                            if (hexAdapter != null) hexAdapter.notifyItemChanged((int) (targetOffset / hexBytesPerRow));
                            Toast.makeText(this, "Patched byte at 0x" + Long.toHexString(targetOffset), Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            Toast.makeText(this, "Invalid hex byte: " + hex, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void undoLastPatch() {
        if (pendingPatches.isEmpty()) {
            Toast.makeText(this, "No patches to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        Long lastKey = null;
        for (Long k : pendingPatches.keySet()) {
            lastKey = k;
        }
        if (lastKey != null) {
            pendingPatches.remove(lastKey);
            updateSaveButtonUi();
            if (hexAdapter != null) hexAdapter.notifyDataSetChanged();
            if (instructionsAdapter != null) instructionsAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Reverted patch at 0x" + Long.toHexString(lastKey), Toast.LENGTH_SHORT).show();
        }
    }

    private void showHexGoToDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g. 0x1000 or 1000");
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(true);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Go to File Offset")
                .setView(wrapDialogView(input))
                .setPositiveButton("Go", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
                    try {
                        long offset = Long.parseLong(text, 16);
                        jumpToHexOffset(offset);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid offset: " + text, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performHexSearch(String query) {
        if (TextUtils.isEmpty(query)) return;
        new Thread(() -> {
            try {
                byte[] targetBytes;
                String q = query.trim();
                if (q.matches("^[0-9a-fA-F\\s]+$") && q.replace(" ", "").length() % 2 == 0) {
                    targetBytes = CryptoHelper.hexToBytes(q.replace(" ", ""));
                } else {
                    targetBytes = q.getBytes(StandardCharsets.UTF_8);
                }
                if (targetBytes.length == 0) return;

                long matchOffset = -1;
                synchronized (Arm64DisassemblerActivity.this) {
                    if (hexRaf != null) {
                        byte[] buf = new byte[65536];
                        long pos = 0;
                        while (pos < hexFileSize) {
                            hexRaf.seek(pos);
                            int read = hexRaf.read(buf);
                            if (read <= 0) break;
                            for (int i = 0; i <= read - targetBytes.length; i++) {
                                boolean match = true;
                                for (int j = 0; j < targetBytes.length; j++) {
                                    if (buf[i + j] != targetBytes[j]) {
                                        match = false;
                                        break;
                                    }
                                }
                                if (match) {
                                    matchOffset = pos + i;
                                    break;
                                }
                            }
                            if (matchOffset >= 0) break;
                            pos += (read - targetBytes.length + 1);
                        }
                    }
                }

                final long found = matchOffset;
                runOnUiThread(() -> {
                    if (found >= 0) {
                        jumpToHexOffset(found);
                        Toast.makeText(this, "Match found at 0x" + Long.toHexString(found), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No match found for: " + query, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void filterStringsAsync(String query) {
        new Thread(() -> {
            List<ElfParser.ElfString> result = new ArrayList<>();
            String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
            synchronized (allStrings) {
                if (TextUtils.isEmpty(q)) {
                    result.addAll(allStrings);
                } else {
                    for (ElfParser.ElfString s : allStrings) {
                        if (s.value.toLowerCase(Locale.ROOT).contains(q)
                                || Long.toHexString(s.vaddr).contains(q)
                                || Long.toHexString(s.fileOffset).contains(q)) {
                            result.add(s);
                        }
                    }
                }
            }
            runOnUiThread(() -> {
                synchronized (filteredStrings) {
                    filteredStrings.clear();
                    filteredStrings.addAll(result);
                }
                if (tvStringsCount != null) {
                    tvStringsCount.setText(String.valueOf(result.size()));
                }
                if (stringsAdapter != null) {
                    stringsAdapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    private void filterTreeAsync(String query) {
        new Thread(() -> {
            List<ElfTreeItem> result = new ArrayList<>();
            String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
            synchronized (allTreeItems) {
                if (TextUtils.isEmpty(q)) {
                    result.addAll(allTreeItems);
                } else {
                    for (ElfTreeItem item : allTreeItems) {
                        if (item.itemType == ElfTreeItem.ItemType.HEADER) {
                            result.add(item);
                        } else if (item.title.toLowerCase(Locale.ROOT).contains(q)
                                || (item.subtitle != null && item.subtitle.toLowerCase(Locale.ROOT).contains(q))) {
                            result.add(item);
                        }
                    }
                }
            }
            runOnUiThread(() -> {
                synchronized (filteredTreeItems) {
                    filteredTreeItems.clear();
                    filteredTreeItems.addAll(result);
                }
                if (elfTreeAdapter != null) {
                    elfTreeAdapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    private void showFileInfoDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(14), dp(20), dp(14));

        addInfoRow(layout, "Lib Name", new File(originalPath).getName());
        addInfoRow(layout, "Size", String.format(Locale.US, "%s (%d bytes)", ElfTreeItem.formatSize(soFile.length()), soFile.length()));
        addInfoRow(layout, "Architecture", "ARM64 (AArch64) - Little Endian");
        addInfoRow(layout, "Entry Point", String.format("0x%X", elfEntryPoint));
        addInfoRow(layout, "Base Address", String.format("0x%X", textSecAddr));
        addInfoRow(layout, "End Address", String.format("0x%X", textSecAddr + textSecSize));
        addInfoRow(layout, "Export Hash", elfExportHash);
        addInfoRow(layout, "Build ID", elfBuildId);

        TextView tvHashHeader = new TextView(this);
        tvHashHeader.setText("Hashes");
        tvHashHeader.setTextSize(14);
        tvHashHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHashHeader.setTextColor(0xFF22C55E);
        tvHashHeader.setPadding(0, dp(10), 0, dp(4));
        layout.addView(tvHashHeader);

        addInfoRow(layout, "md5", hashMd5.isEmpty() ? "Calculating..." : hashMd5);
        addInfoRow(layout, "sha1", hashSha1.isEmpty() ? "Calculating..." : hashSha1);
        addInfoRow(layout, "sha256", hashSha256.isEmpty() ? "Calculating..." : hashSha256);

        ScrollView sv = new ScrollView(this);
        sv.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("File Information")
                .setView(sv)
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy All", (d, w) -> {
                    String allInfo = "Lib Name: " + new File(originalPath).getName() + "\n"
                            + "Size: " + soFile.length() + " bytes\n"
                            + "Architecture: ARM64\n"
                            + "Entry Point: " + String.format("0x%X", elfEntryPoint) + "\n"
                            + "Base Address: " + String.format("0x%X", textSecAddr) + "\n"
                            + "End Address: " + String.format("0x%X", textSecAddr + textSecSize) + "\n"
                            + "Export Hash: " + elfExportHash + "\n"
                            + "Build ID: " + elfBuildId + "\n"
                            + "md5: " + hashMd5 + "\n"
                            + "sha1: " + hashSha1 + "\n"
                            + "sha256: " + hashSha256;
                    copyToClipboard(allInfo, "ELF Info");
                })
                .show();
    }

    private void showCryptoDialog() {
        showCryptoDialog("", true);
    }

    private void showCryptoDialog(String initialData, boolean defaultDecrypt) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(14), dp(18), dp(14));

        final boolean[] isDecryptMode = {defaultDecrypt};

        // Header Title
        TextView tvDialogTitle = new TextView(this);
        tvDialogTitle.setText(defaultDecrypt ? "Text Decryption" : "Text Encryption");
        tvDialogTitle.setTextSize(18);
        tvDialogTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvDialogTitle.setTextColor(0xFFFFFFFF);
        tvDialogTitle.setPadding(0, 0, 0, dp(12));
        layout.addView(tvDialogTitle);

        // Mode Bar (Pill toggle: ENCRYPT | DECRYPT)
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(0, 0, 0, dp(12));

        MaterialButton btnEncrypt = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnEncrypt.setText("ENCRYPT");
        MaterialButton btnDecrypt = new MaterialButton(this);
        btnDecrypt.setText("DECRYPT");

        Runnable updateModeColors = () -> {
            if (isDecryptMode[0]) {
                btnDecrypt.setBackgroundColor(0xFF22C55E);
                btnDecrypt.setTextColor(0xFFFFFFFF);
                btnEncrypt.setBackgroundColor(0x22888888);
                btnEncrypt.setTextColor(0xFFAAAAAA);
                tvDialogTitle.setText("Text Decryption");
            } else {
                btnEncrypt.setBackgroundColor(0xFF22C55E);
                btnEncrypt.setTextColor(0xFFFFFFFF);
                btnDecrypt.setBackgroundColor(0x22888888);
                btnDecrypt.setTextColor(0xFFAAAAAA);
                tvDialogTitle.setText("Text Encryption");
            }
        };

        modeBar.addView(btnEncrypt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        modeBar.addView(btnDecrypt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        layout.addView(modeBar);

        // Algorithm
        layout.addView(createFieldLabel("Algorithm"));

        String[] algorithms = {
                "XOR", "RC4", "AES (ECB)", "AES (CBC)",
                "DES (ECB)", "DES (CBC)", "3DES (Triple DES)",
                "Base64", "Hex", "ROT13"
        };
        Spinner spAlgo = new Spinner(this);
        ArrayAdapter<String> algoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, algorithms);
        spAlgo.setAdapter(algoAdapter);
        layout.addView(spAlgo);

        // Input Encode
        layout.addView(createFieldLabel("Input Encode"));

        String[] inputEncodings = {"Base64", "Text", "Hex"};
        Spinner spInputEncode = new Spinner(this);
        ArrayAdapter<String> inEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, inputEncodings);
        spInputEncode.setAdapter(inEncAdapter);
        spInputEncode.setSelection(defaultDecrypt ? 0 : 1);
        layout.addView(spInputEncode);

        // Output Encode
        layout.addView(createFieldLabel("Output Encode"));

        String[] outputEncodings = {"Text", "Base64", "Hex"};
        Spinner spOutputEncode = new Spinner(this);
        ArrayAdapter<String> outEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, outputEncodings);
        spOutputEncode.setAdapter(outEncAdapter);
        spOutputEncode.setSelection(defaultDecrypt ? 0 : 1);
        layout.addView(spOutputEncode);

        // Key section
        LinearLayout llKey = new LinearLayout(this);
        llKey.setOrientation(LinearLayout.VERTICAL);

        llKey.addView(createFieldLabel("Key"));

        EditText etKey = new EditText(this);
        etKey.setHint(defaultDecrypt ? "Input the decrypt key" : "Input the encrypt key");
        etKey.setTextSize(13);
        etKey.setSingleLine(true);
        llKey.addView(etKey);

        layout.addView(llKey);

        // Key / IV Encode
        LinearLayout llKeyEncode = new LinearLayout(this);
        llKeyEncode.setOrientation(LinearLayout.VERTICAL);

        llKeyEncode.addView(createFieldLabel("Key / IV Encode"));

        String[] keyEncodings = {"Text", "Base64", "Hex"};
        Spinner spKeyEncode = new Spinner(this);
        ArrayAdapter<String> keyEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, keyEncodings);
        spKeyEncode.setAdapter(keyEncAdapter);
        llKeyEncode.addView(spKeyEncode);

        layout.addView(llKeyEncode);

        // IV section (for CBC modes)
        LinearLayout llIv = new LinearLayout(this);
        llIv.setOrientation(LinearLayout.VERTICAL);
        llIv.setVisibility(View.GONE);

        llIv.addView(createFieldLabel("IV (16 bytes for AES, 8 bytes for DES)"));

        EditText etIv = new EditText(this);
        etIv.setHint("Input IV...");
        etIv.setTextSize(13);
        etIv.setSingleLine(true);
        llIv.addView(etIv);

        layout.addView(llIv);

        // Charset
        layout.addView(createFieldLabel("Charset"));

        String[] charsets = {"UTF-8", "US-ASCII", "ISO-8859-1", "UTF-16", "GBK"};
        Spinner spCharset = new Spinner(this);
        ArrayAdapter<String> charsetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, charsets);
        spCharset.setAdapter(charsetAdapter);
        layout.addView(spCharset);

        // Data
        layout.addView(createFieldLabel("Data"));

        EditText etData = new EditText(this);
        etData.setHint("Input the data");
        etData.setTextSize(13);
        etData.setMinLines(3);
        etData.setMaxLines(6);
        etData.setGravity(Gravity.TOP | Gravity.START);
        if (initialData != null && !initialData.isEmpty()) {
            etData.setText(initialData);
        }
        layout.addView(etData);

        // Output / Result Header
        LinearLayout resultHeader = new LinearLayout(this);
        resultHeader.setOrientation(LinearLayout.HORIZONTAL);
        resultHeader.setGravity(Gravity.CENTER_VERTICAL);
        resultHeader.setPadding(0, dp(10), 0, dp(2));

        TextView tvResultLabel = createFieldLabel("Output");
        resultHeader.addView(tvResultLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        MaterialButton btnCopy = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCopy.setText("Copy");
        btnCopy.setTextSize(11);
        resultHeader.addView(btnCopy);

        MaterialButton btnClear = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnClear.setText("Clear");
        btnClear.setTextSize(11);
        LinearLayout.LayoutParams pClear = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pClear.leftMargin = dp(6);
        btnClear.setLayoutParams(pClear);
        resultHeader.addView(btnClear);

        layout.addView(resultHeader);

        // Output text
        EditText etResult = new EditText(this);
        etResult.setTextSize(13);
        etResult.setTypeface(Typeface.MONOSPACE);
        etResult.setMinLines(3);
        etResult.setMaxLines(8);
        etResult.setGravity(Gravity.TOP | Gravity.START);
        etResult.setHint("Result will appear here...");
        etResult.setBackgroundColor(0x18FFFFFF);
        etResult.setPadding(dp(10), dp(8), dp(10), dp(8));
        etResult.setFocusable(false);
        etResult.setClickable(true);
        layout.addView(etResult);

        // Dynamic algorithm switching
        spAlgo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String algo = algorithms[position];
                boolean isCbc = algo.contains("CBC");
                llIv.setVisibility(isCbc ? View.VISIBLE : View.GONE);

                boolean noKey = algo.equals("Base64") || algo.equals("Hex") || algo.equals("ROT13");
                llKey.setVisibility(noKey ? View.GONE : View.VISIBLE);
                llKeyEncode.setVisibility(noKey ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Mode switch listeners
        btnEncrypt.setOnClickListener(v -> {
            isDecryptMode[0] = false;
            updateModeColors.run();
            etKey.setHint("Input the encrypt key");
            spInputEncode.setSelection(1); // Text
            spOutputEncode.setSelection(0); // Text
        });
        btnDecrypt.setOnClickListener(v -> {
            isDecryptMode[0] = true;
            updateModeColors.run();
            etKey.setHint("Input the decrypt key");
            spInputEncode.setSelection(0); // Base64
            spOutputEncode.setSelection(0); // Text
        });
        updateModeColors.run();

        // Copy / Clear buttons
        btnCopy.setOnClickListener(v -> {
            String out = etResult.getText() != null ? etResult.getText().toString() : "";
            if (!out.isEmpty()) copyToClipboard(out, "Crypto Output");
        });
        btnClear.setOnClickListener(v -> {
            etData.setText("");
            etResult.setText("");
        });

        // RUN Button
        MaterialButton btnRun = new MaterialButton(this);
        btnRun.setText("▶  RUN");
        btnRun.setTextSize(14);
        btnRun.setTypeface(Typeface.DEFAULT_BOLD);
        btnRun.setBackgroundColor(0xFF22C55E);
        btnRun.setTextColor(0xFFFFFFFF);
        btnRun.setCornerRadius(dp(8));
        LinearLayout.LayoutParams pRun = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        pRun.topMargin = dp(14);
        pRun.bottomMargin = dp(8);
        btnRun.setLayoutParams(pRun);
        btnRun.setOnClickListener(v -> {
            String algo = spAlgo.getSelectedItem().toString();
            String inEnc = spInputEncode.getSelectedItem().toString();
            String outEnc = spOutputEncode.getSelectedItem().toString();
            String k = etKey.getText() != null ? etKey.getText().toString() : "";
            String kEnc = spKeyEncode.getSelectedItem().toString();
            String iv = etIv.getText() != null ? etIv.getText().toString() : "";
            String cs = spCharset.getSelectedItem().toString();
            String data = etData.getText() != null ? etData.getText().toString() : "";

            String res = CryptoHelper.process(algo, isDecryptMode[0], data, inEnc, outEnc, k, kEnc, iv, kEnc, cs);
            etResult.setText(res);
        });
        layout.addView(btnRun);

        ScrollView sv = new ScrollView(this);
        sv.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private TextView createFieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF22C55E);
        tv.setPadding(0, dp(6), 0, dp(2));
        return tv;
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tvLbl = new TextView(this);
        tvLbl.setText(label + ":");
        tvLbl.setTextSize(12);
        tvLbl.setTypeface(Typeface.DEFAULT_BOLD);
        tvLbl.setTextColor(0xFF888888);
        tvLbl.setLayoutParams(new LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(tvLbl);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextSize(12);
        tvVal.setTypeface(Typeface.MONOSPACE);
        tvVal.setTextColor(MaterialColors.getColor(row, android.R.attr.textColorPrimary, 0xFFFFFFFF));
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(tvVal);

        parent.addView(row);
    }
}
