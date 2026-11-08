package io.github.abdurazaaqmohammed.features.apk.frida;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Configuration surface for the Frida Hook Engine's 21 hook modules: a
 * Material 3 switch list, where each module that carries a companion value
 * object opens an editor (labeled fields, key/value rows, one-per-line lists
 * or raw JSON) when tapped. Shared by the APK "Frida Gadget" patcher (Path B)
 * and the root Frida console (Path C) — both feed the same stored parameters
 * object into the engine.
 */
public final class FridaModuleDialog {

    private FridaModuleDialog() {
    }

    public static void show(Activity context, FridaConfig config, Runnable onChanged) {
        int pad = Extensions.dp2px(context, 16);
        int padSmall = Extensions.dp2px(context, 8);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // Engine-level: route output to logcat under the FridaHookEngine tag.
        root.addView(sectionHeader(context, context.getString(R.string.frida_cfg_engine), pad));
        root.addView(switchRow(context, pad, context.getString(R.string.frida_cfg_debug_log),
                context.getString(R.string.frida_cfg_debug_log_desc),
                config.getBoolean(FridaConfig.DEBUG_LOG_KEY, true), checked ->
                        config.putBoolean(FridaConfig.DEBUG_LOG_KEY, checked)));
        root.addView(divider(context));

        root.addView(sectionHeader(context, context.getString(R.string.frida_cfg_bypasses), pad));
        int groupStart = 0;
        for (int i = 0; i < FridaConfig.MODULES.length; i++) {
            FridaConfig.Module module = FridaConfig.MODULES[i];
            boolean newGroup = i > 0 && groupOf(module) != groupOf(FridaConfig.MODULES[i - 1]);
            if (newGroup) {
                root.addView(divider(context));
                root.addView(sectionHeader(context, groupTitle(context, module), pad));
                groupStart = i;
            }
            View row = moduleRow(context, pad, padSmall, config, module, onChanged);
            root.addView(row);
        }

        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.75);
        ScrollView scroller = new ScrollView(context);
        scroller.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        scroller.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapper.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(maxHeight, Math.max(pad, scroller.getMeasuredHeight()))));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.frida_modules_title)
                .setView(wrapper)
                .setNeutralButton(R.string.frida_cfg_reset, (d, w) -> {
                    config.reset(context);
                    if (onChanged != null) onChanged.run();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> config.save(context))
                .show();
    }

    private static int groupOf(FridaConfig.Module module) {
        return switch (module.flag) {
            case "geoRestrictionBypassEnabled", "localeSpoofEnabled",
                 "suspiciousPackageSpoofEnabled", "installedPackageSpoofEnabled",
                 "deviceIdSpoofEnabled", "buildPropSpoofEnabled", "systemPropertySpoofEnabled",
                 "hardwareFeatureSpoofEnabled", "contentProviderSpoofEnabled" -> 1;
            case "javaHooksEnabled", "nativeHooksEnabled", "nativePatchesEnabled",
                 "registerNativesLogEnabled" -> 2;
            default -> 0;
        };
    }

    private static String groupTitle(Activity context, FridaConfig.Module module) {
        int res = groupOf(module) == 1 ? R.string.frida_cfg_spoofing : R.string.frida_cfg_advanced;
        return context.getString(res);
    }

    private static View moduleRow(Activity context, int pad, int padSmall,
                                  FridaConfig config, FridaConfig.Module module, Runnable onChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, padSmall, pad, padSmall);
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(module.titleRes);
        title.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        textCol.addView(title);

        TextView desc = new TextView(context);
        desc.setText(module.descRes);
        desc.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        desc.setAlpha(0.7f);
        textCol.addView(desc);
        row.addView(textCol);

        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked(config.isEnabled(module.flag));
        sw.setOnCheckedChangeListener((bv, checked) -> {
            config.setEnabled(module.flag, checked);
            config.save(context);
            if (onChanged != null) onChanged.run();
        });
        row.addView(sw);

        if (module.editor != FridaConfig.Editor.NONE) {
            row.setOnClickListener(v -> {
                // Don't fire when the switch itself was touched.
                openEditor(context, config, module, onChanged);
            });
            TextView chevron = new TextView(context);
            chevron.setText("⋯");
            chevron.setTextSize(18f);
            chevron.setAlpha(0.6f);
            chevron.setPadding(padSmall, 0, 0, 0);
            row.addView(chevron);
        }
        return row;
    }

    private interface BooleanCallback {
        void accept(boolean value);
    }

    private static View switchRow(Activity context, int pad, String title, String desc,
                                  boolean checked, BooleanCallback onChecked) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, Extensions.dp2px(context, 8), pad, Extensions.dp2px(context, 8));
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        textCol.addView(t);
        TextView d = new TextView(context);
        d.setText(desc);
        d.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        d.setAlpha(0.7f);
        textCol.addView(d);
        row.addView(textCol);
        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((bv, c) -> onChecked.accept(c));
        row.addView(sw);
        return row;
    }

    private static TextView sectionHeader(Activity context, String text, int pad) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setPadding(pad, Extensions.dp2px(context, 12), pad, Extensions.dp2px(context, 4));
        return header;
    }

    private static View divider(Activity context) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Extensions.dp2px(context, 1))));
        v.setAlpha(0.15f);
        v.setBackgroundColor(0xFF888888);
        return v;
    }

    // ------------------------------------------------------------------
    // editors
    // ------------------------------------------------------------------

    private static void openEditor(Activity context, FridaConfig config,
                                   FridaConfig.Module module, Runnable onChanged) {
        switch (module.editor) {
            case MAP_FIXED:
                showFixedMapEditor(context, config, module, fieldKeys(module.valuesKey), onChanged);
                break;
            case MAP_OPEN:
                showKeyValueEditor(context, config, module, onChanged);
                break;
            case LIST:
                showListEditor(context, config, module.valuesKey,
                        context.getString(module.titleRes), onChanged);
                break;
            case JSON:
                showJsonEditor(context, config, module, onChanged);
                break;
            case DEVICE_ID:
                showDeviceIdEditor(context, config, module, onChanged);
                break;
            case REGISTER_NATIVES:
                showRegisterNativesEditor(context, config, module, onChanged);
                break;
            default:
                break;
        }
    }

    private static String[] fieldKeys(String valuesKey) {
        return switch (valuesKey) {
            case "geoSpoofValues" -> FridaConfig.GEO_KEYS;
            case "localeSpoofValues" -> FridaConfig.LOCALE_KEYS;
            case "buildPropSpoofValues" -> FridaConfig.BUILD_PROP_KEYS;
            default -> new String[0];
        };
    }

    /** A fixed-key value map rendered as labeled input fields. */
    private static void showFixedMapEditor(Activity context, FridaConfig config,
                                           FridaConfig.Module module, String[] keys, Runnable onChanged) {
        JSONObject values = config.getValues(module.valuesKey);
        if (values == null) values = FridaConfig.defaultValues(module.valuesKey);
        if (values == null) values = new JSONObject();
        JSONObject current = values;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);
        List<TextInputEditText> inputs = new ArrayList<>();
        for (String key : keys) {
            TextInputLayout layout = new TextInputLayout(context, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            layout.setHint(prettyKey(key));
            TextInputEditText input = new TextInputEditText(context);
            input.setText(current.optString(key, ""));
            input.setTextSize(14f);
            layout.addView(input);
            root.addView(layout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.add(input);
        }
        ScrollView scroller = new ScrollView(context);
        scroller.addView(root);
        new MaterialAlertDialogBuilder(context)
                .setTitle(module.titleRes)
                .setView(scroller)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    JSONObject updated = new JSONObject();
                    for (int i = 0; i < keys.length; i++) {
                        String text = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString().trim();
                        try {
                            updated.put(keys[i], text);
                        } catch (Exception ignored) {
                        }
                    }
                    config.putValues(module.valuesKey, updated);
                    config.save(context);
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    /** An open-ended key/value map rendered as editable rows. */
    private static void showKeyValueEditor(Activity context, FridaConfig config,
                                           FridaConfig.Module module, Runnable onChanged) {
        JSONObject values = config.getValues(module.valuesKey);
        if (values == null) values = FridaConfig.defaultValues(module.valuesKey);
        if (values == null) values = new JSONObject();
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = values.keys(); it.hasNext(); ) keys.add(it.next());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);

        Runnable[] rebuild = new Runnable[1];
        List<String[]> pairs = new ArrayList<>();
        for (String k : keys) pairs.add(new String[]{k, values.optString(k)});
        rebuild[0] = () -> {
            rows.removeAllViews();
            for (int i = 0; i < pairs.size(); i++) {
                final int index = i;
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = new TextView(context);
                label.setText(pairs.get(i)[0]);
                label.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                label.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(label);
                TextInputEditText valueInput = new TextInputEditText(context);
                valueInput.setText(pairs.get(i)[1]);
                valueInput.setTextSize(13f);
                valueInput.setSingleLine(true);
                valueInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                valueInput.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus && valueInput.getText() != null) {
                        pairs.get(index)[1] = valueInput.getText().toString();
                    }
                });
                row.addView(valueInput);
                TextView remove = new TextView(context);
                remove.setText("✕");
                remove.setPadding(pad / 2, 0, 0, 0);
                remove.setOnClickListener(v -> {
                    valueInput.clearFocus();
                    pairs.remove(index);
                    rebuild[0].run();
                });
                row.addView(remove);
                rows.addView(row);
            }
        };
        rebuild[0].run();

        TextView add = new TextView(context);
        add.setText(context.getString(R.string.frida_cfg_add_property));
        add.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        add.setPadding(0, Extensions.dp2px(context, 12), 0, 0);
        add.setOnClickListener(v -> {
            pairs.add(new String[]{"", ""});
            rebuild[0].run();
        });
        root.addView(add);

        ScrollView scroller = new ScrollView(context);
        scroller.addView(root);
        new MaterialAlertDialogBuilder(context)
                .setTitle(module.titleRes)
                .setView(scroller)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    JSONObject updated = new JSONObject();
                    for (String[] pair : pairs) {
                        String key = pair[0] == null ? "" : pair[0].trim();
                        if (key.isEmpty()) continue;
                        try {
                            updated.put(key, pair[1] == null ? "" : pair[1].trim());
                        } catch (Exception ignored) {
                        }
                    }
                    config.putValues(module.valuesKey, updated);
                    config.save(context);
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    /** A string list rendered as one entry per line. */
    private static void showListEditor(Activity context, FridaConfig config, String valuesKey,
                                       CharSequence title, Runnable onChanged) {
        StringBuilder sb = new StringBuilder();
        JSONArray array = config.getArray(valuesKey);
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(array.optString(i));
            }
        }
        int pad = Extensions.dp2px(context, 16);
        EditText input = new EditText(context);
        input.setText(sb.toString());
        input.setMinLines(4);
        input.setTextSize(14f);
        input.setHorizontallyScrolling(false);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);
        TextView hint = new TextView(context);
        hint.setText(R.string.frida_cfg_one_per_line);
        hint.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hint.setAlpha(0.7f);
        root.addView(hint);
        root.addView(input);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    JSONArray updated = new JSONArray();
                    String text = input.getText() == null ? "" : input.getText().toString();
                    for (String line : text.split("\n")) {
                        line = line.trim();
                        if (!line.isEmpty()) updated.put(line);
                    }
                    config.putArray(valuesKey, updated);
                    config.save(context);
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    /** Raw JSON editor for the advanced value objects (hooks, patches, nested maps). */
    private static void showJsonEditor(Activity context, FridaConfig config,
                                       FridaConfig.Module module, Runnable onChanged) {
        Object current = config.params().opt(module.valuesKey);
        if (current == null) current = FridaConfig.defaultValues(module.valuesKey);
        String text = current == null ? "[]" : current.toString();
        try {
            Object parsed = new JSONTokener(text).nextValue();
            if (parsed instanceof JSONObject) {
                text = ((JSONObject) parsed).toString(2);
            } else if (parsed instanceof JSONArray) {
                text = ((JSONArray) parsed).toString(2);
            }
        } catch (Exception ignored) {
        }
        int pad = Extensions.dp2px(context, 16);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);
        TextView hint = new TextView(context);
        hint.setText(jsonHint(module.valuesKey));
        hint.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hint.setAlpha(0.7f);
        root.addView(hint);
        EditText input = new EditText(context);
        input.setText(text);
        input.setMinLines(6);
        input.setTextSize(13f);
        input.setTypeface(Typeface.MONOSPACE);
        input.setHorizontallyScrolling(false);
        root.addView(input);
        new MaterialAlertDialogBuilder(context)
                .setTitle(module.titleRes)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    try {
                        Object parsed = new JSONTokener(value).nextValue();
                        if (parsed instanceof JSONObject) {
                            config.putValues(module.valuesKey, (JSONObject) parsed);
                        } else if (parsed instanceof JSONArray) {
                            config.putArray(module.valuesKey, (JSONArray) parsed);
                        } else {
                            Extensions.showMessage(context, R.string.frida_cfg_invalid_json);
                            return;
                        }
                        config.save(context);
                        if (onChanged != null) onChanged.run();
                    } catch (Exception e) {
                        Extensions.showMessage(context, context.getString(
                                R.string.frida_cfg_invalid_json) + "\n" + e.getMessage());
                    }
                })
                .show();
    }

    private static String jsonHint(String valuesKey) {
        return switch (valuesKey) {
            case "javaHooks" -> "[{\"className\":\"com.example.Cls\",\"methodName\":\"m\","
                    + "\"argTypes\":[\"java.lang.String\"],\"returnType\":\"boolean\","
                    + "\"onCall\":{\"forceReturn\":true}}]";
            case "nativeHooks" -> "[{\"moduleName\":\"libtarget.so\",\"symbolName\":\"sym\","
                    + "\"argTypes\":[\"int\"],\"returnType\":\"int\","
                    + "\"onCall\":{\"forceReturn\":1}}]";
            case "nativePatches" -> "[{\"moduleName\":\"libtarget.so\",\"offset\":\"0x2A10\","
                    + "\"patchBytes\":[\"1F\",\"20\",\"03\",\"D5\"]}]";
            case "installedPackageSpoofValues" ->
                    "{\"com.example.pkg\":{\"versionName\":\"1.0\",\"uid\":\"10123\"}}";
            case "contentProviderSpoofValues" -> "{\"settings\":{\"key\":\"value\"}}";
            default -> "{}";
        };
    }

    /** Device identifiers: fixed profile fields plus the random-profile switch. */
    private static void showDeviceIdEditor(Activity context, FridaConfig config,
                                           FridaConfig.Module module, Runnable onChanged) {
        JSONObject values = config.getValues("deviceIdSpoofValues");
        if (values == null) values = FridaConfig.defaultValues("deviceIdSpoofValues");
        if (values == null) values = new JSONObject();
        JSONObject current = values;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);

        LinearLayout randomRow = new LinearLayout(context);
        randomRow.setOrientation(LinearLayout.HORIZONTAL);
        randomRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView randomLabel = new TextView(context);
        randomLabel.setText(R.string.frida_cfg_random_device_id);
        randomLabel.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        randomLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        randomRow.addView(randomLabel);
        MaterialSwitch randomSwitch = new MaterialSwitch(context);
        randomSwitch.setChecked(config.getBoolean(FridaConfig.DEVICE_ID_RANDOM_KEY, false));
        randomRow.addView(randomSwitch);
        root.addView(randomRow);
        TextView randomHint = new TextView(context);
        randomHint.setText(R.string.frida_cfg_random_device_id_desc);
        randomHint.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        randomHint.setAlpha(0.7f);
        root.addView(randomHint);

        List<TextInputEditText> inputs = new ArrayList<>();
        for (String key : FridaConfig.DEVICE_ID_KEYS) {
            TextInputLayout layout = new TextInputLayout(context, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            layout.setHint(prettyKey(key));
            TextInputEditText input = new TextInputEditText(context);
            input.setText(current.optString(key, ""));
            input.setTextSize(14f);
            layout.addView(input);
            root.addView(layout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.add(input);
        }
        ScrollView scroller = new ScrollView(context);
        scroller.addView(root);
        new MaterialAlertDialogBuilder(context)
                .setTitle(module.titleRes)
                .setView(scroller)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    config.putBoolean(FridaConfig.DEVICE_ID_RANDOM_KEY, randomSwitch.isChecked());
                    JSONObject updated = new JSONObject();
                    for (int i = 0; i < FridaConfig.DEVICE_ID_KEYS.length; i++) {
                        String text = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString().trim();
                        try {
                            updated.put(FridaConfig.DEVICE_ID_KEYS[i], text);
                        } catch (Exception ignored) {
                        }
                    }
                    config.putValues("deviceIdSpoofValues", updated);
                    config.save(context);
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    /** RegisterNatives discovery: class filter list plus the libart locator override. */
    private static void showRegisterNativesEditor(Activity context, FridaConfig config,
                                                  FridaConfig.Module module, Runnable onChanged) {
        JSONObject locator = config.getValues(FridaConfig.REGISTER_NATIVES_LOCATOR_KEY);
        if (locator == null) locator = new JSONObject();
        JSONObject current = locator;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, Extensions.dp2px(context, 8), pad, 0);

        TextView filterLabel = new TextView(context);
        filterLabel.setText(R.string.frida_cfg_class_filter);
        filterLabel.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        root.addView(filterLabel);
        TextView filterHint = new TextView(context);
        filterHint.setText(R.string.frida_cfg_one_per_line);
        filterHint.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        filterHint.setAlpha(0.7f);
        root.addView(filterHint);
        EditText filterInput = new EditText(context);
        StringBuilder sb = new StringBuilder();
        JSONArray filter = config.getArray(FridaConfig.REGISTER_NATIVES_FILTER_KEY);
        if (filter != null) {
            for (int i = 0; i < filter.length(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(filter.optString(i));
            }
        }
        filterInput.setText(sb.toString());
        filterInput.setMinLines(2);
        filterInput.setTextSize(14f);
        root.addView(filterInput);

        TextView locatorLabel = new TextView(context);
        locatorLabel.setText(R.string.frida_cfg_locator);
        locatorLabel.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        locatorLabel.setPadding(0, Extensions.dp2px(context, 12), 0, 0);
        root.addView(locatorLabel);
        List<TextInputEditText> inputs = new ArrayList<>();
        for (String key : FridaConfig.REGISTER_NATIVES_KEYS) {
            TextInputLayout layout = new TextInputLayout(context, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            layout.setHint(prettyKey(key));
            TextInputEditText input = new TextInputEditText(context);
            input.setText(current.optString(key, ""));
            input.setTextSize(14f);
            layout.addView(input);
            root.addView(layout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.add(input);
        }
        ScrollView scroller = new ScrollView(context);
        scroller.addView(root);
        new MaterialAlertDialogBuilder(context)
                .setTitle(module.titleRes)
                .setView(scroller)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    JSONArray updatedFilter = new JSONArray();
                    String text = filterInput.getText() == null ? "" : filterInput.getText().toString();
                    for (String line : text.split("\n")) {
                        line = line.trim();
                        if (!line.isEmpty()) updatedFilter.put(line);
                    }
                    config.putArray(FridaConfig.REGISTER_NATIVES_FILTER_KEY, updatedFilter);
                    JSONObject updatedLocator = new JSONObject();
                    for (int i = 0; i < FridaConfig.REGISTER_NATIVES_KEYS.length; i++) {
                        String value = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString().trim();
                        try {
                            updatedLocator.put(FridaConfig.REGISTER_NATIVES_KEYS[i], value);
                        } catch (Exception ignored) {
                        }
                    }
                    config.putValues(FridaConfig.REGISTER_NATIVES_LOCATOR_KEY, updatedLocator);
                    config.save(context);
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    private static String prettyKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append(' ');
            sb.append(c);
        }
        String out = sb.toString();
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }
}