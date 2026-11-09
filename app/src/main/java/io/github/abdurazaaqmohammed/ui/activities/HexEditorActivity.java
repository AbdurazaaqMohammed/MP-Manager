package io.github.abdurazaaqmohammed.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.MinimizedEditors;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;


public class HexEditorActivity extends BaseActivity {

    /** Upper bound for byte ranges read for copy-as / delete, keeping the copy and digest buffers sane. */
    private static final int MAX_MENU_BYTES = 4 * 1024 * 1024;

    private File file;
    private RandomAccessFile raf;
    private long size;
    private boolean readOnly;
    private String rootOriginalPath;

    /**
     * In-place byte edits (length preserving). Kept on the fast path: saving only
     * rewrites these bytes. Once a length-changing edit happens (delete / paste of
     * a different length) everything is baked into {@link #chunks} and this stays empty.
     */
    private final TreeMap<Long, Integer> mods = new TreeMap<>();
    /** True once a length-changing edit happened; all content then lives in chunks. */
    private boolean structural;
    /** Size of the file when it was opened / last saved; the chunk model resets to this. */
    private long originalSize;

    /** One applied content change; before/after may differ in length (insert/delete). */
    private static final class EditOp {
        final long pos;
        final byte[] before;
        byte[] after;

        EditOp(long pos, byte[] before, byte[] after) {
            this.pos = pos;
            this.before = before;
            this.after = after;
        }
    }

    private final ArrayDeque<EditOp> undoStack = new ArrayDeque<>();
    private final ArrayDeque<EditOp> redoStack = new ArrayDeque<>();

    /**
     * Virtual file model used after structural edits: the content is a sequence of
     * chunks, each either a range of the original file or an in-memory byte array.
     * A deletion removes chunks, a paste of any length splices a memory chunk in,
     * so the virtual size can grow and shrink freely.
     */
    private static final class Chunk {
        final boolean memory;
        long origOffset;   // file chunk: offset in the original file
        long length;
        byte[] data;       // memory chunk

        Chunk(long origOffset, long length) {
            this.memory = false;
            this.origOffset = origOffset;
            this.length = length;
        }

        Chunk(byte[] data) {
            this.memory = true;
            this.data = data;
            this.length = data.length;
        }
    }

    private final List<Chunk> chunks = new ArrayList<>();
    /** Cumulative virtual start offset of every chunk, kept in sync by {@link #rebuildIndex()}. */
    private long[] chunkStarts = new long[0];

    // Offsets are long: system images (.iso/.img) pass 2 GB, where int math
    // silently wraps and the editor would corrupt the wrong bytes.
    private long cursorPos;
    private boolean nibbleHigh = true;
    private HexRowAdapter adapter;
    private TextView statusText;
    private LinearLayoutManager layoutManager;
    private FastScrollerRecyclerView recycler;

    // Byte selection, MT-manager style: long-press a cell then drag across rows,
    // or use the long-press menu's "Select range" dialog. Offsets are inclusive.
    private long selStart = -1, selEnd = -1;
    private volatile boolean selectionDrag;
    private boolean selectionWasEmpty;
    private boolean selectionIntercepted;
    private long selectionAnchor;
    /** Byte the current cell menu was opened on, so Copy works on the pressed cell. */
    private long menuAnchorByte = -1;
    private float lastDragX, lastDragY;
    private final Handler dragHandler = new Handler(Looper.getMainLooper());

    private View lastTouchedCell;
    private GestureDetector tapDetector;

    private final View.OnTouchListener cellTouchListener = (v, ev) -> {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) lastTouchedCell = v;
        if (tapDetector != null) tapDetector.onTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            // The stream stayed with the cell, so no drag interception happened:
            // a long-press without movement ends here and opens the cell menu.
            endSelectionDrag(true);
        } else if (action == MotionEvent.ACTION_CANCEL && !selectionDrag) {
            // Canceled without an armed drag (parent scroll etc.) - nothing to keep.
            endSelectionDrag(false);
        }
        // Consumed: cursor moves and selection drags are driven here instead of
        // the TextView's own (single-cell) text selection.
        return true;
    };

    /** Receives the touch stream once a selection drag is intercepted. */
    private final RecyclerView.SimpleOnItemTouchListener dragInterceptor = new RecyclerView.SimpleOnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent ev) {
            if (selectionDrag && ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                selectionIntercepted = true;
                return true;
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                lastDragX = ev.getX();
                lastDragY = ev.getY();
                updateDragFocus();
                startAutoScrollIfNeeded();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                endSelectionDrag(false);
            }
        }
    };

    private final Runnable dragAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (!selectionDrag || recycler == null) return;
            int height = recycler.getHeight();
            if (height == 0) return;
            int edge = dp(28);
            int direction;
            if (lastDragY < edge) direction = -1;
            else if (lastDragY > height - edge) direction = 1;
            else return;
            recycler.scrollBy(0, direction * dp(28));
            updateDragFocus();
            dragHandler.postDelayed(this, 40);
        }
    };

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_editor);

        String path = getIntent().getStringExtra("path");
        file = path != null ? new File(path) : null;
        String rop = getIntent().getStringExtra("rootOriginalPath");
        if (rop != null && !rop.isEmpty() && file != null
                && !rop.equals(file.getAbsolutePath())) {
            rootOriginalPath = rop;
        }
        if (file == null || !file.isFile()) {
            Extensions.showMessage(this, getString(R.string.hex_file_not_found));
            finish();
            return;
        }
        try {
            try {
                raf = new RandomAccessFile(file, "rw");
                readOnly = false;
            } catch (Exception e) {
                raf = new RandomAccessFile(file, "r");
                readOnly = true;
            }
            size = raf.length();
            originalSize = size;
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.hexToolbar);
        toolbar.setTitle(R.string.hex_editor);
        toolbar.setSubtitle(rootOriginalPath != null
                ? new File(rootOriginalPath).getName() + " (root)"
                : file.getName());
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        toolbar.setNavigationOnClickListener(v -> handleBack());

        tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                View v = lastTouchedCell;
                if (v != null && v.getTag() instanceof Number) {
                    clearSelection();
                    setCursor(((Number) v.getTag()).longValue(), false);
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                View v = lastTouchedCell;
                if (v == null || !(v.getTag() instanceof Number)) return;
                selectionAnchor = ((Number) v.getTag()).longValue();
                menuAnchorByte = selectionAnchor;
                selectionWasEmpty = selStart < 0;
                selectionIntercepted = false;
                // Highlight the pressed byte immediately; it becomes the drag
                // anchor as soon as the finger moves, or the menu opens on release.
                if (selectionWasEmpty) setSelectionRange(selectionAnchor, selectionAnchor);
                selectionDrag = true;
            }
        });

        setupHeader();

        statusText = findViewById(R.id.statusText);
        recycler = findViewById(R.id.hexRecycler);
        layoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(layoutManager);
        recycler.addOnItemTouchListener(dragInterceptor);
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

        long initialOffset = getIntent().getLongExtra("offset", -1);
        if (initialOffset >= 0) {
            recycler.post(() -> setCursor(Math.min(initialOffset, Math.max(0, size - 1)), true));
        }
    }

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
                if (!(key instanceof TextView)) continue;
                int d = Character.digit(digits[digitIndex++], 16);
                key.setEnabled(!readOnly);
                key.setOnClickListener(v -> inputNibble(d));
            }
        }
    }

    // ==========================================
    // Virtual content model
    // ==========================================

    /** Rebuilds the cumulative chunk index and the virtual size. */
    private void rebuildIndex() {
        chunkStarts = new long[chunks.size()];
        long acc = 0;
        for (int i = 0; i < chunks.size(); i++) {
            chunkStarts[i] = acc;
            acc += chunks.get(i).length;
        }
        size = acc;
    }

    /** Resets the content view to the original file (open / after save / revert). */
    private void resetChunks() {
        chunks.clear();
        mods.clear();
        structural = false;
        size = originalSize;
        if (size > 0) chunks.add(new Chunk(0, size));
        rebuildIndex();
    }

    /** Index of the chunk containing {@code vpos} (or the last chunk when at the end). */
    private int findChunkIndex(long vpos) {
        int lo = 0, hi = chunkStarts.length - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (chunkStarts[mid] <= vpos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private Chunk sliceChunk(Chunk c, long from, long len) {
        if (c.memory) {
            byte[] part = new byte[(int) len];
            System.arraycopy(c.data, (int) from, part, 0, (int) len);
            return new Chunk(part);
        }
        return new Chunk(c.origOffset + from, len);
    }

    /** Splits the chunk containing {@code vpos} so a splice can start/end there. */
    private void splitAt(long vpos) {
        if (vpos <= 0 || vpos >= size || chunks.isEmpty()) return;
        int idx = findChunkIndex(vpos);
        if (idx >= chunks.size()) return;
        Chunk c = chunks.get(idx);
        long off = vpos - chunkStarts[idx];
        if (off <= 0 || off >= c.length) return;
        Chunk right = sliceChunk(c, off, c.length - off);
        c.length = off;
        if (c.memory) c.data = Arrays.copyOf(c.data, (int) off);
        chunks.add(idx + 1, right);
        rebuildIndex();
    }

    private void applyDeleteChunks(long pos, long end) {
        if (end <= pos || pos >= size) return;
        end = Math.min(end, size);
        splitAt(pos);
        splitAt(end);
        List<Chunk> out = new ArrayList<>(chunks.size());
        long start = 0;
        for (Chunk c : chunks) {
            long cStart = start;
            long cEnd = start + c.length;
            start = cEnd;
            if (cEnd <= pos || cStart >= end) {
                out.add(c);
                continue;
            }
            if (cStart < pos) out.add(sliceChunk(c, 0, pos - cStart));
            if (cEnd > end) out.add(sliceChunk(c, end - cStart, c.length - (end - cStart)));
        }
        chunks.clear();
        chunks.addAll(out);
        rebuildIndex();
    }

    private void applyInsertChunks(long pos, byte[] data) {
        if (data.length == 0) return;
        if (pos >= size || chunks.isEmpty()) {
            chunks.add(new Chunk(data.clone()));
            rebuildIndex();
            return;
        }
        splitAt(pos);
        int idx = findChunkIndex(pos);
        if (idx >= chunks.size()) chunks.add(new Chunk(data.clone()));
        else chunks.add(idx, new Chunk(data.clone()));
        rebuildIndex();
    }

    /**
     * Replaces {@code oldLen} virtual bytes at {@code pos} with {@code data} (any
     * length, including empty for a deletion). Length-preserving edits on the
     * un-structural path stay in the fast {@link #mods} map; anything else bakes
     * the state into chunks once and splices there.
     */
    private void splice(long pos, long oldLen, byte[] data) {
        if (oldLen < 0) oldLen = 0;
        pos = Math.max(0, Math.min(pos, size));
        long end = Math.min(pos + oldLen, size);
        oldLen = end - pos;
        if (!structural) {
            if (data.length == oldLen && oldLen > 0) {
                for (int i = 0; i < data.length; i++) mods.put(pos + i, data[i] & 0xFF);
                return;
            }
            bakeStructural();
        }
        // Chunk fast path: same-length write fully inside one memory chunk.
        if (data.length == oldLen && oldLen > 0 && !chunks.isEmpty()) {
            int idx = findChunkIndex(pos);
            if (idx < chunks.size()) {
                Chunk c = chunks.get(idx);
                long off = pos - chunkStarts[idx];
                if (c.memory && off + oldLen <= c.length) {
                    System.arraycopy(data, 0, c.data, (int) off, data.length);
                    return;
                }
            }
        }
        if (oldLen > 0) applyDeleteChunks(pos, pos + oldLen);
        applyInsertChunks(pos, data);
    }

    /** Moves all fast-path edits into the chunk model; from here on everything splices. */
    private void bakeStructural() {
        if (structural) return;
        structural = true;
        chunks.clear();
        if (size > 0) chunks.add(new Chunk(0, size));
        for (Map.Entry<Long, Integer> e : mods.entrySet()) {
            applyDeleteChunks(e.getKey(), e.getKey() + 1);
            applyInsertChunks(e.getKey(), new byte[]{e.getValue().byteValue()});
        }
        mods.clear();
        rebuildIndex();
    }

    private int readByte(long pos) {
        if (pos < 0 || pos >= size) return -1;
        if (!structural) {
            Integer modded = mods.get(pos);
            if (modded != null) return modded;
            try {
                synchronized (raf) {
                    raf.seek(pos);
                    return raf.read();
                }
            } catch (IOException e) {
                return -1;
            }
        }
        if (chunks.isEmpty()) return -1;
        int idx = findChunkIndex(pos);
        if (idx >= chunks.size()) return -1;
        Chunk c = chunks.get(idx);
        long off = pos - chunkStarts[idx];
        if (c.memory) return c.data[(int) off] & 0xFF;
        try {
            synchronized (raf) {
                raf.seek(c.origOffset + off);
                return raf.read();
            }
        } catch (IOException e) {
            return -1;
        }
    }

    /** Reads [start, end) efficiently: bulk reads per chunk, byte-wise only where needed. */
    private byte[] readRange(long start, long end) {
        start = Math.max(0, start);
        end = Math.min(end, size);
        if (end <= start) return new byte[0];
        byte[] out = new byte[(int) (end - start)];
        int filled = 0;
        long vpos = start;
        while (vpos < end) {
            if (structural && chunks.isEmpty()) break;
            int idx = structural ? findChunkIndex(vpos) : -1;
            Chunk c = structural && idx < chunks.size() ? chunks.get(idx) : null;
            long take;
            if (c == null) {
                // Un-structural: a plain span of the original file + sparse in-place mods.
                take = end - vpos;
                try {
                    synchronized (raf) {
                        raf.seek(vpos);
                        int done = 0;
                        while (done < take) {
                            int r = raf.read(out, filled + done, (int) (take - done));
                            if (r <= 0) return Arrays.copyOf(out, filled);
                            done += r;
                        }
                    }
                } catch (IOException e) {
                    return Arrays.copyOf(out, filled);
                }
                for (Map.Entry<Long, Integer> e : mods.tailMap(vpos).entrySet()) {
                    if (e.getKey() >= vpos + take) break;
                    out[filled + (int) (e.getKey() - vpos)] = e.getValue().byteValue();
                }
            } else if (c.memory) {
                long inOff = vpos - chunkStarts[idx];
                take = Math.min(c.length - inOff, end - vpos);
                System.arraycopy(c.data, (int) inOff, out, filled, (int) take);
            } else {
                long inOff = vpos - chunkStarts[idx];
                take = Math.min(c.length - inOff, end - vpos);
                try {
                    synchronized (raf) {
                        raf.seek(c.origOffset + inOff);
                        int done = 0;
                        while (done < take) {
                            int r = raf.read(out, filled + done, (int) (take - done));
                            if (r <= 0) return Arrays.copyOf(out, filled);
                            done += r;
                        }
                    }
                } catch (IOException e) {
                    return Arrays.copyOf(out, filled);
                }
            }
            filled += (int) take;
            vpos += take;
        }
        return out;
    }

    private void inputNibble(int digit) {
        if (readOnly || cursorPos >= size) return;
        int oldVal = readByte(cursorPos);
        if (oldVal == -1) return;
        boolean wasHigh = nibbleHigh;
        int newVal = wasHigh ? ((digit << 4) | (oldVal & 0x0F)) : ((oldVal & 0xF0) | digit);

        if (wasHigh) {
            undoStack.push(new EditOp(cursorPos, new byte[]{(byte) oldVal}, new byte[]{(byte) newVal}));
            redoStack.clear();
        } else {
            EditOp top = undoStack.peek();
            if (top != null && top.pos == cursorPos && top.after.length == 1) {
                top.after = new byte[]{(byte) newVal};
            } else {
                undoStack.push(new EditOp(cursorPos, new byte[]{(byte) oldVal}, new byte[]{(byte) newVal}));
            }
        }
        splice(cursorPos, 1, new byte[]{(byte) newVal});
        nibbleHigh = !nibbleHigh;
        refreshRows(cursorPos, cursorPos);
        // Advance to the next byte only after the low (2nd) character was entered so both characters of the current byte can be edited
        if (!wasHigh && cursorPos + 1 < size) setCursor(cursorPos + 1, false);
        updateStatus();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        EditOp e = undoStack.pop();
        splice(e.pos, e.after.length, e.before);
        redoStack.push(e);
        nibbleHigh = true;
        if (adapter != null) adapter.notifyDataSetChanged();
        setCursor(e.pos, true);
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        EditOp e = redoStack.pop();
        splice(e.pos, e.before.length, e.after);
        undoStack.push(e);
        nibbleHigh = true;
        if (adapter != null) adapter.notifyDataSetChanged();
        setCursor(e.pos, true);
    }

    private void setCursor(long pos, boolean scroll) {
        long old = cursorPos;
        cursorPos = Math.max(0, Math.min(pos, Math.max(0, size - 1)));
        nibbleHigh = true;
        refreshRows(old, cursorPos);
        if (scroll) layoutManager.scrollToPositionWithOffset((int) (cursorPos / 8), (int) (getResources().getDisplayMetrics().density * 80));
        updateStatus();
    }

    private void refreshRows(long... positions) {
        for (long p : positions) {
            int row = (int) Math.min(p / 8, Math.max(0, adapter.getItemCount() - 1));
            if (row >= 0 && row < adapter.getItemCount()) adapter.notifyItemChanged(row);
        }
        updateStatus();
    }

    // ==========================================
    // Selection (drag multi-select + range dialog)
    // ==========================================

    private boolean isByteSelected(long offset) {
        return selStart >= 0 && offset >= selStart && offset <= selEnd;
    }

    private void setSelectionRange(long start, long end) {
        start = Math.max(0, Math.min(start, size - 1));
        end = Math.max(0, Math.min(end, size - 1));
        if (start > end) {
            long t = start;
            start = end;
            end = t;
        }
        long oldStart = selStart, oldEnd = selEnd;
        selStart = start;
        selEnd = end;
        if (adapter != null) refreshRange(oldStart, oldEnd, start, end);
        updateStatus();
    }

    private void clearSelection() {
        if (selStart < 0) return;
        long s = selStart, e = selEnd;
        selStart = selEnd = -1;
        if (adapter != null) refreshRange(s, e, -1, -1);
        updateStatus();
    }

    /** Repaints every row covered by either the previous or the new selection. */
    private void refreshRange(long aStart, long aEnd, long bStart, long bEnd) {
        long lo = -1, hi = -1;
        if (aStart >= 0) {
            lo = Math.min(aStart, aEnd);
            hi = Math.max(aStart, aEnd);
        }
        if (bStart >= 0) {
            lo = lo < 0 ? Math.min(bStart, bEnd) : Math.min(lo, Math.min(bStart, bEnd));
            hi = hi < 0 ? Math.max(bStart, bEnd) : Math.max(hi, Math.max(bStart, bEnd));
        }
        if (hi < 0 || adapter == null) return;
        int firstRow = (int) Math.max(0, lo / 8);
        int lastRow = (int) Math.min(adapter.getItemCount() - 1, hi / 8);
        for (int r = firstRow; r <= lastRow; r++) adapter.notifyItemChanged(r);
    }

    private void endSelectionDrag(boolean menuIfNoDrag) {
        dragHandler.removeCallbacks(dragAutoScroll);
        if (!selectionDrag) return;
        selectionDrag = false;
        if (menuIfNoDrag && !selectionIntercepted) {
            // Long press released without dragging: restore the previous
            // selection state and open the cell menu.
            if (selectionWasEmpty) clearSelection();
            showCellMenu();
        }
        selectionIntercepted = false;
    }

    private void updateDragFocus() {
        long focus = byteAt(recycler, lastDragX, lastDragY);
        if (focus < 0) return;
        setSelectionRange(selectionAnchor, focus);
    }

    private void startAutoScrollIfNeeded() {
        int height = recycler.getHeight();
        if (height == 0) return;
        int edge = dp(28);
        boolean inEdge = lastDragY < edge || lastDragY > height - edge;
        if (inEdge) dragHandler.removeCallbacks(dragAutoScroll);
        if (inEdge) dragHandler.postDelayed(dragAutoScroll, 40);
    }

    /** Maps recycler-local coordinates to the byte offset under the pointer. */
    private long byteAt(RecyclerView rv, float x, float y) {
        if (rv == null) return -1;
        View row = rv.findChildViewUnder(x, y);
        if (row == null) return -1;
        int pos = rv.getChildAdapterPosition(row);
        if (pos == RecyclerView.NO_POSITION) return -1;
        long rowStart = pos * 8L;
        int[] rvLoc = new int[2];
        rv.getLocationOnScreen(rvLoc);
        int[] rowLoc = new int[2];
        row.getLocationOnScreen(rowLoc);
        float rowX = x + rvLoc[0] - rowLoc[0];

        View ascii = row.findViewById(R.id.asciiCell);
        if (ascii != null && ascii.getWidth() > 0 && rowX >= ascii.getLeft() && rowX < ascii.getRight()) {
            int col = (int) (((rowX - ascii.getLeft()) * 8) / ascii.getWidth());
            col = Math.max(0, Math.min(7, col));
            return Math.min(rowStart + col, size - 1);
        }
        long best = -1;
        long bestDist = Long.MAX_VALUE;
        int slop = dp(4);
        if (!(row instanceof ViewGroup rowGroup)) return -1;
        for (int i = 0; i < rowGroup.getChildCount(); i++) {
            View child = rowGroup.getChildAt(i);
            if (!(child instanceof TextView) || !(child.getTag() instanceof Number)) continue;
            long dist = (long) Math.abs(rowX - (child.getLeft() + child.getRight()) / 2);
            if (rowX >= child.getLeft() - slop && rowX < child.getRight() + slop) dist = 0;
            if (dist < bestDist) {
                bestDist = dist;
                best = ((Number) child.getTag()).longValue();
            }
        }
        return best >= 0 ? Math.min(best, size - 1) : -1;
    }

    private int getSelectionColor() {
        return (getPrimaryColor() & 0x00FFFFFF) | 0x55000000;
    }

    /** Long-press menu: copy, copy-as formats, delete, paste and the range dialog. */
    private void showCellMenu() {
        boolean hasSelection = selStart >= 0;
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(hasSelection ? R.string.hex_copy_selection : R.string.hex_copy));
        actions.add(this::copySelectionOrByte);
        items.add(getString(R.string.hex_copy_as));
        actions.add(this::showCopyAsDialog);
        items.add(getString(R.string.hex_paste));
        actions.add(this::pasteClipboardHex);
        items.add(getString(R.string.delete));
        actions.add(this::deleteSelectionOrByte);
        items.add(getString(R.string.select));
        actions.add(this::showSelectRangeDialog);
        if (hasSelection) {
            items.add(getString(R.string.hex_clear_selection));
            actions.add(this::clearSelection);
        }
        CharSequence[] labels = items.toArray(new CharSequence[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.select))
                .setItems(labels, (d, w) -> actions.get(w).run())
                .show();
    }

    /** Start of the byte range the cell menu operates on. */
    private long menuRangeStart() {
        return selStart >= 0 ? selStart : (menuAnchorByte >= 0 ? menuAnchorByte : cursorPos);
    }

    private long menuRangeEnd() {
        return selStart >= 0 ? selEnd : menuRangeStart();
    }

    /** Reads the menu range; null (with a message) when it exceeds the copy cap. */
    private byte[] readMenuRangeBytes() {
        long start = menuRangeStart();
        long end = menuRangeEnd();
        if (end - start + 1 > MAX_MENU_BYTES) {
            Extensions.showMessage(this, getString(R.string.hex_selection_too_large, MAX_MENU_BYTES / (1024 * 1024)));
            return null;
        }
        byte[] out = readRange(start, end + 1);
        return out.length == 0 ? null : out;
    }

    private void copySelectionOrByte() {
        byte[] data = readMenuRangeBytes();
        if (data == null || data.length == 0) return;
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte b : data) hex.append(String.format(Locale.US, "%02X", b));
        copyToClipboard(hex.toString());
        Extensions.showMessage(this, getString(R.string.hex_copied_n, data.length));
    }

    private void showCopyAsDialog() {
        byte[] data = readMenuRangeBytes();
        if (data == null || data.length == 0) return;
        String[] formats = {
                getString(R.string.hex_copy_hex),
                getString(R.string.hex_copy_dec),
                getString(R.string.hex_copy_bin),
                getString(R.string.hex_copy_ascii),
                getString(R.string.hex_copy_b64),
                getString(R.string.hex_copy_sha1),
                getString(R.string.hex_copy_md5),
                getString(R.string.hex_copy_crc32)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_copy_as))
                .setItems(formats, (d, w) -> {
                    String value = formatBytesAs(data, w);
                    copyToClipboard(value);
                    Extensions.showMessage(this, getString(R.string.hex_copied_n, data.length));
                })
                .show();
    }

    private static String formatBytesAs(byte[] data, int format) {
        switch (format) {
            case 0: { // Hex text
                StringBuilder sb = new StringBuilder(data.length * 2);
                for (byte b : data) sb.append(String.format(Locale.US, "%02X", b));
                return sb.toString();
            }
            case 1: { // Decimal text
                StringBuilder sb = new StringBuilder(data.length * 4);
                for (int i = 0; i < data.length; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(data[i] & 0xFF);
                }
                return sb.toString();
            }
            case 2: { // Binary text
                StringBuilder sb = new StringBuilder(data.length * 9);
                for (int i = 0; i < data.length; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(String.format(Locale.US, "%8s", Integer.toBinaryString(data[i] & 0xFF)).replace(' ', '0'));
                }
                return sb.toString();
            }
            case 3: { // ASCII
                StringBuilder sb = new StringBuilder(data.length);
                for (byte b : data) {
                    int v = b & 0xFF;
                    sb.append(v >= 0x20 && v <= 0x7E ? (char) v : '.');
                }
                return sb.toString();
            }
            case 4: // Base64
                return Base64.encodeToString(data, Base64.NO_WRAP);
            case 5: // SHA1
                return digestHex("SHA-1", data);
            case 6: // MD5
                return digestHex("MD5", data);
            case 7: { // CRC32
                CRC32 crc = new CRC32();
                crc.update(data);
                return String.format(Locale.US, "%08X", crc.getValue());
            }
            default:
                return "";
        }
    }

    private static String digestHex(String algorithm, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format(Locale.US, "%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void pasteClipboardHex() {
        if (readOnly) {
            Extensions.showMessage(this, getString(R.string.hex_read_only));
            return;
        }
        byte[] data;
        try {
            data = parsePastedBytes(0, getClipboardText());
        } catch (IllegalArgumentException e) {
            Extensions.showMessage(this, e.getMessage());
            return;
        }
        if (data == null || data.length == 0) {
            Extensions.showMessage(this, getString(R.string.hex_nothing_to_paste));
            return;
        }
        writeBytesAt(menuRangeStart(), data, null);
        clearSelection();
        Extensions.showMessage(this, getString(R.string.hex_pasted_n, data.length));
    }

    /**
     * Removes the menu range from the file entirely: the file shrinks by the
     * deleted byte count and everything after shifts forward. Undoable.
     */
    private void deleteSelectionOrByte() {
        if (readOnly) {
            Extensions.showMessage(this, getString(R.string.hex_read_only));
            return;
        }
        long start = menuRangeStart();
        long count = menuRangeEnd() - start + 1;
        if (count <= 0) return;
        if (count > MAX_MENU_BYTES) {
            Extensions.showMessage(this, getString(R.string.hex_selection_too_large, MAX_MENU_BYTES / (1024 * 1024)));
            return;
        }
        byte[] before = readRange(start, start + count);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete))
                .setMessage(getString(R.string.hex_delete_selection_msg, (int) count))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    splice(start, count, new byte[0]);
                    undoStack.push(new EditOp(start, before, new byte[0]));
                    redoStack.clear();
                    nibbleHigh = true;
                    clearSelection();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    setCursor(Math.min(start, Math.max(0, size - 1)), false);
                    updateStatus();
                })
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("hex", text));
    }

    /**
     * MT-style quick select: start address plus either a length or an end
     * address ("Use Start address + Length" unchecked switches the field),
     * each field interpreted in hex or decimal.
     */
    private void showSelectRangeDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, 0);

        final EditText etStart = new EditText(this);
        etStart.setHint(getString(R.string.hex_start_offset));
        final EditText etLength = new EditText(this);
        etLength.setHint(getString(R.string.hex_length));

        final CheckBox cbStartLen = new CheckBox(this);
        cbStartLen.setText(R.string.hex_use_start_plus_length);
        cbStartLen.setChecked(true);
        final CheckBox cbHex = new CheckBox(this);
        cbHex.setText(R.string.hex_hexadecimal);
        cbHex.setChecked(true);

        container.addView(UiFields.wrap(this, etStart, null, 0));
        container.addView(UiFields.wrap(this, etLength, null, 0));
        container.addView(cbStartLen);
        container.addView(cbHex);

        final boolean[] reformatting = {false};
        etStart.setText(formatOffset(cursorPos, true));
        etLength.setText("1");

        // Reformat both fields when the number base changes so a half-typed
        // address survives the toggle.
        cbHex.setOnCheckedChangeListener((b, hex) -> {
            if (reformatting[0]) return;
            try {
                long start = parseOffset(etStart.getText().toString(), !hex);
                long len = parseOffset(etLength.getText().toString(), !hex);
                reformatting[0] = true;
                etStart.setText(formatOffset(start, hex));
                etLength.setText(formatOffset(len, hex));
                reformatting[0] = false;
            } catch (Exception ignored) {
            }
        });

        // Switching between length and end converts the value so the visible
        // range stays the same (start=100, len=8 becomes end=107).
        cbStartLen.setOnCheckedChangeListener((b, useLength) -> {
            if (reformatting[0]) return;
            try {
                boolean hex = cbHex.isChecked();
                long start = parseOffset(etStart.getText().toString(), hex);
                long value = parseOffset(etLength.getText().toString(), hex);
                long converted = useLength ? value - start + 1 : start + value - 1;
                if (converted < 0) converted = 0;
                reformatting[0] = true;
                etLength.setText(formatOffset(converted, hex));
                etLength.setHint(getString(useLength ? R.string.hex_length : R.string.hex_end_offset));
                reformatting[0] = false;
            } catch (Exception ignored) {
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_select_range))
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        boolean hex = cbHex.isChecked();
                        long start = parseOffset(etStart.getText().toString(), hex);
                        long end = cbStartLen.isChecked()
                                ? start + parseOffset(etLength.getText().toString(), hex) - 1
                                : parseOffset(etLength.getText().toString(), hex);
                        if (start < 0 || end < start) {
                            Extensions.showMessage(this, getString(R.string.hex_invalid_range));
                            return;
                        }
                        setSelectionRange(start, end);
                        setCursor(start, true);
                    } catch (Exception e) {
                        Extensions.showMessage(this, getString(R.string.hex_invalid_range));
                    }
                })
                .show();
    }

    private static String formatOffset(long value, boolean hex) {
        return hex ? String.format(Locale.US, "%X", value) : String.valueOf(value);
    }

    private static long parseOffset(String text, boolean hex) throws NumberFormatException {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new NumberFormatException("empty");
        return hex ? Long.parseLong(t, 16) : Long.parseLong(t);
    }

    private void updateStatus() {
        int val = readByte(cursorPos);
        String text = String.format(Locale.US, "Val: %02Xh  Pos: %Xh  Size: %Xh [HEX]",
                val == -1 ? 0 : val, cursorPos, size);
        if (selStart >= 0) {
            text += String.format(Locale.US, "  Sel: %Xh-%Xh (%d)", selStart, selEnd, selEnd - selStart + 1);
        }
        statusText.setText(text);
        updateInspector();
    }

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

    private static long unsigned(int[] b, int off, int len, boolean bigEndian) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            int by = b[off + i];
            if (by < 0) return v;
            v |= (by & 0xFFL) << (8 * (bigEndian ? len - 1 - i : i));
        }
        return v;
    }

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

    private boolean ensureSearchPattern() {
        lastPattern = null;
        try {
            lastPattern = buildPattern(searchTypeIndex, searchValue.getText().toString(), searchBigEndian.isChecked());
            return true;
        } catch (Exception e) {
            Extensions.showMessage(this, getString(R.string.hex_invalid_search, DATA_TYPES.get(searchTypeIndex)));
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
                    setCursor(f, true);
                } else {
                    Extensions.showMessage(this, getString(R.string.hex_not_found));
                }
            });
        }).start();
    }

    private boolean matchesAt(long pos, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++)
            if (readByte(pos + i) != (pattern[i] & 0xFF)) return false;
        return true;
    }

    private void replaceCurrentMatch() {
        if (!ensureSearchPattern()) return;
        long target = lastMatchPos >= 0 ? lastMatchPos : cursorPos;
        if (target + lastPattern.length > size) {
            Extensions.showMessage(this, getString(R.string.hex_not_found));
            return;
        }
        byte[] repl;
        try {
            repl = buildPattern(replaceTypeIndex, replaceValue.getText().toString(), replaceBigEndian.isChecked());
        } catch (Exception e) {
            Extensions.showMessage(this, getString(R.string.hex_invalid_replace, DATA_TYPES.get(replaceTypeIndex)));
            return;
        }
        writeBytesAt(target, repl, null);
        lastMatchPos = target;
        findNext();
    }

    /**
     * Replaces the bytes starting at {@code pos} with {@code data} of any length:
     * shorter data shrinks the file, longer data grows it. The change is undoable
     * as a single operation.
     */
    private void writeBytesAt(long pos, byte[] data, Runnable extraUndoAction) {
        if (data.length == 0) return;
        pos = Math.max(0, Math.min(pos, size));
        byte[] before = readRange(pos, pos + data.length);
        splice(pos, before.length, data);
        undoStack.push(new EditOp(pos, before, data.clone()));
        redoStack.clear();
        nibbleHigh = true;
        if (adapter != null) {
            if (before.length != data.length) {
                adapter.notifyDataSetChanged();
            } else {
                int firstRow = (int) (pos / 8);
                int lastRow = (int) ((pos + Math.max(1, data.length) - 1) / 8);
                for (int r = firstRow; r <= lastRow && r < adapter.getItemCount(); r++) adapter.notifyItemChanged(r);
            }
        }
        setCursor(Math.min(pos + data.length, Math.max(0, size - 1)), false);
        updateStatus();
    }

    private void showPasteFromDialog() {
        if (readOnly) {
            Extensions.showMessage(this, getString(R.string.hex_read_only));
            return;
        }
        String[] options = {getString(R.string.hex_paste_hex), getString(R.string.hex_paste_dec), getString(R.string.hex_paste_bin),
                getString(R.string.hex_paste_ascii), getString(R.string.hex_paste_b64)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_paste_from))
                .setItems(options, (d, w) -> promptPasteInput(w))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptPasteInput(int formatIndex) {
        String[] formats = {"hex", "decimal", "binary", "ASCII", "Base64"};
        EditText input = new EditText(this);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setText(getClipboardText());
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_paste_from_x, formats[formatIndex]))
                .setView(UiFields.wrap(this, input, getString(R.string.hex_paste_bytes), 16))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        byte[] data = parsePastedBytes(formatIndex, input.getText().toString());
                        if (data == null || data.length == 0) {
                            Extensions.showMessage(this, getString(R.string.hex_nothing_to_paste));
                            return;
                        }
                        writeBytesAt(cursorPos, data, null);
                        Extensions.showMessage(this, getString(R.string.hex_pasted_n, data.length));
                    } catch (IllegalArgumentException e) {
                        Extensions.showMessage(this, e.getMessage());
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
                if (hex.length() % 2 != 0) throw new IllegalArgumentException(getString(R.string.hex_even));
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
                    throw new IllegalArgumentException(getString(R.string.hex_bin8));
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

    private static byte[] toBytes(String upperHex) {
        byte[] out = new byte[upperHex.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(upperHex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }


    private void saveChanges() {
        saveChanges(null);
    }

    private void saveChanges(Runnable onSaved) {
        if (!hasUnsavedChanges()) {
            if (onSaved != null) onSaved.run();
            return;
        }
        if (readOnly) {
            if (onSaved == null) Extensions.showMessage(this, getString(R.string.hex_read_only));
            else finish();
            return;
        }
        if (RootStaging.needsWriteConfirm(rootOriginalPath)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.editor_write_system))
                    .setMessage(getString(R.string.editor_write_system_msg, rootOriginalPath))
                    .setPositiveButton(android.R.string.ok, (d, w) -> saveChangesRoot(onSaved))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        saveChangesRoot(onSaved);
    }

    private boolean hasUnsavedChanges() {
        return !mods.isEmpty() || structural;
    }

    private void saveChangesRoot(Runnable onSaved) {
        backupForSave();
        try {
            if (structural) {
                rebuildFileFromChunks();
            } else {
                try (RandomAccessFile w = new RandomAccessFile(file, "rw")) {
                    for (Map.Entry<Long, Integer> entry : mods.entrySet()) {
                        w.seek(entry.getKey());
                        w.writeByte(entry.getValue());
                    }
                }
            }
            originalSize = structural ? size : originalSize;
            resetChunks();
            undoStack.clear();
            redoStack.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            updateStatus();
            Extensions.showMessage(this, R.string.saved);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
            return;
        }
        if (rootOriginalPath != null) {
            Extensions.showMessage(this, getString(R.string.editor_writing_root));
            new Thread(() -> {
                try {
                    RootStaging.writeBack(this, file, rootOriginalPath);
                    runOnUiThread(() -> Extensions.showMessage(this, getString(R.string.editor_saved_root)));
                } catch (Exception e) {
                    runOnUiThread(() -> new ErrorUtil(this).showError(e));
                }
            }).start();
        }
        if (onSaved != null) onSaved.run();
    }

    /**
     * Streams the chunk model into a fresh file and swaps it in, so a crash
     * mid-write cannot corrupt the original. Only used once a length-changing
     * edit happened; plain byte edits keep the fast in-place save path.
     */
    private void rebuildFileFromChunks() throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp_hex");
        try (RandomAccessFile out = new RandomAccessFile(temp, "rw")) {
            out.setLength(0);
            byte[] buf = new byte[64 * 1024];
            for (Chunk c : chunks) {
                if (c.memory) {
                    out.write(c.data);
                    continue;
                }
                long remaining = c.length;
                long src = c.origOffset;
                while (remaining > 0) {
                    int n;
                    synchronized (raf) {
                        raf.seek(src);
                        n = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                    }
                    if (n <= 0) throw new IOException("Unexpected end of source file");
                    out.write(buf, 0, n);
                    src += n;
                    remaining -= n;
                }
            }
        }
        if (!temp.renameTo(file)) {
            try (InputStream in = new FileInputStream(temp);
                 RandomAccessFile out = new RandomAccessFile(file, "rw")) {
                out.setLength(0);
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            temp.delete();
        }
        try {
            raf.close();
        } catch (IOException ignored) {
        }
        raf = new RandomAccessFile(file, "rw");
        size = raf.length();
        originalSize = size;
    }

    private void backupForSave() {
        try {
            if (!UiPrefs.genBackup(this)) return;
            if (rootOriginalPath != null) {
                if (AccessManager.fileOpsOn(this) && AccessManager.exists(this, rootOriginalPath)) {
                    if (!AccessManager.exists(this, rootOriginalPath + ".bak")) {
                        AccessManager.copyFile(this, rootOriginalPath, rootOriginalPath + ".bak", true);
                        AccessManager.preserveTime(this, rootOriginalPath, rootOriginalPath + ".bak");
                    }
                }
            } else if (file != null && file.isFile()) {
                FileUtils.backupOnce(file);
            }
        } catch (Exception ignored) {
        }
    }

    private void revertChanges() {
        if (!hasUnsavedChanges()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_revert))
                .setMessage(getString(R.string.hex_revert_msg))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    resetChunks();
                    undoStack.clear();
                    redoStack.clear();
                    nibbleHigh = true;
                    clearSelection();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    setCursor(Math.min(cursorPos, Math.max(0, size - 1)), false);
                    updateStatus();
                }).show();
    }

    private void showGotoDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.hex_offset_hint));
        input.setText(String.format(Locale.US, "%X", cursorPos));
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_go_offset))
                .setView(UiFields.wrap(this, input, null, 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        long target = Long.parseLong(input.getText().toString().trim(), 16);
                        setCursor(Math.min(target, Math.max(0, size - 1)), true);
                    } catch (NumberFormatException ignored) {
                        Extensions.showMessage(this, getString(R.string.hex_invalid_offset));
                    }
                }).show();
    }

    private void confirmDiscardAndFinish() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.hex_unsaved))
                .setMessage(getString(R.string.hex_discard_unsaved))
                .setNegativeButton(R.string.minimize, (d, w) ->
                        MinimizedEditors.minimize(this, R.string.hex_editor,
                                file != null ? file.getName() : null))
                .setPositiveButton(R.string.save, (d, w) -> saveChanges(this::finish))
                .setNeutralButton(R.string.delete, (d, w) -> finish())
                .show();
    }

    /** Shared by the toolbar up-arrow and the system back gesture. */
    private void handleBack() {
        onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        handleBack();
        return true;
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
        dragHandler.removeCallbacks(dragAutoScroll);
        if (raf != null) try {
            raf.close();
        } catch (IOException ignored) {
        }
    }

    private int getPrimaryColor() {
        return MaterialColors.getColor(this, android.R.attr.colorPrimary, Color.BLUE);
    }

    private int getOnPrimaryColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
    }

    private int getPrimaryContainerColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY);
    }

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
            long rowStart = position * 8L;
            int[] values = new int[8];
            for (int i = 0; i < 8; i++) values[i] = readByte(rowStart + i);

            holder.offsetCell.setText(String.format(Locale.US, "%04X", rowStart & 0xFFFF));

            long selCol = cursorPos - rowStart;
            for (int i = 0; i < 8; i++) {
                TextView cell = holder.cells[i];
                // Tag every cell (even past EOF) so hit-testing during a
                // selection drag never reads a recycled tag from another row.
                cell.setTag(rowStart + i);
                cell.setOnTouchListener(cellTouchListener);
                if (values[i] == -1) {
                    cell.setText("");
                    cell.setBackground(null);
                    continue;
                }
                cell.setText(String.format(Locale.US, "%02X", values[i]));
                if (i == selCol) {
                    cell.setBackgroundColor(getPrimaryContainerColor());
                } else if (isByteSelected(rowStart + i)) {
                    cell.setBackgroundColor(getSelectionColor());
                } else {
                    cell.setBackground(null);
                }
            }

            StringBuilder asciiBuilder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                int v = values[i];
                asciiBuilder.append(v >= 0x20 && v <= 0x7E ? (char) v : '.');
            }
            SpannableString ascii = new SpannableString(asciiBuilder.toString());
            // Selection spans first so the cursor span (set below) stays on top
            // where both cover the same character.
            if (selStart >= 0 && selEnd >= rowStart && selStart <= rowStart + 7) {
                int from = (int) Math.max(0, selStart - rowStart);
                int to = (int) Math.min(8, selEnd - rowStart + 1);
                ascii.setSpan(new BackgroundColorSpan(getSelectionColor()), from, to, 0);
                ascii.setSpan(new ForegroundColorSpan(getOnPrimaryColor()), from, to, 0);
            }
            if (selCol >= 0 && selCol < 8) {
                ascii.setSpan(new BackgroundColorSpan(getPrimaryColor()), (int) selCol, (int) selCol + 1, 0);
                ascii.setSpan(new ForegroundColorSpan(getOnPrimaryColor()), (int) selCol, (int) selCol + 1, 0);
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
