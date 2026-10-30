package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of LibPatcher (TDOhex): writes ARM/AArch64 machine-code stubs at
 * user supplied offsets inside a native library. The original tool drove
 * radare2 through r2pipe only to run "wx <bytes> @ <offset>", so the patching
 * itself is a plain byte write — the values are hardcoded instruction
 * encodings and the architecture comes from the ELF header, which removes the
 * radare2/python dependency entirely.
 *
 * Offsets follow the same semantics as the original tool (r2 "wx"): they are
 * virtual addresses and are translated to file offsets through the PT_LOAD
 * program headers (identity for the typical Android .so layout).
 */
public final class NativeLibPatcher {

    private NativeLibPatcher() {
    }

    /** Patch types, in the order the original tool prompts for them. */
    public static final String[] PATCH_TYPES = {
            "boolean_true", "boolean_false", "integer_zero", "integer16_max",
            "integer32_max", "long_zero", "long_64", "float_zero",
            "double_zero", "void_nop"
    };

    private static final int EM_ARM = 40;
    private static final int EM_AARCH64 = 183;

    /**
     * Instruction stubs per data type, big-endian hex as in the original tool.
     * The arm64 column matches LibPatcher verbatim; the arm32 column matches it
     * too except void_nop, whose original value was a truncated 6-byte
     * sequence — it is BX LR here.
     */
    private static String valueFor(String type, boolean arm64) {
        switch (type) {
            case "boolean_true":
                return arm64 ? "20008052C0035FD6" : "0100A0E31EFF2FE1";
            case "boolean_false":
                return arm64 ? "00008052C0035FD6" : "0000A0E31EFF2FE1";
            case "integer_zero":
                return arm64 ? "00008052C0035FD6" : "0000A0E31EFF2FE1";
            case "integer16_max":
                return arm64 ? "E0FF8F52C0035FD6" : "FF0F07E31EFF2FE1";
            case "integer32_max":
                return arm64 ? "E0FF9F52E0FFAF72C0035FD6" : "FF0F0FE3FF0F47E31EFF2FE1";
            case "long_zero":
                return arm64 ? "00008052C0035FD6" : "0000A0E31EFF2FE1";
            case "long_64":
                return arm64 ? "E0FF9FD2E0FFBFF2E00FC0F2C0035FD6" : "0201E0E31EFF2FE1";
            case "float_zero":
                return arm64 ? "E003271EC0035FD6" : "0000A0E31EFF2FE1";
            case "double_zero":
                return arm64 ? "E003679EC0035FD6" : "0000A0E31EFF2FE1";
            case "void_nop":
                return arm64 ? "C0035FD6" : "1EFF2FE1";
            default:
                return null;
        }
    }

    /**
     * Returns "arm64-v8a" or "armeabi-v7a" for supported libraries, a short
     * unsupported marker ("x86", "x86_64", ...) for others, or null for files
     * that are not little-endian ELF binaries.
     */
    public static String detectArch(byte[] so) {
        if (so == null || so.length < 20) return null;
        if (so[0] != 0x7f || so[1] != 'E' || so[2] != 'L' || so[3] != 'F') return null;
        if (so[5] != 1) return null; // big-endian: not supported
        boolean is64 = so[4] == 2;
        int machine = (so[18] & 0xFF) | ((so[19] & 0xFF) << 8);
        if (machine == EM_AARCH64) return "arm64-v8a";
        if (machine == EM_ARM) return "armeabi-v7a";
        if (machine == 3) return "x86";
        if (machine == 62) return "x86_64";
        return "unknown(" + machine + ")";
    }

    /** All lib/...*.so entries in an APK. */
    public static List<String> listNativeLibs(File apk) throws IOException {
        List<String> result = new ArrayList<>();
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String name = h.getFileName();
                if (!h.isDirectory() && name.startsWith("lib/") && name.endsWith(".so")) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * entry name -> detected architecture label, for every lib/*.so in the
     * APK. Unsupported binaries are still listed so the user can see them.
     */
    public static Map<String, String> nativeLibArchives(File apk) {
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String name = h.getFileName();
                if (h.isDirectory() || !name.startsWith("lib/") || !name.endsWith(".so")) continue;
                String arch;
                try (InputStream is = zf.getInputStream(h)) {
                    byte[] b = new byte[20];
                    int r = is.read(b);
                    arch = detectArch(r > 0 ? b : null);
                    if (arch == null) arch = "not ELF";
                } catch (Exception e) {
                    arch = "?";
                }
                result.put(name, arch);
            }
        } catch (Exception e) {
            // unreadable zip: leave whatever was collected
        }
        return result;
    }

    public static boolean hasNativeLibs(File apk) {
        try {
            return !listNativeLibs(apk).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** {@code type -> [offset, ...]} in parsing order. */
    public static Map<String, List<Long>> parseOffsets(Map<String, String> raw) throws IOException {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (value.isEmpty()) continue;
            List<Long> offsets = new ArrayList<>();
            for (String part : value.split(",")) {
                String p = part.trim();
                if (!p.matches("^0x[0-9a-fA-F]+$")) {
                    throw new IOException("Invalid offset: " + p);
                }
                offsets.add(Long.decode(p));
            }
            result.put(e.getKey(), offsets);
        }
        return result;
    }

    /**
     * Applies every offset patch to the raw .so image and returns the patched
     * bytes (the input array is modified in place and also returned).
     *
     * @param entryName zip entry name, only used for error messages
     */
    public static byte[] patch(byte[] so, String entryName, Map<String, List<Long>> offsetsByType)
            throws IOException {
        String arch = detectArch(so);
        if ("arm64-v8a".equals(arch)) {
            return patch(so, entryName, offsetsByType, true);
        }
        if ("armeabi-v7a".equals(arch)) {
            return patch(so, entryName, offsetsByType, false);
        }
        throw new IOException(entryName + ": unsupported architecture " + arch
                + " (only armeabi-v7a and arm64-v8a are supported)");
    }

    private static byte[] patch(byte[] so, String entryName, Map<String, List<Long>> offsetsByType,
                                boolean arm64) throws IOException {
        List<long[]> loads = loadSegments(so);
        for (Map.Entry<String, List<Long>> e : offsetsByType.entrySet()) {
            String hex = valueFor(e.getKey(), arm64);
            if (hex == null) throw new IOException("Unknown patch type " + e.getKey());
            byte[] stub = hexToBytes(hex);
            for (long vaddr : e.getValue()) {
                long fileOffset = vaddrToFileOffset(loads, vaddr);
                if (fileOffset < 0 || fileOffset + stub.length > so.length) {
                    throw new IOException("Offset 0x" + Long.toHexString(vaddr)
                            + " (" + e.getKey() + ") is outside of " + entryName);
                }
                System.arraycopy(stub, 0, so, (int) fileOffset, stub.length);
            }
        }
        return so;
    }

    /** PT_LOAD segments as {@code {vaddr, fileOffset, fileSize}} triples. */
    private static List<long[]> loadSegments(byte[] so) {
        List<long[]> result = new ArrayList<>();
        boolean is64 = so.length >= 20 && so[4] == 2;
        int phoffOff = is64 ? 32 : 28;
        int phentsizeOff = is64 ? 54 : 42;
        int phnumOff = is64 ? 56 : 44;
        int phEntHdrSize = is64 ? 56 : 32;
        if (so.length < phnumOff + 2) return result;
        long phoff = is64 ? u64(so, phoffOff) : u32(so, phoffOff);
        int phentsize = u16(so, phentsizeOff);
        int phnum = u16(so, phnumOff);
        for (int i = 0; i < phnum; i++) {
            long base = phoff + (long) i * phentsize;
            if (base + phEntHdrSize > so.length) break;
            if (u32(so, (int) base) != 1 /* PT_LOAD */) continue;
            long offset = is64 ? u64(so, (int) base + 8) : u32(so, (int) base + 4);
            long vaddr = is64 ? u64(so, (int) base + 16) : u32(so, (int) base + 8);
            long filesz = is64 ? u64(so, (int) base + 32) : u32(so, (int) base + 16);
            result.add(new long[]{vaddr, offset, filesz});
        }
        return result;
    }

    /** r2 semantics: the offset is a virtual address, translated via PT_LOAD. */
    private static long vaddrToFileOffset(List<long[]> loads, long vaddr) {
        for (long[] seg : loads) {
            if (vaddr >= seg[0] && vaddr < seg[0] + seg[2]) {
                return vaddr - seg[0] + seg[1];
            }
        }
        // Outside every PT_LOAD range: treat the value as a raw file offset so
        // addresses copied from tools that show paddr still land correctly.
        return vaddr;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static long u64(byte[] b, int off) {
        long result = 0;
        for (int i = 7; i >= 0; i--) {
            result = (result << 8) | (b[off + i] & 0xFFL);
        }
        return result;
    }
}
