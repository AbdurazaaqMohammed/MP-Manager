package io.github.abdurazaaqmohammed.core.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Byte-layout tests for {@link ArscColorTable}.
 *
 * <p>The table is consumed by native resource code, so a wrong offset does not
 * throw where Java can see it -- it shows up as a silently ignored overlay. These
 * tests assert the exact chunk framing instead, which is what a native parser
 * reads first.
 */
public class ArscColorTableTest {

    private static final int TYPE_ID = 3;
    private static final int PACKAGE_ID = 0x7F;
    private static final String PKG_NAME = "io.github.abdurazaaqmohammed.MPManager";

    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_TABLE = 0x0002;
    private static final int CHUNK_PACKAGE = 0x0200;
    private static final int CHUNK_TYPE = 0x0201;
    private static final int CHUNK_TYPE_SPEC = 0x0202;

    private static final int TABLE_HEADER_SIZE = 0x000C;
    private static final int PACKAGE_HEADER_SIZE = 0x0120;
    private static final int TYPE_SPEC_HEADER_SIZE = 0x0010;
    private static final int TYPE_HEADER_SIZE = 0x0054;
    private static final int STRING_POOL_HEADER_SIZE = 0x001C;
    private static final int ENTRY_SIZE = 16;
    private static final int OFFSET_NO_ENTRY = 0xFFFFFFFF;

    private static ArscColorTable.Entry entry(int id, int argb) {
        return new ArscColorTable.Entry(id, "mp_test_" + id, argb);
    }

    private static byte[] table(ArscColorTable.Entry... entries) {
        return ArscColorTable.build(PACKAGE_ID, PKG_NAME, TYPE_ID, entries);
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int i32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    /** Offset of the first child chunk of the given type, or -1. */
    private static int chunkAt(byte[] t, int type) {
        // Children of RES_TABLE start after its fixed header.
        int off = TABLE_HEADER_SIZE;
        while (off + 8 <= t.length) {
            if (u16(t, off) == type) return off;
            int size = i32(t, off + 4);
            if (size <= 0) return -1;
            off += size;
        }
        return -1;
    }

    // typeSpec and type are children of the package chunk, so they are not
    // reachable by walking top-level chunks: derive them from the pool offsets.
    private static int pkgAt(byte[] t) {
        return chunkAt(t, CHUNK_PACKAGE);
    }

    private static int typeSpecAt(byte[] t) {
        int pkg = pkgAt(t);
        int typeStrings = i32(t, pkg + 268);
        int keyStrings = i32(t, pkg + 276);
        return pkg + PACKAGE_HEADER_SIZE
                + i32(t, pkg + typeStrings + 4) + i32(t, pkg + keyStrings + 4);
    }

    private static int typeAt(byte[] t) {
        int spec = typeSpecAt(t);
        return spec + i32(t, spec + 4);
    }

    @Test
    public void emptyInputProducesNoTable() {
        assertEquals(0, ArscColorTable.build(PACKAGE_ID, "pkg", TYPE_ID, null).length);
        assertEquals(0, ArscColorTable.build(PACKAGE_ID, "pkg", TYPE_ID,
                new ArscColorTable.Entry[0]).length);
    }

    @Test
    public void tableHeaderFramesTheWholeFile() {
        byte[] t = table(entry(0, 0xFF303030));
        assertEquals(CHUNK_TABLE, u16(t, 0));
        assertEquals(TABLE_HEADER_SIZE, u16(t, 2));
        assertEquals(t.length, i32(t, 4));
        assertEquals(1, i32(t, 8)); // packageCount
    }

    /**
     * A native parser walks chunks by (type, size); a size that disagrees with the
     * bytes actually written is the failure mode that silently drops the table.
     */
    @Test
    public void everyChunkSizeAccountsForItsChildren() {
        byte[] t = table(entry(0, 0xFF303030), entry(1, 0xFF151515), entry(2, 0xFFC5C5C5));

        int tableSize = i32(t, 4);
        int off = TABLE_HEADER_SIZE;

        assertEquals(CHUNK_STRING_POOL, u16(t, off));
        int globalPoolSize = i32(t, off + 4);
        assertEquals("empty global pool is header only", STRING_POOL_HEADER_SIZE, globalPoolSize);
        off += globalPoolSize;

        assertEquals(CHUNK_PACKAGE, u16(t, off));
        assertEquals(PACKAGE_HEADER_SIZE, u16(t, off + 2));
        int packageSize = i32(t, off + 4);

        // typeSpec and type are nested in the package chunk, after both string pools.
        int specOff = typeSpecAt(t);
        assertEquals(CHUNK_TYPE_SPEC, u16(t, specOff));
        assertEquals(TYPE_SPEC_HEADER_SIZE, u16(t, specOff + 2));
        int typeOff = typeAt(t);
        assertEquals(CHUNK_TYPE, u16(t, typeOff));
        assertEquals(TYPE_HEADER_SIZE, u16(t, typeOff + 2));
        assertEquals("package size must cover its pools and chunks",
                typeOff + i32(t, typeOff + 4) - off, packageSize);

        off += packageSize;
        assertEquals("chunks must tile the table exactly", tableSize, off);
        assertEquals(t.length, off);
    }

    /**
     * typeStrings/keyStrings are offsets from the start of the package chunk, so a
     * wrong value makes the parser read string data as a header.
     */
    @Test
    public void packageStringPoolOffsetsPointAtTheirPools() {
        byte[] t = table(entry(0, 0xFF303030));
        int pkg = chunkAt(t, CHUNK_PACKAGE);
        assertTrue(pkg > 0);

        // name[] occupies bytes 12..267; the five offsets follow it.
        int typeStringsOffset = i32(t, pkg + 268);
        int lastPublicType = i32(t, pkg + 272);
        int keyStringsOffset = i32(t, pkg + 276);
        int lastPublicKey = i32(t, pkg + 280);
        int typeIdOffset = i32(t, pkg + 284);

        assertEquals(PACKAGE_HEADER_SIZE, typeStringsOffset);
        assertEquals(0, lastPublicType);
        assertEquals(0, lastPublicKey);
        assertEquals(0, typeIdOffset);
        assertTrue("keyStrings must follow typeStrings", keyStringsOffset > typeStringsOffset);

        assertEquals(CHUNK_STRING_POOL, u16(t, pkg + typeStringsOffset));
        assertEquals(CHUNK_STRING_POOL, u16(t, pkg + keyStringsOffset));
        assertEquals("keyStrings offset must match the real position",
                keyStringsOffset, typeStringsOffset + i32(t, pkg + typeStringsOffset + 4));

        assertEquals("package id", PACKAGE_ID, i32(t, pkg + 8));
    }

    @Test
    public void packageNameIsUtf16AndZeroPaddedTo128Chars() {
        byte[] t = table(entry(0, 0xFF303030));
        int pkg = chunkAt(t, CHUNK_PACKAGE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PKG_NAME.length(); i++) {
            sb.append((char) u16(t, pkg + 12 + i * 2));
        }
        assertEquals(PKG_NAME, sb.toString());
        for (int i = PKG_NAME.length(); i < 128; i++) {
            assertEquals("name pad char " + i, 0, u16(t, pkg + 12 + i * 2));
        }
    }

    @Test
    public void typeIdIsWrittenIntoSpecAndTypeChunks() {
        byte[] t = table(entry(0, 0xFF303030));
        assertEquals(TYPE_ID, t[typeSpecAt(t) + 8] & 0xFF);
        assertEquals(TYPE_ID, t[typeAt(t) + 8] & 0xFF);
    }

    /**
     * Entry ids must keep the app's own numbering: an overlay entry at the wrong
     * index would recolour a different resource.
     */
    @Test
    public void entryValuesSitAtTheirEntryIdOffsets() {
        byte[] t = table(entry(0, 0xFF303030), entry(2, 0xFF224452));
        int typeOff = typeAt(t);
        int entryCount = i32(t, typeOff + 12);
        assertEquals(3, entryCount); // ids 0..2, id 1 is absent

        int entriesStart = i32(t, typeOff + 16);
        assertEquals(TYPE_HEADER_SIZE + entryCount * 4, entriesStart);

        int offsetTable = typeOff + TYPE_HEADER_SIZE;
        assertEquals(0, i32(t, offsetTable + 0 * 4));
        assertEquals(OFFSET_NO_ENTRY, i32(t, offsetTable + 1 * 4));
        assertEquals(ENTRY_SIZE, i32(t, offsetTable + 2 * 4));

        int first = typeOff + entriesStart;
        assertEquals(0x1C, t[first + 11] & 0xFF); // TYPE_INT_COLOR_ARGB8
        assertEquals(0xFF303030, i32(t, first + 12));

        int second = first + ENTRY_SIZE;
        assertEquals(0x1C, t[second + 11] & 0xFF);
        assertEquals(0xFF224452, i32(t, second + 12));
    }

    /**
     * Entries are sorted by id, so a gap in the input still lines key names up with
     * the entry that references them.
     */
    @Test
    public void unsortedInputIsNormalisedByEntryId() {
        byte[] t = table(entry(3, 0xFF111111), entry(1, 0xFF222222), entry(2, 0xFF333333));
        int typeOff = typeAt(t);
        int entryCount = i32(t, typeOff + 12);
        assertEquals(4, entryCount);

        int offsetTable = typeOff + TYPE_HEADER_SIZE;
        assertEquals(OFFSET_NO_ENTRY, i32(t, offsetTable + 0 * 4));
        assertEquals(0, i32(t, offsetTable + 1 * 4));

        int first = typeOff + i32(t, typeOff + 16);
        assertEquals("lowest id first", 0xFF222222, i32(t, first + 12));
        assertEquals(0xFF333333, i32(t, first + ENTRY_SIZE + 12));
        assertEquals(0xFF111111, i32(t, first + 2 * ENTRY_SIZE + 12));
    }

    @Test
    public void everyEntryIsMarkedPublic() {
        byte[] t = table(entry(0, 0xFF303030), entry(1, 0xFF151515));
        int specOff = typeSpecAt(t);
        int entryCount = i32(t, specOff + 12);
        for (int i = 0; i < entryCount; i++) {
            assertEquals("spec flag " + i, 0x40000000, i32(t, specOff + 16 + i * 4));
        }

        int typeOff = typeAt(t);
        int first = typeOff + i32(t, typeOff + 16);
        for (int i = 0; i < 2; i++) {
            int e = first + i * ENTRY_SIZE;
            assertEquals("entry header size", 8, u16(t, e));
            assertEquals("entry flag", 0x0002, u16(t, e + 2));
            assertEquals("value size", 8, u16(t, e + 8));
            assertEquals(0, t[e + 6] & 0xFF);
        }
    }

    /** A zeroed 64-byte config with 0x40 in byte 0 is the "no qualifier" config. */
    @Test
    public void defaultConfigIsSixtyFourZeroBytesWithSizeInFirstByte() {
        byte[] t = table(entry(0, 0xFF303030));
        int typeOff = typeAt(t);
        int configAt = typeOff + 20;
        assertEquals(0x40, t[configAt] & 0xFF);
        for (int i = 1; i < 64; i++) {
            assertEquals("config byte " + i, 0, t[configAt + i] & 0xFF);
        }
    }

    @Test
    public void stringPoolIsFourByteAlignedAndWellFormed() {
        byte[] t = table(entry(0, 0xFF303030), entry(1, 0xFF151515));
        int pkg = chunkAt(t, CHUNK_PACKAGE);
        int typeStringsOffset = i32(t, pkg + 268);
        int keyStringsOffset = i32(t, pkg + 276);

        int typePool = pkg + typeStringsOffset;
        assertEquals(1, i32(t, typePool + 8));           // stringCount
        assertEquals(0, i32(t, typePool + 12));          // styleCount
        assertEquals(0, i32(t, typePool + 16));          // flags: UTF-16, no sort
        assertEquals(0, i32(t, typePool + 24));          // stylesStart

        int keyPool = pkg + keyStringsOffset;
        assertEquals(2, i32(t, keyPool + 8));            // one key per entry

        for (int pool : new int[]{typePool, keyPool}) {
            int chunkSize = i32(t, pool + 4);
            assertEquals("chunk size is 4-byte aligned", 0, chunkSize % 4);
            assertEquals("header size", STRING_POOL_HEADER_SIZE, u16(t, pool + 2));
            int stringsStart = i32(t, pool + 20);
            assertEquals(0, stringsStart % 4);
            assertEquals("string data follows the offsets", 0, (chunkSize - stringsStart) % 4);
        }

        // Offset 0 is the first string, so it sits at stringsStart + 4.
        assertEquals("color", poolString(t, typePool, 0));
        assertEquals("mp_test_0", poolString(t, keyPool, 0));
        assertEquals("mp_test_1", poolString(t, keyPool, 1));
    }

    /** Decodes the nth UTF-16 string of the pool chunk at {@code pool}. */
    private static String poolString(byte[] t, int pool, int index) {
        int stringsStart = i32(t, pool + 20);
        int at = pool + stringsStart + i32(t, pool + STRING_POOL_HEADER_SIZE + index * 4);
        int length = u16(t, at);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) u16(t, at + 2 + i * 2));
        }
        assertEquals("strings are NUL terminated", 0, u16(t, at + 2 + length * 2));
        return sb.toString();
    }

    @Test
    public void outputIsStableAcrossCalls() {
        byte[] a = table(entry(0, 0xFF303030), entry(1, 0xFF151515));
        byte[] b = table(entry(0, 0xFF303030), entry(1, 0xFF151515));
        assertTrue(Arrays.equals(a, b));
    }
}
