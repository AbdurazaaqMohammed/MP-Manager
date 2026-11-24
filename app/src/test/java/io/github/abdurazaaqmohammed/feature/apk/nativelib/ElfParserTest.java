package io.github.abdurazaaqmohammed.feature.apk.nativelib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ElfParserTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File writeBytes(String name, byte[] data) throws IOException {
        File file = tempFolder.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        return file;
    }

    private byte[] buildMinimalElf64(int machine, long entry) {
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x7F).put((byte) 'E').put((byte) 'L').put((byte) 'F');
        buf.put((byte) 2); // ELFCLASS64
        buf.put((byte) 1); // ELFDATA2LSB (little-endian)
        buf.put((byte) 1); // EV_CURRENT
        buf.position(16);
        buf.putShort((short) 3); // ET_DYN
        buf.putShort((short) machine);
        buf.putInt(1); // e_version
        buf.putLong(entry); // e_entry
        buf.putLong(0L); // e_phoff
        buf.putLong(0L); // e_shoff
        buf.putInt(0); // e_flags
        buf.putShort((short) 64); // e_ehsize
        buf.putShort((short) 56); // e_phentsize
        buf.putShort((short) 0); // e_phnum
        buf.putShort((short) 64); // e_shentsize
        buf.putShort((short) 0); // e_shnum
        buf.putShort((short) 0); // e_shstrndx
        return buf.array();
    }

    private byte[] buildMinimalElf32(int machine, int entry) {
        ByteBuffer buf = ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x7F).put((byte) 'E').put((byte) 'L').put((byte) 'F');
        buf.put((byte) 1); // ELFCLASS32
        buf.put((byte) 1); // ELFDATA2LSB (little-endian)
        buf.put((byte) 1); // EV_CURRENT
        buf.position(16);
        buf.putShort((short) 3); // ET_DYN
        buf.putShort((short) machine);
        buf.putInt(1); // e_version
        buf.putInt(entry); // e_entry
        buf.putInt(0); // e_phoff
        buf.putInt(0); // e_shoff
        buf.putInt(0); // e_flags
        buf.putShort((short) 52); // e_ehsize
        buf.putShort((short) 32); // e_phentsize
        buf.putShort((short) 0); // e_phnum
        buf.putShort((short) 40); // e_shentsize
        buf.putShort((short) 0); // e_shnum
        buf.putShort((short) 0); // e_shstrndx
        return buf.array();
    }

    @Test
    public void parsesValidArm64Elf64Header() throws Exception {
        File file = writeBytes("libarm64.so", buildMinimalElf64(0xB7, 0x1234L));
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser parser = new ElfParser(raf)) {
            assertTrue(parser.is64);
            assertEquals(0xB7, parser.machine);
            assertEquals(0x1234L, parser.entry);
            assertEquals("AArch64", ElfParser.machineName(parser.machine));
        }
    }

    @Test
    public void parsesValidArm32Elf32Header() throws Exception {
        File file = writeBytes("libarm32.so", buildMinimalElf32(0x28, 0x400));
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser parser = new ElfParser(raf)) {
            assertFalse(parser.is64);
            assertEquals(0x28, parser.machine);
            assertEquals(0x400L, parser.entry);
            assertEquals("ARM", ElfParser.machineName(parser.machine));
        }
    }

    @Test(expected = IOException.class)
    public void rejectsNonElfMagicHeader() throws Exception {
        File file = writeBytes("not_elf.bin", new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser ignored = new ElfParser(raf)) {
            // Expected IOException
        }
    }

    @Test(expected = IOException.class)
    public void rejectsBigEndianElf() throws Exception {
        byte[] data = buildMinimalElf64(0xB7, 0x1000L);
        data[5] = 2; // ELFDATA2MSB
        File file = writeBytes("big_endian.so", data);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser ignored = new ElfParser(raf)) {
            // Expected IOException
        }
    }
}
