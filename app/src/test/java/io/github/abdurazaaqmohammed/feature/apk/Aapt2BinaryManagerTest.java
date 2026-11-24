package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Aapt2BinaryManagerTest {
    private static final long DT_FLAGS_1 = 0x6ffffffbL;
    private static final long DF_1_PIE = 0x08000000L;

    private File elf64(long flags1) throws IOException {
        File file = File.createTempFile("aapt2-elf64-", ".bin");
        file.deleteOnExit();
        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 0x7f).put(1, (byte) 'E').put(2, (byte) 'L').put(3, (byte) 'F')
                .put(4, (byte) 2).put(5, (byte) 1).put(6, (byte) 1);
        buffer.putLong(0x20, 64);
        buffer.putShort(0x36, (short) 56);
        buffer.putShort(0x38, (short) 1);
        buffer.putInt(64, 2); // PT_DYNAMIC
        buffer.putLong(64 + 8, 128);
        buffer.putLong(64 + 32, 32);
        buffer.putLong(128, DT_FLAGS_1);
        buffer.putLong(128 + 8, flags1);
        write(file, buffer);
        return file;
    }

    private File elf32(long flags1) throws IOException {
        File file = File.createTempFile("aapt2-elf32-", ".bin");
        file.deleteOnExit();
        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 0x7f).put(1, (byte) 'E').put(2, (byte) 'L').put(3, (byte) 'F')
                .put(4, (byte) 1).put(5, (byte) 1).put(6, (byte) 1);
        buffer.putInt(0x1c, 52);
        buffer.putShort(0x2a, (short) 32);
        buffer.putShort(0x2c, (short) 1);
        buffer.putInt(52, 2);
        buffer.putInt(52 + 4, 128);
        buffer.putInt(52 + 16, 16);
        buffer.putInt(128, (int) DT_FLAGS_1);
        buffer.putInt(128 + 4, (int) flags1);
        write(file, buffer);
        return file;
    }

    private void write(File file, ByteBuffer buffer) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.write(buffer.array());
        }
    }

    private long read64(File file, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            return Long.reverseBytes(raf.readLong());
        }
    }

    private long read32(File file, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            return Integer.reverseBytes(raf.readInt()) & 0xffffffffL;
        }
    }

    @Test public void clearsPieFlagInElf64() throws Exception {
        File file = elf64(DF_1_PIE | 0x00000002);
        Aapt2BinaryManager.clearPieFlag(file);
        assertEquals(0x00000002, read64(file, 136));
    }

    @Test public void clearsPieFlagInElf32() throws Exception {
        File file = elf32(DF_1_PIE | 0x00000002);
        Aapt2BinaryManager.clearPieFlag(file);
        assertEquals(0x00000002, read32(file, 132));
    }

    @Test public void keepsOtherFlagBitsWhenPieAlreadyCleared() throws Exception {
        File file = elf64(0x00000002);
        Aapt2BinaryManager.clearPieFlag(file);
        assertEquals(0x00000002, read64(file, 136));
    }

    @Test public void rejectsFileWithoutFlagsEntry() throws Exception {
        File file = elf64(DF_1_PIE);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(128);
            raf.writeLong(0);
        }
        IOException error = assertThrows(IOException.class, () -> Aapt2BinaryManager.clearPieFlag(file));
        assertTrue(error.getMessage().contains("DT_FLAGS_1"));
    }

    @Test public void rejectsNonElfFile() throws Exception {
        File file = File.createTempFile("aapt2-not-elf-", ".bin");
        file.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.write(new byte[64]);
        }
        IOException error = assertThrows(IOException.class, () -> Aapt2BinaryManager.clearPieFlag(file));
        assertTrue(error.getMessage().contains("ELF"));
    }
}
