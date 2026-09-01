package io.github.abdurazaaqmohammed.adapters.main;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.apk.axml.APKParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.apk.axml.serializableItems.XMLEntry;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;

import android.os.Environment;
import com.google.android.material.checkbox.MaterialCheckBox;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RunUtil;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

public class ApkManifestEditor {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;

    public ApkManifestEditor(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
    }

    public void showEditManifestDialog(File apkFile) {
        final android.content.res.Resources rss = context.rss;
        View quickEditDialog = LayoutInflater.from(context).inflate(R.layout.quick_edit_dialog, null, false);
        quickEditDialog.findViewById(R.id.app_lancer_icon).setOnClickListener(v -> editLauncherIcon(apkFile));

        quickEditDialog.findViewById(R.id.install_location_dropdown);
        AutoCompleteTextView installLocationTextView = quickEditDialog.findViewById(R.id.install_location);
        List<XMLEntry> entries = decodeManifest(apkFile);

        String installLoc = "";
        TextView appNameInput = quickEditDialog.findViewById(R.id.appNameInput);
        TextView verCodeInput = quickEditDialog.findViewById(R.id.verCodeInput);
        TextView verNameInput = quickEditDialog.findViewById(R.id.verNameInput);
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.dropdownitem, versions) {
            @NonNull
            @Override
            public View getView(int position1, @Nullable View convertView, @NonNull ViewGroup parent1) {
                if (convertView == null)
                    convertView = LayoutInflater.from(context).inflate(R.layout.dropdownitem, parent1, false);
                TextView view1 = (TextView) convertView;
                view1.setText(rss.getString(R.string.android_ver_text, versions[position1], position1 + 1));
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
                    String appNameSelected = android.text.TextUtils.isEmpty(appNameInputText) ? "" : appNameInputText.toString();
                    boolean appNameChanged = (!finalAppName.equals(appNameSelected));

                    CharSequence verCodeInputText = verCodeInput.getText();
                    String verCodeSelected = android.text.TextUtils.isEmpty(verCodeInputText) ? "" : verCodeInputText.toString();
                    boolean verCodeChanged = (!finalVerCode.equals(verCodeSelected));

                    CharSequence verNameInputText = verNameInput.getText();
                    String verNameSelected = android.text.TextUtils.isEmpty(verNameInputText) ? "" : verNameInputText.toString();
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
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doEdit = () -> {
                        ProgressManager pm = new ProgressManager(context, true).show();
                        pm.setText(rss.getString(R.string.saving));

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
                                        if(sign[0]) wrapper[0].signApk(apkFile);
                                        pm.dismiss();
                                        return true;
                                    } catch (Exception e) {
                                        pm.dismiss();
                                        new ErrorUtil(context).showError(e);
                                        return false;
                                    }
                                });
                    };
                    if(sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doEdit.run();
                    }); else doEdit.run();

                })
                .setNegativeButton(android.R.string.cancel, null)
                .setView(quickEditDialog)
                .create();
        dialogUtil.styleAlertDialog(menuDialog);
    }

    public void editLauncherIcon(File apkFile) {
        FilePickerDialog.Properties properties = new FilePickerDialog.Properties();
        properties.selection_mode = FilePickerDialog.SINGLE_MODE;
        properties.selection_type = FilePickerDialog.FILE_SELECT;
        properties.root = new File(Environment.getExternalStorageDirectory().getPath());
        properties.offset = new File(Environment.getExternalStorageDirectory().getPath());
        properties.preferenceKey = "icon";
        properties.extensions = new String[]{"png", "webp", "jpg", "jpeg"};
        FilePickerDialog fpd = new FilePickerDialog(context, properties);
        fpd.setTitle(context.rss.getString(R.string.select_icon));
        ProgressManager pm = new ProgressManager(context, true);

        fpd.setDialogSelectionListener(files -> new Thread(() -> {
            String iconPath = findIconPathInManifest(apkFile);

            try (InputStream is = FileUtils.getInputStream(files[0])) {
                pm.show().setText(context.rss.getString(R.string.adding, files[0]));
                replaceZipEntry(apkFile,
                        iconPath != null ? iconPath : "res/mipmap-xxhdpi-v4/ic_launcher.png",
                        is);
                pm.dismiss();
                Extensions.showMessage(context, R.string.icon_changed);
            } catch (Exception e) {
                pm.dismiss();
                context.handler.post(() -> new ErrorUtil(context).showError(e));
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

    public void editAllManifestEntries(File apkFile) {
        new Thread(() -> {
            try {
                List<XMLEntry> entries = decodeManifest(apkFile);
                if (entries == null) {
                    Extensions.showMessage(context, R.string.could_not_decode_am);
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
                .setNegativeButton(android.R.string.cancel, null)
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
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
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

    public String readManifestAttrValue(File apkFile, String attrName) {
        List<XMLEntry> entries = decodeManifest(apkFile);
        if (entries == null) return "";
        for (XMLEntry e : entries) {
            if (e.getTag().contains(attrName)) return e.getValue();
        }
        return "";
    }

    public void writeManifestAttrValue(File apkFile, String attrName, String newValue) throws Exception {
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

    public void removeManifestAttr(File apkFile, String attrName) throws Exception {
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
}
