package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.sun.security.x509.CertAndKeyGen;
import android.sun.security.x509.X500Name;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

public class FtpsCertificateUtil {

    private static final String ALIAS = "mpmanager-ftps";
    private static final char[] DEFAULT_PASSWORD = "mpmanager-ftps".toCharArray();

    private FtpsCertificateUtil() {
    }

    /**
     * Generates a self-signed certificate and stores it in a JKS keystore file.
     * If the file already exists it is reused.
     *
     * @param keystoreFile the destination keystore file
     * @return the same keystore file
     */
    public static File ensureKeystore(File keystoreFile) throws Exception {
        if (keystoreFile.exists() && keystoreFile.length() > 0) {
            return keystoreFile;
        }
        Security.addProvider(new android.sun.security.provider.JavaKeyStoreProvider());
        File parent = keystoreFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        keyGen.generate(2048);
        X500Name x500Name = new X500Name("CN=MP Manager FTP Server, O=MP Manager, L=Mobile, C=WW");
        long oneYear = 365L * 24 * 60 * 60;
        X509Certificate cert = keyGen.getSelfCertificate(x500Name, new Date(), oneYear);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, DEFAULT_PASSWORD);
        keyStore.setKeyEntry(ALIAS, keyGen.getPrivateKey(), DEFAULT_PASSWORD, new X509Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            keyStore.store(fos, DEFAULT_PASSWORD);
        }
        return keystoreFile;
    }

    public static char[] getPassword() {
        return DEFAULT_PASSWORD;
    }

    public static String getPasswordString() {
        return new String(DEFAULT_PASSWORD);
    }
}
