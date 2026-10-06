package io.github.abdurazaaqmohammed.core.ui.util;

import android.R;
import android.app.Activity;
import android.content.Context;

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
}
