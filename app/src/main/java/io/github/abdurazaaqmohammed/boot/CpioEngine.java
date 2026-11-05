package io.github.abdurazaaqmohammed.boot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java CPIO SVR4 Archive extractor and packager.
 * Supports tree-manifest based reconstruction with exact modes and orders.
 */
public class CpioEngine {

    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    public static List<RamdiskManifest.Entry> extract(byte[] cpioData, File outDir, ProgressCallback cb) throws IOException {
        return extractStream(new java.io.ByteArrayInputStream(cpioData), cpioData.length, outDir, cb);
    }

    public static List<RamdiskManifest.Entry> extractFile(File cpioFile, File outDir, ProgressCallback cb) throws IOException {
        try (FileInputStream fis = new FileInputStream(cpioFile)) {
            return extractStream(fis, cpioFile.length(), outDir, cb);
        }
    }

    public static List<RamdiskManifest.Entry> extractStream(java.io.InputStream in, long totalLen, File outDir, ProgressCallback cb) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();

        List<RamdiskManifest.Entry> manifestEntries = new ArrayList<>();
        byte[] hdr = new byte[110];
        byte[] buf = new byte[65536];
        long bytesProcessed = 0;

        while (true) {
            int readHdr = readFully(in, hdr, 0, 110);
            if (readHdr < 110) break;
            bytesProcessed += 110;

            String magic = new String(hdr, 0, 6, StandardCharsets.US_ASCII);
            if (!"070701".equals(magic) && !"070702".equals(magic)) {
                break;
            }

            int ino = (int) Long.parseLong(new String(hdr, 6, 8, StandardCharsets.US_ASCII), 16);
            int mode = (int) Long.parseLong(new String(hdr, 14, 8, StandardCharsets.US_ASCII), 16);
            int uid = (int) Long.parseLong(new String(hdr, 22, 8, StandardCharsets.US_ASCII), 16);
            int gid = (int) Long.parseLong(new String(hdr, 30, 8, StandardCharsets.US_ASCII), 16);
            int nlink = (int) Long.parseLong(new String(hdr, 38, 8, StandardCharsets.US_ASCII), 16);
            int mtime = (int) Long.parseLong(new String(hdr, 46, 8, StandardCharsets.US_ASCII), 16);
            long filesize = Long.parseLong(new String(hdr, 54, 8, StandardCharsets.US_ASCII), 16);
            int namesize = (int) Long.parseLong(new String(hdr, 94, 8, StandardCharsets.US_ASCII), 16);

            byte[] nameBytes = new byte[namesize];
            readFully(in, nameBytes, 0, namesize);
            bytesProcessed += namesize;

            String name = new String(nameBytes, 0, namesize > 0 && nameBytes[namesize - 1] == 0 ? namesize - 1 : namesize, StandardCharsets.UTF_8);

            // Header padding to 4 bytes
            int rem = (110 + namesize) % 4;
            if (rem != 0) {
                int pad = 4 - rem;
                readFully(in, new byte[pad], 0, pad);
                bytesProcessed += pad;
            }

            if ("TRAILER!!!".equals(name)) {
                break;
            }

            File target = new File(outDir, name);
            boolean isDir = (mode & 0170000) == 0040000;
            boolean isSymlink = (mode & 0170000) == 0120000;

            String entryType = "file";
            String symTarget = null;

            if (isDir) {
                target.mkdirs();
                entryType = "dir";
                skipBytes(in, filesize);
                bytesProcessed += filesize;
            } else if (isSymlink) {
                entryType = "symlink";
                byte[] symContent = new byte[(int) filesize];
                readFully(in, symContent, 0, (int) filesize);
                bytesProcessed += filesize;
                symTarget = new String(symContent, StandardCharsets.UTF_8);

                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                File symDesc = new File(outDir, name + ".symlink.txt");
                try (FileOutputStream fos = new FileOutputStream(symDesc)) {
                    fos.write(("Symlink to: " + symTarget + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (FileOutputStream fos = new FileOutputStream(target);
                     java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(fos, 65536)) {
                    long remaining = filesize;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int r = in.read(buf, 0, toRead);
                        if (r == -1) break;
                        bos.write(buf, 0, r);
                        remaining -= r;
                        bytesProcessed += r;
                    }
                    bos.flush();
                }
            }

            int dataRem = (int) (filesize % 4);
            if (dataRem != 0) {
                int pad = 4 - dataRem;
                readFully(in, new byte[pad], 0, pad);
                bytesProcessed += pad;
            }

            manifestEntries.add(new RamdiskManifest.Entry(name, mode, uid, gid, nlink, mtime, filesize, entryType, symTarget));

            if (cb != null && totalLen > 0) {
                int percent = (int) Math.min(100, (bytesProcessed * 100) / totalLen);
                cb.onProgress("Extracting: " + name, percent);
            }
        }

        return manifestEntries;
    }

    public static byte[] packFromFolder(File rootDir, List<RamdiskManifest.Entry> originalManifest) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        packFolderToStream(rootDir, baos, originalManifest);
        return baos.toByteArray();
    }

    public static void packFolderToFile(File rootDir, File outCpioFile, List<RamdiskManifest.Entry> originalManifest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outCpioFile);
             java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(fos, 65536)) {
            packFolderToStream(rootDir, bos, originalManifest);
            bos.flush();
        }
    }

    public static void packFolderToStream(File rootDir, java.io.OutputStream out, List<RamdiskManifest.Entry> originalManifest) throws IOException {
        Map<String, RamdiskManifest.Entry> manifestMap = new HashMap<>();
        if (originalManifest != null) {
            for (RamdiskManifest.Entry e : originalManifest) {
                manifestMap.put(e.name, e);
            }
        }

        List<FileEntry> entries = new ArrayList<>();
        collectEntries(rootDir, "", entries);

        Collections.sort(entries, (a, b) -> a.relPath.compareTo(b.relPath));

        int ino = 1;
        for (FileEntry fe : entries) {
            RamdiskManifest.Entry orig = manifestMap.get(fe.relPath);

            int mode = orig != null ? orig.mode : (fe.isDir ? 0040755 : 0100644);
            int uid = orig != null ? orig.uid : 0;
            int gid = orig != null ? orig.gid : 0;
            int nlink = orig != null ? orig.nlink : (fe.isDir ? 2 : 1);
            int mtime = orig != null ? orig.mtime : (int) (fe.file.lastModified() / 1000);
            long size = fe.isDir ? 0 : fe.file.length();

            if (fe.relPath.endsWith(".symlink.txt")) {
                continue;
            }

            File symlinkMarker = new File(rootDir, fe.relPath + ".symlink.txt");
            boolean isSymlink = symlinkMarker.exists();
            byte[] symlinkBytes = null;
            if (isSymlink) {
                mode = 0120777;
                String line = new String(readFile(symlinkMarker), StandardCharsets.UTF_8).trim();
                if (line.startsWith("Symlink to: ")) {
                    line = line.substring(12).trim();
                }
                symlinkBytes = line.getBytes(StandardCharsets.UTF_8);
                size = symlinkBytes.length;
            }

            writeEntry(out, ino++, mode, uid, gid, nlink, mtime, size, fe.relPath, fe.file, isSymlink ? symlinkBytes : null);
        }

        writeTrailer(out, ino++);
    }

    private static void writeEntry(java.io.OutputStream out, int ino, int mode, int uid, int gid, int nlink, int mtime,
                                   long size, String name, File file, byte[] symlinkData) throws IOException {
        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        int namesize = nameBytes.length;

        StringBuilder sb = new StringBuilder();
        sb.append("070701");
        sb.append(String.format("%08X", ino));
        sb.append(String.format("%08X", mode));
        sb.append(String.format("%08X", uid));
        sb.append(String.format("%08X", gid));
        sb.append(String.format("%08X", nlink));
        sb.append(String.format("%08X", mtime));
        sb.append(String.format("%08X", size));
        sb.append(String.format("%08X", 0)); // devmajor
        sb.append(String.format("%08X", 0)); // devminor
        sb.append(String.format("%08X", 0)); // rdevmajor
        sb.append(String.format("%08X", 0)); // rdevminor
        sb.append(String.format("%08X", namesize));
        sb.append(String.format("%08X", 0)); // check

        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int rem = (110 + namesize) % 4;
        if (rem != 0) {
            for (int i = 0; i < (4 - rem); i++) out.write(0);
        }

        if (symlinkData != null) {
            out.write(symlinkData);
        } else if (size > 0 && file != null && file.isFile()) {
            byte[] buf = new byte[65536];
            try (FileInputStream fis = new FileInputStream(file)) {
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int r = fis.read(buf, 0, toRead);
                    if (r == -1) break;
                    out.write(buf, 0, r);
                    remaining -= r;
                }
            }
        }

        int dataRem = (int) (size % 4);
        if (dataRem != 0) {
            for (int i = 0; i < (4 - dataRem); i++) out.write(0);
        }
    }

    private static void writeTrailer(java.io.OutputStream out, int ino) throws IOException {
        writeEntry(out, ino, 0, 0, 0, 1, 0, 0, "TRAILER!!!", null, null);
    }

    private static void collectEntries(File dir, String prefix, List<FileEntry> entries) {
        File[] list = dir.listFiles();
        if (list == null) return;

        for (File f : list) {
            String rel = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) {
                entries.add(new FileEntry(rel, f, true));
                collectEntries(f, rel, entries);
            } else {
                entries.add(new FileEntry(rel, f, false));
            }
        }
    }

    private static class FileEntry {
        String relPath;
        File file;
        boolean isDir;

        FileEntry(String relPath, File file, boolean isDir) {
            this.relPath = relPath;
            this.file = file;
            this.isDir = isDir;
        }
    }

    private static int readFully(java.io.InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(b, off + total, len - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }

    private static void skipBytes(java.io.InputStream in, long count) throws IOException {
        long rem = count;
        while (rem > 0) {
            long s = in.skip(rem);
            if (s <= 0) {
                if (in.read() == -1) break;
                rem--;
            } else {
                rem -= s;
            }
        }
    }

    private static byte[] readFile(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int read = 0;
            while (read < b.length) {
                int r = fis.read(b, read, b.length - read);
                if (r == -1) break;
                read += r;
            }
        }
        return b;
    }
}
