package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Creating a symbolic link to a file or folder.
 *
 * <p>A real symlink needs a shell, so this goes through whichever backend the app
 * already has enabled (root or Shizuku) and asks it to run {@code ln -s}. Where
 * no shell is available the caller is expected to fall back to an in-app entry
 * rather than pretend the link exists -- {@link #canCreateSymlink} says which
 * case applies before anything is attempted.
 *
 * <p>Both paths are quoted before being handed to the shell: file names on Android
 * routinely contain spaces and parentheses from downloads, and an unquoted path
 * would either fail or, worse, be split into the wrong arguments.
 */
public final class SymlinkTool {

    private SymlinkTool() {
    }

    /** How a link attempt ended. */
    public enum Outcome {
        CREATED,
        /** No shell backend, so nothing was created. */
        NO_PERMISSION,
        /** The link name is already taken. */
        EXISTS,
        /** The shell refused, or the filesystem does not support links. */
        FAILED
    }

    /** Whether a real symlink can be attempted right now. */
    public static boolean canCreateSymlink(Context context) {
        return AccessManager.active(context) != AccessManager.Backend.NONE;
    }

    /**
     * Creates a symlink at {@code link} pointing at {@code target}.
     *
     * <p>Paths are resolved first: a link pointing at a relative target breaks as
     * soon as anything moves, and the target usually is meant to be absolute.
     */
    public static Outcome create(Context context, File target, File link) {
        if (target == null || link == null) return Outcome.FAILED;
        if (!target.exists()) return Outcome.FAILED;
        if (link.exists()) return Outcome.EXISTS;
        if (!canCreateSymlink(context)) return Outcome.NO_PERMISSION;

        String t = quote(target.getAbsolutePath());
        String l = quote(link.getAbsolutePath());
        // -n so an existing link is not clobbered, -f so a stale broken link is.
        String cmd = "ln -snf " + t + " " + l + " 2>&1";
        RootManager.ShellResult r = AccessManager.execute(context, cmd, 10);
        if (r == null) return Outcome.FAILED;
        if (r.exitCode() == 0 && link.exists()) return Outcome.CREATED;
        // Some shells report success oddly; trust the filesystem where we can.
        return Outcome.FAILED;
    }

    /**
     * Checks with the shell whether {@code path} is a link, for callers that have
     * a context. Returns false when it cannot tell.
     */
    public static boolean isSymlink(Context context, File f) {
        if (!canCreateSymlink(context)) return false;
        String p = quote(f.getAbsolutePath());
        RootManager.ShellResult r = AccessManager.execute(context,
                "if [ -L " + p + " ]; then echo yes; else echo no; fi", 10);
        if (r == null || r.exitCode() != 0) return false;
        return r.output().toLowerCase(Locale.ENGLISH).trim().startsWith("yes");
    }

    /** Single-quote a path for the shell, escaping any embedded quote. */
    static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}