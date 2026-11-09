package io.github.abdurazaaqmohammed.utils;

import io.github.abdurazaaqmohammed.features.system.SystemImageEngine;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SparseImageRegressionTest {
    private byte[] fixture(boolean extended) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int fileHeader = extended ? 32 : 28, chunkHeader = extended ? 16 : 12;
        ByteBuffer header = ByteBuffer.allocate(fileHeader).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0xed26ff3a).putShort((short) 1).putShort((short) 0)
                .putShort((short) fileHeader).putShort((short) chunkHeader)
                .putInt(4).putInt(3).putInt(4).putInt(0);
        output.write(header.array());
        chunk(output, chunkHeader, 0xcac1, 1, new byte[]{1, 2, 3, 4});
        chunk(output, chunkHeader, 0xcac2, 1, new byte[]{5, 6, 7, 8});
        chunk(output, chunkHeader, 0xcac3, 1, new byte[0]);
        chunk(output, chunkHeader, 0xcac4, 0, new byte[4]);
        return output.toByteArray();
    }

    private void chunk(ByteArrayOutputStream output, int headerSize, int type, int blocks, byte[] payload) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) type).putShort((short) 0).putInt(blocks).putInt(headerSize + payload.length);
        output.write(header.array()); output.write(payload);
    }

    private File file(byte[] bytes) throws Exception {
        File file = File.createTempFile("sparse-regression-", ".img");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
        return file;
    }

    @Test public void correctAndroidHeaderOffsetsAndEveryChunkType() throws Exception {
        for (boolean extended : new boolean[]{false, true}) {
            File file = file(fixture(extended));
            try {
                assertEquals(12, SystemImageEngine.readSparseInfo(file).uncompressedSizeBytes);
                SystemImageEngine.validateSparseImage(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                SystemImageEngine.unsparseToStream(file, output, null);
                assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0}, output.toByteArray());
            } finally { file.delete(); }
        }
    }

    @Test public void truncatedImageFailsBeforeWritingAnything() throws Exception {
        byte[] image = fixture(false);
        File file = file(Arrays.copyOf(image, image.length - 1));
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            assertThrows(java.io.IOException.class, () -> SystemImageEngine.unsparseToStream(file, output, null));
            assertEquals(0, output.size());
        } finally { file.delete(); }
    }

    @Test public void mismatchedBlockCountAndUnknownChunkFailPreflight() throws Exception {
        for (int mode = 0; mode < 2; mode++) {
            byte[] bytes = fixture(false);
            if (mode == 0) bytes[16] = 4;
            else bytes[28] = 0;
            File file = file(bytes);
            try { assertThrows(java.io.IOException.class, () -> SystemImageEngine.validateSparseImage(file)); }
            finally { file.delete(); }
        }
    }
}
