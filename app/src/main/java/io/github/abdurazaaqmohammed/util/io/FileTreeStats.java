package io.github.abdurazaaqmohammed.util.io;

import io.github.abdurazaaqmohammed.privilege.internal.ShizukuFile;

import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.util.io.DirectoryStats;
import io.github.abdurazaaqmohammed.util.io.SafFile;
import io.github.abdurazaaqmohammed.util.io.TransferCancelledException;
import io.github.abdurazaaqmohammed.util.io.TransferControl;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.File;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/** One bounded, cancellable walk for properties and delete totals; never follows symlinks. */
public final class FileTreeStats {
    private FileTreeStats() {}

    public static DirectoryStats read(Context context, File root, TransferControl control, long timeoutMs) {
        if (root == null) return new DirectoryStats(0, 0, 0, 0, 1);
        if (!(root instanceof SafFile) && PrivilegeManager.Files.active(context) == PrivilegeManager.Backend.ROOT) {
            DirectoryStats nativeStats = PrivilegeManager.Native.stats(context, root.getAbsolutePath(), control, timeoutMs);
            if (nativeStats != null) return nativeStats;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        ArrayDeque<File> pending = new ArrayDeque<>();
        pending.push(root);
        long files = 0, folders = 0, bytes = 0, hidden = 0, errors = 0;
        while (!pending.isEmpty()) {
            if (control != null) control.checkpoint();
            if (Thread.currentThread().isInterrupted()) throw new TransferCancelledException();
            if (System.nanoTime() > deadline) { errors += pending.size(); break; }
            File entry = pending.pop();
            boolean directory, regular;
            long length;
            if (entry instanceof SafFile) {
                directory = entry.isDirectory();
                regular = !directory;
                length = entry.length();
            } else if (entry instanceof ShizukuFile shizuku) {
                directory = shizuku.isDirectory() && !shizuku.isLink();
                regular = shizuku.isFile() && !shizuku.isLink();
                length = shizuku.length();
            } else {
                try {
                    StructStat stat = Os.lstat(entry.getAbsolutePath());
                    directory = OsConstants.S_ISDIR(stat.st_mode);
                    regular = OsConstants.S_ISREG(stat.st_mode);
                    length = stat.st_size;
                } catch (Exception unavailable) {
                    if (PrivilegeManager.Files.active(context) == PrivilegeManager.Backend.SHIZUKU) {
                        directory = PrivilegeManager.Files.isDirectory(context, entry.getAbsolutePath());
                        regular = !directory && PrivilegeManager.Files.isFile(context, entry.getAbsolutePath());
                        if (!directory && !regular) { errors++; continue; }
                        length = regular ? PrivilegeManager.Files.getSize(context, entry.getAbsolutePath()) : 0;
                    } else { errors++; continue; }
                }
            }
            if (entry != root && entry.getName().startsWith(".")) hidden++;
            if (directory) {
                folders++;
                if (virtualFilesystem(entry.getAbsolutePath())) continue;
                File[] children = entry.listFiles();
                if (children == null && PrivilegeManager.Files.active(context) == PrivilegeManager.Backend.SHIZUKU)
                    children = PrivilegeManager.Shizuku.listWithStat(context, entry.getAbsolutePath());
                if (children == null) { errors++; continue; }
                for (File child : children) pending.push(child);
            } else {
                files++;
                if (regular) bytes += Math.max(0, length);
            }
        }
        return new DirectoryStats(files, folders, bytes, hidden, errors);
    }

    public static boolean virtualFilesystem(String path) {
        for (String root : new String[]{"/proc", "/sys", "/dev", "/acct", "/config", "/d", "/linkerconfig", "/data_mirror"})
            if (path.equals(root) || path.startsWith(root + "/")) return true;
        return false;
    }
}
