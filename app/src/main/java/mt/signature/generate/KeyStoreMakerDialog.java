/*
 * JKS-SignKey-Generator , Android jks key generator with optional certificate details
 * Copyright 2024, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */

package mt.signature.generate;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.PasswordEncryptor;

public class KeyStoreMakerDialog extends DialogFragment {

    private static final String PREF_NAME = "KeyStore";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_KEY_SIZE = 1024;
    private static final int MAX_KEY_SIZE = 8192;
    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final int MAX_VALIDITY_YEARS = 100;

    private SharedPreferences s;
    private ProgressDialog progress;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Views
    private LinearLayout linear1;
    private LinearLayout linear12;
    private LinearLayout fab;
    private ScrollView vscroll3;
    private LinearLayout linear2;
    private LinearLayout linear13;
    private TextInputLayout textinputlayout1;
    private TextInputLayout textinput_keyName;
    private TextInputLayout textinput2;
    private TextInputLayout textinput3;
    private TextInputLayout textinput4;
    private TextInputLayout textinput5;
    private TextInputLayout textinput6;
    private CompoundButton moreOption;
    private LinearLayout linear_more;
    private TextView copyright;
    private CheckBox generatePairKeys;
    private CheckBox generateJKS;
    private EditText directory;
    private EditText key_name;
    private EditText storePass;
    private EditText keyPass;
    private EditText keySize;
    private EditText date;
    private EditText commonName;
    private TextInputLayout textinput7;
    private TextInputLayout textinput8;
    private TextInputLayout textinput9;
    private TextInputLayout textinput10;
    private TextInputLayout textinput11;
    private EditText organizationUnit;
    private EditText organizationName;
    private EditText localityName;
    private EditText stateName;
    private EditText country;
    private ImageView fab_icon;
    private TextView fab_text;

    private OnKeyGeneratedListener listener;

    public interface OnKeyGeneratedListener {
        void onKeyGenerated(KeyParam keyParam);
        void onError(String error);
    }

    public static KeyStoreMakerDialog newInstance() {
        return new KeyStoreMakerDialog();
    }

    public void setOnKeyGeneratedListener(OnKeyGeneratedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.key_store_maker, null);
        initializeViews(view);
        initializeLogic();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setView(view);
        builder.setPositiveButton(R.string.generate_key, (dialog, which) -> {
            // FIX BUG 9: Cek activity tidak null
            if (getActivity() == null || isDetached()) return;
            
            // Validasi dulu sebelum start
            if (!validateInputs()) return;
            
            generateKeys();
            
            // FIX BUG 7: Save biometric pref setelah validasi sukses
            CompoundButton biometrics = view.findViewById(R.id.cb_save_password);
            boolean useBiometrics = biometrics.isChecked();
            s.edit().putBoolean("useBiometrics", useBiometrics).apply();
            if (useBiometrics) {
                s.edit().putString("keyPass", PasswordEncryptor.encryptString(keyPass.getText().toString())).apply();
            }
        });

        return builder.create();
    }

    private void initializeViews(View view) {
        linear1 = view.findViewById(R.id.linear1);
        view.findViewById(R.id.cb_save_password).setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? View.VISIBLE : View.GONE);
        linear2 = view.findViewById(R.id.linear2);
        linear13 = view.findViewById(R.id.linear13);
        textinputlayout1 = view.findViewById(R.id.textinputlayout1);
        textinput_keyName = view.findViewById(R.id.textinput_keyName);
        textinput2 = view.findViewById(R.id.textinput2);
        textinput3 = view.findViewById(R.id.textinput3);
        textinput4 = view.findViewById(R.id.textinput4);
        textinput5 = view.findViewById(R.id.textinput5);
        textinput6 = view.findViewById(R.id.textinput6);
        moreOption = view.findViewById(R.id.switch1);
        linear_more = view.findViewById(R.id.linear_more);
        copyright = view.findViewById(R.id.copyright);
        copyright.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/developer-krushna/JKS-SignKey-Generator"))));
        generatePairKeys = view.findViewById(R.id.checkbox1);
        generateJKS = view.findViewById(R.id.checkbox2);
        directory = view.findViewById(R.id.directory);
        key_name = view.findViewById(R.id.key_name);
        storePass = view.findViewById(R.id.storePass);
        keyPass = view.findViewById(R.id.keyPass);
        keySize = view.findViewById(R.id.keySize);
        date = view.findViewById(R.id.date);
        commonName = view.findViewById(R.id.commonName);
        textinput7 = view.findViewById(R.id.textinput7);
        textinput8 = view.findViewById(R.id.textinput8);
        textinput9 = view.findViewById(R.id.textinput9);
        textinput10 = view.findViewById(R.id.textinput10);
        textinput11 = view.findViewById(R.id.textinput11);
        organizationUnit = view.findViewById(R.id.organizationUnit);
        organizationName = view.findViewById(R.id.organizationName);
        localityName = view.findViewById(R.id.localityName);
        stateName = view.findViewById(R.id.stateName);
        country = view.findViewById(R.id.country);

        s = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    private void initializeLogic() {
        country.setText(requireContext().getResources().getConfiguration().locale.getCountry());
        setupListeners();
    }

    private void setupListeners() {
        moreOption.setOnCheckedChangeListener((buttonView, isChecked) -> {
            linear_more.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                country.setText("");
                organizationName.setText("");
                organizationUnit.setText("");
                localityName.setText("");
                stateName.setText("");
            } else {
                country.setText(requireContext().getResources().getConfiguration().locale.getCountry());
            }
        });

        generateJKS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            textinput2.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            textinput3.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        addTextWatcher(key_name, textinput_keyName);
        addTextWatcher(storePass, textinput2);
        addTextWatcher(keyPass, textinput3);
        addTextWatcher(keySize, textinput4);
        addTextWatcher(date, textinput5);
        addTextWatcher(commonName, textinput6);
    }

    private void addTextWatcher(EditText editText, TextInputLayout textInputLayout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (textInputLayout != null) {
                    textInputLayout.setErrorEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * FIX BUG 7, 8: Validasi input yang lebih ketat.
     * Return true kalau semua valid.
     */
    private boolean validateInputs() {
        if (!generatePairKeys.isChecked() && !generateJKS.isChecked()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage("Pilih minimal satu output: Pair Keys (.pk8 + .x509.pem) atau JKS (.jks)")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return false;
        }

        String keyNameStr = key_name.getText().toString().trim();
        if (keyNameStr.isEmpty()) {
            textinput_keyName.setError("Nama key tidak boleh kosong");
            return false;
        }
        if (keyNameStr.contains("/") || keyNameStr.contains("\\") || keyNameStr.contains("..")) {
            textinput_keyName.setError("Nama key tidak boleh mengandung / \\ atau ..");
            return false;
        }

        // Validasi key size
        String keySizeStr = keySize.getText().toString().trim();
        if (keySizeStr.isEmpty()) {
            textinput4.setError("Key size tidak boleh kosong");
            return false;
        }
        try {
            int ks = Integer.parseInt(keySizeStr);
            if (ks < MIN_KEY_SIZE || ks > MAX_KEY_SIZE) {
                textinput4.setError("Key size harus antara " + MIN_KEY_SIZE + " - " + MAX_KEY_SIZE);
                return false;
            }
            if (ks % 8 != 0) {
                textinput4.setError("Key size harus kelipatan 8");
                return false;
            }
        } catch (NumberFormatException e) {
            textinput4.setError("Key size harus berupa angka");
            return false;
        }

        // FIX BUG 4: Validasi tanggal
        String dateStr = date.getText().toString().trim();
        if (dateStr.isEmpty()) {
            textinput5.setError("Masa berlaku tidak boleh kosong");
            return false;
        }
        try {
            long years = Long.parseLong(dateStr);
            if (years < 1 || years > MAX_VALIDITY_YEARS) {
                textinput5.setError("Masa berlaku harus antara 1 - " + MAX_VALIDITY_YEARS + " tahun");
                return false;
            }
        } catch (NumberFormatException e) {
            textinput5.setError("Masa berlaku harus berupa angka");
            return false;
        }

        if (commonName.getText().toString().trim().isEmpty()) {
            textinput6.setError("Common Name tidak boleh kosong");
            return false;
        }

        // FIX BUG 8: Validasi country 2 huruf
        if (moreOption instanceof CompoundButton && ((CompoundButton) moreOption).isChecked()) {
            String c = country.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!c.isEmpty() && c.length() != 2) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage("Country code harus 2 huruf (contoh: ID, US, CN)")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return false;
            }
        }

        if (generateJKS.isChecked()) {
            String sp = storePass.getText().toString();
            String kp = keyPass.getText().toString();
            if (sp.isEmpty()) {
                textinput2.setError("Store password tidak boleh kosong");
                return false;
            }
            // FIX BUG 7: Minimal 6 karakter
            if (sp.length() < MIN_PASSWORD_LENGTH) {
                textinput2.setError("Password minimal " + MIN_PASSWORD_LENGTH + " karakter");
                return false;
            }
            if (kp.isEmpty()) {
                textinput3.setError("Key password tidak boleh kosong");
                return false;
            }
            if (kp.length() < MIN_PASSWORD_LENGTH) {
                textinput3.setError("Password minimal " + MIN_PASSWORD_LENGTH + " karakter");
                return false;
            }
        }

        return true;
    }

    private void generateKeys() {
        showProgress();

        Activity c = getActivity();
        if (c == null) {
            if (progress != null) progress.dismiss();
            return;
        }

        new Thread(() -> {
            try {
                KeyParam keyParam = save();
                mainHandler.post(() -> {
                    if (progress != null && progress.isShowing()) progress.dismiss();
                    if (listener != null) {
                        listener.onKeyGenerated(keyParam);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (progress != null && progress.isShowing()) progress.dismiss();
                    new ErrorUtil(c).showError(e);
                    if (listener != null) {
                        listener.onError(e.toString());
                    }
                });
            }
        }).start();
    }

    private void showProgress() {
        if (getContext() == null) return;
        progress = new ProgressDialog(requireContext());
        progress.setMessage(getString(R.string.generating));
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setCancelable(false);
        progress.show();
    }

    private KeyParam save() throws Exception {
        KeyParam keyParam = new KeyParam();

        String obj = keySize.getText().toString().trim();
        keyParam.keySize = obj.isEmpty() ? DEFAULT_KEY_SIZE : Integer.parseInt(obj);

        String directoryStr = directory.getText().toString();
        // Pastikan ada separator sebelum nama file
        if (!directoryStr.isEmpty() && !directoryStr.endsWith("/")) {
            directoryStr = directoryStr + "/";
        }
        String basePath = Environment.getExternalStorageDirectory() +
                directoryStr +
                key_name.getText().toString().trim();
        keyParam.keyPath = basePath + ".pk8";
        keyParam.certOrAlias = basePath + ".x509.pem";
        keyParam.alias = key_name.getText().toString().trim();
        keyParam.jksPath = basePath + ".jks";
        keyParam.storePass = storePass.getText().toString();
        keyParam.keyPass = keyPass.getText().toString();
        keyParam.commonName = commonName.getText().toString().trim();
        keyParam.organizationUnit = organizationUnit.getText().toString().trim();
        keyParam.organizationName = organizationName.getText().toString().trim();
        keyParam.localityName = localityName.getText().toString().trim();
        keyParam.stateName = stateName.getText().toString().trim();
        keyParam.country = country.getText().toString().trim().toUpperCase(Locale.ROOT);
        // FIX BUG 2: Pakai tahun langsung, nanti dikonversi pakai Calendar di generateKey
        keyParam.days = Long.parseLong(date.getText().toString().trim());

        generateKey(keyParam);
        return keyParam;
    }

    private void generateKey(KeyParam keyParam) throws Exception {
        KeyPairGenerator instance = KeyPairGenerator.getInstance("RSA");
        instance.initialize(keyParam.keySize, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair generateKeyPair = instance.generateKeyPair();
        PublicKey publicKey = generateKeyPair.getPublic();
        PrivateKey privateKey = generateKeyPair.getPrivate();

        CertificateExtensions certificateExtensions = new CertificateExtensions();
        certificateExtensions.set("SubjectKeyIdentifier",
                new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));

        StringBuilder x500NameBuilder = new StringBuilder("CN=").append(keyParam.commonName);
        if (keyParam.organizationName != null && !keyParam.organizationName.isEmpty()) {
            x500NameBuilder.append(", O=").append(keyParam.organizationName);
        }
        if (keyParam.organizationUnit != null && !keyParam.organizationUnit.isEmpty()) {
            x500NameBuilder.append(", OU=").append(keyParam.organizationUnit);
        }
        if (keyParam.localityName != null && !keyParam.localityName.isEmpty()) {
            x500NameBuilder.append(", L=").append(keyParam.localityName);
        }
        if (keyParam.stateName != null && !keyParam.stateName.isEmpty()) {
            x500NameBuilder.append(", ST=").append(keyParam.stateName);
        }
        if (keyParam.country != null && !keyParam.country.isEmpty()) {
            x500NameBuilder.append(", C=").append(keyParam.country);
        }
        X500Name x500Name = new X500Name(x500NameBuilder.toString());

        // FIX BUG 2: Pakai Calendar.add(YEAR, n) biar leap year ke-handle
        Date startDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.YEAR, (int) keyParam.days);
        Date endDate = cal.getTime();

        certificateExtensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(startDate, endDate));

        // FIX BUG 1: Generate cert SEKALI saja
        X509Certificate generatedCert = generateCert(privateKey, publicKey, x500Name,
                startDate, endDate, certificateExtensions);

        if (generatePairKeys.isChecked()) {
            writeCertificate(privateKey, generatedCert, keyParam);
        }
        // FIX BUG 1: Pakai generatedCert yang sama, bukan generate ulang
        if (generateJKS.isChecked()) {
            writeJks(privateKey, generatedCert, keyParam);
        }
    }

    private X509Certificate generateCert(PrivateKey privateKey, PublicKey publicKey,
                                         X500Name x500Name, Date date, Date date2,
                                         CertificateExtensions certificateExtensions) throws Exception {
        String str = "SHA512withRSA";
        try {
            CertificateValidity certificateValidity = new CertificateValidity(date, date2);
            X509CertInfo x509CertInfo = new X509CertInfo();
            x509CertInfo.set("version", new CertificateVersion(2));
            // FIX BUG 6: Pakai SecureRandom untuk serial number
            SecureRandom sr = new SecureRandom();
            x509CertInfo.set("serialNumber", new CertificateSerialNumber(sr.nextInt() & Integer.MAX_VALUE));
            x509CertInfo.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(str)));
            x509CertInfo.set("subject", new CertificateSubjectName(x500Name));
            x509CertInfo.set("key", new CertificateX509Key(publicKey));
            x509CertInfo.set("validity", certificateValidity);
            x509CertInfo.set("issuer", new CertificateIssuerName(x500Name));

            if (certificateExtensions != null) {
                x509CertInfo.set("extensions", certificateExtensions);
            }

            X509CertImpl x509CertImpl = new X509CertImpl(x509CertInfo);
            x509CertImpl.sign(privateKey, str);
            return x509CertImpl;
        } catch (IOException e) {
            throw new CertificateEncodingException("getSelfCert: " + e.getMessage());
        }
    }

    /**
     * FIX BUG 3: Pakai FileUtils.getOutputStream() biar support Shizuku/root path.
     */
    private void writeCertificate(PrivateKey privateKey, X509Certificate x509Certificate,
                                  KeyParam keyParam) throws Exception {
        File keyFile = new File(keyParam.keyPath);
        File parent = keyFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Tulis private key (.pk8)
        try (OutputStream key = FileUtils.getOutputStream(keyFile)) {
            key.write(privateKey.getEncoded());
        }

        // Tulis certificate (.x509.pem)
        File certFile = new File(keyParam.certOrAlias);
        try (OutputStream cery = FileUtils.getOutputStream(certFile)) {
            cery.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            byte[] encoded = Base64.encode(x509Certificate.getEncoded(), Base64.NO_WRAP);
            cery.write(encoded);
            cery.write("\n-----END CERTIFICATE-----\n".getBytes());
            cery.flush();
        }
    }

    private void writeJks(PrivateKey privateKey, X509Certificate x509Certificate,
                          KeyParam keyParam) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] storePass = keyParam.storePass.toCharArray();

        File file = new File(keyParam.jksPath);
        // FIX BUG 3: Handle kalau file existing
        if (file.exists() && file.length() > 0) {
            try (InputStream fis = FileUtils.getInputStream(file)) {
                keyStore.load(fis, storePass);
            } catch (Exception e) {
                // Kalau file corrupt atau password salah, mulai dari awal
                keyStore.load(null, storePass);
            }
        } else {
            keyStore.load(null, storePass);
        }

        char[] keyPass = keyParam.keyPass.toCharArray();
        keyStore.setKeyEntry(keyParam.alias, privateKey, keyPass, new Certificate[]{x509Certificate});

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (OutputStream fos = FileUtils.getOutputStream(file)) {
            keyStore.store(fos, storePass);
        }
    }

    public static class KeyParam {
        public String certOrAlias;
        public String alias;
        public String jksPath;
        public String commonName;
        public String country;
        public long days;
        public String keyPass;
        public String keyPath;
        public int keySize;
        public String localityName;
        public String organizationName;
        public String organizationUnit;
        public String stateName;
        public String storePass;
    }
}
