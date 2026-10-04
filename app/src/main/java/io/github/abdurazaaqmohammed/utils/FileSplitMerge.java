package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splitting a file into chunks and putting it back together.
 *
 * <p>Pure file handling with no Android dependencies, so the round trip can be
 * tested off-device: split a file, merge the parts, and the result has to be
 * byte-identical to the original.
 *
 * <p>Parts are named {@code name.part001}, {@code name.part002} and so on, which
 * sorts in the right order as plain text and keeps the original extension visible
 * ({@code video.mp4.part001}) so it is obvious what the parts belong to.
 */
public final class FileSplitMerge {

    /** Chunk names carry a fixed width so lexical order is numeric order. */
    private static final String SUFFIX = ".part%03d";
    /** .part%03d stops being unique past this, and sorting breaks down. */
    public static final int MAX_PARTS = 9999;
    private static final Pattern PART = Pattern.compile("^(.*)\\.part(\\d+)$");

    private FileSplitMerge() {
    }

    /** Reports progress as a fraction; return false from the callback to abort. */
    public interface Progress {
        boolean onProgress(long done, long total);
    }

    /**
     * Splits {@code source} into {@code chunkSize}-byte parts inside
     * {@code outDir}.
     *
     * @return the parts in order, or an empty list if nothing was written.
     */
    public static List<File> split(File source, File outDir, long chunkSize, Progress progress)
            throws IOException {
        if (chunkSize <= 0) throw new IOException("Chunk size must be positive");
        if (!source.isFile()) throw new IOException("Not a file: " + source);
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("Cannot create " + outDir);

        String base = source.getName();
        long total = source.length();
        List<File> parts = new ArrayList<>();
        long done = 0;
        int index = 1;

        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            while (true) {
                File part = new File(outDir, base + String.format(Locale.ENGLISH, SUFFIX, index));
                long written = 0;
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(part))) {
                    byte[] buf = new byte[64 * 1024];
                    while (written < chunkSize) {
                        long want = Math.min(buf.length, chunkSize - written);
                        int n = in.read(buf, 0, (int) want);
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        written += n;
                        if (progress != null && !progress.onProgress(done + written, total)) {
                            // Cancelled. Drop every part this call produced: leaving
                            // the earlier ones behind would leave a set that looks
                            // complete and would merge into a silently truncated file.
                            for (File done2 : parts) {
                                //noinspection ResultOfMethodCallIgnored
                                done2.delete();
                            }
                            //noinspection ResultOfMethodCallIgnored
                            part.delete();
                            return new ArrayList<>();
                        }
                    }
                }
                if (written == 0) {
                    // Nothing left to read: an exact multiple of the chunk size
                    // leaves an empty trailing part, which must not be kept.
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    break;
                }
                parts.add(part);
                done += written;
                if (written < chunkSize) break;
                index++;
                if (index > MAX_PARTS) {
                    // Silently stopping here would look like the split finished.
                    for (File earlier : parts) {
                        //noinspection ResultOfMethodCallIgnored
                        earlier.delete();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    throw new IOException("More than " + MAX_PARTS
                            + " parts needed; choose a larger chunk size");
                }
            }
        }
        return parts;
    }

    /**
     * The parts that belong together with {@code anyPart}.
     *
     * <p>Takes any one of the parts and finds its siblings by the name in front of
     * {@code .partNNN}, so long-pressing the first chunk is enough to merge them
     * all. Returns them in numeric order, or an empty list if {@code anyPart} is
     * not part of a set.
     */
    public static List<File> findParts(File anyPart) {
        File dir = anyPart.getParentFile();
        if (dir == null) return new ArrayList<>();
        Matcher m = PART.matcher(anyPart.getName());
        if (!m.matches()) return new ArrayList<>();
        final String prefix = m.group(1);

        File[] siblings = dir.listFiles();
        if (siblings == null) return new ArrayList<>();
        List<File> parts = new ArrayList<>();
        for (File f : siblings) {
            Matcher sm = PART.matcher(f.getName());
            if (sm.matches() && sm.group(1).equals(prefix)) parts.add(f);
        }
        parts.sort((a, b) -> Integer.compare(partNumber(a), partNumber(b)));
        return parts.size() < 2 ? new ArrayList<>() : parts;
    }

    private static int partNumber(File f) {
        Matcher m = PART.matcher(f.getName());
        try {
            return m.matches() ? Integer.parseInt(m.group(2)) : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Concatenates {@code parts} in order into {@code output}.
     *
     * @return bytes written.
     */
    public static long merge(List<File> parts, File output, Progress progress) throws IOException {
        if (parts == null || parts.isEmpty()) throw new IOException("Nothing to merge");
        long total = 0;
        for (File p : parts) total += p.length();

        long done = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] buf = new byte[64 * 1024];
            for (File p : parts) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(p))) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (progress != null && !progress.onProgress(done, total)) {
                            //noinspection ResultOfMethodCallIgnored
                            output.delete();
                            return -1;
                        }
                    }
                }
            }
        }
        return done;
    }

    /** The name a merge should produce: the prefix, without the .partNNN tail. */
    public static String mergedName(String partName) {
        Matcher m = PART.matcher(partName);
        return m.matches() ? m.group(1) : partName;
    }

    /** Chunk sizes offered in the UI. */
    public static long[] chunkSizePresets() {
        return new long[]{1L, 5L, 10L, 25L, 50L, 100L, 250L, 500L};
    }

    /** Human label for a preset in bytes. */
    public static String describeSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return (bytes / (1024L * 1024 * 1024)) + " GB";
        if (bytes >= 1024L * 1024) return (bytes / (1024L * 1024)) + " MB";
        return (bytes / 1024) + " KB";
    }

    static List<File> partsOf(File... files) {
        return new ArrayList<>(Arrays.asList(files));
    }
}