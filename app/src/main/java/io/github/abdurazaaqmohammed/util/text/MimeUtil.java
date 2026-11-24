package io.github.abdurazaaqmohammed.util.text;

import io.github.abdurazaaqmohammed.util.io.FileUtils;


import android.content.Context;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

public class MimeUtil {

    /**
     * Preference key for the user's external default app (Open with →
     * long-press → Set as default) for one MIME type. The key stays
     * MIME-scoped on purpose: one saved default per MIME, no per-extension
     * aliases, so existing keys keep working.
     */
    public static String defaultAppKey(String mime) {
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    /**
     * The semantic display name of a file for MIME purposes. When the caller
     * dispatches {@code foo.pdf.bak} under its stripped name, MIME probing
     * should see {@code foo.pdf} - never a default keyed off the {@code .bak}
     * suffix. A lone {@code foo.bak} (nothing meaningful under the suffix)
     * keeps its full name.
     */
    public static String logicalDisplayName(String fileName) {
        if (fileName == null) return null;
        String bak = ".bak";
        if (fileName.toLowerCase(Locale.ROOT).endsWith(bak)) {
            String stripped = fileName.substring(0, fileName.length() - bak.length());
            if (!FilenameUtils.getExtension(stripped).isEmpty()) return stripped;
        }
        return fileName;
    }

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] ZIP_EMPTY_MAGIC = {'P', 'K', 5, 6};
    private static final byte[] RAR_MAGIC = {'R', 'a', 'r', '!', 0x1A};
    private static final byte[] SEVENZ_MAGIC = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] GZIP_MAGIC = {(byte) 0x1F, (byte) 0x8B};
    private static final byte[] BZ2_MAGIC = {'B', 'Z', 'h'};
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
    private static final byte[] ZSTD_MAGIC = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};
    private static final byte[] LZ4_MAGIC = {0x04, 0x22, 0x4D, 0x18};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F'};
    private static final byte[] BMP_MAGIC = {'B', 'M'};
    private static final byte[] ICO_MAGIC = {0x00, 0x00, 0x01, 0x00};
    private static final byte[] CUR_MAGIC = {0x00, 0x00, 0x02, 0x00};
    private static final byte[] PSD_MAGIC = {'8', 'B', 'P', 'S'};
    private static final byte[] TIFF_LE_MAGIC = {'I', 'I', '*', 0x00};
    private static final byte[] TIFF_BE_MAGIC = {'M', 'M', 0x00, '*'};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] ASF_MAGIC = {0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] RTF_MAGIC = {'{', '\\', 'r', 't', 'f'};
    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] FLAC_MAGIC = {'f', 'L', 'a', 'C'};
    private static final byte[] FLV_MAGIC = {'F', 'L', 'V'};
    private static final byte[] MIDI_MAGIC = {'M', 'T', 'h', 'd'};
    private static final byte[] ELF_MAGIC = {0x7F, 'E', 'L', 'F'};
    private static final byte[] EXE_MAGIC = {'M', 'Z'};
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};
    private static final byte[] ODEX_MAGIC = {'d', 'e', 'y', '\n'};
    private static final byte[] VDEX_MAGIC = {'v', 'd', 'e', 'x'};
    private static final byte[] CDEX_MAGIC = {'c', 'd', 'e', 'x'};
    private static final byte[] BOOTIMG_MAGIC = {'A', 'N', 'D', 'R', 'O', 'I', 'D', '!'};
    private static final byte[] SPARSE_MAGIC = {0x3A, (byte) 0xFF, 0x26, (byte) 0xED};
    private static final byte[] SQLITE_MAGIC = {'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0x00};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] TTF_MAGIC = {0x00, 0x01, 0x00, 0x00, 0x00};
    private static final byte[] OTF_MAGIC = {'O', 'T', 'T', 'O'};
    private static final byte[] WOFF_MAGIC = {'w', 'O', 'F', 'F'};
    private static final byte[] WOFF2_MAGIC = {'w', 'O', 'F', '2'};
    private static final byte[] TTC_MAGIC = {'t', 't', 'c', 'f'};
    private static final byte[] WASM_MAGIC = {0x00, 'a', 's', 'm'};
    private static final byte[] SQUASHFS_MAGIC = {'h', 's', 'q', 's'};
    private static final byte[] DEB_MAGIC = {'!', '<', 'a', 'r', 'c', 'h', '>', '\n', 'd', 'e', 'b', 'i', 'a', 'n'};
    private static final byte[] CAB_MAGIC = {'M', 'S', 'C', 'F'};

    public static String getReportedMimeType(Context context, Object fileOrZipEntry) {
        try {
            if (fileOrZipEntry instanceof ZipEntryInfo entry) return guessFromExtension(entry.getName());
            return context.getContentResolver().getType(FileProvider.getUriForFile(context, context.getPackageName() + ".provider", (File) fileOrZipEntry));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reported MIME type with an explicit logical name. When the on-disk name
     * differs from the name the user clicked (a staged {@code foo.pdf.bak}
     * copy, a renamed cache entry), the extension of {@code logicalName} wins
     * before any probing of the physical file, so the saved default for the
     * semantic type still applies.
     */
    public static String getReportedMimeType(Context context, Object fileOrZipEntry, String logicalName) {
        if (logicalName != null) {
            String byName = getMimeTypeFromExtension(logicalName);
            if (byName != null) return byName;
        }
        return getReportedMimeType(context, fileOrZipEntry);
    }

    public static String getRealMimeType(File file) {
        if (file == null || !file.isFile()) return null;
        byte[] data = new byte[2048];
        int len;
        try (InputStream is = new FileInputStream(file)) {
            len = is.read(data);
        } catch (Exception e) {
            return null;
        }
        if (len <= 0) return guessFromExtension(file.getName());

        String ext = FilenameUtils.getExtension(logicalDisplayName(file.getName())).toLowerCase(Locale.ROOT);

        if (startsWith(data, len, ZIP_MAGIC) || startsWith(data, len, ZIP_EMPTY_MAGIC)) {
            return detectZipMimeType(file, ext);
        }
        if (startsWith(data, len, RAR_MAGIC)) return "application/vnd.rar";
        if (startsWith(data, len, SEVENZ_MAGIC)) return "application/x-7z-compressed";
        if (startsWith(data, len, GZIP_MAGIC)) return ext.equals("svgz") ? "image/svg+xml" : "application/gzip";
        if (startsWith(data, len, BZ2_MAGIC)) return "application/x-bzip2";
        if (startsWith(data, len, XZ_MAGIC)) return "application/x-xz";
        if (startsWith(data, len, ZSTD_MAGIC)) return "application/zstd";
        if (startsWith(data, len, LZ4_MAGIC)) return "application/x-lz4";
        if (startsWith(data, len, CAB_MAGIC)) return "application/vnd.ms-cab-compressed";
        if (startsWith(data, len, DEB_MAGIC)) return "application/vnd.debian.binary-package";
        if (startsWith(data, len, SQUASHFS_MAGIC)) return "application/x-squashfs";
        if (containsAt(data, len, "ustar".getBytes(StandardCharsets.US_ASCII), 257)) return "application/x-tar";

        if (startsWith(data, len, PNG_MAGIC)) return "image/png";
        if (startsWith(data, len, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(data, len, GIF_MAGIC)) return "image/gif";
        if (startsWith(data, len, BMP_MAGIC)) return "image/bmp";
        if (startsWith(data, len, PSD_MAGIC)) return "image/vnd.adobe.photoshop";
        if (startsWith(data, len, TIFF_LE_MAGIC) || startsWith(data, len, TIFF_BE_MAGIC)) return "image/tiff";
        if (startsWith(data, len, RIFF_MAGIC)) {
            String riffType = ascii(data, len, 8, 4);
            switch (riffType) {
                case "WEBP" -> {return "image/webp";}
                case "WAVE" -> {return "audio/wav";}
                case "AVI " -> {return "video/x-msvideo";}
                default -> {}
            }
        }
        if (startsWith(data, len, new byte[]{'F', 'O', 'R', 'M'}) && "AIFF".equals(ascii(data, len, 8, 4))) {
            return "audio/aiff";
        }
        if (startsWith(data, len, "ID3".getBytes(StandardCharsets.US_ASCII))) return "audio/mpeg";
        if (startsWith(data, len, "#!AMR".getBytes(StandardCharsets.US_ASCII))) return "audio/amr";
        if (len >= 2 && (data[0] & 0xFF) == 0xFF) {
            int b1 = data[1] & 0xFF;
            if ((b1 & 0xF6) == 0xF0) return "audio/aac";
            if ((b1 & 0xE0) == 0xE0 && b1 != 0xD8) return "audio/mpeg";
        }
        if (len > 11 && "ftyp".equals(ascii(data, len, 4, 4))) {
            String brand = ascii(data, len, 8, 4);
            switch (brand) {
                case "M4A ", "M4B ", "M4P " -> {return "audio/mp4";}
                case "avif", "avis" -> {return "image/avif";}
                case "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis", "avic" -> {return "image/heic";}
                case "qt  ", "qt" -> {return "video/quicktime";}
                case "3gp4", "3gp5", "3gp6", "3g2a" -> {return "video/3gpp";}
                default -> {return "video/mp4";}
            }
        }
        if (startsWith(data, len, EBML_MAGIC)) {
            String head = new String(data, 0, Math.min(len, 64), StandardCharsets.US_ASCII);
            return head.contains("webm") ? "video/webm" : "video/x-matroska";
        }
        if (startsWith(data, len, ASF_MAGIC)) {
            return (ext.equals("wma")) ? "audio/x-ms-wma" : "video/x-ms-wmv";
        }
        if (startsWith(data, len, PDF_MAGIC)) return "application/pdf";
        if (startsWith(data, len, RTF_MAGIC)) return "application/rtf";
        if (startsWith(data, len, SQLITE_MAGIC)) return "application/vnd.sqlite3";
        if (startsWith(data, len, OLE2_MAGIC)) return detectOle2MimeType(file, ext);
        if (startsWith(data, len, OGG_MAGIC)) return containsAscii(data, len, "OpusHead") ? "audio/opus" : "audio/ogg";
        if (startsWith(data, len, FLAC_MAGIC)) return "audio/flac";
        if (startsWith(data, len, FLV_MAGIC)) return "video/x-flv";
        if (startsWith(data, len, MIDI_MAGIC)) return "audio/midi";

        if (startsWith(data, len, OTF_MAGIC)) return "font/otf";
        if (startsWith(data, len, WOFF_MAGIC)) return "font/woff";
        if (startsWith(data, len, WOFF2_MAGIC)) return "font/woff2";
        if (startsWith(data, len, TTF_MAGIC) || startsWith(data, len, TTC_MAGIC)
                || "true".equals(ascii(data, len, 0, 4))) return "font/ttf";
        if (startsWith(data, len, ICO_MAGIC) || startsWith(data, len, CUR_MAGIC)) return "image/x-icon";

        if (startsWith(data, len, DEX_MAGIC) || startsWith(data, len, ODEX_MAGIC) || startsWith(data, len, CDEX_MAGIC)) {
            return "application/vnd.android.dex";
        }
        if (startsWith(data, len, VDEX_MAGIC)) return "application/octet-stream";
        if (startsWith(data, len, BOOTIMG_MAGIC)) return "application/vnd.android.bootimg";
        if (startsWith(data, len, SPARSE_MAGIC)) return "application/vnd.android.sparse-image";
        if (startsWith(data, len, ELF_MAGIC)) {
            if (len >= 18 && data[16] == 0x03 && data[17] == 0x00) return "application/x-sharedlib";
            return "application/x-executable";
        }
        if (startsWith(data, len, EXE_MAGIC)) return "application/x-msdownload";
        if (startsWith(data, len, WASM_MAGIC)) return "application/wasm";

        if (len >= 8 && data[0] == 0x02 && data[1] == 0x00 && data[2] == 0x0C && data[3] == 0x00) {
            return "application/vnd.android.arsc";
        }
        if (isBinaryXml(file)) return "text/xml";

        String offsetDiscovered = detectOffsetBinarySignature(file);
        if (offsetDiscovered != null) return offsetDiscovered;

        String textMime = detectTextMimeType(data, len, ext);
        if (textMime != null) return textMime;
        return guessFromExtension(ext);
    }

    public static String getMimeTypeForAction(Context context, File file) {
        String reported = getReportedMimeType(context, file);
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("fix_mime_type", false))
            return reported;
        String real = getRealMimeType(file);
        return real != null ? real : reported;
    }

    /**
     * MIME to dispatch on, with the logical (clicked) name overriding the
     * physical file name for reported probing; the real-bytes probe below is
     * unchanged, so a genuinely different payload still wins under the
     * fix_mime_type setting.
     */
    public static String getMimeTypeForAction(Context context, File file, String logicalName) {
        String reported = getReportedMimeType(context, file, logicalName);
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("fix_mime_type", false))
            return reported;
        String real = getRealMimeType(file);
        return real != null ? real : reported;
    }

    private static String detectZipMimeType(File file, String ext) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(file)) {
            if (zf.getEntry("AndroidManifest.xml") != null || zf.getEntry("classes.dex") != null) {
                return "application/vnd.android.package-archive";
            }
            if (zf.getEntry("[Content_Types].xml") != null) {
                if (zf.getEntry("word/document.xml") != null || hasEntryPrefix(zf, "word/", 48)) {
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
                if (zf.getEntry("xl/workbook.xml") != null || hasEntryPrefix(zf, "xl/", 48)) {
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }
                if (zf.getEntry("ppt/presentation.xml") != null || hasEntryPrefix(zf, "ppt/", 48)) {
                    return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                }
            }
            java.util.zip.ZipEntry mimeEntry = zf.getEntry("mimetype");
            if (mimeEntry != null && mimeEntry.getSize() > 0 && mimeEntry.getSize() < 128) {
                try (InputStream is = zf.getInputStream(mimeEntry)) {
                    byte[] buf = new byte[(int) mimeEntry.getSize()];
                    int n = is.read(buf);
                    if (n > 0) {
                        String val = new String(buf, 0, n, StandardCharsets.US_ASCII).trim();
                        if (val.contains("/")) return val;
                    }
                }
            }
            boolean hasApkEntry = false;
            boolean hasPayloadApp = false;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            int checked = 0;
            while (en.hasMoreElements() && checked++ < 64) {
                String name = en.nextElement().getName();
                if (name.startsWith("word/")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                if (name.startsWith("xl/")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                if (name.startsWith("ppt/")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                if (name.equals("base.apk") || name.endsWith("/base.apk") || name.endsWith(".apk")) hasApkEntry = true;
                if (name.startsWith("Payload/") && name.contains(".app/")) hasPayloadApp = true;
            }
            if (hasApkEntry) return "application/vnd.android.package-archive";
            if (hasPayloadApp) return "application/octet-stream";
            if (zf.getEntry("META-INF/MANIFEST.MF") != null) return "application/java-archive";
        } catch (Exception ignored) {
        }
        return zipFamilyMimeType(ext);
    }

    private static boolean hasEntryPrefix(java.util.zip.ZipFile zf, String prefix, int limit) {
        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
        int i = 0;
        while (en.hasMoreElements() && i++ < limit) {
            if (en.nextElement().getName().startsWith(prefix)) return true;
        }
        return false;
    }

    private static String detectOle2MimeType(File file, String ext) {
        try (InputStream is = new FileInputStream(file)) {
            int maxScan = (int) Math.min(file.length(), 131072L);
            byte[] buf = new byte[maxScan];
            int total = 0;
            while (total < maxScan) {
                int r = is.read(buf, total, maxScan - total);
                if (r <= 0) break;
                total += r;
            }
            StringBuilder asciiText = new StringBuilder(total / 2);
            for (int i = 0; i < total; i++) {
                byte b = buf[i];
                if (b >= 32 && b < 127) asciiText.append((char) b);
            }
            String collapsed = asciiText.toString();
            if (collapsed.contains("WordDocument")) return "application/msword";
            if (collapsed.contains("Workbook")) return "application/vnd.ms-excel";
            if (collapsed.contains("PowerPoint Document") || collapsed.contains("Current User")) {
                if (collapsed.contains("PowerPoint")) return "application/vnd.ms-powerpoint";
            }
        } catch (Exception ignored) {
        }
        return switch (ext) {
            case "doc" -> "application/msword";
            case "xls" -> "application/vnd.ms-excel";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "msi" -> "application/x-msi";
            default -> "application/x-ole-storage";
        };
    }

    private static String detectOffsetBinarySignature(File file) {
        long size = file.length();
        if (size < 1082) return null;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            if (size > 0x8006) {
                raf.seek(0x8001);
                byte[] iso = new byte[5];
                if (raf.read(iso) == 5 && "CD001".equals(new String(iso, StandardCharsets.US_ASCII))) {
                    return "application/x-iso9660-image";
                }
            }
            if (size >= 0x440) {
                raf.seek(0x438);
                int lo = raf.read();
                int hi = raf.read();
                if (lo == 0x53 && hi == 0xEF) return "application/x-ext4-image";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String detectTextMimeType(byte[] data, int len, String ext) {
        String decoded = decodeAsTextIfValid(data, len);
        if (decoded == null) return null;

        String trimmed = decoded.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("{\\rtf")) return "application/rtf";
        if (lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"))) return "image/svg+xml";
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) return "text/html";
        if (lower.startsWith("<?xml")
                || lower.startsWith("<manifest")
                || lower.startsWith("<resources")
                || lower.startsWith("<layout")
                || lower.startsWith("<linearlayout")
                || lower.startsWith("<relativelayout")) {
            return "text/xml";
        }
        if (lower.startsWith("#!")) return "text/x-shellscript";
        if (trimmed.startsWith("BEGIN:VCARD")) return "text/vcard";
        if (trimmed.startsWith("diff --git ") || trimmed.startsWith("--- ")) return "text/x-diff";
        if ((trimmed.startsWith("{") && trimmed.contains("\":"))
                || (trimmed.startsWith("[{") && trimmed.contains("\":"))
                || trimmed.equals("{}") || trimmed.equals("[]")) {
            return "application/json";
        }
        if (trimmed.startsWith(".class ") && (lower.contains(".super ") || lower.contains(".method "))) {
            return "text/x-smali";
        }

        String byExt = guessFromExtension(ext);
        if (byExt != null && (byExt.startsWith("text/") || byExt.equals("application/json")
                || byExt.equals("application/javascript") || byExt.equals("application/xml"))) {
            return byExt;
        }
        return "text/plain";
    }

    private static String decodeAsTextIfValid(byte[] data, int len) {
        int start = 0;
        if (len >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            start = 3;
        } else if (len >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, len - 2, StandardCharsets.UTF_16LE);
        } else if (len >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, len - 2, StandardCharsets.UTF_16BE);
        }
        for (int i = start; i < len; i++) {
            int ub = data[i] & 0xFF;
            if (ub == 0) return null;
            if (ub < 9 || (ub > 13 && ub < 32) || ub == 127) return null;
        }
        return new String(data, start, len - start, StandardCharsets.UTF_8);
    }

    private static String zipFamilyMimeType(String ext) {
        return switch (ext) {
            case "apk", "apks", "xapk", "apkm", "aspk" -> "application/vnd.android.package-archive";
            case "jar", "war" -> "application/java-archive";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            case "epub" -> "application/epub+zip";
            case "ipa" -> "application/octet-stream";
            default -> "application/zip";
        };
    }

    private static String guessFromExtension(String fileNameOrExt) {
        try {
            String ext = fileNameOrExt.contains(".")
                    ? FilenameUtils.getExtension(fileNameOrExt).toLowerCase(Locale.ROOT)
                    : fileNameOrExt.toLowerCase(Locale.ROOT);
            if (ext.isEmpty()) return null;
            String mapped = switch (ext) {
                case "apk", "apks", "xapk", "apkm", "aspk" -> "application/vnd.android.package-archive";
                case "dex" -> "application/vnd.android.dex";
                case "arsc" -> "application/vnd.android.arsc";
                case "axml", "xml" -> "text/xml";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "doc" -> "application/msword";
                case "xls" -> "application/vnd.ms-excel";
                case "ppt" -> "application/vnd.ms-powerpoint";
                case "rtf" -> "application/rtf";
                case "odt" -> "application/vnd.oasis.opendocument.text";
                case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
                case "odp" -> "application/vnd.oasis.opendocument.presentation";
                case "epub" -> "application/epub+zip";
                case "json" -> "application/json";
                case "md", "markdown" -> "text/markdown";
                case "yaml", "yml" -> "text/yaml";
                case "svg" -> "image/svg+xml";
                case "webp" -> "image/webp";
                case "avif" -> "image/avif";
                case "heic", "heif" -> "image/heic";
                case "mkv" -> "video/x-matroska";
                case "webm" -> "video/webm";
                case "flac" -> "audio/flac";
                case "opus" -> "audio/opus";
                case "ttf" -> "font/ttf";
                case "otf" -> "font/otf";
                case "woff" -> "font/woff";
                case "woff2" -> "font/woff2";
                case "db", "db3", "sqlite", "sqlite3" -> "application/vnd.sqlite3";
                case "7z" -> "application/x-7z-compressed";
                case "rar" -> "application/vnd.rar";
                case "xz" -> "application/x-xz";
                case "zst" -> "application/zstd";
                case "lz4" -> "application/x-lz4";
                case "tar" -> "application/x-tar";
                case "sh" -> "text/x-shellscript";
                case "smali" -> "text/x-smali";
                case "so" -> "application/x-sharedlib";
                default -> null;
            };
            if (mapped != null) return mapped;
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        } catch (Exception e) {
            return null;
        }
    }

    /** Public extension-to-MIME lookup used by the saved-default fast path. */
    public static String getMimeTypeFromExtension(String fileNameOrExt) {
        return guessFromExtension(fileNameOrExt);
    }

    private static boolean isBinaryXml(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return FileUtils.isAxml(is);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean containsAscii(byte[] data, int len, String needle) {
        byte[] pat = needle.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= len - pat.length; i++) {
            boolean match = true;
            for (int j = 0; j < pat.length; j++) {
                if (data[i + j] != pat[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] data, int len, byte[] prefix) {
        if (len < prefix.length || data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static boolean containsAt(byte[] data, int len, byte[] pattern, int offset) {
        if (len < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (data[offset + i] != pattern[i]) return false;
        return true;
    }

    private static String ascii(byte[] data, int len, int offset, int count) {
        if (len < offset + count || data.length < offset + count) return "";
        return new String(data, offset, count, StandardCharsets.US_ASCII);
    }
}
