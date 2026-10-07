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
        return extractWithStoredPasswords(context, archive, destDir, preserveTime,
                manualPassword, null);
    }

    /**
     * Same as above with live progress and cancellation: a cancel stops the
     * attempt loop and reports {@code Result(false, -1, null)}; the caller
     * tells that apart from a wrong password through its own cancel flag.
     */
    public static Result extractWithStoredPasswords(Context context, File archive,
                                                     File destDir, boolean preserveTime,
                                                     char[] manualPassword,
                                                     ArchiveUtil.ExtractProgress progress) {
        List<String> passwords = ArchivePasswordStore.list(context);
        Exception lastError = null;

        for (int i = 0; i < passwords.size(); i++) {
            if (progress != null && progress.isCancelled()) {
                cleanupLeftovers(destDir);
                return new Result(false, -1, null);
            }
            File attemptDir = new File(destDir.getParentFile(),
                    destDir.getName() + ".try" + i);
            try {
                boolean completed = ArchiveUtil.extractWithProgress(archive, attemptDir,
                        preserveTime, passwords.get(i).toCharArray(), progress);
                if (!completed) {
                    // Cancelled mid-attempt: drop the partial folder.
                    deleteDir(attemptDir);
                    cleanupLeftovers(destDir);
                    return new Result(false, -1, null);
                }
                if (attemptDir.isDirectory() && attemptDir.list() != null
                        && attemptDir.list().length > 0) {
                    // Move it into place only now that something came out. If the
                    // rename cannot happen, copy instead of extracting a second
                    // time -- re-extracting into the destination could leave it
                    // half written and destroy the attempt that already succeeded.
                    if (!deleteDir(destDir)) {
                        deleteDir(attemptDir);
                        lastError = new IOException("Cannot clear " + destDir);
                        continue;
                    }
                    if (!attemptDir.renameTo(destDir)) {
                        copyTree(attemptDir, destDir);
                        deleteDir(attemptDir);
                    }
                    // Sweep any folders left by the passwords tried before this
                    // one, then record which password actually worked.
                    cleanupLeftovers(destDir);
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
            if (progress != null && progress.isCancelled()) {
                cleanupLeftovers(destDir);
                return new Result(false, -1, null);
            }
            try {
                boolean completed = ArchiveUtil.extractWithProgress(archive, destDir,
                        preserveTime, manualPassword, progress);
                if (!completed) {
                    cleanupLeftovers(destDir);
                    return new Result(false, -1, null);
                }
                return new Result(true, -1, null);
            } catch (Exception e) {
                lastError = e;
            }
        }
        cleanupLeftovers(destDir);
        return new Result(false, -1, lastError);
    }

    /**
     * Whether the archive looks like it needs a password at all.
     *
     * <p>Probed by opening the header: header-encrypted archives (the7-Zip and
     * rar -hp default) fail without the password, while zip answers directly
     * from its central directory. A plain7z/rar no longer drags every stored
     * password through a full extraction attempt.
     *
     * <p>7z needs a second probe beyond the header: when 7-Zip encrypts with
     * its default <code>-p</code> (no <code>-mhe</code>) the headers stay
     * readable and only the entry content is locked, so the open above
     * succeeds and the real answer comes from reading the first file entry.
     * Damaged archives report false — a broken file is not a password
     * problem, and prompting for one would just send the user in circles.
     */
    public static boolean isEncryptedCandidate(File archive) {
        String n = archive.getName().toLowerCase(java.util.Locale.ROOT);
        try {
            if (n.endsWith(".zip")) {
                try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(archive)) {
                    return zf.isEncrypted();
                }
            }
            if (n.endsWith(".7z")) {
                return is7zLocked(archive);
            }
            if (n.endsWith(".rar")) {
                try (com.github.junrar.Archive rar = new com.github.junrar.Archive(archive)) {
                    return rar.isPasswordProtected();
                } catch (Exception headerLocked) {
                    return true;
                }
            }
        } catch (Exception unreadable) {
            // A damaged central directory: let a plain extract surface the
            // real error instead of asking for a password that cannot help.
            return false;
        }
        return false;
    }

    /**
     * True when the 7z cannot be read without a password, be it through the
     * header (encrypted headers fail on open) or through the content
     * (plain headers, encrypted entry streams fail on the first read).
     */
    private static boolean is7zLocked(File archive) {
        try {
            try (org.apache.commons.compress.archivers.sevenz.SevenZFile f =
                         new org.apache.commons.compress.archivers.sevenz.SevenZFile(archive)) {
                org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
                while ((entry = f.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    try (java.io.InputStream in = f.getInputStream(entry)) {
                        // One small read is enough: the AES stream verifies the
                        // password while producing its first bytes. With a solid
                        // block this only decompresses the first chunk, not the
                        // whole entry.
                        in.read(new byte[64]);
                    }
                    return false;
                }
                // Nothing readable in the archive, so nothing to decrypt either.
                return false;
            }
        } catch (org.apache.commons.compress.PasswordRequiredException locked) {
            return true;
        } catch (Exception damaged) {
            return false;
        }
    }

    /**
     * Removes any leftover .tryN folders beside the destination.
     *
     * <p>An attempt folder is created per password, so an unexpected exception
     * partway through would otherwise leave them next to the real output.
     */
    private static void cleanupLeftovers(File destDir) {
        File parent = destDir.getParentFile();
        if (parent == null) return;
        File[] kids = parent.listFiles();
        if (kids == null) return;
        String prefix = destDir.getName() + ".try";
        for (File k : kids) {
            if (k.getName().startsWith(prefix)) deleteDir(k);
        }
    }

    /** Copies a tree, used when a rename cannot cross the boundary. */
    private static void copyTree(File from, File to) {
        File[] kids = from.listFiles();
        if (kids == null) return;
        if (!to.exists() && !to.mkdirs()) return;
        for (File k : kids) {
            File dest = new File(to, k.getName());
            if (k.isDirectory()) {
                copyTree(k, dest);
            } else {
                try (java.io.InputStream in = new java.io.BufferedInputStream(
                        new java.io.FileInputStream(k));
                     java.io.OutputStream os = new java.io.BufferedOutputStream(
                             new java.io.FileOutputStream(dest))) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** @return true when the path is gone afterwards. */
    private static boolean deleteDir(File f) {
        try {
            if (f == null || !f.exists()) return true;
            boolean ok = true;
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) ok &= deleteDir(k);
            }
            return f.delete() || !f.exists();
        } catch (Exception e) {
            return false;
        }
    }
}