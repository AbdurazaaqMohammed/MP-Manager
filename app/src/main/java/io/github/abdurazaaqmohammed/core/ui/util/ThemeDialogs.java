package io.github.abdurazaaqmohammed.core.ui.util;

import android.R;
import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import io.github.abdurazaaqmohammed.core.ui.base.BaseDialog;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemePlugin;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

import java.util.List;

/**
 * Example of extracted common UI: theme switching previously required
 * touching every activity; now one dialog + BaseActivity handles it.
 */
public final class ThemeDialogs {

    private ThemeDialogs() {
    }

    public static void showThemeChooser(final Context context) {
        List<ThemePlugin> themes = ThemeRegistry.getAll();
        String[] names = new String[themes.size()];
        for (int i = 0; i < themes.size(); i++) names[i] = themes.get(i).displayName();
        String current = ThemeRegistry.getCurrentId(context);
        int checked = 0;
        for (int i = 0; i < themes.size(); i++) {
            if (themes.get(i).id().equals(current)) {
                checked = i;
                break;
            }
        }
        final int[] selected = {checked};
        BaseDialog.builder(context)
                .setTitle("Theme")
                .setSingleChoiceItems(names, checked, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    ThemeRegistry.setCurrentId(context, themes.get(selected[0]).id());
                    if (context instanceof Activity) {
                        try {
                            ((Activity) context).recreate();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Accent color dialog: follow material color, the last 5 colors the
     *  user used, or a full color-wheel picker. Applied as a stacked theme
     *  overlay on every BaseActivity. */
    public static void showAccentChooser(final Context context) {
        List<Integer> history = ThemeRegistry.getAccentHistory(context);
        String[] names = new String[history.size() + 2];
        names[0] = "Follow material color";
        for (int i = 0; i < history.size(); i++) {
            names[i + 1] = String.format("#%06X", history.get(i) & 0xFFFFFF);
        }
        names[names.length - 1] = "Custom color\u2026";
        String current = null;
        int currentColor = 0;
        try {
            android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            current = prefs.getString(ThemeRegistry.ACCENT_PREF_KEY, null);
            currentColor = prefs.getInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, 0);
        } catch (Exception ignored) {
        }
        int checked = 0;
        if ("custom".equals(current) && currentColor != 0) {
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i) == currentColor) {
                    checked = i + 1;
                    break;
                }
            }
        }
        final List<Integer> historyFinal = history;
        final int[] selected = {checked};
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle("Accent color")
                        .setSingleChoiceItems(names, checked, (d, which) -> selected[0] = which)
                        .setPositiveButton(R.string.ok, (d, which) -> {
                            if (selected[0] == 0) {
                                applyAccentMode(context, null); // follow material color
                            } else if (selected[0] == names.length - 1) {
                                showCustomAccentPicker(context);
                            } else {
                                int color = historyFinal.get(selected[0] - 1);
                                ThemeRegistry.pushAccentHistory(context, color);
                                try {
                                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                                            .edit()
                                            .putString(ThemeRegistry.ACCENT_PREF_KEY, "custom")
                                            .putInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, color)
                                            .apply();
                                } catch (Exception ignored) {
                                }
                                applyAccentMode(context, "custom");
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
        // Color the radio labels with their swatches.
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        android.widget.ListView list = dialog.getListView();
        if (list != null) {
            list.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(android.view.View parent, android.view.View child) {
                    tintRow(child, historyFinal, names.length);
                }

                @Override
                public void onChildViewRemoved(android.view.View parent, android.view.View child) {
                }
            });
            // Rows already present at show-time.
            for (int i = 0; i < list.getChildCount(); i++) {
                tintRow(list.getChildAt(i), historyFinal, names.length);
            }
        }
    }

    private static void tintRow(android.view.View child, List<Integer> history, int count) {
        if (!(child instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) child;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View grand = group.getChildAt(i);
            if (grand instanceof android.widget.TextView) {
                android.widget.TextView text = (android.widget.TextView) grand;
                String value = String.valueOf(text.getText());
                int index = -1;
                for (int h = 0; h < history.size(); h++) {
                    if (String.format("#%06X", history.get(h) & 0xFFFFFF).equals(value)) {
                        index = h;
                        break;
                    }
                }
                if (index >= 0) {
                    text.setTextColor(history.get(index));
                }
            }
        }
    }

    private static void applyAccentMode(Context context, String mode) {
        try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(ThemeRegistry.ACCENT_PREF_KEY, mode).apply();
        } catch (Exception ignored) {
        }
        if (context instanceof Activity) {
            try {
                ((Activity) context).recreate();
            } catch (Exception ignored) {
            }
        }
    }

    /** Custom accent picker: the app's color wheel with hex input and the
     *  last-used color as the starting point. */
    private static void showCustomAccentPicker(final Context context) {
        final int initial;
        try {
            initial = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, 0xFF6750A4);
        } catch (Exception e) {
            return;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad / 2, pad, 0);

        io.github.abdurazaaqmohammed.ui.ColorWheelView wheel =
                new io.github.abdurazaaqmohammed.ui.ColorWheelView(context);
        root.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * context.getResources().getDisplayMetrics().density)));

        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.view.View previewSwatch = new android.view.View(context);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                (int) (48 * context.getResources().getDisplayMetrics().density),
                (int) (48 * context.getResources().getDisplayMetrics().density));
        swatchParams.rightMargin = pad / 2;
        previewRow.addView(previewSwatch, swatchParams);
        final android.widget.EditText hexInput = new android.widget.EditText(context);
        hexInput.setHint("#RRGGBB");
        hexInput.setSingleLine(true);
        previewRow.addView(hexInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(previewRow);

        final int[] chosen = {initial};
        Runnable sync = () -> {
            int color = wheel.getColor(255);
            chosen[0] = color;
            previewSwatch.setBackgroundColor(color);
            String hex = String.format("#%06X", color & 0xFFFFFF);
            if (!hex.equalsIgnoreCase(String.valueOf(hexInput.getText()))) {
                hexInput.setText(hex);
            }
        };
        wheel.setListener(sync);
        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String hex = s.toString().trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                if (hex.matches("#[0-9a-fA-F]{6}")) {
                    try {
                        int parsed = (int) Long.parseLong(hex.substring(1), 16) | 0xFF000000;
                        if ((parsed & 0xFFFFFF) != (wheel.getColor(255) & 0xFFFFFF)) {
                            wheel.setColor(parsed);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        wheel.setColor(initial);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Custom color")
                .setView(root)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    int color = chosen[0] | 0xFF000000;
                    ThemeRegistry.pushAccentHistory(context, color);
                    try {
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                                .edit()
                                .putString(ThemeRegistry.ACCENT_PREF_KEY, "custom")
                                .putInt(ThemeRegistry.ACCENT_CUSTOM_PREF_KEY, color)
                                .apply();
                    } catch (Exception ignored) {
                    }
                    if (context instanceof Activity) {
                        try {
                            ((Activity) context).recreate();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}