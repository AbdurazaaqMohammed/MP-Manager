package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** ELF detection, PT_LOAD offset translation and stub patching of NativeLibPatcher. */
public class NativeLibPatchTest {

    private static void putU16(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

    private static void putU32(byte[] b, int off, long v) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) (v >> (8 * i));
    }

    private static void putU64(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) (v >> (8 * i));
    }

    private static byte[] hex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** Minimal ELF: header + one PT_LOAD + filler body. */
    private static byte[] elf(boolean is64, int machine, long vaddr, long fileOffset, int bodySize) {
        int ehsize = is64 ? 64 : 52;
        int phentsize = is64 ? 56 : 32;
        byte[] so = new byte[ehsize + phentsize + bodySize];
        so[0] = 0x7f;
        so[1] = 'E';
        so[2] = 'L';
        so[3] = 'F';
        so[4] = (byte) (is64 ? 2 : 1);
        so[5] = 1; // little endian
        putU16(so, 18, machine);
        putU32(so, 20, 1);
        if (is64) {
            putU16(so, 52, ehsize);
            putU16(so, 54, phentsize);
            putU16(so, 56, 1);
            putU64(so, 32, ehsize);       // e_phoff
            long ph = ehsize;
            putU32(so, (int) ph, 1);      // PT_LOAD
            putU32(so, (int) ph + 4, 5);  // R|X
            putU64(so, (int) ph + 8, fileOffset);
            putU64(so, (int) ph + 16, vaddr);
            putU64(so, (int) ph + 32, bodySize);
            putU64(so, (int) ph + 40, bodySize);
        } else {
            putU16(so, 40, ehsize);
            putU16(so, 42, phentsize);
            putU16(so, 44, 1);
            putU32(so, 28, ehsize);       // e_phoff
            long ph = ehsize;
            putU32(so, (int) ph, 1);      // PT_LOAD
            putU32(so, (int) ph + 4, fileOffset);
            putU32(so, (int) ph + 8, vaddr);
            putU32(so, (int) ph + 16, bodySize);
            putU32(so, (int) ph + 20, bodySize);
        }
        for (int i = ehsize + phentsize; i < so.length; i++) so[i] = 0x11;
        return so;
    }

    private static void assertBytesAt(byte[] so, long offset, String hex) {
        byte[] expected = hex(hex);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("at 0x" + Long.toHexString(offset + i),
                    expected[i], so[(int) (offset + i)]);
        }
    }

    @Test
    public void detectsArchitectures() {
        assertEquals("arm64-v8a", NativeLibPatcher.detectArch(elf(true, 183, 0x1000, 0x1000, 64)));
        assertEquals("armeabi-v7a", NativeLibPatcher.detectArch(elf(false, 40, 0x1000, 0x1000, 64)));
        assertEquals("x86_64", NativeLibPatcher.detectArch(elf(true, 62, 0x1000, 0x1000, 64)));
        assertNull(NativeLibPatcher.detectArch(new byte[]{1, 2, 3, 4}));
    }

    @Test
    public void patchesArm64AtIdentityVaddr() throws IOException {
        // identity mapping: vaddr == file offset for the body region
        byte[] so = elf(true, 183, 0x80, 0x80, 0x100);
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("boolean_true", "0xC2");
        NativeLibPatcher.patch(so, "lib/arm64-v8a/libtest.so", NativeLibPatcher.parseOffsets(raw));
        // MOV W0, #1; RET
        assertBytesAt(so, 0xC2, "20008052C0035FD6");
        // untouched fillers on both sides
        assertEquals(0x11, so[0xC1]);
        assertEquals(0x11, so[0xC2 + 8]);
    }

    @Test
    public void translatesNonIdentityProgramHeaders() throws IOException {
        // vaddr 0x1000 maps to file offset 0x80
        byte[] so = elf(true, 183, 0x1000, 0x80, 0x100);
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("void_nop", "0x1010");
        NativeLibPatcher.patch(so, "lib/arm64-v8a/libtest.so", NativeLibPatcher.parseOffsets(raw));
        // RET lands at file offset 0x80 + 0x10
        assertBytesAt(so, 0x80 + 0x10, "C0035FD6");
    }

    @Test
    public void patchesArm32MultipleOffsets() throws IOException {
        // vaddr 0x1000 maps to file offset 0x80
        byte[] so = elf(false, 40, 0x1000, 0x80, 0x100);
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("boolean_false", "0x1000, 0x1010");
        raw.put("void_nop", "0x1020");
        NativeLibPatcher.patch(so, "lib/armeabi-v7a/libtest.so", NativeLibPatcher.parseOffsets(raw));
        // MOV R0, #0; BX LR
        assertBytesAt(so, 0x80, "0000A0E31EFF2FE1");
        // the python tool's truncated 6-byte void stub is fixed to BX LR
        assertBytesAt(so, 0x80 + 0x20, "1EFF2FE1");
    }

    @Test
    public void rejectsUnsupportedArchAndOutOfRangeOffsets() {
        byte[] x86 = elf(true, 62, 0x1000, 0x1000, 0x100);
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("void_nop", "0x1010");
        assertThrows(IOException.class, () ->
                NativeLibPatcher.patch(x86, "lib/x86_64/libtest.so", NativeLibPatcher.parseOffsets(raw)));

        byte[] so = elf(true, 183, 0x1000, 0x1000, 0x100);
        Map<String, String> big = new LinkedHashMap<>();
        big.put("void_nop", "0x1FF0");
        assertThrows(IOException.class, () ->
                NativeLibPatcher.patch(so, "lib/arm64-v8a/libtest.so", NativeLibPatcher.parseOffsets(big)));
    }

    @Test
    public void parseOffsetsValidatesFormat() {
        assertThrows(IOException.class, () -> {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("void_nop", "100");
            NativeLibPatcher.parseOffsets(raw);
        });
        assertThrows(IOException.class, () -> {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("void_nop", "0x10;0x20");
            NativeLibPatcher.parseOffsets(raw);
        });
    }

    @Test
    public void nativeLibArchivesListsSoEntries() throws IOException {
        File apk = File.createTempFile("libs", ".apk");
        try {
            byte[] arm64 = elf(true, 183, 0x1000, 0x1000, 64);
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apk))) {
                zos.putNextEntry(new ZipEntry("classes.dex"));
                zos.write("dex\n035".getBytes(StandardCharsets.US_ASCII));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("lib/arm64-v8a/libtest.so"));
                zos.write(arm64);
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("AndroidManifest.xml"));
                zos.write("<manifest/>".getBytes(StandardCharsets.US_ASCII));
                zos.closeEntry();
            }
            Map<String, String> libs = NativeLibPatcher.nativeLibArchives(apk);
            assertEquals(1, libs.size());
            assertEquals("arm64-v8a", libs.get("lib/arm64-v8a/libtest.so"));
            assertTrue(NativeLibPatcher.hasNativeLibs(apk));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
    }
}
