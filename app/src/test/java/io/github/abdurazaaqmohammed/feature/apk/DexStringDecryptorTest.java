package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for enhanced DexStringDecryptor heuristics:
 * XOR, rolling XOR, positional XOR, string key XOR, char shift, and Base64-XOR.
 */
public class DexStringDecryptorTest {

    @Test
    public void testStandardXor() {
        String plain = "Hello, MT Parity!";
        int key = 0x42;
        String cipher = DexStringDecryptor.xorDecrypt(plain, key);
        String decrypted = DexStringDecryptor.xorDecrypt(cipher, key);
        assertEquals(plain, decrypted);
    }

    @Test
    public void testPositionalXor() {
        String plain = "SecretToken123456";
        int key = 0x5a;
        String cipher = DexStringDecryptor.positionalXorDecrypt(plain, key);
        String decrypted = DexStringDecryptor.positionalXorDecrypt(cipher, key);
        assertEquals(plain, decrypted);
    }

    @Test
    public void testRollingXor() {
        String plain = "DexGuard_OLLVM_ProtectedString";
        int key = 0x1337;
        // In rolling XOR: c[i] = p[i] ^ key; key = (key * 31 + c[i])
        // Let's encrypt
        char[] pChars = plain.toCharArray();
        char[] cChars = new char[pChars.length];
        int rollingKey = key;
        for (int i = 0; i < pChars.length; i++) {
            cChars[i] = (char) (pChars[i] ^ (rollingKey & 0xffff));
            rollingKey = (rollingKey * 31 + cChars[i]) & 0xffff;
        }
        String cipher = new String(cChars);

        String decrypted = DexStringDecryptor.rollingXorDecrypt(cipher, key);
        assertEquals(plain, decrypted);
    }

    @Test
    public void testStringKeyXor() {
        String plain = "https://api.example.com/v1/auth";
        String key = "MySecretKey";
        String cipher = DexStringDecryptor.stringKeyXorDecrypt(plain, key);
        String decrypted = DexStringDecryptor.stringKeyXorDecrypt(cipher, key);
        assertEquals(plain, decrypted);
    }

    @Test
    public void testCharShift() {
        String plain = "SimpleShiftString";
        int shift = 7;
        // Encrypt by adding shift
        char[] chars = plain.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ((chars[i] + shift) & 0xffff);
        }
        String cipher = new String(chars);

        String decrypted = DexStringDecryptor.charShiftDecrypt(cipher, shift);
        assertEquals(plain, decrypted);
    }
}
