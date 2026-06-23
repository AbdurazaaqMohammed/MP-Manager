package io.github.abdurazaaqmohammed.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;

import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.apksig.ApkVerifier;
import com.apk.axml.APKParser;
import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.apk.axml.serializableItems.XMLEntry;
import com.faith.apkinstaller.APKInstallHelper;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.decompile.DecompileOptions;
import com.reandroid.apkeditor.decompile.Decompiler;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.RefactorOptions;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.TextEditorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.LogUtil;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.RunUtil;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

public class MainFilesArrayAdapter extends ArrayAdapter<Object> {
    private void showEditManifestDialog(File apkFile) {
        final Resources rss = context.rss;
        View quickEditDialog = LayoutInflater.from(context).inflate(R.layout.quick_edit_dialog, null, false);
        quickEditDialog.findViewById(R.id.app_lancer_icon).setOnClickListener(v -> editLauncherIcon(apkFile));

        quickEditDialog.findViewById(R.id.install_location_dropdown);
        AutoCompleteTextView installLocationTextView = quickEditDialog.findViewById(R.id.install_location);
        List<XMLEntry> entries = decodeManifest(apkFile);

        String installLoc = "";
        TextInputEditText appNameInput = quickEditDialog.findViewById(R.id.appNameInput);
        TextInputEditText verCodeInput = quickEditDialog.findViewById(R.id.verCodeInput);
        TextInputEditText verNameInput = quickEditDialog.findViewById(R.id.verNameInput);
        AutoCompleteTextView targetSdk = quickEditDialog.findViewById(R.id.targetSdk);
        AutoCompleteTextView minSdk = quickEditDialog.findViewById(R.id.minSdk);

        final String[] versions = {
                "1 (BASE/SDK 1)", "1.1 (BASE_1_1/SDK 2)", "1.5 (Cupcake/SDK 3)", "1.6 (Donut/SDK 4)",
                "2 (Eclair/SDK 5)", "2.0.1 (Eclair_0_1/SDK 6)", "2.1 (Eclair_MR1/SDK 7)", "2.2 (Froyo/SDK 8)",
                "2.3 (Gingerbread/SDK 9)", "2.3.3 (Gingerbread_MR1/SDK 10)", "3 (Honeycomb/SDK 11)",
                "3.1 (Honeycomb_MR1/SDK 12)", "3.2 (Honeycomb_MR2/SDK 13)", "4 (Ice Cream Sandwich/SDK 14)",
                "4.0.3 (Ice Cream Sandwich_MR1/SDK 15)", "4.1 (Jellybean/SDK 16)", "4.2 (Jellybean_MR1/SDK 17)",
                "4.3 (Jellybean_MR2/SDK 18)", "4.4 (Kitkat/SDK 19)", "4.4W (Kitkat Watch/SDK 20)",
                "5 (Lollipop/SDK 21)", "5.1 (Lollipop_MR1/SDK 22)", "6 (Marshmallow/SDK 23)",
                "7 (Nougat/SDK 24)", "7.1 (Nougat_MR1/SDK 25)", "8 (Oreo/SDK 26)",
                "8.1 (Oreo_MR1/SDK 27)", "9 (Pie/SDK 28)", "10 (Q/SDK 29)",
                "11 (R/SDK 30)", "12 (S/SDK 31)", "12L (S_V2/SDK 32)", "13 (Tiramisu/SDK 33)",
                "14 (Upside Down Cake/SDK 34)", "15 (Vanilla Ice Cream/SDK 35)", "16 (Baklava/SDK 36)", "17 (Cinnamon Bun/SDK 37)"
        };


        String minSdkVersion = "", targetSdkVersion = "", verCode = "", verName = "", appName = "";
        boolean foundMinSdk = false;
        for (XMLEntry e : entries) {
            String tag = e.getTag();
            if (tag.contains("android:installLocation")) installLoc = e.getValue();
            else if (tag.contains("android:label")) appNameInput.setText(appName = e.getValue());
            else if (tag.contains("android:versionCode")) verCodeInput.setText(verCode = e.getValue());
            else if (tag.contains("android:versionName")) verNameInput.setText(verName = e.getValue());
            else if (!foundMinSdk && tag.contains("android:minSdkVersion")) {
                foundMinSdk = true; // Avoid getting wrong minsdk from other property
                int minSdkVer = Integer.parseInt(minSdkVersion = e.getValue());
                minSdk.setText(rss.getString(R.string.android_ver_text, versions[minSdkVer-1], minSdkVer));
            }
            else if (tag.contains("android:targetSdkVersion")) {
                int targetSdkVer = Integer.parseInt(targetSdkVersion = e.getValue());
                targetSdk.setText(rss.getString(R.string.android_ver_text, versions[targetSdkVer-1], targetSdkVer));
            }
        }
        final String[] minSdkVersionSelected = new String[1];
        final String[] targetSdkVersionSelected = new String[1];
        minSdk.setOnItemClickListener((parent, view, position, id) -> minSdkVersionSelected[0] = (position+1) +"");
        targetSdk.setOnItemClickListener((parent, view, position, id) -> targetSdkVersionSelected[0] = (position+1) +"");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, R.layout.dropdownitem, versions) {
            @NonNull
            @Override
            public View getView(int position1, @Nullable View convertView, @NonNull ViewGroup parent1) {
                if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.dropdownitem, parent1, false);
                TextView view1 = (TextView) convertView;
                view1.setText(rss.getString(R.string.android_ver_text, versions[position1], position1+1));
                return convertView;
            }
        };
        targetSdk.setAdapter(adapter);
        minSdk.setAdapter(adapter);
        String[] items = rss.getStringArray(R.array.install_locations);
        if("".equals(installLoc)) installLocationTextView.setText(items[3]);
        else installLocationTextView.setText(items[Integer.parseInt(installLoc)]);

        final String[] installLocationSelected = new String[1];
        installLocationTextView.setOnItemClickListener((parent, view, position, id) -> installLocationSelected[0] = position +"");
        installLocationTextView.setAdapter(new ArrayAdapter<String>(context, R.layout.dropdownitem, items) {
            @NonNull @Override
            public View getView(int position1, @Nullable View convertView, @NonNull ViewGroup parent1) {
                if (convertView == null)
                    convertView = LayoutInflater.from(context).inflate(R.layout.dropdownitem, parent1, false);
                TextView view1 = (TextView) convertView;
                view1.setText(items[position1]);
                return convertView;
            }
        });

        quickEditDialog.findViewById(R.id.editall).setOnClickListener(v -> editAllManifestEntries(apkFile));

        String finalAppName = appName;
        String finalVerCode = verCode;
        String finalVerName = verName;
        String finalTargetSdkVersion = targetSdkVersion;
        String finalMinSdkVersion = minSdkVersion;
        String finalInstallLoc = installLoc;
        final boolean[] sign = new boolean[1];
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        MaterialCheckBox autosign = quickEditDialog.findViewById(R.id.autosign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        quickEditDialog.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

        AlertDialog menuDialog = dialogUtil.getDialogBuilder()
                .setCustomTitle(uiHelper.getTitle("Fast Edit Attributes"))
                .setPositiveButton("Done", (dialog, which) -> {
                    CharSequence appNameInputText = appNameInput.getText();
                    String appNameSelected = TextUtils.isEmpty(appNameInputText) ? "" : appNameInputText.toString();
                    boolean appNameChanged = (!finalAppName.equals(appNameSelected));

                    CharSequence verCodeInputText = verCodeInput.getText();
                    String verCodeSelected = TextUtils.isEmpty(verCodeInputText) ? "" : verCodeInputText.toString();
                    boolean verCodeChanged = (!finalVerCode.equals(verCodeSelected));

                    CharSequence verNameInputText = verNameInput.getText();
                    String verNameSelected = TextUtils.isEmpty(verNameInputText) ? "" : verNameInputText.toString();
                    boolean verNameChanged = (!finalVerName.equals(verNameSelected));

                    boolean minSdkVersionChanged = minSdkVersionSelected[0] != null && (!finalMinSdkVersion.equals(minSdkVersionSelected[0]));
                    boolean targetSdkVersionChanged = targetSdkVersionSelected[0] != null && (!finalTargetSdkVersion.equals(targetSdkVersionSelected[0]));


                    boolean installLocationChanged = installLocationSelected[0] != null && !installLocationSelected[0].equals(finalInstallLoc);
                    StringBuilder sb = new StringBuilder();
                    String[] options = {
                            "App Launcher Icon",
                            "App Name",
                            "Install Location",
                            "Version Code",
                            "Version Name",
                            "Min SDK Version",
                            "Target SDK Version",
                            "Edit All Activities / Properties"
                    };

                    if(appNameChanged) sb.append(options[1]).append(", ");
                    if(installLocationChanged) sb.append(options[2]).append(", ");
                    if(verCodeChanged) sb.append(options[3]).append(", ");
                    if(verNameChanged) sb.append(options[4]).append(", ");
                    if(minSdkVersionChanged) sb.append(options[5]).append(", ");
                    if(targetSdkVersionChanged) sb.append(options[6]);
                    sb.append(" updated");
                    AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
                    dialogUtil.styleAlertDialog(progressDialog);
                    TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
                    progressText.setText(rss.getString(R.string.saving));

                    new RunUtil(context.handler, context, sb)
                            .runInBackground(() -> {
                                try {
                                    boolean foundMinSdk2 = false;
                                    boolean foundInstallLocation = false;
                                    int entryToRemove = 0;
                                    for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
                                        XMLEntry e = entries.get(i);
                                        String tag = e.getTag();
                                        if (appNameChanged && tag.contains("android:label"))
                                            e.setValue(appNameSelected);
                                        else if (verCodeChanged && tag.contains("android:versionCode"))
                                            e.setValue(verCodeSelected);
                                        else if (verNameChanged && tag.contains("android:versionName"))
                                            e.setValue(verNameSelected);
                                        else if (!foundMinSdk2 && minSdkVersionChanged && tag.contains("android:minSdkVersion")) {
                                            foundMinSdk2 = true;
                                            e.setValue(minSdkVersionSelected[0]);
                                        } else if (targetSdkVersionChanged && tag.contains("android:targetSdkVersion"))
                                            e.setValue(targetSdkVersionSelected[0]);
                                        else if (installLocationChanged && tag.contains("android:installLocation")) {
                                            foundInstallLocation = true;
                                            if (installLocationSelected[0].isEmpty()) entryToRemove = i;
                                            else e.setValue(installLocationSelected[0]);
                                        }
                                    }
                                    if(installLocationChanged) {
                                        if(foundInstallLocation) {
                                            if (entryToRemove != 0) entries.remove(entryToRemove);
                                        } else entries.add(4, new XMLEntry("android:installLocation", "=\"", installLocationSelected[0], "\""));
                                    }

                                    writeManifestEntries(apkFile, entries);
                                    if(sign[0]) {
                                        SignWrapper signWrapper = new SignWrapper(
                                                settings.getString("keyPath",
                                                        FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", context).getPath()),
                                                settings.getString("signatureKeyPassword", "android"), settings.getBoolean("v1", true),
                                                settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false));
                                        signWrapper.signApk(apkFile);
                                    }
                                    context.handler.post(progressDialog::dismiss);
                                    return true;
                                } catch (Exception e) {
                                    context.handler.post(progressDialog::dismiss);
                                    new ErrorUtil(context).showError(e);
                                    return false;
                                }
                            });

                })
                .setNegativeButton("Cancel", null)
                .setView(quickEditDialog)
                .create();
        dialogUtil.styleAlertDialog(menuDialog);
    }

    private void editLauncherIcon(File apkFile) {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = new File(DialogConfigs.DEFAULT_DIR);
        properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
        properties.offset = new File(DialogConfigs.DEFAULT_DIR);
        properties.extensions = new String[]{"png", "webp", "jpg", "jpeg"};
        FilePickerDialog fpd = new FilePickerDialog(context, properties);
        fpd.setTitle("Select Icon");
        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);

        fpd.setDialogSelectionListener(files -> new Thread(() -> {
            String iconPath = findIconPathInManifest(apkFile);

            try (InputStream is = FileUtils.getInputStream(files[0])) {
                context.handler.post(() -> {
                    dialogUtil.styleAlertDialog(progressDialog);
                    TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
                    progressText.setText(context.rss.getString(R.string.adding, files[0]));

                });
                replaceZipEntry(apkFile,
                        iconPath != null ? iconPath : "res/mipmap-xxhdpi-v4/ic_launcher.png",
                        is);
                context.handler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, "Icon changed", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                context.handler.post(() -> {
                    progressDialog.dismiss();
                    new ErrorUtil(context).showError(e);
                });
            }
        }).start());
        fpd.show();
    }

    private String findIconPathInManifest(File apkFile) {
        try (ZipFile zf = new ZipFile(apkFile)) {
            FileHeader manifestEntry = zf.getFileHeader("AndroidManifest.xml");
            if (manifestEntry == null) return null;
            try (InputStream mis = zf.getInputStream(manifestEntry)) {
                APKParser apkParser = new APKParser();
                apkParser.parse(apkFile.getPath(), context);
                List<ResEntry> decodedResources = apkParser.getDecodedResources();

                List<XMLEntry> entries = new aXMLDecoder(mis, decodedResources).decode();
                for (XMLEntry e : entries) {
                    if (e.getTag().contains("android:icon")) {
                        String val = e.getValue();
                        // This is not changing it on all screens sizes fix this
                        if (val.startsWith("res/")) return val;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void editAllManifestEntries(File apkFile) {
        new Thread(() -> {
            try {
                List<XMLEntry> entries = decodeManifest(apkFile);
                if (entries == null) {
                    context.handler.post(() -> Toast.makeText(context, "Could not decode AndroidManifest.xml", Toast.LENGTH_SHORT).show());
                    return;
                }
                context.handler.post(() -> showManifestTreeDialog(apkFile, entries));
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    @SuppressLint("SetTextI18n")
    private void showManifestTreeDialog(File apkFile, List<XMLEntry> entries) {
        ListView listView = new ListView(context);
        listView.setDividerHeight(1);

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount()          { return entries.size(); }
            @Override public Object getItem(int p)   { return entries.get(p); }
            @Override public long getItemId(int p)   { return p; }

            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                LinearLayout row;
                TextView label;
                CheckBox disableChk;
                ImageView editBtn;

                if (convertView == null) {
                    row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(8, 8, 8, 8);

                    disableChk = new CheckBox(context);
                    disableChk.setTag("chk");
                    disableChk.setPadding(0, 0, 8, 0);

                    label = new TextView(context);
                    label.setTag("lbl");
                    label.setTextSize(12);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    label.setLayoutParams(lp);
                    label.setSingleLine(false);
                    ColorUtil.setTextViewColor(label, Color.WHITE);

                    editBtn = new ImageView(context);
                    editBtn.setTag("btn");
                    editBtn.setImageResource(android.R.drawable.ic_menu_edit);
                    editBtn.setPadding(8, 0, 0, 0);

                    row.addView(disableChk);
                    row.addView(label);
                    row.addView(editBtn);
                    convertView = row;
                } else {
                    row        = (LinearLayout) convertView;
                    disableChk = row.findViewWithTag("chk");
                    label      = row.findViewWithTag("lbl");
                    editBtn    = row.findViewWithTag("btn");
                }

                XMLEntry entry = entries.get(pos);
                String text = entry.getText();
                label.setText(text);

                boolean isDisabled = isDisabledEntry(entry);
                disableChk.setOnCheckedChangeListener(null);
                disableChk.setChecked(isDisabled);
                disableChk.setOnCheckedChangeListener((btn, checked) -> {
                    toggleDisabled(entry, checked);
                    label.setText(entry.getText());
                });

                editBtn.setOnClickListener(v -> showEntryEditDialog(apkFile, entries, entry));

                return convertView;
            }
        };

        listView.setAdapter(adapter);

        AlertDialog d = dialogUtil.getDialogBuilder()
                .setCustomTitle(uiHelper.getTitle("Edit Manifest Entries"))
                .setView(listView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save All", (dlg, w) ->
                        new RunUtil(context.handler, context, "Manifest saved")
                                .runInBackground(() -> {
                                    try {
                                        writeManifestEntries(apkFile, entries);
                                        return true;
                                    } catch (Exception e) {
                                        new ErrorUtil(context).showError(e);
                                        return false;
                                    }
                                }))
                .create();
        dialogUtil.styleAlertDialog(d);
    }

    private boolean isDisabledEntry(XMLEntry entry) {
        return entry.getMiddleTag().contains("_disabled")
                || entry.getTag().contains("_disabled");
    }

    private void toggleDisabled(XMLEntry entry, boolean disable) {
        String current = entry.getValue();
        if (disable) {
            if (!current.startsWith("__DISABLED__")) {
                entry.setValue("__DISABLED__" + current);
            }
        } else {
            if (current.startsWith("__DISABLED__")) {
                entry.setValue(current.substring("__DISABLED__".length()));
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void showEntryEditDialog(File apkFile,
                                     List<XMLEntry> entries,
                                     XMLEntry entry) {

        String rawValue = entry.getValue().replace("__DISABLED__", "");

        EditText input = new EditText(context);
        input.setText(rawValue);
        uiHelper.styleEditText(input);

        AlertDialog d = dialogUtil.getDialogBuilder()
                .setCustomTitle(uiHelper.getTitle(
                        "Edit: " + entry.getMiddleTag().trim()))
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (dlg, w) -> {
                    String newVal = input.getText().toString();
                    boolean wasDisabled = entry.getValue().startsWith("__DISABLED__");
                    entry.setValue(wasDisabled ? "__DISABLED__" + newVal : newVal);
                })
                .create();
        dialogUtil.styleAlertDialog(d);
    }

    private List<XMLEntry> decodeManifest(File apkFile) {
        try (ZipFile zf = new ZipFile(apkFile)) {
            FileHeader manifestEntry = zf.getFileHeader("AndroidManifest.xml");
            if (manifestEntry == null) return null;
            try (InputStream is = zf.getInputStream(manifestEntry)) {
                return new aXMLDecoder(is).decode();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String readManifestAttrValue(File apkFile, String attrName) {
        List<XMLEntry> entries = decodeManifest(apkFile);
        if (entries == null) return "";
        for (XMLEntry e : entries) {
            if (e.getTag().contains(attrName)) return e.getValue();
        }
        return "";
    }

    private void writeManifestAttrValue(File apkFile, String attrName, String newValue) throws Exception {
        List<XMLEntry> entries = decodeManifest(apkFile);
        if (entries == null) throw new IOException("Failed to decode AndroidManifest.xml");

        boolean found = false;
        for (XMLEntry e : entries) {
            if (e.getTag().contains(attrName.split(":")[1])) {
                e.setValue(newValue);
                found = true;
            }
        }
        if (!found) {
            throw new IOException("Attribute " + attrName + " not found in manifest");
        }
        writeManifestEntries(apkFile, entries);
    }

    private void removeManifestAttr(File apkFile, String attrName) throws Exception {
        List<XMLEntry> entries = decodeManifest(apkFile);
        if (entries == null) throw new IOException("Failed to decode AndroidManifest.xml");
        for (int i = 0, listSize = entries.size(); i < listSize; i++) {
            XMLEntry item = entries.get(i);
            if (item.getTag().contains(attrName)) entries.remove(i);
        }
        writeManifestEntries(apkFile, entries);
    }

    private void writeManifestEntries(File apkFile, List<XMLEntry> entries) throws Exception {
        replaceZipEntry(apkFile, "AndroidManifest.xml", new aXMLEncoder().encodeString(entries, context));
    }

    private String appendDisabled(String middleTag) {
        int eqIdx = middleTag.lastIndexOf('=');
        if (eqIdx > 0) {
            return middleTag.substring(0, eqIdx) + "_disabled" + middleTag.substring(eqIdx);
        }
        return middleTag.trim().isEmpty() ? middleTag : middleTag + "_disabled";
    }

    private void replaceZipEntry(File apkFile, String entryPath, byte[] newBytes)
            throws IOException {
        ZipParameters zp = new ZipParameters();
        zp.setCompressionMethod(CompressionMethod.STORE);
        zp.setFileNameInZip(entryPath);
        try (ZipFile sourceZip = new ZipFile(apkFile); InputStream is = new ByteArrayInputStream(newBytes)) {
            sourceZip.addStream(is, zp);
        }
    }

    private void replaceZipEntry(File apkFile, String entryPath, InputStream is)
            throws IOException {
        ZipParameters zp = new ZipParameters();
        zp.setCompressionMethod(CompressionMethod.STORE);
        zp.setFileNameInZip(entryPath);
        try (ZipFile sourceZip = new ZipFile(apkFile)) {
            sourceZip.addStream(is, zp);
        }
    }

    private final MainActivity context;
    public final Object[] values;
    public boolean isInZip;
    public String currentZipPath;
    public final boolean pane1; //THIS IS WHETHER THE ADAPTER IS FOR PANE 1 OR 2 NOT THE LAST CLICKED PANE
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;

    private boolean isMultiSelectMode = false;

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    private final Set<Integer> selectedPositions = new HashSet<>();
    private Integer rangeStartPosition = null;

    private static Object[] getNewValues(Object[] values, File parentFile) {
        Object[] letUpDir = new File[values.length + 1];
        letUpDir[0] = parentFile;
        System.arraycopy(values, 0, letUpDir, 1, values.length);
        return letUpDir;
    }

    private static List<Object> getNewValues(List<Object> values, Object parentFile) {
        ArrayList<Object> letUpDir = new ArrayList<>(values.size() + 1);
        letUpDir.add(parentFile);
        letUpDir.addAll(values);
        return letUpDir;
    }

    private File[] getOldValues() {
        int newLength = values.length - 1;
        File[] oldValues = new File[newLength];
        System.arraycopy(values, 1, oldValues, 0, newLength);
        return oldValues;
    }

    public MainFilesArrayAdapter(MainActivity context, Object[] values, Object parent, boolean pane1, boolean isInZip,
            String currentZipPath) {
        super(context, android.R.layout.simple_list_item_1,
                values = isInZip ? values : getNewValues(values, (File) parent));
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.isInZip = isInZip;
        this.currentZipPath = currentZipPath;
        dialogUtil = context.dialogUtil;
        uiHelper = context.uiHelper;
    }

    private void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        if (zipEntry.isDirectory()) {
            Drawable drawable = ResourcesCompat.getDrawable(context.rss, R.drawable.folder_24px, context.getTheme());
            ColorUtil.changeImageColor(drawable, context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
            fileIconView.setImageDrawable(drawable);
            fileDateView.setVisibility(View.INVISIBLE);
        } else {
            setupNonFolderIconView(zipEntry.getFullPath(), fileIconView);
            fileDateView.setVisibility(View.VISIBLE);
            Date lastModifiedDate = new Date(zipEntry.getLastModified());
            SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm");
            String formattedDate = sdf.format(lastModifiedDate);
            fileDateView.setText(new StringBuilder(formattedDate).append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize())));
        }
    }

    private static final ExecutorService iconLoaderService = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Drawable> iconCache = new LruCache<>(100);

    private static Bitmap loadImageThumbnail(String path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            
            int width = options.outWidth;
            int height = options.outHeight;
            int scale = 1;
            while (width / 2 >= 128 && height / 2 >= 128) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }
            
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap loadVideoThumbnail(String path) {
        try {
            return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
        } catch (Throwable t) {
            return null;
        }
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        String extension = FilenameUtils.getExtension(path);
        if (extension != null) {
            extension = extension.toLowerCase(Locale.ROOT);
        } else {
            extension = "";
        }

        if (!isInZip) {
            Drawable cached = iconCache.get(path);
            if (cached != null) {
                fileIconView.setImageDrawable(cached);
                return;
            }
        }

        switch (extension) {
            case "apk":
                fileIconView.setImageResource(R.drawable.apk_document_24px);
                if (!isInZip) {
                    fileIconView.setTag(path);
                    iconLoaderService.execute(() -> {
                        if (!path.equals(fileIconView.getTag())) {
                            return;
                        }
                        try {
                            PackageManager pm = context.getPackageManager();
                            PackageInfo packageInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES);
                            if (packageInfo != null) {
                                ApplicationInfo appInfo = packageInfo.applicationInfo;
                                if (appInfo != null) {
                                    if (TextUtils.isEmpty(appInfo.sourceDir) || TextUtils.isEmpty(appInfo.publicSourceDir)) {
                                        appInfo.sourceDir = path;
                                        appInfo.publicSourceDir = path;
                                    }
                                    Drawable icon = appInfo.loadIcon(pm);
                                    if (icon != null) {
                                        iconCache.put(path, icon);
                                        context.runOnUiThread(() -> {
                                            if (path.equals(fileIconView.getTag())) {
                                                fileIconView.setImageDrawable(icon);
                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                }
                break;

            case "png":
            case "jpg":
            case "jpeg":
            case "webp":
            case "gif":
            case "bmp":
                fileIconView.setImageResource(R.drawable.image_24px);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                if (!isInZip) {
                    fileIconView.setTag(path);
                    iconLoaderService.execute(() -> {
                        if (!path.equals(fileIconView.getTag())) {
                            return;
                        }
                        Bitmap bitmap = loadImageThumbnail(path);
                        if (bitmap != null) {
                            if (!path.equals(fileIconView.getTag())) {
                                return;
                            }
                            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
                            iconCache.put(path, icon);
                            context.runOnUiThread(() -> {
                                if (path.equals(fileIconView.getTag())) {
                                    fileIconView.setImageDrawable(icon);
                                }
                            });
                        }
                    });
                }
                break;

            case "mp4":
            case "mkv":
            case "webm":
            case "avi":
            case "3gp":
            case "mov":
            case "ts":
            case "m4v":
                fileIconView.setImageResource(R.drawable.video_24px);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                if (!isInZip) {
                    fileIconView.setTag(path);
                    iconLoaderService.execute(() -> {
                        if (!path.equals(fileIconView.getTag())) {
                            return;
                        }
                        Bitmap bitmap = loadVideoThumbnail(path);
                        if (bitmap != null) {
                            if (!path.equals(fileIconView.getTag())) {
                                return;
                            }
                            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
                            iconCache.put(path, icon);
                            context.runOnUiThread(() -> {
                                if (path.equals(fileIconView.getTag())) {
                                    fileIconView.setImageDrawable(icon);
                                }
                            });
                        }
                    });
                }
                break;

            case "mp3":
            case "wav":
            case "ogg":
            case "m4a":
            case "flac":
            case "aac":
                fileIconView.setImageResource(R.drawable.music_24px);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                break;

            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
            case "bz2":
                fileIconView.setImageResource(R.drawable.baseline_folder_zip_24);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                break;

            case "pdf":
                fileIconView.setImageResource(R.drawable.pdf_24px);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                break;

            case "txt":
            case "log":
            case "xml":
            case "json":
            case "html":
            case "css":
            case "js":
            case "java":
            case "kt":
            case "md":
                fileIconView.setImageResource(R.drawable.baseline_text_snippet_24);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                break;

            default:
                fileIconView.setImageResource(R.drawable.baseline_insert_drive_file_24);
                ColorUtil.changeImageColor(fileIconView.getDrawable(), context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                break;
        }
    }

    private void setupFileView(File file, ImageView fileIconView, TextView fileDateView, int position) {
        if (file.isFile()) {
            fileDateView.setVisibility(View.VISIBLE);
            Date lastModifiedDate = new Date(file.lastModified());
            SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm");
            String formattedDate = sdf.format(lastModifiedDate);
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(new StringBuilder(formattedDate).append(' ').append(FileSize.getHumanReadableFileSize(file.length())));
        } else {
            Drawable drawable = ResourcesCompat.getDrawable(context.rss, R.drawable.folder_24px, context.getTheme());
            ColorUtil.changeImageColor(drawable, context.theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
            fileIconView.setImageDrawable(drawable);
            fileDateView.setVisibility(View.INVISIBLE);
        }
    }

    private void showDecompileOptionsDialog(File file, String fileName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_decompile_options, null);

        MaterialAutoCompleteTextView frameworkVersion = dialogView.findViewById(R.id.frameworkVersion);
        MaterialAutoCompleteTextView decodeTypes = dialogView.findViewById(R.id.decodeTypes);
        MaterialAutoCompleteTextView dexLibrary = dialogView.findViewById(R.id.dexLibrary);
        TextInputEditText loadDex = dialogView.findViewById(R.id.loadDex);
        CompoundButton flagDex = dialogView.findViewById(R.id.flagDex);
        CompoundButton noDexDebug = dialogView.findViewById(R.id.noDexDebug);
        CompoundButton dexMarkers = dialogView.findViewById(R.id.dexMarkers);
        CompoundButton flagForce = dialogView.findViewById(R.id.flagForce);
        CompoundButton keepResPath = dialogView.findViewById(R.id.keepResPath);
        CompoundButton splitJson = dialogView.findViewById(R.id.splitJson);
        CompoundButton vrd = dialogView.findViewById(R.id.vrd);

        int[] frameworkVersions = context.getResources().getIntArray(R.array.framework_versions);
        int savedFramework = settings.getInt("fwVer", 35);
        int frameworkSelection = savedFramework > frameworkVersions.length ? 0 : savedFramework;
        String[] frameworkStrings = new String[frameworkVersions.length];
        for (int i = 0; i < frameworkVersions.length; i++) {
            frameworkStrings[i] = Integer.toString(frameworkVersions[i]);
        }
        ArrayAdapter<String> frameworkAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, frameworkStrings);
        frameworkVersion.setAdapter(frameworkAdapter);
        frameworkVersion.setText(frameworkStrings[frameworkSelection], false);
        frameworkVersion.setOnItemClickListener((parent, view, position, id) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putInt("fwVer", frameworkVersions[position])
            );
        });

        String[] decodeTypesArray = new String[]{"xml", "json", "raw", "sig"};
        int savedDecodeType = settings.getInt("decodeTypes", 0);
        ArrayAdapter<String> decodeAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, decodeTypesArray);
        decodeTypes.setAdapter(decodeAdapter);
        decodeTypes.setText(decodeTypesArray[savedDecodeType], false);
        decodeTypes.setOnItemClickListener((parent, view, position, id) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putInt("decodeTypes", position)
            );
        });

        String[] dexLibraryArray = new String[]{"Internal (dex up to 042)", "jf (dex versions 035 and below)"};
        int savedDexLib = settings.getInt("dexLib", 0);
        ArrayAdapter<String> dexAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, dexLibraryArray);
        dexLibrary.setAdapter(dexAdapter);
        dexLibrary.setText(dexLibraryArray[savedDexLib], false);
        dexLibrary.setOnItemClickListener((parent, view, position, id) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putInt("dexLib", position)
            );
        });

        int savedLoadDex = settings.getInt("loadDex", 3);
        loadDex.setText(String.valueOf(savedLoadDex));
        loadDex.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    LegacyUtils.applySharedPrefEditor(
                            settings.edit().putInt("loadDex", Integer.parseInt(s.toString()))
                    );
                } catch (NumberFormatException ignored) {}
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        flagDex.setChecked(settings.getBoolean("flagDex", false));
        flagDex.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("flagDex", isChecked)
            );
        });

        noDexDebug.setChecked(settings.getBoolean("noDexDebug", true));
        noDexDebug.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("noDexDebug", isChecked)
            );
        });

        dexMarkers.setChecked(settings.getBoolean("dexMarkers", false));
        dexMarkers.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("dexMarkers", isChecked)
            );
        });

        flagForce.setChecked(settings.getBoolean("flagForce", false));
        flagForce.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("flagForce", isChecked)
            );
        });

        keepResPath.setChecked(settings.getBoolean("keepResPath", false));
        keepResPath.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("keepResPath", isChecked)
            );
        });

        splitJson.setChecked(settings.getBoolean("splitJson", true));
        splitJson.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("splitJson", isChecked)
            );
        });

        vrd.setChecked(settings.getBoolean("vrd", true));
        vrd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LegacyUtils.applySharedPrefEditor(
                    settings.edit().putBoolean("vrd", isChecked)
            );
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("Decompile Options")
                .setView(dialogView)
                .setPositiveButton("Decompile", (d, which) -> {

                    DialogUtil dialogUtil = new DialogUtil(context);
                    AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
                    dialogUtil.styleAlertDialog(progressDialog);
                    TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
                    APKLogger logger = LogUtil.getApkLogger(progressText, context.handler, context);

                    new Thread(() -> {
                        try {
                            int fwVer = settings.getInt("fwVer", 35);
                            int loadDexValue = settings.getInt("loadDex", 3);
                            int decodeTypesValue = settings.getInt("decodeTypes", 0);
                            int dexLibValue = settings.getInt("dexLib", 0);
                            boolean keepDex = settings.getBoolean("flagDex", false);
                            boolean noDexDebugValue = settings.getBoolean("noDexDebug", true);
                            boolean dexMarkersValue = settings.getBoolean("dexMarkers", false);
                            boolean forceDeleteOutputPath = settings.getBoolean("flagForce", false);
                            boolean keepResPathValue = settings.getBoolean("keepResPath", false);
                            boolean splitJsonValue = settings.getBoolean("splitJson", true);
                            boolean vrdValue = settings.getBoolean("vrd", true);
                            DecompileOptions decompileOptions = new DecompileOptions();
                            decompileOptions.inputFile = file;
                            File outputFile = new File(file.getPath().replaceFirst('.' + FilenameUtils.getExtension(fileName) + "$", ""));
                            outputFile.mkdir();
                            decompileOptions.outputFile = outputFile;
                            decompileOptions.frameworkVersion = fwVer;
                            decompileOptions.loadDex = loadDexValue;
                            decompileOptions.type = decodeTypesValue == 0 ? "xml"
                                    : decodeTypesValue == 1 ? "json"
                                    : decodeTypesValue == 2 ? "raw" : "sig";
                            decompileOptions.dexLib = dexLibValue == 0 ? "internal" : "jf";
                            decompileOptions.dex = keepDex;
                            decompileOptions.dexMarkers = dexMarkersValue;
                            decompileOptions.force = forceDeleteOutputPath;
                            decompileOptions.keepResPath = keepResPathValue;
                            decompileOptions.noDexDebug = noDexDebugValue;
                            decompileOptions.splitJson = splitJsonValue;
                            decompileOptions.validateResDir = vrdValue;
                            Decompiler decompiler = decompileOptions.newCommandExecutor(logger);
                            decompiler.setEnableLog(true);
                            decompiler.runCommand();
                            logger.close();
                            context.handler.post(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(context, "Decompiled to " + outputFile.getName(), Toast.LENGTH_SHORT).show();
                                context.reloadCurrentFolder();
                            });
                        } catch (Exception e) {
                            progressDialog.dismiss();
                            new ErrorUtil(context).showError(e);
                            logger.close();
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null);
        builder.show();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.list_file, parent, false);
        Object item = values[position];
        File file;
        ZipEntryInfo entry;
        String fileName;

        TextView fileNameView = convertView.findViewById(R.id.fileName);
        ImageView fileIconView = convertView.findViewById(R.id.fileIcon);
        TextView fileDateView = convertView.findViewById(R.id.fileDate);

        if (isInZip) {
            entry = (ZipEntryInfo) item;
            setupZipEntryView(entry, fileIconView, fileDateView);
            file = null;
            fileNameView.setText(fileName = entry.getName());
        } else {
            entry = null;
            setupFileView(file = (File) item, fileIconView, fileDateView, position);
            fileNameView.setText(fileName = (position == 0 ? ".." : file.getName()));
        }

        convertView.setBackgroundColor(selectedPositions.contains(position) ? Color.DKGRAY : Color.TRANSPARENT);

        View.OnClickListener originalClickListener;
        if(isInZip && position == 0 && entry.getFullPath() == null) {
            originalClickListener = v -> context.loadFolderInPane(entry.getZipFile().getParentFile(), pane1);
        } else originalClickListener = isMultiSelectMode ? v -> {
            context.lastPaneSelected = pane1 ? 1 : 2;
            handleMultiSelect(position);
        } : !isInZip && file.isFile() ?
                v -> {
            context.lastPaneSelected = pane1 ? 1 : 2;
            context.setCurrentFolder(file.getParentFile(), getOldValues());
            if (fileName.endsWith(".txt") || fileName.endsWith(".json")
                    || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
                    || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
                context.startActivity(new Intent(context, TextEditorActivity.class) .putExtra("path", file.getPath()));
            } else if(fileName.endsWith(".xml")) {
                try (InputStream is = FileUtils.getInputStream(file)) {
                    if (FileUtils.isAxml(is)) try (InputStream is2 = FileUtils.getInputStream(file)) {
                            context.startActivity(new Intent(context, TextEditorActivity.class)
                                    .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                    .putExtra("axml", true)
                                    .putExtra("path", file.getPath()));
                        }
                    else context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", file.getPath()));
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            } else if (fileName.endsWith(".apk")) {
                View display = LayoutInflater.from(context).inflate(R.layout.apk_display, parent, false);
                ImageView apkIcon = display.findViewById(R.id.apkIcon);
                PackageManager pm = context.getPackageManager();
                PackageInfo packageInfo = pm.getPackageArchiveInfo(file.getPath(), PackageManager.GET_ACTIVITIES);
                ApplicationInfo appInfo;
                if (packageInfo == null || (appInfo = packageInfo.applicationInfo) == null) {

                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider",
                            file);
                    Intent intent = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, context.getContentResolver().getType(uri))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(intent, "Open " + file.getName()));
                    return;

                }
                String filePath = file.getPath();
                if (TextUtils.isEmpty(appInfo.sourceDir) || TextUtils.isEmpty(appInfo.publicSourceDir)) {
                    appInfo.sourceDir = filePath;
                    appInfo.publicSourceDir = filePath;
                }
                apkIcon.setImageDrawable(appInfo.loadIcon(pm));
                TextView apkTitle = display.findViewById(R.id.apkTitle);
                apkTitle.setText(appInfo.loadLabel(pm));

                TextView apkVersionName = display.findViewById(R.id.apkVersionName);
                apkVersionName.setText(packageInfo.versionName);

                TextView verCode = display.findViewById(R.id.verCode);
                verCode.setText(Integer.toString(packageInfo.versionCode));

                TextView pkgName = display.findViewById(R.id.pkgName);
                pkgName.setText(packageInfo.packageName);
                uiHelper.scrollTextView(pkgName);

                TextView fileSize = display.findViewById(R.id.fileSize);
                fileSize.setText(FileSize.getHumanReadableFileSize(file.length()));

                TextView signaturesInApk = display.findViewById(R.id.signaturesInApk);
                StringBuilder signatures = new StringBuilder();

                ApkVerifier apkVerifier = new ApkVerifier.Builder(file).build();
                try {
                    ApkVerifier.Result result = apkVerifier.verify();
                    boolean verified = result.isVerified();
                    boolean v1 = result.isVerifiedUsingV1Scheme();
                    boolean v2 = result.isVerifiedUsingV2Scheme();
                    boolean v3 = result.isVerifiedUsingV3Scheme();
                    boolean v31 = result.isVerifiedUsingV31Scheme();
                    boolean v4 = result.isVerifiedUsingV4Scheme();
                    if (v1)
                        signatures.append("V1");
                    if (v2) {
                        if (v1)
                            signatures.append(" + ");
                        signatures.append("V2");
                    }
                    if (v3 || v31) {
                        if (v1 || v2)
                            signatures.append(" + ");
                        signatures.append("V3");
                    }
                    if (v4) {
                        if (v1 || v2 || v3 || v31)
                            signatures.append(" + ");
                        signatures.append("V4");
                    }
                    if (verified && !TextUtils.isEmpty(signatures)) {
                        signaturesInApk.setText(signatures);
                    } else {
                        try (ArchiveFile af = new ArchiveFile(file)) {
                            signaturesInApk.setText(af.getEntrySource("META-INF/MANIFEST.MF") == null ? "Not signed"
                                    : "Verification failed");
                        }
                    }
                } catch (Exception e) {
                    signaturesInApk.setText(context.rss.getString(R.string.unknown));
                }

                TextView protectedDisplay = display.findViewById(R.id.protectedDisplay);
                try (ArchiveFile af = new ArchiveFile(file); ApkModule am = new ApkModule(af.createZipEntryMap())) {
                    String aProtected = Util.isProtected(am);
                    protectedDisplay.setText(TextUtils.isEmpty(aProtected) ? "Not found" : aProtected);
                } catch (Exception e) {
                    protectedDisplay.setText(context.rss.getString(R.string.unknown));
                }


                @SuppressLint("RequestInstallPackagesPolicy") AlertDialog ad = dialogUtil.getDialogBuilder()
                        .setView(display)
                        .setNeutralButton("More", (dialog, which) -> {
                            String[] items = new String[]{"Sign APK", "Optimize APK", "Decompile (REAndroid APKEditor)", "Refactor obfuscated resource names", "Protect (REAndroid APKEditor)"};//, "Fast Edit Attributes"};
                            AlertDialog alertDialog = dialogUtil.getDialogBuilder()
                                    .setSingleChoiceItems(items, -1, (dialog12, which1) -> {
                                        dialog12.dismiss();
                                        if (which1 == 0) sign(false, file);
                                        else if (which1 == 2) showDecompileOptionsDialog(file, fileName);
                                        else if (which1 == 3) {
                                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                                            boolean forceDeleteOutputPath = settings.getBoolean("flagForce", false);
                                            boolean cleanMeta = settings.getBoolean("cleanMeta", true);
                                            boolean fixTypes = settings.getBoolean("fixTypes", true);

                                            RefactorOptions options = new RefactorOptions();
                                            options.inputFile = file;
                                            String extension = FilenameUtils.getExtension(fileName);
                                            options.outputFile = new File(file.getParentFile(), fileName.replace('.' + extension, "_refactored." + extension));

                                            LinearLayout layout = new LinearLayout(context);
                                            layout.setOrientation(LinearLayout.VERTICAL);

                                            final String[] publicXmlPath = {null};
                                            MaterialButton publicXmlInputView = new MaterialButton(context);
                                            String publiXmlText = context.rss.getString(R.string.public_xml);
                                            publicXmlInputView.setText(publiXmlText);
                                            publicXmlInputView.setOnClickListener(v5 -> {
                                                DialogProperties properties = new DialogProperties();
                                                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                                                properties.selection_type = DialogConfigs.FILE_SELECT;
                                                properties.root = new File(DialogConfigs.DEFAULT_DIR);
                                                properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
                                                properties.offset = new File(DialogConfigs.DEFAULT_DIR);
                                                properties.extensions = new String[]{"xml"};
                                                FilePickerDialog fpd = new FilePickerDialog(context, properties);
                                                fpd.setTitle(publiXmlText);
                                                fpd.setDialogSelectionListener(files -> publicXmlPath[0] = files[0]);
                                                fpd.show();
                                            });

                                            MaterialSwitch cleanMetaSwitch = new MaterialSwitch(context);
                                            cleanMetaSwitch.setText(context.rss.getString(R.string.clean_meta));
                                            cleanMetaSwitch.setChecked(cleanMeta);
                                            cleanMetaSwitch.setOnCheckedChangeListener(
                                                    (buttonView, isChecked2) -> LegacyUtils.applySharedPrefEditor(
                                                            settings.edit().putBoolean("cleanMeta", isChecked2)));

                                            MaterialSwitch fixTypesSwitch = new MaterialSwitch(context);
                                            fixTypesSwitch.setText(R.string.fix_types);
                                            fixTypesSwitch.setChecked(fixTypes);
                                            fixTypesSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("fixTypes", isChecked2).apply());

                                            MaterialSwitch forceSwitch = new MaterialSwitch(context);
                                            forceSwitch.setText(context.rss.getString(R.string.force_delete_output_path));
                                            forceSwitch.setChecked(forceDeleteOutputPath);
                                            forceSwitch.setOnCheckedChangeListener(
                                                    (buttonView, isChecked2) -> LegacyUtils.applySharedPrefEditor(
                                                            settings.edit().putBoolean("flagForce", isChecked2)));

                                            layout.addView(publicXmlInputView);
                                            layout.addView(cleanMetaSwitch);
                                            layout.addView(fixTypesSwitch);
                                            layout.addView(forceSwitch);

                                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                                    .setNegativeButton("Cancel", null)
                                                    .setPositiveButton("Refactor", (dialog2, which3) -> {
                                                        options.cleanMeta = settings.getBoolean("cleanMeta", true);
                                                        options.fixTypeNames = settings.getBoolean("fixTypes", true);
                                                        options.force = settings.getBoolean("flagForce", false);
                                                        AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
                                                        dialogUtil.styleAlertDialog(progressDialog);
                                                        TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
                                                        APKLogger logger = LogUtil.getApkLogger(progressText, context.handler, context);
                                                        new Thread(() -> {
                                                            try {
                                                                String pXmlFilePath = publicXmlPath[0];
                                                                if(!TextUtils.isEmpty(pXmlFilePath)) options.publicXml = new File(pXmlFilePath);
                                                                options.newCommandExecutor(logger).runCommand();
                                                                logger.close();
                                                                context.handler.post(() -> {
                                                                    progressDialog.dismiss();
                                                                    Toast.makeText(context, "Refactored", Toast.LENGTH_SHORT).show();
                                                                });
                                                            } catch (Exception e) {
                                                                context.handler.post(() -> {
                                                                    progressDialog.dismiss();
                                                                    new ErrorUtil(context).showError(e);
                                                                    logger.close();
                                                                });
                                                            }
                                                        }).start();
                                                    }).create());
                                        } else if (which1 == 4) {
                                            SharedPreferences settings = PreferenceManager
                                                    .getDefaultSharedPreferences(context);
                                            boolean skipManifest = settings.getBoolean("skipManifest", false);
                                            boolean confuseZip = settings.getBoolean("confuseZip", false);
                                            int dexLevel = settings.getInt("dexLevel", 0);
                                            boolean flagForce = settings.getBoolean("flagForce", false);

                                            ProtectorOptions options = new ProtectorOptions();
                                            options.inputFile = file;

                                            LinearLayout layout = new LinearLayout(context);
                                            layout.setOrientation(LinearLayout.VERTICAL);
                                            LayoutInflater layoutInflater = LayoutInflater.from(context);

                                            View skipManifestView = layoutInflater.inflate(R.layout.item_switch, layout,
                                                    false);
                                            TextView skipManifestTitle = skipManifestView.findViewById(R.id.title);
                                            CheckBox skipManifestSwtch = skipManifestView
                                                    .findViewById(R.id.switch_view);
                                            skipManifestTitle.setText(context.rss.getString(R.string.skip_manifest_protection));
                                            skipManifestSwtch.setChecked(skipManifest);
                                            skipManifestSwtch.setOnCheckedChangeListener(
                                                    (buttonView, isChecked) -> LegacyUtils.applySharedPrefEditor(
                                                            settings.edit().putBoolean("skipManifest", isChecked)));
                                            skipManifestView.setOnClickListener(v2 -> skipManifestSwtch.toggle());

                                            View confuseZipView = layoutInflater.inflate(R.layout.item_switch, layout,
                                                    false);
                                            TextView confuseZipTitle = confuseZipView.findViewById(R.id.title);
                                            CheckBox confuseZipSwtch = confuseZipView.findViewById(R.id.switch_view);
                                            confuseZipTitle.setText(context.rss.getString(R.string.confuse_zip_structure));
                                            confuseZipSwtch.setChecked(confuseZip);
                                            confuseZipSwtch.setOnCheckedChangeListener(
                                                    (buttonView, isChecked) -> LegacyUtils.applySharedPrefEditor(
                                                            settings.edit().putBoolean("confuseZip", isChecked)));
                                            confuseZipView.setOnClickListener(v2 -> confuseZipSwtch.toggle());

                                            View dexLevelView = layoutInflater.inflate(R.layout.item_edit_number,
                                                    layout,
                                                    false);
                                            TextView dexLevelTitle = dexLevelView.findViewById(R.id.title);
                                            EditText dexLevelInput = dexLevelView.findViewById(R.id.edit_text);
                                            dexLevelTitle.setText(context.rss.getString(R.string.dex_protection_level));
                                            dexLevelInput.setText(String.valueOf(dexLevel));
                                            dexLevelInput.addTextChangedListener(new TextWatcher() {
                                                @Override
                                                public void beforeTextChanged(CharSequence s, int start, int count,
                                                                              int after) {
                                                }

                                                @Override
                                                public void onTextChanged(CharSequence s, int start, int before,
                                                                          int count) {
                                                }

                                                @Override
                                                public void afterTextChanged(Editable s) {
                                                    try {
                                                        LegacyUtils.applySharedPrefEditor(settings.edit().putInt(
                                                                "dexLevel", Integer.parseInt(s.toString())));
                                                    } catch (Exception ignored) {
                                                    }
                                                }
                                            });

                                            View forceView = layoutInflater.inflate(R.layout.item_switch, layout,
                                                    false);
                                            TextView forceTitle = forceView.findViewById(R.id.title);
                                            CheckBox forceSwitch = forceView.findViewById(R.id.switch_view);
                                            forceTitle.setText(context.rss.getString(R.string.force_delete_output_path));
                                            forceSwitch.setChecked(flagForce);
                                            forceSwitch.setOnCheckedChangeListener(
                                                    (buttonView, isChecked) -> LegacyUtils.applySharedPrefEditor(
                                                            settings.edit().putBoolean("flagForce", isChecked)));
                                            forceView.setOnClickListener(v2 -> forceSwitch.toggle());

                                            layout.addView(skipManifestView);
                                            layout.addView(confuseZipView);
                                            layout.addView(dexLevelView);
                                            layout.addView(forceView);

                                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                                    .setNegativeButton("Cancel", null)
                                                    .setPositiveButton("Protect", (dialog2, which3) -> {
                                                        options.skipManifest = settings.getBoolean("skipManifest",
                                                                false);
                                                        options.confuse_zip = settings.getBoolean("confuseZip", false);
                                                        options.dexLevel = settings.getInt("dexLevel", 0);
                                                        options.force = settings.getBoolean("flagForce", false);
                                                        options.outputFile = options.generateOutputFromInput(file);

                                                        AlertDialog progressDialog = dialogUtil
                                                                .getProgressDialog(true);
                                                        dialogUtil.styleAlertDialog(progressDialog);
                                                        TextView progressText = progressDialog
                                                                .findViewById(R.id.dialogTitle);
                                                        APKLogger logger = LogUtil.getApkLogger(progressText, context.handler, context);
                                                        new Thread(() -> {
                                                            try {
                                                                options.newCommandExecutor(logger).runCommand();
                                                                logger.close();
                                                                context.handler.post(() -> {
                                                                    dialog2.dismiss();
                                                                    Toast.makeText(context, context.rss.getString(R.string.protectd), Toast.LENGTH_SHORT).show();
                                                                });
                                                            } catch (Exception e) {
                                                                context.handler.post(dialog2::dismiss);
                                                                new ErrorUtil(context).showError(e);
                                                                logger.close();
                                                            }
                                                        }).start();
                                                    }).create());
                                        } else if (which1 == 5) {
                                            showEditManifestDialog(file);
                                        }
                                    }).create();
                            dialogUtil.styleAlertDialog(alertDialog);

                        })
                        .setPositiveButton("Install",
                                (dialog, which) -> context.startActivity(new Intent(
                                        Intent.ACTION_INSTALL_PACKAGE)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        .setData(FileProvider.getUriForFile(context,
                                                "io.github.abdurazaaqmohammed.MPManager.provider", file))))
                        .setNegativeButton("View", (dialog, which) -> openZipFile(file, null))
                        .create(); dialogUtil.styleAlertDialog(ad);
                display.findViewById(R.id.quickEdit).setOnClickListener(v7 -> {
                    ad.dismiss();
                    showEditManifestDialog(file);
                }); } else if (fileName.endsWith(".zip")) {
                openZipFile(file, null);
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk")
                    || fileName.endsWith(".apkm")) {
                String[] items = new String[] { "Install", "View", "Sign", "AntiSplit/merge to APK" };
                dialogUtil.styleAlertDialog(
                        dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                            dialog.dismiss();
                            try {
                                switch (which) {
                                    case 0:
                                        if (LegacyUtils.aboveSdk20) {
                                            new RunUtil(context).runInBackground(() -> {
                                                try (ArchiveFile archiveFile = new ArchiveFile(file)) {
                                                    return (new APKInstallHelper(context).installApk(
                                                            archiveFile.getInputSources(archiveEntry -> archiveEntry.getName().endsWith(".apk"))) == PackageInstaller.STATUS_SUCCESS);
                                                }
                                            });
                                        } else {
                                            Toast.makeText(context,
                                                    "Installing split APKs is not supported on this version of Android :(",
                                                    Toast.LENGTH_SHORT).show();
                                            Toast.makeText(context, "You could try merging the APK then installing it",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    case 1:
                                        openZipFile(file, null);
                                        break;
                                    case 2:
                                        sign(true, file);
                                        break;
                                    case 3:
                                        MergeUtil.mergeSplitApk(file, context);
                                        break;
                                }
                            } catch (Exception e) {
                                new ErrorUtil(context).showError(e);
                            }
                        }).create());
            } else {
                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider",
                        file);
                context.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }
        } : (View.OnClickListener) v -> {
            context.lastPaneSelected = pane1 ? 1 : 2;
            if (isInZip)
                handleZipEntryClick(entry);
            else
                context.loadFolderInPane(file, pane1);
        };

         View.OnLongClickListener originalLongClickListener = v -> {
            context.lastPaneSelected = pane1 ? 1 : 2;
            if (isInZip) {
                context.setCurrentFolder(currentZipPath, Arrays.asList(values));
            } else
                context.setCurrentFolder(file.getParentFile(), getOldValues());

            boolean multi = !selectedPositions.isEmpty();
            String direction = pane1 ? "->" : "<-";
            String[] baseItems = new String[] { "Copy " + direction, "Move " + direction, "Rename", "Delete", "Compress", "Properties", "Share", "Open with", "Bookmark" };
            List<String> itemsList = new ArrayList<>(Arrays.asList(baseItems));

            MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) ((ListView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
            Object compareFile1 = null;
            Object compareFile2 = null;
            if (selectedPositions.size() == 1 && otherPaneAdapter.selectedPositions.size() == 1) {
                compareFile1 = values[selectedPositions.iterator().next()];
                compareFile2 = otherPaneAdapter.values[otherPaneAdapter.selectedPositions.iterator().next()];
                String name1 = compareFile1 instanceof File ? ((File)compareFile1).getName() : ((ZipEntryInfo)compareFile1).getName();
                String name2 = compareFile2 instanceof File ? ((File)compareFile2).getName() : ((ZipEntryInfo)compareFile2).getName();

                String ext1 = FilenameUtils.getExtension(name1).toLowerCase();
                String ext2 = FilenameUtils.getExtension(name2).toLowerCase();

                boolean isZip1 = ext1.equals("zip") || ext1.equals("apk") || ext1.equals("jar");
                boolean isZip2 = ext2.equals("zip") || ext2.equals("apk") || ext2.equals("jar");
                boolean isArsc1 = ext1.equals("arsc") || ext1.equals("apk");
                boolean isArsc2 = ext2.equals("arsc") || ext2.equals("apk");

                if (isZip1 && isZip2) itemsList.add("Compare ZIP");
                if (isArsc1 && isArsc2) itemsList.add("Compare ARSC");
                if (!isZip1 && !isZip2 && !ext1.equals("arsc") && !ext2.equals("arsc")) itemsList.add("Compare Text");
            }
            String[] items = itemsList.toArray(new String[0]);
            
            final Object finalCompareFile1 = compareFile1;
            final Object finalCompareFile2 = compareFile2;

            GridView gridView = new GridView(context);
            gridView.setNumColumns(2);
            gridView.setBackgroundColor(Color.TRANSPARENT);
            gridView.setPadding(16, 16, 16, 16);
            gridView.setAdapter(new DialogAdapter(context, items));
            gridView.setVerticalSpacing(40);
            AlertDialog dialog = dialogUtil.getDialogBuilder()
                    .setTitle(fileName)
                    .setView(gridView)
                    .create();

            gridView.setOnItemClickListener((parent1, view, position1, id) -> {
                dialog.dismiss();
                Handler handler = context.handler;
                AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
                dialogUtil.styleAlertDialog(progressDialog);
                TextView progressText = progressDialog.findViewById(R.id.dialogTitle);
                try {
                    String selectedAction = items[position1];
                    switch (selectedAction) {
                        case "Compare Text":
                            context.startActivity(new Intent(context, CompareTextActivity.class)
                                    .putExtra("file1", finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getFullPath())
                                    .putExtra("file2", finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getFullPath())
                                    .putExtra("isZip1", finalCompareFile1 instanceof ZipEntryInfo)
                                    .putExtra("isZip2", finalCompareFile2 instanceof ZipEntryInfo)
                                    .putExtra("zip1", finalCompareFile1 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath() : null)
                                    .putExtra("zip2", finalCompareFile2 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath() : null)
                            );
                            progressDialog.dismiss();
                            return;
                        case "Compare ZIP":
                            new CompareZipDialog(context,
                                    finalCompareFile1 instanceof File ? (File) finalCompareFile1 : ((ZipEntryInfo) finalCompareFile1).getZipFile(),
                                    finalCompareFile2 instanceof File ? (File) finalCompareFile2 : ((ZipEntryInfo) finalCompareFile2).getZipFile()
                            ).show();
                            progressDialog.dismiss();
                            return;
                        case "Compare ARSC":
                            new CompareArscDialog(context,
                                    finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath(),
                                    finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath()
                            ).show();
                            progressDialog.dismiss();
                            return;
                        default:
                            switch (position1) {
                                case 0:
                                    new Thread(() -> {
                                        try {
                                            if (multi) for (int f : selectedPositions) {
                                                Object file1 = values[f];
                                                handler.post(() -> progressText.setText(context.rss.getString(R.string.copying, file1)));
                                                copy(file1);
                                            }
                                            else {
                                                handler.post(() -> progressText.setText(context.rss.getString(R.string.copying, item)));
                                                copy(item);
                                            }
                                            handler.post(progressDialog::dismiss);
                                        } catch (Exception e) {
                                            handler.post(progressDialog::dismiss);
                                            new ErrorUtil(context).showError(e);
                                        }
                                    }).start();
                                    break;
                                case 1:
                                    if (context.pane1Folder == context.pane2Folder) {
                                        progressDialog.dismiss();
                                        break;
                                    }
                                    new Thread(() -> {
                                        try {
                                            if (multi) {
                                                for (int f : selectedPositions) {
                                                    Object file1 = values[f];
                                                    handler.post(() -> progressText.setText(context.rss.getString(R.string.copying, file1)));
                                                    copy(file1);
                                                    handler.post(() -> progressText.setText(context.rss.getString(R.string.deleting, file1)));
                                                    if (file1 instanceof File) {
                                                        ((File) file1).delete();
                                                    } else if (file1 instanceof ZipEntryInfo) {
                                                        deleteZipEntry((ZipEntryInfo) file1);
                                                    }
                                                }
                                            } else {
                                                handler.post(() -> progressText.setText(context.rss.getString(R.string.copying, item)));
                                                copy(item);
                                                handler.post(() -> progressText.setText(context.rss.getString(R.string.deleting, item)));
                                                if (item instanceof File && ((File) item).delete())
                                                    ;
                                                else if (item instanceof ZipEntryInfo)
                                                    deleteZipEntry((ZipEntryInfo) item);
                                            }
                                            handler.post(progressDialog::dismiss);
                                        } catch (Exception e) {
                                            handler.post(progressDialog::dismiss);
                                            new ErrorUtil(context).showError(e);
                                        }
                                    }).start();
                                    break;
                                case 2:
                                    handler.post(progressDialog::dismiss);
                                    if (multi) {
                                        Toast.makeText(context, "To be implemented", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    MaterialAlertDialogBuilder renameDialog = dialogUtil.getDialogBuilder();
                                    EditText renameInput = new EditText(context);
                                    renameInput.setText(fileName);
                                    renameInput.setBackgroundColor(Color.TRANSPARENT);
                                    renameDialog
                                            .setCustomTitle(uiHelper.getTitle("Rename " + fileName))
                                            .setView(renameInput)
                                            .setNegativeButton("Cancel", null)
                                            .setNeutralButton("Paste", (dialog1, which) -> {
                                                int selectionStart = renameInput.getSelectionStart();
                                                int selectionEnd = renameInput.getSelectionEnd();
                                                if (selectionStart != selectionEnd) {
                                                    renameInput.getText().delete(selectionStart, selectionEnd);
                                                }
                                                CharSequence text = ((ClipboardManager) context
                                                        .getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                                                if (!TextUtils.isEmpty(text))
                                                    renameInput.getText().insert(selectionStart, text);
                                            })
                                            .setPositiveButton("OK", (dialog3, which) -> {
                                                String s = renameInput.getText().toString();
                                                if(isInZip) {
                                                    File zipFile = entry.getZipFile();
                                                    try (ZipFile zf = new ZipFile(zipFile)) {
                                                        String entryName = entry.getName();
                                                        if (entry.isDirectory()) {
                                                            // I dont know why this doesnt work it says it should delete folder entry
                                                            //entryName += '/';
                                                            //zf.renameFile((ent), s);
                                                            Map<String, String> map = new HashMap<>();

                                                            for(FileHeader fh : zf.getFileHeaders()) {
                                                                String fhFileName = fh.getFileName();
                                                                if(fhFileName.startsWith(entryName)) map.put(fhFileName, fhFileName.replace(entryName, s));
                                                            }
                                                            if(!map.isEmpty()) zf.renameFiles(map);
                                                        } else zf.renameFile((entryName), s);
                                                        context.loadZipFolderInPane(zipFile, currentZipPath, pane1, false);
                                                    } catch (Exception e) {
                                                        new ErrorUtil(context).showError(e);
                                                    }
                                                } else {
                                                    File ogFolder = file.getParentFile();
                                                    if (file.renameTo(new File(ogFolder, s))) context.loadFolderInPane(ogFolder, pane1);
                                                    else Toast.makeText(context, "Failed to rename " + fileName, Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                    AlertDialog ad = renameDialog.create();
                                    dialogUtil.styleAlertDialog(ad);
                                    ad.getButton(AlertDialog.BUTTON_NEUTRAL)
                                            .setOnClickListener(v6 -> {
                                                int selectionStart = renameInput.getSelectionStart();
                                                int selectionEnd = renameInput.getSelectionEnd();
                                                if (selectionStart != selectionEnd) {
                                                    renameInput.getText().delete(selectionStart, selectionEnd);
                                                }
                                                CharSequence text = ((ClipboardManager) context
                                                        .getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                                                if (!TextUtils.isEmpty(text))
                                                    renameInput.getText().insert(selectionStart, text);
                                            });
                                    break;
                                case 3:
                                    MaterialAlertDialogBuilder deleteDialog = dialogUtil.getDialogBuilder();
                                    CharSequence filesToDisplay = getFilesToDisplay(multi, position);
                                    SharedPreferences settings = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                                    boolean[] sign = new boolean[1];
                                    File zipFile = isInZip ? entry.getZipFile() : null;
                                    if(isInZip && zipFile.getName().endsWith(".apk")) {
                                        LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.item_modified_dialog, null);
                                        ll.<TextView>findViewById(R.id.modifiedText).setText("Are you sure you want to delete " + filesToDisplay + "?");
                                        CheckBox autosign = ll.findViewById(R.id.autosign);
                                        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                                        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                                        ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                                        deleteDialog.setView(ll);
                                    } else deleteDialog.setMessage("Are you sure you want to delete " + filesToDisplay + "?");
                                    deleteDialog.setTitle("Alert").setPositiveButton("Yes", (dialog3, which) -> new Thread(() -> {
                                                try {
                                                    if (multi) {
                                                        if (!isInZip) {
                                                            File selectedFile = null;
                                                            for (int i : selectedPositions) {
                                                                selectedFile = (File) values[i];
                                                                File finalSelectedFile1 = selectedFile;
                                                                if (finalSelectedFile1 != null) handler.post(() -> progressText.setText("Deleting " + finalSelectedFile1.getName()));

                                                                if (selectedFile.isDirectory())
                                                                    Util.deleteDir(selectedFile);
                                                                else
                                                                    selectedFile.delete();
                                                            }
                                                            if (selectedFile != null) {
                                                                File finalSelectedFile = selectedFile;
                                                                handler.post(() -> context.loadFolderInPane(finalSelectedFile.getParentFile(), pane1));
                                                            }
                                                        } else {
                                                            List<ZipEntryInfo> selected = new ArrayList<>();
                                                            for (int i : selectedPositions) selected.add((ZipEntryInfo) values[i]);
                                                            deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                                            if(sign[0]) new SignWrapper(
                                                                    settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", context).getPath()),
                                                                    settings.getString("signatureKeyPassword", "android"), settings.getBoolean("v1", true),
                                                                    settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false)).signApk(zipFile);
                                                        }
                                                    } else if (!isInZip) {
                                                        handler.post(() -> progressText.setText("Deleting " + file.getName()));

                                                        if (file.isDirectory())
                                                            Util.deleteDir(file);
                                                        else
                                                            file.delete();
                                                        handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1));
                                                    } else {
                                                        deleteZipEntry(entry);
                                                        if(sign[0]) new SignWrapper(
                                                                settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", context).getPath()),
                                                                settings.getString("signatureKeyPassword", "android"), settings.getBoolean("v1", true),
                                                                settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false)).signApk(zipFile);
                                                    }
                                                    handler.post(progressDialog::dismiss);
                                                } catch (Exception e) {
                                                    handler.post(progressDialog::dismiss);
                                                    new ErrorUtil(context).showError(e);
                                                }
                                            }).start()).setNegativeButton("Cancel", (dialog1, which1) -> progressDialog.dismiss());
                                    dialogUtil.styleAlertDialog(deleteDialog.create());
                                    break;
                                case 4:
                                    if (isInZip) {
                                        progressDialog.dismiss();
                                        break;
                                    }
                                    File parentFile2 = file.getParentFile();
                                    String parentFileName = parentFile2.getName();
                                    MaterialAlertDialogBuilder compressDialog = dialogUtil.getDialogBuilder();
                                    compressDialog.setTitle(context.rss.getString(R.string.compress));
                                    View compressView = LayoutInflater.from(context).inflate(R.layout.compress_dialog, null);

                                    TextInputEditText filenameEditText = compressView.findViewById(R.id.filename_compress_edittext);
                                    filenameEditText.setText(multi ? parentFileName + ".zip" : fileName.replace(FilenameUtils.getExtension(fileName), "zip"));
                                    settings = PreferenceManager.getDefaultSharedPreferences(context);

                                    AutoCompleteTextView compressLevelInput = compressView.findViewById(R.id.compress_level);
                                    compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                                    List<String> compressionLevels = new ArrayList<>();
                                    for (CompressionLevel cl : CompressionLevel.values())
                                        compressionLevels.add(cl.name());
                                    compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
                                    compressLevelInput.setOnItemClickListener((parent2, view1, position2, id1) -> settings.edit().putString("compressLevel", compressionLevels.get(position2)).apply());
                                    compressDialog.setView(compressView);
                                    compressDialog.setNegativeButton(context.rss.getString(R.string.cancel), (dialog5, which) -> handler.post(() -> {
                                        dialog5.dismiss();
                                        progressDialog.dismiss();
                                    }));
                                    compressDialog.setPositiveButton(context.rss.getString(R.string.compress), (dialog4, which) -> new Thread(() -> {
                                        File outputZip = new File(parentFile2, (multi ? parentFileName : fileName) + ".zip");

                                        ZipParameters zipParameters = new ZipParameters();
                                        CompressionLevel compressionLevel = CompressionLevel.valueOf(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                                        zipParameters.setCompressionLevel(compressionLevel);
                                        if (compressionLevel == CompressionLevel.NO_COMPRESSION)
                                            zipParameters.setCompressionMethod(CompressionMethod.STORE);
                                        CharSequence pw = ((TextView) compressView.findViewById(R.id.pw_edittext)).getText();

                                        try (ZipFile zf = new ZipFile(outputZip)) {
                                            if (!TextUtils.isEmpty(pw))
                                                zf.setPassword(pw.toString().toCharArray()); // why it not setting password check this
                                            if (multi) {
                                                List<File> selectedFiles = new ArrayList<>();
                                                for (int i : selectedPositions)
                                                    selectedFiles.add((File) values[i]);
                                                zf.addFiles(selectedFiles, zipParameters);
                                            } else zf.addFile(file, zipParameters);
                                            handler.post(progressDialog::dismiss);
                                        } catch (Exception e) {
                                            handler.post(progressDialog::dismiss);
                                            new ErrorUtil(context).showError(e);
                                        }
                                    }).start());
                                    context.handler.post(() -> {
                                        compressDialog.show();
                                        progressText.setText(context.rss.getString(R.string.compressing));
                                    });
                                    break;
                                case 5:
                                    LinearLayout parentLayout = new LinearLayout(context);
                                    parentLayout.setOrientation(LinearLayout.HORIZONTAL);
                                    parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT));

                                    LinearLayout firstVerticalLayout = new LinearLayout(context);
                                    firstVerticalLayout.setOrientation(LinearLayout.VERTICAL);
                                    firstVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT));

                                    String[] labels = {"Name", "Parent", "Type", "Size", "Modified"};
                                    for (String label : labels) {
                                        TextView textView = new TextView(context);
                                        textView.setLayoutParams(new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT));
                                        textView.setText(label);
                                        textView.setTextSize(20);
                                        textView.setPadding(10, 10, 10, 10);
                                        firstVerticalLayout.addView(textView);
                                    }

                                    LinearLayout secondVerticalLayout = new LinearLayout(context);
                                    secondVerticalLayout.setOrientation(LinearLayout.VERTICAL);
                                    secondVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT));
                                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                                            .setCustomTitle(uiHelper.getTitle("Properties")).setView(parentLayout)
                                            .create());

                                    for (int i = 0; i < labels.length; i++) {
                                        TextView textView = new TextView(context);
                                        switch (i) {
                                            case 0:
                                                textView.setText(getFilesToDisplay(multi, position));
                                                uiHelper.scrollTextView(textView);
                                                break;
                                            case 1:
                                                if (!isInZip && file.getParentFile() != null) {
                                                    textView.setText(file.getParentFile().getName());
                                                    uiHelper.scrollTextView(textView);
                                                }
                                                break;
                                            case 2:
                                                if (isInZip) {
                                                    textView.setText(entry.isDirectory() ? "Folder" : "File");
                                                } else {
                                                    textView.setText(file.isDirectory() ? "Folder" : "File");
                                                }
                                                break;
                                            case 3:
                                                long size = isInZip ? entry.getSize() : file.length();
                                                textView.setText(
                                                        Formatter.formatFileSize(context, size));
                                                break;
                                            case 4:
                                                if (!isInZip)
                                                    textView.setText(new SimpleDateFormat("yy-MM-dd HH:mm")
                                                            .format(file.lastModified()));
                                                break;
                                            default:
                                                break;
                                        }
                                        textView.setLayoutParams(new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT));
                                        textView.setTextSize(20);
                                        textView.setPadding(10, 10, 10, 10);
                                        secondVerticalLayout.addView(textView);
                                    }

                                    parentLayout.addView(firstVerticalLayout);
                                    parentLayout.addView(secondVerticalLayout);
                                    break;
                                case 6:
                                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                    context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(context.getContentResolver().getType(uri)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                                    break;
                                case 7:
                                    Uri u = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                    context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(u, context.getContentResolver().getType(u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                                    break;
                                case 8:
                                    if (!isInZip) context.addBookmark(file);
                                    break;
                            }
                            break;
                    }
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
            dialogUtil.styleAlertDialog(dialog);
            return true;
        };
        convertView.setOnTouchListener(new SwipeTouchListener(
                context,
                originalClickListener,
                originalLongClickListener,
                position,
                this,
                pane1 ? 1 : 2));

        return convertView;
    }

    private void sign(boolean isSplitApk, File file) {
        CharSequence[] items = new CharSequence[] {
                "Pick signature file (current: " + new File(context.signatureKeyPath).getName() + ")", "V1", "V2", "V3",
                "V4" };
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        MaterialAlertDialogBuilder builder = dialogUtil.getDialogBuilder();
        builder.setCustomTitle(uiHelper.getTitle("Signature Options"));

        SignListAdapter adapter = new SignListAdapter(context, items, settings.getBoolean("v1", true),
                settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false));
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);

        builder.setView(listView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Sign", (dialog, which) -> {
            LegacyUtils.applySharedPrefEditor(settings.edit().putBoolean("v1", adapter.v1).putBoolean("v2", adapter.v2).putBoolean("v3", adapter.v3).putBoolean("v4", adapter.v4));
            AlertDialog progressDialog = dialogUtil.getProgressDialog(true);
            dialogUtil.styleAlertDialog(progressDialog);
            TextView progressText = progressDialog.findViewById(R.id.dialogTitle);

            new Thread(() -> {
                try {
                    SignWrapper signWrapper = new SignWrapper(settings.getString("keyPath", FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", context).getPath()),
                        settings.getString("signatureKeyPassword", "android"), adapter.v1, adapter.v2, adapter.v3,
                        adapter.v4);
                    File cacheDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
                    String sigFileName = file.getName();
                    File file2 = new File(file.getParentFile(), sigFileName.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", "_signed.$1"));
                    if (isSplitApk)
                        try (ArchiveFile archiveFile = new ArchiveFile(file)) {
                            for (InputSource inputSource : archiveFile.getInputSources(archiveEntry -> archiveEntry.getName().endsWith(".apk"))) {
                                context.handler.post(() -> progressText.setText(context.rss.getString(R.string.signing_, inputSource.getAlias())));
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
                            context.handler.post(() -> {
                                progressDialog.dismiss();
                                dialog.dismiss();
                                Toast.makeText(context, context.rss.getString(R.string.signed, sigFileName), Toast.LENGTH_SHORT).show();
                            });
                        }
                        else signWrapper.signApk(file, FileUtils.getUnusedFile(file2));
                    } catch (Exception e) {
                        context.handler.post(() -> {
                            progressDialog.dismiss();
                            dialog.dismiss();
                        });
                        new ErrorUtil(context).showError(e);
                    }
            }).start();
        });
        dialogUtil.styleAlertDialog(builder.create());
    }

    private CharSequence getFilesToDisplay(boolean multi, int position) {
        if (multi) {
            StringBuilder sb = new StringBuilder();
            for (int i : selectedPositions) sb.append(',').append(isInZip ? ((ZipEntryInfo) values[i]).getName() : ((File) values[i]).getName());
            return sb.deleteCharAt(0);
        }
        return isInZip ? ((ZipEntryInfo) values[position]).getName() : ((File) values[position]).getName();
    }

    public long getFolderSize(File file, TextView toUpdate) {
        File[] files = file.listFiles();
        if (files == null)
            return 0;
        long length = 0;
        for (File f : files) {
            if (f.isFile()) {
                length += f.length();
            } else if (f.isDirectory()) {
                length += getFolderSize(f, toUpdate);
            }
        }
        if (toUpdate != null)
            toUpdate.setText(FileSize.getHumanReadableFileSize(length));
        return length;
    }

    private void copy(Object item) throws IOException {
        if (isMultiSelectMode && !selectedPositions.isEmpty()) {
            List<Object> itemsToCopy = new ArrayList<>();
            for (int index : selectedPositions) {
                itemsToCopy.add(values[index]);
            }
            if (isInZip) {
                copyFromZip(itemsToCopy);
            } else {
                copyToDestination(itemsToCopy);
            }
        } else {
            copyToDestination(Collections.singletonList(item));
        }
    }

    private void copyToDestination(List<Object> items) throws IOException {
        File destinationFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) ((ListView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            copyToRegularFolder(items, destinationFolder);
        }
    }

    private void copyToRegularFolder(List<Object> items, File destinationFolder) throws IOException {
        for (Object item : items) {
            if (item instanceof File) {
                File f = (File) item;
                File dest = FileUtils.getUnusedFile(destinationFolder, f.getName());
                if (f.isDirectory()) {
                    dest.mkdir();
                    FileUtils.copyFolder(f, dest);
                } else
                    FileUtils.copyFile(f, dest);
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        context.handler.post(() -> context.loadFolderInPane(destinationFolder, !pane1));
    }

    public void copyToZip(List items, File zipFile, String currentPath) throws IOException {
        try (ZipFile sourceZip = new ZipFile(zipFile)) {
            if (items.get(0) instanceof File) sourceZip.addFiles(items);
            else {
                File tempFileDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
                tempFileDir.mkdir();
                List<File> list = new ArrayList<>();
                for(Object item : items) {
                    ZipEntryInfo zipEntry = (ZipEntryInfo) item;
                    try (ZipFile sourceZipFile = new ZipFile(zipEntry.getZipFile())) {
                        FileHeader fh = sourceZipFile.getFileHeader(zipEntry.getFullPath());
                        if (fh != null) {
                            String fileName = fh.getFileName();
                            String newPath = TextUtils.isEmpty(currentPath) ? fileName : currentPath + File.separator + fileName;
                            if (!fh.isDirectory()) {
                                try (InputStream is = sourceZipFile.getInputStream(fh)) {
                                    String child = newPath.replace(File.separator, "U+1F602");
                                    FileUtils.copyFile(is, new File(tempFileDir, child));
                                    list.add(new File(child));
                                }
                            }
                        }
                    }
                }
                sourceZip.addFiles(list);
            }
        }
        openZipFile(zipFile, currentPath);
    }

    private void copyFromZip(List<Object> items) throws IOException {
        File destinationFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) ((ListView) context
                .findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            copyToRegularFolder(items, destinationFolder);
        }
    }

    private void extractZipEntry(ZipEntryInfo zipEntry, File destinationFolder) throws IOException {
        String destinationPath = destinationFolder.getPath();
        String zipEntryPath = zipEntry.getFullPath();
        try (ZipFile zf = new ZipFile(zipEntry.getZipFile())) {
            if(zipEntry.isDirectory()) {
              for(FileHeader fh : zf.getFileHeaders()) if(fh.getFileName().startsWith(zipEntryPath)) zf.extractFile(fh, destinationPath);
            } else zf.extractFile(zf.getFileHeader(zipEntryPath), destinationPath);
        }
    }

    private void updateFolderCountOnMainScreen(int position) {
    }

    public void handleSwipe(int position) {
        context.setCurrentPane(pane1 ? 1 : 2);
        if (isMultiSelectMode) {
            if (rangeStartPosition != null) {
                int start = Math.min(rangeStartPosition, position);
                int end = Math.max(rangeStartPosition, position);
                for (int i = start; i <= end; i++) {
                    selectedPositions.add(i);
                }
                updateFolderCountOnMainScreen(position);
            }
        } else {
            isMultiSelectMode = true;
            rangeStartPosition = position;
            selectedPositions.add(position);
            updateFolderCountOnMainScreen(position);
        }
        notifyDataSetChanged();
    }

    public void handleMultiSelect(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
            if (selectedPositions.isEmpty()) {
                isMultiSelectMode = false;
                rangeStartPosition = null;
                if (isInZip) {
                    List<Object> zipEntryInfos = Arrays.asList(values);
                    context.setCurrentFolder(currentZipPath, zipEntryInfos);
                } else
                    context.setCurrentFolder(pane1 ? context.pane1Folder : context.pane2Folder, (File[]) values);
            } else
                updateFolderCountOnMainScreen(position);
        } else {
            selectedPositions.add(position);
            updateFolderCountOnMainScreen(position);
        }
        notifyDataSetChanged();
    }

    private void deleteZipEntry(ZipEntryInfo... entryToDelete) throws IOException {
        File f = entryToDelete[0].getZipFile();
        List<String> toDelete = new ArrayList<>();
        try(ZipFile zf = new ZipFile(f)) {
            //zf.removeFiles(toDelete); // This is not deleting folders properly
            for(FileHeader fh : zf.getFileHeaders()) {
                String name = fh.getFileName();
                for (ZipEntryInfo info : entryToDelete) if(name.startsWith(info.getName())) toDelete.add(name);
            }
            zf.removeFiles(toDelete);
        }
        context.loadZipFolderInPane(f, currentZipPath, pane1, false);
    }

    public List<Object> getSelectedFiles() {
        List<Object> selectedFiles = new ArrayList<>();
        for (Integer position : selectedPositions) {
            selectedFiles.add(values[position]);
        }
        return selectedFiles;
    }

    private void openZipFile(File zipFile, String path) {
        context.loadZipFolderInPane(zipFile, path != null ? path : "", pane1, true);
    }

    private void handleZipEntryClick(ZipEntryInfo zipEntry) {
        File zipFile = zipEntry.getZipFile();
        String fullPath = zipEntry.getFullPath();
        if(zipEntry.isDirectory()) context.loadZipFolderInPane(zipFile, fullPath, pane1, false);
        else try (ZipFile zf = new ZipFile(zipFile);
             InputStream is = zf.getInputStream(zf.getFileHeader(fullPath))) {
            final String name = zipEntry.getName();
            File tempFolder = new File(context.getCacheDir() + File.separator + UUID.randomUUID());
            tempFolder.mkdir();
            File tempFile = new File(tempFolder, name);
            tempFile.createNewFile();
            if(name.endsWith(".xml")) {
                boolean isAxml = FileUtils.isAxml(is);
                if(isAxml) try(InputStream rssStream = zf.getInputStream(zf.getFileHeader("resources.arsc")); InputStream is2 = zf.getInputStream(zf.getFileHeader(fullPath))) {
                    ResourceTableParser rtp = new ResourceTableParser(rssStream);
                    context.startActivityForResult(new Intent(context, TextEditorActivity.class)
                            .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2, rtp.parse()).decodeAsString())
                            .putExtra("zf", zipFile.getPath())
                            .putExtra("zipEntryPath", fullPath)
                            .putExtra("axml", true)
                            .putExtra("path", tempFile.getPath()), 757);
                } else context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", tempFile.getPath()));
            } else {
                FileUtils.copyFile(is, tempFile);
                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", tempFile);
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
        }
    }

    public void clearSelection() {
        selectedPositions.clear();
        isMultiSelectMode = false;
        rangeStartPosition = null;
        notifyDataSetChanged();
    }

    public void selectAll() {
        isMultiSelectMode = true;
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
    }

    static class OptionItem {
        static final int TYPE_SPINNER = 0;
        static final int TYPE_SWITCH = 1;
        static final int TYPE_EDIT_NUMBER = 2;
        static final int TYPE_EDIT_TEXT = 3;

        int type;
        String title;
        String key;
        Object defaultValue;

        public OptionItem(int type, String title, String key, Object defaultValue) {
            this.type = type;
            this.title = title;
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }
}