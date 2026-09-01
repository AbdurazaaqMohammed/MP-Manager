package io.github.abdurazaaqmohammed.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.android.apksig.ApkSigner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.bouncycastle.jcajce.provider.keystore.pkcs12.PKCS12KeyStoreSpi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;

public class SignWrapper {

    public interface SignKeyCallback {
        void onAuthenticated(SignWrapper signWrapper);
    }

    private static final String DEFAULT_SIGNED_BY = "Android Gradle 8.0.2";
    private static final String PK8_EXT = ".pk8";
    private static final String PEM_EXT = ".pem";
    private static final String X509_PEM_EXT = ".x509.pem";

    private static final Pattern PRIVATE_KEY_PEM = Pattern.compile("-----BEGIN PRIVATE KEY-----([\\s\\S]*?)-----END PRIVATE KEY-----");
    private static final Pattern CERTIFICATE_PEM = Pattern.compile("-----BEGIN CERTIFICATE-----([\\s\\S]*?)-----END CERTIFICATE-----");

    private final File key;
    private final char[] password;
    private final boolean v1;
    private final boolean v2;
    private final boolean v3;
    private final boolean v4;
    private final String signedBy;

    public SignWrapper(File signatureKeyFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled, CharSequence signedBy) {
        this.key = signatureKeyFile;
        this.password = password == null ? new char[0] : password.toCharArray();
        this.v1 = v1Enabled;
        this.v2 = v2Enabled;
        this.v3 = v3Enabled;
        this.v4 = v4Enabled;
        this.signedBy = signedBy == null ? null : signedBy.toString();
    }

    public SignWrapper(File signatureKeyFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this(signatureKeyFile, password, v1Enabled, v2Enabled, v3Enabled, v4Enabled, null);
    }

    public SignWrapper(String pathToSignatureFile, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this(new File(pathToSignatureFile), password, v1Enabled, v2Enabled, v3Enabled, v4Enabled, null);
    }

    public SignWrapper(String pathToSignatureFile, String password, MainActivity context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.key = new File(pathToSignatureFile);
        this.password = password == null ? new char[0] : password.toCharArray();
        this.v1 = settings.getBoolean("v1", true);
        this.v2 = settings.getBoolean("v2", true);
        this.v3 = settings.getBoolean("v3", true);
        this.v4 = settings.getBoolean("v4", false);
        this.signedBy = settings.getString("signedBy", DEFAULT_SIGNED_BY);
    }

    public SignWrapper(String pathToSignatureFile, String password) {
        this(pathToSignatureFile, password, true, true, true, false);
    }

    public static void requireAuth(MainActivity activity, SignKeyCallback callback) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String keyPath;
        try {
            keyPath = prefs.getString("keyPath", FileUtils.getDebugKeystore(activity).getPath());
        } catch (IOException e) {
            Extensions.showMessage(activity, "Failed to load default key: " + e.getMessage());
            return;
        }
        File keyFile = new File(keyPath);
        boolean v1 = prefs.getBoolean("v1", true);
        boolean v2 = prefs.getBoolean("v2", true);
        boolean v3 = prefs.getBoolean("v3", true);
        boolean v4 = prefs.getBoolean("v4", false);
        String signedBy = prefs.getString("signedBy", DEFAULT_SIGNED_BY);

        if (keyPath.endsWith("debug.keystore")) {
            callback.onAuthenticated(new SignWrapper(keyFile, "android", v1, v2, v3, v4, signedBy));
        } else if (isPk8OrPemPath(keyPath)) {
            callback.onAuthenticated(new SignWrapper(keyFile, null, v1, v2, v3, v4, signedBy));
        } else if (prefs.getBoolean("useBiometrics", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String storedPass = PasswordEncryptor.decryptString(prefs.getString("keyPass", ""));
            if (!TextUtils.isEmpty(storedPass)) {
                authenticateWithBiometrics(activity, keyFile, storedPass, v1, v2, v3, v4, signedBy, callback);
                return;
            }
            requestPassword(activity, keyFile, v1, v2, v3, v4, signedBy, callback);
        } else {
            requestPassword(activity, keyFile, v1, v2, v3, v4, signedBy, callback);
        }
    }

    private static void requestPassword(MainActivity activity, File keyFile, boolean v1, boolean v2, boolean v3, boolean v4, String signedBy, SignKeyCallback callback) {
        EditText pwInput = new EditText(activity);
        pwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwInput.setHint(activity.rss.getString(R.string.enter_password));
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        layout.addView(pwInput);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.rss.getString(R.string.enter_password))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String password = pwInput.getText() != null ? pwInput.getText().toString() : "";
                    if (TextUtils.isEmpty(password)) {
                        Extensions.showMessage(activity, R.string.no_password_entered);
                        return;
                    }
                    if (!verifyKeystorePassword(keyFile, password)) {
                        Extensions.showMessage(activity, R.string.invalid_password);
                        return;
                    }
                    callback.onAuthenticated(new SignWrapper(keyFile, password, v1, v2, v3, v4, signedBy));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void authenticateWithBiometrics(MainActivity activity, File keyFile, String storedPass, boolean v1, boolean v2, boolean v3, boolean v4, String signedBy, SignKeyCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Extensions.showMessage(activity, "Authentication error: " + errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                callback.onAuthenticated(new SignWrapper(keyFile, storedPass, v1, v2, v3, v4, signedBy));
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Extensions.showMessage(activity, "Authentication failed");
            }
        });
        BiometricPrompt.PromptInfo.Builder auth = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.rss.getString(R.string.auth_sign))
                .setSubtitle(activity.rss.getString(R.string.auth_sign_msg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            auth.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else auth.setDeviceCredentialAllowed(true);
        biometricPrompt.authenticate(auth.build());
    }

    private static boolean verifyKeystorePassword(File keyFile, String password) {
        try {
            try (InputStream is = new FileInputStream(keyFile)) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(is, password.toCharArray());
                ks.aliases().nextElement();
                return true;
            }
        } catch (Exception e) {
            try (InputStream is = new FileInputStream(keyFile)) {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(is, password.toCharArray());
                ks.aliases().nextElement();
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    public void signApk(File inputApk, File output, boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        boolean inPlace = inputApk.equals(output);
        File actualOutput = inPlace ? resolveSibling(output, output.getName() + ".tmp") : output;

        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(createSignerConfig()))
                .setInputApk(inputApk)
                .setOutputApk(actualOutput)
                .setCreatedBy(TextUtils.isEmpty(signedBy) ? DEFAULT_SIGNED_BY : signedBy)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(v4);
        if (v4) {
            builder.setV4SignatureOutputFile(getV4SignatureOutputFile(inputApk, actualOutput));
        }
        builder.build().sign();

        if (inPlace) {
            finishInPlaceSign(inputApk, actualOutput);
        }
    }

    public void signApk(File inputApk, File output) throws Exception {
        signApk(inputApk, output, v1, v2, v3, v4);
    }

    public void signApk(File inputApk) throws Exception {
        signApk(inputApk, inputApk, v1, v2, v3, v4);
    }

    private ApkSigner.SignerConfig createSignerConfig() throws Exception {
        if (isPk8OrPemKey()) {
            return createPk8SignerConfig();
        }
        return createKeyStoreSignerConfig();
    }

    private ApkSigner.SignerConfig createPk8SignerConfig() throws Exception {
        File pk8 = findPk8File();
        File pem = findPemFile();
        if (pk8 != null && pem != null) {
            return buildSignerConfig(readPrivateKey(pk8), readCertificate(pem));
        }
        if (pem != null) {
            byte[] pemBytes = readAllBytes(pem);
            if (extractPemBlock(pemBytes, PRIVATE_KEY_PEM) != null && extractPemBlock(pemBytes, CERTIFICATE_PEM) != null) {
                return buildSignerConfig(readPrivateKey(pem), readCertificate(pem));
            }
        }
        throw new IOException("Could not load pk8/pem signing key from " + key.getPath()
                + ". Expected a .pk8 and a .pem file with the same name in the same directory.");
    }

    private ApkSigner.SignerConfig createKeyStoreSignerConfig() throws Exception {
        Exception lastError = null;
        String[] types = {"PKCS12", "JKS", "BKS"};
        for (String type : types) {
            try {
                return loadKeyStoreConfig(type);
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new IOException("Failed to load signing key from " + key.getPath(), lastError);
    }

    private ApkSigner.SignerConfig loadKeyStoreConfig(String type) throws Exception {
        if (type.equals("PKCS12")) {
            PKCS12KeyStoreSpi.BCPKCS12KeyStore keyStore = new PKCS12KeyStoreSpi.BCPKCS12KeyStore();
            try (InputStream in = FileUtils.getInputStream(key)) {
                keyStore.engineLoad(in, password);
            }
            String alias = keyStore.engineAliases().nextElement();
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.engineGetEntry(alias, new KeyStore.PasswordProtection(password));
            return buildSignerConfig(entry.getPrivateKey(), (X509Certificate) keyStore.engineGetCertificate(alias));
        }
        try (InputStream in = FileUtils.getInputStream(key)) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(in, password);
            String alias = keyStore.aliases().nextElement();
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, new KeyStore.PasswordProtection(password));
            return buildSignerConfig(entry.getPrivateKey(), (X509Certificate) keyStore.getCertificate(alias));
        }
    }

    private static ApkSigner.SignerConfig buildSignerConfig(PrivateKey privateKey, X509Certificate certificate) {
        return new ApkSigner.SignerConfig.Builder("CERT", privateKey, Collections.singletonList(certificate)).build();
    }

    private static PrivateKey readPrivateKey(File file) throws Exception {
        byte[] bytes = readAllBytes(file);
        byte[] keyBytes = isPemEncoded(bytes) ? extractPemBlock(bytes, PRIVATE_KEY_PEM) : bytes;
        if (keyBytes == null || keyBytes.length == 0) {
            throw new InvalidKeySpecException("No PRIVATE KEY found in " + file.getPath());
        }
        Exception lastError = null;
        for (String algorithm : new String[]{"RSA", "EC", "DSA"}) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new InvalidKeySpecException("Unsupported private key format in " + file.getPath(), lastError);
    }

    private static X509Certificate readCertificate(File file) throws Exception {
        byte[] bytes = readAllBytes(file);
        byte[] der = isPemEncoded(bytes) ? extractPemBlock(bytes, CERTIFICATE_PEM) : bytes;
        if (der == null || der.length == 0) {
            throw new CertificateException("No CERTIFICATE found in " + file.getPath());
        }
        try (InputStream in = new ByteArrayInputStream(der)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private boolean isPk8OrPemKey() {
        String name = key.getName();
        return name.endsWith(PK8_EXT) || name.endsWith(PEM_EXT);
    }

    private static boolean isPk8OrPemPath(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(PK8_EXT) || lower.endsWith(PEM_EXT);
    }

    private File findPk8File() {
        String name = key.getName();
        if (name.endsWith(PK8_EXT)) {
            return key.exists() ? key : null;
        }
        if (name.endsWith(PEM_EXT)) {
            File pk8 = resolveSibling(key, baseName(name) + PK8_EXT);
            return pk8.exists() ? pk8 : null;
        }
        return null;
    }

    private File findPemFile() {
        String name = key.getName();
        if (name.endsWith(PEM_EXT)) {
            return key.exists() ? key : null;
        }
        if (name.endsWith(PK8_EXT)) {
            String base = baseName(name);
            File pem = resolveSibling(key, base + PEM_EXT);
            if (pem.exists()) return pem;
            pem = resolveSibling(key, base + X509_PEM_EXT);
            return pem.exists() ? pem : null;
        }
        return null;
    }

    private static String baseName(String fileName) {
        if (fileName.endsWith(X509_PEM_EXT)) {
            return fileName.substring(0, fileName.length() - X509_PEM_EXT.length());
        }
        if (fileName.endsWith(PEM_EXT)) {
            return fileName.substring(0, fileName.length() - PEM_EXT.length());
        }
        if (fileName.endsWith(PK8_EXT)) {
            return fileName.substring(0, fileName.length() - PK8_EXT.length());
        }
        return fileName;
    }

    private static File resolveSibling(File file, String name) {
        File parent = file.getParentFile();
        return parent == null ? new File(name) : new File(parent, name);
    }

    private File getV4SignatureOutputFile(File inputApk, File output) {
        String fileName = inputApk.getName();
        String formattedName = fileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", ".idsig");
        String idsigName;
        if (fileName.equals(formattedName)) {
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot == -1) {
                idsigName = fileName + "_signed";
            } else {
                idsigName = fileName.substring(0, lastDot) + "_signed." + fileName.substring(lastDot + 1);
            }
        } else {
            idsigName = formattedName;
        }
        return resolveSibling(output, idsigName);
    }

    private void finishInPlaceSign(File inputApk, File output) throws IOException {
        if (!inputApk.delete()) {
            throw new IOException("Failed to delete original file " + inputApk.getPath());
        }
        if (!output.renameTo(inputApk)) {
            throw new IOException("Failed to move signed file to " + inputApk.getPath());
        }
    }

    private static boolean isPemEncoded(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII).contains("-----BEGIN");
    }

    private static byte[] extractPemBlock(byte[] bytes, Pattern pattern) {
        Matcher matcher = pattern.matcher(new String(bytes, StandardCharsets.US_ASCII));
        if (matcher.find()) {
            return Base64.decode(matcher.group(1).replaceAll("\\s", ""), Base64.DEFAULT);
        }
        return null;
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (InputStream in = FileUtils.getInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}