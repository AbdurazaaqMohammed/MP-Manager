package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Writes a folder out as a text listing.
 *
 * <p>Useful for checking what an extraction produced, or what a batch operation
 * touched, without scrolling a file manager. Tab separated so it opens cleanly
 * in a spreadsheet, one line per entry with size and modified time, preceded by
 * a short summary.
 */
public final class FileListExporter {

    private FileListExporter() {
    }

    /** Totals from a listing, reported in the file header. */
    public static final class Summary {
        public int files;
        public int folders;
        public long totalBytes;

        public int entries() {
            return files + folders;
        }
    }

    /**
     * Lists {@code root} into {@code out}.
     *
     * @param recursive walk into subfolders.
     * @return what was written.
     */
    public static Summary export(File root, File out, boolean recursive) throws IOException {
        if (!root.exists()) throw new IOException("Missing: " + root);
        Summary summary = new Summary();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            w.write("# Listing of " + root.getAbsolutePath() + "\n");
            w.write("# Generated " + fmt.format(new Date()) + "\n");
            w.write("# Mode: " + (recursive ? "recursive" : "top level only") + "\n\n");
            w.write("TYPE\tSIZE\tMODIFIED\tPATH\n");
            list(root, root, recursive, fmt, summary, w);
            w.write("\n# " + summary.files + " files, " + summary.folders + " folders, "
                    + summary.totalBytes + " bytes\n");
        }
        return summary;
    }

    private static void list(File dir, File root, boolean recursive, SimpleDateFormat fmt,
                             Summary summary, Writer w) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        // Folders first then files, each alphabetical: reads like a listing
        // rather than an arbitrary order.
        Arrays.sort(kids, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : kids) {
            if (f.isDirectory()) {
                summary.folders++;
            } else {
                summary.files++;
                summary.totalBytes += f.length();
            }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            if (rel == null || rel.isEmpty()) rel = f.getName();
            w.write((f.isDirectory() ? "DIR" : "FILE") + "\t"
                    + (f.isDirectory() ? "" : String.valueOf(f.length())) + "\t"
                    + fmt.format(new Date(f.lastModified())) + "\t"
                    + rel + "\n");
            if (f.isDirectory() && recursive) list(f, root, true, fmt, summary, w);
        }
    }

    /** Default output name next to {@code root}. */
    public static File defaultOutput(File root) {
        String name = root.getName();
        if (name == null || name.isEmpty()) name = "root";
        return new File(root.getParentFile(), name + "_files.txt");
    }
}