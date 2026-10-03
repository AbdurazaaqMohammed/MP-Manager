package io.github.abdurazaaqmohammed.utils;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Iterator;

/**
 * OpenPGP encryption and decryption, producing files GPG can read.
 *
 * <p>Implemented with BouncyCastle's OpenPGP module rather than an external gpg
 * binary, so nothing is executed and nothing needs to be rooted or downloaded
 * at runtime.
 *
 * <p>Two modes: password-based (symmetric, AES-256 with salted+iterated S2K,
 * SHA-1 digest at 65536 iterations, matching gpg's compatible defaults) and
 * public-key (the payload is AES-256, the session key is wrapped for the
 * recipient's key). Both carry an MDC integrity packet and are zlib-compressed;
 * output is ASCII-armored.
 */
public final class GpgCrypto {

    private static final int BUFFER = 64 * 1024;

    private GpgCrypto() {
    }

    /**
     * Encrypts {@code in} with {@code password} and writes ASCII-armored output
     * to {@code out}. Neither stream is closed here; the caller owns them.
     */
    public static void encrypt(InputStream in, OutputStream out, char[] password)
            throws IOException, PGPException {
        PGPEncryptedDataGenerator encGen = newEncryptor();
        encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(password));
        writeEncrypted(encGen, in, out);
    }

    /**
     * Encrypts {@code in} for the holder of {@code key} (an encryption-capable
     * public key), writing ASCII-armored output to {@code out}.
     */
    public static void encryptForKey(InputStream in, OutputStream out, PGPPublicKey key)
            throws IOException, PGPException {
        PGPEncryptedDataGenerator encGen = newEncryptor();
        encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key));
        writeEncrypted(encGen, in, out);
    }

    /**
     * Decrypts password-encrypted input (armored or binary) with
     * {@code password}. Throws on a wrong password or corrupt data; the MDC is
     * verified before any plaintext is handed out by the caller's caller, but
     * callers should write to a temporary file and only move it into place
     * after this method returns, so a failure never clobbers a real file.
     */
    public static void decrypt(InputStream in, OutputStream out, char[] password)
            throws IOException, PGPException {
        PGPEncryptedDataList dataList = readDataList(in);
        PGPPBEEncryptedData pbeData = null;
        for (PGPEncryptedData ed : dataList) {
            if (ed instanceof PGPPBEEncryptedData) {
                pbeData = (PGPPBEEncryptedData) ed;
                break;
            }
        }
        if (pbeData == null) {
            throw new PGPException("Not password-encrypted (maybe a public-key file)");
        }
        try (InputStream clear = pbeData.getDataStream(
                new JcePBEDataDecryptorFactoryBuilder(new BcPGPDigestCalculatorProvider())
                        .build(password))) {
            readLiteral(clear, out);
            verify(pbeData);
        }
    }

    /**
     * Decrypts public-key-encrypted input using the secret key in
     * {@code secretKeyIn} unlocked with {@code passphrase} (empty allowed for
     * unprotected keys).
     */
    public static void decryptWithKey(InputStream in, OutputStream out,
                                      InputStream secretKeyIn, char[] passphrase)
            throws IOException, PGPException {
        PGPEncryptedDataList dataList = readDataList(in);
        PGPPublicKeyEncryptedData pkData = null;
        for (PGPEncryptedData ed : dataList) {
            if (ed instanceof PGPPublicKeyEncryptedData) {
                pkData = (PGPPublicKeyEncryptedData) ed;
                break;
            }
        }
        if (pkData == null) {
            throw new PGPException("Not public-key-encrypted (maybe a password file)");
        }

        PGPSecretKeyRingCollection secretKeys = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(secretKeyIn), new BcKeyFingerprintCalculator());
        PGPSecretKey secretKey = secretKeys.getSecretKey(pkData.getKeyID());
        if (secretKey == null) {
            throw new PGPException("No matching secret key in that key file");
        }
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(
                new JcePBESecretKeyDecryptorBuilder().setProvider("BC")
                        .build(passphrase == null || passphrase.length == 0 ? null : passphrase));

        try (InputStream clear = pkData.getDataStream(
                new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC")
                        .build(privateKey))) {
            readLiteral(clear, out);
            verify(pkData);
        }
    }

    /**
     * True when the stream holds a public-key-encrypted message rather than a
     * password-encrypted one, so the UI can ask for a key file up front.
     */
    public static boolean isPublicKeyEncrypted(InputStream in) throws IOException {
        try {
            PGPEncryptedDataList dataList = readDataList(in);
            for (PGPEncryptedData ed : dataList) {
                if (ed instanceof PGPPublicKeyEncryptedData) return true;
                if (ed instanceof PGPPBEEncryptedData) return false;
            }
        } catch (PGPException e) {
            throw new IOException("Not a PGP message", e);
        }
        return false;
    }

    /**
     * Reads a public key ring file (armored or binary) and returns the first
     * encryption-capable key: a dedicated encryption subkey if there is one,
     * else the master key.
     */
    public static PGPPublicKey loadEncryptionKey(InputStream keyIn)
            throws IOException, PGPException {
        PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(keyIn), new BcKeyFingerprintCalculator());
        PGPPublicKey master = null;
        Iterator<PGPPublicKeyRing> ringIt = rings.getKeyRings();
        while (ringIt.hasNext()) {
            Iterator<PGPPublicKey> keyIt = ringIt.next().getPublicKeys();
            while (keyIt.hasNext()) {
                PGPPublicKey k = keyIt.next();
                if (!k.isEncryptionKey()) continue;
                if (!k.isMasterKey()) return k;
                if (master == null) master = k;
            }
        }
        if (master != null) return master;
        throw new PGPException("No encryption-capable key found in that file");
    }

    // ---- internals ----

    private static PGPEncryptedDataGenerator newEncryptor() {
        JcePGPDataEncryptorBuilder builder = new JcePGPDataEncryptorBuilder(
                PGPEncryptedData.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(new SecureRandom());
        return new PGPEncryptedDataGenerator(builder);
    }

    private static void writeEncrypted(PGPEncryptedDataGenerator encGen,
                                       InputStream in, OutputStream out)
            throws IOException, PGPException {
        // Literal data is compressed so the plaintext is not padded to the
        // block size, and the encrypted payload is smaller than the file.
        byte[] compressed = compress(literalData(in));
        ArmoredOutputStream armor = new ArmoredOutputStream(out);
        try (OutputStream cipherOut = encGen.open(armor, compressed.length)) {
            cipherOut.write(compressed);
        }
        armor.flush();
        armor.close();
    }

    private static PGPEncryptedDataList readDataList(InputStream in)
            throws IOException, PGPException {
        InputStream decoded = PGPUtil.getDecoderStream(new BufferedInputStream(in, BUFFER));
        PGPObjectFactory factory = new JcaPGPObjectFactory(decoded);
        Object first = factory.nextObject();
        if (first instanceof PGPEncryptedDataList) {
            return (PGPEncryptedDataList) first;
        }
        // Skip marker packets and friends until the data list shows up.
        Object next = factory.nextObject();
        if (next instanceof PGPEncryptedDataList) {
            return (PGPEncryptedDataList) next;
        }
        throw new PGPException("Not a PGP encrypted message");
    }

    private static void readLiteral(InputStream clear, OutputStream out)
            throws IOException, PGPException {
        PGPObjectFactory plainFactory = new JcaPGPObjectFactory(clear);
        Object message = plainFactory.nextObject();
        if (message instanceof PGPCompressedData) {
            plainFactory = new JcaPGPObjectFactory(((PGPCompressedData) message).getDataStream());
            message = plainFactory.nextObject();
        }
        if (!(message instanceof PGPLiteralData)) {
            throw new PGPException("Not a literal data packet");
        }
        InputStream literal = ((PGPLiteralData) message).getInputStream();
        byte[] buffer = new byte[BUFFER];
        int n;
        while ((n = literal.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
    }

    /**
     * The MDC is only checked after the payload is read, so a wrong password
     * must not be accepted just because the bytes decrypted.
     */
    private static void verify(PGPEncryptedData data) throws IOException, PGPException {
        if (data.isIntegrityProtected() && !data.verify()) {
            throw new PGPException("Data failed integrity verification");
        }
    }

    private static byte[] literalData(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
        String name = "data";
        long size = in.available();
        try (OutputStream out = literal.open(baos, PGPLiteralData.BINARY, name, size, new Date())) {
            byte[] buffer = new byte[BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        }
        return baos.toByteArray();
    }

    private static byte[] compress(byte[] data) throws IOException {
        PGPCompressedDataGenerator compressGen = new PGPCompressedDataGenerator(
                PGPCompressedData.ZLIB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream out = compressGen.open(baos)) {
            out.write(data);
        }
        return baos.toByteArray();
    }
}