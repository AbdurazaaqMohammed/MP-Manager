package io.github.abdurazaaqmohammed.vault;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.DigestFactory;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Bitwarden-compatible vault cryptography for the self-hosted client (NodeWarden first).
 *
 * <p>Only the pieces a personal vault needs: KDF (PBKDF2-SHA256 and Argon2id), master key hash,
 * HKDF key stretching, and the type-2 cipher string ({@code 2.iv|data|mac}, AES-256-CBC with an
 * HMAC-SHA256 over iv||data). Organisation/RSA (type 4) material is deliberately out of scope:
 * NodeWarden does not implement organisations.
 *
 * <p>The primitives come from the BouncyCastle provider already bundled with the app; the JCE
 * covers AES and HMAC.
 */
public final class BwCrypto {

    /** KDF kinds returned by the prelogin endpoint. */
    public static final int KDF_PBKDF2 = 0;
    public static final int KDF_ARGON2ID = 1;

    private BwCrypto() {
    }

    /** KDF parameters for one account, answered by {@code POST /identity/accounts/prelogin}. */
    public static final class KdfConfig {
        public final int type;
        public final int iterations;
        public final int memoryMB;
        public final int parallelism;

        public KdfConfig(int type, int iterations, int memoryMB, int parallelism) {
            this.type = type;
            this.iterations = iterations;
            this.memoryMB = memoryMB;
            this.parallelism = parallelism;
        }

        /** Bitwarden's own default, used when the server does not answer prelogin. */
        public static KdfConfig defaults() {
            return new KdfConfig(KDF_PBKDF2, 600000, 0, 0);
        }

        public static KdfConfig fromPrelogin(JSONObject json) {
            int type = json.optInt("kdf", KDF_PBKDF2);
            int iterations = json.optInt("kdfIterations", 600000);
            int memory = json.optInt("kdfMemory", 0);
            int parallelism = json.optInt("kdfParallelism", 0);
            if (iterations <= 0) iterations = 600000;
            return new KdfConfig(type, iterations, memory, parallelism);
        }
    }

    /** A 64-byte symmetric key split into its encryption and MAC halves. */
    public static final class SymKey {
        public final byte[] enc;
        public final byte[] mac;

        public SymKey(byte[] enc, byte[] mac) {
            if (enc == null || enc.length != 32 || mac == null || mac.length != 32) {
                throw new IllegalArgumentException("a SymKey is two 32-byte halves");
            }
            this.enc = enc;
            this.mac = mac;
        }

        static SymKey of64(byte[] key) {
            if (key == null || key.length != 64) {
                throw new IllegalArgumentException("expected a 64-byte key");
            }
            return new SymKey(Arrays.copyOfRange(key, 0, 32), Arrays.copyOfRange(key, 32, 64));
        }
    }

    // ------------------------------------------------------------------ KDF

    /**
     * masterKey = KDF(password, salt=email). PBKDF2-SHA256 uses the raw email as salt;
     * Argon2id salts with SHA-256(email), matching the reference clients.
     */
    public static byte[] deriveMasterKey(String password, String email, KdfConfig cfg) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] emailBytes = email.getBytes(StandardCharsets.UTF_8);
        if (cfg.type == KDF_ARGON2ID) {
            Digest sha = DigestFactory.createSHA256();
            sha.update(emailBytes, 0, emailBytes.length);
            byte[] salt = new byte[32];
            sha.doFinal(salt, 0);
            Argon2BytesGenerator argon = new Argon2BytesGenerator();
            argon.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withIterations(cfg.iterations)
                    .withMemoryAsKB(cfg.memoryMB * 1024)
                    .withParallelism(cfg.parallelism)
                    .withSalt(salt)
                    .withVersion(0x13)
                    .build());
            byte[] out = new byte[32];
            argon.generateBytes(passwordBytes, out);
            return out;
        }
        PKCS5S2ParametersGenerator gen =
                new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
        gen.init(passwordBytes, emailBytes, cfg.iterations);
        return ((KeyParameter) gen.generateDerivedParameters(256)).getKey();
    }

    /** What the server actually receives as the "password": base64(PBKDF2(masterKey, password, 1)). */
    public static String deriveMasterKeyHash(byte[] masterKey, String password) {
        PKCS5S2ParametersGenerator gen =
                new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
        gen.init(masterKey, password.getBytes(StandardCharsets.UTF_8), 1);
        return Base64.getEncoder()
                .encodeToString(((KeyParameter) gen.generateDerivedParameters(256)).getKey());
    }

    /** stretchedMasterKey: HKDF-SHA256 expand-only over the master key, info "enc"/"mac". */
    public static SymKey stretchMasterKey(byte[] masterKey) {
        return new SymKey(hkdfExpand(masterKey, "enc"), hkdfExpand(masterKey, "mac"));
    }

    private static byte[] hkdfExpand(byte[] key, String info) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(DigestFactory.createSHA256());
        hkdf.init(HKDFParameters.skipExtractParameters(
                key, info.getBytes(StandardCharsets.UTF_8)));
        byte[] out = new byte[32];
        hkdf.generateBytes(out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------ cipher strings

    /** The login answer carries the user key encrypted with the stretched master key. */
    public static SymKey decryptUserKey(SymKey stretched, String keyCipherString)
            throws Exception {
        return SymKey.of64(decrypt(stretched, keyCipherString));
    }

    public static String decryptToString(SymKey key, String cipherString) throws Exception {
        byte[] plain = decrypt(key, cipherString);
        return plain == null ? null : new String(plain, StandardCharsets.UTF_8);
    }

    public static byte[] decrypt(SymKey key, String cipherString) throws Exception {
        if (cipherString == null) return null;
        int dot = cipherString.indexOf('.');
        if (dot < 0) throw new Exception("malformed cipher string");
        if (!"2".equals(cipherString.substring(0, dot))) {
            throw new Exception("unsupported cipher string type: "
                    + cipherString.substring(0, dot));
        }
        String[] parts = cipherString.substring(dot + 1).split("\\|", -1);
        if (parts.length != 3) throw new Exception("malformed cipher string");
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] data = Base64.getDecoder().decode(parts[1]);
        byte[] mac = Base64.getDecoder().decode(parts[2]);

        byte[] expected = mac(key.mac, iv, data);
        if (!MessageDigest.isEqual(expected, mac)) {
            throw new Exception("MAC mismatch - wrong key or corrupted entry");
        }

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.enc, "AES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    public static String encryptToString(SymKey key, String plaintext) throws Exception {
        return plaintext == null ? null : encrypt(key, plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public static String encrypt(SymKey key, byte[] plaintext) throws Exception {
        if (plaintext == null) return null;
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.enc, "AES"),
                new IvParameterSpec(iv));
        byte[] data = cipher.doFinal(plaintext);
        byte[] mac = mac(key.mac, iv, data);
        Base64.Encoder b64 = Base64.getEncoder();
        return "2." + b64.encodeToString(iv) + "|" + b64.encodeToString(data) + "|"
                + b64.encodeToString(mac);
    }

    private static byte[] mac(byte[] macKey, byte[] iv, byte[] data) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        hmac.update(iv);
        return hmac.doFinal(data);
    }
}
