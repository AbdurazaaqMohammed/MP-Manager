package io.github.abdurazaaqmohammed.vault;

import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP for the {@code Totp} field of a login entry. Understands the usual
 * {@code otpauth://totp/...} URIs with a Base32 secret; anything else (for example
 * NodeWarden's {@code steam://}) returns null and the UI simply hides the code.
 */
public final class BwTotp {

    private BwTotp() {
    }

    /** Current code for the given otpauth URI at {@code nowMillis}; null when unsupported. */
    public static String code(String uri, long nowMillis) {
        try {
            if (uri == null) return null;
            String lower = uri.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("otpauth://totp/")) return null;

            String query = uri.substring(uri.indexOf('?') + 1);
            String secret = null;
            int digits = 6;
            int period = 30;
            String algorithm = "SHA1";
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                if (key.equalsIgnoreCase("secret")) secret = value;
                else if (key.equalsIgnoreCase("digits")) digits = Integer.parseInt(value);
                else if (key.equalsIgnoreCase("period")) period = Integer.parseInt(value);
                else if (key.equalsIgnoreCase("algorithm")) algorithm = value;
            }
            if (secret == null || secret.isEmpty()) return null;

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
