package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tries the stored archive passwords in order.
 *
 * <p>A wrong password makes the archive library throw, which is the signal used
 * here: nothing is written for a failed attempt beyond the empty folder the
 * caller created, so a wrong guess is detectable rather than silently producing a
 * partial extraction.
 *
 * <p>The password that worked is recorded so the password manager can show which
 * one it was.
 */
public final class PasswordedArchive {

    private PasswordedArchive() {
    }

    /** Outcome of an extraction attempt. */
    public static final class Result {
        /** True when the archive was extracted. */
        public final boolean ok;
        /** Index into the stored list that worked, or -1. */
        public final int index;
        /** Set when ok is false. */
        public final Exception error;

        Result(boolean ok, int index, Exception error) {
            this.ok = ok;
            this.index = index;
            this.error = error;
        }
    }

    /**
     * Extracts {@code archive} into {@code destDir}, trying each stored password
     * in order and falling back to {@code manualPassword} last.
     *
     * <p>Each attempt runs into its own folder, so a failed try cannot leave
     * half an archive behind for the next one to trip over.
     */
    public static Result extractWithStoredPasswords(Context context, File archive,
                                                     File destDir, boolean preserveTime,
                                                     char[] manualPassword) {
        List<String> passwords = ArchivePasswordStore.list(context);
        Exception lastError = null;

        for (int i = 0; i < passwords.size(); i++) {
            File attemptDir = new File(destDir.getParentFile(),
                    destDir.getName() + ".try" + i);
            try {
                ArchiveUtil.extract(archive, attemptDir, preserveTime,
                        passwords.get(i).toCharArray());
                if (attemptDir.isDirectory() && attemptDir.list() != null
                        && attemptDir.list().length > 0) {
                    // Only replace the real destination once something came out.
                    deleteDir(destDir);
                    if (!attemptDir.renameTo(destDir)) {
                        ArchiveUtil.extract(archive, destDir, preserveTime,
                                passwords.get(i).toCharArray());
                        deleteDir(attemptDir);
                    }
                    ArchivePasswordStore.recordMatch(context, archive.getName(), i, passwords.size());
                    return new Result(true, i, null);
                }
                deleteDir(attemptDir);
                lastError = new IOException("Wrong password");
            } catch (Exception e) {
                deleteDir(attemptDir);
                lastError = e;
            }
        }

        if (manualPassword != null) {
            try {
                ArchiveUtil.extract(archive, destDir, preserveTime, manualPassword);
                return new Result(true, -1, null);
            } catch (Exception e) {
                lastError = e;
            }
        }
        return new Result(false, -1, lastError);
    }

    /** Whether the archive looks like it needs a password at all. */
    public static boolean isEncryptedCandidate(File archive) {
        String n = archive.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".7z") || n.endsWith(".rar");
    }

    private static void deleteDir(File f) {
        try {
            if (f == null || !f.exists()) return;
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteDir(k);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignored) {
        }
    }
}