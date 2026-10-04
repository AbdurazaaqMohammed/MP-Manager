package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.IOException;

/**
 * Swapping the names of two files.
 *
 * <p>The name travels to the other item's folder: picking {@code dir1/a.txt} and
 * {@code dir2/b.txt} leaves {@code dir1/b.txt} and {@code dir2/a.txt}. Swapping
 * contents instead would leave the names where they were, which is a different
 * operation and not the one asked for.
 *
 * <p>When both live in the same folder the two target paths are the ones just
 * vacated, so the swap goes through a temporary name; otherwise each item can be
 * renamed directly.
 */
public final class FileNameSwap {

    private FileNameSwap() {
    }

    /**
     * Swaps the names of {@code a} and {@code b}.
     *
     * @throws IOException if anything could not be renamed.
     */
    public static void swap(File a, File b) throws IOException {
        if (a == null || b == null) throw new IOException("Nothing to swap");
        if (!a.exists()) throw new IOException("Missing: " + a);
        if (!b.exists()) throw new IOException("Missing: " + b);
        if (a.equals(b)) throw new IOException("Same file selected twice");

        String nameA = a.getName();
        String nameB = b.getName();
        if (nameA.equals(nameB)) {
            throw new IOException("Both are called " + nameA + " in different folders");
        }

        File dirA = a.getParentFile();
        File dirB = b.getParentFile();
        if (dirA == null || dirB == null) throw new IOException("Cannot work out the folders");

        if (!dirA.equals(dirB)) {
            // Different folders: each target name is free, so rename straight.
            File aTo = new File(dirB, nameA);
            File bTo = new File(dirA, nameB);
            if (!move(a, aTo)) throw new IOException("Cannot rename " + nameA);
            if (!move(b, bTo)) {
                move(aTo, a); // put it back rather than leave half a swap
                throw new IOException("Cannot rename " + nameB);
            }
            return;
        }

        // Same folder: b's name is taken until a moves aside.
        File holder = freeTempName(a);
        if (holder == null) throw new IOException("Could not find a free temporary name");
        if (!move(a, holder)) throw new IOException("Cannot rename " + nameA);
        File aTo = new File(dirB, nameA);
        if (!move(b, aTo)) {
            move(holder, a);
            throw new IOException("Cannot rename " + nameB + " to " + nameA);
        }
        File bTo = new File(dirA, nameB);
        if (!move(holder, bTo)) {
            throw new IOException("Swapped " + nameB + " but could not put " + nameA
                    + " back; it is still at " + holder.getName());
        }
    }

    /**
     * Renames, falling back to a copy when the platform refuses.
     *
     * <p>A directory cannot always be moved between folders with renameTo -- the
     * call simply fails -- so a swap of two folders in different places needs the
     * contents moved too.
     */
    private static boolean move(File from, File to) {
        if (from.renameTo(to)) return true;
        if (!from.isDirectory()) return false;
        return copyTree(from, to) && deleteTree(from);
    }

    private static boolean copyTree(File from, File to) {
        File[] kids = from.listFiles();
        if (kids == null) return false;
        if (!to.exists() && !to.mkdirs()) return false;
        for (File k : kids) {
            File dest = new File(to, k.getName());
            if (k.isDirectory()) {
                if (!copyTree(k, dest)) return false;
            } else {
                try (java.io.InputStream in = new java.io.BufferedInputStream(
                        new java.io.FileInputStream(k));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(
                             new java.io.FileOutputStream(dest))) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (!deleteTree(k)) return false;
            }
        }
        return f.delete();
    }

    /** A name in {@code dir} that is not taken, based on {@code like}. */
    private static File freeTempName(File like) {
        File dir = like.getParentFile();
        if (dir == null) return null;
        String base = like.getName() + ".swap-tmp";
        File candidate = new File(dir, base);
        int n = 1;
        while (candidate.exists() && n < 1000) {
            candidate = new File(dir, base + "." + n);
            n++;
        }
        return candidate.exists() ? null : candidate;
    }
}