package io.github.abdurazaaqmohammed.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

/**
 * Simple hex editor: paged 8-bytes-per-row hex/ASCII viewer backed by a RandomAccessFile,
 * with an overlay of unsaved byte modifications, a 0-F keypad for editing the selected
 * byte nibble-by-nibble, undo/redo, goto-offset and save-to-file.
 */
public class HexEditorActivity extends AppCompatActivity {

    private File file;
    private RandomAccessFile raf;
    private long size;
    private boolean readOnly;

    /** Unsaved modifications: position -> new unsigned byte value. */
    private final TreeMap<Integer, Integer> mods = new TreeMap<>();
    /** Undo entries: {position, originalValue, newValue}. */
    private final ArrayDeque<int[]> undoStack = new ArrayDeque<>();
    private final ArrayDeque<int[]> redoStack = new ArrayDeque<>();

    private int cursorPos;
    private boolean nibbleHigh = true;
    private HexRowAdapter adapter;
    private TextView statusText;
    private LinearLayoutManager layoutManager;

    // Search panel state
    private View searchPanel, inspectorPanel;
    private EditText searchValue, replaceValue;
    private AutoCompleteTextView searchType, replaceType;
    private CheckBox searchBigEndian, replaceBigEndian, inspectorBigEndian;
    private int searchTypeIndex, replaceTypeIndex;
    private byte[] lastPattern;
    private long lastMatchPos = -1;
    private TextView[] inspectorValues;
    private TextView inspectorRawHex;

    /** Search data types; index matters for encoding. */
    private static final List<String> DATA_TYPES = Arrays.asList(
            "Hex Text", "ASCII String", "Unicode String", "UTF-8 String", "GBK String",
            "Signed Byte", "Unsigned Byte", "Signed Short", "Unsigned Short",
            "Signed Int", "Unsigned Int", "Signed Long", "Unsigned Long", "Float", "Double");

    private static final int TYPE_HEX_TEXT = 0, TYPE_ASCII = 1, TYPE_UNICODE = 2, TYPE_UTF8 = 3, TYPE_GBK = 4,
            TYPE_SBYTE = 5, TYPE_UBYTE = 6, TYPE_SSHORT = 7, TYPE_USHORT = 8,
            TYPE_SINT = 9, TYPE_UINT = 10, TYPE_SLONG = 11, TYPE_ULONG = 12, TYPE_FLOAT = 13, TYPE_DOUBLE = 14;

    private static boolean endianApplies(int type) {
        return switch (type) {
            case TYPE_UNICODE, TYPE_SSHORT, TYPE_USHORT, TYPE_SINT, TYPE_UINT,
                 TYPE_SLONG, TYPE_ULONG, TYPE_FLOAT, TYPE_DOUBLE -> true;
            default -> false;
        };
    }

    private static int typeSize(int type) {
        return switch (type) {
            case TYPE_SSHORT, TYPE_USHORT, TYPE_UNICODE -> 2;
            case TYPE_SINT, TYPE_UINT, TYPE_FLOAT -> 4;
            case TYPE_SLONG, TYPE_ULONG, TYPE_DOUBLE -> 8;
            default -> 1;
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences themeSettings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(themeSettings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_editor);

        String path = getIntent().getStringExtra("path");
        file = path != null ? new File(path) : null;
        if (file == null || !file.isFile()) {
            Extensions.showMessage(this, "File not found");
            finish();
            return;
        }
        readOnly = !file.canWrite();

        try {
            raf = new RandomAccessFile(file, "r");
            size = raf.length();
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.hexToolbar);
        toolbar.setSubtitle(file.getName());
        toolbar.setNavigationIcon(R.drawable.chevron_left_24px);
        toolbar.setNavigationOnClickListener(v -> confirmDiscardAndFinish());
        setSupportActionBar(toolbar);

        setupHeader();

        statusText = findViewById(R.id.statusText);
        FastScrollerRecyclerView recycler = findViewById(R.id.hexRecycler);
        layoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(layoutManager);
        adapter = new HexRowAdapter();
        recycler.setAdapter(adapter);

        setupKeypad();

        View keypad = findViewById(R.id.hexKeypad);
        keypad.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnToggleKeypad).setOnClickListener(v ->
                keypad.setVisibility(keypad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        searchPanel = findViewById(R.id.searchPanel);
        inspectorPanel = findViewById(R.id.inspectorPanel);
        setupSearch();
        setupInspector();

        updateStatus();
    }

    /** Left sidebar: value + data type + big endian + expandable replace section, with next/prev/replace actions. */
    private void setupSearch() {
        searchValue = searchPanel.findViewById(R.id.searchValue);
        searchType = searchPanel.findViewById(R.id.searchType);
        searchBigEndian = searchPanel.findViewById(R.id.searchBigEndian);
        replaceValue = searchPanel.findViewById(R.id.replaceValue);
        replaceType = searchPanel.findViewById(R.id.replaceType);
        replaceBigEndian = searchPanel.findViewById(R.id.replaceBigEndian);

        setupDropdown(searchType, idx -> {
            searchTypeIndex = idx;
            searchBigEndian.setEnabled(endianApplies(idx));
        });
        setupDropdown(replaceType, idx -> {
            replaceTypeIndex = idx;
            replaceBigEndian.setEnabled(endianApplies(idx));
        });

        searchPanel.findViewById(R.id.btnCloseSearch).setOnClickListener(v -> searchPanel.setVisibility(View.GONE));
        searchPanel.findViewById(R.id.btnSearchNext).setOnClickListener(v -> findNext());
        searchPanel.findViewById(R.id.btnSearchPrev).setOnClickListener(v -> findPrev());
        searchPanel.findViewById(R.id.btnDoReplace).setOnClickListener(v -> replaceCurrentMatch());

        View headerRow = searchPanel.findViewById(R.id.replaceHeaderRow);
        View replaceArea = searchPanel.findViewById(R.id.replaceArea);
        TextView arrow = searchPanel.findViewById(R.id.replaceExpandArrow);
        headerRow.setOnClickListener(v -> {
            boolean expanding = replaceArea.getVisibility() != View.VISIBLE;
            replaceArea.setVisibility(expanding ? View.VISIBLE : View.GONE);
            arrow.setText(expanding ? "▾" : "▸");
        });
    }

    private interface TypeSelectedListener {
        void onTypeSelected(int index);
    }

    private void setupDropdown(AutoCompleteTextView dropdown, TypeSelectedListener listener) {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, DATA_TYPES);
        dropdown.setAdapter(typeAdapter);
        dropdown.setText(DATA_TYPES.get(0), false);
        dropdown.setOnItemClickListener((parent, view, position, id) -> listener.onTypeSelected(position));
        dropdown.setOnClickListener(v -> dropdown.showDropDown());
    }

    /** Populates the static column-header row (0-7 over the hex columns, 01234567 over the ASCII column). */
    private void setupHeader() {
        View header = findViewById(R.id.hexHeader);
        int[] cellIds = {R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6, R.id.cell7};
        for (int i = 0; i < cellIds.length; i++) {
            TextView cell = header.findViewById(cellIds[i]);
            cell.setText(String.format(Locale.US, "%X", i));
            cell.setClickable(false);
        }
        TextView asciiHeader = header.findViewById(R.id.asciiCell);
        asciiHeader.setText("01234567");
    }

    private void setupKeypad() {
        LinearLayout keypad = findViewById(R.id.hexKeypad);
        char[] digits = "0123456789ABCDEF".toCharArray();
        int digitIndex = 0;
        for (int row = 0; row < keypad.getChildCount(); row++) {
            LinearLayout keyRow = (LinearLayout) keypad.getChildAt(row);
            for (int i = 0; i < keyRow.getChildCount(); i++) {
                View key = keyRow.getChildAt(i);
                if (!(key instanceof TextView tv)) continue;
                int d = Character.digit(digits[digitIndex++], 16);
                key.setEnabled(!readOnly);
                key.setOnClickListener(v -> inputNibble(d));
            }
        }
    }

    /** Reads one byte honouring unsaved modifications. Returns -1 past EOF. */
    private int readByte(int pos) {
        Integer modded = mods.get(pos);
        if (modded != null) return modded;
        try {
            if (pos >= size) return -1;
            synchronized (raf) {
                raf.seek(pos);
                return raf.read();
            }
        } catch (IOException e) {
            return -1;
        }
    }

    private void inputNibble(int digit) {
        if (readOnly || cursorPos >= size) return;
        int oldVal = readByte(cursorPos);
        if (oldVal == -1) return;
        int newVal = nibbleHigh ? ((digit << 4) | (oldVal & 0x0F)) : ((oldVal & 0xF0) | digit);

        if (nibbleHigh) {
            undoStack.push(new int[]{cursorPos, oldVal, newVal});
            redoStack.clear();
        } else {
            int[] top = undoStack.peek();
            if (top != null && top[0] == cursorPos) top[2] = newVal;
            else undoStack.push(new int[]{cursorPos, oldVal, newVal});
        }
        mods.put(cursorPos, newVal);
        nibbleHigh = !nibbleHigh;
        refreshRows(cursorPos, cursorPos);
        if (!nibbleHigh && cursorPos + 1 < size) setCursor(cursorPos + 1, false);
        updateStatus();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        int[] e = undoStack.pop();
        mods.put(e[0], e[1]);
        redoStack.push(e);
        nibbleHigh = true;
        setCursor(e[0], true);
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        int[] e = redoStack.pop();
        mods.put(e[0], e[2]);
        undoStack.push(e);
        nibbleHigh = true;
        setCursor(e[0], true);
    }

    private void setCursor(int pos, boolean scroll) {
        int old = cursorPos;
        cursorPos = Math.max(0, (int) Math.min(pos, Math.max(0, size - 1)));
        nibbleHigh = true;
        refreshRows(old, cursorPos);
        if (scroll) layoutManager.scrollToPositionWithOffset(cursorPos / 8, (int) (getResources().getDisplayMetrics().density * 80));
        updateStatus();
    }

    private void refreshRows(int... positions) {
        for (int p : positions) {
            int row = Math.min(p / 8, Math.max(0, adapter.getItemCount() - 1));
            if (row >= 0 && row < adapter.getItemCount()) adapter.notifyItemChanged(row);
        }
        updateStatus();
    }

    private void updateStatus() {
        int val = readByte(cursorPos);
        String text = String.format(Locale.US, "Val: %02Xh  Pos: %Xh  Size: %Xh [HEX]",
                val == -1 ? 0 : val, cursorPos, size);
        statusText.setText(text);
        updateInspector();
    }

    /** Right sidebar inspector: decodes bytes at the cursor in all numeric/text interpretations. */
    private void setupInspector() {
        inspectorRawHex = inspectorPanel.findViewById(R.id.inspectorRawHex);
        LinearLayout rows = inspectorPanel.findViewById(R.id.inspectorRows);
        inspectorBigEndian = inspectorPanel.findViewById(R.id.inspectorBigEndian);
        inspectorBigEndian.setOnCheckedChangeListener((b, checked) -> updateInspector());

        String[] labels = {"Signed Byte", "Unsigned Byte", "Signed Short", "Unsigned Short",
                "Signed Int", "Unsigned Int", "Signed Long", "Unsigned Long", "Float", "Double"};
        inspectorValues = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
            label.setTextSize(18);
            label.setPadding(0, dp(10), 0, dp(2));
            rows.addView(label);

            TextView value = new TextView(this);
            value.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            value.setTextSize(15);
            value.setTextIsSelectable(true);
            value.setPadding(0, 0, 0, dp(4));
            rows.addView(value);
            inspectorValues[i] = value;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateInspector() {
        if (inspectorValues == null || inspectorPanel == null || inspectorPanel.getVisibility() != View.VISIBLE) return;
        boolean be = inspectorBigEndian.isChecked();
        int[] b = new int[8];
        for (int i = 0; i < 8; i++) b[i] = readByte(cursorPos + i);

        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 8; i++) raw.append(String.format(Locale.US, "%02X ", b[i]));
        inspectorRawHex.setText(raw.toString().trim());

        long u16 = unsigned(b, 0, 2, be), u32 = unsigned(b, 0, 4, be), u64 = unsigned(b, 0, 8, be);
        setInspectorValue(0, b[0] >= 0 ? String.valueOf((byte) b[0]) : null);
        setInspectorValue(1, b[0] >= 0 ? String.valueOf(b[0] & 0xFF) : null);
        setInspectorValue(2, b[1] >= 0 ? String.valueOf((short) u16) : null);
        setInspectorValue(3, b[1] >= 0 ? Long.toUnsignedString(u16) : null);
        setInspectorValue(4, b[3] >= 0 ? String.valueOf((int) u32) : null);
        setInspectorValue(5, b[3] >= 0 ? Long.toUnsignedString(u32) : null);
        setInspectorValue(6, b[7] >= 0 ? String.valueOf(u64) : null);
        setInspectorValue(7, b[7] >= 0 ? Long.toUnsignedString(u64) : null);
        setInspectorValue(8, b[3] >= 0 ? String.valueOf(Float.intBitsToFloat((int) u32)) : null);
        setInspectorValue(9, b[7] >= 0 ? String.valueOf(Double.longBitsToDouble(u64)) : null);
    }

    private void setInspectorValue(int index, String value) {
        inspectorValues[index].setText(value != null ? value : "—");
    }

    /** Assembles len bytes starting at off into an unsigned long honouring endianness. */
    private static long unsigned(int[] b, int off, int len, boolean bigEndian) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            int by = b[off + i];
            if (by < 0) return v;
            v |= (by & 0xFFL) << (8 * (bigEndian ? len - 1 - i : i));
        }
        return v;
    }

    // ------------------------------------------------------------------ search / replace

    /** Encodes the given text as the byte pattern for the selected data type. */
    private byte[] buildPattern(int type, String text, boolean bigEndian) throws Exception {
        if (text == null || TextUtils.isEmpty(text.trim())) throw new IllegalArgumentException("Empty search value");
        switch (type) {
            case TYPE_HEX_TEXT: {
                String hex = text.replaceAll("[^0-9a-fA-F]", "");
                if (hex.isEmpty() || hex.length() % 2 != 0) throw new IllegalArgumentException("Invalid hex");
                return toBytes(hex);
            }
            case TYPE_ASCII: return text.getBytes(StandardCharsets.ISO_8859_1);
            case TYPE_UNICODE: return text.getBytes(bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
            case TYPE_UTF8: return text.getBytes(StandardCharsets.UTF_8);
            case TYPE_GBK: try {return text.getBytes("GBK");} catch (Exception e) {return text.getBytes(StandardCharsets.UTF_8);}
            default: {
                long v = parseNumberForType(type, text);
                int len = typeSize(type);
                byte[] out = new byte[len];
                putValue(out, 0, v, len, bigEndian);
                return out;
            }
        }
    }

    private static void putValue(byte[] out, int off, long val, int len, boolean bigEndian) {
        for (int i = 0; i < len; i++)
            out[off + i] = (byte) ((val >> (8 * (bigEndian ? len - 1 - i : i))) & 0xFF);
    }

    /** Parses and range-checks a numeric literal for the given signed/unsigned/floating type. */
    private long parseNumberForType(int type, String text) throws NumberFormatException {
        String t = text.trim();
        switch (type) {
            case TYPE_SBYTE: return Byte.parseByte(t);
            case TYPE_UBYTE: {long v = Long.parseLong(t); if (v < 0 || v > 255) throw new NumberFormatException(); return v;}
            case TYPE_SSHORT: return Short.parseShort(t);
            case TYPE_USHORT: {long v = Long.parseLong(t); if (v < 0 || v > 65535) throw new NumberFormatException(); return v;}
            case TYPE_SINT: return Integer.parseInt(t);
            case TYPE_UINT: {BigInteger bi = new BigInteger(t); BigInteger max = BigInteger.valueOf(4294967295L); if (bi.signum() < 0 || bi.compareTo(max) > 0) throw new NumberFormatException(); return bi.longValue();}
            case TYPE_SLONG: return Long.parseLong(t);
            case TYPE_ULONG: {BigInteger bi = new BigInteger(t); BigInteger max = new BigInteger("18446744073709551615"); if (bi.signum() < 0 || bi.compareTo(max) > 0) throw new NumberFormatException(); return bi.longValue();}
            case TYPE_FLOAT: return Float.floatToIntBits(Float.parseFloat(t));
            case TYPE_DOUBLE: return Double.doubleToLongBits(Double.parseDouble(t));
            default: throw new IllegalArgumentException("Not a numeric type");
        }
    }

    /** Builds the pattern from the sidebar inputs; shows a toast and returns false when invalid. */
    private boolean ensureSearchPattern() {
        lastPattern = null;
        try {
            lastPattern = buildPattern(searchTypeIndex, searchValue.getText().toString(), searchBigEndian.isChecked());
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Invalid search value for " + DATA_TYPES.get(searchTypeIndex), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void findNext() {
        if (!ensureSearchPattern()) return;
        scan(cursorPos + 1, true);
    }

    private void findPrev() {
        if (!ensureSearchPattern()) return;
        scan(cursorPos - 1, false);
    }

    /** Scans forward/backward on a background thread, honouring unsaved modifications. */
    private void scan(long start, boolean forward) {
        final byte[] pattern = lastPattern;
        final long startPos = start < 0 ? (forward ? 0 : Math.max(0, size - pattern.length)) : start;
        final ProgressManager pm = new ProgressManager(this, true);
        pm.setText(getString(R.string.search) + "…");
        pm.show();
        new Thread(() -> {
            long found = -1;
            if (forward) {
                for (long p = startPos; p + pattern.length <= size; p++)
                    if (matchesAt(p, pattern)) {found = p; break;}
            } else {
                for (long p = Math.min(startPos, size - pattern.length); p >= 0; p--)
                    if (matchesAt(p, pattern)) {found = p; break;}
            }
            final long f = found;
            runOnUiThread(() -> {
                pm.dismiss();
                if (f >= 0) {
                    lastMatchPos = f;
                    setCursor((int) f, true);
                } else {
                    Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private boolean matchesAt(long pos, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++)
            if (readByte((int) (pos + i)) != (pattern[i] & 0xFF)) return false;
        return true;
    }

    /** Writes the replace-field value over the current match (or at the cursor when no match yet). */
    private void replaceCurrentMatch() {
        if (!ensureSearchPattern()) return;
        long target = lastMatchPos >= 0 ? lastMatchPos : cursorPos;
        if (target + lastPattern.length > size) {
            Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] repl;
        try {
            repl = buildPattern(replaceTypeIndex, replaceValue.getText().toString(), replaceBigEndian.isChecked());
        } catch (Exception e) {
            Toast.makeText(this, "Invalid replace value for " + DATA_TYPES.get(replaceTypeIndex), Toast.LENGTH_SHORT).show();
            return;
        }
        writeBytesAt(target, repl, null);
        lastMatchPos = target;
        findNext();
    }

    /** Applies bytes through the modification overlay, recording one undo entry per byte. */
    private void writeBytesAt(long pos, byte[] data, Runnable extraUndoAction) {
        List<int[]> entries = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            int p = (int) (pos + i);
            if (p >= size) break;
            int oldVal = readByte(p);
            int newVal = data[i] & 0xFF;
            mods.put(p, newVal);
            entries.add(new int[]{p, oldVal, newVal});
        }
        undoStack.addAll(entries); // each entry undone individually
        redoStack.clear();
        nibbleHigh = true;
        if (adapter != null) {
            int firstRow = (int) (pos / 8);
            int lastRow = (int) ((pos + Math.max(1, entries.size()) - 1) / 8);
            for (int r = firstRow; r <= lastRow && r < adapter.getItemCount(); r++) adapter.notifyItemChanged(r);
        }
        setCursor((int) Math.min(pos + data.length, Math.max(0, size - 1)), false);
        updateStatus();
    }

    // ------------------------------------------------------------------ paste from

    private void showPasteFromDialog() {
        if (readOnly) {
            Extensions.showMessage(this, "File is read-only");
            return;
        }
        String[] options = {"Paste from hex text", "Paste from decimal text", "Paste from binary text",
                "Paste from ascii", "Paste from base64"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Paste from")
                .setItems(options, (d, w) -> promptPasteInput(w))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptPasteInput(int formatIndex) {
        String[] formats = {"hex", "decimal", "binary", "ASCII", "Base64"};
        EditText input = new EditText(this);
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP);
        input.setText(getClipboardText());
        new MaterialAlertDialogBuilder(this)
                .setTitle("Paste from " + formats[formatIndex])
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        byte[] data = parsePastedBytes(formatIndex, input.getText().toString());
                        if (data == null || data.length == 0) {
                            Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        writeBytesAt(cursorPos, data, null);
                        Toast.makeText(this, "Pasted " + data.length + " bytes", Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getClipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData cd = cm != null ? cm.getPrimaryClip() : null;
        return cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null
                ? String.valueOf(cd.getItemAt(0).getText()) : "";
    }

    private byte[] parsePastedBytes(int format, String text) {
        if (TextUtils.isEmpty(text)) return new byte[0];
        switch (format) {
            case 0: { // Hex text
                String hex = text.replaceAll("[^0-9a-fA-F]", "").toLowerCase(Locale.ROOT);
                if (hex.length() % 2 != 0) throw new IllegalArgumentException("Hex length must be even");
                byte[] out = new byte[hex.length() / 2];
                for (int i = 0; i < out.length; i++)
                    out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                return out;
            }
            case 1: { // Decimal text
                String[] parts = text.trim().split("[,\\s]+");
                byte[] out = new byte[parts.length];
                for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i].trim());
                return out;
            }
            case 2: { // Binary text
                String bits = text.replaceAll("[^01]", "");
                if (bits.isEmpty() || bits.length() % 8 != 0)
                    throw new IllegalArgumentException("Binary length must be a multiple of 8");
                byte[] out = new byte[bits.length() / 8];
                for (int i = 0; i < out.length; i++)
                    out[i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2);
                return out;
            }
            case 3: return text.getBytes(StandardCharsets.ISO_8859_1); // ASCII
            case 4:
                return Base64.decode(text.trim(), Base64.DEFAULT); // Base64
            default: return new byte[0];
        }
    }

    /** Builds a byte array from an uppercase hex string. */
    private static byte[] toBytes(String upperHex) {
        byte[] out = new byte[upperHex.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(upperHex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }


    private void saveChanges() {
        if (mods.isEmpty()) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show();
            return;
        }
        if (readOnly) {
            Extensions.showMessage(this, "File is read-only");
            return;
        }
        try (RandomAccessFile w = new RandomAccessFile(file, "rw")) {
            for (java.util.Map.Entry<Integer, Integer> entry : mods.entrySet()) {
                w.seek(entry.getKey());
                w.writeByte(entry.getValue());
            }
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void revertChanges() {
        if (mods.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Revert changes")
                .setMessage("Discard all unsaved changes?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    mods.clear();
                    undoStack.clear();
                    redoStack.clear();
                    nibbleHigh = true;
                    adapter.notifyDataSetChanged();
                    updateStatus();
                }).show();
    }

    private void showGotoDialog() {
        EditText input = new EditText(this);
        input.setHint("Offset in hex (e.g. 1A0)");
        input.setText(String.format(Locale.US, "%X", cursorPos));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Go to offset")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        long target = Long.parseLong(input.getText().toString().trim(), 16);
                        setCursor((int) Math.min(target, Math.max(0, size - 1)), true);
                    } catch (NumberFormatException ignored) {
                        Toast.makeText(this, "Invalid offset", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void confirmDiscardAndFinish() {
        if (mods.isEmpty()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved changes")
                .setMessage("Discard unsaved changes?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if(inspectorPanel.getVisibility() == View.VISIBLE) inspectorPanel.setVisibility(View.GONE);
        else if(searchPanel.getVisibility() == View.VISIBLE) searchPanel.setVisibility(View.GONE);
        else confirmDiscardAndFinish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_hex_editor, menu);
        menu.findItem(R.id.action_undo).setEnabled(!readOnly);
        menu.findItem(R.id.action_redo).setEnabled(!readOnly);
        menu.findItem(R.id.action_save).setVisible(!readOnly);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) undo();
        else if (id == R.id.action_redo) redo();
        else if (id == R.id.action_save) saveChanges();
        else if (id == R.id.action_goto) showGotoDialog();
        else if (id == R.id.action_revert) revertChanges();
        else if (id == R.id.action_search) {
            searchPanel.setVisibility(searchPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            inspectorPanel.setVisibility(View.GONE);
        } else if (id == R.id.action_inspector) {
            inspectorPanel.setVisibility(inspectorPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            searchPanel.setVisibility(View.GONE);
            updateInspector();
        } else if (id == R.id.action_paste_from) showPasteFromDialog();
        else if (id == R.id.action_exit) confirmDiscardAndFinish();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (raf != null) try {
            raf.close();
        } catch (IOException ignored) {
        }
    }

    private int getPrimaryColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE);
    }

    private int getOnPrimaryColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
    }

    private int getPrimaryContainerColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY);
    }

    /** Rows of 8 bytes: offset + hex cells + ASCII preview. */
    private class HexRowAdapter extends RecyclerView.Adapter<HexRowAdapter.ViewHolder> {

        HexRowAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(HexEditorActivity.this).inflate(R.layout.item_hex_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int rowStart = position * 8;
            int[] values = new int[8];
            for (int i = 0; i < 8; i++) values[i] = readByte(rowStart + i);

            holder.offsetCell.setText(String.format(Locale.US, "%04X", rowStart & 0xFFFF));

            int selCol = cursorPos - rowStart;
            for (int i = 0; i < 8; i++) {
                TextView cell = holder.cells[i];
                if (values[i] == -1) {
                    cell.setText("");
                    cell.setBackground(null);
                    continue;
                }
                cell.setText(String.format(Locale.US, "%02X", values[i]));
                if (i == selCol) {
                    cell.setBackgroundColor(getPrimaryContainerColor());
                } else {
                    cell.setBackground(null);
                }
                final int pos = rowStart + i;
                cell.setOnClickListener(v -> setCursor(pos, false));
            }

            StringBuilder asciiBuilder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                int v = values[i];
                asciiBuilder.append(v >= 0x20 && v <= 0x7E ? (char) v : '.');
            }
            SpannableString ascii = new SpannableString(asciiBuilder.toString());
            if (selCol >= 0 && selCol < 8) {
                ascii.setSpan(new BackgroundColorSpan(getPrimaryColor()), selCol, selCol + 1, 0);
                ascii.setSpan(new ForegroundColorSpan(getOnPrimaryColor()), selCol, selCol + 1, 0);
            }
            holder.asciiCell.setText(ascii);
        }

        @Override
        public int getItemCount() {
            return (int) ((size + 7) / 8);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView offsetCell, asciiCell;
            final TextView[] cells = new TextView[8];

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                offsetCell = itemView.findViewById(R.id.offsetCell);
                asciiCell = itemView.findViewById(R.id.asciiCell);
                cells[0] = itemView.findViewById(R.id.cell0);
                cells[1] = itemView.findViewById(R.id.cell1);
                cells[2] = itemView.findViewById(R.id.cell2);
                cells[3] = itemView.findViewById(R.id.cell3);
                cells[4] = itemView.findViewById(R.id.cell4);
                cells[5] = itemView.findViewById(R.id.cell5);
                cells[6] = itemView.findViewById(R.id.cell6);
                cells[7] = itemView.findViewById(R.id.cell7);
            }
        }
    }
}
