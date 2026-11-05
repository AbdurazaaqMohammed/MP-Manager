package io.github.abdurazaaqmohammed.boot;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Android Boot / InitBoot Image Engine (supports Header v0, v1, v2, v3, v4).
 * Pure Java: Unpacks kernel + ramdisk into editable directory with metadata,
 * and accurately repacks with correct alignment and header fields.
 */
public class BootImageEngine {

    public interface EngineCallback {
        void onProgress(String status, int percent);
        void onLog(String log);
    }

    public static boolean isBootImage(File file) {
        if (file == null || !file.isFile() || file.length() < 4096) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[8];
            if (fis.read(magic) < 8) return false;
            return "ANDROID!".equals(new String(magic, StandardCharsets.US_ASCII));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static JSONObject unpack(File imageFile, File outputDir, EngineCallback cb) throws Exception {
        if (!imageFile.exists()) {
            throw new IOException("File does not exist: " + imageFile.getAbsolutePath());
        }
        outputDir.mkdirs();

        if (cb != null) {
            cb.onProgress("Reading Boot Image Header...", 10);
            cb.onLog("Reading image: " + imageFile.getName());
        }

        byte[] headerData = new byte[4096];
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            fis.read(headerData);
        }

        String magic = new String(headerData, 0, 8, StandardCharsets.US_ASCII);
        if (!"ANDROID!".equals(magic)) {
            throw new IOException("Not a valid Android Boot Image ('ANDROID!' magic missing)");
        }

        ByteBuffer bb = ByteBuffer.wrap(headerData).order(ByteOrder.LITTLE_ENDIAN);
        int kernelSize = bb.getInt(8);
        int ramdiskSize = bb.getInt(12);
        int osVersionRaw = bb.getInt(16);
        int headerSize = bb.getInt(20);
        int headerVersion = bb.getInt(40);

        int pageSize = 4096;
        if (headerVersion < 3) {
            pageSize = bb.getInt(36);
            if (pageSize <= 0) pageSize = 2048;
        }

        String cmdline = readNullTermString(headerData, 44, 1536);
        String cmdlineExtra = headerSize > 1580 ? readNullTermString(headerData, 1580, 512) : "";

        JSONObject meta = new JSONObject();
        meta.put("magic", magic);
        meta.put("header_version", headerVersion);
        meta.put("kernel_size", kernelSize);
        meta.put("ramdisk_size", ramdiskSize);
        meta.put("header_size", headerSize);
        meta.put("os_version_raw", osVersionRaw);
        meta.put("page_size", pageSize);
        meta.put("cmdline", cmdline);
        meta.put("cmdline_extra", cmdlineExtra);
        meta.put("original_file_name", imageFile.getName());
        meta.put("original_image_size", imageFile.length());

        File metaFile = new File(outputDir, "header_info.json");
        try (FileOutputStream fos = new FileOutputStream(metaFile)) {
            fos.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        long headerBlockSize = pageSize;
        long kernelOffset = headerBlockSize;
        long kernelPadded = align(kernelSize, pageSize);
        long ramdiskOffset = kernelOffset + kernelPadded;

        // If kernel is present, extract it as kernel / Image
        if (kernelSize > 0) {
            File kernelOut = new File(outputDir, "kernel");
            try (FileInputStream fis = new FileInputStream(imageFile);
                 FileOutputStream fos = new FileOutputStream(kernelOut)) {
                fis.skip(kernelOffset);
                copyBytes(fis, fos, kernelSize);
            }
        }

        // Extract Ramdisk
        if (ramdiskSize > 0) {
            File ramdiskCompressed = new File(outputDir, "ramdisk.raw");
            byte[] magicPeek = new byte[4];
            try (FileInputStream fis = new FileInputStream(imageFile);
                 FileOutputStream fos = new FileOutputStream(ramdiskCompressed)) {
                fis.skip(ramdiskOffset);
                byte[] buf = new byte[65536];
                long rem = ramdiskSize;
                boolean peekDone = false;
                while (rem > 0) {
                    int toRead = (int) Math.min(buf.length, rem);
                    int r = fis.read(buf, 0, toRead);
                    if (r == -1) break;
                    if (!peekDone && r >= 4) {
                        System.arraycopy(buf, 0, magicPeek, 0, 4);
                        peekDone = true;
                    }
                    fos.write(buf, 0, r);
                    rem -= r;
                }
            }

            File cpioOut = new File(outputDir, "ramdisk.cpio");
            String compType = "raw";

            // LZ4 Legacy stream (0x184C2102)
            if (magicPeek[0] == 0x02 && magicPeek[1] == 0x21 && magicPeek[2] == 0x4C && magicPeek[3] == 0x18) {
                compType = "lz4";
                boolean success = Lz4Engine.decompressFile(ramdiskCompressed, cpioOut);
                if (!success || !cpioOut.exists() || cpioOut.length() == 0) {
                    byte[] raw = readFile(ramdiskCompressed);
                    byte[] decomp = Lz4Engine.decompress(raw);
                    try (FileOutputStream fos = new FileOutputStream(cpioOut)) {
                        fos.write(decomp);
                    }
                }
            } else if (magicPeek[0] == (byte) 0x1F && magicPeek[1] == (byte) 0x8B) {
                // GZIP
                compType = "gzip";
                try (FileInputStream fis = new FileInputStream(ramdiskCompressed);
                     GZIPInputStream gis = new GZIPInputStream(fis);
                     FileOutputStream fos = new FileOutputStream(cpioOut)) {
                    byte[] b = new byte[65536];
                    int r;
                    while ((r = gis.read(b)) > 0) fos.write(b, 0, r);
                }
            } else {
                copyFile(ramdiskCompressed, cpioOut);
            }

            meta.put("ramdisk_compression", compType);
            try (FileOutputStream fos = new FileOutputStream(metaFile)) {
                fos.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            File ramdiskDir = new File(outputDir, "ramdisk");
            List<RamdiskManifest.Entry> manifestEntries = CpioEngine.extractFile(cpioOut, ramdiskDir, (msg, p) -> {
                if (cb != null) cb.onProgress(msg, 30 + (p * 50 / 100));
            });

            File treeFile = new File(outputDir, "ramdisk_tree.json");
            try {
                RamdiskManifest.saveManifest(manifestEntries, treeFile);
            } catch (Exception ignored) {}

            ramdiskCompressed.delete();
            cpioOut.delete();
        }

        // Check for AVB footer at the end of the image (last 64 bytes)
        long fileLen = imageFile.length();
        if (fileLen >= 64) {
            byte[] footerBuf = new byte[64];
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                fis.skip(fileLen - 64);
                fis.read(footerBuf);
            }
            if (footerBuf[0] == 'A' && footerBuf[1] == 'V' && footerBuf[2] == 'B' && footerBuf[3] == 'f') {
                ByteBuffer fbb = ByteBuffer.wrap(footerBuf).order(ByteOrder.BIG_ENDIAN);
                long vbmetaOffset = fbb.getLong(20);
                long vbmetaSize = fbb.getLong(28);
                if (vbmetaOffset > 0 && vbmetaSize > 0 && vbmetaOffset + vbmetaSize <= fileLen) {
                    File vbmetaOut = new File(outputDir, "avb_vbmeta.bin");
                    try (FileInputStream fis = new FileInputStream(imageFile);
                         FileOutputStream fos = new FileOutputStream(vbmetaOut)) {
                        fis.skip(vbmetaOffset);
                        copyBytes(fis, fos, vbmetaSize);
                    }
                    meta.put("has_avb_footer", true);
                    meta.put("avb_vbmeta_size", vbmetaSize);
                    try (FileOutputStream fos = new FileOutputStream(metaFile)) {
                        fos.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }

        if (cb != null) {
            cb.onProgress("Unpack Finished!", 100);
        }

        JSONObject res = new JSONObject();
        res.put("status", "success");
        res.put("output_dir", outputDir.getAbsolutePath());
        res.put("metadata", meta);
        return res;
    }

    public static JSONObject repack(File inputDir, File outputImageFile, EngineCallback cb) throws Exception {
        if (!inputDir.exists()) {
            throw new IOException("Input directory does not exist: " + inputDir.getAbsolutePath());
        }

        File ramdiskSrc = new File(inputDir, "ramdisk");
        if (!ramdiskSrc.exists()) {
            if (new File(inputDir, "init").exists()) {
                ramdiskSrc = inputDir;
            } else {
                throw new IOException("Cannot locate 'ramdisk' folder or 'init' inside: " + inputDir.getAbsolutePath());
            }
        }

        JSONObject meta = null;
        File metaFile = new File(inputDir, "header_info.json");
        if (metaFile.exists()) {
            try (FileInputStream fis = new FileInputStream(metaFile)) {
                byte[] b = new byte[(int) metaFile.length()];
                fis.read(b);
                meta = new JSONObject(new String(b, StandardCharsets.UTF_8));
            }
        }

        int headerVersion = meta != null ? meta.optInt("header_version", 4) : 4;
        int kernelSize = meta != null ? meta.optInt("kernel_size", 0) : 0;
        int osVersionRaw = meta != null ? meta.optInt("os_version_raw", 0) : 0;
        int headerSize = meta != null ? meta.optInt("header_size", 4096) : 4096;
        int pageSize = meta != null ? meta.optInt("page_size", 4096) : 4096;
        String cmdline = meta != null ? meta.optString("cmdline", "") : "";
        String cmdlineExtra = meta != null ? meta.optString("cmdline_extra", "") : "";
        String compType = meta != null ? meta.optString("ramdisk_compression", "lz4") : "lz4";

        File kernelFile = new File(inputDir, "kernel");
        if (kernelFile.exists()) {
            kernelSize = (int) kernelFile.length();
        }

        // Pack ramdisk directory to CPIO
        File tempCpio = new File(inputDir, "temp_repack.cpio");
        File treeFile = new File(inputDir, "ramdisk_tree.json");
        List<RamdiskManifest.Entry> originalManifest = RamdiskManifest.loadManifest(treeFile);
        CpioEngine.packFolderToFile(ramdiskSrc, tempCpio, originalManifest);

        // Compress ramdisk
        File tempCompressed = new File(inputDir, "temp_repack.compressed");
        if ("gzip".equalsIgnoreCase(compType)) {
            try (FileInputStream fis = new FileInputStream(tempCpio);
                 FileOutputStream fos = new FileOutputStream(tempCompressed);
                 GZIPOutputStream gos = new GZIPOutputStream(fos)) {
                byte[] b = new byte[65536];
                int r;
                while ((r = fis.read(b)) > 0) gos.write(b, 0, r);
            }
        } else {
            // Default LZ4 Legacy
            boolean success = Lz4Engine.compressLegacyFile(tempCpio, tempCompressed);
            if (!success || !tempCompressed.exists() || tempCompressed.length() < 4) {
                byte[] cpioBytes = readFile(tempCpio);
                byte[] lz4Bytes = Lz4Engine.compressLegacy(cpioBytes);
                try (FileOutputStream fos = new FileOutputStream(tempCompressed)) {
                    fos.write(lz4Bytes);
                }
            }
        }

        int compressedRamdiskLen = (int) tempCompressed.length();

        byte[] headerBuf = new byte[pageSize];
        System.arraycopy("ANDROID!".getBytes(StandardCharsets.US_ASCII), 0, headerBuf, 0, 8);

        ByteBuffer bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(8, kernelSize);
        bb.putInt(12, compressedRamdiskLen);
        bb.putInt(16, osVersionRaw);
        bb.putInt(20, headerSize);
        if (headerVersion < 3) {
            bb.putInt(36, pageSize);
        }
        bb.putInt(40, headerVersion);

        byte[] cmdBytes = cmdline.getBytes(StandardCharsets.UTF_8);
        int cmdLen = Math.min(cmdBytes.length, 1536);
        System.arraycopy(cmdBytes, 0, headerBuf, 44, cmdLen);

        if (!cmdlineExtra.isEmpty() && headerSize > 1580) {
            byte[] extraBytes = cmdlineExtra.getBytes(StandardCharsets.UTF_8);
            int extraLen = Math.min(extraBytes.length, 512);
            System.arraycopy(extraBytes, 0, headerBuf, 1580, extraLen);
        }

        long originalImgSize = meta != null ? meta.optLong("original_image_size", 0) : 0;

        File parentOut = outputImageFile.getParentFile();
        if (parentOut != null) parentOut.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outputImageFile);
             java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(fos, 65536)) {
            bos.write(headerBuf);

            // Write Kernel if exists
            if (kernelSize > 0 && kernelFile.exists()) {
                try (FileInputStream fis = new FileInputStream(kernelFile)) {
                    byte[] b = new byte[65536];
                    int r;
                    while ((r = fis.read(b)) > 0) bos.write(b, 0, r);
                }
                writePadding(bos, kernelSize, pageSize);
            }

            // Write Ramdisk
            try (FileInputStream fis = new FileInputStream(tempCompressed)) {
                byte[] b = new byte[65536];
                int r;
                while ((r = fis.read(b)) > 0) bos.write(b, 0, r);
            }
            writePadding(bos, compressedRamdiskLen, pageSize);

            // Write AVB vbmeta if present
            File vbmetaFile = new File(inputDir, "avb_vbmeta.bin");
            boolean hasAvb = vbmetaFile.exists() && (meta != null && meta.optBoolean("has_avb_footer", false));
            long vbmetaOffset = 0;
            long vbmetaLen = 0;
            if (hasAvb) {
                bos.flush();
                vbmetaOffset = outputImageFile.length();
                vbmetaLen = vbmetaFile.length();
                try (FileInputStream fis = new FileInputStream(vbmetaFile)) {
                    byte[] b = new byte[65536];
                    int r;
                    while ((r = fis.read(b)) > 0) bos.write(b, 0, r);
                }
                writePadding(bos, vbmetaLen, pageSize);
            }

            bos.flush();

            // Maintain partition / original size padding so the boot image matches partition size
            long written = outputImageFile.length();
            if (hasAvb && originalImgSize >= written + 64) {
                long needed = (originalImgSize - 64) - written;
                byte[] zeros = new byte[65536];
                while (needed > 0) {
                    int chunk = (int) Math.min(zeros.length, needed);
                    bos.write(zeros, 0, chunk);
                    needed -= chunk;
                }
                // Write AVB footer (64 bytes big-endian)
                ByteBuffer fbb = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);
                fbb.put("AVBf".getBytes(StandardCharsets.US_ASCII));
                fbb.putInt(4, 1); // version_major
                fbb.putInt(8, 0); // version_minor
                fbb.putLong(12, vbmetaOffset); // original_image_size / image_size
                fbb.putLong(20, vbmetaOffset); // vbmeta_offset
                fbb.putLong(28, vbmetaLen);    // vbmeta_size
                bos.write(fbb.array());
                bos.flush();
            } else if (originalImgSize > written) {
                long needed = originalImgSize - written;
                byte[] zeros = new byte[65536];
                while (needed > 0) {
                    int chunk = (int) Math.min(zeros.length, needed);
                    bos.write(zeros, 0, chunk);
                    needed -= chunk;
                }
                bos.flush();
            }
        }

        tempCpio.delete();
        tempCompressed.delete();

        JSONObject res = new JSONObject();
        res.put("status", "success");
        res.put("output_image", outputImageFile.getAbsolutePath());
        res.put("ramdisk_size", compressedRamdiskLen);
        return res;
    }

    private static void writePadding(java.io.OutputStream out, long size, int pageSize) throws IOException {
        long padded = align(size, pageSize);
        long padLen = padded - size;
        if (padLen > 0) {
            byte[] zeros = new byte[(int) padLen];
            out.write(zeros);
        }
    }

    private static long align(long val, int pageSize) {
        return ((val + pageSize - 1) / pageSize) * pageSize;
    }

    private static void copyBytes(FileInputStream in, FileOutputStream out, long count) throws IOException {
        byte[] buf = new byte[65536];
        long remaining = count;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) break;
            out.write(buf, 0, r);
            remaining -= r;
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = fis.read(buf)) > 0) fos.write(buf, 0, r);
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

    private static String readNullTermString(byte[] b, int offset, int maxLen) {
        int end = offset;
        int limit = Math.min(b.length, offset + maxLen);
        while (end < limit && b[end] != 0) end++;
        return new String(b, offset, end - offset, StandardCharsets.UTF_8).trim();
    }
}
