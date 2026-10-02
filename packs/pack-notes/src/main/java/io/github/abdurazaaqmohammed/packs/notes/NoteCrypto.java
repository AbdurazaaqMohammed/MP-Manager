package io.github.abdurazaaqmohammed.packs.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class NoteCrypto {

    private static final String PREFS = "mp_notes_security";
    private static final String KEY_HASH = "pin_hash";
    private static final String KEY_SALT = "pin_salt";
    private static final int ITERATIONS = 20000;
    private static final int KEY_BITS = 256;

    private NoteCrypto() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasPin(Context context) {
        return prefs(context).getString(KEY_HASH, "").length() > 0;
    }

    public static void setPin(Context context, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs(context).edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
                .apply();
    }

    public static boolean verifyPin(Context context, String pin) {
        String salt64 = prefs(context).getString(KEY_SALT, "");
        String hash64 = prefs(context).getString(KEY_HASH, "");
        if (salt64.isEmpty() || hash64.isEmpty()) return false;
        try {
            byte[] salt = Base64.decode(salt64, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hash64, Base64.NO_WRAP);
            byte[] actual = hash(pin, salt);
            if (actual.length != expected.length) return false;
            int diff = 0;
            for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
            return diff == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void clearPin(Context context) {
        prefs(context).edit().remove(KEY_HASH).remove(KEY_SALT).apply();
    }

    private static byte[] deriveKey(Context context, String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            return new byte[]{0};
        }
    }

    public static String encrypt(Context context, String pin, String plain) {
        if (plain == null) return null;
        try {
            byte[] salt = Base64.decode(prefs(context).getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] keyBytes = deriveKey(context, pin, salt);
            if (keyBytes == null) return null;
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            byte[] data = plain.getBytes("UTF-8");
            boolean modern = android.os.Build.VERSION.SDK_INT >= 21;
            int ivLen = modern ? 12 : 16;
            byte[] iv = new byte[ivLen];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(modern ? "AES/GCM/NoPadding" : "AES/CBC/PKCS7Padding");
            if (modern) {
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            }
            byte[] out = cipher.doFinal(data);
            byte[] packed = new byte[1 + iv.length + out.length];
            packed[0] = (byte) (modern ? 'G' : 'C');
            System.arraycopy(iv, 0, packed, 1, iv.length);
            System.arraycopy(out, 0, packed, 1 + iv.length, out.length);
            return Base64.encodeToString(packed, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static String decrypt(Context context, String pin, String packed64) {
        if (packed64 == null || packed64.isEmpty()) return null;
        try {
            byte[] salt = Base64.decode(prefs(context).getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] keyBytes = deriveKey(context, pin, salt);
            if (keyBytes == null) return null;
            byte[] packed = Base64.decode(packed64, Base64.NO_WRAP);
            if (packed.length < 18) return null;
            boolean modern = packed[0] == 'G';
            int ivLen = modern ? 12 : 16;
            byte[] iv = new byte[ivLen];
            byte[] data = new byte[packed.length - 1 - ivLen];
            System.arraycopy(packed, 1, iv, 0, ivLen);
            System.arraycopy(packed, 1 + ivLen, data, 0, data.length);
            Cipher cipher = Cipher.getInstance(modern ? "AES/GCM/NoPadding" : "AES/CBC/PKCS7Padding");
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            if (modern) {
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            }
            return new String(cipher.doFinal(data), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
