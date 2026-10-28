package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Zip access that does not depend on zip4j knowing every compression method.
 *
 * zip4j only implements store, deflate and AES-encrypted entries; any archive
 * written by other tools (XZ, Zstandard, bzip2, LZMA) makes its central
 * directory reader abort the whole listing with
 * {@code ZipException: Unknown compression method}. Release packs produced by
 * Magisk-style tooling, LSPosed modules and many busybox based zips are exactly
 * that kind, so the central directory is parsed here directly whenever zip4j
 * cannot handle the archive, and the payloads are decompressed from the
 * matching commons-compress codec.
 */
public final class ZipCompat {

    private static final int METHOD_STORE = 0;
    private static final int METHOD_DEFLATE = 8;
    private static final int METHOD_BZIP2 = 12;
    private static final int METHOD_LZMA = 14;
    private static final int METHOD_ZSTD = 93;
    private static final int METHOD_XZ = 95;

    private static final long SIG_LOCAL = 0x04034b50L;
    private static final long SIG_CENTRAL = 0x02014b50L;
    private static final long SIG_EOCD = 0x06054b50L;
    private static final long SIG_ZIP64_EOCD = 0x06064b50L;
    private static final long SIG_ZIP64_LOCATOR = 0x07064b50L;

    private static final long UINT32_MAX = 0xFFFFFFFFL;
    private static final int UINT16_MAX = 0xFFFF;
    private static final int COPY_BUFFER = 64 * 1024;
    private static final byte[] LZMA_SIZE_UNKNOWN = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private ZipCompat() {
    }

    /**
     * All central-directory entries, as zip4j headers so existing callers keep
     * working. Falls back to a direct central-directory scan when zip4j
     * refuses the archive because of an unknown compression method.
     */
    public static List<FileHeader> listHeaders(File zip) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("Not a file: " + zip);
        try (ZipFile zf = new ZipFile(zip)) {
            return zf.getFileHeaders();
        } catch (Exception zip4jFailure) {
            try {
                return fromCentralDirectory(readCentralDirectory(zip));
            } catch (IOException directFailure) {
                directFailure.addSuppressed(zip4jFailure);
                throw directFailure;
            }
        }
    }

    /** Like {@link #listHeaders} but never throws: returns an empty list. */
    public static List<FileHeader> listHeadersOrEmpty(File zip) {
        try {
            return listHeaders(zip);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Opens one entry for reading. Uses zip4j when it understands the archive,
     * otherwise reads the local header directly and decompresses the payload.
     */
    public static InputStream openEntry(File zip, String entryName) throws IOException {
        if (zip == null || entryName == null) throw new IOException("Missing archive or entry name");
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            FileHeader fh = zf.getFileHeader(entryName);
            if (fh == null) throw new IOException("Entry not found: " + entryName);
            return closeZipWithStream(zf, zf.getInputStream(fh));
        } catch (IOException | RuntimeException zip4jFailure) {
            closeQuietly(zf);
            if (isPasswordProblem(zip4jFailure)) throw asIoException(zip4jFailure);
            try {
                return openRawEntry(zip, entryName);
            } catch (IOException directFailure) {
                directFailure.addSuppressed(zip4jFailure);
                throw directFailure;
            }
        }
    }

    /**
     * zip4j streams read straight from the {@link ZipFile}'s file handle, so the
     * handle has to stay open for as long as the returned stream lives.
     */
    private static InputStream closeZipWithStream(ZipFile zf, InputStream stream) {
        return new FilterInputStream(stream) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    super.close();
                } finally {
                    try {
                        zf.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        };
    }

    private static boolean isPasswordProblem(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ZipException
                    && ((ZipException) c).getType()
                    == ZipException.Type.WRONG_PASSWORD) return true;
            if (c.getCause() == c) break;
        }
        return false;
    }

    private static IOException asIoException(Throwable t) {
        return t instanceof IOException ? (IOException) t
                : new IOException(t.getMessage(), t);
    }

    /** Extracts a single entry, flattening it into {@code outFile}. */
    public static void extractEntry(File zip, String entryName, File outFile) throws IOException {
        File parent = outFile.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (InputStream in = openEntry(zip, entryName);
             OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER)) {
            byte[] buf = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    /**
     * Extracts a single entry without trusting the central directory's
     * compressed size. zip4j caps entry reads at that size and fails with
     * {@code EOFException: Unexpected end of input stream} on archives that
     * under-declare it, which some large .apks bundles hit. The payload is
     * located via the entry's real local header, and deflated streams are
     * inflated to their natural end instead of a declared byte count.
     */
    public static void extractEntryTolerant(File zip, String entryName, File outFile) throws IOException {
        Entry e = findEntry(readCentralDirectory(zip), entryName);
        if (e.encrypted) throw new IOException("Encrypted entries need a password: " + e.name);
        File parent = outFile.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
            byte[] local = new byte[30];
            raf.seek(e.localOffset);
            raf.readFully(local);
            if (u4(local, 0) != SIG_LOCAL)
                throw new IOException("Bad local header for entry: " + e.name);
            long dataStart = e.localOffset + 30L + u2(local, 26) + u2(local, 28);
            long available = raf.length() - dataStart;
            if (available <= 0) throw new IOException("Entry data missing for: " + e.name);
            // Deflate is self-terminating, so an oversized window cannot leak
            // into the next entry; stored payloads have no such marker and
            // still need the declared size as their end.
            long regionLen = e.method == METHOD_DEFLATE ? available
                    : (e.compressedSize > 0 ? Math.min(e.compressedSize, available) : available);
            CRC32 crc = new CRC32();
            try (InputStream in = decompress(new RegionInputStream(raf, dataStart, regionLen), e.method, e.name);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[COPY_BUFFER];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    crc.update(buf, 0, n);
                }
            }
            if ((e.uncompressedSize != 0 || e.crc != 0) && crc.getValue() != e.crc)
                throw new IOException("Entry data is corrupt (CRC mismatch): " + e.name);
        }
    }

    /**
     * Extracts every entry below {@code destDir}, refusing names that escape it.
     * Used as the fallback when zip4j cannot walk the archive at all.
     */
    public static void extractAll(File zip, File destDir, boolean preserveTime) throws IOException {
        String canonicalRoot = destDir.getCanonicalPath();
        for (Entry e : readCentralDirectory(zip)) {
            File out = new File(destDir, e.name);
            String canonicalOut = out.getCanonicalPath();
            if (!canonicalOut.equals(canonicalRoot)
                    && !canonicalOut.startsWith(canonicalRoot + File.separator)) {
                throw new IOException("Refusing entry outside destination: " + e.name);
            }
            if (e.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                out.mkdirs();
                continue;
            }
            File parent = out.getParentFile();
            if (parent != null) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            RandomAccessFile raf = new RandomAccessFile(zip, "r");
            try (InputStream in = openRawEntry(raf, e);
                 OutputStream os = new java.io.BufferedOutputStream(new FileOutputStream(out), COPY_BUFFER)) {
                byte[] buf = new byte[COPY_BUFFER];
                int n;
                while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            } catch (IOException | RuntimeException ex) {
                closeQuietly(raf);
                throw ex;
            }
            if (preserveTime && e.lastModified > 0) {
                //noinspection ResultOfMethodCallIgnored
                out.setLastModified(e.lastModified);
            }
        }
    }

    /** CRC-32 of an entry's uncompressed bytes, or -1 when it cannot be read. */
    public static long crc32(File zip, String entryName) {
        try (InputStream in = openEntry(zip, entryName)) {
            CRC32 crc = new CRC32();
            byte[] buf = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buf)) != -1) crc.update(buf, 0, n);
            return crc.getValue();
        } catch (Exception e) {
            return -1L;
        }
    }

    private static InputStream openRawEntry(File zip, String entryName) throws IOException {
        Entry entry = findEntry(readCentralDirectory(zip), entryName);
        RandomAccessFile raf = new RandomAccessFile(zip, "r");
        try {
            return openRawEntry(raf, entry);
        } catch (IOException | RuntimeException e) {
            closeQuietly(raf);
            throw e;
        }
    }

    private static InputStream openRawEntry(RandomAccessFile raf, Entry e) throws IOException {
        if (e.encrypted) throw new IOException("Encrypted entries need a password: " + e.name);
        byte[] local = new byte[30];
        raf.seek(e.localOffset);
        raf.readFully(local);
        if (u4(local, 0) != SIG_LOCAL)
            throw new IOException("Bad local header for entry: " + e.name);
        long dataStart = e.localOffset + 30L + u2(local, 26) + u2(local, 28);
        InputStream raw = new RegionInputStream(raf, dataStart, e.compressedSize);
        return decompress(raw, e.method, e.name);
    }

    private static InputStream decompress(InputStream raw, int method, String name) throws IOException {
        switch (method) {
            case METHOD_STORE:
                return new BufferedInputStream(raw);
            case METHOD_DEFLATE:
                return new InflaterInputStream(raw, new Inflater(true), COPY_BUFFER);
            case METHOD_BZIP2:
                return new BZip2CompressorInputStream(raw, true);
            case METHOD_XZ:
                return new XZCompressorInputStream(raw, true);
            case METHOD_LZMA:
                return openLzma(raw);
            case METHOD_ZSTD:
                return openZstd(raw);
            default:
                throw new IOException("Unsupported compression method " + method + " for entry: " + name);
        }
    }

    /**
     * ZIP method 14 wraps an LZMA1 stream in a 4 byte header followed by the 5
     * property bytes. commons-compress expects lzma-alone framing, so the
     * unknown-size field is synthesised on top of the real properties.
     */
    private static InputStream openLzma(InputStream raw) throws IOException {
        byte[] header = new byte[13];
        if (readFully(raw, header, 0, 4) < 4)
            throw new IOException("Truncated LZMA entry header");
        readFully(raw, header, 0, 5);
        System.arraycopy(LZMA_SIZE_UNKNOWN, 0, header, 5, 8);
        return new LZMACompressorInputStream(
                new SequenceInputStream(new ByteArrayInputStream(header), raw));
    }

    private static InputStream openZstd(InputStream raw) throws IOException {
        try {
            return (InputStream) Class
                    .forName("org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream")
                    .getConstructor(InputStream.class, boolean.class)
                    .newInstance(raw, true);
        } catch (ClassNotFoundException e) {
            throw new IOException("Zstandard entries are not supported on this build", e);
        } catch (ReflectiveOperationException | LinkageError e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Cannot read Zstandard entry: " + cause.getMessage(), cause);
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static void closeQuietly(ZipFile zf) {
        if (zf == null) return;
        try {
            zf.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(RandomAccessFile raf) {
        if (raf == null) return;
        try {
            raf.close();
        } catch (IOException ignored) {
        }
    }

    private static Entry findEntry(List<Entry> entries, String name) throws IOException {
        String normalized = name.replace('\\', '/');
        for (Entry e : entries) {
            if (e.name.equals(normalized)) return e;
        }
        throw new IOException("Entry not found: " + name);
    }

    private static List<FileHeader> fromCentralDirectory(List<Entry> entries) {
        List<FileHeader> headers = new ArrayList<>(entries.size());
        for (Entry e : entries) headers.add(e.toFileHeader());
        return headers;
    }

    static List<Entry> readCentralDirectory(File zip) throws IOException {
        long length = zip.length();
        if (length < 22) throw new IOException("File is too small to be a zip archive: " + zip.getName());
        try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
            int window = (int) Math.min(length, (long) UINT16_MAX + 22);
            byte[] tail = new byte[window];
            raf.seek(length - window);
            raf.readFully(tail);
            int eocd = -1;
            for (int i = tail.length - 22; i >= 0; i--) {
                if (u4(tail, i) == SIG_EOCD) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) throw new IOException("Not a zip archive (no central directory): " + zip.getName());
            long total = u2(tail, eocd + 10) & UINT16_MAX;
            long cdOffset = u4(tail, eocd + 16) & UINT32_MAX;
            if (total == UINT16_MAX || cdOffset == UINT32_MAX) {
                int locator = eocd - 20;
                if (locator < 0 || u4(tail, locator) != SIG_ZIP64_LOCATOR)
                    throw new IOException("Zip64 archive without locator: " + zip.getName());
                byte[] record = new byte[56];
                raf.seek(i8(tail, locator + 8));
                raf.readFully(record);
                if (u4(record, 0) != SIG_ZIP64_EOCD)
                    throw new IOException("Zip64 end-of-central-directory missing: " + zip.getName());
                total = i8(record, 32);
                cdOffset = i8(record, 48);
            }
            if (cdOffset < 0 || cdOffset >= length)
                throw new IOException("Central directory offset out of range: " + zip.getName());

            List<Entry> entries = new ArrayList<>((int) Math.min(total, 1 << 20));
            raf.seek(cdOffset);
            byte[] head = new byte[46];
            for (long seen = 0; seen < total; seen++) {
                raf.readFully(head);
                if (u4(head, 0) != SIG_CENTRAL) break;
                int flags = u2(head, 8);
                int method = u2(head, 10);
                int dosDate = u2(head, 14);
                int dosTime = u2(head, 12);
                long crc = u4(head, 16);
                long csize = u4(head, 20);
                long usize = u4(head, 24);
                int nameLen = u2(head, 28);
                int extraLen = u2(head, 30);
                int commentLen = u2(head, 32);
                long localOffset = u4(head, 42);
                byte[] nameBytes = new byte[nameLen];
                raf.readFully(nameBytes);
                byte[] extra = new byte[extraLen];
                raf.readFully(extra);
                if (commentLen > 0) raf.skipBytes(commentLen);

                boolean needUsize = usize == UINT32_MAX;
                boolean needCsize = csize == UINT32_MAX;
                boolean needOffset = localOffset == UINT32_MAX;
                if (needUsize || needCsize || needOffset) {
                    int[] cursor = {0};
                    if (needUsize) usize = zip64Value(extra, cursor);
                    if (needCsize) csize = zip64Value(extra, cursor);
                    if (needOffset) localOffset = zip64Value(extra, cursor);
                }

                Entry entry = new Entry();
                entry.name = decodeName(nameBytes, (flags & 0x800) != 0);
                entry.method = method;
                entry.flags = flags;
                entry.encrypted = (flags & 0x0001) != 0;
                entry.crc = crc;
                entry.compressedSize = csize;
                entry.uncompressedSize = usize;
                entry.localOffset = localOffset;
                entry.lastModified = dosToMillis(dosDate, dosTime);
                entries.add(entry);
            }
            if (entries.isEmpty() && total > 0)
                throw new IOException("Unreadable central directory: " + zip.getName());
            return entries;
        }
    }

    private static long zip64Value(byte[] extra, int[] cursor) throws IOException {
        int p = cursor[0];
        while (p + 4 <= extra.length) {
            int id = u2(extra, p);
            int size = u2(extra, p + 2);
            if (p + 4 + size > extra.length) break;
            if (id == 0x0001) {
                if (size < 8) throw new IOException("Malformed zip64 extra field");
                long v = i8(extra, p + 4);
                cursor[0] = p + 4 + size;
                return v;
            }
            p += 4 + size;
        }
        throw new IOException("Zip64 extra field missing");
    }

    private static String decodeName(byte[] nameBytes, boolean utf8) {
        Charset cs = utf8 ? StandardCharsets.UTF_8 : legacyCp437();
        String name = new String(nameBytes, cs).replace('\\', '/');
        int nul = name.indexOf('\0');
        return nul >= 0 ? name.substring(0, nul) : name;
    }

    /**
     * IBM code page 437 is the historical default for zip names that are not
     * flagged UTF-8, but Android does not ship it, so ISO-8859-1 is the
     * closest available stand-in there.
     */
    private static Charset legacyCp437() {
        for (String name : new String[]{"IBM437", "CP437"}) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static long dosToMillis(int dosDate, int dosTime) {
        int year = ((dosDate >> 9) & 0x7F) + 1980;
        int month = ((dosDate >> 5) & 0x0F) - 1;
        int day = dosDate & 0x1F;
        int hour = (dosTime >> 11) & 0x1F;
        int minute = (dosTime >> 5) & 0x3F;
        int second = (dosTime << 1) & 0x3E;
        if (month < 0 || month > 11 || day < 1 || day > 31) return 0L;
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static int u2(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long u4(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static long i8(byte[] b, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[off + i] & 0xFFL);
        return v;
    }

    static final class Entry {
        String name;
        int method;
        int flags;
        boolean encrypted;
        long crc;
        long compressedSize;
        long uncompressedSize;
        long localOffset;
        long lastModified;

        boolean isDirectory() {
            return name.endsWith("/");
        }

        FileHeader toFileHeader() {
            FileHeader fh = new FileHeader();
            fh.setFileName(name);
            fh.setCompressedSize(compressedSize);
            fh.setUncompressedSize(uncompressedSize);
            fh.setCrc(crc);
            if (lastModified > 0) fh.setLastModifiedTime(lastModified);
            fh.setOffsetLocalHeader(localOffset);
            fh.setFileNameUTF8Encoded((flags & 0x800) != 0);
            fh.setGeneralPurposeFlag(new byte[]{(byte) (flags & 0xFF), (byte) ((flags >> 8) & 0xFF)});
            fh.setEncrypted(encrypted);
            fh.setDataDescriptorExists((flags & 0x0008) != 0);
            fh.setVersionNeededToExtract(20);
            fh.setCompressionMethod(method == METHOD_DEFLATE ? CompressionMethod.DEFLATE : CompressionMethod.STORE);
            return fh;
        }
    }

    /**
     * Reads exactly {@code length} bytes from a fixed window of the archive.
     * Closing this stream closes the backing file, since the caller owns it for
     * the whole lifetime of the decompressor wrapping the stream.
     */
    private static final class RegionInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long position;
        private long remaining;

        RegionInputStream(RandomAccessFile raf, long position, long length) {
            this.raf = raf;
            this.position = position;
            this.remaining = Math.max(0, length);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = raf.read();
            if (b < 0) return -1;
            position++;
            remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int want = (int) Math.min(len, remaining);
            raf.seek(position);
            int n = raf.read(b, off, want);
            if (n < 0) return -1;
            position += n;
            remaining -= n;
            return n;
        }

        @Override
        public int available() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, remaining));
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }
}