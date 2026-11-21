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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
                if (isDisassemblyMode) {
                    if (!popNavigationHistory()) {
                        showFunctionsViewUi();
                    }
                } else if (!pendingPatches.isEmpty()) {
                    showDiscardConfirmDialog();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
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
            if (isDisassemblyMode) {
                if (!popNavigationHistory()) {
                    showFunctionsViewUi();
                }
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

        btnGoTo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGoTo.setText("Go To");
        btnGoTo.setOnClickListener(v -> showGoToDialog());
        actionLayout.addView(btnGoTo);

        btnReview = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnReview.setText("Diff");
        btnReview.setVisibility(View.GONE);
        btnReview.setOnClickListener(v -> showReviewPatchesDialog());
        LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reviewParams.leftMargin = dp(8);
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
        saveParams.leftMargin = dp(8);
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

        // Main Frame containing both views
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

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    String info = String.format(Locale.ROOT, "ELF64 AArch64 | %d functions | .text: 0x%X - 0x%X",
                            allFunctions.size(), textSecAddr, textSecAddr + textSecSize);
                    tvStatusInfo.setText(info);
                    functionsAdapter.notifyDataSetChanged();
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
        functionsContainer.setVisibility(View.GONE);
        disassemblyContainer.setVisibility(View.VISIBLE);
        updateDisassemblyHeader();
        updateLoadMoreButton();
        instructionsAdapter.notifyDataSetChanged();
    }

    private void showFunctionsViewUi() {
        isDisassemblyMode = false;
        navHistory.clear();
        disassemblyContainer.setVisibility(View.GONE);
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
}
