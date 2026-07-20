package io.github.abdurazaaqmohammed.utils;

import android.content.SharedPreferences;
import android.sun.security.pkcs12.PKCS12KeyStore;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.android.apksig.ApkSigner;

import org.bouncycastle.jcajce.PKCS12Key;
import org.bouncycastle.jcajce.provider.keystore.pkcs12.PKCS12KeyStoreSpi;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.crypto.SecretKeyFactory;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;

public class SignWrapper {

    public void setKey(File key) {
        this.key = key;
    }

    File key;
    char[] pw;
    boolean v1;
    boolean v2;
    boolean v3;
    boolean v4;
    String signedBy;

    public SignWrapper(String pathToSignatureFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this(new File(pathToSignatureFile), password, v1Enabled, v2Enabled, v3Enabled, v4Enabled);
    }

    public SignWrapper(String pathToSignatureFile, String password, MainActivity context) {
        this.key = new File(pathToSignatureFile);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.pw = password.toCharArray();
        this.v1 = settings.getBoolean("v1", true);
        this.v2 = settings.getBoolean("v2", true);
        this.v3 = settings.getBoolean("v3", true);
        this.v4 = settings.getBoolean("v4", false);
        this.signedBy = settings.getString("signedBy", "Android Gradle 8.0.2");
    }

    public SignWrapper(File signatureKeyFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this.key = (signatureKeyFile);
        this.pw = password.toCharArray();
        this.v1 = v1Enabled;
        this.v2 = v2Enabled;
        this.v3 = v3Enabled;
        this.v4 = v4Enabled;
    }

    public SignWrapper(File signatureKeyFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled, CharSequence signedBy) {
        this.key = (signatureKeyFile);
        this.pw = password.toCharArray();
        this.v1 = v1Enabled;
        this.v2 = v2Enabled;
        this.v3 = v3Enabled;
        this.v4 = v4Enabled;
        this.signedBy = signedBy.toString();
    }

    public SignWrapper(String pathToSignatureFile, String password) {
        this(pathToSignatureFile, password, true, true, true, false);
    }

    public void signApk(File inputApk, File output, boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        Exception e1, e2 = null;

        try (InputStream fis = FileUtils.getInputStream(key)) {
            PKCS12KeyStoreSpi.BCPKCS12KeyStore keystore = new PKCS12KeyStoreSpi.BCPKCS12KeyStore();

            keystore.engineLoad(fis, pw);
            String alias = keystore.engineAliases().nextElement();
            boolean b1 = inputApk == output;
            if(b1) output = new File(output.getParentFile(), output.getName() + "_signed.apk");
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keystore.engineGetEntry(alias, new KeyStore.PasswordProtection(pw));
            ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(new ApkSigner.SignerConfig.Builder("CERT",
                    privateKeyEntry.getPrivateKey(),
                    Collections.singletonList((X509Certificate) keystore.engineGetCertificate(alias))).build()))
                    .setInputApk(inputApk)
                    .setOutputApk(output)
                    .setCreatedBy(TextUtils.isEmpty(signedBy) ? "Android Gradle 8.0.2" : signedBy)
                    .setV1SigningEnabled(v1)
                    .setV2SigningEnabled(v2)
                    .setV3SigningEnabled(v3)
                    .setV4SigningEnabled(v4);
            if(v4) {
                String fileName = inputApk.getName();
                String formattedName = fileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", ".idsig");
                int lastDotIndex;
                b.setV4SignatureOutputFile(new File(output.getParentFile(), fileName.equals(formattedName) ? (lastDotIndex = fileName.lastIndexOf('.')) == -1 ?
                                                                                                             fileName + "_signed" : fileName.substring(0, lastDotIndex) + "_signed." + fileName.substring(lastDotIndex + 1) : formattedName));
            }
            b.build().sign();
            if(b1) {
                //inputApk.delete();
                //output.renameTo(inputApk);
            }
        } catch (Exception e) {
            e1 = e;
            String[] types = {"JKS", "BKS"};
            for (int i = 0, typesLength = types.length; i < typesLength; i++) {
                String type = types[i];
                try (InputStream fis = FileUtils.getInputStream(key)) {
                    KeyStore keystore = KeyStore.getInstance(type);

                    keystore.load(fis, pw);
                    String alias = keystore.aliases().nextElement();
                    boolean inputIsOutput = inputApk == output;
                    if (inputIsOutput) output = new File(output.getParentFile(), output.getName() + "_signed.apk");
                    ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(new ApkSigner.SignerConfig.Builder("CERT",
                            ((KeyStore.PrivateKeyEntry) keystore.getEntry(alias, new KeyStore.PasswordProtection(pw))).getPrivateKey(),
                            Collections.singletonList((X509Certificate) keystore.getCertificate(alias))).build()))
                            .setInputApk(inputApk)
                            .setOutputApk(output)
                            .setCreatedBy(TextUtils.isEmpty(signedBy) ? "Android Gradle 8.0.2" : signedBy)
                            .setV1SigningEnabled(v1)
                            .setV2SigningEnabled(v2)
                            .setV3SigningEnabled(v3)
                            .setV4SigningEnabled(v4);
                    if (v4) {
                        String fileName = inputApk.getName();
                        String formattedName = fileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", ".idsig");
                        int lastDotIndex;
                        b.setV4SignatureOutputFile(new File(output.getParentFile(), fileName.equals(formattedName) ? (lastDotIndex = fileName.lastIndexOf('.')) == -1 ?
                                                                                                                     fileName + "_signed" : fileName.substring(0, lastDotIndex) + "_signed." + fileName.substring(lastDotIndex + 1) : formattedName));
                    }
                    b.build().sign();
                    if (inputIsOutput) {
                        //inputApk.delete();
                        //output.renameTo(inputApk);
                    }
                    break;
                } catch (Exception ex) {
                    if(i == 0) e2 = ex; else throw new Exception("Failed all type - PKCS12: " + e1.getMessage() + "JKS: " + e2 + "BKS: " + ex.getMessage());
                }
            }
        }
    }

    public void signApk(File inputApk, File output) throws Exception {
        signApk(inputApk, output, v1, v2, v3, v4);
    }
    public void signApk(File inputApk) throws Exception {
        signApk(inputApk, inputApk, v1, v2, v3, v4);
    }
}