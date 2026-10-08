package io.github.abdurazaaqmohammed.adapters.main;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.graphics.Color;
import androidx.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import io.github.abdurazaaqmohammed.ui.UiFields;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RunUtil;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

public class ApkManifestEditor {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    final Resources rss;

    public ApkManifestEditor(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        rss = context.rss;
    }

    private static class ManifestData {
        List<XMLEntry> entries;
        List<ResEntry> res;
    }

    private static int resolveInstallLocIndex(String installLoc) {
        if (installLoc == null || installLoc.isEmpty()) return 3;
        try {
            return Integer.parseInt(installLoc.trim());
        } catch (Exception ignored) {
            String l = installLoc.trim().toLowerCase();
            if (l.startsWith("auto")) return 0;
            if (l.startsWith("internal")) return 1;
            if (l.startsWith("prefer")) return 2;
            return 3;
        }
    }

    private ManifestData decodeManifestWithRes(File apkFile) {
        ManifestData data = new ManifestData();
        try (ZipFile zf = new ZipFile(apkFile)) {
            FileHeader manifestEntry = zf.getFileHeader("AndroidManifest.xml");
            if (manifestEntry == null) return null;
            List<ResEntry> res = null;
            try {
                APKParser apkParser = new APKParser();
                apkParser.parse(apkFile.getPath(), context);
                res = apkParser.getDecodedResources();
            } catch (Exception ignored) { }
            try (InputStream is = zf.getInputStream(manifestEntry)) {
                data.entries = new aXMLDecoder(is, res).decode();
                data.res = res;
                return data;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveLabelRef(String ref, List<ResEntry> res) {
        if (ref == null || res == null) return null;
        String r = ref.trim();
        if (r.isEmpty() || !r.startsWith("@")) return null;
        // @string/name form
        for (ResEntry e : res) {
            if (r.equals(e.getName())) return e.getValue();
        }
        // @0x7F... / @7F... hex form
        try {
            String hex = r.substring(1);
            if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
            long id = Long.parseLong(hex, 16);
            for (ResEntry e : res) {
                if ((e.getResourceId() & 0xFFFFFFFFL) == id) {
                    if (e.getValue() != null) return e.getValue();
                    return e.getName();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void updateResolvedLabel(TextView resolvedView, String current, List<ResEntry> res) {        if (resolvedView == null) return;
        if (current == null || current.trim().isEmpty() || !current.trim().startsWith("@")) {
            resolvedView.setVisibility(View.GONE);
            return;
        }
        String v = resolveLabelRef(current, res);
        if (v == null || v.isEmpty()) {
            resolvedView.setVisibility(View.GONE);
        } else {
            resolvedView.setVisibility(View.VISIBLE);
            resolvedView.setText(rss.getString(R.string.resolved_value, v));
        }
    }

    private void wireCompressionDropdown(View root) {
        try {
            AutoCompleteTextView compressTv = root.findViewById(R.id.compressLevelTv);
            if (compressTv == null) return;
            java.util.List<String> levels = new java.util.ArrayList<>();
            for (net.lingala.zip4j.model.enums.CompressionLevel cl
                    : net.lingala.zip4j.model.enums.CompressionLevel.values()) levels.add(cl.name());
            SharedPreferences defSettings = PreferenceManager.getDefaultSharedPreferences(context);
            compressTv.setText(defSettings.getString("compressLevel",
                    net.lingala.zip4j.model.enums.CompressionLevel.NORMAL.name()), false);
            compressTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, levels));
            compressTv.setOnItemClickListener((parent, view, position, id) ->
                    defSettings.edit().putString("compressLevel", levels.get(position)).apply());
        } catch (Exception ignored) { }
    }

    public void showEditManifestDialog(File apkFile) {
        View quickEditDialog = LayoutInflater.from(context).inflate(R.layout.quick_edit_dialog, null, false);
        quickEditDialog.findViewById(R.id.app_lancer_icon).setOnClickListener(v -> editLauncherIcon(apkFile));

        quickEditDialog.findViewById(R.id.install_location_dropdown);
        AutoCompleteTextView installLocationTextView = quickEditDialog.findViewById(R.id.install_location);
        ManifestData manifestData = decodeManifestWithRes(apkFile);
        List<XMLEntry> loadedEntries = manifestData != null ? manifestData.entries : null;
        if (loadedEntries == null) loadedEntries = decodeManifest(apkFile);
        if (loadedEntries == null) {
            Extensions.showMessage(context, R.string.could_not_decode_am);
            return;
        }
        final List<XMLEntry> entries = loadedEntries;
        final List<ResEntry> resEntries = manifestData != null ? manifestData.res : null;

        String installLoc = "";
        TextView pkgNameInput = quickEditDialog.findViewById(R.id.pkgNameInput);
        TextView appNameInput = quickEditDialog.findViewById(R.id.appNameInput);
        TextView appNameResolved = quickEditDialog.findViewById(R.id.appNameResolved);
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

        String minSdkVersion = "", targetSdkVersion = "", verCode = "", verName = "", appName = "", pkgName = "";
        boolean foundMinSdk = false;
        boolean foundLabel = false;
        for (XMLEntry e : entries) {
            String tag = e.getTag();
            String trimmed = tag.trim();
            if ("package".equals(trimmed)) {
                if (pkgNameInput != null) pkgNameInput.setText(pkgName = e.getValue());
                else pkgName = e.getValue();
            }
            else if (tag.contains("android:installLocation")) installLoc = e.getValue();
            else if (!foundLabel && tag.contains("android:label")) {
                foundLabel = true;
                appNameInput.setText(appName = e.getValue());
            }
            else if (tag.contains("android:versionCode")) verCodeInput.setText(verCode = e.getValue());
            else if (tag.contains("android:versionName")) verNameInput.setText(verName = e.getValue());
            else if (!foundMinSdk && tag.contains("android:minSdkVersion")) {
                foundMinSdk = true; // Avoid getting wrong minsdk from other property
                try {
                    int minSdkVer = Integer.parseInt(minSdkVersion = e.getValue());
                    if (minSdkVer >= 1 && minSdkVer <= versions.length)
                        minSdk.setText(rss.getString(R.string.android_ver_text, versions[minSdkVer-1], minSdkVer));
                    else minSdk.setText(minSdkVersion);
                } catch (Exception ignored) { minSdk.setText(e.getValue()); }
            }
            else if (tag.contains("android:targetSdkVersion")) {
                try {
                    int targetSdkVer = Integer.parseInt(targetSdkVersion = e.getValue());
                    if (targetSdkVer >= 1 && targetSdkVer <= versions.length)
                        targetSdk.setText(rss.getString(R.string.android_ver_text, versions[targetSdkVer-1], targetSdkVer));
                    else targetSdk.setText(targetSdkVersion);
                } catch (Exception ignored) { targetSdk.setText(e.getValue()); }
            }
        }
        // Fallback: package name via PackageManager if manifest decode missed it.
        if ((pkgName == null || pkgName.isEmpty()) && pkgNameInput != null) {
            try {
                PackageInfo pi = context.getPackageManager()
                        .getPackageArchiveInfo(apkFile.getPath(), 0);
                if (pi != null && pi.packageName != null) pkgNameInput.setText(pkgName = pi.packageName);
            } catch (Exception ignored) { }
        }
        updateResolvedLabel(appNameResolved, appName, resEntries);
        if (appNameInput instanceof EditText) {
            ((EditText) appNameInput).addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    updateResolvedLabel(appNameResolved, s.toString(), resEntries);
                }
            });
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
        if ("".equals(installLoc)) installLocationTextView.setText(items[3]);
        else {
            int locIdx = 3;
            try {
                locIdx = Integer.parseInt(installLoc.trim());
            } catch (Exception ignored) {
                String l = installLoc.trim().toLowerCase();
                if (l.startsWith("auto")) locIdx = 0;
                else if (l.startsWith("internal")) locIdx = 1;
                else if (l.startsWith("prefer")) locIdx = 2;
            }
            if (locIdx < 0 || locIdx >= items.length) locIdx = 3;
            installLocationTextView.setText(items[locIdx]);
        }

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

        wireCompressionDropdown(quickEditDialog);

        String finalAppName = appName;
        String finalPkgName = pkgName;
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
                .setCustomTitle(uiHelper.getTitle(rss.getString(R.string.me_fast_attrs)))
                .setPositiveButton(rss.getString(R.string.done), (dialog, which) -> {
                    CharSequence pkgNameInputText = pkgNameInput != null ? pkgNameInput.getText() : null;
                    String pkgNameSelected = TextUtils.isEmpty(pkgNameInputText) ? "" : pkgNameInputText.toString().trim();
                    boolean pkgNameChanged = !pkgNameSelected.isEmpty() && (!finalPkgName.equals(pkgNameSelected));
                    if (pkgNameChanged && !pkgNameSelected.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
                        Extensions.showMessage(context, rss.getString(R.string.invalid_pkg_name));
                        return;
                    }
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

                    boolean installLocationChanged = installLocationSelected[0] != null
                            && !installLocationSelected[0].equals(String.valueOf(resolveInstallLocIndex(finalInstallLoc)));
                    StringBuilder sb = new StringBuilder();
                    String[] options = {
                            rss.getString(R.string.me_icon),
                            rss.getString(R.string.pkgName),
                            rss.getString(R.string.appname),
                            rss.getString(R.string.install_location),
                            rss.getString(R.string.version_code),
                            rss.getString(R.string.version_name),
                            rss.getString(R.string.me_min_sdk_v),
                            rss.getString(R.string.me_target_sdk_v),
                            rss.getString(R.string.me_edit_all)
                    };

                    if(pkgNameChanged) sb.append(options[1]).append(", ");
                    if(appNameChanged) sb.append(options[2]).append(", ");
                    if(installLocationChanged) sb.append(options[3]).append(", ");
                    if(verCodeChanged) sb.append(options[4]).append(", ");
                    if(verNameChanged) sb.append(options[5]).append(", ");
                    if(minSdkVersionChanged) sb.append(options[6]).append(", ");
                    if(targetSdkVersionChanged) sb.append(options[7]);
                    sb.append(rss.getString(R.string.me_updated));
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doEdit = () -> {
                        ProgressManager pm = new ProgressManager(context, true).show();
                        pm.setText(rss.getString(R.string.saving));

                        new RunUtil(context.handler, context, sb, true)
                                .runInBackground(() -> {
                                    try {
                                        boolean foundMinSdk2 = false;
                                        boolean foundInstallLocation = false;
                                        int entryToRemove = 0;
                                        for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
                                            XMLEntry e = entries.get(i);
                                            String tag = e.getTag();
                                            if (pkgNameChanged && tag.trim().equals("package"))
                                                e.setValue(pkgNameSelected);
                                            else if (appNameChanged && tag.contains("android:label"))
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
                                                // Dropdown index 3 = "Default" => remove the attribute.
                                                if (installLocationSelected[0].isEmpty() || "3".equals(installLocationSelected[0])) entryToRemove = i;
                                                else e.setValue(installLocationSelected[0]);
                                            }
                                        }
                                        if(installLocationChanged) {
                                            if(foundInstallLocation) {
                                                if (entryToRemove != 0) entries.remove(entryToRemove);
                                            } else if (!"3".equals(installLocationSelected[0])) entries.add(4, new XMLEntry("android:installLocation", "=\"", installLocationSelected[0], "\""));
                                        }

                                        if (UiPrefs.genBackup(context)) {
                                            try {
                                                FileUtils.copyFile(apkFile, new File(apkFile.getPath() + ".bak"));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        writeManifestEntries(apkFile, entries, resEntries);
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
                .setCustomTitle(uiHelper.getTitle(rss.getString(R.string.me_edit_entries)))
                .setView(listView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(rss.getString(R.string.me_save_all), (dlg, w) ->
                        new RunUtil(context.handler, context, rss.getString(R.string.me_manifest_saved))
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
                        rss.getString(R.string.me_edit_prefix, entry.getMiddleTag().trim())))
                .setView(UiFields.wrap(context, input, null, 16))
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
        if (entries == null) throw new IOException(rss.getString(R.string.me_decode_fail));

        boolean found = false;
        for (XMLEntry e : entries) {
            if (e.getTag().contains(attrName.split(":")[1])) {
                e.setValue(newValue);
                found = true;
            }
        }
        if (!found) {
            throw new IOException(rss.getString(R.string.me_attr_missing, attrName));
        }
        writeManifestEntries(apkFile, entries);
    }

    public void removeManifestAttr(File apkFile, String attrName) throws Exception {
        List<XMLEntry> entries = decodeManifest(apkFile);
        if (entries == null) throw new IOException(rss.getString(R.string.me_decode_fail));
        for (int i = 0, listSize = entries.size(); i < listSize; i++) {
            XMLEntry item = entries.get(i);
            if (item.getTag().contains(attrName)) entries.remove(i);
        }
        writeManifestEntries(apkFile, entries);
    }

    /** Prefix marking a permission ineffective while keeping it declared (and re-listable). */
    public static final String DISABLED_PREFIX = "__DISABLED__";

    /**
     * Enables/disables permissions non-destructively in a single decode/encode pass.
     * Disabling prefixes the permission name so the system ignores it, while it stays
     * declared and therefore keeps showing up in this dialog for re-enabling.
     * Returns the number of changed permissions.
     */
    public int setPermissionsDisabled(File apkFile, List<String> toDisable, List<String> toEnable) throws Exception {
        ManifestData data = decodeManifestWithRes(apkFile);
        List<XMLEntry> entries = data != null ? data.entries : null;
        List<ResEntry> res = data != null ? data.res : null;
        if (entries == null) entries = decodeManifest(apkFile);
        if (entries == null) throw new IOException(rss.getString(R.string.me_decode_fail));
        Set<String> disableSet = new HashSet<>(toDisable);
        Set<String> enableSet = new HashSet<>(toEnable);
        int changed = 0;
        // Decoder emits "<uses-permission" open row followed by attribute rows
        // ("    android:name=\"...\"" merged with "/>"). Toggle the name value in place.
        for (int i = entries.size() - 1; i >= 0; i--) {
            XMLEntry item = entries.get(i);
            String tag = item.getTag();
            if (tag == null || !tag.contains("uses-permission")) continue;
            String trimmed = tag.trim();
            boolean isElementStart = trimmed.startsWith("<");
            if (!isElementStart) continue;
            int end = i;
            // Span extends over following attribute rows until next element start/end row.
            int j = i + 1;
            while (j < entries.size()) {
                String nt = entries.get(j).getTag();
                if (nt == null) break;
                String ntrim = nt.trim();
                if (ntrim.startsWith("<") || ntrim.startsWith("</")) break;
                end = j;
                j++;
            }
            for (int k = i; k <= end; k++) {
                XMLEntry attr = entries.get(k);
                String at = attr.getTag();
                if (at == null || !at.contains("android:name")) continue;
                String v = attr.getValue();
                if (v == null || v.isEmpty()) continue;
                if (disableSet.contains(v)) {
                    attr.setValue(DISABLED_PREFIX + v);
                    changed++;
                } else if (v.startsWith(DISABLED_PREFIX)
                        && enableSet.contains(v.substring(DISABLED_PREFIX.length()))) {
                    String restored = v.substring(DISABLED_PREFIX.length());
                    if (!restored.isEmpty()) {
                        attr.setValue(restored);
                        changed++;
                    }
                }
            }
        }
        if (changed > 0) {
            if (UiPrefs.genBackup(context)) {
                try {
                    FileUtils.copyFile(apkFile, new File(apkFile.getPath() + ".bak"));
                } catch (Exception ignored) {
                }
            }
            writeManifestEntries(apkFile, entries, res);
        }
        return changed;
    }

    /**
     * Permanently deletes matching permission entries (both enabled and disabled forms).
     * Returns the number of removed permissions.
     */
    public int removeManifestPermissions(File apkFile, List<String> perms) throws Exception {
        ManifestData data = decodeManifestWithRes(apkFile);
        List<XMLEntry> entries = data != null ? data.entries : null;
        List<ResEntry> res = data != null ? data.res : null;
        if (entries == null) entries = decodeManifest(apkFile);
        if (entries == null) throw new IOException(rss.getString(R.string.me_decode_fail));
        Set<String> targets = new HashSet<>(perms);
        int removed = 0;
        // Decoder emits "<uses-permission" open row followed by attribute rows
        // ("    android:name=\"...\"" merged with "/>"). Remove the whole span.
        for (int i = entries.size() - 1; i >= 0; i--) {
            XMLEntry item = entries.get(i);
            String tag = item.getTag();
            if (tag == null || !tag.contains("uses-permission")) continue;
            String trimmed = tag.trim();
            boolean isElementStart = trimmed.startsWith("<");
            if (!isElementStart) continue;
            int end = i;
            // Span extends over following attribute rows until next element start/end row.
            int j = i + 1;
            while (j < entries.size()) {
                String nt = entries.get(j).getTag();
                if (nt == null) break;
                String ntrim = nt.trim();
                if (ntrim.startsWith("<") || ntrim.startsWith("</")) break;
                end = j;
                j++;
            }
            boolean match = targets.contains(item.getValue());
            if (!match) {
                for (int k = i; k <= end; k++) {
                    XMLEntry attr = entries.get(k);
                    String at = attr.getTag();
                    if (at != null && at.contains("android:name") && targets.contains(attr.getValue())) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                for (int k = end; k >= i; k--) entries.remove(k);
                removed++;
            }
        }
        if (removed > 0) {
            if (UiPrefs.genBackup(context)) {
                try {
                    FileUtils.copyFile(apkFile, new File(apkFile.getPath() + ".bak"));
                } catch (Exception ignored) {
                }
            }
            writeManifestEntries(apkFile, entries, res);
        }
        return removed;
    }

    private interface PermissionOp {
        int apply() throws Exception;
    }

    private void runPermissionOp(File apkFile, PermissionOp op, int doneMsgRes,
                                 boolean[] sign, SignWrapper[] wrapper) {
        Runnable doEdit = () -> {
            ProgressManager pm2 = new ProgressManager(context, true).show();
            new Thread(() -> {
                try {
                    int changed;
                    try {
                        changed = op.apply();
                    } catch (Exception e) {
                        pm2.dismiss();
                        new ErrorUtil(context).showError(e);
                        return;
                    }
                    if (changed > 0 && sign[0]) wrapper[0].signApk(apkFile);
                    pm2.dismiss();
                    int done = changed;
                    context.handler.post(() -> {
                        Extensions.showMessage(context, rss.getString(doneMsgRes, done));
                        try { context.refreshAllPanes(); }
                        catch (Exception ignored) {
                            context.loadFolderInPane(apkFile.getParentFile(), true);
                        }
                    });
                } catch (Exception e) {
                    pm2.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        };
        if (sign[0]) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doEdit.run();
        });
        else doEdit.run();
    }

    public void showPermissionsDialog(File apkFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            String[] perms;
            try {
                PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(
                        apkFile.getPath(), PackageManager.GET_PERMISSIONS);
                perms = pi == null ? null : pi.requestedPermissions;
                if (perms == null || perms.length == 0) throw new IOException("No permissions found");
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
                return;
            }
            String[] labels = new String[perms.length];
            String[] baseNames = new String[perms.length];
            boolean[] keep = new boolean[perms.length];
            for (int i = 0; i < perms.length; i++) {
                boolean disabled = perms[i] != null && perms[i].startsWith(DISABLED_PREFIX);
                String base = disabled ? perms[i].substring(DISABLED_PREFIX.length()) : perms[i];
                baseNames[i] = base;
                keep[i] = !disabled;
                boolean dangerous = false;
                try {
                    int level = context.getPackageManager().getPermissionInfo(base, 0).protectionLevel
                            & PermissionInfo.PROTECTION_MASK_BASE;
                    dangerous = level == PermissionInfo.PROTECTION_DANGEROUS;
                } catch (Exception ignored) {
                }
                StringBuilder label = new StringBuilder(base);
                if (disabled) label.append(" (").append(rss.getString(R.string.disabled)).append(')');
                if (dangerous) label.append(" (dangerous)");
                labels[i] = label.toString();
            }
            pm.dismiss();
            context.handler.post(() -> {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                boolean[] sign = {settings.getBoolean("autosign", true)};

                View permDialog = LayoutInflater.from(context).inflate(R.layout.dialog_permissions, null, false);
                wireCompressionDropdown(permDialog);
                CheckBox autosign = permDialog.findViewById(R.id.autosign);
                autosign.setChecked(sign[0]);
                autosign.setOnCheckedChangeListener((buttonView, isChecked) ->
                        settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                permDialog.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                ListView permList = permDialog.findViewById(R.id.permList);
                permList.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_multiple_choice, labels));
                permList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                for (int i = 0; i < keep.length; i++) permList.setItemChecked(i, keep[i]);

                AlertDialog dialog = dialogUtil.getDialogBuilder()
                        .setTitle(rss.getString(R.string.me_perms_n, perms.length))
                        .setView(permDialog)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(rss.getString(R.string.apply), (d, which) -> {
                            SignWrapper[] wrapper = new SignWrapper[1];
                            List<String> toDisable = new ArrayList<>();
                            List<String> toEnable = new ArrayList<>();
                            for (int i = 0; i < perms.length; i++) {
                                boolean wantEnabled = permList.isItemChecked(i);
                                boolean isDisabled = perms[i] != null
                                        && perms[i].startsWith(DISABLED_PREFIX);
                                String base = baseNames[i];
                                if (base == null || base.isEmpty()) continue;
                                if (!wantEnabled && !isDisabled) toDisable.add(base);
                                else if (wantEnabled && isDisabled) toEnable.add(base);
                            }
                            if (toDisable.isEmpty() && toEnable.isEmpty()) return;
                            runPermissionOp(apkFile,
                                    () -> setPermissionsDisabled(apkFile, toDisable, toEnable),
                                    R.string.me_perms_updated, sign, wrapper);
                        })
                        .setNeutralButton(rss.getString(R.string.remove), (d, which) -> {
                            List<String> toRemove = new ArrayList<>();
                            for (int i = 0; i < perms.length; i++) {
                                if (permList.isItemChecked(i)) continue;
                                String base = baseNames[i];
                                if (base == null || base.isEmpty()) continue;
                                toRemove.add(base);
                                toRemove.add(DISABLED_PREFIX + base);
                            }
                            if (toRemove.isEmpty()) return;
                            int count = toRemove.size() / 2;
                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                                    .setTitle(rss.getString(R.string.remove))
                                    .setMessage(rss.getString(R.string.me_confirm_remove, count))
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton(android.R.string.ok, (dd, ww) -> {
                                        SignWrapper[] wrapper = new SignWrapper[1];
                                        runPermissionOp(apkFile,
                                                () -> removeManifestPermissions(apkFile, toRemove),
                                                R.string.me_perms_updated, sign, wrapper);
                                    })
                                    .create());
                        }).create();
                dialogUtil.styleAlertDialog(dialog);
            });
        }).start();
    }

    public void showManifestTogglesDialog(File apkFile) {
        String[] attrs = {"android:debuggable", "android:allowBackup", "android:usesCleartextTraffic",
                "android:requestLegacyExternalStorage", "android:largeHeap"};
        String[] labels = {rss.getString(R.string.me_tog_debug), rss.getString(R.string.me_tog_backup), rss.getString(R.string.me_tog_cleartext), rss.getString(R.string.me_tog_legacy), rss.getString(R.string.me_tog_heap)};
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            boolean[] current = new boolean[attrs.length];
            try {
                List<XMLEntry> entries = decodeManifest(apkFile);
                if (entries == null) throw new IOException(rss.getString(R.string.me_decode_fail));
                for (int i = 0; i < attrs.length; i++) {
                    String key = attrs[i].split(":")[1];
                    for (XMLEntry e : entries) {
                        if (e.getTag().contains(key)) {
                            current[i] = "true".equalsIgnoreCase(e.getValue());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
                return;
            }
            pm.dismiss();
            context.handler.post(() -> {
                LinearLayout root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
                root.setPadding(pad, pad / 2, pad, pad / 2);
                CheckBox[] boxes = new CheckBox[attrs.length];
                for (int i = 0; i < attrs.length; i++) {
                    boxes[i] = new CheckBox(context);
                    boxes[i].setText(labels[i]);
                    boxes[i].setChecked(current[i]);
                    root.addView(boxes[i]);
                }
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                boolean[] sign = new boolean[1];
                CheckBox autosign = new CheckBox(context);
                autosign.setText(rss.getString(R.string.auto_sign));
                autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                autosign.setOnCheckedChangeListener((b, c) -> settings.edit().putBoolean("autosign", sign[0] = c).apply());
                root.addView(autosign);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(rss.getString(R.string.me_toggles))
                        .setView(root)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(rss.getString(R.string.apply), (dialog, which) -> {
                            SignWrapper[] wrapper = new SignWrapper[1];
                            Runnable doEdit = () -> {
                                ProgressManager pm2 = new ProgressManager(context, true).show();
                                new Thread(() -> {
                                    try {
                                        int changed = 0;
                                        boolean backedUp = false;
                                        for (int i = 0; i < attrs.length; i++) {
                                            if (boxes[i].isChecked() == current[i]) continue;
                                            try {
                                                if (!backedUp && UiPrefs.genBackup(context)) {
                                                    try {
                                                        FileUtils.copyFile(apkFile, new File(apkFile.getPath() + ".bak"));
                                                    } catch (Exception ignored) {
                                                    }
                                                    backedUp = true;
                                                }
                                                writeManifestAttrValue(apkFile, attrs[i], Boolean.toString(boxes[i].isChecked()));
                                                changed++;
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        if (changed > 0 && sign[0]) wrapper[0].signApk(apkFile);
                                        pm2.dismiss();
                                        int done = changed;
                                        context.handler.post(() -> {
                                            Extensions.showMessage(context, rss.getString(R.string.me_toggles_applied, done));
                                            try { context.refreshAllPanes(); }
                                            catch (Exception ignored) {
                                                context.loadFolderInPane(apkFile.getParentFile(), true);
                                            }
                                        });
                                    } catch (Exception e) {
                                        pm2.dismiss();
                                        new ErrorUtil(context).showError(e);
                                    }
                                }).start();
                            };
                            if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                wrapper[0] = sw;
                                doEdit.run();
                            });
                            else doEdit.run();
                        }).create());
            });
        }).start();
    }

    private void writeManifestEntries(File apkFile, List<XMLEntry> entries) throws Exception {
        writeManifestEntries(apkFile, entries, null);
    }

    private void writeManifestEntries(File apkFile, List<XMLEntry> entries, List<ResEntry> resEntries) throws Exception {
        // The same resource table used at decode time must be supplied at encode time:
        // symbolic references (e.g. "@string/app_name") otherwise resolve to 0 and break the APK.
        replaceZipEntry(apkFile, "AndroidManifest.xml", new aXMLEncoder().encodeString(entries, context, resEntries));
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
        ZipParameters zp = MergeUtil.newPreferredZipParameters(context);
        zp.setFileNameInZip(entryPath);
        try (ZipFile sourceZip = new ZipFile(apkFile); InputStream is = new ByteArrayInputStream(newBytes)) {
            sourceZip.addStream(is, zp);
        }
    }

    private void replaceZipEntry(File apkFile, String entryPath, InputStream is)
            throws IOException {
        ZipParameters zp = MergeUtil.newPreferredZipParameters(context);
        zp.setFileNameInZip(entryPath);
        try (ZipFile sourceZip = new ZipFile(apkFile)) {
            sourceZip.addStream(is, zp);
        }
    }
}
