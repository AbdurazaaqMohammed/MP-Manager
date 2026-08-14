package io.github.abdurazaaqmohammed.adapters;

import static android.content.Context.RECEIVER_NOT_EXPORTED;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

import android.app.Dialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.core.content.res.ResourcesCompat;

import android.provider.MediaStore;
import android.provider.Settings;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ScrollView;

import android.content.ClipData;
import android.content.DialogInterface;
import android.graphics.Typeface;

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.apksig.ApkVerifier;
import com.apk.axml.APKParser;
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
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.decompile.DecompileOptions;
import com.reandroid.apkeditor.decompile.Decompiler;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.RefactorOptions;
import com.reandroid.archive.ArchiveFile;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

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
import java.security.cert.X509Certificate;


import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager;
import io.github.abdurazaaqmohammed.commandhelper.ProfileManager.Profile;
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.ApkCompareUtil;
import io.github.abdurazaaqmohammed.utils.ApkInfoUtil;
import io.github.abdurazaaqmohammed.utils.ApkOptimizer;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RunUtil;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.activity.DexEditorActivity;
import mt.modder.hub.apkCloner.util.ApkCloner;

public class MainFilesArrayAdapter extends RecyclerView.Adapter<MainFilesArrayAdapter.ViewHolder> {
    private void showEditManifestDialog(File apkFile) {
        final Resources rss = context.rss;
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
        ProgressManager pm = new ProgressManager(context, true);

        fpd.setDialogSelectionListener(files -> new Thread(() -> {
            String iconPath = findIconPathInManifest(apkFile);

            try (InputStream is = FileUtils.getInputStream(files[0])) {
                pm.show().setText(context.rss.getString(R.string.adding, files[0]));
                replaceZipEntry(apkFile,
                        iconPath != null ? iconPath : "res/mipmap-xxhdpi-v4/ic_launcher.png",
                        is);
                pm.dismiss();
                context.handler.post(() -> Toast.makeText(context, "Icon changed", Toast.LENGTH_LONG).show());
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
        this.values = isInZip ? values : getNewValues(values, (File) parent);
        this.context = context;
        this.pane1 = pane1;
        this.isInZip = isInZip;
        this.currentZipPath = currentZipPath;
        dialogUtil = context.dialogUtil;
        uiHelper = context.uiHelper;
        ensureCachedIcons(context.getResources(), context.theme);
    }

    private void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        if (zipEntry.isDirectory()) {
            fileIconView.setImageDrawable(cachedFolderIcon);
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

    private static Drawable cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon;
    private static int cachedIconTheme = -1;

    private static void ensureCachedIcons(Resources res, int theme) {
        if (cachedIconTheme == theme) return;
        cachedIconTheme = theme;
        int color = theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE;
        cachedFolderIcon  = tintAndCache(res, R.drawable.folder_24px, color);
        cachedApkIcon     = tintAndCache(res, R.drawable.apk_document_24px, color);
        cachedImageIcon   = tintAndCache(res, R.drawable.image_24px, color);
        cachedVideoIcon   = tintAndCache(res, R.drawable.video_24px, color);
        cachedMusicIcon   = tintAndCache(res, R.drawable.music_24px, color);
        cachedArchiveIcon = tintAndCache(res, R.drawable.baseline_folder_zip_24, color);
        cachedPdfIcon     = tintAndCache(res, R.drawable.pdf_24px, color);
        cachedTextIcon    = tintAndCache(res, R.drawable.baseline_text_snippet_24, color);
        cachedFileIcon    = tintAndCache(res, R.drawable.baseline_insert_drive_file_24, color);
    }

    private static Drawable tintAndCache(Resources res, int id, int color) {
        Drawable d = ResourcesCompat.getDrawable(res, id, null);
        if (d == null) return null;
        d = d.mutate();
        DrawableCompat.setTint(d, color);
        return d;
    }

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
        if (!isInZip) {
            Drawable cached = iconCache.get(path);
            if (cached != null) {
                fileIconView.setImageDrawable(cached);
                return;
            }
        }

        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        if (".apk".equals(ext)) {
            fileIconView.setImageDrawable(cachedApkIcon);
            if (!isInZip) loadApkIconAsync(path, fileIconView);
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(cachedImageIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(cachedVideoIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, true);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(cachedMusicIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(cachedArchiveIcon);
        } else if (".pdf".equals(ext)) {
            fileIconView.setImageDrawable(cachedPdfIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            fileIconView.setImageDrawable(cachedTextIcon);
        } else {
            fileIconView.setImageDrawable(cachedFileIcon);
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
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
                                if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null || !path.equals(fileIconView.getTag())) return;
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            iconCache.put(path, icon);
            context.runOnUiThread(() -> {
                if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
            });
        });
    }

    private void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        if (file.isFile()) {
            fileDateView.setVisibility(View.VISIBLE);
            Date lastModifiedDate = new Date(file.lastModified());
            SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm");
            String formattedDate = sdf.format(lastModifiedDate);
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(new StringBuilder(formattedDate).append(' ').append(FileSize.getHumanReadableFileSize(file.length())));
        } else {
            fileIconView.setImageDrawable(cachedFolderIcon);
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
        frameworkVersion.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("fwVer", frameworkVersions[position]).apply());

        String[] decodeTypesArray = new String[]{"xml", "json", "raw", "sig"};
        int savedDecodeType = settings.getInt("decodeTypes", 0);
        ArrayAdapter<String> decodeAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, decodeTypesArray);
        decodeTypes.setAdapter(decodeAdapter);
        decodeTypes.setText(decodeTypesArray[savedDecodeType], false);
        decodeTypes.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("decodeTypes", position).apply());

        String[] dexLibraryArray = new String[]{"Internal (REAndroid)", "developer-krushna"};
        int savedDexLib = settings.getInt("dexLib", 0);
        ArrayAdapter<String> dexAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, dexLibraryArray);
        dexLibrary.setAdapter(dexAdapter);
        dexLibrary.setText(dexLibraryArray[savedDexLib], false);
        dexLibrary.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("dexLib", position).apply());

        int savedLoadDex = settings.getInt("loadDex", 3);
        loadDex.setText(String.valueOf(savedLoadDex));
        loadDex.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    settings.edit().putInt("loadDex", Integer.parseInt(s.toString())).apply();
                } catch (NumberFormatException ignored) {}
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        flagDex.setChecked(settings.getBoolean("flagDex", false));
        flagDex.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagDex", isChecked).apply());

        noDexDebug.setChecked(settings.getBoolean("noDexDebug", true));
        noDexDebug.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("noDexDebug", isChecked).apply());

        dexMarkers.setChecked(settings.getBoolean("dexMarkers", false));
        dexMarkers.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("dexMarkers", isChecked).apply());

        flagForce.setChecked(settings.getBoolean("flagForce", false));
        flagForce.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagForce", isChecked).apply());

        keepResPath.setChecked(settings.getBoolean("keepResPath", false));
        keepResPath.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("keepResPath", isChecked).apply());

        splitJson.setChecked(settings.getBoolean("splitJson", true));
        splitJson.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("splitJson", isChecked).apply());

        vrd.setChecked(settings.getBoolean("vrd", true));
        vrd.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("vrd", isChecked).apply());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("Decompile Options")
                .setView(dialogView)
                .setPositiveButton("Decompile", (d, which) -> {

                    ProgressManager pm = new ProgressManager(context, true).show();
                    APKLogger logger = pm.getLogger();

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
                            pm.dismiss();
                            context.handler.post(() -> {
                                Toast.makeText(context, "Decompiled to " + outputFile.getName(), Toast.LENGTH_SHORT).show();
                                context.reloadCurrentFolder();
                            });
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(context).showError(e);
                            logger.close();
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    @Override
    public int getItemCount() { return values.length; }

    public Object getItem(int position) { return values[position]; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameView, fileDateView;
        final ImageView fileIconView;
        ViewHolder(View v) {
            super(v);
            fileNameView = v.findViewById(R.id.fileName);
            fileIconView = v.findViewById(R.id.fileIcon);
            fileDateView = v.findViewById(R.id.fileDate);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final View convertView = holder.itemView;
        position = holder.getBindingAdapterPosition();
        Object item = values[position];
        File file;
        ZipEntryInfo entry;
        String fileName;

        holder.fileNameView.setText("");
        holder.fileDateView.setText("");
        holder.fileIconView.setImageDrawable(null);

        if (isInZip) {
            entry = (ZipEntryInfo) item;
            setupZipEntryView(entry, holder.fileIconView, holder.fileDateView);
            file = null;
            holder.fileNameView.setText(fileName = entry.getName());
        } else {
            entry = null;
            file = (File) item;
            setupFileView(file, holder.fileIconView, holder.fileDateView);
            holder.fileNameView.setText(fileName = (position == 0 ? ".." : file.getName()));
        }

        convertView.setBackgroundColor(selectedPositions.contains(position) ? Color.DKGRAY : Color.TRANSPARENT);
        int finalPosition = position;
        new Thread(new Runnable() {
            @Override
            public void run() {
                View.OnClickListener originalClickListener;
                if(isInZip && finalPosition == 0 && entry.getFullPath() == null) {
                    originalClickListener = v -> context.loadFolderInPane(entry.getZipFile().getParentFile(), pane1);
                } else {
                    originalClickListener = isMultiSelectMode ? v -> {
                        context.lastPaneSelected = pane1 ? 1 : 2;
                        handleMultiSelect(finalPosition);
                    } : !isInZip && file.isFile() ?
                        v -> {
                            context.lastPaneSelected = pane1 ? 1 : 2;
                            context.setCurrentFolder(file.getParentFile(), getOldValues());
                            String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
                            if (fileName.endsWith(".txt") || fileName.endsWith(".json")
                                || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
                                || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
                                context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", file.getPath()));
                            } else if(HashUtil.isChecksumFile(fileName)) {
                                showHashVerifyDialog(file);
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
                            } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
                                context.openImageViewer(file.getPath());
                            } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
                                context.playMediaFile(file.getPath());
                            } else if ((ext.equals(".apk"))) {
                                showApkInfoDialog(file, fileName);
                            } else {
                                String bak = ".bak";
                                if(ext.equals(bak)) {
                                    View et = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
                                    context.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                                    EditText tv = et.findViewById(R.id.m_et_edittext);
                                    String newName = fileName.replace(bak, "");
                                    tv.setText(newName);
                                    tv.requestFocus();
                                    tv.post(() -> {
                                        tv.setSelection(0, newName.indexOf(FilenameUtils.getExtension(newName)) - 1);
                                        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                                        if (imm != null) imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT);
                                    });
                                    new MaterialAlertDialogBuilder(context)
                                    .setTitle("Restore backup")
                                    .setView(et)
                                    .setPositiveButton("Restore", (dialog, which) -> {
                                        String bakPath = file.getPath();
                                        String origPath = bakPath.replace(bak, "");
                                        File orig = new File(origPath);
                                        boolean origExists = orig.exists();
                                        if(origExists) {
                                            orig.renameTo(new File(origPath + "_tmp_" + bak));
                                        }
                                        file.renameTo(new File(origPath));
                                        if(origExists) orig.renameTo(new File(bakPath));
                                    })
                                    .setNegativeButton(android.R.string.cancel, null).show();
                                } else if (fileName.endsWith(".zip")) {
                                    openZipFile(file, null);
                                } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                                    dialogUtil.styleAlertDialog(
                                            dialogUtil.getDialogBuilder().setSingleChoiceItems(new CharSequence[] { context.rss.getString(R.string.extract), context.rss.getString(R.string.open_with) }, -1, (dialog, which) -> {
                                                dialog.dismiss();
                                                if (which == 0) extractArchive(file);
                                                else {
                                                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                                    context.startActivity(new Intent(Intent.ACTION_VIEW)
                                                            .setDataAndType(uri, context.getContentResolver().getType(uri))
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                                                }
                                            }).create());
                                } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                                    String[] items = new String[] { "Install", "View", "Sign", "AntiSplit/merge to APK" };
                                    dialogUtil.styleAlertDialog(
                                            dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                                                dialog.dismiss();
                                                try {
                                                    switch (which) {
                                                        case 0:
                                                            if (LegacyUtils.aboveSdk20) {
                                                                ProgressManager pm = new ProgressManager(context, true).show();
                                                                APKLogger logger = pm.getLogger();
                                                                new Thread(() -> {
                                                                    BroadcastReceiver receiver = null;
                                                                    try (ZipFile zf = new ZipFile(file)) {
                                                                        int sessionId = new APKInstallHelper(context).installApk(zf, logger);
                                                                        receiver = new BroadcastReceiver() {
                                                                            @Override public void onReceive(Context context, Intent intent) {
                                                                                if ("DISMISS_DIALOG".equals(intent.getAction())) {
                                                                                    pm.dismiss();
                                                                                    context.unregisterReceiver(this);
                                                                                }
                                                                            }
                                                                        };
                                                                        if (Build.VERSION.SDK_INT >= 33) {
                                                                            context.registerReceiver(receiver, new IntentFilter("DISMISS_DIALOG"), RECEIVER_NOT_EXPORTED);
                                                                        } else context.registerReceiver(receiver, new IntentFilter("DISMISS_DIALOG"));
                                                                    } catch (Exception e) {pm.dismiss(); new ErrorUtil(context).showError(e); if(receiver != null) context.unregisterReceiver(receiver);}
                                                                }).start();
                                                            } else {
                                                                Toast.makeText(context, "Installing split APKs is not supported on this version of Android :(", Toast.LENGTH_SHORT).show();

                                                                // We should check if apk minsdk <20 here
                                                                Toast.makeText(context, "You could try merging the APK then installing it", Toast.LENGTH_SHORT).show();
                                                            }
                                                            break;
                                                        case 1:
                                                            openZipFile(file, null);
                                                            break;
                                                        case 2:
                                                            SignatureKeyDialog.show(context, file, true);
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
                            }
                        } : (View.OnClickListener) v -> {
                        context.lastPaneSelected = pane1 ? 1 : 2;
                        if (isInZip)
                            handleZipEntryClick(entry);
                        else
                            context.loadFolderInPane(file, pane1);
                    };
                }

                int finalPosition1 = finalPosition;
                View.OnLongClickListener originalLongClickListener = v -> {
                    context.lastPaneSelected = pane1 ? 1 : 2;
                    if (isInZip) {
                        context.setCurrentFolder(currentZipPath, Arrays.asList(values));
                    } else
                        context.setCurrentFolder(file.getParentFile(), getOldValues());

                    boolean multi = !selectedPositions.isEmpty();
                    String direction = pane1 ? "->" : "<-";
                    String[] baseItems = new String[] { "Copy " + direction, "Move " + direction, "Rename", "Delete", "Compress", "Properties", "Share", "Open with", "Bookmark", "Command Helper", context.getString(R.string.checksums) };
                    List<String> itemsList = new ArrayList<>(Arrays.asList(baseItems));

                    if (multi && !isInZip) {
                        boolean allApks = true;
                        for (int bp : selectedPositions) {
                            Object selected = values[bp];
                            if (!(selected instanceof File) || !((File) selected).getName().toLowerCase(Locale.ENGLISH).endsWith(".apk")) {
                                allApks = false;
                                break;
                            }
                        }
                        if (allApks) {
                            itemsList.add(context.getString(R.string.batch_sign));
                            itemsList.add(context.getString(R.string.batch_optimize));
                            itemsList.add(context.getString(R.string.batch_install));
                        }
                    }

                    if (!multi && !isInZip && !file.isDirectory() && ArchiveUtil.isSupportedArchive(fileName)) {
                        itemsList.add(context.getString(R.string.extract));
                    }

                    RecyclerView.Adapter a = ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
                    Object compareFile1 = null;
                    Object compareFile2 = null;
                    if(a instanceof MainFilesArrayAdapter) {
                        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) a;
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
                            if (!isZip1 && !isZip2 && !ext1.equals("arsc") && !ext2.equals("arsc")) {
                                itemsList.add("Compare Text");
                                if (compareFile1 instanceof File && compareFile2 instanceof File
                                        && !((File) compareFile1).isDirectory() && !((File) compareFile2).isDirectory())
                                    itemsList.add(context.getString(R.string.compare_hashes));
                            }
                            if (ext1.equals("apk") && ext2.equals("apk")
                                    && compareFile1 instanceof File && compareFile2 instanceof File)
                                itemsList.add(context.getString(R.string.compare_apks));
                        }
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
                        ProgressManager pm;
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
                                    return;
                                case "Compare ZIP":
                                    new CompareZipDialog(context,
                                            finalCompareFile1 instanceof File ? (File) finalCompareFile1 : ((ZipEntryInfo) finalCompareFile1).getZipFile(),
                                            finalCompareFile2 instanceof File ? (File) finalCompareFile2 : ((ZipEntryInfo) finalCompareFile2).getZipFile()
                                    ).show();
                                    return;
                                case "Compare ARSC":
                                    new CompareArscDialog(context,
                                            finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath(),
                                            finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath()
                                    ).show();
                                    return;
                                case "Compare hashes":
                                    showCompareHashesDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                    return;
                                case "Checksums":
                                    if (isInZip) {
                                        Toast.makeText(context, context.getString(R.string.checksum_not_supported_zip), Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    List<File> checksumFiles = new ArrayList<>();
                                    if (multi) {
                                        for (int cmdPos : selectedPositions) checksumFiles.add((File) values[cmdPos]);
                                    } else {
                                        checksumFiles.add(file);
                                    }
                                    showChecksumsDialog(checksumFiles);
                                    return;
                                case "Compare APKs":
                                    showCompareApksDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                    return;
                                case "Batch sign APKs": {
                                    List<File> apks = new ArrayList<>();
                                    for (int bp : selectedPositions) apks.add((File) values[bp]);
                                    batchSignApks(apks);
                                    return;
                                }
                                case "Batch optimize APKs": {
                                    List<File> apks = new ArrayList<>();
                                    for (int bp : selectedPositions) apks.add((File) values[bp]);
                                    batchOptimizeApks(apks);
                                    return;
                                }
                                case "Install APKs": {
                                    for (int bp : selectedPositions) InstallUtil.installApk(context, (File) values[bp]);
                                    return;
                                }
                                case "Command Helper":
                                    if (isInZip) {
                                        Toast.makeText(context, "Command Helper not supported for zip entries", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    ArrayList<String> cmdFilePaths = new ArrayList<>();
                                    if (multi) {
                                        for (int cmdPos : selectedPositions) cmdFilePaths.add(((File) values[cmdPos]).getAbsolutePath());
                                    } else {
                                        cmdFilePaths.add(file.getAbsolutePath());
                                    }
                                    showCommandHelperDialog(cmdFilePaths);
                                    return;
                                case "Extract":
                                    if (isInZip || multi) return;
                                    extractArchive(file);
                                    return;
                                default:
                                    switch (position1) {
                                        case 0:
                                            pm = new ProgressManager(context, true).show();
                                            new Thread(() -> {
                                                try {
                                                    if (multi) for (int f : selectedPositions) {
                                                        Object file1 = values[f];
                                                        pm.setText(context.rss.getString(R.string.copying, file1));
                                                        copy(file1);
                                                    }
                                                    else {
                                                        pm.setText(context.rss.getString(R.string.copying, item));
                                                        copy(item);
                                                    }
                                                    pm.dismiss();
                                                } catch (Exception e) {
                                                    pm.dismiss();
                                                    new ErrorUtil(context).showError(e);
                                                }
                                            }).start();
                                            break;
                                        case 1:
                                            if (context.pane1Folder == context.pane2Folder) {
                                                break;
                                            }
                                            pm = new ProgressManager(context, true).show();
                                            new Thread(() -> {
                                                try {
                                                    pm.setText(context.rss.getString(R.string.copying, item));
                                                    move(item);
                                                    pm.dismiss();
                                                } catch (Exception e) {
                                                    pm.dismiss();
                                                    new ErrorUtil(context).showError(e);
                                                }
                                            }).start();
                                            break;
                                        case 2:
                                            if (multi) {
                                                RenameUtil.showMultiRenameDialog(context, selectedPositions, isInZip, values, pane1, currentZipPath);
                                                return;
                                            }
                                            MaterialAlertDialogBuilder renameDialog = dialogUtil.getDialogBuilder();
                                            View rnm = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
                                            EditText renameInput = rnm.findViewById(R.id.m_et_edittext);//new EditText(context);
                                            renameInput.setText(fileName);
                                            renameInput.requestFocus();
                                            renameInput.post(() -> {
                                                renameInput.setSelection(0, fileName.indexOf(FilenameUtils.getExtension(fileName)) - 1);
                                                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                                                if (imm != null) imm.showSoftInput(renameInput, InputMethodManager.SHOW_IMPLICIT);
                                            });
                                            renameDialog
                                                    .setTitle(context.rss.getString(R.string.rename_1, fileName))
                                                    .setView(rnm)
                                                    .setNegativeButton(android.R.string.cancel, null)
                                                    .setNeutralButton(android.R.string.paste, (dialog1, which) -> {
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
                                                    .setPositiveButton(android.R.string.ok, (dialog3, which) -> {
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
                                            pm = new ProgressManager(context, true);
                                            MaterialAlertDialogBuilder deleteDialog = dialogUtil.getDialogBuilder();
                                            CharSequence filesToDisplay = getFilesToDisplay(multi, finalPosition1);
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
                                            deleteDialog.setTitle("Alert").setPositiveButton("Yes", (dialog3, which) -> {
                                                SignWrapper[] wrapper = new SignWrapper[1];
                                                Runnable doDelete = () -> {
                                                    pm.show();
                                                    new Thread(() -> {
                                                        try {
                                                            if(isInZip) FileUtils.copyFile(zipFile, new File(zipFile.getParent(), zipFile.getName() + ".bak"));
                                                            if (multi) {
                                                                if (!isInZip) {
                                                                    File selectedFile = null;
                                                                    for (int i : selectedPositions) {
                                                                        selectedFile = (File) values[i];
                                                                        File finalSelectedFile1 = selectedFile;
                                                                        if (finalSelectedFile1 != null)
                                                                            pm.setText(context.rss.getString(R.string.deleting, finalSelectedFile1.getName()));

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
                                                                    if (sign[0]) wrapper[0].signApk(zipFile);
                                                                }
                                                            } else if (!isInZip) {
                                                                int total = (int) Util.countInsideFolder(file).total();
                                                                pm.setProgress(0, total);
                                                                pm.setText(context.rss.getString(R.string.deleting, file.getName()));

                                                                if (file.isDirectory()) Util.deleteDir(file, pm, total);
                                                                else file.delete();
                                                                handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1));
                                                            } else {
                                                                deleteZipEntry(entry);
                                                                if (sign[0]) wrapper[0].signApk(zipFile);
                                                            }
                                                            pm.dismiss();
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    }).start();
                                                };
                                                if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                                    wrapper[0] = sw;
                                                    doDelete.run();
                                                }); else doDelete.run();
                                            }).setNegativeButton(android.R.string.cancel, (dialog1, which1) -> pm.dismiss());
                                            dialogUtil.styleAlertDialog(deleteDialog.create());
                                            break;
                                        case 4:
                                            if (isInZip) {
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

                                            String[] archiveFormats = ArchiveUtil.getSupportedCreateExts();
                                            AutoCompleteTextView archiveFormatInput = compressView.findViewById(R.id.compress_format);
                                            archiveFormatInput.setText(archiveFormats[0]);
                                            archiveFormatInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, archiveFormats));

                                            AutoCompleteTextView compressLevelInput = compressView.findViewById(R.id.compress_level);
                                            compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                                            List<String> compressionLevels = new ArrayList<>();
                                            for (CompressionLevel cl : CompressionLevel.values()) compressionLevels.add(cl.name());
                                            compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
                                            compressLevelInput.setOnItemClickListener((parent2, view1, position2, id1) -> settings.edit().putString("compressLevel", compressionLevels.get(position2)).apply());
                                            compressDialog.setView(compressView);
                                            compressDialog.setNegativeButton(context.rss.getString(android.R.string.cancel), null);
                                            pm = new ProgressManager(context, true);
                                            compressDialog.setPositiveButton(context.rss.getString(R.string.compress), (dialog4, which) -> {
                                                pm.show();
                                                new Thread(() -> {
                                                    String name = ((TextInputEditText) compressView.findViewById(R.id.filename_compress_edittext)).getText().toString().trim();
                                                    if (name.isEmpty()) name = multi ? parentFileName : fileName;
                                                    String format = ((AutoCompleteTextView) compressView.findViewById(R.id.compress_format)).getText().toString().trim();
                                                    if (!format.startsWith(".")) format = "." + format;
                                                    if (!name.toLowerCase(Locale.ENGLISH).endsWith(format)) name += format;
                                                    File outputZip = new File(parentFile2, name);

                                                    List<File> sources = new ArrayList<>();
                                                    if (multi) {
                                                        for (int i : selectedPositions) sources.add((File) values[i]);
                                                    } else sources.add(file);

                                                    if (format.equals(".zip")) {
                                                        ZipParameters zipParameters = new ZipParameters();
                                                        CompressionLevel compressionLevel = CompressionLevel.valueOf(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                                                        zipParameters.setCompressionLevel(compressionLevel);
                                                        if (compressionLevel == CompressionLevel.NO_COMPRESSION)
                                                            zipParameters.setCompressionMethod(CompressionMethod.STORE);
                                                        CharSequence pw = ((TextView) compressView.findViewById(R.id.pw_edittext)).getText();

                                                        try (ZipFile zf = new ZipFile(outputZip)) {
                                                            if (!TextUtils.isEmpty(pw)) {
                                                                zipParameters.setEncryptFiles(true);
                                                                zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                                                                zf.setPassword(pw.toString().toCharArray());
                                                            }
                                                            for (File source : sources) {
                                                                if (source.isDirectory())
                                                                    zf.addFolder(source, zipParameters);
                                                                else zf.addFile(source, zipParameters);
                                                            }
                                                            pm.dismiss();
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    } else {
                                                        try {
                                                            ArchiveUtil.create(outputZip, sources);
                                                            pm.dismiss();
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    }
                                                }).start();
                                            });
                                            pm.setText(context.rss.getString(R.string.compressing));
                                            context.handler.post(compressDialog::show);
                                            break;
                                        case 5:
                                            View propView = LayoutInflater.from(context).inflate(R.layout.dialog_properties, null);
                                            TextView propTitle = propView.findViewById(R.id.propertyTitle);
                                            TextView propSubtitle = propView.findViewById(R.id.propertySubtitle);
                                            ImageView propIcon = propView.findViewById(R.id.propertyIcon);
                                            LinearLayout propRows = propView.findViewById(R.id.propertyRows);
                                            LinearLayout checksumSection = propView.findViewById(R.id.checksumSection);
                                            LinearLayout checksumRows = propView.findViewById(R.id.checksumRows);

                                            String displayName = getFilesToDisplay(multi, finalPosition1).toString();
                                            propTitle.setText(displayName);

                                            boolean isFolder = isInZip ? entry.isDirectory() : file.isDirectory();
                                            String typeStr = multi ? context.getString(R.string.items, selectedPositions.size())
                                                    : (isFolder ? context.getString(R.string.folder) : context.getString(R.string.file));
                                            propSubtitle.setText(typeStr);

                                            String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
                                            boolean hasIcon = false;
                                            if (!isInZip && !multi && file.isFile() && FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
                                                Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                                                if (bmp != null) {
                                                    propIcon.setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(48), dp(48), true));
                                                    propIcon.setColorFilter(null);
                                                    hasIcon = true;
                                                }
                                            }
                                            if (!hasIcon) {
                                                propIcon.setImageResource(isFolder ? R.drawable.folder_24px : R.drawable.baseline_insert_drive_file_24);
                                                propIcon.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
                                            }
                                            GradientDrawable iconBg = new GradientDrawable();
                                            iconBg.setShape(GradientDrawable.OVAL);
                                            iconBg.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.GRAY));
                                            propIcon.setBackground(iconBg);
                                            int iconPad = dp(10);
                                            propIcon.setPadding(iconPad, iconPad, iconPad, iconPad);

                                            addPropertyRow(propRows, context.getString(R.string.name), displayName);
                                            if (!isInZip && file.getParentFile() != null)
                                                addPropertyRow(propRows, context.getString(R.string.parent), file.getParentFile().getName());
                                            addPropertyRow(propRows, context.getString(R.string.type), typeStr);

                                            long size = 0;
                                            if (multi) {
                                                for (int p : selectedPositions) {
                                                    Object o = values[p];
                                                    if (o instanceof File) size += ((File) o).length();
                                                    else if (o instanceof ZipEntryInfo) size += ((ZipEntryInfo) o).getSize();
                                                }
                                            } else size = isInZip ? entry.getSize() : file.length();
                                            TextView sizeValue = addPropertyRow(propRows, context.getString(R.string.size), Formatter.formatFileSize(context, size));

                                            if (!isInZip) {
                                                addPropertyRow(propRows, context.getString(R.string.modified),
                                                        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified()));
                                                if (!multi && file.isDirectory()) {
                                                    TextView sizeText = sizeValue;
                                                    new Thread(() -> {
                                                        long folderSize = getFolderSize(file, null);
                                                        context.handler.post(() -> sizeText.setText(FileSize.getHumanReadableFileSize(folderSize)));
                                                    }).start();
                                                }
                                            }

                                            if (!isInZip && !multi && file.isFile()) {
                                                propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                                                    checksumRows.removeAllViews();
                                                    startChecksumComputation(checksumRows, file);
                                                });
                                                startChecksumComputation(checksumRows, file);
                                            } else {
                                                checksumSection.setVisibility(View.GONE);
                                            }

                                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                                                    .setTitle(context.getString(R.string.properties))
                                                    .setView(propView)
                                                    .setPositiveButton(android.R.string.ok, null)
                                                    .create());
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
                context.handler.post(() -> convertView.setOnTouchListener(new SwipeTouchListener(
                        context,
                        originalClickListener,
                        originalLongClickListener,
                        finalPosition,
                        MainFilesArrayAdapter.this,
                        pane1 ? 1 : 2)));
            }
        }).start();

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

    private TextView addPropertyRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        TextView valueView = new TextView(context);
        valueView.setText(value == null ? "—" : value);
        valueView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        valueView.setTextIsSelectable(true);
        valueView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(context, valueView.getText());
            return true;
        });
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));

        row.addView(labelView);
        row.addView(valueView);
        container.addView(row);
        return valueView;
    }

    private TextView addChecksumRow(LinearLayout container, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        TextView valueView = new TextView(context);
        valueView.setText("—");
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextSize(12);
        valueView.setTextIsSelectable(true);
        valueView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        valueView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(context, valueView.getText());
            return true;
        });
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton copyButton = new ImageButton(context);
        copyButton.setImageResource(R.drawable.baseline_content_copy_24);
        copyButton.setBackgroundColor(Color.TRANSPARENT);
        copyButton.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        int pad = dp(8);
        copyButton.setPadding(pad, pad, pad, pad);
        copyButton.setContentDescription(context.getString(android.R.string.copy));
        copyButton.setOnClickListener(v -> CopyUtil.copyToClipboard(context, valueView.getText()));
        copyButton.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));

        row.addView(labelView);
        row.addView(valueView);
        row.addView(copyButton);
        container.addView(row);
        return valueView;
    }

    private void addChecksumFileHeader(LinearLayout container, String fileName) {
        TextView header = new TextView(context);
        header.setText(fileName);
        header.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        header.setPadding(0, dp(10), 0, dp(4));
        container.addView(header);
    }

    private void startChecksumComputation(LinearLayout container, File file) {
        Map<String, TextView> views = new HashMap<>();
        for (String algo : HashUtil.ALGORITHMS) views.put(algo, addChecksumRow(container, algo));
        new Thread(() -> {
            try {
                Map<String, String> hashes = HashUtil.hashAll(file);
                context.handler.post(() -> {
                    for (Map.Entry<String, String> e : hashes.entrySet()) {
                        TextView tv = views.get(e.getKey());
                        if (tv != null) tv.setText(e.getValue());
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> {
                    for (TextView tv : views.values()) tv.setText("—");
                });
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showChecksumsDialog(List<File> files) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.computing_checksums));
        new Thread(() -> {
            try {
                List<Map<String, String>> results = new ArrayList<>();
                for (File f : files) {
                    pm.setText(context.getString(R.string.hashing, f.getName()));
                    results.add(HashUtil.hashAll(f));
                }
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_checksums, null);
                    LinearLayout container = view.findViewById(R.id.checksumContainer);
                    if (files.size() == 1) {
                        for (Map.Entry<String, String> e : results.get(0).entrySet()) {
                            TextView valueView = addChecksumRow(container, e.getKey());
                            valueView.setText(e.getValue());
                        }
                    } else {
                        for (int i = 0; i < files.size(); i++) {
                            addChecksumFileHeader(container, files.get(i).getName());
                            for (Map.Entry<String, String> e : results.get(i).entrySet()) {
                                TextView valueView = addChecksumRow(container, e.getKey());
                                valueView.setText(e.getValue());
                            }
                        }
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(files.size() > 1 ? context.getString(R.string.checksums) : context.getString(R.string.checksums_of, files.get(0).getName()))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void addVerifyRow(LinearLayout container, HashUtil.CheckResult r) {
        int color = r.valid ? Color.rgb(0x4C, 0xAF, 0x50) : Color.rgb(0xF4, 0x43, 0x36);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(6), 0, dp(6));

        ImageView icon = new ImageView(context);
        icon.setImageResource(r.valid ? R.drawable.baseline_check_circle_24 : R.drawable.baseline_remove_circle_24);
        icon.setColorFilter(color);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(context);
        nameView.setText(r.fileName);
        nameView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        nameView.setTypeface(null, Typeface.BOLD);
        info.addView(nameView);

        if (r.valid) {
            TextView statusView = new TextView(context);
            statusView.setText(context.getString(R.string.verify_checksum_pass) + " · " + r.algorithm + ": " + r.actual);
            statusView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            statusView.setTextColor(color);
            info.addView(statusView);
        } else {
            TextView statusView = new TextView(context);
            statusView.setText(r.error == null ? context.getString(R.string.verify_checksum_fail) : r.error);
            statusView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            statusView.setTextColor(color);
            info.addView(statusView);
            TextView expectedView = new TextView(context);
            expectedView.setText(context.getString(R.string.expected_hash, r.expected));
            expectedView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            expectedView.setTypeface(Typeface.MONOSPACE);
            info.addView(expectedView);
            if (r.actual != null) {
                TextView actualView = new TextView(context);
                actualView.setText(context.getString(R.string.actual_hash, r.actual));
                actualView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                actualView.setTypeface(Typeface.MONOSPACE);
                info.addView(actualView);
            }
        }

        row.addView(icon);
        row.addView(info);
        container.addView(row);
    }

    private void showHashVerifyDialog(File checksumFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.verify_progress));
        new Thread(() -> {
            try {
                List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(checksumFile);
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_hash_verify, null);
                    LinearLayout container = view.findViewById(R.id.verifyContainer);
                    if (results.isEmpty()) {
                        TextView empty = new TextView(context);
                        empty.setText(context.getString(R.string.checksum_verify_empty));
                        empty.setPadding(0, dp(8), 0, dp(8));
                        container.addView(empty);
                    } else {
                        for (HashUtil.CheckResult r : results) addVerifyRow(container, r);
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(context.getString(R.string.verify_checksum_title, checksumFile.getName()))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showCompareHashesDialog(File file1, File file2) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.computing_checksums));
        new Thread(() -> {
            try {
                Map<String, String> h1 = HashUtil.hashAll(file1);
                Map<String, String> h2 = HashUtil.hashAll(file2);
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_hash_verify, null);
                    LinearLayout container = view.findViewById(R.id.verifyContainer);
                    for (String algo : HashUtil.ALGORITHMS) {
                        String a = h1.get(algo);
                        String b = h2.get(algo);
                        boolean same = a != null && a.equals(b);
                        int color = same ? Color.rgb(0x4C, 0xAF, 0x50) : Color.rgb(0xF4, 0x43, 0x36);
                        LinearLayout row = new LinearLayout(context);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.TOP);
                        row.setPadding(0, dp(6), 0, dp(6));

                        ImageView icon = new ImageView(context);
                        icon.setImageResource(same ? R.drawable.baseline_check_circle_24 : R.drawable.baseline_remove_circle_24);
                        icon.setColorFilter(color);
                        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));

                        LinearLayout info = new LinearLayout(context);
                        info.setOrientation(LinearLayout.VERTICAL);
                        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        TextView algoView = new TextView(context);
                        algoView.setText(algo + " · " + (same ? context.getString(R.string.hash_equal) : context.getString(R.string.hash_not_equal)));
                        algoView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                        algoView.setTypeface(null, Typeface.BOLD);
                        algoView.setTextColor(color);
                        info.addView(algoView);

                        TextView hash1View = new TextView(context);
                        hash1View.setText(file1.getName() + "\n" + a);
                        hash1View.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                        hash1View.setTypeface(Typeface.MONOSPACE);
                        info.addView(hash1View);

                        TextView hash2View = new TextView(context);
                        hash2View.setText(file2.getName() + "\n" + b);
                        hash2View.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                        hash2View.setTypeface(Typeface.MONOSPACE);
                        info.addView(hash2View);

                        row.addView(icon);
                        row.addView(info);
                        container.addView(row);
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(context.getString(R.string.compare_hashes))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
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

    private void move(Object item) throws IOException {
        if (isMultiSelectMode && !selectedPositions.isEmpty()) {
            List<Object> itemsToMove = new ArrayList<>();
            for (int index : selectedPositions) {
                itemsToMove.add(values[index]);
            }
            if (isInZip) {
                copyFromZip(itemsToMove);
                for (Object o : itemsToMove) deleteZipEntry((ZipEntryInfo) o);
            } else {
                moveToDestination(itemsToMove);
            }
        } else if (isInZip) {
            copyToDestination(Collections.singletonList(item));
            deleteZipEntry((ZipEntryInfo) item);
        } else {
            moveToDestination(Collections.singletonList(item));
        }
    }

    private void moveToDestination(List<Object> items) throws IOException {
        File destinationFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter adapter = ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (adapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) adapter).uploadFiles(items);
            return;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) adapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
            for (Object item : items) {
                if (item instanceof File) ((File) item).delete();
                else if (item instanceof ZipEntryInfo) deleteZipEntry((ZipEntryInfo) item);
            }
            return;
        }
        for (Object item : items) {
            if (item instanceof File f) {
                File dest = FileUtils.getUnusedFile(destinationFolder, f.getName());
                if (f.renameTo(dest)) continue;
                if (f.isDirectory()) {
                    dest.mkdir();
                    FileUtils.copyFolder(f, dest);
                } else
                    FileUtils.copyFile(f, dest);
                f.delete();
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        context.handler.post(() -> context.loadFolderInPane(destinationFolder, !pane1));
    }

    private void copyToDestination(List<Object> items) throws IOException {
        File destinationFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter adapter = ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (adapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) adapter).uploadFiles(items);
            return;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) adapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            copyToRegularFolder(items, destinationFolder);
        }
    }

    private void copyToRegularFolder(List<Object> items, File destinationFolder) throws IOException {
        for (Object item : items) {
            if (item instanceof File f) {
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
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) ((RecyclerView) context
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
                rangeStartPosition = null;
            } else {
                selectedPositions.add(position);
                rangeStartPosition = position;
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

    private void extractArchive(File archive) {
        File parent = archive.getParentFile();
        String baseName = archive.getName();
        String folderName = baseName;
        if (baseName.endsWith(".tar.gz")) folderName = baseName.substring(0, baseName.length() - ".tar.gz".length());
        else if (baseName.endsWith(".tar.bz2")) folderName = baseName.substring(0, baseName.length() - ".tar.bz2".length());
        else if (baseName.endsWith(".tar.xz")) folderName = baseName.substring(0, baseName.length() - ".tar.xz".length());
        else folderName = baseName.substring(0, baseName.lastIndexOf('.'));
        File destDir = FileUtils.getUnusedFile(new File(parent, folderName));
        destDir.mkdirs();
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.rss.getString(R.string.extracting_to_folder, destDir.getName()));
        pm.show();
        new Thread(() -> {
            try {
                ArchiveUtil.extract(archive, destDir);
                pm.dismiss();
                context.handler.post(() -> context.loadFolderInPane(parent, pane1));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void handleZipEntryClick(ZipEntryInfo zipEntry) {
        File zipFile = zipEntry.getZipFile();
        String fullPath = zipEntry.getFullPath();
        if(zipEntry.isDirectory()) context.loadZipFolderInPane(zipFile, fullPath, pane1, false);
        else new Thread(() -> {
            try (ZipFile zf = new ZipFile(zipFile);
             InputStream is = zf.getInputStream(zf.getFileHeader(fullPath))) {
            final String name = zipEntry.getName();
            String outputDir = context.getCacheDir() + File.separator + UUID.randomUUID();
            File tempFolder = new File(outputDir);
            tempFolder.mkdir();
            File tempFile = new File(tempFolder, name);
            tempFile.createNewFile();
            if(name.endsWith(".dex")) {
                List<String> dexFiles = new ArrayList<>();
                    try {
                        FileHeader fh = zf.getFileHeader("classes.dex");
                        int i = 2;
                        while (fh != null) {
                            dexFiles.add(fh.getFileName());
                            //zf.extractFile(fh, outputDir);
                            fh = zf.getFileHeader("classes" + i + ".dex");
                            i++;
                        }
                        ProgressManager pm = new ProgressManager(context, false);
                        int finalI = i;
                        int size = dexFiles.size();
                        Thread t = new Thread(() -> {
                            try {
                                for (int j = 0; j < size; j++) {
                                    String df = dexFiles.get(j);
                                    if (pm.dialog != null && pm.dialog.isShowing()) {
                                        pm.setProgress(j, finalI);
                                        pm.setText(context.rss.getString(R.string.extracting, df));
                                    }
                                    zf.extractFile(zf.getFileHeader(df), outputDir);
                                }
                            } catch (ZipException e) {
                            throw new RuntimeException(e);
                        }});
                        t.start();
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                        builder.setTitle("MultiDex");
                        CharSequence[] fileNames = new String[size];
                        for (int j = 0; j < size; j++) fileNames[j] = dexFiles.get(j);

                        boolean[] selectedItems = new boolean[size];
                        String classesNo = name.replace("classes", "").replace(".dex", "");
                        try {
                            int initialIndex = TextUtils.isEmpty(classesNo) ? 0 : (Integer.parseInt(classesNo) - 1);
                            if (initialIndex != -1) selectedItems[initialIndex] = true;
                        } catch (NumberFormatException ignored) { }
                        builder.setMultiChoiceItems(fileNames, selectedItems, (dialog, which, isChecked) -> selectedItems[which] = isChecked);

                        builder.setNeutralButton(context.rss.getString(android.R.string.selectAll), null).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                           ArrayList<String> selectedPaths = new ArrayList<>();
                            for (int k = 0; k < selectedItems.length; k++) {
                                if (selectedItems[k]) selectedPaths.add(new File(tempFolder, dexFiles.get(k)).getPath());
                            }
                            if(t.isAlive()) {
                                pm.show();
                                try {
                                    t.join();
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            context.startActivityForResult(new Intent(context, DexEditorActivity.class)
                                        .putExtra("theme", context.theme)
                                        .putStringArrayListExtra("SelectedDexFiles", selectedPaths), 757);
                        });
                        builder.setNegativeButton(android.R.string.cancel, null);

                        context.handler.post(() -> {
                            AlertDialog dialog = builder.create();

                            dialog.setOnShowListener(dialogInterface -> {
                                Button invertButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                                invertButton.setOnClickListener(v -> {
                                    String buttonText = invertButton.getText().toString();

                                    if (buttonText.equals(context.rss.getString(android.R.string.selectAll))) {
                                        // First click: select all
                                        for (int i1 = 0; i1 < selectedItems.length; i1++) {
                                            selectedItems[i1] = true;
                                            dialog.getListView().setItemChecked(i1, true);
                                        }
                                        invertButton.setText("Invert Selection");
                                    } else {
                                        // Subsequent clicks: invert selection
                                        for (int i1 = 0; i1 < selectedItems.length; i1++) {
                                            selectedItems[i1] = !selectedItems[i1];
                                            dialog.getListView().setItemChecked(i1, selectedItems[i1]);
                                        }
                                    }
                                });
                            });
                            dialog.show();
                        });
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
            }
            else if(name.endsWith(".xml")) {
                boolean isAxml = FileUtils.isAxml(is);
                if(isAxml) try(InputStream rssStream = zf.getInputStream(zf.getFileHeader("resources.arsc")); InputStream is2 = zf.getInputStream(zf.getFileHeader(fullPath))) {
                    //ResourceTableParser rtp = new ResourceTableParser(rssStream);
                    //List<ResEntry> resEntries = rtp.parse();
                    File tmpRss = new File(context.getCacheDir(), System.currentTimeMillis() + name);
                    FileUtils.copyFile(rssStream, tmpRss);
                    FileUtils.copyFile(is2, tempFile);

                    context.startActivityForResult(new Intent(context, TextEditorActivity.class)
                        .putExtra("rssPath", tmpRss.getPath())
                        //.putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2, resEntries).decodeAsString())
                        //.putExtra("resEntries", (Serializable) resEntries)
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
        }).start();
    }

    public void clearSelection() {
        selectedPositions.clear();
        isMultiSelectMode = false;
        rangeStartPosition = null;
        notifyDataSetChanged();
    }

    public void selectAll() {
        isMultiSelectMode = true;
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) selectedPositions.add(i);
        notifyDataSetChanged();
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private void showApkInfoDialog(File file, String fileName) {
        String filePath = file.getPath();
        View display = LayoutInflater.from(context).inflate(R.layout.apk_display, null, false);
        ImageView apkIcon = display.findViewById(R.id.apkIcon);
        TextView apkTitle = display.findViewById(R.id.apkTitle);
        TextView apkVersionName = display.findViewById(R.id.apkVersionName);
        TextView verCode = display.findViewById(R.id.verCode);
        TextView pkgName = display.findViewById(R.id.pkgName);
        TextView signaturesInApk = display.findViewById(R.id.signaturesInApk);
        TextView protectedDisplay = display.findViewById(R.id.protectedDisplay);
        TextView fileSize = display.findViewById(R.id.fileSize);
        TextView apkTargetSdk = display.findViewById(R.id.apkTargetSdk);
        TextView apkMinSdk = display.findViewById(R.id.apkMinSdk);
        TextView apkCert = display.findViewById(R.id.apkCert);
        TextView apkInstalled = display.findViewById(R.id.apkInstalled);
        TextView apkPermissions = display.findViewById(R.id.apkPermissions);
        apkIcon.setImageDrawable(cachedApkIcon);
        apkTitle.setText(R.string.loading);
        apkVersionName.setText(R.string.loading);
        verCode.setText(R.string.loading);
        pkgName.setText(R.string.loading);
        signaturesInApk.setText(R.string.loading);
        protectedDisplay.setText(R.string.loading);

        AlertDialog ad = dialogUtil.getDialogBuilder()
                .setView(display)
                .setNeutralButton("More", (dialog, which) -> {
                    String[] items = new String[]{"Sign APK", "Optimize APK", "Decompile (REAndroid APKEditor)", "Refactor obfuscated resource names", "Protect (REAndroid APKEditor)", "Clone APK", context.getString(R.string.view_certificate)};
                    dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog12, which1) -> {
                        dialog12.dismiss();
                        if (which1 == 0) SignatureKeyDialog.show(context, file, false);
                        else if (which1 == 1) {
                            View ll = LayoutInflater.from(context).inflate(R.layout.dialog_opt, null);
                            final boolean[] sign = new boolean[1];
                            final boolean[] delFiles = new boolean[1];
                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                            CheckBox autosign = ll.findViewById(R.id.autosign);
                            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                            ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                            CheckBox deleteFiles = ll.findViewById(R.id.files_to_delete);
                            deleteFiles.setChecked(delFiles[0] = settings.getBoolean("delFiles", true));
                            deleteFiles.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("delFiles", delFiles[0] = isChecked).apply());
                            ll.findViewById(R.id.choose_files_delete).setOnClickListener(v8 -> {
                                Set<String> filesToDelete = settings.getStringSet("filesToDelete", null);
                                String[] filesFiDelete = (filesToDelete == null) ? new String[]{"assets/audience_network.dex", "androidsupportmultidexversion.txt", "DebugProbesKt.bin", "stamp-cert-sha256", "user-messaging-platform.properties", "transport-runtime.properties", "transport-backend-cct.properties", "transport-api.properties", "protolite-well-known-types.properties", "play-services-tasks.properties", "play-services-stats.properties", "play-services-measurement-sdk-api.properties", "play-services-measurement-sdk.properties", "play-services-measurement-impl.properties", "play-services-measurement-base.properties", "play-services-measurement-api.properties", "play-services-measurement.properties", "play-services-cloud-messaging.properties", "play-services-basement.properties", "play-services-base.properties", "play-services-appset.properties", "play-services-ads-lite.properties", "play-services-ads-identifier.properties", "play-services-ads-base.properties", "play-services-ads.properties", "firebase-abt.properties", "firebase-analytics-ktx.properties", "firebase-analytics.properties", "firebase-annotations.properties", "firebase-common-ktx.properties", "firebase-common.properties", "firebase-components.properties", "firebase-config-ktx.properties", "firebase-config.properties", "firebase-crashlytics-ktx.properties", "firebase-crashlytics.properties", "firebase-datatransport.properties", "firebase-encoders-json.properties", "firebase-encoders-proto.properties", "firebase-encoders.properties", "firebase-iid-interop.properties", "firebase-installations-interop.properties", "firebase-installations.properties", "firebase-measurement-connector.properties", "firebase-messaging-ktx.properties", "firebase-messaging.properties", "firebase-perf-ktx.properties", "firebase-perf.properties"}
                                        : filesToDelete.toArray(new String[0]);
                                List<String> filesFiDel = new ArrayList<>(Arrays.asList(filesFiDelete));
                                ListView listView = new ListView(context);
                                ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, R.layout.item_bottom_bar_config, filesFiDel) {
                                    @NonNull
                                    @Override
                                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                        if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.item_bottom_bar_config, parent, false);
                                        TextView textLabel = convertView.findViewById(R.id.text_label);
                                        ImageButton btnEdit = convertView.findViewById(R.id.btn_edit);
                                        ImageButton btnDelete = convertView.findViewById(R.id.btn_delete);
                                        textLabel.setText(filesFiDel.get(position));
                                        btnEdit.setVisibility(View.GONE);
                                        btnDelete.setOnClickListener(v -> { filesFiDel.remove(position); notifyDataSetChanged(); });
                                        return convertView;
                                    }
                                };
                                listView.setAdapter(adapter);
                                dialogUtil.getDialogBuilder()
                                        .setNegativeButton(context.rss.getString(R.string.cancel), null)
                                        .setNeutralButton(context.rss.getString(R.string.add), (dialog9, which6) -> {
                                            EditText et = new EditText(context);
                                            dialogUtil.getDialogBuilder().setView(et).setNegativeButton(context.rss.getString(R.string.cancel), null)
                                                    .setPositiveButton(context.rss.getString(io.github.rosemoe.sora.R.string.sora_editor_next), (dialog8, which5) -> {
                                                        filesFiDel.add(et.getText().toString());
                                                        adapter.notifyDataSetChanged();
                                                    }).show();
                                        })
                                        .setPositiveButton(context.rss.getString(io.github.rosemoe.sora.R.string.sora_editor_next), (dialog8, which5) -> settings.edit().putStringSet("filesToDelete", new HashSet<>(filesFiDel)).apply())
                                        .setView(listView)
                                        .show();
                            });
                                     dialogUtil.getDialogBuilder().setView(ll)
                                            .setNegativeButton(context.rss.getString(R.string.cancel), null)
                                            .setPositiveButton(context.rss.getString(R.string.opt), (dialog7, which4) -> {
                                                SignWrapper[] wrapper = new SignWrapper[1];
                                                Runnable doOpt = () -> {
                                                    ProgressManager pm = new ProgressManager(context, true).show();
                                                    APKLogger logger = pm.getLogger();
                                                    new Thread(() -> {
                                                        try {
                                                            File opt = ApkOptimizer.optimize(context, file, delFiles[0], settings, logger);
                                                            if (sign[0]) wrapper[0].signApk(opt);
                                                            pm.dismiss();
                                                            context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    }).start();
                                                };
                                                if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                                    wrapper[0] = sw;
                                                    doOpt.run();
                                                }); else doOpt.run();
                                            }).show();
                        } else if (which1 == 2) showDecompileOptionsDialog(file, fileName);
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
                            cleanMetaSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("cleanMeta", isChecked2).apply());
                            MaterialSwitch fixTypesSwitch = new MaterialSwitch(context);
                            fixTypesSwitch.setText(R.string.fix_types);
                            fixTypesSwitch.setChecked(fixTypes);
                            fixTypesSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("fixTypes", isChecked2).apply());
                            MaterialSwitch forceSwitch = new MaterialSwitch(context);
                            forceSwitch.setText(context.rss.getString(R.string.force_delete_output_path));
                            forceSwitch.setChecked(forceDeleteOutputPath);
                            forceSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("flagForce", isChecked2).apply());
                            layout.addView(publicXmlInputView);
                            layout.addView(cleanMetaSwitch);
                            layout.addView(fixTypesSwitch);
                            layout.addView(forceSwitch);
                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton("Refactor", (dialog2, which3) -> {
                                        options.cleanMeta = settings.getBoolean("cleanMeta", true);
                                        options.fixTypeNames = settings.getBoolean("fixTypes", true);
                                        options.force = settings.getBoolean("flagForce", false);
                                        ProgressManager pm = new ProgressManager(context, true).show();
                                        APKLogger logger = pm.getLogger();
                                        new Thread(() -> {
                                            try {
                                                String pXmlFilePath = publicXmlPath[0];
                                                if (!TextUtils.isEmpty(pXmlFilePath)) options.publicXml = new File(pXmlFilePath);
                                                options.newCommandExecutor(logger).runCommand();
                                                logger.close();
                                                pm.dismiss();
                                                context.handler.post(() -> Toast.makeText(context, "Refactored", Toast.LENGTH_SHORT).show());
                                            } catch (Exception e) {
                                                pm.dismiss();
                                                context.handler.post(() -> new ErrorUtil(context).showError(e));
                                                logger.close();
                                            }
                                        }).start();
                                    }).create());
                        } else if (which1 == 4) {
                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                            boolean skipManifest = settings.getBoolean("skipManifest", false);
                            boolean confuseZip = settings.getBoolean("confuseZip", false);
                            int dexLevel = settings.getInt("dexLevel", 0);
                            boolean flagForce = settings.getBoolean("flagForce", false);
                            ProtectorOptions options = new ProtectorOptions();
                            options.inputFile = file;
                            LinearLayout layout = new LinearLayout(context);
                            layout.setOrientation(LinearLayout.VERTICAL);
                            LayoutInflater layoutInflater = LayoutInflater.from(context);
                            View skipManifestView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                            TextView skipManifestTitle = skipManifestView.findViewById(R.id.title);
                            CheckBox skipManifestSwtch = skipManifestView.findViewById(R.id.switch_view);
                            skipManifestTitle.setText(context.rss.getString(R.string.skip_manifest_protection));
                            skipManifestSwtch.setChecked(skipManifest);
                            skipManifestSwtch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("skipManifest", isChecked).apply());
                            skipManifestView.setOnClickListener(v2 -> skipManifestSwtch.toggle());
                            View confuseZipView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                            TextView confuseZipTitle = confuseZipView.findViewById(R.id.title);
                            CheckBox confuseZipSwtch = confuseZipView.findViewById(R.id.switch_view);
                            confuseZipTitle.setText(context.rss.getString(R.string.confuse_zip_structure));
                            confuseZipSwtch.setChecked(confuseZip);
                            confuseZipSwtch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("confuseZip", isChecked).apply());
                            confuseZipView.setOnClickListener(v2 -> confuseZipSwtch.toggle());
                            View dexLevelView = layoutInflater.inflate(R.layout.item_edit_number, layout, false);
                            TextView dexLevelTitle = dexLevelView.findViewById(R.id.title);
                            EditText dexLevelInput = dexLevelView.findViewById(R.id.edit_text);
                            dexLevelTitle.setText(context.rss.getString(R.string.dex_protection_level));
                            dexLevelInput.setText(String.valueOf(dexLevel));
                            dexLevelInput.addTextChangedListener(new TextWatcher() {
                                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                                @Override public void afterTextChanged(Editable s) {
                                    try {
                                        settings.edit().putInt("dexLevel", Integer.parseInt(s.toString())).apply();
                                    } catch (Exception ignored) {}
                                }
                            });
                            View forceView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                            TextView forceTitle = forceView.findViewById(R.id.title);
                            CheckBox forceSwitch = forceView.findViewById(R.id.switch_view);
                            forceTitle.setText(context.rss.getString(R.string.force_delete_output_path));
                            forceSwitch.setChecked(flagForce);
                            forceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagForce", isChecked).apply());
                            forceView.setOnClickListener(v2 -> forceSwitch.toggle());
                            layout.addView(skipManifestView);
                            layout.addView(confuseZipView);
                            layout.addView(dexLevelView);
                            layout.addView(forceView);
                            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton("Protect", (dialog2, which3) -> {
                                        options.skipManifest = settings.getBoolean("skipManifest", false);
                                        options.confuse_zip = settings.getBoolean("confuseZip", false);
                                        options.dexLevel = settings.getInt("dexLevel", 0);
                                        options.force = settings.getBoolean("flagForce", false);
                                        options.outputFile = options.generateOutputFromInput(file);
                                        ProgressManager pm = new ProgressManager(context, true).show();
                                        APKLogger logger = pm.getLogger();
                                        new Thread(() -> {
                                            try {
                                                options.newCommandExecutor(logger).runCommand();
                                                logger.close();
                                                pm.dismiss();
                                                context.handler.post(() -> { dialog2.dismiss(); Toast.makeText(context, context.rss.getString(R.string.protectd), Toast.LENGTH_SHORT).show(); });
                                            } catch (Exception e) {
                                                pm.dismiss();
                                                context.handler.post(dialog2::dismiss);
                                                new ErrorUtil(context).showError(e);
                                                logger.close();
                                            }
                                        }).start();
                                    }).create());
                        } else if (which1 == 5) {
                            View ll = LayoutInflater.from(context).inflate(R.layout.dialog_clone, null);
                            TextView pkgNameView = ll.findViewById(R.id.package_name_input);
                            String pkgNameFromApk = getPackageNameFromApk(filePath);
                            pkgNameView.setText(ApkCloner.changeEndCharacter(pkgNameFromApk));
                            final boolean[] sign = new boolean[1];
                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                            CheckBox autosign = ll.findViewById(R.id.autosign);
                            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                            ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                            dialogUtil.getDialogBuilder().setView(ll)
                                    .setNegativeButton(context.rss.getString(R.string.cancel), null)
                                    .setPositiveButton(context.rss.getString(R.string.clone), (dialog6, which2) -> {
                                        SignWrapper[] wrapper = new SignWrapper[1];
                                        Runnable doClone = () -> {
                                            ProgressManager pm = new ProgressManager(context, false).show();
                                            APKLogger logger = pm.getLogger();
                                            new Thread(() -> {
                                                ApkCloner apkCloner = new ApkCloner(context, new ApkCloner.ApkClonerCallBack() {
                                                @Override public void onMessage(String msg) { logger.logMessage(msg); }
                                                @Override public void onProgress(int progress, int total) { pm.setProgress(progress, total); }
                                            });
                                                String pkgNameInput = pkgNameView.getText().toString();
                                                apkCloner.setPath(filePath, pkgNameFromApk, pkgNameInput);
                                                try {
                                                    apkCloner.processApk();
                                                    if (sign[0]) wrapper[0].signApk(new File(filePath.replace(".apk", "_clone.apk")));
                                                    pm.dismiss();
                                                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                                } catch (Exception e) { pm.dismiss(); new ErrorUtil(context).showError(e); }
                                            }).start();
                                        };
                                        if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                            wrapper[0] = sw;
                                            doClone.run();
                                        }); else doClone.run();
                                    }).show();
                        } else if (which1 == 6) showCertificateDialog(file);
                         /*else if (which1 == 6) {
                            ProgressManager pm = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    try (ZipFile zf = new ZipFile(file)){
                                        zf.extractFile("classes.dex", "/sdcard/MP Manager");
                                    }
                                    com.dex2c.cli.Main.main(new String[]{"-a", filePath, "-o", filePath.replace(".apk", "_dex2c.apk")});

                                    List<String> cmd = new ArrayList<>();
                                    cmd.add("/storage/emulated/0/MP Manager/android-ndk-r29/ndk-build");
                                    cmd.add("-C");
                                    cmd.add(context.getFilesDir().getPath());

                                    ProcessBuilder pb = new ProcessBuilder(cmd);
                                    pb.directory(new File("/sdcard/MP Manager/dataDir"));
                                    pb.redirectErrorStream(true);
                                    Process process = pb.start();
                                    try (InputStream is = process.getInputStream();
                                            InputStreamReader isr = new InputStreamReader(is);
                                            BufferedReader reader = new BufferedReader(isr)) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            String finalLine = line;
                                            context.handler.post(() -> pm.setText(finalLine));
                                        }
                                    }
                                    pm.dismiss();
                                    Extensions.showMessage(context, "Dex2C Done");
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        }*/
                    }).show();
                })
                .setPositiveButton("Install", (dialog, which) -> InstallUtil.installApk(context, file))
                .setNegativeButton("View", (dialog, which) -> openZipFile(file, null))
                .create();
        dialogUtil.styleAlertDialog(ad);
        display.findViewById(R.id.quickEdit).setOnClickListener(v7 -> {
            ad.dismiss();
            showEditManifestDialog(file);
        });
        ad.show();

        new Thread(() -> {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo packageInfo = pm.getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
                final ApplicationInfo appInfo;
                if (packageInfo == null || (appInfo = packageInfo.applicationInfo) == null) {
                    context.handler.post(() -> {
                        ad.dismiss();
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, context.getContentResolver().getType(uri))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                    });
                    return;
                }
                if (TextUtils.isEmpty(appInfo.sourceDir) || TextUtils.isEmpty(appInfo.publicSourceDir)) {
                    appInfo.sourceDir = filePath;
                    appInfo.publicSourceDir = filePath;
                }
                Drawable icon = appInfo.loadIcon(pm);
                String label = appInfo.loadLabel(pm).toString();
                String verName = packageInfo.versionName;
                int vCode = packageInfo.versionCode;
                String pkg = packageInfo.packageName;

                StringBuilder sigs = new StringBuilder();
                final String[] certFp = {""};
                try {
                    ApkVerifier.Result result = new ApkVerifier.Builder(file).build().verify();
                    try {
                        List<X509Certificate> certs = result.getSignerCertificates();
                        if (certs != null && !certs.isEmpty()) certFp[0] = CertUtil.getSha256(certs.get(0));
                    } catch (Exception ignored) {}
                    boolean verified = result.isVerified();
                    boolean v1 = result.isVerifiedUsingV1Scheme();
                    boolean v2 = result.isVerifiedUsingV2Scheme();
                    boolean v3 = result.isVerifiedUsingV3Scheme();
                    boolean v31 = result.isVerifiedUsingV31Scheme();
                    boolean v4 = result.isVerifiedUsingV4Scheme();
                    if (v1) sigs.append("V1");
                    if (v2) { if (v1) sigs.append(" + "); sigs.append("V2"); }
                    if (v3 || v31) { if (v1 || v2) sigs.append(" + "); sigs.append("V3"); }
                    if (v4) { if (v1 || v2 || v3 || v31) sigs.append(" + "); sigs.append("V4"); }
                    if (verified && !TextUtils.isEmpty(sigs)) { /* use sigs */ }
                    else {
                        sigs.setLength(0);
                        try (ArchiveFile af = new ArchiveFile(file)) {
                            sigs.append(af.getEntrySource("META-INF/MANIFEST.MF") == null ? "Not signed" : "Verification failed");
                        }
                    }
                } catch (Exception e) {
                    sigs.append(context.rss.getString(R.string.unknown));
                }
                String signatureStr = sigs.toString();

                String protectedStr;
                try (ArchiveFile af = new ArchiveFile(file); ApkModule am = new ApkModule(af.createZipEntryMap())) {
                    String aProtected = Util.isProtected(am);
                    protectedStr = TextUtils.isEmpty(aProtected) ? "Not found" : aProtected;
                } catch (Exception e) {
                    protectedStr = context.rss.getString(R.string.unknown);
                }

                String finalProtectedStr = protectedStr;
                context.handler.post(() -> {
                    apkIcon.setImageDrawable(icon);
                    apkTitle.setText(label);
                    apkVersionName.setText(verName);
                    verCode.setText(Integer.toString(vCode));
                    pkgName.setText(pkg);
                    uiHelper.scrollTextView(pkgName);
                    signaturesInApk.setText(signatureStr);
                    protectedDisplay.setText(finalProtectedStr);
                    fileSize.setText(Formatter.formatFileSize(context, file.length()) + " · " + ApkInfoUtil.getEntryCount(file) + " entries");
                    apkTargetSdk.setText(String.valueOf(packageInfo.applicationInfo.targetSdkVersion));
                    int min = ApkInfoUtil.getMinSdk(packageInfo);
                    apkMinSdk.setText(min < 0 ? context.getString(R.string.unknown_sdk) : String.valueOf(min));
                    apkCert.setText(TextUtils.isEmpty(certFp[0]) ? context.getString(R.string.no_signature_found) : certFp[0]);
                    String installedVer = ApkInfoUtil.getInstalledVersion(context, pkg);
                    if (installedVer == null) apkInstalled.setText(context.getString(R.string.not_installed));
                    else {
                        String installedText = installedVer;
                        if (ApkInfoUtil.isDowngrade(context, packageInfo)) installedText += " (" + context.getString(R.string.downgrade) + ")";
                        apkInstalled.setText(installedText);
                    }
                    String perms = ApkInfoUtil.getPermissions(packageInfo);
                    apkPermissions.setText(TextUtils.isEmpty(perms) ? context.getString(R.string.permissions_none) : perms);
                });
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
            View.OnLongClickListener lcl = v -> {
                if(v instanceof TextView tv) CopyUtil.copyToClipboard(context, tv.getText());
                return false;
            };
            signaturesInApk.setOnLongClickListener(lcl);
            protectedDisplay.setOnLongClickListener(lcl);
            pkgName.setOnLongClickListener(lcl);
            apkTitle.setOnLongClickListener(lcl);
            apkVersionName.setOnLongClickListener(lcl);
            verCode.setOnLongClickListener(lcl);
            fileSize.setOnLongClickListener(lcl);
            apkTargetSdk.setOnLongClickListener(lcl);
            apkMinSdk.setOnLongClickListener(lcl);
            apkCert.setOnLongClickListener(lcl);
            apkInstalled.setOnLongClickListener(lcl);
            apkPermissions.setOnLongClickListener(lcl);
        }).start();
    }

    private String getPackageNameFromApk(String filePath) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
            if (pi != null && pi.applicationInfo != null) return pi.applicationInfo.packageName;
        } catch (Exception ignored) {}
        return "";
    }

    private void showCertificateDialog(File apkFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                List<X509Certificate> certs = CertUtil.getCertificates(apkFile);
                CharSequence text;
                if (certs == null || certs.isEmpty()) text = context.getString(R.string.no_signature_found);
                else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < certs.size(); i++) {
                        if (i > 0) sb.append("\n\n");
                        sb.append(context.getString(R.string.cert, i + 1)).append('\n');
                        sb.append(CertUtil.describe(certs.get(i)));
                    }
                    text = sb;
                }
                pm.dismiss();
                context.handler.post(() -> dialogUtil.getDialogBuilder()
                        .setTitle(R.string.view_certificate)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, text))
                        .show());
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showCompareApksDialog(File f1, File f2) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                String report = ApkCompareUtil.compare(context, f1, f2);
                pm.dismiss();
                context.handler.post(() -> {
                    TextView tv = new TextView(context);
                    tv.setText(report);
                    tv.setTextIsSelectable(true);
                    tv.setTextSize(13);
                    tv.setTypeface(Typeface.MONOSPACE);
                    int pad = dp(16);
                    tv.setPadding(pad, pad, pad, pad);
                    ScrollView scroll = new ScrollView(context);
                    scroll.addView(tv);
                    dialogUtil.getDialogBuilder()
                            .setTitle(R.string.compare_apks)
                            .setView(scroll)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, report))
                            .show();
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void batchSignApks(List<File> apks) {
        SignWrapper.requireAuth(context, sw -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    for (int i = 0; i < apks.size(); i++) {
                        File apk = apks.get(i);
                        String msg = context.rss.getString(R.string.signing, apk.getName());
                        pm.setText(msg);
                        logger.logMessage(msg);
                        sw.signApk(apk);
                    }
                    pm.dismiss();
                    context.handler.post(() -> {
                        Toast.makeText(context, context.rss.getString(R.string.signed, apks.size() + " APKs"), Toast.LENGTH_SHORT).show();
                        context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        });
    }

    private void batchOptimizeApks(List<File> apks) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean sign = settings.getBoolean("autosign", true);
        boolean delFiles = settings.getBoolean("delFiles", true);
        if (sign) SignWrapper.requireAuth(context, sw -> runBatchOptimize(apks, delFiles, sw, settings));
        else runBatchOptimize(apks, delFiles, null, settings);
    }

    private void runBatchOptimize(List<File> apks, boolean delFiles, SignWrapper wrapper, SharedPreferences settings) {
        ProgressManager pm = new ProgressManager(context, true).show();
        APKLogger logger = pm.getLogger();
        new Thread(() -> {
            try {
                for (int i = 0; i < apks.size(); i++) {
                    File apk = apks.get(i);
                    String msg = context.rss.getString(R.string.optimizing, apk.getName());
                    pm.setText(msg);
                    logger.logMessage(msg);
                    File opt = ApkOptimizer.optimize(context, apk, delFiles, settings, logger);
                    if (wrapper != null) wrapper.signApk(opt);
                }
                pm.dismiss();
                context.handler.post(() -> {
                    Toast.makeText(context, context.rss.getString(R.string.opt_done), Toast.LENGTH_SHORT).show();
                    context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showCommandHelperDialog(ArrayList<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return;
        ProfileManager chPm = new ProfileManager(context);
        List<Profile> profiles = chPm.getProfiles();
        if (profiles.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.no_profiles)
                    .setMessage(R.string.no_profiles_msg)
                    .setPositiveButton(R.string.settings, (d, w) -> new CommandHelperSettingsDialog().show(context.getSupportFragmentManager(), "CommandHelperSettings"))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (filePaths.size() > 1) showMultiFileCommandDialog(profiles, filePaths, chPm);
        else buildAndShowCommandDialog(profiles, filePaths, true, chPm);
    }

    private void showMultiFileCommandDialog(List<Profile> profiles, ArrayList<String> filePaths, ProfileManager chPm) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.multiple_files);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, pad);
        MaterialCheckBox sameProfileCb = new MaterialCheckBox(context);
        sameProfileCb.setText(R.string.use_same_profile);
        sameProfileCb.setChecked(true);
        layout.addView(sameProfileCb);
        builder.setView(layout);
        builder.setPositiveButton(android.R.string.copy, (d, w) -> buildAndShowCommandDialog(profiles, filePaths, sameProfileCb.isChecked(), chPm));
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private static class CmdViewHolder {
        LinearLayout card;
        TextView fileLabel;
        Spinner spinner;
        TextView cmdText;
        MaterialButton copyBtn;
        MaterialButton termuxBtn;
        String filePath;
        boolean hasSpinner;
    }

    private void buildAndShowCommandDialog(List<Profile> profiles, ArrayList<String> filePaths, boolean sameProfile, ProfileManager chPm) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoCopy = prefs.getBoolean("auto_copy", false);
        int lastProfileIdx = Math.min(prefs.getInt("last_profile_idx", 0), profiles.size() - 1);
        if (lastProfileIdx < 0) lastProfileIdx = 0;
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.command_helper);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        boolean multipleFiles = filePaths.size() > 1;
        ArrayList<Profile> mutableProfiles = new ArrayList<>(profiles);
        final Spinner[] sharedSpinnerRef = new Spinner[1];
        if (sameProfile && multipleFiles) {
            sharedSpinnerRef[0] = createProfileSpinner(mutableProfiles);
            sharedSpinnerRef[0].setSelection(lastProfileIdx);
            root.addView(sharedSpinnerRef[0]);
            root.addView(spacer(dp(8)));
        }
        List<CmdViewHolder> holders = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) {
            String fp = filePaths.get(i);
            CmdViewHolder h = new CmdViewHolder();
            h.filePath = fp;
            h.card = new LinearLayout(context);
            h.card.setOrientation(LinearLayout.VERTICAL);
            h.card.setPadding(0, dp(4), 0, dp(4));
            if (multipleFiles) {
                h.fileLabel = new TextView(context);
                h.fileLabel.setText((i + 1) + ". " + new File(fp).getName());
                h.fileLabel.setTextAppearance(context, android.R.style.TextAppearance_Small);
                h.card.addView(h.fileLabel);
            }
            boolean showIndividualSpinner = !sameProfile || !multipleFiles;
            if (showIndividualSpinner) {
                h.spinner = createProfileSpinner(mutableProfiles);
                h.spinner.setSelection(lastProfileIdx);
                h.hasSpinner = true;
                h.card.addView(h.spinner);
            }
            h.cmdText = new TextView(context);
            h.cmdText.setTextAppearance(context, android.R.style.TextAppearance_Medium);
            h.cmdText.setTypeface(Typeface.MONOSPACE);
            h.cmdText.setPadding(0, dp(4), 0, dp(4));
            h.cmdText.setTextIsSelectable(true);
            h.card.addView(h.cmdText);
            LinearLayout btnRow = new LinearLayout(context);
            btnRow.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            h.copyBtn = new MaterialButton(context);
            h.copyBtn.setText(multipleFiles ? ("Copy #" + (i + 1)) : "Copy");
            h.copyBtn.setWidth(0);
            h.copyBtn.setPadding(5, 0, 5, 0);
            h.copyBtn.setIconResource(R.drawable.ic_copy);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            h.copyBtn.setLayoutParams(lp);
            h.copyBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btnRow.addView(h.copyBtn);

            h.termuxBtn = new MaterialButton(context);
            h.termuxBtn.setText("Termux");
            h.termuxBtn.setWidth(0);
            h.termuxBtn.setPadding(5, 0, 5, 0);
            h.termuxBtn.setLayoutParams(lp);
            h.termuxBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            h.termuxBtn.setIconResource(R.drawable.terminal_24px);
            btnRow.addView(h.termuxBtn);
            h.card.addView(btnRow);
            root.addView(h.card);
            if (i < filePaths.size() - 1) root.addView(divider());
            holders.add(h);
        }
        if (multipleFiles) {
            root.addView(spacer(dp(8)));
            LinearLayout multiBtnRow = new LinearLayout(context);
            multiBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton copyAllBtn = new MaterialButton(context);
            copyAllBtn.setText(R.string.copy_all);
            multiBtnRow.addView(copyAllBtn);
            MaterialButton termuxAllBtn = new MaterialButton(context);
            termuxAllBtn.setText("Run All in Termux");
            multiBtnRow.addView(termuxAllBtn);
            root.addView(multiBtnRow);
            copyAllBtn.setOnClickListener(v -> {
                StringBuilder sb = new StringBuilder();
                for (CmdViewHolder hldr : holders) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(hldr.cmdText.getText());
                }
                CopyUtil.copyToClipboard(context, sb);
            });
            termuxAllBtn.setOnClickListener(v -> {
                for (CmdViewHolder hldr : holders) {
                    String cmd = hldr.cmdText.getText().toString();
                    if (!cmd.isEmpty()) runInTermux(cmd);
                }
            });
        }
        root.addView(spacer(dp(8)));
        MaterialButton manageBtn = new MaterialButton(context);
        manageBtn.setText(R.string.manage_profiles);
        root.addView(manageBtn);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        builder.setView(scroll);
        builder.setNegativeButton(R.string.close, null);
        AlertDialog mainDialog = builder.create();
        mainDialog.show();

        Runnable refreshCallback = () -> {
            List<Profile> freshProfiles = chPm.getProfiles();
            mutableProfiles.clear();
            mutableProfiles.addAll(freshProfiles);
            for (CmdViewHolder h : holders) {
                if (h.hasSpinner && h.spinner != null) {
                    ArrayAdapter<String> ad = (ArrayAdapter<String>) h.spinner.getAdapter();
                    ad.clear();
                    for (Profile pr : freshProfiles) ad.add(pr.name);
                    ad.notifyDataSetChanged();
                    int sel = h.spinner.getSelectedItemPosition();
                    if (sel >= freshProfiles.size()) h.spinner.setSelection(Math.max(0, freshProfiles.size() - 1));
                }
            }
            if (sharedSpinnerRef[0] != null) {
                ArrayAdapter<String> ad = (ArrayAdapter<String>) sharedSpinnerRef[0].getAdapter();
                ad.clear();
                for (Profile pr : freshProfiles) ad.add(pr.name);
                ad.notifyDataSetChanged();
                int sel = sharedSpinnerRef[0].getSelectedItemPosition();
                if (sel >= freshProfiles.size()) sharedSpinnerRef[0].setSelection(Math.max(0, freshProfiles.size() - 1));
            }
        };

        manageBtn.setOnClickListener(v -> {
            CommandHelperSettingsDialog settingsDialog = new CommandHelperSettingsDialog();
            settingsDialog.onProfilesChanged = refreshCallback;
            settingsDialog.show(context.getSupportFragmentManager(), "CommandHelperSettings");
        });

        for (int i = 0; i < holders.size(); i++) {
            CmdViewHolder h = holders.get(i);
            Profile initProfile = mutableProfiles.get(Math.min(lastProfileIdx, mutableProfiles.size() - 1));
            h.cmdText.setText(initProfile.getGeneratedCommand(h.filePath));
            if (h.hasSpinner) {
                h.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Profile p = mutableProfiles.get(position);
                        h.cmdText.setText(p.getGeneratedCommand(h.filePath));
                        prefs.edit().putInt("last_profile_idx", position).apply();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
            h.copyBtn.setOnClickListener(v -> CopyUtil.copyToClipboard(context, h.cmdText.getText()));
            h.termuxBtn.setOnClickListener(v -> {
                String cmd = h.cmdText.getText().toString();
                if (!cmd.isEmpty()) runInTermux(cmd);
            });
        }
        if (sharedSpinnerRef[0] != null) {
            sharedSpinnerRef[0].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Profile p = mutableProfiles.get(position);
                    for (CmdViewHolder hldr : holders) hldr.cmdText.setText(p.getGeneratedCommand(hldr.filePath));
                    prefs.edit().putInt("last_profile_idx", position).apply();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        if (autoCopy && !holders.isEmpty()) {
            CopyUtil.copyToClipboard(context, mutableProfiles.get(Math.min(lastProfileIdx, mutableProfiles.size() - 1)).getGeneratedCommand(holders.get(0).filePath));
        }
    }

    private Spinner createProfileSpinner(List<Profile> profiles) {
        Spinner spinner = new Spinner(context);
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) names.add(profiles.get(i).name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void runInTermux(String command) {
        try {
            context.getPackageManager().getPackageInfo("com.termux", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.command_helper)
                    .setMessage("Warning: Termux was not found on the device")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Download Termux Now", (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")))).show();
            return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command + "; exec bash"});
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            context.startService(intent);
        } catch (SecurityException e) {
            new MaterialAlertDialogBuilder(context).setMessage("Note: You need to grant permission for MP Manager to be able to send command to Termux\n(Permissions > Additional Permissions > Run commands in Termux environment)").setTitle("Permissions").setPositiveButton(android.R.string.ok, (dialog, which) -> context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())))).setNegativeButton(android.R.string.cancel, null).show();
        } catch (IllegalStateException ise) {
            // This can happen if Termux is force stopped even if you granted draw over other apps permission
            context.startActivity(new Intent().setClassName("com.termux", "com.termux.app.TermuxActivity"));
            context.handler.postDelayed(() -> context.startService(intent), 2000);
        }
    }

    private View spacer(int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private View divider() {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(0x1A000000);
        v.setMinimumHeight(1);
        return v;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static class CommandHelperSettingsDialog extends DialogFragment {
        private ProfileManager profileManager;
        private List<Profile> profiles;
        private ArrayAdapter<String> listAdapter;
        private ListView profileListView;
        public Runnable onProfilesChanged;

        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            if (onProfilesChanged != null) onProfilesChanged.run();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            profileManager = new ProfileManager(getActivity());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            LinearLayout root = new LinearLayout(getActivity());
            root.setOrientation(LinearLayout.VERTICAL);

            MaterialCheckBox autoCopyCheck = new MaterialCheckBox(getActivity());
            autoCopyCheck.setText(R.string.auto_copy);
            autoCopyCheck.setChecked(prefs.getBoolean("auto_copy", false));
            autoCopyCheck.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("auto_copy", isChecked).apply());
            root.addView(autoCopyCheck);

            TextView autoCopyDesc = new TextView(getActivity());
            autoCopyDesc.setText(R.string.auto_copy_desc);
            autoCopyDesc.setTextAppearance(getActivity(), android.R.style.TextAppearance_Small);
            autoCopyDesc.setPadding(dp2(32), 0, 0, dp2(8));
            root.addView(autoCopyDesc);

            View sep = new View(getActivity());
            sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            sep.setBackgroundColor(0x1A000000);
            root.addView(sep);

            TextView profilesTitle = new TextView(getActivity());
            profilesTitle.setText(R.string.profiles);
            profilesTitle.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
            profilesTitle.setTypeface(null, Typeface.BOLD);
            profilesTitle.setPadding(dp2(16), dp2(16), dp2(16), dp2(8));
            root.addView(profilesTitle);

            loadProfiles();
            listAdapter = new ArrayAdapter<>(getActivity(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, getProfileNames()) {
                @Override
                public View getView(int pos, View convertView, ViewGroup parent) {
                    View view = super.getView(pos, convertView, parent);
                    if (view instanceof TextView tv) {
                        tv.setText(profiles.get(pos).name);
                        tv.setPadding(dp2(16), dp2(12), dp2(16), dp2(12));
                    }
                    return view;
                }
            };
            profileListView = new ListView(getActivity());
            profileListView.setAdapter(listAdapter);
            profileListView.setOnItemClickListener((parent, view, position, id) -> showEditDialog(position));
            profileListView.setOnItemLongClickListener((parent, view, position, id) -> {
                showDeleteDialog(position);
                return true;
            });
            root.addView(profileListView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            MaterialButton addBtn = new MaterialButton(getActivity());
            addBtn.setText(R.string.add_profile);
            addBtn.setOnClickListener(v -> showAddDialog());
            root.addView(addBtn);

            return new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.settings)
                    .setView(root)
                    .setPositiveButton(R.string.close, null)
                    .create();
        }

        private void loadProfiles() { profiles = profileManager.getProfiles(); }

        private List<String> getProfileNames() {
            List<String> names = new ArrayList<>();
            for (Profile p : profiles) names.add(p.name);
            return names;
        }

        private void refreshList() {
            loadProfiles();
            listAdapter.clear();
            for (String name : getProfileNames()) listAdapter.add(name);
            listAdapter.notifyDataSetChanged();
        }

        private void showAddDialog() {
            showProfileDialog(-1, null, null);
        }

        private void showEditDialog(int index) {
            Profile p = profiles.get(index);
            showProfileDialog(index, p.name, p.command);
        }

        private void showProfileDialog(int index, String existingName, String existingCommand) {
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle(index < 0 ? R.string.add_profile : R.string.edit_profile);
            LinearLayout layout = new LinearLayout(getActivity());
            layout.setOrientation(LinearLayout.VERTICAL);
            int p = dp2(16);
            layout.setPadding(p, p, p, p);
            TextInputLayout nameLayout = new TextInputLayout(getActivity());
            TextInputEditText nameInput = new TextInputEditText(getActivity());
            nameInput.setHint(R.string.profile_name);
            if (existingName != null) nameInput.setText(existingName);
            nameLayout.addView(nameInput);
            layout.addView(nameLayout);
            TextInputLayout cmdLayout = new TextInputLayout(getActivity());
            TextInputEditText cmdInput = new TextInputEditText(getActivity());
            cmdInput.setHint(R.string.command_template_hint);
            cmdInput.setSingleLine(false);
            cmdInput.setLines(3);
            if (existingCommand != null) cmdInput.setText(existingCommand);
            cmdLayout.addView(cmdInput);
            layout.addView(cmdLayout);
            MaterialButton placeholderHelpBtn = new MaterialButton(getActivity());
            placeholderHelpBtn.setText("Placeholder Help");
            placeholderHelpBtn.setIconResource(R.drawable.baseline_info_24);
            placeholderHelpBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            layout.addView(placeholderHelpBtn);
            placeholderHelpBtn.setOnClickListener(v -> {
                Context ctx = getContext();
                MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(ctx);
                dialogBuilder.setTitle("Command Template Placeholders");

                LinearLayout container = new LinearLayout(ctx);
                container.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (ctx.getResources().getDisplayMetrics().density * 12);
                container.setPadding(pad, pad, pad, pad);

                String[][] items = new String[][]{
                        {"%FPATH%", "%FPATH% - Full file path (shell-safe, quoted)"},
                        {"%FILE%",  "%FILE% - Full file path (raw)"},
                        {"%FNAME%", "%FNAME% - File name with extension"},
                        {"%NAME%",  "%NAME% - File name without extension"},
                        {"%EXT%",   "%EXT% - File extension only"},
                };

                for (String[] item : items) {
                    String token = item[0];
                    String desc = item[1];

                    LinearLayout row = new LinearLayout(ctx);
                    row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    TextView tv = new TextView(ctx);
                    LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    tv.setLayoutParams(tvLp);
                    tv.setText(desc);

                    MaterialButton copyBtn = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    copyBtn.setText(android.R.string.copy);
                    copyBtn.setOnClickListener(view -> {
                        ((android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("placeholder", token));
                        Extensions.showMessage((AppCompatActivity) getActivity(), getString(R.string.copied_to_clipboard, token));
                    });

                    row.addView(tv);
                    row.addView(copyBtn);
                    container.addView(row);
                }

                TextView example = new TextView(ctx);
                example.setText("Example: java -jar tool.jar -i %FPATH% -o %NAME%_modified.%EXT%");
                example.setPadding(0, pad, 0, 0);
                container.addView(example);

                dialogBuilder.setView(container);
                dialogBuilder.setPositiveButton(R.string.close, null);
                dialogBuilder.show();
            });

            builder.setView(layout);
            builder.setPositiveButton(R.string.save, (d, w) -> {
                String name = nameInput.getText().toString().trim();
                String cmd = cmdInput.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(getActivity(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(cmd)) {
                    Toast.makeText(getActivity(), "Command template cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (index < 0) profileManager.addProfile(new Profile(name, cmd));
                else profileManager.updateProfile(index, new Profile(name, cmd));
                refreshList();
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            if(index >= 0) builder.setNeutralButton(R.string.delete, (dialog, which) -> showDeleteDialog(index));
            builder.show();
        }

        private void showDeleteDialog(int index) {
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.delete_profile)
                    .setMessage(getContext().getString(R.string.delete_confirm, profiles.get(index).name))
                    .setPositiveButton(R.string.save, (d, w) -> {
                        profileManager.deleteProfile(index);
                        refreshList();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }

        private int dp2(int dp) {
            return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}