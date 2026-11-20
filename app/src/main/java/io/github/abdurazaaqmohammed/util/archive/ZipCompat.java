package io.github.abdurazaaqmohammed.util.archive;

import io.github.abdurazaaqmohammed.util.archive.MpZstdInputStream;
import io.github.abdurazaaqmohammed.util.archive.SplitArchiveUtil;
import io.github.abdurazaaqmohammed.util.io.TransferControl;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
    /**
     * Info-ZIP Unicode Path extra field: stores the UTF-8 name beside the
     * legacy name plus a CRC-32 of those legacy bytes. Preferred whenever its
     * checksum validates, since it is the writer's own unicode answer.
     */
    private static final int EXTRA_UNICODE_PATH = 0x7075;
    private static final byte[] LZMA_SIZE_UNKNOWN = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private ZipCompat() {
    }

    /**
     * All central-directory entries, as zip4j headers so existing callers keep
     * working.
     *
     * <p>Archives with legacy non-ASCII names (Chinese GBK zips above all) are
     * listed through the direct central-directory scan, because its per-entry
     * charset heuristic decodes names zip4j can only read with one
     * archive-wide charset. The scan is also preferred when entries carry a
     * validated Unicode Path extra field, which zip4j ignores. Everything else
     * still goes through zip4j, falling back to the direct scan when zip4j
     * refuses the archive because of an unknown compression method.
     */
    public static List<FileHeader> listHeaders(File zip) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("Not a file: " + zip);
        File target = SplitArchiveUtil.isZipSplit(zip) ? SplitArchiveUtil.findFirstPart(zip) : zip;
        if (target == null || !target.exists()) target = zip;
        try {
            // centralDirectoryFor also reads split archives: their directory
            // lives in the last part, not in the file zip4j is pointed at.
            List<Entry> entries = centralDirectoryFor(target);
            if (ZipCharset.planFor(entries) != ZipCharset.Plan.DEFAULT) {
                return fromCentralDirectory(entries);
            }
        } catch (IOException ignoredParse) {
            // zip4j may still read archives this strict scan rejects; its
            // failure handling below is unchanged.
        }
        try (ZipFile zf = new ZipFile(target)) {
            List<FileHeader> headers = zf.getFileHeaders();
            // Convention for everything that leaves this class: the
            // lastModifiedTime field carries epoch millis. zip4j stores raw
            // DOS time there after its own read, so zip4j-decoded headers are
            // converted here; headers built by fromCentralDirectory already
            // hold epoch. Mutating these returned copies is safe — callers
            // that write archives open their own ZipFile handles.
            for (FileHeader fh : headers) {
                fh.setLastModifiedTime(fh.getLastModifiedTimeEpoch());
            }
            return headers;
        } catch (Exception zip4jFailure) {
            try {
                return fromCentralDirectory(readCentralDirectory(target));
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
     * The zip4j attempt applies the archive's detected legacy charset, so the
     * name lookup matches the decoded names produced by {@link #listHeaders}.
     */
    public static InputStream openEntry(File zip, String entryName) throws IOException {
        if (zip == null || entryName == null) throw new IOException("Missing archive or entry name");
        File target = SplitArchiveUtil.isZipSplit(zip) ? SplitArchiveUtil.findFirstPart(zip) : zip;
        if (target == null || !target.exists()) target = zip;
        ZipFile zf = null;
        try {
            zf = openZip(target);
            FileHeader fh = zf.getFileHeader(entryName);
            if (fh == null) throw new IOException("Entry not found: " + entryName);
            return closeZipWithStream(zf, zf.getInputStream(fh));
        } catch (IOException | RuntimeException zip4jFailure) {
            closeQuietly(zf);
            if (isPasswordProblem(zip4jFailure)) throw asIoException(zip4jFailure);
            try {
                return openRawEntry(target, entryName);
            } catch (IOException directFailure) {
                directFailure.addSuppressed(zip4jFailure);
                throw directFailure;
            }
        }
    }

    /**
     * A zip4j handle with the archive's detected legacy charset applied, so
     * every name zip4j decodes matches the per-entry heuristic used for
     * listing. Mutation call sites use this to keep entry names consistent
     * between what the user saw and what zip4j matches against. Mixed-charset
     * archives additionally get their header names corrected from the
     * per-entry decode, because no single charset lets zip4j spell them all.
     */
    public static ZipFile openZip(File zip) throws IOException {
        ZipCharset.Plan plan = ZipCharset.planFor(zip);
        ZipFile zf = ZipCharset.apply(new ZipFile(zip), plan);
        if (plan == ZipCharset.Plan.MIXED) {
            correctZip4jHeaderNames(zf, zip);
        }
        return zf;
    }

    /** Like {@link #openZip(File)} with a password for encrypted archives. */
    public static ZipFile openZip(File zip, char[] password) throws IOException {
        ZipFile zf = openZip(zip);
        if (password != null) zf.setPassword(password);
        return zf;
    }

    /**
     * Rewrites zip4j's decoded header names with the per-entry decode, matched
     * through the disk-number + local-header-offset key both readers share
     * (offsets alone repeat across split parts). Used when one archive-wide
     * charset cannot decode every entry (mixed legacy GBK + UTF-8-flagged
     * names) but zip4j must still run the extraction — encrypted archives
     * above all — so the names written to disk are the correct ones. Best
     * effort: any zip4j read failure leaves its own decode standing.
     */
    public static void correctZip4jHeaderNames(ZipFile zf, File zip) {
        List<Entry> entries;
        try {
            entries = centralDirectoryFor(zip);
        } catch (IOException ignoredParse) {
            return;
        }
        java.util.Map<String, String> ourNameByKey = new java.util.HashMap<>();
        for (Entry e : entries) {
            ourNameByKey.put(offsetKey(e.diskNumberStart, e.localOffset), e.name);
        }
        try {
            for (FileHeader fh : zf.getFileHeaders()) {
                String ours = ourNameByKey.get(offsetKey(fh.getDiskNumberStart(), fh.getOffsetLocalHeader()));
                if (ours != null && !ours.equals(fh.getFileName())) {
                    fh.setFileName(ours);
                }
            }
        } catch (ZipException ignoredRead) {
            // zip4j could not walk its own central directory; keep its decode.
        }
    }

    /**
     * Composite header identity: disk number plus local-header offset. Split
     * archives repeat offsets across parts, so the disk number is part of the
     * key everywhere our parser meets zip4j's headers.
     */
    public static String offsetKey(int diskNumberStart, long localOffset) {
        return diskNumberStart + "@" + localOffset;
    }

    /**
     * Central directory of the archive zip4j is pointed at. Split archives
     * keep the directory in their last part, so that part is read when the
     * pointed-at file has none.
     */
    static List<Entry> centralDirectoryFor(File target) throws IOException {
        try {
            return readCentralDirectory(target);
        } catch (IOException noEocdHere) {
            List<File> parts = SplitArchiveUtil.getOrderedParts(target);
            if (!parts.isEmpty()) {
                File last = parts.get(parts.size() - 1);
                if (!last.equals(target)) return readCentralDirectory(last);
            }
            throw noEocdHere;
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
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER)) {
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
        extractAll(zip, destDir, preserveTime, null);
    }

    /**
     * Extracts every entry below {@code destDir}, refusing names that escape it.
     *
     * <p>This is the fallback used when zip4j cannot walk the archive at all.
     * It is plain Java, so it takes the pause / cancel checkpoint itself.
     *
     * @param control the pause / cancel token, or null
     */
    public static void extractAll(File zip, File destDir, boolean preserveTime,
                                  TransferControl control) throws IOException {
        String canonicalRoot = destDir.getCanonicalPath();
        for (Entry e : readCentralDirectory(zip)) {
            if (control != null) control.checkpoint();
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
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out), COPY_BUFFER)) {
                byte[] buf = new byte[COPY_BUFFER];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (control != null) control.checkpoint();
                    os.write(buf, 0, n);
                }
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

    /**
     * Zstandard (zip method 93) entries are decoded by the app's own native
     * zstd stream (pure C, compiled per ABI); the app does not depend on
     * zstd-jni, which has no Android natives. The stream closes the
     * underlying region stream, which closes the RAF.
     */
    private static InputStream openZstd(InputStream raw) throws IOException {
        return new MpZstdInputStream(raw);
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
                int diskNumberStart = u2(head, 34);
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
                byte[] effectiveName = nameBytes;
                boolean effectiveUtf8 = (flags & 0x800) != 0;
                boolean fromUnicodePath = false;
                byte[] unicodeName = unicodePathFromExtra(extra, crc32Of(nameBytes));
                if (unicodeName != null) {
                    // The field's CRC-32 validated against the stored legacy
                    // name, so the writer vouched for these UTF-8 bytes.
                    effectiveName = unicodeName;
                    effectiveUtf8 = true;
                    fromUnicodePath = true;
                }
                entry.name = ZipCharset.decodeName(effectiveName, effectiveUtf8);
                entry.nameNonAscii = !isPureAscii(effectiveName);
                entry.nameCharset = effectiveUtf8
                        ? java.nio.charset.StandardCharsets.UTF_8
                        : ZipCharset.charsetFor(effectiveName, false);
                entry.unicodePathName = fromUnicodePath;
                entry.utf8Flag = effectiveUtf8;
                entry.method = method;
                entry.flags = flags;
                entry.encrypted = (flags & 0x0001) != 0;
                entry.crc = crc;
                entry.compressedSize = csize;
                entry.uncompressedSize = usize;
                entry.localOffset = localOffset;
                entry.diskNumberStart = diskNumberStart;
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

    /**
     * The UTF-8 name stored in a Unicode Path extra field (id 0x7075), or
     * null. The field carries a CRC-32 of the legacy name bytes it replaces;
     * a mismatch means the field is corrupt or does not belong to this entry,
     * in which case the stored name stays authoritative.
     */
    private static byte[] unicodePathFromExtra(byte[] extra, long nameCrc) {
        if (extra == null) return null;
        int p = 0;
        while (p + 4 <= extra.length) {
            int id = u2(extra, p);
            int size = u2(extra, p + 2);
            if (size < 0 || p + 4 + size > extra.length) break;
            if (id == EXTRA_UNICODE_PATH && size >= 5) {
                long crc = u4(extra, p + 4);
                if (crc == nameCrc) {
                    byte[] name = new byte[size - 4];
                    System.arraycopy(extra, p + 8, name, 0, size - 4);
                    return name;
                }
            }
            p += 4 + size;
        }
        return null;
    }

    private static long crc32Of(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static boolean isPureAscii(byte[] data) {
        for (byte b : data) {
            if (b < 0) return false;
        }
        return true;
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

    /** One parsed central-directory record; visible to ZipCharset for plan scans. */
    static final class Entry {
        String name;
        int method;
        int flags;
        boolean encrypted;
        long crc;
        long compressedSize;
        long uncompressedSize;
        long localOffset;
        /** Split-archive part this entry's data starts in (central header field). */
        int diskNumberStart;
        long lastModified;
        /** Charset the per-entry heuristic picked for the name, for plan scans. */
        Charset nameCharset;
        /** True when the stored name bytes contain any non-ASCII byte. */
        boolean nameNonAscii;
        /** True when the name came from a validated Unicode Path extra field. */
        boolean unicodePathName;
        boolean utf8Flag;

        boolean isDirectory() {
            return name.endsWith("/");
        }

        FileHeader toFileHeader() {
            FileHeader fh = new FileHeader();
            fh.setFileName(name);
            // zip4j reads its own isDirectory field and never derives it from
            // the name, so directories would lose their trailing slash in the
            // pane grouping without this.
            fh.setDirectory(isDirectory());
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