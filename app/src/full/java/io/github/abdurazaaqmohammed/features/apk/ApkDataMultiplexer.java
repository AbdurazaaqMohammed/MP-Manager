package io.github.abdurazaaqmohammed.features.apk;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APK data multiplexing, MP-Manager's own implementation.
 *
 * Xposed-patcher outputs keep the original APK inside themselves as a stored
 * entry (the "host" entry, e.g. assets/lspatch/origin.apk). Every outer entry
 * whose name, data, compression method and sizes are identical to an entry
 * inside that host blob duplicates bytes the host already carries. This class
 * drops those duplicated local headers + data segments from the output and
 * repoints their central-directory offsets at the matching local header
 * *inside* the host blob. The host bytes themselves are copied verbatim, so
 * both the host and every redirected entry stay readable by the platform zip
 * parser (the redirected entry reuses the inner local header, which carries
 * the same name, method and sizes).
 *
 * The result must be signed afterwards with a raw-file V2/V3 signer (the
 * vendored NeoApk signer) — any zip re-serialization would undo the layout.
 *
 * Original MP-Manager implementation; only standard java.util.zip structures
 * and manual random access are used.
 */
final class ApkDataMultiplexer {

    private static final int CD_HEADER_SIG = 0x504B0102;
    private static final int EOCD_SIG = 0x504B0506;
    private static final int LFH_SIG = 0x504B0304;
    private static final int ALIGN = 4096;
    private static final int CD_OFFSET_FIELD = 42; // local-header offset inside a CD record

    private static final String[] KNOWN_HOST_ENTRIES = {
            "assets/lspatch/origin.apk",
            "assets/SignatureKiller/origin.apk",
            "assets/npatch/origin.apk",
            "assets/base.apk",
            "assets/origin.apk"
    };

    private ApkDataMultiplexer() {
    }

    static class Entry {
        String name;
        int method;
        long crc;
        long csize;
        long usize;
        int flags;
        long lfhOffset;      // local file header offset in the input
        long dataOffset;     // entry data offset in the input
        int lfhNameLen;      // local header name length
        int lfhExtraLen;     // local header extra length
        long cdOffset;       // central directory record offset in the input
        int cdRecordLen;     // full CD record length (46 + name + extra + comment)
        long newLfhOffset = -1;
        boolean redirected;
        long redirectTarget; // absolute output offset the CD should point at
    }

    /** Detects the host entry: known patcher paths first, then the largest
     *  stored entry whose data parses as a ZIP. Returns null when nothing
     *  suitable exists. */
    static String detectHostEntry(File input) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(input, "r")) {
            List<Entry> entries = parseCentralDirectory(raf);
            Map<String, Entry> byName = byName(entries);
            for (String candidate : KNOWN_HOST_ENTRIES) {
                Entry e = byName.get(candidate);
                if (e != null && e.method == 0 && blobParsesAsZip(raf, e)) return candidate;
            }
            Entry best = null;
            long bestSize = 1024 * 1024;
            for (Entry e : entries) {
                if (e.method != 0 || e.csize < bestSize) continue;
                if (e.name.startsWith("META-INF/") || e.name.equals("resources.arsc")) continue;
                if (!blobParsesAsZip(raf, e)) continue;
                best = e;
                bestSize = e.csize;
            }
            return best == null ? null : best.name;
        }
    }

    private static boolean blobParsesAsZip(RandomAccessFile raf, Entry host) {
        try {
            indexBlob(raf, host);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Multiplexes {@code input} into {@code output} against
     * {@code hostEntryName}. Returns bytes saved.
     */
    static long multiplex(File input, File output, String hostEntryName) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(input, "r");
             RandomAccessFile out = new RandomAccessFile(output, "rw")) {
            out.setLength(0);

            List<Entry> entries = parseCentralDirectory(in);
            Entry host = null;
            for (Entry e : entries) {
                if (e.name.equals(hostEntryName)) {
                    host = e;
                    break;
                }
            }
            if (host == null || host.method != 0) {
                throw new IOException("Host entry not found or not stored: " + hostEntryName);
            }
            Map<String, List<Entry>> inner = indexBlob(in, host);

            // Decide which outer entries can be redirected: an identical inner
            // entry must exist (same name, method, sizes, crc), and the entry
            // must not need page alignment — redirected entries inherit the
            // alignment they had inside the original APK.
            for (Entry e : entries) {
                if (e == host) continue;
                List<Entry> candidates = inner.get(e.name);
                if (candidates == null || needsAlignment(e.name)) continue;
                for (Entry c : candidates) {
                    if (c.method == e.method && c.csize == e.csize && c.usize == e.usize
                            && c.crc == e.crc) {
                        e.redirected = true;
                        // inner local header offset relative to the blob data start
                        e.redirectTarget = c.lfhOffset - host.dataOffset;
                        break;
                    }
                }
            }

            // Pass 1: copy non-redirected local headers + data in original CD
            // order, then the host blob verbatim (re-aligning its data start).
            long blobDataStart = -1;
            for (Entry e : entries) {
                if (e.redirected) continue;
                e.newLfhOffset = out.getFilePointer();
                boolean alignable = e.method == 0 && needsAlignment(e.name);
                long dataStart = e.newLfhOffset + 30L + e.lfhNameLen + e.lfhExtraLen;
                long pad = alignable ? (ALIGN - (dataStart % ALIGN)) % ALIGN : 0;
                if (pad != 0 && e.lfhExtraLen + pad > 0xFFFF) pad = 0; // cannot express padding
                // write header (with alignment padding when needed) + data in one pass
                dataStart = writeRealignedLocalHeader(in, out, e, (int) pad);
                if (e == host) blobDataStart = dataStart;
            }
            if (blobDataStart < 0) throw new IOException("Host entry missing from output");
            long blobShift = blobDataStart - host.dataOffset;

            // Align the end of the entry region to 4096: the raw-file V2/V3
            // signer digests the entry region padded to a 4096 boundary, so
            // the padding must physically exist before the central directory.
            long fill = (ALIGN - (out.getFilePointer() % ALIGN)) % ALIGN;
            for (long i = 0; i < fill; i++) out.write(0);

            // Pass 2: central directory — copy each record and patch its
            // local-header offset (redirected entries point inside the blob).
            long cdStart = out.getFilePointer();
            long cdSize = 0;
            for (Entry e : entries) {
                long recordStart = out.getFilePointer();
                copy(in, out, e.cdOffset, e.cdRecordLen);
                long target = e.redirected
                        ? blobDataStart + e.redirectTarget
                        : e.newLfhOffset;
                out.seek(recordStart + CD_OFFSET_FIELD);
                writeIntLe(out, target);
                out.seek(recordStart + e.cdRecordLen);
                cdSize += e.cdRecordLen;
            }
            long cdEnd = out.getFilePointer();

            // EOCD
            if (entries.size() > 0xFFFF || cdStart > 0xFFFFFFFFL) {
                throw new IOException("ZIP64 output not supported");
            }
            out.writeInt(EOCD_SIG);
            writeShortLe(out, 0);              // disk number
            writeShortLe(out, 0);              // CD start disk
            writeShortLe(out, entries.size()); // entries on this disk
            writeShortLe(out, entries.size()); // entries total
            writeIntLe(out, cdSize);
            writeIntLe(out, cdStart);
            writeShortLe(out, 0);              // comment length

            return input.length() - (cdEnd + 22);
        }
    }

    // --- parsing ---------------------------------------------------------

    /** Parses the input's central directory into entries. */
    private static List<Entry> parseCentralDirectory(RandomAccessFile raf) throws IOException {
        long eocdPos = findEocd(raf, raf.length());
        raf.seek(eocdPos + 10);
        int total = readUnsignedShort(raf);
        raf.seek(eocdPos + 16);
        long cdStart = readUnsignedInt(raf);

        List<Entry> entries = new ArrayList<>(total);
        raf.seek(cdStart);
        for (int i = 0; i < total; i++) {
            long cdOffset = raf.getFilePointer();
            if (raf.readInt() != CD_HEADER_SIG) throw new IOException("Bad central directory");
            raf.seek(cdOffset + 8);
            int flags = readUnsignedShort(raf);
            int method = readUnsignedShort(raf);
            raf.seek(cdOffset + 16);
            long crc = readUnsignedInt(raf);
            long csize = readUnsignedInt(raf);
            long usize = readUnsignedInt(raf);
            int nameLenCd = readUnsignedShort(raf);
            int extraLenCd = readUnsignedShort(raf);
            int commentLen = readUnsignedShort(raf);
            raf.seek(cdOffset + 42);
            long lfhOffset = readUnsignedInt(raf);
            byte[] nameBytes = new byte[nameLenCd];
            raf.readFully(nameBytes);

            // local header: read its own name/extra lengths
            raf.seek(lfhOffset);
            if (raf.readInt() != LFH_SIG) {
                throw new IOException("Bad local header for " + new String(nameBytes, StandardCharsets.UTF_8));
            }
            raf.seek(lfhOffset + 26);
            int nameLenLfh = readUnsignedShort(raf);
            int extraLenLfh = readUnsignedShort(raf);

            Entry e = new Entry();
            e.name = new String(nameBytes, StandardCharsets.UTF_8);
            e.method = method;
            e.crc = crc;
            e.csize = csize;
            e.usize = usize;
            e.flags = flags;
            e.lfhOffset = lfhOffset;
            e.dataOffset = lfhOffset + 30 + nameLenLfh + extraLenLfh;
            e.lfhNameLen = nameLenLfh;
            e.lfhExtraLen = extraLenLfh;
            e.cdOffset = cdOffset;
            e.cdRecordLen = 46 + nameLenCd + extraLenCd + commentLen;
            entries.add(e);
            raf.seek(cdOffset + e.cdRecordLen); // next record
        }
        return entries;
    }

    /** Parses the host blob's own central directory (the blob is a full zip). */
    private static Map<String, List<Entry>> indexBlob(RandomAccessFile raf, Entry host) throws IOException {
        long blobStart = host.dataOffset;
        long blobEnd = host.dataOffset + host.csize;
        long eocdPos = findEocd(raf, blobEnd);
        if (eocdPos < blobStart) throw new IOException("Host blob has no EOCD");
        raf.seek(eocdPos + 10);
        int total = readUnsignedShort(raf);
        raf.seek(eocdPos + 16);
        long cdStart = readUnsignedInt(raf); // relative to the blob start

        Map<String, List<Entry>> byName = new LinkedHashMap<>();
        raf.seek(blobStart + cdStart);
        for (int i = 0; i < total; i++) {
            long cdOffset = raf.getFilePointer();
            if (raf.readInt() != CD_HEADER_SIG) throw new IOException("Bad inner central directory");
            raf.seek(cdOffset + 8);
            int flags = readUnsignedShort(raf);
            int method = readUnsignedShort(raf);
            raf.seek(cdOffset + 16);
            long crc = readUnsignedInt(raf);
            long csize = readUnsignedInt(raf);
            long usize = readUnsignedInt(raf);
            int nameLenCd = readUnsignedShort(raf);
            int extraLenCd = readUnsignedShort(raf);
            int commentLen = readUnsignedShort(raf);
            raf.seek(cdOffset + 42);
            long lfhOffset = readUnsignedInt(raf);
            byte[] nameBytes = new byte[nameLenCd];
            raf.readFully(nameBytes);

            long absLfh = blobStart + lfhOffset;
            raf.seek(absLfh + 26);
            int nameLenLfh = readUnsignedShort(raf);
            int extraLenLfh = readUnsignedShort(raf);

            Entry e = new Entry();
            e.name = new String(nameBytes, StandardCharsets.UTF_8);
            e.method = method;
            e.crc = crc;
            e.csize = csize;
            e.usize = usize;
            e.flags = flags;
            e.lfhOffset = absLfh;
            e.dataOffset = absLfh + 30 + nameLenLfh + extraLenLfh;
            e.lfhNameLen = nameLenLfh;
            e.lfhExtraLen = extraLenLfh;
            byName.computeIfAbsent(e.name, k -> new ArrayList<>()).add(e);
            raf.seek(cdOffset + 46L + nameLenCd + extraLenCd + commentLen); // next record
        }
        return byName;
    }

    /** Finds the EOCD signature scanning backwards from {@code limit}. */
    private static long findEocd(RandomAccessFile raf, long limit) throws IOException {
        long window = Math.min(limit, 22L + 0xFFFF);
        byte[] buf = new byte[(int) window];
        raf.seek(limit - window);
        raf.readFully(buf);
        for (int i = buf.length - 22; i >= 0; i--) {
            if (buf[i] == 0x50 && buf[i + 1] == 0x4b && buf[i + 2] == 0x05 && buf[i + 3] == 0x06) {
                return limit - window + i;
            }
        }
        throw new EOFException("EOCD not found");
    }

    /** True for entries whose data must stay page-aligned in an installed APK. */
    private static boolean needsAlignment(String name) {
        return name.equals("resources.arsc")
                || (name.startsWith("lib/") && name.endsWith(".so"))
                || (name.startsWith("assets/") && name.endsWith(".so"))
                || name.endsWith("/origin.apk");
    }

    /** Rewrites the host's local header with an enlarged extra field carrying
     *  {@code pad} alignment bytes, then copies the host data verbatim.
     *  Returns the new data start offset. */
    private static long writeRealignedLocalHeader(RandomAccessFile in, RandomAccessFile out,
                                                  Entry host, int pad) throws IOException {
        if (pad > 0 && pad < 4) pad += ALIGN; // the padding record needs 4 bytes minimum
        long newLfh = out.getFilePointer();
        out.writeInt(LFH_SIG);
        copy(in, out, host.lfhOffset + 4, 2);  // version needed
        copy(in, out, host.lfhOffset + 6, 2);  // GP flags
        writeShortLe(out, host.method);
        copy(in, out, host.lfhOffset + 10, 4); // time + date
        writeIntLe(out, host.crc);
        writeIntLe(out, host.csize);
        writeIntLe(out, host.usize);
        writeShortLe(out, host.lfhNameLen);
        writeShortLe(out, host.lfhExtraLen + pad);
        copy(in, out, host.lfhOffset + 30, host.lfhNameLen + host.lfhExtraLen);
        if (pad >= 4) {
            // zipalign-style alignment padding: header id + size + zeros
            int payload = pad - 4;
            writeShortLe(out, 0xD935);
            writeShortLe(out, payload);
            for (int i = 0; i < payload; i++) out.write(0);
        }
        copy(in, out, host.dataOffset, host.csize);
        return newLfh + 30 + host.lfhNameLen + host.lfhExtraLen + pad;
    }

    // --- small helpers ---------------------------------------------------

    private static Map<String, Entry> byName(List<Entry> entries) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry e : entries) map.putIfAbsent(e.name, e);
        return map;
    }

    private static int readUnsignedShort(RandomAccessFile raf) throws IOException {
        int b1 = raf.read();
        int b2 = raf.read();
        if (b2 < 0) throw new EOFException();
        return (b1 & 0xFF) | ((b2 & 0xFF) << 8);
    }

    private static long readUnsignedInt(RandomAccessFile raf) throws IOException {
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        int b4 = raf.read();
        if (b4 < 0) throw new EOFException();
        return (b1 & 0xFFL) | ((b2 & 0xFFL) << 8) | ((b3 & 0xFFL) << 16) | ((b4 & 0xFFL) << 24);
    }

    private static void writeShortLe(RandomAccessFile out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeIntLe(RandomAccessFile out, long v) throws IOException {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 24) & 0xFF));
    }

    private static void copy(RandomAccessFile in, RandomAccessFile out, long srcPos, long length) throws IOException {
        byte[] buf = new byte[65536];
        in.seek(srcPos);
        long remaining = length;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) throw new EOFException();
            out.write(buf, 0, n);
            remaining -= n;
        }
    }
}
