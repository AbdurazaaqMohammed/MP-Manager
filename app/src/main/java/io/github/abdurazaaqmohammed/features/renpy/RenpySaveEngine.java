package io.github.abdurazaaqmohammed.features.renpy;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 100% Pure Java Engine for Ren'Py save (.save) and persistent files.
 * Zero external Python dependencies, zero process spawning, zero permission issues on Android.
 * Features:
 * - Nested object tree traversal matching saveeditonline.com (e.g. "word > sPoint", "unsorted_pointlist > sayori").
 * - Interactive raw JSON export & import.
 * - In-place safe binary patching.
 * - Automatic gallery & CG unlocker.
 */
public final class RenpySaveEngine {

    private RenpySaveEngine() {}

    public static class RenpyVariable implements Serializable {
        public final String rawKey;
        public final String displayName;
        public final String type;
        public Object value;

        public RenpyVariable(String rawKey, String displayName, String type, Object value) {
            this.rawKey = rawKey;
            this.displayName = displayName;
            this.type = type;
            this.value = value;
        }

        public String getDisplayValue() {
            if (value == null) return "None";
            if (value instanceof String) return "\"" + value + "\"";
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (d == (long) d) return String.valueOf((long) d);
            }
            return String.valueOf(value);
        }
    }

    public static class ParseResult {
        public final boolean isSave;
        public final List<RenpyVariable> variables;

        public ParseResult(boolean isSave, List<RenpyVariable> variables) {
            this.isSave = isSave;
            this.variables = variables;
        }
    }

    public static boolean isZipFile(File file) {
        if (file == null || !file.exists() || file.length() < 22) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            return read == 4 && header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] decompressZlib(byte[] input) throws Exception {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length * 3);
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count > 0) {
                    baos.write(buf, 0, count);
                } else {
                    if (inflater.needsInput()) break;
                }
            }
            return baos.toByteArray();
        } finally {
            inflater.end();
        }
    }

    public static byte[] compressZlib(byte[] input) throws Exception {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] readFileBytes(File file) throws Exception {
        long len = file.length();
        if (len > 150 * 1024 * 1024) throw new IllegalStateException("File too large (" + len + " bytes)");
        byte[] bytes = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = fis.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        return bytes;
    }

    public static byte[] extractPickleBytes(File targetFile, boolean isSave) throws Exception {
        if (isSave) {
            try (ZipFile zf = new ZipFile(targetFile)) {
                ZipEntry logEntry = zf.getEntry("log");
                if (logEntry == null) throw new IllegalStateException("Not a valid Ren'Py save: 'log' entry missing");
                try (InputStream is = zf.getInputStream(logEntry);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream((int) logEntry.getSize())) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    return baos.toByteArray();
                }
            }
        } else {
            byte[] raw = readFileBytes(targetFile);
            return decompressZlib(raw);
        }
    }

    /**
     * Light dynamic representation of arbitrary Python Pickle object or dictionary.
     */
    public static class PObject extends HashMap<String, Object> {
        public final String className;
        public PObject(String className) {
            this.className = className;
        }
    }

    /**
     * Pure Java implementation of Python Pickle Protocol 2 Stack Machine Unpickler.
     */
    public static Object unpickle(byte[] data) {
        Stack<Object> stack = new Stack<>();
        Map<Integer, Object> memo = new HashMap<>();
        Stack<Integer> marks = new Stack<>();
        int pos = 0;
        int n = data.length;

        while (pos < n) {
            int code = data[pos++] & 0xFF;

            if (code == 0x80) { // PROTO
                pos++;
            } else if (code == 'c') { // GLOBAL
                int nl1 = findByte(data, (byte) '\n', pos);
                int nl2 = findByte(data, (byte) '\n', nl1 + 1);
                String module = new String(data, pos, nl1 - pos, StandardCharsets.ISO_8859_1);
                String name = new String(data, nl1 + 1, nl2 - (nl1 + 1), StandardCharsets.ISO_8859_1);
                pos = nl2 + 1;
                stack.push(module + "." + name);
            } else if (code == ')') { // EMPTY_TUPLE
                stack.push(new ArrayList<>());
            } else if (code == ']') { // EMPTY_LIST
                stack.push(new ArrayList<>());
            } else if (code == '}') { // EMPTY_DICT
                stack.push(new PObject("dict"));
            } else if (code == 0x81) { // NEWOBJ
                Object args = stack.isEmpty() ? null : stack.pop();
                Object cls = stack.isEmpty() ? "" : stack.pop();
                stack.push(new PObject(String.valueOf(cls)));
            } else if (code == '(') { // MARK
                marks.push(stack.size());
            } else if (code == 'q') { // BINPUT
                if (pos < n) memo.put(data[pos++] & 0xFF, stack.isEmpty() ? null : stack.peek());
            } else if (code == 'r') { // LONG_BINPUT
                if (pos + 4 <= n) {
                    int idx = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    pos += 4;
                    memo.put(idx, stack.isEmpty() ? null : stack.peek());
                }
            } else if (code == 'h') { // BINGET
                if (pos < n) stack.push(memo.get(data[pos++] & 0xFF));
            } else if (code == 'j') { // LONG_BINGET
                if (pos + 4 <= n) {
                    int idx = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    pos += 4;
                    stack.push(memo.get(idx));
                }
            } else if (code == 'N') { // NONE
                stack.push(null);
            } else if (code == 0x88) { // NEWTRUE
                stack.push(Boolean.TRUE);
            } else if (code == 0x89) { // NEWFALSE
                stack.push(Boolean.FALSE);
            } else if (code == 'K') { // BININT1
                if (pos < n) stack.push((long) (data[pos++] & 0xFF));
            } else if (code == 'M') { // BININT2
                if (pos + 2 <= n) {
                    stack.push((long) (ByteBuffer.wrap(data, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF));
                    pos += 2;
                }
            } else if (code == 'J') { // BININT
                if (pos + 4 <= n) {
                    stack.push((long) ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
                    pos += 4;
                }
            } else if (code == 'G') { // BINFLOAT
                if (pos + 8 <= n) {
                    stack.push(ByteBuffer.wrap(data, pos, 8).order(ByteOrder.BIG_ENDIAN).getDouble());
                    pos += 8;
                }
            } else if (code == 'U') { // SHORT_BINSTRING
                if (pos < n) {
                    int l = data[pos++] & 0xFF;
                    if (pos + l <= n) {
                        stack.push(new String(data, pos, l, StandardCharsets.ISO_8859_1));
                        pos += l;
                    }
                }
            } else if (code == 'X') { // BINUNICODE
                if (pos + 4 <= n) {
                    int l = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    pos += 4;
                    if (l >= 0 && pos + l <= n) {
                        stack.push(new String(data, pos, l, StandardCharsets.UTF_8));
                        pos += l;
                    }
                }
            } else if (code == 'T') { // BINSTRING
                if (pos + 4 <= n) {
                    int l = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    pos += 4;
                    if (l >= 0 && pos + l <= n) {
                        stack.push(new String(data, pos, l, StandardCharsets.ISO_8859_1));
                        pos += l;
                    }
                }
            } else if (code == 't') { // TUPLE
                int k = marks.isEmpty() ? 0 : marks.pop();
                List<Object> tuple = new ArrayList<>();
                while (stack.size() > k) {
                    tuple.add(0, stack.pop());
                }
                stack.push(tuple);
            } else if (code == 0x85) { // TUPLE1
                Object top = stack.isEmpty() ? null : stack.pop();
                List<Object> list = new ArrayList<>();
                list.add(top);
                stack.push(list);
            } else if (code == 0x86) { // TUPLE2
                Object b = stack.isEmpty() ? null : stack.pop();
                Object a = stack.isEmpty() ? null : stack.pop();
                List<Object> list = new ArrayList<>();
                list.add(a);
                list.add(b);
                stack.push(list);
            } else if (code == 0x87) { // TUPLE3
                Object c = stack.isEmpty() ? null : stack.pop();
                Object b = stack.isEmpty() ? null : stack.pop();
                Object a = stack.isEmpty() ? null : stack.pop();
                List<Object> list = new ArrayList<>();
                list.add(a);
                list.add(b);
                list.add(c);
                stack.push(list);
            } else if (code == 's') { // SETITEM
                Object val = stack.isEmpty() ? null : stack.pop();
                Object key = stack.isEmpty() ? null : stack.pop();
                if (!stack.isEmpty() && stack.peek() instanceof Map) {
                    ((Map<String, Object>) stack.peek()).put(String.valueOf(key), val);
                }
            } else if (code == 'u') { // SETITEMS
                int k = marks.isEmpty() ? 0 : marks.pop();
                List<Object> items = new ArrayList<>();
                while (stack.size() > k) {
                    items.add(0, stack.pop());
                }
                if (!stack.isEmpty() && stack.peek() instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) stack.peek();
                    for (int m = 0; m < items.size() - 1; m += 2) {
                        map.put(String.valueOf(items.get(m)), items.get(m + 1));
                    }
                }
            } else if (code == 'a') { // APPEND
                Object it = stack.isEmpty() ? null : stack.pop();
                if (!stack.isEmpty() && stack.peek() instanceof List) {
                    ((List<Object>) stack.peek()).add(it);
                }
            } else if (code == 'e') { // APPENDS
                int k = marks.isEmpty() ? 0 : marks.pop();
                List<Object> items = new ArrayList<>();
                while (stack.size() > k) {
                    items.add(0, stack.pop());
                }
                if (!stack.isEmpty()) {
                    Object top = stack.peek();
                    if (top instanceof List) {
                        ((List<Object>) top).addAll(items);
                    } else if (top instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) top;
                        for (Object o : items) map.put(String.valueOf(o), Boolean.TRUE);
                    }
                }
            } else if (code == 'b') { // BUILD
                Object state = stack.isEmpty() ? null : stack.pop();
                if (!stack.isEmpty() && stack.peek() instanceof Map) {
                    Map<String, Object> inst = (Map<String, Object>) stack.peek();
                    if (state instanceof Map) {
                        inst.putAll((Map<String, Object>) state);
                    } else if (state instanceof List) {
                        List<?> list = (List<?>) state;
                        for (Object elem : list) {
                            if (elem instanceof Map) inst.putAll((Map<String, Object>) elem);
                        }
                    }
                }
            } else if (code == 0x8F) { // EMPTY_SET
                stack.push(new PObject("set"));
            } else if (code == 0x90) { // ADDITEMS
                int k = marks.isEmpty() ? 0 : marks.pop();
                List<Object> items = new ArrayList<>();
                while (stack.size() > k) {
                    items.add(0, stack.pop());
                }
                if (!stack.isEmpty() && stack.peek() instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) stack.peek();
                    for (Object o : items) map.put(String.valueOf(o), Boolean.TRUE);
                }
            } else if (code == '.') { // STOP
                break;
            }
        }

        return stack.isEmpty() ? null : stack.peek();
    }

    private static int findByte(byte[] data, byte b, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == b) return i;
        }
        return data.length;
    }

    /**
     * Parses variables from the pickle byte stream, matching the deep extraction of saveeditonline.com.
     */
    public static ParseResult parse(Context context, File targetFile) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] data = extractPickleBytes(targetFile, isSave);

        Object rootObj = unpickle(data);
        Map<String, Object> roots;
        if (rootObj instanceof List && !((List<?>) rootObj).isEmpty() && ((List<?>) rootObj).get(0) instanceof Map) {
            roots = (Map<String, Object>) ((List<?>) rootObj).get(0);
        } else if (rootObj instanceof Map) {
            roots = (Map<String, Object>) rootObj;
        } else {
            roots = new HashMap<>();
        }

        Map<String, RenpyVariable> extractedMap = new HashMap<>();
        recursiveExtract(roots, "", isSave, extractedMap, 0);

        List<RenpyVariable> list = new ArrayList<>(extractedMap.values());
        Collections.sort(list, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return new ParseResult(isSave, list);
    }

    private static void recursiveExtract(Map<String, Object> current, String prefix, boolean isSave,
                                         Map<String, RenpyVariable> out, int depth) {
        if (current == null || depth > 3) return;

        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String k = entry.getKey();
            if (k == null || k.isEmpty() || k.startsWith("store._")) continue;

            if (isSave && prefix.isEmpty()) {
                if (!k.startsWith("store.")) continue;
            }

            String clean = (isSave && k.startsWith("store.")) ? k.substring(6) : k;
            if (prefix.isEmpty() && clean.startsWith("_")
                    && !clean.equals("_seen_images") && !clean.equals("_seen_ever") && !clean.equals("_seen_audio")) {
                continue;
            }

            String displayName = prefix.isEmpty() ? clean : (prefix + " > " + clean);
            Object v = entry.getValue();

            if (v instanceof Boolean) {
                out.put(displayName, new RenpyVariable(k, displayName, "bool", v));
            } else if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
                out.put(displayName, new RenpyVariable(k, displayName, "int", ((Number) v).longValue()));
            } else if (v instanceof Double || v instanceof Float) {
                out.put(displayName, new RenpyVariable(k, displayName, "float", ((Number) v).doubleValue()));
            } else if (v instanceof String) {
                out.put(displayName, new RenpyVariable(k, displayName, "str", v));
            } else if (v == null) {
                out.put(displayName, new RenpyVariable(k, displayName, "None", null));
            } else if (v instanceof Map) {
                recursiveExtract((Map<String, Object>) v, displayName, isSave, out, depth + 1);
            }
        }
    }

    /**
     * Converts all extracted variables to clean, formatted JSON.
     */
    public static String exportToJson(List<RenpyVariable> variables) throws Exception {
        JSONObject root = new JSONObject();
        for (RenpyVariable v : variables) {
            root.put(v.displayName, v.value);
        }
        return root.toString(2);
    }

    /**
     * Applies edits from a raw JSON string into modifications map.
     */
    public static Map<String, Object> parseJsonModifications(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        Map<String, Object> mods = new HashMap<>();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = root.isNull(key) ? null : root.get(key);
            mods.put(key, val);
        }
        return mods;
    }

    /**
     * Serializes a primitive/scalar value into Python Pickle Protocol 2 byte sequence.
     */
    public static byte[] serializeValueProto2(Object val) {
        if (val == null) {
            return new byte[]{'N'};
        } else if (val instanceof Boolean) {
            return new byte[]{(byte) (((Boolean) val) ? 0x88 : 0x89)};
        } else if (val instanceof Number) {
            long l = ((Number) val).longValue();
            if (val instanceof Double || val instanceof Float) {
                byte[] b = new byte[9];
                b[0] = 'G';
                ByteBuffer.wrap(b, 1, 8).order(ByteOrder.BIG_ENDIAN).putDouble(((Number) val).doubleValue());
                return b;
            } else if (l >= 0 && l <= 255) {
                return new byte[]{'K', (byte) l};
            } else if (l >= 0 && l <= 65535) {
                byte[] b = new byte[3];
                b[0] = 'M';
                ByteBuffer.wrap(b, 1, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) l);
                return b;
            } else if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                byte[] b = new byte[5];
                b[0] = 'J';
                ByteBuffer.wrap(b, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) l);
                return b;
            } else {
                byte[] sBytes = (l + "L").getBytes(StandardCharsets.US_ASCII);
                byte[] b = new byte[2 + sBytes.length];
                b[0] = 'p';
                b[1] = (byte) sBytes.length;
                System.arraycopy(sBytes, 0, b, 2, sBytes.length);
                return b;
            }
        } else {
            String s = String.valueOf(val);
            byte[] sBytes = s.getBytes(StandardCharsets.UTF_8);
            byte[] b = new byte[5 + sBytes.length];
            b[0] = 'X';
            ByteBuffer.wrap(b, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sBytes.length);
            System.arraycopy(sBytes, 0, b, 5, sBytes.length);
            return b;
        }
    }

    private static class Patch {
        final int start;
        final int end;
        final byte[] newBytes;
        Patch(int start, int end, byte[] newBytes) {
            this.start = start;
            this.end = end;
            this.newBytes = newBytes;
        }
    }

    public static byte[] patchPickleBytes(byte[] rawBytes, Map<String, Object> modifications) {
        if (modifications == null || modifications.isEmpty()) return rawBytes;

        // Build target lookup set: support rawKey, displayName, or sub-key (after ' > ')
        Map<String, Object> lookup = new HashMap<>();
        for (Map.Entry<String, Object> e : modifications.entrySet()) {
            lookup.put(e.getKey(), e.getValue());
            if (e.getKey().contains(" > ")) {
                String sub = e.getKey().substring(e.getKey().lastIndexOf(" > ") + 3);
                lookup.put(sub, e.getValue());
            }
            if (e.getKey().startsWith("store.")) {
                lookup.put(e.getKey().substring(6), e.getValue());
            }
        }

        List<Patch> patches = new ArrayList<>();
        int n = rawBytes.length;
        int i = 0;

        while (i < n - 3) {
            int tag = rawBytes[i] & 0xFF;
            String key = null;

            if (tag == 'U') {
                int slen = rawBytes[i + 1] & 0xFF;
                if (i + 2 + slen <= n) {
                    key = new String(rawBytes, i + 2, slen, StandardCharsets.ISO_8859_1);
                }
                i += 2 + slen;
            } else if (tag == 'X') {
                if (i + 5 <= n) {
                    int slen = ByteBuffer.wrap(rawBytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (slen >= 0 && i + 5 + slen <= n) {
                        key = new String(rawBytes, i + 5, slen, StandardCharsets.UTF_8);
                    }
                    i += 5 + slen;
                } else {
                    i++;
                    continue;
                }
            } else {
                i++;
                continue;
            }

            if (key == null) continue;
            Object newVal = lookup.get(key);
            if (newVal == null && !lookup.containsKey(key)) continue;

            if (i < n && (rawBytes[i] & 0xFF) == 'q') {
                i += 2;
            } else if (i + 4 < n && (rawBytes[i] & 0xFF) == 'r') {
                i += 5;
            }

            if (i >= n) break;

            int valStart = i;
            int valEnd = i;
            int vTag = rawBytes[i] & 0xFF;

            if (vTag == 0x88 || vTag == 0x89 || vTag == 'N') {
                valEnd = i + 1;
            } else if (vTag == 'K') {
                valEnd = i + 2;
            } else if (vTag == 'M') {
                valEnd = i + 3;
            } else if (vTag == 'J') {
                valEnd = i + 5;
            } else if (vTag == 'G') {
                valEnd = i + 9;
            } else if (vTag == 'U') {
                int vlen = rawBytes[i + 1] & 0xFF;
                valEnd = i + 2 + vlen;
            } else if (vTag == 'X') {
                int vlen = ByteBuffer.wrap(rawBytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                valEnd = i + 5 + vlen;
            }

            if (valEnd > valStart && valEnd <= n) {
                byte[] encoded = serializeValueProto2(newVal);
                patches.add(new Patch(valStart, valEnd, encoded));
            }
        }

        Collections.sort(patches, (p1, p2) -> Integer.compare(p1.start, p2.start));

        ByteArrayOutputStream baos = new ByteArrayOutputStream(n + 1024);
        int currentPos = 0;
        for (Patch p : patches) {
            if (p.start > currentPos) {
                baos.write(rawBytes, currentPos, p.start - currentPos);
            }
            baos.write(p.newBytes, 0, p.newBytes.length);
            currentPos = p.end;
        }
        if (currentPos < n) {
            baos.write(rawBytes, currentPos, n - currentPos);
        }

        return baos.toByteArray();
    }

    public static byte[] unlockGalleryBytes(byte[] rawBytes, boolean isSave) {
        byte[] copy = rawBytes.clone();
        int n = copy.length;
        int i = 0;

        while (i < n - 3) {
            int tag = copy[i] & 0xFF;
            String key = null;

            if (tag == 'U') {
                int slen = copy[i + 1] & 0xFF;
                if (i + 2 + slen <= n) {
                    key = new String(copy, i + 2, slen, StandardCharsets.ISO_8859_1);
                }
                i += 2 + slen;
            } else if (tag == 'X') {
                if (i + 5 <= n) {
                    int slen = ByteBuffer.wrap(copy, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (slen >= 0 && i + 5 + slen <= n) {
                        key = new String(copy, i + 5, slen, StandardCharsets.UTF_8);
                    }
                    i += 5 + slen;
                } else {
                    i++;
                    continue;
                }
            } else {
                i++;
                continue;
            }

            if (key == null || key.length() < 2) continue;

            if (i < n && (copy[i] & 0xFF) == 'q') {
                i += 2;
            } else if (i + 4 < n && (copy[i] & 0xFF) == 'r') {
                i += 5;
            }

            if (i >= n) break;

            String lower = key.toLowerCase(Locale.ROOT);
            boolean isGalleryKey = lower.contains("gallery") || lower.contains("clear") || lower.contains("unlock")
                    || lower.contains("ending") || lower.contains("scene") || lower.contains("cg") || lower.contains("seen_")
                    || lower.contains("achievement");

            if (isGalleryKey) {
                int vTag = copy[i] & 0xFF;
                if (vTag == 0x89 || vTag == 'N') {
                    copy[i] = (byte) 0x88;
                } else if (vTag == 'K' && i + 1 < n && copy[i + 1] == 0) {
                    copy[i + 1] = 1;
                }
            }
        }

        for (int j = 1; j < n - 1; j++) {
            if ((copy[j] & 0xFF) == 0x89) {
                int prev = copy[j - 1] & 0xFF;
                int next = copy[j + 1] & 0xFF;
                if ((prev == 0x88 || prev == 0x89 || prev == '(') || (next == 0x88 || next == 0x89 || next == ']' || next == 'e')) {
                    copy[j] = (byte) 0x88;
                }
            }
        }

        return copy;
    }

    public static File unlockGallery(Context context, File targetFile) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] pickleBytes = extractPickleBytes(targetFile, isSave);
        byte[] unlocked = unlockGalleryBytes(pickleBytes, isSave);
        return writeModifiedFile(targetFile, isSave, unlocked);
    }

    public static File saveWithBackup(Context context, File targetFile, Map<String, Object> modifications) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] pickleBytes = extractPickleBytes(targetFile, isSave);
        byte[] patched = patchPickleBytes(pickleBytes, modifications);
        return writeModifiedFile(targetFile, isSave, patched);
    }

    private static File writeModifiedFile(File targetFile, boolean isSave, byte[] newPickleData) throws Exception {
        File bak = new File(targetFile.getParentFile(), targetFile.getName() + ".bak");
        if (bak.exists()) bak.delete();

        try (InputStream in = new FileInputStream(targetFile);
             OutputStream out = new FileOutputStream(bak)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }

        if (isSave) {
            File tmpZip = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
            try (ZipFile zin = new ZipFile(bak);
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmpZip))) {
                Enumeration<? extends ZipEntry> entries = zin.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zout.putNextEntry(newEntry);
                    if ("log".equals(entry.getName())) {
                        zout.write(newPickleData);
                    } else {
                        try (InputStream is = zin.getInputStream(entry)) {
                            byte[] b = new byte[8192];
                            int len;
                            while ((len = is.read(b)) != -1) {
                                zout.write(b, 0, len);
                            }
                        }
                    }
                    zout.closeEntry();
                }
            }
            if (!tmpZip.renameTo(targetFile)) {
                targetFile.delete();
                tmpZip.renameTo(targetFile);
            }
        } else {
            byte[] compressed = compressZlib(newPickleData);
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(compressed);
            }
        }

        return bak;
    }
}
