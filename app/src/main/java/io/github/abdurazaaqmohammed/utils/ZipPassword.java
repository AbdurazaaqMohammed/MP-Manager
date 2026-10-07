package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public final class ZipPassword {

    private static final Map<String, String> REMEMBERED = new ConcurrentHashMap<>();

    private ZipPassword() {
    }

    public static void remember(File zip, String password) {
        if (zip != null && password != null) REMEMBERED.put(zip.getAbsolutePath(), password);
    }

    public static String remembered(File zip) {
        return zip == null ? null : REMEMBERED.get(zip.getAbsolutePath());
    }

    public static void apply(ZipFile zf, File zip) {
        String password = remembered(zip);
        if (password != null) zf.setPassword(password.toCharArray());
    }

    /**
     * Finds the password for an encrypted entry: the remembered one and the
     * stored archive passwords are tried first (a wrong guess only fails once
     * the entry has been read to the end, which is why each try reads fully),
     * then the user is asked and re-asked until it works or they cancel.
     *
     * @return null when the entry is not encrypted or the user cancelled.
     */
    public static String resolve(Context context, ZipFile zf, File zip, FileHeader header) throws IOException {
        if (header == null) throw new IOException("Entry not found");
        if (!header.isEncrypted()) return null;

        List<String> candidates = new ArrayList<>();
        String memo = remembered(zip);
        if (memo != null) candidates.add(memo);
        candidates.addAll(ArchivePasswordStore.list(context));
        for (String password : candidates) {
            if (tryPassword(zf, header, password)) {
                remember(zip, password);
                return password;
            }
        }

        while (true) {
            String password = prompt(context);
            if (password == null || password.isEmpty()) return null;
            if (tryPassword(zf, header, password)) {
                remember(zip, password);
                return password;
            }
            if (context instanceof android.app.Activity activity) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Extensions.showMessage(activity, R.string.wrong_password_or_corrupt));
            }
        }
    }

    private static boolean tryPassword(ZipFile zf, FileHeader header, String password) {
        try {
            zf.setPassword(password.toCharArray());
            try (InputStream in = zf.getInputStream(header)) {
                byte[] buffer = new byte[8192];
                while (in.read(buffer) != -1) {
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String prompt(Context context) {
        String[] out = {null};
        CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (context instanceof android.app.Activity activity
                    && (activity.isFinishing() || activity.isDestroyed())) {
                latch.countDown();
                return;
            }
            EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint(R.string.enter_archive_password);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.archive_password_needed)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        CharSequence text = input.getText();
                        out[0] = text == null ? "" : text.toString();
                    })
                    .setOnDismissListener(dialog -> latch.countDown())
                    .show();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out[0];
    }
}
