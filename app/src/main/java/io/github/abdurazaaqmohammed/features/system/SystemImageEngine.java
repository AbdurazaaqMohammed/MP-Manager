package io.github.abdurazaaqmohammed.features.system;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import io.github.abdurazaaqmohammed.features.dumper.PartitionItem;

/**
 * Direct file and image engine for Android partitions (.img files).
 * Performs:
 * 1. Physical magic bytes and header inspection (Boot, init_boot, recovery, vendor_boot, vbmeta, dtbo, etc.).
 * 2. Android Sparse Image detection and pure Java unsparse engine (simg2img).
 * 3. Partition compatibility and type mismatch validation.
 */
public final class SystemImageEngine {

    public enum ImageType {
        EROFS,
        EXT4,
        SPARSE,
        UNKNOWN
    }

    public interface ProgressCallback {
        void onProgress(String message, int percent);
        void onError(String error);
        void onSuccess(File result);
    }

    private SystemImageEngine() {}

    public static class ImageIdentity {
        public final String typeKey;
        public final String displayNameEn;
        public final String displayNameAr;
        public final String details;
        public final boolean isBootOrRecovery;
        public final boolean isFilesystem;
        public final boolean isLikely;

        public ImageIdentity(String typeKey, String displayNameEn, String displayNameAr,
                             String details, boolean isBootOrRecovery, boolean isFilesystem, boolean isLikely) {
            this.typeKey = typeKey;
            this.displayNameEn = displayNameEn;
            this.displayNameAr = displayNameAr;
            this.details = details;
            this.isBootOrRecovery = isBootOrRecovery;
            this.isFilesystem = isFilesystem;
            this.isLikely = isLikely;
        }

        public ImageIdentity(String typeKey, String displayNameEn, String displayNameAr,
                             String details, boolean isBootOrRecovery, boolean isFilesystem) {
            this(typeKey, displayNameEn, displayNameAr, details, isBootOrRecovery, isFilesystem, false);
        }

        public String getDisplayName(boolean isAr) {
            return isAr ? displayNameAr : displayNameEn;
        }
    }

    public static ImageIdentity identifyBytes(byte[] head, long fileLength) {
        if (head == null || head.length < 8) {
            return new ImageIdentity("unknown", "Unknown / Raw Data", "بيانات خام / غير معروف", "", false, false, false);
        }

        // 1. Android Boot Image: "ANDROID!" (0x41 0x4E 0x44 0x52 0x4F 0x49 0x44 0x21)
        if (head[0] == 0x41 && head[1] == 0x4E && head[2] == 0x44 && head[3] == 0x52
                && head[4] == 0x4F && head[5] == 0x49 && head[6] == 0x44 && head[7] == 0x21) {
            int headerVersion = 0;
            if (head.length >= 44) {
                headerVersion = ((head[43] & 0xFF) << 24) | ((head[42] & 0xFF) << 16) | ((head[41] & 0xFF) << 8) | (head[40] & 0xFF);
            }
            long kernelSize = 0;
            if (head.length >= 12) {
                kernelSize = ((long) (head[11] & 0xFF) << 24) | ((head[10] & 0xFF) << 16) | ((head[9] & 0xFF) << 8) | (head[8] & 0xFF);
            }
            long ramdiskSize = 0;
            if (headerVersion >= 3) {
                if (head.length >= 16) {
                    ramdiskSize = ((long) (head[15] & 0xFF) << 24) | ((head[14] & 0xFF) << 16) | ((head[13] & 0xFF) << 8) | (head[12] & 0xFF);
                }
            } else {
                if (head.length >= 20) {
                    ramdiskSize = ((long) (head[19] & 0xFF) << 24) | ((head[18] & 0xFF) << 16) | ((head[17] & 0xFF) << 8) | (head[16] & 0xFF);
                }
            }

            if (kernelSize == 0 && ramdiskSize > 0) {
                if (ramdiskSize > 30L * 1024 * 1024) {
                    String en = "Android Boot Image (Likely recovery - Header v" + headerVersion + ", Ramdisk: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(ramdiskSize) + ")";
                    String ar = "صورة إقلاع أندرويد (مرجح: ريكفري / Likely recovery - إصدار v" + headerVersion + " - رام ديسك: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(ramdiskSize) + ")";
                    return new ImageIdentity("recovery", en, ar, "Recovery", true, false, true);
                } else {
                    String en = "Android Boot Image (Likely init_boot - Header v" + headerVersion + ", Ramdisk: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(ramdiskSize) + ")";
                    String ar = "صورة إقلاع أندرويد (مرجح: init_boot / Likely init_boot - إصدار v" + headerVersion + " - رام ديسك: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(ramdiskSize) + ")";
                    return new ImageIdentity("init_boot", en, ar, "init_boot", true, false, true);
                }
            } else if (kernelSize > 0 && ramdiskSize == 0) {
                String en = "Android Boot Image (Likely boot / GKI Kernel - Header v" + headerVersion + ", Kernel: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(kernelSize) + ")";
                String ar = "صورة إقلاع أندرويد (مرجح: بوت كيرنل GKI / Likely boot - إصدار v" + headerVersion + " - كيرنل: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(kernelSize) + ")";
                return new ImageIdentity("boot_gki", en, ar, "GKI Kernel", true, false, true);
            } else {
                String en = "Android Boot Image (Likely boot - Header v" + headerVersion + ", Kernel: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(kernelSize) + ", Ramdisk: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(ramdiskSize) + ")";
                String ar = "صورة إقلاع أندرويد (مرجح: بوت / Likely boot - إصدار v" + headerVersion + " - كيرنل ورام ديسك)";
                return new ImageIdentity("boot", en, ar, "Kernel + Ramdisk", true, false, true);
            }
        }

        // 2. Android Vendor Boot: "VNDRBOOT" (0x56 0x4E 0x44 0x52 0x42 0x4F 0x4F 0x54)
        if (head[0] == 0x56 && head[1] == 0x4E && head[2] == 0x44 && head[3] == 0x52
                && head[4] == 0x42 && head[5] == 0x4F && head[6] == 0x4F && head[7] == 0x54) {
            int ver = head.length >= 20 ? (((head[19] & 0xFF) << 24) | ((head[18] & 0xFF) << 16) | ((head[17] & 0xFF) << 8) | (head[16] & 0xFF)) : 0;
            String en = "Android Vendor Boot Image (vendor_boot - Header v" + ver + ")";
            String ar = "صورة إقلاع الفيندور (vendor_boot - إصدار v" + ver + ")";
            return new ImageIdentity("vendor_boot", en, ar, "vendor_boot", true, false, false);
        }

        // 3. Android Verified Boot Metadata: "AVB0" (0x41 0x56 0x42 0x30)
        if (head[0] == 0x41 && head[1] == 0x56 && head[2] == 0x42 && head[3] == 0x30) {
            String en = "Android Verified Boot Metadata (vbmeta / AVB)";
            String ar = "بيانات حماية التحقق من النظام (vbmeta / AVB)";
            return new ImageIdentity("vbmeta", en, ar, "AVB", false, false, false);
        }

        // 4. Android DTBO: 0xD7B7AB1E (1E AB B7 D7)
        if ((head[0] & 0xFF) == 0x1E && (head[1] & 0xFF) == 0xAB && (head[2] & 0xFF) == 0xB7 && (head[3] & 0xFF) == 0xD7) {
            String en = "Android Device Tree Blob Overlay (dtbo)";
            String ar = "صورة تعريفات العتاد (dtbo)";
            return new ImageIdentity("dtbo", en, ar, "DTBO", false, false, false);
        }

        // 5. Android Sparse Image: 0xED26FF3A (3A FF 26 ED)
        if ((head[0] & 0xFF) == 0x3A && (head[1] & 0xFF) == 0xFF && (head[2] & 0xFF) == 0x26 && (head[3] & 0xFF) == 0xED) {
            String en = "Android Sparse Compressed Image";
            String ar = "صورة أندرويد مضغوطة (Android Sparse Image)";
            return new ImageIdentity("sparse", en, ar, "Sparse", false, true, false);
        }

        // 6. EROFS Filesystem at offset 1024: 0xE0F5E1E2 (E2 E1 F5 E0)
        if (head.length >= 1028) {
            if ((head[1024] & 0xFF) == 0xE2 && (head[1025] & 0xFF) == 0xE1
                    && (head[1026] & 0xFF) == 0xF5 && (head[1027] & 0xFF) == 0xE0) {
                String en = "EROFS Linux Filesystem (Read-Only OS)";
                String ar = "نظام ملفات EROFS مضغوط (نظام تشغيل للقراءة فقط)";
                return new ImageIdentity("erofs", en, ar, "EROFS", false, true, false);
            }
        }

        // 7. EXT4 / EXT3 / EXT2 Filesystem at offset 1080: 0xEF53 (53 EF)
        if (head.length >= 1082) {
            if ((head[1080] & 0xFF) == 0x53 && (head[1081] & 0xFF) == 0xEF) {
                String label = "";
                if (head.length >= 1160) {
                    byte[] lblBytes = new byte[16];
                    System.arraycopy(head, 1144, lblBytes, 0, 16);
                    int len = 0;
                    while (len < 16 && lblBytes[len] != 0 && lblBytes[len] >= 32 && lblBytes[len] <= 126) {
                        len++;
                    }
                    if (len > 0) label = new String(lblBytes, 0, len, StandardCharsets.US_ASCII).trim();
                }
                boolean hasLabel = !label.isEmpty();
                String en = hasLabel ? ("EXT4 Filesystem (Likely partition: " + label + ")") : "EXT4 Linux Filesystem (Read-Write)";
                String ar = hasLabel ? ("نظام ملفات EXT4 (مرجح: قسم " + label + ")") : "نظام ملفات EXT4 لينكس (قابل للقراءة والكتابة)";
                return new ImageIdentity("ext4", en, ar, label, false, true, hasLabel);
            }
        }

        // 8. F2FS Filesystem at offset 1024: 0xF2F52010 (10 20 F5 F2)
        if (head.length >= 1028) {
            if ((head[1024] & 0xFF) == 0x10 && (head[1025] & 0xFF) == 0x20
                    && (head[1026] & 0xFF) == 0xF5 && (head[1027] & 0xFF) == 0xF2) {
                String en = "F2FS Flash Filesystem (Userdata / Storage)";
                String ar = "نظام ملفات F2FS (بيانات المستخدم / الذاكرة)";
                return new ImageIdentity("f2fs", en, ar, "F2FS", false, true);
            }
        }

        // 9. ISO 9660 / UDF Optical Disc (CD / DVD / Windows / Linux Install Media):
        // Standard Primary Volume Descriptor at sector 16 (offset 32768 = 0x8000)
        // Offset 32769: "CD001" (0x43 0x44 0x30 0x30 0x31)
        if (head.length >= 32774) {
            if (head[32769] == 0x43 && head[32770] == 0x44 && head[32771] == 0x30
                    && head[32772] == 0x30 && head[32773] == 0x31) {
                String volId = "";
                if (head.length >= 32768 + 72) {
                    byte[] volBytes = new byte[32];
                    System.arraycopy(head, 32768 + 40, volBytes, 0, 32);
                    volId = new String(volBytes, StandardCharsets.US_ASCII).trim();
                }
                String en = volId.isEmpty() ? "ISO 9660 Optical Disc Image (CD/DVD)" : ("ISO 9660 Disc Image (Windows / Linux: " + volId + ")");
                String ar = volId.isEmpty() ? "صورة قرص ضوئي ISO 9660 (أسطوانة CD/DVD)" : ("صورة قرص ضوئي ISO 9660 (نسخة ويندوز / لينكس: " + volId + ")");
                return new ImageIdentity("iso", en, ar, volId, false, true);
            }
        }

        // 10. Windows NTFS: offset 3 has "NTFS    " (0x4E 0x54 0x46 0x53 0x20 0x20)
        if (head.length >= 11) {
            if (head[3] == 0x4E && head[4] == 0x54 && head[5] == 0x46 && head[6] == 0x53
                    && head[7] == 0x20 && head[8] == 0x20) {
                String en = "Windows NTFS Filesystem Image";
                String ar = "نظام ملفات ويندوز NTFS";
                return new ImageIdentity("ntfs", en, ar, "NTFS", false, true);
            }
        }

        // 11. FAT32 / FAT16
        if (head.length >= 90) {
            String fat32 = new String(head, 82, 8, StandardCharsets.US_ASCII);
            String fat16 = new String(head, 54, 8, StandardCharsets.US_ASCII);
            if (fat32.startsWith("FAT32") || fat16.startsWith("FAT16") || fat16.startsWith("FAT12")) {
                String en = "FAT Filesystem Image (EFI / Boot Partition)";
                String ar = "نظام ملفات FAT (قسم الإقلاع EFI / Boot)";
                return new ImageIdentity("fat", en, ar, "FAT", false, true);
            }
        }

        return new ImageIdentity("unknown", "Raw Data / Image File", "بيانات قرص خام (Raw Image)", "", false, false);
    }

    public static ImageIdentity identifyImage(File imgFile) {
        if (imgFile == null) return new ImageIdentity("unknown", "Unknown", "غير معروف", "", false, false);
        byte[] buffer = new byte[36864];
        int bytesRead = 0;
        long totalLen = imgFile.length();

        // 1. Try standard Java reading
        try (RandomAccessFile raf = new RandomAccessFile(imgFile, "r")) {
            bytesRead = raf.read(buffer);
        } catch (Exception e) {
            // 2. If permission denied or block device, try root
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", "toybox head -c 36864 '" + imgFile.getAbsolutePath() + "' || head -c 36864 '" + imgFile.getAbsolutePath() + "'"});
                try (java.io.InputStream is = p.getInputStream()) {
                    int r;
                    while (bytesRead < buffer.length && (r = is.read(buffer, bytesRead, buffer.length - bytesRead)) != -1) {
                        bytesRead += r;
                    }
                }
                p.waitFor();
            } catch (Exception ignored) {
                if (p != null) p.destroy();
            }
        }

        if (bytesRead < 8) {
            return new ImageIdentity("unknown", "Unknown / Unreadable", "غير معروف / يتعذر القراءة", "", false, false);
        }

        byte[] validHead;
        if (bytesRead == buffer.length) {
            validHead = buffer;
        } else {
            validHead = new byte[bytesRead];
            System.arraycopy(buffer, 0, validHead, 0, bytesRead);
        }

        return identifyBytes(validHead, totalLen);
    }

    /**
     * Identifies the filesystem type of a .img file by reading magic bytes.
     */
    public static ImageType detectImageType(File imgFile) {
        ImageIdentity id = identifyImage(imgFile);
        if ("sparse".equals(id.typeKey)) return ImageType.SPARSE;
        if ("erofs".equals(id.typeKey)) return ImageType.EROFS;
        if ("ext4".equals(id.typeKey)) return ImageType.EXT4;
        return ImageType.UNKNOWN;
    }

    /**
     * Checks if a diagnosed file image identity is incompatible with the target partition.
     * Returns true if there is a clear, dangerous mismatch (e.g. flashing recovery to boot,
     * or vbmeta to system, or filesystem to boot).
     */
    public static boolean isTypeMismatch(PartitionItem item, ImageIdentity fileId, ImageIdentity partitionLiveId) {
        if (item == null || fileId == null) return false;
        String fileType = fileId.typeKey != null ? fileId.typeKey.toLowerCase() : "unknown";
        if ("unknown".equals(fileType)) return false; // Raw or unidentified data: do not false-alarm

        // Determine base partition name without slot suffix
        String raw = item.rawName != null ? item.rawName.toLowerCase() : "";
        if (raw.endsWith("_a") || raw.endsWith("_b")) {
            raw = raw.substring(0, raw.length() - 2);
        } else if (raw.endsWith("-a") || raw.endsWith("-b")) {
            raw = raw.substring(0, raw.length() - 2);
        }

        // 1. Filesystem images (erofs, ext4, f2fs, sparse)
        if (fileId.isFilesystem || "sparse".equals(fileType) || "erofs".equals(fileType) || "ext4".equals(fileType) || "f2fs".equals(fileType)) {
            // If target partition is a boot-class or firmware partition (boot, init_boot, recovery, vendor_boot, vbmeta, dtbo)
            if (raw.equals("boot") || raw.equals("init_boot") || raw.equals("recovery")
                    || raw.equals("vendor_boot") || raw.startsWith("vbmeta") || raw.equals("dtbo")) {
                return true; // Flashing filesystem to boot/vbmeta/dtbo is a fatal mismatch
            }
        }

        // 2. ISO disc images
        if ("iso".equals(fileType) || "ntfs".equals(fileType)) {
            return true; // Flashing optical/Windows ISO to Android internal partitions is a mismatch
        }

        // 3. Boot / Recovery / Init_Boot / Kernel images
        if (fileId.isBootOrRecovery || "boot".equals(fileType) || "boot_gki".equals(fileType)
                || "init_boot".equals(fileType) || "recovery".equals(fileType) || "vendor_boot".equals(fileType)) {
            // If target is an OS filesystem / dynamic partition (system, vendor, product, odm, userdata, super)
            if (item.isSystemOrVendor() || item.isDynamic || raw.equals("system") || raw.equals("vendor")
                    || raw.equals("product") || raw.equals("system_ext") || raw.equals("odm")
                    || raw.equals("super") || raw.equals("userdata") || raw.equals("data")) {
                return true; // Flashing boot/recovery to system/userdata is a fatal mismatch
            }

            // Between Boot, Init_Boot, Recovery, Vendor_Boot:
            if (raw.equals("boot")) {
                // Boot partition needs a boot image with kernel.
                // If the file is specifically diagnosed as recovery, init_boot, or vendor_boot -> mismatch!
                if ("recovery".equals(fileType) || "init_boot".equals(fileType) || "vendor_boot".equals(fileType)) {
                    return true;
                }
            } else if (raw.equals("init_boot")) {
                // init_boot requires ramdisk-only generic init_boot.
                // If file is boot_gki (kernel only), recovery, or vendor_boot -> mismatch!
                if ("boot_gki".equals(fileType) || "recovery".equals(fileType) || "vendor_boot".equals(fileType)) {
                    return true;
                }
            } else if (raw.equals("recovery")) {
                // Recovery requires recovery ramdisk.
                // If file is boot_gki (kernel with 0 ramdisk), init_boot (not recovery), or vendor_boot -> mismatch!
                if ("boot_gki".equals(fileType) || "init_boot".equals(fileType) || "vendor_boot".equals(fileType)) {
                    return true;
                }
            } else if (raw.equals("vendor_boot")) {
                if (!"vendor_boot".equals(fileType)) {
                    return true;
                }
            } else if (raw.startsWith("vbmeta") || raw.equals("dtbo")) {
                return true; // Flashing boot image to vbmeta or dtbo
            }
        }

        // 4. VBMeta images
        if ("vbmeta".equals(fileType)) {
            if (!raw.startsWith("vbmeta")) {
                return true; // Flashing vbmeta to boot, system, recovery, etc.
            }
        } else if (raw.startsWith("vbmeta")) {
            // Target is vbmeta, but file is not vbmeta
            return true;
        }

        // 5. DTBO images
        if ("dtbo".equals(fileType)) {
            if (!raw.equals("dtbo")) {
                return true;
            }
        } else if (raw.equals("dtbo")) {
            return true;
        }

        // 6. Check against live hardware partition type if available
        if (partitionLiveId != null && !"unknown".equals(partitionLiveId.typeKey)) {
            String liveKey = partitionLiveId.typeKey.toLowerCase();
            if (partitionLiveId.isFilesystem && fileId.isBootOrRecovery) {
                return true;
            }
            if (partitionLiveId.isBootOrRecovery && fileId.isFilesystem) {
                return true;
            }
            if ("vbmeta".equals(liveKey) && !"vbmeta".equals(fileType)) {
                return true;
            }
            if (!"vbmeta".equals(liveKey) && "vbmeta".equals(fileType)) {
                return true;
            }
            if ("dtbo".equals(liveKey) && !"dtbo".equals(fileType)) {
                return true;
            }
            if (!"dtbo".equals(liveKey) && "dtbo".equals(fileType)) {
                return true;
            }
        }

        return false;
    }

    public static class SparseInfo {
        public final int blockSize;
        public final long totalBlocks;
        public final int totalChunks;
        public final long uncompressedSizeBytes;

        public SparseInfo(int blockSize, long totalBlocks, int totalChunks, long uncompressedSizeBytes) {
            this.blockSize = blockSize;
            this.totalBlocks = totalBlocks;
            this.totalChunks = totalChunks;
            this.uncompressedSizeBytes = uncompressedSizeBytes;
        }
    }

    public static SparseInfo readSparseInfo(File file) {
        if (file == null || !file.isFile() || file.length() < 28) return null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] h = new byte[28];
            raf.readFully(h);
            int magic = ((h[3] & 0xFF) << 24) | ((h[2] & 0xFF) << 16) | ((h[1] & 0xFF) << 8) | (h[0] & 0xFF);
            if (magic != 0xED26FF3A) return null;

            int blkSz = ((h[15] & 0xFF) << 24) | ((h[14] & 0xFF) << 16) | ((h[13] & 0xFF) << 8) | (h[12] & 0xFF);
            long totalBlks = ((long) (h[19] & 0xFF) << 24) | ((h[18] & 0xFF) << 16) | ((h[17] & 0xFF) << 8) | (h[16] & 0xFF);
            int totalChunks = ((h[23] & 0xFF) << 24) | ((h[22] & 0xFF) << 16) | ((h[21] & 0xFF) << 8) | (h[20] & 0xFF);
            long uncompressedBytes = totalBlks * (long) blkSz;

            return new SparseInfo(blkSz, totalBlks, totalChunks, uncompressedBytes);
        } catch (Exception e) {
            return null;
        }
    }

    public static void unsparseToStream(File sparseFile, OutputStream os, ProgressCallback cb) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sparseFile), 1024 * 1024)) {
            byte[] h = new byte[28];
            int readH = bis.read(h);
            if (readH < 28) throw new IOException("Invalid sparse header");

            int magic = ((h[3] & 0xFF) << 24) | ((h[2] & 0xFF) << 16) | ((h[1] & 0xFF) << 8) | (h[0] & 0xFF);
            if (magic != 0xED26FF3A) throw new IOException("Not a sparse image");

            int fileHdrSz = ((h[7] & 0xFF) << 8) | (h[6] & 0xFF);
            int chunkHdrSz = ((h[9] & 0xFF) << 8) | (h[8] & 0xFF);
            int blkSz = ((h[15] & 0xFF) << 24) | ((h[14] & 0xFF) << 16) | ((h[13] & 0xFF) << 8) | (h[12] & 0xFF);
            long totalBlks = ((long) (h[19] & 0xFF) << 24) | ((h[18] & 0xFF) << 16) | ((h[17] & 0xFF) << 8) | (h[16] & 0xFF);
            int totalChunks = ((h[23] & 0xFF) << 24) | ((h[22] & 0xFF) << 16) | ((h[21] & 0xFF) << 8) | (h[20] & 0xFF);

            if (fileHdrSz > 28) {
                long toSkip = fileHdrSz - 28;
                while (toSkip > 0) {
                    long s = bis.skip(toSkip);
                    if (s <= 0) break;
                    toSkip -= s;
                }
            }

            long writtenBytes = 0;
            long totalBytes = totalBlks * (long) blkSz;
            byte[] chunkHdr = new byte[12];
            byte[] buffer = new byte[64 * 1024];
            byte[] zeroBuf = new byte[64 * 1024];

            for (int chunk = 0; chunk < totalChunks; chunk++) {
                int rHdr = bis.read(chunkHdr);
                if (rHdr < 12) break;

                int chunkType = ((chunkHdr[1] & 0xFF) << 8) | (chunkHdr[0] & 0xFF);
                int chunkSz = ((chunkHdr[7] & 0xFF) << 24) | ((chunkHdr[6] & 0xFF) << 16) | ((chunkHdr[5] & 0xFF) << 8) | (chunkHdr[4] & 0xFF);

                long outBytes = (long) chunkSz * blkSz;

                if (chunkHdrSz > 12) {
                    long toSkip = chunkHdrSz - 12;
                    while (toSkip > 0) {
                        long s = bis.skip(toSkip);
                        if (s <= 0) break;
                        toSkip -= s;
                    }
                }

                if (chunkType == 0xCAC1) { // CHUNK_TYPE_RAW
                    long toRead = outBytes;
                    while (toRead > 0) {
                        int want = (int) Math.min(buffer.length, toRead);
                        int r = bis.read(buffer, 0, want);
                        if (r == -1) throw new IOException("Unexpected EOF reading RAW chunk");
                        os.write(buffer, 0, r);
                        toRead -= r;
                        writtenBytes += r;
                    }
                } else if (chunkType == 0xCAC2) { // CHUNK_TYPE_FILL
                    byte[] fillVal = new byte[4];
                    int rFill = bis.read(fillVal);
                    if (rFill < 4) throw new IOException("Unexpected EOF reading FILL chunk");

                    byte[] fillPattern = new byte[Math.min(65536, (int) Math.max(4, outBytes))];
                    for (int i = 0; i < fillPattern.length; i += 4) {
                        System.arraycopy(fillVal, 0, fillPattern, i, Math.min(4, fillPattern.length - i));
                    }

                    long toWrite = outBytes;
                    while (toWrite > 0) {
                        int want = (int) Math.min(fillPattern.length, toWrite);
                        os.write(fillPattern, 0, want);
                        toWrite -= want;
                        writtenBytes += want;
                    }
                } else if (chunkType == 0xCAC3) { // CHUNK_TYPE_DONT_CARE
                    long toWrite = outBytes;
                    while (toWrite > 0) {
                        int want = (int) Math.min(zeroBuf.length, toWrite);
                        os.write(zeroBuf, 0, want);
                        toWrite -= want;
                        writtenBytes += want;
                    }
                } else if (chunkType == 0xCAC4) { // CHUNK_TYPE_CRC32
                    byte[] crc = new byte[4];
                    bis.read(crc);
                }

                if (cb != null && totalBytes > 0 && chunk % 10 == 0) {
                    int percent = (int) ((writtenBytes * 100) / totalBytes);
                    cb.onProgress("Unsparsing: " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(writtenBytes) + " / " + io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(totalBytes), percent);
                }
            }
            os.flush();
        }
    }

    public static void unsparseToFile(File sparseFile, File outputFile, ProgressCallback cb) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile), 1024 * 1024)) {
            unsparseToStream(sparseFile, bos, cb);
            if (cb != null) {
                cb.onSuccess(outputFile);
            }
        } catch (Exception e) {
            outputFile.delete();
            if (cb != null) {
                cb.onError(e.getMessage());
            }
            throw e;
        }
    }

    public static boolean isSparseImage(File f) {
        if (f == null || !f.isFile()) return false;
        ImageIdentity id = identifyImage(f);
        return id != null && "sparse".equals(id.typeKey);
    }
}
