package io.github.abdurazaaqmohammed.ui.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.dex.DexCompareEngine;
import io.github.abdurazaaqmohammed.ui.activities.CompareDexActivity;

/**
 * Shown when the "Compare DEX" file action is picked: displays the old (left)
 * and new (right) file with a button to exchange them plus the ignore options
 * that are handed to {@link CompareDexActivity}.
 */
public class CompareDexOptionsDialog {

    private static final String KEY_IGNORE_DEBUG = "cmp_dex_ignore_debug";
    private static final String KEY_IGNORE_OPT = "cmp_dex_ignore_optimizations";
    private static final String KEY_IGNORE_REGISTERS = "cmp_dex_ignore_registers";
    private static final String KEY_IGNORE_NOP = "cmp_dex_ignore_nop";

    private final Context context;
    private List<File> left;
    private List<File> right;

    public CompareDexOptionsDialog(Context context, List<File> files1, List<File> files2) {
        this.context = context;
        this.left = new ArrayList<>(files1);
        this.right = new ArrayList<>(files2);
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_compare_dex_options, null);
        TextView oldName = view.findViewById(R.id.cmp_dex_old_name);
        TextView newName = view.findViewById(R.id.cmp_dex_new_name);
        View exchange = view.findViewById(R.id.cmp_dex_exchange);
        CheckBox ignoreDebug = view.findViewById(R.id.cb_ignore_debug_info);
        CheckBox ignoreOptimizations = view.findViewById(R.id.cb_ignore_optimizations);
        CheckBox ignoreRegisters = view.findViewById(R.id.cb_ignore_register_count);
        CheckBox ignoreNop = view.findViewById(R.id.cb_ignore_nop);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ignoreDebug.setChecked(prefs.getBoolean(KEY_IGNORE_DEBUG, false));
        ignoreOptimizations.setChecked(prefs.getBoolean(KEY_IGNORE_OPT, false));
        ignoreRegisters.setChecked(prefs.getBoolean(KEY_IGNORE_REGISTERS, false));
        ignoreNop.setChecked(prefs.getBoolean(KEY_IGNORE_NOP, false));

        Runnable refresh = () -> {
            oldName.setText(joinNames(left));
            newName.setText(joinNames(right));
        };
        refresh.run();
        exchange.setOnClickListener(v -> {
            List<File> swap = left;
            left = right;
            right = swap;
            refresh.run();
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.compare_dex)
                .setView(view)
                .setPositiveButton(R.string.compare_dex, (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(KEY_IGNORE_DEBUG, ignoreDebug.isChecked())
                            .putBoolean(KEY_IGNORE_OPT, ignoreOptimizations.isChecked())
                            .putBoolean(KEY_IGNORE_REGISTERS, ignoreRegisters.isChecked())
                            .putBoolean(KEY_IGNORE_NOP, ignoreNop.isChecked())
                            .apply();
                    DexCompareEngine.Options options = new DexCompareEngine.Options(
                            ignoreDebug.isChecked(),
                            ignoreOptimizations.isChecked(),
                            ignoreRegisters.isChecked(),
                            ignoreNop.isChecked());
                    context.startActivity(CompareDexActivity.intent(context, left, right, options));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String joinNames(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(file.getName());
        }
        return sb.toString();
    }
}