package io.github.abdurazaaqmohammed.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import mt.signature.generate.KeyStoreMakerDialog;

public class SignatureKeyDialog {

    public static void show(MainActivity activity) {
        show(activity, null, false);
    }

    public static void show(MainActivity activity, File file, boolean isSplitApk) {
        File keysDir = new File(
                Environment.getExternalStorageDirectory().getPath() + File.separatorChar + "MT2"
                        + File.separatorChar + "keys"
        );

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        String PREF_SIGNATURE_PATHS = "signature_key_paths";

        List<String> signaturePaths = new ArrayList<>();
        signaturePaths.addAll(getKeystoreFiles(keysDir));

        signaturePaths.addAll(getSavedPaths(prefs, PREF_SIGNATURE_PATHS));

        signaturePaths = new ArrayList<>(dedupe(signaturePaths));
        Collections.sort(signaturePaths);
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.dialog_signature_key, null, false);

        TextView signedBy = view.findViewById(R.id.signerInput);
        signedBy.setText(prefs.getString("signedBy", "Android Gradle 8.0.2"));

        TextView passwordEt = view.findViewById(R.id.et_password);
        /*MaterialCheckBox cbSavePassword = view.findViewById(R.id.cb_save_password);
        boolean savePassword = prefs.getBoolean("save_password", false);
        cbSavePassword.setChecked(savePassword);
        if(savePassword) passwordEt.setText(prefs.getString("keyPass", "android"));*/
        AutoCompleteTextView actv = view.findViewById(R.id.actv_signature_key);
        MaterialButton btnPick = view.findViewById(R.id.btn_pick_file);

        CompoundButton view1 = view.findViewById(R.id.v1);
        CompoundButton view2 = view.findViewById(R.id.v2);
        CompoundButton view3 = view.findViewById(R.id.v3);
        CompoundButton view4 = view.findViewById(R.id.v4);
        view1.setChecked(prefs.getBoolean("v1", true));
        view2.setChecked(prefs.getBoolean("v2", true));
        view3.setChecked(prefs.getBoolean("v3", true));
        view4.setChecked(prefs.getBoolean("v4", false));

        CompoundButton cbBiometric = view.findViewById(R.id.cb_biometric);
        cbBiometric.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? View.VISIBLE : View.GONE);
        cbBiometric.setChecked(prefs.getBoolean("useBiometrics", false));

        ArrayAdapter<String> ddAdapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_dropdown_item_1line,
                getKeystoreFiles(keysDir)
        );

        String savedKeyPath = prefs.getString("keyPath", null);
        if (!TextUtils.isEmpty(savedKeyPath) && signaturePaths.contains(savedKeyPath)) {
            actv.setText(savedKeyPath, false);
        }
        actv.setAdapter(ddAdapter);

        final String[] pickedPath = new String[1];
        pickedPath[0] = !TextUtils.isEmpty(savedKeyPath) ? savedKeyPath : null;

        List<String> finalSignaturePaths = signaturePaths;
        btnPick.setOnClickListener(v -> {
            DialogProperties properties = new DialogProperties();
            properties.selection_mode = DialogConfigs.SINGLE_MODE;
            properties.selection_type = DialogConfigs.FILE_SELECT;
            properties.root = new File(keysDir.getPath());
            properties.error_dir = new File(keysDir.getPath());
            properties.offset = new File(keysDir.getPath());

            properties.extensions = new String[]{"jks", "keystore", "p12", "pfx"};

            FilePickerDialog fpd = new FilePickerDialog(activity, properties);
            fpd.setDialogSelectionListener(files -> {
                if (files == null || files.length == 0 || files[0] == null) return;

                String path = files[0];
                pickedPath[0] = path;
                actv.setText(path, false);

                if (!finalSignaturePaths.contains(path)) {
                    finalSignaturePaths.add(path);
                    Collections.sort(finalSignaturePaths);

                    ddAdapter.clear();
                    ddAdapter.addAll(finalSignaturePaths);
                    ddAdapter.notifyDataSetChanged();
                }

                prefs.edit()
                                .putStringSet(PREF_SIGNATURE_PATHS, new HashSet<>(finalSignaturePaths))
                                .putString("keyPath", path).apply();

                Toast.makeText(activity, "Signature file set", Toast.LENGTH_SHORT).show();
            });
            fpd.show();
        });

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(view);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Signature Key")
                .setView(scrollView)
                .setPositiveButton("OK", (d, which) -> {
                    String selectedPath = actv.getText() != null ? actv.getText().toString() : null;
                    String password = passwordEt.getText() != null ? passwordEt.getText().toString() : null;
                    boolean biometricChecked = cbBiometric.isChecked();
                    boolean wasUsingBiometrics = prefs.getBoolean("useBiometrics", false);
                    boolean useBiometrics;

                    if (TextUtils.isEmpty(selectedPath)) selectedPath = pickedPath[0];

                    if (TextUtils.isEmpty(selectedPath)) {
                        Toast.makeText(activity, "No signature selected", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!new File(selectedPath).exists()) {
                        Toast.makeText(activity, "Invalid file path", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (biometricChecked) {
                        if (!TextUtils.isEmpty(password)) {
                            if (!verifyKeystorePassword(new File(selectedPath), password)) {
                                Toast.makeText(activity, "Invalid password", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            prefs.edit().putString("keyPass", PasswordEncryptor.encryptString(password)).apply();
                            prefs.edit().putBoolean("useBiometrics", true).apply();
                            useBiometrics = true;
                        } else if (wasUsingBiometrics) {
                            useBiometrics = true;
                        } else {
                            Toast.makeText(activity, "Enter password first to enable biometrics", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        if (wasUsingBiometrics) {
                            prefs.edit().putBoolean("useBiometrics", false).remove("keyPass").apply();
                        }
                        if (TextUtils.isEmpty(password)) {
                            Toast.makeText(activity, "No password entered", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        useBiometrics = false;
                    }

                    boolean v1 = view1.isChecked();
                    boolean v2 = view2.isChecked();
                    boolean v3 = view3.isChecked();
                    boolean v4 = view4.isChecked();
                    String signedByS = signedBy.getText().toString();
                    prefs.edit().putBoolean("v1", v1).putBoolean("v2", v2).putBoolean("v3", v3).putBoolean("v4", v4).putString("signedBy", signedByS).apply();

                    if(file != null) {
                        ProgressManager pm = new ProgressManager(activity, true).show();
                        if(useBiometrics && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Executor executor = ContextCompat.getMainExecutor(activity);
                            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationError(int errorCode,
                                                                  @NonNull CharSequence errString) {
                                    super.onAuthenticationError(errorCode, errString);
                                    Toast.makeText(activity, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onAuthenticationSucceeded(
                                        @NonNull BiometricPrompt.AuthenticationResult result) {
                                    super.onAuthenticationSucceeded(result);
                                    Toast.makeText(activity, "Authentication succeeded!", Toast.LENGTH_SHORT).show();
                                    new Thread(() -> {
                                        try {
                                            String storedPass = PasswordEncryptor.decryptString(prefs.getString("keyPass", "android"));
                                            SignWrapper signWrapper = new SignWrapper(new File(prefs.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", activity).getPath())),
                                                    storedPass, v1, v2, v3, v4, signedByS);
                                            File cacheDir = new File(activity.getCacheDir(), UUID.randomUUID().toString());
                                            String sigFileName = file.getName();
                                            File file2 = new File(file.getParentFile(), sigFileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", "_signed.$1"));
                                            if (isSplitApk) try (ArchiveFile archiveFile = new ArchiveFile(file)) {
                                                for (InputSource inputSource : archiveFile.getInputSources(archiveEntry -> archiveEntry.getName().endsWith(".apk"))) {
                                                    pm.setText(activity.getString(R.string.signing_, inputSource.getAlias()));
                                                    File inputApk = inputSource.toFile(cacheDir);
                                                    try(InputStream is = inputSource.openStream()) { FileUtils.copyFile(is, inputApk); }
                                                    signWrapper.signApk(inputApk, new File(cacheDir, inputSource.getName().replace(".apk", "_signed.apk")));
                                                }
                                                for (File file1 : cacheDir.listFiles()) {
                                                    if (file1.getName().endsWith("_signed.apk")) {
                                                        File dest = new File(file1.getPath().replace("_signed.apk", ".apk"));
                                                        if (dest.exists()) dest.delete();
                                                        file1.renameTo(dest);
                                                    } else file1.delete();
                                                }
                                                File signedSplitApk = FileUtils.getUnusedFile(file2);
                                                try (ZipFile zf = new ZipFile(signedSplitApk)) {
                                                    zf.addFiles(Arrays.asList(cacheDir.listFiles()));
                                                }
                                            } else signWrapper.signApk(file, FileUtils.getUnusedFile(file2));
                                            pm.dismiss();
                                            activity.runOnUiThread(() -> {
                                                Toast.makeText(activity, activity.getString(R.string.signed, sigFileName), Toast.LENGTH_SHORT).show();
                                                activity.reloadCurrentFolder();
                                            });
                                        } catch (Exception e) {
                                            pm.dismiss();
                                            new ErrorUtil(activity).showError(e);
                                        }
                                    }).start();  }

                                @Override
                                public void onAuthenticationFailed() {
                                    super.onAuthenticationFailed();
                                    Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                            BiometricPrompt.PromptInfo.Builder auth = new BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Authenticate Signature")
                                    .setSubtitle("Authenticate to use sign key");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                auth.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                            } else auth.setDeviceCredentialAllowed(true);
                            biometricPrompt.authenticate(auth.build());
                        } else new Thread(() -> {
                            try {
                                SignWrapper signWrapper = new SignWrapper(new File(prefs.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", activity).getPath())),
                                        password, v1, v2, v3, v4, signedByS);
                                File cacheDir = new File(activity.getCacheDir(), UUID.randomUUID().toString());
                                String sigFileName = file.getName();
                                File file2 = new File(file.getParentFile(), sigFileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", "_signed.$1"));
                                if (isSplitApk) try (ArchiveFile archiveFile = new ArchiveFile(file)) {
                                    for (InputSource inputSource : archiveFile.getInputSources(archiveEntry -> archiveEntry.getName().endsWith(".apk"))) {
                                        pm.setText(activity.getString(R.string.signing_, inputSource.getAlias()));
                                        File inputApk = inputSource.toFile(cacheDir);
                                        try(InputStream is = inputSource.openStream()) { FileUtils.copyFile(is, inputApk); }
                                        signWrapper.signApk(inputApk, new File(cacheDir, inputSource.getName().replace(".apk", "_signed.apk")));
                                    }
                                    for (File file1 : cacheDir.listFiles()) {
                                        if (file1.getName().endsWith("_signed.apk")) {
                                            File dest = new File(file1.getPath().replace("_signed.apk", ".apk"));
                                            if (dest.exists()) dest.delete();
                                            file1.renameTo(dest);
                                        } else file1.delete();
                                    }
                                    File signedSplitApk = FileUtils.getUnusedFile(file2);
                                    try (ZipFile zf = new ZipFile(signedSplitApk)) {
                                        zf.addFiles(Arrays.asList(cacheDir.listFiles()));
                                    }
                                } else signWrapper.signApk(file, FileUtils.getUnusedFile(file2));
                                pm.dismiss();
                                activity.runOnUiThread(() -> {
                                    Toast.makeText(activity, activity.getString(R.string.signed, sigFileName), Toast.LENGTH_SHORT).show();
                                    activity.reloadCurrentFolder();
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                new ErrorUtil(activity).showError(e);
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null).setNeutralButton("New Key", (dialog, which) -> {
                    KeyStoreMakerDialog ksmd = KeyStoreMakerDialog.newInstance();
                    ksmd.setOnKeyGeneratedListener(new KeyStoreMakerDialog.OnKeyGeneratedListener() {
                        @Override
                        public void onKeyGenerated(KeyStoreMakerDialog.KeyParam keyParam) {
                            String keyPath = keyParam.keyPath;
                            String jksPath = keyParam.jksPath;
                            File pk8 = new File(keyPath);
                            File jks = new File(jksPath);
                            String path;
                            if(!pk8.exists()) path = jksPath;
                            else if(!jks.exists()) path = keyPath;
                            else if (pk8.lastModified() < jks.lastModified()) path = jksPath;
                            else path = keyPath;
                            prefs.edit().putString("keyPath", path).apply();
                            Toast.makeText(activity, "Keys generated: " + path, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String error) {
                            new ErrorUtil(activity).showError(new Throwable(error));
                        }
                    });
                    ksmd.show(activity.getSupportFragmentManager(), "KeyStoreMakerDialog");
                })
                .show();
    }

    private static List<String> getKeystoreFiles(File dir) {
        List<String> out = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return out;

        File[] files = dir.listFiles();
        if (files == null) return out;

        List<String> exts = Arrays.asList("jks", "keystore", "p12", "pfx");
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            String name = f.getName().toLowerCase();
            int dot = name.lastIndexOf('.');
            if (dot < 0) continue;
            String ext = name.substring(dot + 1);
            if (exts.contains(ext)) out.add(f.getAbsolutePath());
        }
        return out;
    }

    private static List<String> getSavedPaths(SharedPreferences prefs, String key) {
        java.util.Set<String> set = prefs.getStringSet(key, null);
        if (set == null) return new ArrayList<>();
        return new ArrayList<>(set);
    }

    private static java.util.Set<String> dedupe(List<String> list) {
        java.util.Set<String> s = new java.util.HashSet<>();
        if (list == null) return s;
        for (String x : list) if (x != null) s.add(x);
        return s;
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
}
