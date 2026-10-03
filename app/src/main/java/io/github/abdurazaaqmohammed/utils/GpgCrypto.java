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
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Date;

/**
 * Password-based OpenPGP encryption, producing files GPG can read.
 *
 * <p>Implemented with BouncyCastle's OpenPGP module rather than an external gpg
 * binary, so nothing is executed and nothing needs to be rooted or downloaded
 * at runtime. Output is ASCII-armored, so it can be pasted and mailed too.
 *
 * <p>Symmetric AES-256 with SHA-256 key derivation and an integrity packet.
 * That is the reasonable default for a password-encrypted file and what gpg
 * itself picks for {@code gpg -c --cipher-algo AES256}.
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
        // Literal data goes through compression so the plaintext is not padded
        // to the block size, and the encrypted payload is smaller than the file.
        byte[] literal = literalData(in);
        byte[] compressed = compress(literal);

        JcePGPDataEncryptorBuilder builder = new JcePGPDataEncryptorBuilder(
                PGPEncryptedData.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(new SecureRandom());

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(builder);
        encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(password));

        ArmoredOutputStream armor = new ArmoredOutputStream(out);
        try (OutputStream cipherOut = encGen.open(armor, compressed.length)) {
            cipherOut.write(compressed);
        }
        armor.flush();
        armor.close();
    }

    /**
     * Decrypts ASCII-armored or binary input with {@code password} and writes the
     * plaintext to {@code out}. Throws on a wrong password or corrupt data.
     */
    public static void decrypt(InputStream in, OutputStream out, char[] password)
            throws IOException, PGPException {
        InputStream decoded = PGPUtil.getDecoderStream(new BufferedInputStream(in, BUFFER));
        PGPObjectFactory factory = new JcaPGPObjectFactory(decoded);

        Object first = factory.nextObject();
        PGPEncryptedDataList dataList;
        if (first instanceof PGPEncryptedDataList) {
            dataList = (PGPEncryptedDataList) first;
        } else {
            // The first packet may be a marker; keep walking.
            dataList = (PGPEncryptedDataList) factory.nextObject();
        }

        org.bouncycastle.openpgp.PGPPBEEncryptedData pbeData = null;
        for (org.bouncycastle.openpgp.PGPEncryptedData ed : dataList) {
            if (ed instanceof org.bouncycastle.openpgp.PGPPBEEncryptedData) {
                pbeData = (org.bouncycastle.openpgp.PGPPBEEncryptedData) ed;
                break;
            }
        }
        if (pbeData == null) {
            throw new PGPException("No password-encrypted data found");
        }

        PGPDigestCalculatorProvider digestCalculatorProvider;
        try {
            digestCalculatorProvider = new BcPGPDigestCalculatorProvider();
        } catch (Exception e) {
            throw new PGPException("Could not build digest calculator", e);
        }

        try (InputStream clear = pbeData.getDataStream(
                new JcePBEDataDecryptorFactoryBuilder(digestCalculatorProvider)
                        .build(password))) {
            PGPObjectFactory plainFactory = new JcaPGPObjectFactory(clear);
            Object message = plainFactory.nextObject();
            if (message instanceof PGPCompressedData) {
                plainFactory = new JcaPGPObjectFactory(
                        ((PGPCompressedData) message).getDataStream());
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
    }

    /** Wraps bytes as a literal-data packet: the payload type OpenPGP expects. */
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