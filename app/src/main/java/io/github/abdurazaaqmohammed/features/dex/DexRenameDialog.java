package io.github.abdurazaaqmohammed.features.dex;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import org.apache.commons.io.FilenameUtils;

import com.reandroid.apk.APKLogger;

/**
 * MT's "global rename" dialog: a rename script in, a re-signed APK out.
 *
 * <p>The script itself is interpreted by {@link DexRefactor}; this class only collects it, asks
 * for a signature when auto-sign is on and swaps the result over the original APK.
 */
public final class DexRenameDialog {

    private static final String PREF_SCRIPT = "dex_rename_script";

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final File apkFile;

    private DexRenameDialog(MainActivity context, DialogUtil dialogUtil, File apkFile) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.apkFile = apkFile;
    }

    public static void show(MainActivity context, DialogUtil dialogUtil, File apkFile) {
        new DexRenameDialog(context, dialogUtil, apkFile).show();
    }

    private void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_dex_rename, null);
        EditText script = view.findViewById(R.id.dexr_script);
        MaterialCheckBox inner = view.findViewById(R.id.dexr_inner);
        MaterialCheckBox access = view.findViewById(R.id.dexr_access);
        MaterialCheckBox autoSign = view.findViewById(R.id.dexr_auto_sign);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        autoSign.setChecked(settings.getBoolean("autosign", true));
        // A remembered script is only a convenience: it must never silently re-run.
        String saved = settings.getString(PREF_SCRIPT, null);
        if (saved != null && !saved.isEmpty()) {
            script.setText(saved);
        }

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(R.string.dex_global_rename)
                .setView(view)
                .create();
        view.findViewById(R.id.dexr_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.dexr_ok).setOnClickListener(v -> {
            String text = script.getText().toString();
            DexRefactor.Script parsed = DexRefactor.parse(text);
            if (parsed.size() == 0) {
                Extensions.showMessage(context, R.string.dex_global_rename_bad_script);
                return;
            }
            parsed.renameInnerClasses = inner.isChecked();
            parsed.fixAccessibility = access.isChecked();
            boolean sign = autoSign.isChecked();
            settings.edit()
                    .putBoolean("autosign", sign)
                    .putString(PREF_SCRIPT, text)
                    .apply();
            dialog.dismiss();
            run(parsed, sign);
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private void run(DexRefactor.Script script, boolean sign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doRename = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    File out = FileUtils.getUnusedFile(new File(apkFile.getAbsoluteFile().getParentFile(),
                            FilenameUtils.getBaseName(apkFile.getName()) + "_rename.apk"));
                    DexRefactor.Result result = DexRefactor.apply(apkFile, out, script,
                            logger::logMessage);
                    if (sign) {
                        wrapper[0].signApk(out);
                    }
                    FileUtils.swapWithBackup(apkFile, out);
                    logger.close();
                    pm.dismiss();
                    final int applied = result.applied;
                    context.handler.post(() -> {
                        Extensions.showMessage(context, applied > 0
                                ? context.rss.getString(R.string.dex_global_rename_done, applied)
                                : context.rss.getString(R.string.dex_global_rename_none));
                        context.loadFolderInPane(apkFile.getParentFile(), true, false);
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    context.handler.post(logger::close);
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        };
        if (sign) {
            SignWrapper.requireAuth(context, sw -> {
                wrapper[0] = sw;
                doRename.run();
            });
        } else {
            doRename.run();
        }
    }
}