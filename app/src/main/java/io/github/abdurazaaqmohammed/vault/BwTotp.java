package io.github.abdurazaaqmohammed.vault;

import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP for the {@code Totp} field of a login entry. Understands the usual
 * {@code otpauth://totp/...} URI and the bare Base32 secret NodeWarden stores;
 * anything else (for example {@code steam://}) returns null and the UI hides the code.
 */
public final class BwTotp {

    private BwTotp() {
    }

    /**
     * Current code for the given TOTP value at {@code nowMillis}; null when unsupported.
     * Accepts the usual {@code otpauth://totp/...} URI and the bare Base32 secret that
     * NodeWarden stores (then: 6 digits, 30 seconds, SHA1). {@code steam://} and friends
     * return null and the UI simply hides the code.
     */
    public static String code(String uri, long nowMillis) {
        try {
            if (uri == null || uri.isEmpty()) return null;
            String secret;
            int digits = 6;
            int period = 30;
            String algorithm = "SHA1";
            String lower = uri.toLowerCase(Locale.ROOT);
            if (lower.startsWith("otpauth://totp/")) {
                String found = null;
                String query = uri.substring(uri.indexOf('?') + 1);
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq < 0) continue;
                    String key = part.substring(0, eq);
                    String value = part.substring(eq + 1);
                    if (key.equalsIgnoreCase("secret")) found = value;
                    else if (key.equalsIgnoreCase("digits")) digits = Integer.parseInt(value);
                    else if (key.equalsIgnoreCase("period")) period = Integer.parseInt(value);
                    else if (key.equalsIgnoreCase("algorithm")) algorithm = value;
                }
                if (found == null || found.isEmpty()) return null;
                secret = found;
            } else if (lower.contains("://")) {
                return null;
            } else {
                secret = uri;
            }

            byte[] key = base32(secret);
            long counter = nowMillis / 1000L / period;
            Mac mac = Mac.getInstance("Hmac" + algorithm);
            mac.init(new SecretKeySpec(key, "Hmac" + algorithm));
            byte[] hash = mac.doFinal(longToBytes(counter));

            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int modulo = (int) Math.pow(10, digits);
            String code = Integer.toString(binary % modulo, 10);
            while (code.length() < digits) code = "0" + code;
            return code;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] longToBytes(long value) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        return out;
    }

    static byte[] base32(String text) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String clean = text.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = alphabet.indexOf(clean.charAt(i));
            if (val < 0) throw new IllegalArgumentException("bad base32 character");
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[index++] = (byte) ((buffer >> bits) & 0xff);
            }
        }
        return out;
    }
}
