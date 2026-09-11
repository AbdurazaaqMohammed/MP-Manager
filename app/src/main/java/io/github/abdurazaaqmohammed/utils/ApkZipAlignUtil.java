package io.github.abdurazaaqmohammed.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class ApkZipAlignUtil {

    private ApkZipAlignUtil() {
    }

    private static final int ALIGN_RES_ARSC = 4;
    /** Extra-field header id used for alignment padding (same as zipalign). */
    private static final int ALIGN_EXTRA_ID = 0xd935;

    private static final int SIG_LOCAL = 0x04034b50;
    private static final int SIG_CENTRAL = 0x02014b50;
    private static final int SIG_EOCD = 0x06054b50;
    private static final int SIG_DATA_DESC = 0x08074b50;
    private static final int FLAG_DESCRIPTOR = 0x08;
    private static final int FLAG_UTF8 = 0x800;

    public static boolean mustStore(String name) {
        if (name == null) return false;
        return name.equals("AndroidManifest.xml")
                || name.equals("resources.arsc")
                || (name.startsWith("res/") && !name.endsWith(".xml"));
    }

    public static boolean ensureInstallable(File apk) throws IOException {
        String issue = installIssue(apk);
        if (issue == null) return false;
        File tmp = new File(apk.getParentFile(), apk.getName() + ".align" + System.nanoTime() + ".tmp");
        try {
            rebuild(apk, tmp);
            String stillBad = installIssue(tmp);
            if (stillBad != null) {
                throw new IOException("Could not make " + apk.getName() + " installable: " + stillBad);
            }
            replaceFile(tmp, apk);
            return true;
        } finally {
            try {
                tmp.delete();
            } catch (Exception ignored) {
            }
        }
    }

    public static String installIssue(File apk) {
        try {
            List<CDEntry> entries = readCentralDirectory(apk);
            try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
                for (CDEntry e : entries) {
                    String name = e.name();
                    if (e.isDir) continue;
                    if (mustStore(name) && e.method != 0) {
                        return name + " is compressed, must be STORED";
                    }
                    if (name.equals("resources.arsc")) {
                        if (e.method != 0) return "resources.arsc is compressed, must be STORED";
                        LocalHeader lh = readLocalHeader(raf, e.localOffset);
                        long dataOff = e.localOffset + lh.headerLen;
                        if (dataOff % ALIGN_RES_ARSC != 0) {
                            return "resources.arsc data at offset " + dataOff
                                    + " is not 4-byte aligned";
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return "unreadable zip: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // rebuild
    // ------------------------------------------------------------------

    private static void rebuild(File src, File dst) throws IOException {
        List<CDEntry> entries = readCentralDirectory(src);
        try (RandomAccessFile raf = new RandomAccessFile(src, "r");
             CountingOut out = new CountingOut(new FileOutputStream(dst))) {
            List<CDEntry> outEntries = new ArrayList<>(entries.size());
            for (CDEntry e : entries) {
                String name = e.name();
                if (e.isDir) {
                    long off = out.pos;
                    copyBytes(raf, e.localOffset, out, e.localHeaderLen(raf));
                    CDEntry n = e.copy();
                    n.localOffset = off;
                    outEntries.add(n);
                    continue;
                }
                boolean wantStore = mustStore(name);
                boolean isStored = e.method == 0;
                if (wantStore && !isStored) {
                    outEntries.add(convertToStored(raf, e, out));
                } else if (name.equals("resources.arsc")) {
                    outEntries.add(rewriteStored(raf, e, out));
                } else {
                    outEntries.add(rawCopyEntry(raf, e, out));
                }
            }
            long cdOffset = out.pos;
            writeCentralDirectory(out, outEntries);
            writeEndOfCentralDirectory(out, outEntries, cdOffset, readArchiveComment(src));
        }
    }

    private static CDEntry rawCopyEntry(RandomAccessFile raf, CDEntry e, CountingOut out) throws IOException {
        LocalHeader lh = readLocalHeader(raf, e.localOffset);
        long off = out.pos;
        copyBytes(raf, e.localOffset, out, lh.headerLen);
        long dataLen = e.compSize;
        copyBytes(raf, e.localOffset + lh.headerLen, out, dataLen);
        if ((lh.flags & FLAG_DESCRIPTOR) != 0) {
            copyBytes(raf, e.localOffset + lh.headerLen + dataLen, out, descriptorLen(raf, e.localOffset + lh.headerLen + dataLen));
        }
        CDEntry n = e.copy();
        n.localOffset = off;
        return n;
    }

    private static CDEntry rewriteStored(RandomAccessFile raf, CDEntry e, CountingOut out) throws IOException {
        LocalHeader lh = readLocalHeader(raf, e.localOffset);
        if ((lh.flags & FLAG_DESCRIPTOR) != 0) {
            // Materialize: recompute from central directory values instead.
            return materializeStored(e, lh, out, streamOf(raf, e, lh));
        }
        byte[] baseExtra = stripAlignBlocks(lh.extra);
        long headerLen0 = 30L + lh.name.length + baseExtra.length;
        long dataOff0 = out.pos + headerLen0;
        long pad = (ALIGN_RES_ARSC - (dataOff0 % ALIGN_RES_ARSC)) % ALIGN_RES_ARSC;
        byte[] extra = appendAlignBlock(baseExtra, pad);
        int flags = lh.flags & ~FLAG_DESCRIPTOR;
        long off = out.pos;
        writeLocalHeader(out, e, 0, lh.crc, e.compSize, e.compSize, flags, lh.name, extra);
        copyBytes(raf, e.localOffset + lh.headerLen, out, e.compSize);
        CDEntry n = e.copy();
        n.localOffset = off;
        n.flags = flags;
        n.extra = extra;
        return n;
    }

    private static CDEntry convertToStored(RandomAccessFile raf, CDEntry e, CountingOut out) throws IOException {
        LocalHeader lh = readLocalHeader(raf, e.localOffset);
        CRC32 crc = new CRC32();
        long size = 0;
        try (InputStream in = streamOf(raf, e, lh)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                crc.update(buf, 0, n);
                size += n;
            }
        }
        if (size > 0xFFFFFFFFL) throw new IOException(e.name() + " too large for STORED");
        byte[] extra = new byte[0];
        long headerLen0 = 30L + lh.name.length;
        long dataOff0 = out.pos + headerLen0;
        long pad = 0;
        if (e.name().equals("resources.arsc")) {
            pad = (ALIGN_RES_ARSC - (dataOff0 % ALIGN_RES_ARSC)) % ALIGN_RES_ARSC;
        }
        extra = appendAlignBlock(extra, pad);
        int flags = (lh.flags & ~FLAG_DESCRIPTOR) | FLAG_UTF8;
        long off = out.pos;
        writeLocalHeader(out, e, 0, (int) crc.getValue(), size, size, flags, lh.name, extra);
        try (InputStream in = streamOf(raf, e, lh)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        CDEntry n = e.copy();
        n.localOffset = off;
        n.method = 0;
        n.needVer = 10;
        n.flags = flags;
        n.crc = (int) crc.getValue();
        n.compSize = size;
        n.uncompSize = size;
        n.extra = extra;
        return n;
    }

    private static CDEntry materializeStored(CDEntry e, LocalHeader lh,
                                             CountingOut out, InputStream data) throws IOException {
        CRC32 crc = new CRC32();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[65536];
        int n;
        while ((n = data.read(tmp)) != -1) {
            crc.update(tmp, 0, n);
            buf.write(tmp, 0, n);
        }
        data.close();
        byte[] bytes = buf.toByteArray();
        byte[] baseExtra = stripAlignBlocks(lh.extra);
        long headerLen0 = 30L + lh.name.length + baseExtra.length;
        long dataOff0 = out.pos + headerLen0;
        long pad = (ALIGN_RES_ARSC - (dataOff0 % ALIGN_RES_ARSC)) % ALIGN_RES_ARSC;
        byte[] extra = appendAlignBlock(baseExtra, pad);
        int flags = (lh.flags & ~FLAG_DESCRIPTOR) | FLAG_UTF8;
        long off = out.pos;
        writeLocalHeader(out, e, 0, (int) crc.getValue(), bytes.length, bytes.length, flags, lh.name, extra);
        out.write(bytes);
        CDEntry out_e = e.copy();
        out_e.localOffset = off;
        out_e.method = 0;
        out_e.needVer = 10;
        out_e.flags = flags;
        out_e.crc = (int) crc.getValue();
        out_e.compSize = bytes.length;
        out_e.uncompSize = bytes.length;
        out_e.extra = extra;
        return out_e;
    }

    private static InputStream streamOf(RandomAccessFile raf, CDEntry e, LocalHeader lh) throws IOException {
        long dataPos = e.localOffset + lh.headerLen;
        if (lh.method == 0) {
            return new BoundedRafStream(raf, dataPos, e.compSize);
        }
        if (lh.method == 8) {
            return new InflaterInputStream(new BoundedRafStream(raf, dataPos, e.compSize), new Inflater(true));
        }
        throw new IOException(e.name() + ": unsupported method " + lh.method);
    }

    private static final class CDEntry {
        int madeBy, needVer, flags, method, time, date;
        int crc;
        long compSize, uncompSize, localOffset;
        int internalAttr;
        long externalAttr;
        byte[] name, extra, comment;
        boolean isDir;

        String name() {
            return new String(name, ((flags & FLAG_UTF8) != 0) ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII);
        }

        CDEntry copy() {
            CDEntry n = new CDEntry();
            n.madeBy = madeBy;
            n.needVer = needVer;
            n.flags = flags;
            n.method = method;
            n.time = time;
            n.date = date;
            n.crc = crc;
            n.compSize = compSize;
            n.uncompSize = uncompSize;
            n.localOffset = localOffset;
            n.internalAttr = internalAttr;
            n.externalAttr = externalAttr;
            n.name = name;
            n.extra = extra;
            n.comment = comment;
            n.isDir = isDir;
            return n;
        }

        long localHeaderLen(RandomAccessFile raf) throws IOException {
            return readLocalHeader(raf, localOffset).headerLen;
        }
    }

    private static final class LocalHeader {
        int flags, method, time, date;
        int crc;
        long compSize, uncompSize;
        byte[] name, extra;
        long headerLen;
    }

    private static LocalHeader readLocalHeader(RandomAccessFile raf, long off) throws IOException {
        raf.seek(off);
        byte[] h = new byte[30];
        raf.readFully(h);
        if (readU32(h, 0) != SIG_LOCAL) throw new IOException("Bad local header at " + off);
        LocalHeader lh = new LocalHeader();
        lh.flags = readU16(h, 6);
        lh.method = readU16(h, 8);
        lh.time = readU16(h, 10);
        lh.date = readU16(h, 12);
        lh.crc = (int) readU32(h, 14);
        lh.compSize = readU32(h, 18);
        lh.uncompSize = readU32(h, 22);
        int nameLen = readU16(h, 26);
        int extraLen = readU16(h, 28);
        // file position is now exactly off+30 where the name starts
        lh.name = new byte[nameLen];
        raf.readFully(lh.name);
        lh.extra = new byte[extraLen];
        raf.readFully(lh.extra);
        lh.headerLen = 30L + nameLen + extraLen;
        if (lh.method != 0 && lh.method != 8 && !(lh.compSize == 0 && lh.uncompSize == 0)) {
            throw new IOException("Unsupported method " + lh.method);
        }
        return lh;
    }

    private static long descriptorLen(RandomAccessFile raf, long pos) throws IOException {
        raf.seek(pos);
        byte[] b = new byte[4];
        raf.readFully(b);
        return readU32(b, 0) == SIG_DATA_DESC ? 16 : 12;
    }

    private static List<CDEntry> readCentralDirectory(File apk) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
            long fileLen = raf.length();
            long searchStart = Math.max(0, fileLen - (22 + 65535 + 256));
            int searchLen = (int) (fileLen - searchStart);
            byte[] tail = new byte[searchLen];
            raf.seek(searchStart);
            raf.readFully(tail);
            int eocd = -1;
            for (int i = searchLen - 22; i >= 0; i--) {
                if (readU32(tail, i) == SIG_EOCD) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) throw new IOException("EOCD not found (Zip64 unsupported)");
            long cdCount = readU16(tail, eocd + 10);
            long cdSize = readU32(tail, eocd + 12);
            long cdOff = readU32(tail, eocd + 16);
            if (cdCount == 0xFFFF) throw new IOException("Zip64 unsupported");
            List<CDEntry> list = new ArrayList<>((int) Math.min(cdCount, 100000));
            long pos = cdOff;
            for (long i = 0; i < cdCount; i++) {
                raf.seek(pos);
                byte[] h = new byte[46];
                raf.readFully(h);
                if (readU32(h, 0) != SIG_CENTRAL) throw new IOException("Bad central entry at " + pos);
                CDEntry e = new CDEntry();
                e.madeBy = readU16(h, 4);
                e.needVer = readU16(h, 6);
                e.flags = readU16(h, 8);
                e.method = readU16(h, 10);
                e.time = readU16(h, 12);
                e.date = readU16(h, 14);
                e.crc = (int) readU32(h, 16);
                e.compSize = readU32(h, 20);
                e.uncompSize = readU32(h, 24);
                int nameLen = readU16(h, 28);
                int extraLen = readU16(h, 30);
                int commentLen = readU16(h, 32);
                e.internalAttr = readU16(h, 36);
                e.externalAttr = readU32(h, 38);
                e.localOffset = readU32(h, 42);
                e.name = new byte[nameLen];
                raf.readFully(e.name);
                e.extra = new byte[extraLen];
                raf.readFully(e.extra);
                e.comment = new byte[commentLen];
                raf.readFully(e.comment);
                String nm = e.name();
                e.isDir = nm.endsWith("/");
                pos = pos + 46 + nameLen + extraLen + commentLen;
                if (pos > fileLen) throw new IOException("Central directory overruns file");
                list.add(e);
            }
            return list;
        }
    }

    private static byte[] readArchiveComment(File apk) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
            long fileLen = raf.length();
            long searchStart = Math.max(0, fileLen - (22 + 65535 + 256));
            int searchLen = (int) (fileLen - searchStart);
            byte[] tail = new byte[searchLen];
            raf.seek(searchStart);
            raf.readFully(tail);
            for (int i = searchLen - 22; i >= 0; i--) {
                if (readU32(tail, i) == SIG_EOCD) {
                    int commentLen = readU16(tail, i + 20);
                    byte[] comment = new byte[commentLen];
                    System.arraycopy(tail, i + 22, comment, 0, commentLen);
                    return comment;
                }
            }
        }
        return new byte[0];
    }

    private static void writeLocalHeader(CountingOut out, CDEntry e, int method, int crc,
                                         long compSize, long uncompSize, int flags,
                                         byte[] name, byte[] extra) throws IOException {
        if (compSize > 0xFFFFFFFFL || uncompSize > 0xFFFFFFFFL) {
            throw new IOException(e.name() + ": too large (Zip64 unsupported)");
        }
        writeU32(out, SIG_LOCAL);
        writeU16(out, method == 0 ? 10 : 20);
        writeU16(out, flags);
        writeU16(out, method);
        writeU16(out, e.time);
        writeU16(out, e.date);
        writeU32(out, crc & 0xFFFFFFFFL);
        writeU32(out, compSize);
        writeU32(out, uncompSize);
        writeU16(out, name.length);
        writeU16(out, extra.length);
        out.write(name);
        out.write(extra);
    }

    private static void writeCentralDirectory(CountingOut out, List<CDEntry> entries) throws IOException {
        for (CDEntry e : entries) {
            writeU32(out, SIG_CENTRAL);
            writeU16(out, e.madeBy);
            writeU16(out, e.needVer);
            writeU16(out, e.flags);
            writeU16(out, e.method);
            writeU16(out, e.time);
            writeU16(out, e.date);
            writeU32(out, e.crc & 0xFFFFFFFFL);
            writeU32(out, e.compSize);
            writeU32(out, e.uncompSize);
            writeU16(out, e.name.length);
            writeU16(out, e.extra.length);
            writeU16(out, e.comment.length);
            writeU16(out, 0); // disk start
            writeU16(out, e.internalAttr);
            writeU32(out, e.externalAttr);
            writeU32(out, e.localOffset);
            out.write(e.name);
            out.write(e.extra);
            out.write(e.comment);
        }
    }

    private static void writeEndOfCentralDirectory(CountingOut out, List<CDEntry> entries,
                                                   long cdOffset, byte[] comment) throws IOException {
        if (entries.size() > 0xFFFF) throw new IOException("Too many entries (Zip64 unsupported)");
        long cdSize = out.pos - cdOffset;
        writeU32(out, SIG_EOCD);
        writeU16(out, 0);
        writeU16(out, 0);
        writeU16(out, entries.size());
        writeU16(out, entries.size());
        writeU32(out, cdSize);
        writeU32(out, cdOffset);
        writeU16(out, comment.length);
        out.write(comment);
    }

    private static byte[] stripAlignBlocks(byte[] extra) {
        if (extra == null || extra.length < 4) return extra == null ? new byte[0] : extra;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(extra.length);
            int pos = 0;
            while (pos + 4 <= extra.length) {
                int id = readU16(extra, pos);
                int size = readU16(extra, pos + 2);
                if (pos + 4 + size > extra.length) return extra; // malformed: keep as is
                if (id != ALIGN_EXTRA_ID) {
                    bos.write(extra, pos, 4 + size);
                }
                pos += 4 + size;
            }
            if (pos != extra.length) return extra; // trailing garbage: keep as is
            return bos.toByteArray();
        } catch (Exception ex) {
            return extra;
        }
    }

    private static byte[] appendAlignBlock(byte[] baseExtra, long pad) throws IOException {
        if (pad == 0) return baseExtra;
        long ext = pad < 4 ? pad + 4 : pad;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(baseExtra.length + (int) ext);
        bos.write(baseExtra);
        writeU16(bos, ALIGN_EXTRA_ID);
        writeU16(bos, (int) (ext - 4));
        for (long i = 4; i < ext; i++) bos.write(0);
        return bos.toByteArray();
    }

    private static void copyBytes(RandomAccessFile raf, long pos, OutputStream out, long len) throws IOException {
        raf.seek(pos);
        byte[] buf = new byte[65536];
        while (len > 0) {
            int n = raf.read(buf, 0, (int) Math.min(buf.length, len));
            if (n < 0) throw new IOException("Unexpected end of file");
            out.write(buf, 0, n);
            len -= n;
        }
    }

    private static void replaceFile(File tmp, File dst) throws IOException {
        if (dst.delete() || !dst.exists()) {
            if (tmp.renameTo(dst)) return;
        }
        // renameTo fallback: stream copy (Windows rename quirks)
        try (InputStream in = new java.io.FileInputStream(tmp);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        tmp.delete();
    }

    // ------------------------------------------------------------------
    // tiny io helpers
    // ------------------------------------------------------------------

    private static final class CountingOut extends OutputStream {
        final OutputStream wrapped;
        long pos;

        CountingOut(OutputStream wrapped) {
            this.wrapped = wrapped;
        }

        public void write(int b) throws IOException {
            wrapped.write(b);
            pos++;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            wrapped.write(b, off, len);
            pos += len;
        }

        public void flush() throws IOException {
            wrapped.flush();
        }

        public void close() throws IOException {
            wrapped.close();
        }
    }

    private static final class BoundedRafStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;

        BoundedRafStream(RandomAccessFile raf, long pos, long len) throws IOException {
            this.raf = raf;
            raf.seek(pos);
            this.remaining = len;
        }

        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = raf.read();
            if (b < 0) return -1;
            remaining--;
            return b;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = raf.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }

    private static int readU16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long readU32(byte[] b, int off) {
        return ((b[off] & 0xFFL)) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static void writeU16(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeU32(OutputStream out, long v) throws IOException {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >>> 8) & 0xFF));
        out.write((int) ((v >>> 16) & 0xFF));
        out.write((int) ((v >>> 24) & 0xFF));
    }
}
