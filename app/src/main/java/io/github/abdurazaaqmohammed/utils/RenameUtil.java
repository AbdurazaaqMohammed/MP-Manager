package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class RenameUtil {

    private static class RenamePlan {
        final Object item;
        final String originalName;
        String newName;
        String error;
        boolean noop;

        RenamePlan(Object item, String originalName, String newName) {
            this.item = item;
            this.originalName = originalName;
            this.newName = newName;
        }
    }

    public static void showMultiRenameDialog(MainActivity context, Set<Integer> selectedPositions, boolean isInZip, Object[] values, boolean pane1, String currentZipPath) {
        List<Integer> sorted = new ArrayList<>(selectedPositions);
        Collections.sort(sorted);
        List<Object> items = new ArrayList<>();
        for (int i : sorted) {
            if (isInZip) {
                ZipEntryInfo ze = (ZipEntryInfo) values[i];
                if (ze.getFullPath() == null) continue; // ".." up folder
                items.add(ze);
            } else if (i != 0) items.add(values[i]); // position 0 is ".."
        }
        if (items.isEmpty()) {
            Extensions.showMessage(context, "No files selected");
            return;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_multi_rename, null, false);
        TextInputEditText patternInput = view.findViewById(R.id.rename_pattern_input);
        TextInputEditText findInput = view.findViewById(R.id.find_input);
        TextInputEditText replaceInput = view.findViewById(R.id.replace_input);
        MaterialCheckBox regexCheck = view.findViewById(R.id.regex_checkbox);
        MaterialCheckBox caseSensitiveCheck = view.findViewById(R.id.case_sensitive_checkbox);
        patternInput.setText("{P}{S}");

        int[] quickButtonIds = {R.id.btn_insert_prefix, R.id.btn_insert_suffix, R.id.btn_insert_number, R.id.btn_insert_padded_number};
        String[] quickButtonTokens = {"{P}", "{S}", "{0}", "{z0}"};
        for (int i = 0; i < quickButtonIds.length; i++) {
            String token = quickButtonTokens[i];
            view.findViewById(quickButtonIds[i]).setOnClickListener(v -> insertAtCursor(patternInput, token));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setTitle("Rename " + items.size() + " items")
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.preview, null)
                .setPositiveButton(R.string.rename, null);
        AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showRenamePreview(context, items,
                patternInput.getText().toString(), findInput.getText().toString(),
                replaceInput.getText().toString(), regexCheck.isChecked(), caseSensitiveCheck.isChecked(), pane1, currentZipPath));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> executeRename(context, buildRenamePlan(items,
                patternInput.getText().toString(), findInput.getText().toString(),
                replaceInput.getText().toString(), regexCheck.isChecked(), caseSensitiveCheck.isChecked()), pane1, currentZipPath));
    }

    private static void showRenamePreview(MainActivity context, List<Object> items, String pattern, String find, String replace, boolean regex, boolean caseSensitive, boolean pane1, String currentZipPath) {
        String validationError = validateFind(find, regex);
        if (validationError != null) {
            Toast.makeText(context, validationError, Toast.LENGTH_LONG).show();
            return;
        }
        List<RenamePlan> plans = buildRenamePlan(items, pattern, find, replace, regex, caseSensitive);
        resolveConflicts(plans);

        ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setAdapter(new RenamePreviewAdapter(context, plans));
        LinearLayout container = new LinearLayout(context);
        container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (420 * context.getResources().getDisplayMetrics().density + 0.5f)));
        container.addView(listView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new MaterialAlertDialogBuilder(context)
                .setTitle(("Rename preview"))
                .setView(container)
                .setNegativeButton("Close", null)
                .setPositiveButton("Rename", (d, w) -> executeRename(context, plans, pane1, currentZipPath)).show();
    }

    private static void executeRename(MainActivity context, List<RenamePlan> plans, boolean pane1, String currentZipPath) {
        resolveConflicts(plans);
        for (RenamePlan p : plans) {
            if (p.error != null) {
                Extensions.showMessage(context, "Some names are invalid, please fix them");
                return;
            }
        }
        boolean any = false;
        for (RenamePlan p : plans) if (!p.noop) any = true;
        if (!any) {
            Extensions.showMessage(context, "No files need renaming");
            return;
        }
        if (plans.get(0).item instanceof ZipEntryInfo) executeRenameZip(context, plans, currentZipPath, pane1);
        else executeRenameFiles(context, plans, pane1);
    }

    private static List<RenamePlan> buildRenamePlan(List<Object> items, String pattern, String find, String replace, boolean regex, boolean caseSensitive) {
        List<RenamePlan> plans = new ArrayList<>();
        int startNumber = extractStartNumber(pattern);
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            String original = item instanceof File ? ((File) item).getName() : ((ZipEntryInfo) item).getName();
            String processed = applyFindReplace(original, find, replace, regex, caseSensitive);
            String newName = applyRenameTemplate(pattern, processed, i, startNumber, items.size());
            plans.add(new RenamePlan(item, original, newName));
        }
        return plans;
    }

    private static void resolveConflicts(List<RenamePlan> plans) {
        Set<String> originalNames = new HashSet<>();
        File parent = null;
        for (RenamePlan p : plans) {
            originalNames.add(p.originalName);
            if (p.item instanceof File) parent = ((File) p.item).getParentFile();
        }
        Set<String> existing = new HashSet<>();
        File[] files = parent == null ? null : parent.listFiles();
        if (files != null) for (File f : files) existing.add(f.getName());
        Set<String> used = new HashSet<>();
        for (RenamePlan p : plans) {
            String name = p.newName;
            if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                    || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
                p.error = "Invalid file name";
                continue;
            }
            if (name.equals(p.originalName)) {
                p.noop = true;
                used.add(name);
                continue;
            }
            String candidate = name;
            int counter = 1;
            while (used.contains(candidate) || (existing.contains(candidate) && !originalNames.contains(candidate))) {
                candidate = name + " (" + counter + ")";
                counter++;
            }
            used.add(candidate);
            if (!candidate.equals(name)) p.newName = candidate;
        }
    }

    private static void executeRenameFiles(MainActivity context, List<RenamePlan> plans, boolean pane1) {
        File parent = ((File) plans.get(0).item).getParentFile();
        if (parent == null) {
            Toast.makeText(context, "Failed to rename", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressManager pm = new ProgressManager(context, true).show();
        Handler handler = context.handler;
        new Thread(() -> {
            Map<File, File> oldToTemp = new LinkedHashMap<>();
            Map<File, File> tempToOld = new HashMap<>();
            Map<File, File> tempToFinal = new HashMap<>();
            Map<File, File> finalToTemp = new HashMap<>();
            int t = 0;
            for (RenamePlan p : plans) {
                if (p.noop || p.error != null) continue;
                File temp;
                do {
                    temp = new File(parent, "__MP_RENAME_TMP_" + (t++) + "_");
                } while (temp.exists());
                oldToTemp.put((File) p.item, temp);
                tempToOld.put(temp, (File) p.item);
                tempToFinal.put(temp, new File(parent, p.newName));
                finalToTemp.put(new File(parent, p.newName), temp);
            }
            try {
                for (Map.Entry<File, File> e : oldToTemp.entrySet()) {
                    pm.setText("Renaming...");
                    if (!e.getKey().renameTo(e.getValue())) {
                        for (Map.Entry<File, File> r : tempToOld.entrySet()) r.getKey().renameTo(r.getValue());
                        throw new IOException("Failed to rename \"" + e.getKey().getName() + "\"");
                    }
                }
                for (Map.Entry<File, File> e : tempToFinal.entrySet()) {
                    pm.setText("Renaming...");
                    if (!e.getKey().renameTo(e.getValue())) {
                        for (Map.Entry<File, File> r : finalToTemp.entrySet()) r.getKey().renameTo(r.getValue());
                        for (Map.Entry<File, File> r : tempToOld.entrySet()) r.getKey().renameTo(r.getValue());
                        throw new IOException("Failed to rename \"" + e.getKey().getName() + "\"");
                    }
                }
                pm.dismiss();
                handler.post(() -> context.loadFolderInPane(parent, pane1, false));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private static void executeRenameZip(MainActivity context, List<RenamePlan> plans, String currentZipPath, boolean pane1) {
        File zipFile = ((ZipEntryInfo) plans.get(0).item).getZipFile();
        ProgressManager pm = new ProgressManager(context, true).show();
        Handler handler = context.handler;
        new Thread(() -> {
            try (ZipFile zf = new ZipFile(zipFile)) {
                Map<String, String> oldToTemp = new LinkedHashMap<>();
                Map<String, String> tempToFinal = new LinkedHashMap<>();
                int t = 0;
                for (RenamePlan p : plans) {
                    if (p.noop || p.error != null) continue;
                    ZipEntryInfo ze = (ZipEntryInfo) p.item;
                    String oldPath = ze.getFullPath();
                    String cleanPath = oldPath.replaceAll("/+$", "");
                    String parentPath = "";
                    int slash = cleanPath.lastIndexOf('/');
                    if (slash >= 0) parentPath = cleanPath.substring(0, slash + 1);
                    String tempBase = parentPath + "__MP_RENAME_TMP_" + (t++) + "_";
                    if (ze.isDirectory()) {
                        String dirEntry = oldPath.endsWith("/") ? oldPath : oldPath + "/";
                        oldToTemp.put(dirEntry, tempBase);
                        tempToFinal.put(tempBase, parentPath + p.newName + "/");
                        for (FileHeader fh : zf.getFileHeaders()) {
                            String fhName = fh.getFileName();
                            if (fhName.startsWith(dirEntry) && fhName.length() > dirEntry.length() && !oldToTemp.containsKey(fhName)) {
                                String rest = fhName.substring(dirEntry.length());
                                oldToTemp.put(fhName, tempBase + rest);
                                tempToFinal.put(tempBase + rest, parentPath + p.newName + "/" + rest);
                            }
                        }
                    } else if (!oldToTemp.containsKey(oldPath)) {
                        String tempName = tempBase + FilenameUtils.getBaseName(oldPath);
                        oldToTemp.put(oldPath, tempName);
                        tempToFinal.put(tempName, parentPath + p.newName);
                    }
                }
                if (!oldToTemp.isEmpty()) {
                    pm.setText("Renaming...");
                    zf.renameFiles(oldToTemp);
                    zf.renameFiles(tempToFinal);
                }
                pm.dismiss();
                handler.post(() -> context.loadZipFolderInPane(zipFile, currentZipPath, pane1, false));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private static String validateFind(String find, boolean regex) {
        if (regex && !TextUtils.isEmpty(find)) {
            try {
                Pattern.compile(find);
            } catch (Exception e) {
                return "Invalid regular expression: " + e.getMessage();
            }
        }
        return null;
    }

    private static String applyFindReplace(String name, String find, String replace, boolean regex, boolean caseSensitive) {
        if (find == null || find.isEmpty()) return name;
        String replacement = replace == null ? "" : replace;
        if (!regex) return name.replace(find, replacement);
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(find, flags).matcher(name).replaceAll(replacement);
    }

    private static String applyRenameTemplate(String pattern, String name, int index, int startNumber, int total) {
        if (pattern == null || pattern.isEmpty()) return name;
        String prefix = FilenameUtils.getBaseName(name);
        String ext = FilenameUtils.getExtension(name);
        String suffix = ext.isEmpty() ? "" : "." + ext;
        StringBuilder sb = new StringBuilder();
        int i = 0, len = pattern.length();
        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int close = pattern.indexOf('}', i + 1);
                if (close > i) {
                    String token = pattern.substring(i + 1, close);
                    if (token.equals("P")) {
                        sb.append(prefix);
                        i = close + 1;
                        continue;
                    }
                    if (token.equals("S")) {
                        sb.append(suffix);
                        i = close + 1;
                        continue;
                    }
                    if (token.startsWith("z") && token.length() > 1 && isDigits(token.substring(1))) {
                        int start = Integer.parseInt(token.substring(1));
                        int num = start + index;
                        int width = Math.max(2, String.valueOf(start + total - 1).length());
                        sb.append(String.format(Locale.US, "%0" + width + "d", num));
                        i = close + 1;
                        continue;
                    }
                    if (isDigits(token)) {
                        int start = Integer.parseInt(token);
                        sb.append(start + index);
                        i = close + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int extractStartNumber(String pattern) {
        if (pattern == null) return 0;
        Matcher m = Pattern.compile("\\{(z?)(\\d+)\\}").matcher(pattern);
        return m.find() ? Integer.parseInt(m.group(2)) : 0;
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static void insertAtCursor(EditText editText, String s) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        int min = Math.min(start, end), max = Math.max(start, end);
        editText.getText().replace(min, max, s);
        editText.setSelection(min + s.length());
    }

    private static class RenamePreviewAdapter extends ArrayAdapter<RenamePlan> {
        private final Context ctx;
        private final List<RenamePlan> plans;
        private final int defaultColor;

        RenamePreviewAdapter(Context context, List<RenamePlan> plans) {
            super(context, R.layout.item_rename_preview, plans);
            this.ctx = context;
            this.plans = plans;
            this.defaultColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(ctx).inflate(R.layout.item_rename_preview, parent, false);
            TextView oldView = convertView.findViewById(R.id.preview_old);
            TextView newView = convertView.findViewById(R.id.preview_new);
            RenamePlan p = plans.get(position);
            oldView.setText(p.originalName);
            if (p.error != null) {
                newView.setText("Error: " + p.error);
                newView.setTextColor(0xFFE53935);
            } else if (p.noop) {
                newView.setText(p.newName + "  (unchanged)");
                newView.setTextColor(defaultColor);
            } else {
                newView.setText("→ " + p.newName);
                newView.setTextColor(defaultColor);
            }
            return convertView;
        }
    }
}