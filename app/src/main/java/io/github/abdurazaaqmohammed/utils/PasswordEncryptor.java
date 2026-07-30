package io.github.abdurazaaqmohammed.utils;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.content.SharedPreferences;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class PasswordEncryptor {

    private static final String KEYSTORE_ALIAS = "MPManagerKeyStoreKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public static String encryptString(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return plainText;
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] encryption = cipher.doFinal(plainText.getBytes("UTF-8"));
            byte[] combined = new byte[GCM_IV_LENGTH + encryption.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encryption, 0, combined, GCM_IV_LENGTH, encryption.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return plainText;
        }
    }

    public static String decryptString(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return cipherText;
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            byte[] combined = Base64.decode(cipherText, Base64.NO_WRAP);
            if (combined.length < GCM_IV_LENGTH + 1) return cipherText;
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryption = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryption, 0, encryption.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encryption), "UTF-8");
        } catch (Exception e) {
            return cipherText;
        }
    }

    public static String getEncryptedPref(SharedPreferences prefs, String key, String defaultVal) {
        String val = prefs.getString(key, null);
        if (val == null) return defaultVal;
        String decrypted = decryptString(val);
        if (decrypted != null && !decrypted.equals(val)) return decrypted;
        if (val.equals(decrypted) && !val.equals(defaultVal)) return val;
        if (val.equals(defaultVal)) return defaultVal;
        return val;
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return keyGenerator.generateKey();
    }
}
