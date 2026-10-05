package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.serializableItems.ResEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.FileUtils;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/**
 * Read-only view over the XML living inside an APK: enumerates every {@code *.xml} entry,
 * tells binary AXML apart from plain text, and decodes AXML into readable XML using the
 * APK's own {@code resources.arsc} so references come back as {@code @string/foo} instead
 * of {@code @7f0d0023}.
 *
 * <p>This is the "readable mode" half of the translation screen. The string-resource half
 * goes through {@link ApkStringsTranslator} instead, because in a compiled APK the
 * user-facing strings live inside the arsc and not in any file.
 */
public final class ApkXmlCatalog {

    public static final String MANIFEST = "AndroidManifest.xml";
    public static final String ARSC = "resources.arsc";

    private static final int MAX_XML_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ARSC_BYTES = 192 * 1024 * 1024;

    /** One XML entry inside the APK. */
    public static final class Item {
        public final String path;
        public final long size;
        public final boolean binary;
        public final boolean manifest;

        Item(String path, long size, boolean binary, boolean manifest) {
            this.path = path;
            this.size = size;
            this.binary = binary;
            this.manifest = manifest;
        }

        public String displayName() {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }
    }

    private final File apk;
    private final List<Item> items;
    private final List<ResEntry> resourceEntries;

    private ApkXmlCatalog(File apk, List<Item> items, List<ResEntry> resourceEntries) {
        this.apk = apk;
        this.items = items;
        this.resourceEntries = resourceEntries;
    }

    public List<Item> items() {
        return items;
    }

    public boolean hasResourceTable() {
        return !resourceEntries.isEmpty();
    }

    /**
     * Reads the APK once and caches both the XML listing and the parsed resource table.
     * The table is only parsed when it is actually needed, because it is by far the most
     * expensive part and the readable-xml listing does not require it.
     */
    public static ApkXmlCatalog open(File apk, boolean withResourceTable) throws IOException {
        List<Item> items = new ArrayList<>();
        List<ResEntry> entries = Collections.emptyList();
        try (ZipFile zf = new ZipFile(apk)) {
            if (withResourceTable && zf.getFileHeader(ARSC) != null) {
                try (InputStream in = zf.getInputStream(zf.getFileHeader(ARSC))) {
                    entries = new ResourceTableParser(in).parse();
                } catch (Exception e) {
                    // A malformed arsc only costs symbolic reference names, not the listing.
                    entries = Collections.emptyList();
                }
            }
            for (FileHeader header : zf.getFileHeaders()) {
                if (header == null || header.isDirectory()) continue;
                String name = header.getFileName();
                if (name == null || !name.toLowerCase().endsWith(".xml")) continue;
                boolean binary;
                try (InputStream in = zf.getInputStream(header)) {
                    binary = looksBinaryAxml(readPrefix(in, 8));
                } catch (IOException ignored) {
                    binary = true;
                }
                items.add(new Item(name, header.getUncompressedSize(), binary, MANIFEST.equals(name)));
            }
        }
        items.sort((a, b) -> {
            if (a.manifest != b.manifest) return a.manifest ? -1 : 1;
            return a.path.compareToIgnoreCase(b.path);
        });
        return new ApkXmlCatalog(apk, items, entries);
    }

    /** @return entries whose path contains {@code query}, case-insensitively. */
    public static List<Item> filter(List<Item> source, String query) {
        if (query == null || query.trim().isEmpty()) return source;
        String q = query.trim().toLowerCase();
        List<Item> out = new ArrayList<>();
        for (Item item : source) {
            if (item.path.toLowerCase().contains(q)) out.add(item);
        }
        return out;
    }

    /**
     * Decodes one XML entry to readable text: binary AXML goes through {@link aXMLDecoder},
     * plain XML is returned verbatim.
     */
    public String decode(String entryPath) throws IOException {
        byte[] raw = readEntry(entryPath, MAX_XML_BYTES);
        if (raw == null) throw new IOException("No such entry: " + entryPath);
        if (!looksBinaryAxml(raw)) return new String(raw, StandardCharsets.UTF_8);
        try {
            return new aXMLDecoder(new ByteArrayInputStream(raw), resourceEntries).decodeAsString();
        } catch (Exception e) {
            throw new IOException("Cannot decode " + entryPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Copies an entry into the cache so it can be opened in the text editor, which is where
     * AXML gets re-encoded on save and handed back through {@code setResult(757, ...)}.
     *
     * @return the staged file, or {@code null} when the entry does not exist.
     */
    public File stage(Context context, String entryPath) throws IOException {
        byte[] raw = readEntry(entryPath, MAX_XML_BYTES);
        if (raw == null) return null;
        File dir = new File(context.getCacheDir(), "xlate_axml");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        File staged = new File(dir, entryPath.replace('/', '_'));
        try (OutputStream out = new FileOutputStream(staged)) {
            out.write(raw);
        }
        return staged;
    }

    /**
     * Copies the resource table into the cache. The text editor takes it as a path so it can
     * resolve {@code @string/...} references while decoding.
     *
     * @return the staged file, or {@code null} when the APK has no arsc.
     */
    public File stageResourceTable(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "xlate_axml");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return null;
        File staged = new File(dir, ARSC);
        return copyEntryTo(ARSC, staged, MAX_ARSC_BYTES);
    }

    /**
     * Copies the resource table to an arbitrary path, which is what the arsc editor needs
     * because it edits a standalone file.
     *
     * @return {@code dest}, or {@code null} when the APK has no arsc.
     */
    public File extractResourceTable(File dest) throws IOException {
        return copyEntryTo(ARSC, dest, MAX_ARSC_BYTES);
    }

    /**
     * Streams one entry to disk.
     *
     * <p>Streaming rather than reading into a byte[] matters here: {@code resources.arsc} of a
     * large game is tens of megabytes and buffering it on a phone is how you get an OOM.
     *
     * @return {@code dest}, or {@code null} when the entry does not exist
     */
    public File copyEntryTo(String entryPath, File dest, int maxBytes) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader header = zf.getFileHeader(entryPath);
            if (header == null) return null;
            if (header.getUncompressedSize() > maxBytes) {
                throw new IOException("Entry too large: " + entryPath);
            }
            try (InputStream in = zf.getInputStream(header);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) throw new IOException("Entry too large: " + entryPath);
                    out.write(buf, 0, n);
                }
            }
        }
        return dest;
    }

    public byte[] readEntry(String entryPath, int maxBytes) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader header = zf.getFileHeader(entryPath);
            if (header == null) return null;
            if (header.getUncompressedSize() > maxBytes) {
                throw new IOException("Entry too large: " + entryPath);
            }
            try (InputStream in = zf.getInputStream(header)) {
                return readAll(in, maxBytes);
            }
        }
    }

    /** Reads at most {@code max} bytes, for cheap content sniffing. */
    private static byte[] readPrefix(InputStream in, int max) throws IOException {
        byte[] buf = new byte[max];
        int total = 0;
        int n;
        while (total < max && (n = in.read(buf, total, max - total)) != -1) total += n;
        if (total == max) return buf;
        byte[] exact = new byte[total];
        System.arraycopy(buf, 0, exact, 0, total);
        return exact;
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException("Entry too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Binary Android XML starts with the {@code RES_XML_TYPE} chunk header
     * ({@code 0x00080003}, little endian). Anything else is treated as plain text.
     *
     * <p>{@link FileUtils#isAxml(InputStream)} cannot be used here: it closes the stream it
     * is given and throws on an empty one.
     */
    public static boolean looksBinaryAxml(byte[] data) {
        if (data == null || data.length < 4) return false;
        if ((data[0] & 0xFF) == 0x03 && (data[1] & 0xFF) == 0x00
                && (data[2] & 0xFF) == 0x08 && (data[3] & 0xFF) == 0x00) {
            return true;
        }
        // Defensive: some tools emit the XML type with the header-size field zeroed.
        return (data[0] & 0xFF) == 0x03 && (data[2] & 0xFF) == 0x08;
    }
}
