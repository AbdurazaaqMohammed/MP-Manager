package io.github.abdurazaaqmohammed.utils;

import android.util.Base64;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AbxUtil {

    private static final byte[] MAGIC = {0x41, 0x42, 0x58, 0x00};

    private static final int CMD_START_DOCUMENT = 0x00;
    private static final int CMD_END_DOCUMENT = 0x01;
    private static final int CMD_START_TAG = 0x02;
    private static final int CMD_END_TAG = 0x03;
    private static final int CMD_TEXT = 0x04;
    private static final int CMD_CDSECT = 0x05;
    private static final int CMD_ENTITY_REF = 0x06;
    private static final int CMD_IGNORABLE_WHITESPACE = 0x07;
    private static final int CMD_PROCESSING_INSTRUCTION = 0x08;
    private static final int CMD_COMMENT = 0x09;
    private static final int CMD_DOCDECL = 0x0A;
    private static final int CMD_ATTRIBUTE = 0x0F;

    private static final int TYPE_NULL = 0x10;
    private static final int TYPE_STRING = 0x20;
    private static final int TYPE_STRING_INTERNED = 0x30;
    private static final int TYPE_BYTES_HEX = 0x40;
    private static final int TYPE_BYTES_BASE64 = 0x50;
    private static final int TYPE_INT = 0x60;
    private static final int TYPE_INT_HEX = 0x70;
    private static final int TYPE_LONG = 0x80;
    private static final int TYPE_LONG_HEX = 0x90;
    private static final int TYPE_FLOAT = 0xA0;
    private static final int TYPE_DOUBLE = 0xB0;
    private static final int TYPE_BOOLEAN_TRUE = 0xC0;
    private static final int TYPE_BOOLEAN_FALSE = 0xD0;

    private static final int INTERNED_NEW = 0xFFFF;
    private static final int MAX_INTERNED = 0xFFFE;
    private static final int MAX_UTF_LEN = 0xFFFF;

    private AbxUtil() {
    }

    public static boolean isAbxStream(InputStream in) {
        if (in == null) return false;
        try {
            if (!in.markSupported()) return false;
            in.mark(8);
            byte[] magic = new byte[4];
            int read = 0;
            while (read < 4) {
                int n = in.read(magic, read, 4 - read);
                if (n == -1) break;
                read += n;
            }
            in.reset();
            return read == 4 && magic[0] == MAGIC[0] && magic[1] == MAGIC[1]
                    && magic[2] == MAGIC[2] && magic[3] == MAGIC[3];
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isAbx(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream is = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = 0;
            while (read < 4) {
                int n = is.read(magic, read, 4 - read);
                if (n == -1) break;
                read += n;
            }
            return read == 4 && magic[0] == MAGIC[0] && magic[1] == MAGIC[1]
                    && magic[2] == MAGIC[2] && magic[3] == MAGIC[3];
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String decodeToString(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return decodeToString(is);
        }
    }

    public static String decodeToString(byte[] bytes) throws IOException {
        return decodeToString(new ByteArrayInputStream(bytes));
    }

    public static String decodeToString(InputStream in) throws IOException {
        DataInputStream data = in instanceof DataInputStream
                ? (DataInputStream) in : new DataInputStream(in);
        byte[] magic = new byte[4];
        try {
            data.readFully(magic);
        } catch (EOFException e) {
            throw new IOException("Not ABX: empty file");
        }
        if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
            throw new IOException("Not ABX: bad magic");
        }
        List<String> pool = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        List<String> stack = new ArrayList<>();
        List<Boolean> tagOpen = new ArrayList<>();
        List<Boolean> hasElement = new ArrayList<>();
        int pending = -1;
        boolean empty = true;
        while (true) {
            int token;
            if (pending >= 0) {
                token = pending;
                pending = -1;
            } else {
                try {
                    token = data.readUnsignedByte();
                } catch (EOFException e) {
                    break;
                }
            }
            int cmd = token & 0x0F;
            int type = token & 0xF0;
            if (cmd == CMD_END_DOCUMENT) {
                break;
            } else if (cmd == CMD_START_DOCUMENT) {
                skipPayload(data, pool, type);
            } else if (cmd == CMD_START_TAG) {
                String name = readName(data, pool, type);
                if (!stack.isEmpty()) {
                    int top = stack.size() - 1;
                    if (tagOpen.get(top)) {
                        out.append(">\n");
                        tagOpen.set(top, false);
                    } else {
                        newLineIfNeeded(out);
                    }
                    hasElement.set(top, true);
                }
                indent(out, stack.size());
                out.append('<').append(name);
                while (true) {
                    int attrToken;
                    try {
                        attrToken = data.readUnsignedByte();
                    } catch (EOFException e) {
                        throw new IOException("Truncated ABX inside <" + name + ">");
                    }
                    if ((attrToken & 0x0F) != CMD_ATTRIBUTE) {
                        pending = attrToken;
                        break;
                    }
                    String attrName = readInterned(data, pool);
                    String attrValue = readTypedValue(data, pool, attrToken & 0xF0);
                    out.append(' ').append(attrName).append("=\"")
                            .append(escape(attrValue == null ? "" : attrValue, true)).append('"');
                }
                stack.add(name);
                tagOpen.add(true);
                hasElement.add(false);
                empty = false;
            } else if (cmd == CMD_END_TAG) {
                String name = readName(data, pool, type);
                if (stack.isEmpty()) throw new IOException("Unbalanced ABX: </" + name + "> with empty stack");
                int top = stack.size() - 1;
                stack.remove(top);
                boolean wasOpen = tagOpen.remove(top);
                boolean hadElement = hasElement.remove(top);
                if (wasOpen) {
                    out.append("/>\n");
                } else if (hadElement) {
                    newLineIfNeeded(out);
                    indent(out, stack.size());
                    out.append("</").append(name).append(">\n");
                } else {
                    out.append("</").append(name).append(">\n");
                }
            } else if (cmd == CMD_TEXT || cmd == CMD_CDSECT || cmd == CMD_IGNORABLE_WHITESPACE) {
                String text = readTypedValue(data, pool, type);
                if (text == null) text = "";
                if (cmd == CMD_IGNORABLE_WHITESPACE && text.trim().isEmpty()) continue;
                if (!stack.isEmpty()) {
                    int top = stack.size() - 1;
                    if (tagOpen.get(top)) {
                        out.append('>');
                        tagOpen.set(top, false);
                    }
                }
                out.append(escape(text, false));
                empty = false;
            } else if (cmd == CMD_COMMENT) {
                String text = readTypedValue(data, pool, type);
                if (text == null) text = "";
                if (!stack.isEmpty()) {
                    int top = stack.size() - 1;
                    if (tagOpen.get(top)) {
                        out.append(">\n");
                        tagOpen.set(top, false);
                    } else {
                        newLineIfNeeded(out);
                    }
                    hasElement.set(top, true);
                }
                indent(out, stack.size());
                out.append("<!--").append(text.replace("--", "–")).append("-->\n");
            } else if (cmd == CMD_PROCESSING_INSTRUCTION) {
                String text = readTypedValue(data, pool, type);
                if (text == null) text = "";
                if (!stack.isEmpty()) {
                    int top = stack.size() - 1;
                    if (tagOpen.get(top)) {
                        out.append(">\n");
                        tagOpen.set(top, false);
                    } else {
                        newLineIfNeeded(out);
                    }
                    hasElement.set(top, true);
                }
                indent(out, stack.size());
                out.append("<?").append(text).append("?>\n");
            } else {
                skipPayload(data, pool, type);
            }
        }
        if (!stack.isEmpty()) throw new IOException("Truncated ABX: " + stack.size() + " unclosed tag(s)");
        if (empty) throw new IOException("Empty ABX document");
        return out.toString();
    }

    private static void skipPayload(DataInputStream data, List<String> pool, int type) throws IOException {
        readTypedValue(data, pool, type);
    }

    private static String readName(DataInputStream data, List<String> pool, int type) throws IOException {
        if (type == TYPE_STRING_INTERNED || type == TYPE_NULL) {
            if (type == TYPE_NULL) return "";
            return readInterned(data, pool);
        }
        if (type == TYPE_STRING) return readString(data);
        String v = readTypedValue(data, pool, type);
        return v == null ? "" : v;
    }

    private static String readTypedValue(DataInputStream data, List<String> pool, int type) throws IOException {
        switch (type) {
            case TYPE_NULL:
                return null;
            case TYPE_STRING:
                return readString(data);
            case TYPE_STRING_INTERNED:
                return readInterned(data, pool);
            case TYPE_INT:
                return Integer.toString(data.readInt());
            case TYPE_INT_HEX:
                return Integer.toString(data.readInt(), 16);
            case TYPE_LONG:
                return Long.toString(data.readLong());
            case TYPE_LONG_HEX:
                return Long.toString(data.readLong(), 16);
            case TYPE_FLOAT: {
                float f = Float.intBitsToFloat(data.readInt());
                if (Float.isNaN(f)) return "NaN";
                if (f == Float.POSITIVE_INFINITY) return "Infinity";
                if (f == Float.NEGATIVE_INFINITY) return "-Infinity";
                return Float.toString(f);
            }
            case TYPE_DOUBLE: {
                double d = Double.longBitsToDouble(data.readLong());
                if (Double.isNaN(d)) return "NaN";
                if (d == Double.POSITIVE_INFINITY) return "Infinity";
                if (d == Double.NEGATIVE_INFINITY) return "-Infinity";
                return Double.toString(d);
            }
            case TYPE_BOOLEAN_TRUE:
                return "true";
            case TYPE_BOOLEAN_FALSE:
                return "false";
            case TYPE_BYTES_HEX:
                return toHex(readBytes(data));
            case TYPE_BYTES_BASE64:
                return Base64.encodeToString(readBytes(data), Base64.NO_WRAP);
            default:
                throw new IOException(String.format("Unknown ABX type nibble 0x%02X", type));
        }
    }

    private static byte[] readBytes(DataInputStream data) throws IOException {
        int len = data.readUnsignedShort();
        byte[] buf = new byte[len];
        data.readFully(buf);
        return buf;
    }

    private static String readString(DataInputStream data) throws IOException {
        return new String(readBytes(data), StandardCharsets.UTF_8);
    }

    private static String readInterned(DataInputStream data, List<String> pool) throws IOException {
        int id = data.readUnsignedShort();
        if (id == INTERNED_NEW) {
            String s = readString(data);
            if (pool.size() < MAX_INTERNED) pool.add(s);
            return s;
        }
        if (id < 0 || id >= pool.size()) throw new IOException("Bad ABX string id " + id);
        return pool.get(id);
    }

    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(hex[(b >> 4) & 0xF]).append(hex[b & 0xF]);
        }
        return sb.toString();
    }

    private static String escape(String s, boolean attr) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append(attr ? "&quot;" : "\""); break;
                case '\'': sb.append(attr ? "&apos;" : "'"); break;
                case '\t': sb.append(attr ? "&#9;" : "\t"); break;
                case '\n': sb.append(attr ? "&#10;" : "\n"); break;
                case '\r': sb.append(attr ? "&#13;" : "\r"); break;
                default:
                    if (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D) sb.append('�');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("  ");
    }

    private static void newLineIfNeeded(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
    }

    public static byte[] encodeString(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bos);
        data.write(MAGIC);
        Map<String, Integer> pool = new LinkedHashMap<>();
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_DOCUMENT) {
                data.writeByte(TYPE_NULL | CMD_START_DOCUMENT);
            } else if (event == XmlPullParser.START_TAG) {
                data.writeByte(TYPE_STRING_INTERNED | CMD_START_TAG);
                writeInterned(data, pool, parser.getName());
                int n = parser.getAttributeCount();
                for (int i = 0; i < n; i++) {
                    data.writeByte(TYPE_STRING | CMD_ATTRIBUTE);
                    writeInterned(data, pool, parser.getAttributeName(i));
                    writeString(data, parser.getAttributeValue(i));
                }
            } else if (event == XmlPullParser.TEXT) {
                String text = parser.getText();
                if (text != null && !text.trim().isEmpty()) {
                    data.writeByte(TYPE_STRING | CMD_TEXT);
                    writeString(data, text);
                }
            } else if (event == XmlPullParser.CDSECT) {
                String text = parser.getText();
                if (text != null && !text.isEmpty()) {
                    data.writeByte(TYPE_STRING | CMD_CDSECT);
                    writeString(data, text);
                }
            } else if (event == XmlPullParser.COMMENT) {
                String text = parser.getText();
                if (text != null) {
                    data.writeByte(TYPE_STRING | CMD_COMMENT);
                    writeString(data, text);
                }
            } else if (event == XmlPullParser.END_TAG) {
                data.writeByte(TYPE_STRING_INTERNED | CMD_END_TAG);
                writeInterned(data, pool, parser.getName());
            } else if (event == XmlPullParser.PROCESSING_INSTRUCTION) {
                String text = parser.getText();
                if (text != null) {
                    data.writeByte(TYPE_STRING | CMD_PROCESSING_INSTRUCTION);
                    writeString(data, text);
                }
            }
            event = parser.next();
        }
        data.writeByte(TYPE_NULL | CMD_END_DOCUMENT);
        data.flush();
        return bos.toByteArray();
    }

    private static void writeString(DataOutputStream data, String s) throws IOException {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_UTF_LEN) throw new IOException("ABX string too long (" + bytes.length + " bytes)");
        data.writeShort(bytes.length);
        data.write(bytes);
    }

    private static void writeInterned(DataOutputStream data, Map<String, Integer> pool, String s) throws IOException {
        if (s == null) s = "";
        Integer id = pool.get(s);
        if (id != null) {
            data.writeShort(id);
            return;
        }
        if (pool.size() >= MAX_INTERNED) {
            data.writeShort(INTERNED_NEW);
            writeString(data, s);
            return;
        }
        pool.put(s, pool.size());
        data.writeShort(INTERNED_NEW);
        writeString(data, s);
    }
}
