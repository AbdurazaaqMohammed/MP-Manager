package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.apk.axml.serializableItems.XMLEntry;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * MT's "XML batch replace": one dialog that rewrites every XML inside the APK.
 *
 * <p>The "search type" picker decides which part of a decoded entry may be touched: {@code 代码}
 * walks tag names, attribute names and values, while the other four only consider a value whose
 * own shape matches (string literal, resource reference, colour or integer). "IDs to names"
 * controls whether references are decoded to {@code @string/foo} — the same {@link ResEntry} list
 * must then be handed back to the encoder so the name is resolved to its numeric id again.
 */
public final class XmlBatchReplace {

    private enum Kind { CODE, STRING, RES_ID, COLOR, INT }

    private static final String HISTORY_FIND = "xml_find_history";
    private static final String HISTORY_REPLACE = "xml_replace_history";
    private static final int HISTORY_LIMIT = 10;

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final File apkFile;

    private XmlBatchReplace(MainActivity context, DialogUtil dialogUtil, File apkFile) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.apkFile = apkFile;
    }

    public static void show(MainActivity context, DialogUtil dialogUtil, File apkFile) {
        new XmlBatchReplace(context, dialogUtil, apkFile).show();
    }

    private void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_xml_batch_replace, null);
        AutoCompleteTextView findInput = view.findViewById(R.id.xml_find_input);
        AutoCompleteTextView replaceInput = view.findViewById(R.id.xml_replace_input);
        Spinner searchType = view.findViewById(R.id.xml_search_type);
        MaterialCheckBox idToName = view.findViewById(R.id.xml_id_to_name);
        MaterialCheckBox caseSensitive = view.findViewById(R.id.xml_case_sensitive);
        MaterialCheckBox regex = view.findViewById(R.id.xml_regex);
        MaterialCheckBox autoSign = view.findViewById(R.id.xml_auto_sign);

        bindHistory(findInput, HISTORY_FIND);
        bindHistory(replaceInput, HISTORY_REPLACE);
        bindDropdown(findInput, view.findViewById(R.id.xml_find_drop));
        bindDropdown(replaceInput, view.findViewById(R.id.xml_replace_drop));
        bindUnderline(findInput, view.findViewById(R.id.xml_find_line));
        bindUnderline(replaceInput, view.findViewById(R.id.xml_replace_line));

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        autoSign.setChecked(settings.getBoolean("autosign", true));

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(R.string.xml_batch_replace)
                .setView(view)
                .create();
        view.findViewById(R.id.xml_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.xml_ok).setOnClickListener(v -> {
            String find = findInput.getText().toString();
            String replacement = replaceInput.getText().toString();
            if (find.isEmpty()) {
                Extensions.showMessage(context, R.string.search_query_needed);
                return;
            }
            int position = searchType.getSelectedItemPosition();
            Kind kind = position >= 0 && position < Kind.values().length
                    ? Kind.values()[position] : Kind.CODE;
            boolean matchCase = caseSensitive.isChecked();
            boolean useRegex = regex.isChecked();
            Pattern pattern;
            try {
                pattern = Pattern.compile(useRegex ? find : Pattern.quote(find),
                        matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                Extensions.showMessage(context, R.string.xml_bad_regex);
                return;
            }
            remember(settings, HISTORY_FIND, find);
            remember(settings, HISTORY_REPLACE, replacement);
            settings.edit().putBoolean("autosign", autoSign.isChecked()).apply();
            dialog.dismiss();
            run(pattern, replacement, useRegex, kind, idToName.isChecked());
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    // ------------------------------------------------------------------ dialog widgets

    private void bindHistory(AutoCompleteTextView input, String key) {
        List<String> history = new ArrayList<>(readHistory(key));
        input.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, history));
    }

    private void bindDropdown(AutoCompleteTextView input, View arrow) {
        arrow.setOnClickListener(v -> {
            try {
                input.showDropDown();
            } catch (Exception ignored) {
            }
        });
    }

    private void bindUnderline(AutoCompleteTextView input, View line) {
        Drawable idle = line.getBackground();
        int focused = resolveColor(android.R.attr.colorAccent);
        input.setOnFocusChangeListener((v, hasFocus) ->
                line.setBackground(hasFocus ? new ColorDrawable(focused) : idle));
    }

    private int resolveColor(int attribute) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) return value.data;
        return 0xFF888888;
    }

    private Set<String> readHistory(String key) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> stored = settings.getStringSet(key, null);
        return stored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stored);
    }

    private void remember(SharedPreferences settings, String key, String value) {
        if (value == null || value.isEmpty()) return;
        Set<String> history = readHistory(key);
        history.remove(value);
        List<String> ordered = new ArrayList<>(history);
        ordered.add(0, value);
        Set<String> next = new LinkedHashSet<>();
        for (int i = 0; i < ordered.size() && i < HISTORY_LIMIT; i++) next.add(ordered.get(i));
        settings.edit().putStringSet(key, next).apply();
    }

    // ------------------------------------------------------------------ the actual work

    private void run(Pattern pattern, String replacement, boolean useRegex,
                     Kind kind, boolean idToName) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.rss.getString(R.string.searching));
        new Thread(() -> {
            List<ResEntry> resources = idToName ? loadResources() : null;
            final boolean named = resources != null && !resources.isEmpty();
            File root = new File(context.getCacheDir(), "xml_batch_out");
            int replaced = 0;
            Map<String, byte[]> modified = new LinkedHashMap<>();
            try {
                deleteRecursive(root);
                if (!root.mkdirs() && !root.isDirectory()) throw new IOException("Cannot create " + root);
                try (ZipFile zip = new ZipFile(apkFile)) {
                    java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry entry = enumeration.nextElement();
                        if (entry.isDirectory()) continue;
                        String name = entry.getName();
                        if (!name.toLowerCase(Locale.ROOT).endsWith(".xml")) continue;
                        byte[] data = readAll(zip.getInputStream(entry));
                        if (data.length == 0) continue;
                        byte[] out = null;
                        if (isBinaryXml(data)) {
                            List<XMLEntry> entries = decode(data, named ? resources : null);
                            if (entries == null) continue;
                            int hits = apply(entries, pattern, replacement, useRegex, kind);
                            if (hits > 0) {
                                out = new aXMLEncoder()
                                        .encodeString(entries, context, named ? resources : null);
                                replaced += hits;
                            }
                        } else if (kind == Kind.CODE) {
                            String text = new String(data, StandardCharsets.UTF_8);
                            Holder h = rep(text, pattern, replacement, useRegex);
                            if (h.hits > 0) {
                                out = h.value.getBytes(StandardCharsets.UTF_8);
                                replaced += h.hits;
                            }
                        }
                        if (out != null) modified.put(name, out);
                    }
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                pm.dismiss();
                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.xml_bad_regex));
                return;
            } catch (Throwable t) {
                pm.dismiss();
                context.runOnUiThread(() -> new ErrorUtil(context).showError(t));
                return;
            }

            if (modified.isEmpty()) {
                pm.dismiss();
                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.xml_no_match));
                return;
            }
            final int total = replaced;

            try {
                for (Map.Entry<String, byte[]> item : modified.entrySet()) {
                    File out = new File(root, item.getKey());
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Cannot create " + parent);
                    }
                    try (FileOutputStream os = new FileOutputStream(out)) {
                        os.write(item.getValue());
                    }
                }
                File marker = new File(root, ".done");
                try (FileOutputStream os = new FileOutputStream(marker)) {
                    os.write(1);
                }
                String[] names = modified.keySet().toArray(new String[0]);
                pm.dismiss();
                context.runOnUiThread(() -> {
                    Extensions.showMessage(context, context.rss.getString(
                            R.string.xml_replaced_x, total, modified.size()));
                    context.handleModifiedEntriesResult(Uri.fromFile(marker), names,
                            apkFile.getAbsolutePath(), root.getAbsolutePath());
                });
            } catch (Throwable t) {
                pm.dismiss();
                context.runOnUiThread(() -> new ErrorUtil(context).showError(t));
            }
        }).start();
    }

    private List<ResEntry> loadResources() {
        try (ZipFile zip = new ZipFile(apkFile)) {
            ZipEntry entry = zip.getEntry("resources.arsc");
            if (entry == null) entry = zip.getEntry("res/resources.arsc");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return new ResourceTableParser(in).parse();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<XMLEntry> decode(byte[] data, List<ResEntry> resources) {
        try {
            InputStream in = new ByteArrayInputStream(data);
            aXMLDecoder decoder = resources == null
                    ? new aXMLDecoder(in)
                    : new aXMLDecoder(in, resources);
            return decoder.decode();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Rewrites the entries in place and returns how many substitutions actually landed. */
    private static int apply(List<XMLEntry> entries, Pattern pattern, String replacement,
                             boolean useRegex, Kind kind) {
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            XMLEntry entry = entries.get(i);
            String tag = entry.getTag();
            String middle = entry.getMiddleTag();
            String value = entry.getValue();
            String end = entry.getEndTag();

            if (kind == Kind.CODE) {
                Holder t = rep(tag, pattern, replacement, useRegex);
                Holder v = rep(value, pattern, replacement, useRegex);
                Holder d = rep(end, pattern, replacement, useRegex);
                if (t.hits + v.hits + d.hits > 0) {
                    entries.set(i, new XMLEntry(t.value, middle, v.value, d.value));
                    count += t.hits + v.hits + d.hits;
                }
                continue;
            }

            boolean isText = middle.isEmpty() && end.isEmpty() && !tag.trim().startsWith("<");
            if (isText) {
                if (kind != Kind.STRING) continue;
                Holder t = rep(tag, pattern, replacement, useRegex);
                if (t.hits > 0) {
                    entries.set(i, new XMLEntry(t.value, middle, value, end));
                    count += t.hits;
                }
                continue;
            }

            if (kindOf(value) != kind) continue;
            Holder v = rep(value, pattern, replacement, useRegex);
            if (v.hits > 0) {
                entries.set(i, new XMLEntry(tag, middle, v.value, end));
                count += v.hits;
            }
        }
        return count;
    }

    private static final class Holder {
        String value;
        int hits;

        Holder(String value) {
            this.value = value;
        }
    }

    private static Holder rep(String input, Pattern pattern, String replacement, boolean useRegex) {
        Holder holder = new Holder(input);
        if (input == null || input.isEmpty()) return holder;
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return holder;
        String suffix = useRegex ? replacement : Matcher.quoteReplacement(replacement);
        StringBuffer buffer = new StringBuffer();
        do {
            matcher.appendReplacement(buffer, suffix);
            holder.hits++;
        } while (matcher.find());
        matcher.appendTail(buffer);
        holder.value = buffer.toString();
        return holder;
    }

    private static Kind kindOf(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        if (text.startsWith("@") || text.startsWith("?")) return Kind.RES_ID;
        if (text.matches("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            return Kind.COLOR;
        }
        if (text.matches("[-+]?\\d+(?:\\.\\d+)?(?:dp|dip|sp|px|pt|in|mm|%)?")
                || text.matches("0[xX][0-9a-fA-F]+")) {
            return Kind.INT;
        }
        return Kind.STRING;
    }

    /** Binary AXML starts with type 0x0003 and a header size of 8, little endian. */
    private static boolean isBinaryXml(byte[] data) {
        return data.length >= 4 && data[0] == 3 && data[1] == 0 && data[2] == 8 && data[3] == 0;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = stream.read(buffer)) != -1) out.write(buffer, 0, length);
            return out.toByteArray();
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
