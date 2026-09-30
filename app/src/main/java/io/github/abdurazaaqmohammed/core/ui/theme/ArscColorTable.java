package io.github.abdurazaaqmohammed.core.ui.theme;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Builds a binary resource table ({@code resources.arsc}) holding one type of one
 * package, so runtime colour values can be fed to
 * {@link android.content.res.loader.ResourcesProvider#loadFromTable} and shadow the
 * placeholder colours declared in {@code res/values/mp_palette.xml}.
 *
 * <p>The chunk layout is the one Material Components uses internally in
 * {@code ColorResourcesTableCreator} to build its harmonization overlay; matching its
 * header sizes and field order is what makes the table loadable on API 30+.
 *
 * <p>Pure Java on purpose: no Android imports, so the byte layout is unit-testable
 * on the JVM.
 */
final class ArscColorTable {

    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_TABLE = 0x0002;
    private static final int CHUNK_TABLE_PACKAGE = 0x0200;
    private static final int CHUNK_TABLE_TYPE = 0x0201;
    private static final int CHUNK_TABLE_TYPE_SPEC = 0x0202;

    private static final int TABLE_HEADER_SIZE = 0x000C;
    private static final int STRING_POOL_HEADER_SIZE = 0x001C;
    private static final int PACKAGE_HEADER_SIZE = 0x0120;
    private static final int TYPE_SPEC_HEADER_SIZE = 0x0010;
    private static final int TYPE_HEADER_SIZE = 0x0054;
    private static final int CONFIG_SIZE = 0x40;
    private static final int PACKAGE_NAME_MAX_LENGTH = 128;

    /** ResTable_typeSpec flag marking an entry as publicly referenceable. */
    private static final int SPEC_FLAG_PUBLIC = 0x40000000;
    /** ResTable_entry flag, distinct from the spec flag above. */
    private static final int ENTRY_FLAG_PUBLIC = 0x0002;
    private static final int OFFSET_NO_ENTRY = 0xFFFFFFFF;
    private static final int ENTRY_SIZE = 16;
    private static final int TYPE_COLOR = 0x1C;

    private ArscColorTable() {
    }

    /** One colour to override: the entry id, key name and ARGB value. */
    static final class Entry {
        final int entryId;
        final String key;
        final int argb;

        Entry(int entryId, String key, int argb) {
            this.entryId = entryId;
            this.key = key;
            this.argb = argb;
        }
    }

    private static final Comparator<Entry> BY_ENTRY_ID = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) {
            return a.entryId < b.entryId ? -1 : (a.entryId == b.entryId ? 0 : 1);
        }
    };

    /**
     * @return the table bytes, or an empty array when there is nothing to write.
     */
    static byte[] build(int packageId, String packageName, int typeId, Entry[] entries) {
        if (entries == null || entries.length == 0) return new byte[0];

        Entry[] sorted = entries.clone();
        Arrays.sort(sorted, BY_ENTRY_ID);
        int entryCount = sorted[sorted.length - 1].entryId + 1;

        String[] keys = new String[sorted.length];
        for (int i = 0; i < sorted.length; i++) keys[i] = sorted[i].key;

        StringPool globalStrings = new StringPool();
        StringPool typeStrings = new StringPool(new String[]{"color"});
        StringPool keyStrings = new StringPool(keys);

        int typeSpecSize = TYPE_SPEC_HEADER_SIZE + entryCount * 4;
        int typeSize = TYPE_HEADER_SIZE + entryCount * 4 + sorted.length * ENTRY_SIZE;
        int packageSize = PACKAGE_HEADER_SIZE + typeStrings.size()
                + keyStrings.size() + typeSpecSize + typeSize;
        int totalSize = TABLE_HEADER_SIZE + globalStrings.size() + packageSize;

        // Offset table and spec flags share the same "is this id present" test.
        int[] offsets = new int[entryCount];
        int[] flags = new int[entryCount];
        int running = 0;
        for (int i = 0; i < sorted.length; i++) {
            int id = sorted[i].entryId;
            offsets[id] = running;
            flags[id] = SPEC_FLAG_PUBLIC;
            running += ENTRY_SIZE;
        }
        for (int id = 0; id < entryCount; id++) {
            if (flags[id] == 0) offsets[id] = OFFSET_NO_ENTRY;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
        try {
            chunk(out, CHUNK_TABLE, TABLE_HEADER_SIZE, totalSize);
            i32(out, 1); // packageCount
            globalStrings.writeTo(out);

            chunk(out, CHUNK_TABLE_PACKAGE, PACKAGE_HEADER_SIZE, packageSize);
            i32(out, packageId);
            char[] name = packageName == null ? new char[0] : packageName.toCharArray();
            for (int i = 0; i < PACKAGE_NAME_MAX_LENGTH; i++) {
                u16(out, i < name.length ? name[i] : 0);
            }
            i32(out, PACKAGE_HEADER_SIZE); // typeStrings
            i32(out, 0); // lastPublicType
            i32(out, PACKAGE_HEADER_SIZE + typeStrings.size()); // keyStrings
            i32(out, 0); // lastPublicKey
            i32(out, 0); // typeIdOffset
            typeStrings.writeTo(out);
            keyStrings.writeTo(out);

            chunk(out, CHUNK_TABLE_TYPE_SPEC, TYPE_SPEC_HEADER_SIZE, typeSpecSize);
            typeHeader(out, typeId);
            i32(out, entryCount);
            for (int flag : flags) i32(out, flag);

            chunk(out, CHUNK_TABLE_TYPE, TYPE_HEADER_SIZE, typeSize);
            typeHeader(out, typeId);
            i32(out, entryCount);
            i32(out, TYPE_HEADER_SIZE + entryCount * 4); // entriesStart
            byte[] config = new byte[CONFIG_SIZE];
            config[0] = (byte) CONFIG_SIZE;
            out.write(config);
            for (int offset : offsets) i32(out, offset);
            for (int i = 0; i < sorted.length; i++) {
                u16(out, 8); // ResTable_entry header size
                u16(out, ENTRY_FLAG_PUBLIC);
                i32(out, i); // keyStrings index
                u16(out, 8); // Res_value size
                out.write(0);
                out.write(TYPE_COLOR);
                i32(out, sorted[i].argb);
            }
        } catch (IOException e) {
            return new byte[0];
        }
        return out.toByteArray();
    }

    private static void typeHeader(ByteArrayOutputStream out, int typeId) {
        out.write(typeId);
        out.write(0);
        out.write(0);
        out.write(0);
    }

    private static void chunk(ByteArrayOutputStream out, int type, int headerSize, int size)
            throws IOException {
        u16(out, type);
        u16(out, headerSize);
        i32(out, size);
    }

    /** UTF-16 string pool; the empty pool is legal and used for the table-level pool. */
    private static final class StringPool {
        private final String[] strings;
        private final int[] offsets;
        private final int stringsStart;
        private final int padding;
        private final int chunkSize;

        StringPool(String... values) {
            this.strings = values == null ? new String[0] : values;
            this.offsets = new int[this.strings.length];
            int used = 0;
            for (int i = 0; i < this.strings.length; i++) {
                offsets[i] = used;
                used += this.strings[i].length() * 2 + 4; // u16 length + chars + u16 terminator
            }
            int residue = used % 4;
            padding = residue == 0 ? 0 : 4 - residue;
            stringsStart = STRING_POOL_HEADER_SIZE + this.strings.length * 4;
            chunkSize = stringsStart + used + padding;
        }

        int size() {
            return chunkSize;
        }

        void writeTo(ByteArrayOutputStream out) throws IOException {
            chunk(out, CHUNK_STRING_POOL, STRING_POOL_HEADER_SIZE, chunkSize);
            i32(out, strings.length);
            i32(out, 0); // styleCount
            i32(out, 0); // flags: UTF-16
            i32(out, stringsStart);
            i32(out, 0); // stylesStart
            for (int offset : offsets) i32(out, offset);
            for (String value : strings) {
                u16(out, value.length());
                for (int i = 0; i < value.length(); i++) u16(out, value.charAt(i));
                u16(out, 0);
            }
            if (padding > 0) out.write(new byte[padding]);
        }
    }

    private static void u16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void i32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
