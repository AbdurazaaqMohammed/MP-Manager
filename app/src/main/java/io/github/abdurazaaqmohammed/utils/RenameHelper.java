package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.widget.EditText;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.File;

public final class RenameHelper {
    private RenameHelper() {
    }

    public static void selectBaseName(EditText input, boolean isFile) {
        try {
            String text = input.getText() == null ? "" : input.getText().toString();
            int len = input.length();
            int end = len;
            if (isFile) {
                int dot = text.lastIndexOf('.');
                if (dot > 0 && dot < len) end = dot;
            }
            if (end < 0) end = 0;
            if (end > len) end = len;
            input.setSelection(0, end);
        } catch (Exception ignored) {
            try {
                input.setSelection(input.length());
            } catch (Exception ignored2) {}
        }
    }

    public static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (Exception ignored) {
            return a.equals(b);
        }
    }

    public static boolean renameFile(Context context, File from, File to) {
        if (AccessManager.fileOpsOn(context)) {
            try {
                AccessManager.rename(context, from.getAbsolutePath(), to.getAbsolutePath(), true);
                return true;
            } catch (Exception ignored) {}
        }
        try {
            return from.renameTo(to);
        } catch (Exception e) {
            return false;
        }
    }

    public static void askSwapName(MainActivity activity, DialogUtil dialogUtil,
                                   File src, File dest, String newName, boolean pane1,
                                   Runnable onDone) {
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(activity.rss.getString(R.string.rename_taken_title))
                .setMessage(activity.rss.getString(R.string.rename_taken_msg, dest.getName()))
                .setPositiveButton(activity.rss.getString(R.string.swap_names), (d, w) -> {
                    new Thread(() -> {
                        String err = swapNames(activity, src, dest);
                        activity.handler.post(() -> {
                            if (err == null) {
                                activity.refreshPaneAfterChange(pane1, newName);
                                activity.flashPaneItem(pane1, newName);
                                if (onDone != null) onDone.run();
                            } else {
                                new ErrorUtil(activity).showError(new Exception(err));
                            }
                        });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    private static String swapNames(Context context, File a, File b) {
        File parent = a.getParentFile();
        if (parent == null) return "no parent";
        File tmp = new File(parent, ".swap_" + System.currentTimeMillis());
        if (tmp.exists())
            tmp = new File(parent, ".swap_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 100000));
        String aName = a.getName();
        String bName = b.getName();
        if (!renameFile(context, a, tmp)) return "swap failed at first move";
        if (!renameFile(context, b, new File(parent, aName))) {
            renameFile(context, tmp, a);
            return "swap failed at second move";
        }
        if (!renameFile(context, tmp, new File(parent, bName))) {
            renameFile(context, new File(parent, aName), b);
            renameFile(context, tmp, a);
            return "swap failed at third move";
        }
        return null;
    }
}
