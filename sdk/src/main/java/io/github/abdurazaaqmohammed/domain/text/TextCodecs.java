package io.github.abdurazaaqmohammed.domain.text;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Text codecs extracted from ToolRunnerActivity branches.
 * Pure logic; no Android dependencies.
 */
public final class TextCodecs {

    private TextCodecs() {
    }

    public static String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String s) throws Exception {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String binaryEncode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) b.append(' ');
            StringBuilder bin = new StringBuilder(Integer.toBinaryString(bytes[i] & 255));
            while (bin.length() < 8) bin.insert(0, "0");
            b.append(bin);
        }
        return b.toString();
    }

    public static String binaryDecode(String s) throws Exception {
        String[] parts = s.trim().split("\\s+");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String part : parts) {
            bos.write(Integer.parseInt(part, 2));
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String caesarShift(String s, int shift) {
        shift = ((shift % 26) + 26) % 26;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                b.append((char) ('a' + (c - 'a' + shift) % 26));
            } else if (c >= 'A' && c <= 'Z') {
                b.append((char) ('A' + (c - 'A' + shift) % 26));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    public static String toTitleCase(String s) {
        StringBuilder b = new StringBuilder();
        boolean nextUp = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                nextUp = true;
                b.append(c);
            } else if (nextUp) {
                b.append(Character.toUpperCase(c));
                nextUp = false;
            } else {
                b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    public static String toSentenceCase(String s) {
        String lower = s.toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder(lower);
        boolean nextUp = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (nextUp && Character.isLetter(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
                nextUp = false;
            }
            if (c == '.' || c == '!' || c == '?') {
                nextUp = true;
            }
        }
        return b.toString();
    }

    public static String toAlternateCase(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return b.toString();
    }

    public static String reversed(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    public static Map<String, String> morseEncodeMap() {
        Map<String, String> m = new HashMap<>();
        m.put("A", ".-");
        m.put("B", "-...");
        m.put("C", "-.-.");
        m.put("D", "-..");
        m.put("E", ".");
        m.put("F", "..-.");
        m.put("G", "--.");
        m.put("H", "....");
        m.put("I", "..");
        m.put("J", ".---");
        m.put("K", "-.-");
        m.put("L", ".-..");
        m.put("M", "--");
        m.put("N", "-.");
        m.put("O", "---");
        m.put("P", ".--.");
        m.put("Q", "--.-");
        m.put("R", ".-.");
        m.put("S", "...");
        m.put("T", "-");
        m.put("U", "..-");
        m.put("V", "...-");
        m.put("W", ".--");
        m.put("X", "-..-");
        m.put("Y", "-.--");
        m.put("Z", "--..");
        m.put("0", "-----");
        m.put("1", ".----");
        m.put("2", "..---");
        m.put("3", "...--");
        m.put("4", "....-");
        m.put("5", ".....");
        m.put("6", "-....");
        m.put("7", "--...");
        m.put("8", "---..");
        m.put("9", "----.");
        m.put(".", ".-.-.-");
        m.put(",", "--..--");
        m.put("?", "..--..");
        m.put("'", ".----.");
        m.put("!", "-.-.--");
        m.put("/", "-..-.");
        m.put("(", "-.--.");
        m.put(")", "-.--.-");
        m.put("&", ".-...");
        m.put(":", "---...");
        m.put(";", "-.-.-.");
        m.put("=", "-...-");
        m.put("+", ".-.-.");
        m.put("-", "-....-");
        m.put("_", "..--.-");
        m.put("\"", ".-..-.");
        m.put("$", "...-..-");
        m.put("@", ".--.-.");
        return m;
    }

    public static String morseEncode(String s, Map<String, String> enc) {
        String upper = s.toUpperCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == ' ') {
                b.append("  ");
            } else {
                String code = enc.get(String.valueOf(c));
                if (code != null) {
                    if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                        b.append(' ');
                    }
                    b.append(code);
                }
            }
        }
        return b.toString().trim();
    }

    public static String morseDecode(String s, Map<String, String> dec) {
        StringBuilder b = new StringBuilder();
        for (String word : s.trim().split("   ")) {
            for (String code : word.trim().split(" ")) {
                String letter = dec.get(code.trim());
                if (letter != null) {
                    b.append(letter);
                }
            }
            b.append(' ');
        }
        return b.toString().trim();
    }

    public static String baseConvert(String input, int radix) throws Exception {
        String s = input.trim().replace("0x", "").replace("0X", "");
        long v = Long.parseLong(s, radix);
        return "Bin: " + Long.toBinaryString(v) + "\n"
                + "Oct: " + Long.toOctalString(v) + "\n"
                + "Dec: " + v + "\n"
                + "Hex: " + Long.toHexString(v).toUpperCase(Locale.US) + "\n"
                + "Bits: " + (v == 0 ? 1 : (64 - Long.numberOfLeadingZeros(v)));
    }
}
