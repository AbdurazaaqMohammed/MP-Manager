package io.github.abdurazaaqmohammed.utils.nativelib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal pure-Java ELF parser (32/64-bit, little-endian): header, section
 * headers, symbol tables (.symtab/.dynsym), dynamic entries (SONAME, NEEDED),
 * program headers and virtual-address <-> file-offset mapping. Enough to
 * power the MCP native-library tools (inspect, symbols, imports, xref,
 * disassembly) without any native dependency.
 */
public class ElfParser implements AutoCloseable {

    private final RandomAccessFile raf;
    public final boolean is64;
    private final long e_shoff;
    private final int e_shentsize, e_shnum, e_shstrndx;
    private final int e_phentsize, e_phnum;
    public final int machine; // 0xB7=AArch64, 0x28=ARM, 0x3E=x86_64, 0x03=x86
    public final long entry;

    private Section[] sections;
    private ProgramHeader[] segments;
    private byte[] shstrtab;

    public static final String[] MACHINES = {"?", "AIM", "?", "?", "?", "?", "?", "?", "MIPS", "S370", "MIPS_RS3_LE", "?", "?", "?", "?", "PARISC",
            "?", "VPP500", "SPARC", "i386", "M68K", "M88K", "IAMCU", "i860", "?", "?", "S390", "SPARC32PLUS", "?", "PPC", "PPC64", "S390X",
            "SPU", "V850", "MIPS-X", "?", "?", "?", "?", "?", "NIOS32", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
            "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
            "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
            "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
            "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
            "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "x86_64"};

    public static String machineName(int machine) {
        switch (machine) {
            case 0xB7: return "AArch64";
            case 0x28: return "ARM";
            case 0x3E: return "x86_64";
            case 0x03: return "x86";
            case 0x14: return "PowerPC";
            case 0x08: return "MIPS";
            case 0x32: return "IA-64";
            default: return machine < MACHINES.length ? MACHINES[machine] : "0x" + Integer.toHexString(machine);
        }
    }

    public static final class Section {
        public int index;
        public String name;
        public int type;
        public long addr, offset, size;
        public long link, entsize;
    }

    public static final class ProgramHeader {
        public int type;
        public long offset, vaddr, filesz, memsz;
    }

    public static final class Symbol {
        public String name;
        public long value, size;
        public int type; // st_info & 0xf
        public int bind; // st_info >> 4
        public int shndx;

        public String typeName() {
            switch (type) {
                case 0: return "NOTYPE";
                case 1: return "OBJECT";
                case 2: return "FUNC";
                case 3: return "SECTION";
                case 4: return "FILE";
                case 10: return "IFUNC";
                default: return String.valueOf(type);
            }
        }

        public String bindName() {
            switch (bind) {
                case 0: return "LOCAL";
                case 1: return "GLOBAL";
                case 2: return "WEAK";
                default: return String.valueOf(bind);
            }
        }
    }

    public static final class DynamicEntry {
        public long tag;
        public long value;
        public String name; // for SONAME/NEEDED, resolved via strtab
    }

    public ElfParser(RandomAccessFile raf) throws IOException {
        this.raf = raf;
        byte[] ident = new byte[16];
        raf.readFully(ident);
        if (ident[0] != 0x7F || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') {
            throw new IOException("Not an ELF file");
        }
        if (ident[5] != 1) {
            throw new IOException("Only little-endian ELF supported");
        }
        this.is64 = ident[4] == 2;
        raf.seek(16);
        if (is64) {
            raf.skipBytes(2); // type
            this.machine = readU16();
            raf.skipBytes(4); // version
            this.entry = readU64();
            raf.skipBytes(8); // phoff
            this.e_shoff = readU64();
            raf.skipBytes(4); // flags
            raf.skipBytes(2); // ehsize
            this.e_phentsize = readU16();
            this.e_phnum = readU16();
            this.e_shentsize = readU16();
            this.e_shnum = readU16();
            this.e_shstrndx = readU16();
        } else {
            raf.skipBytes(2);
            this.machine = readU16();
            raf.skipBytes(4);
            this.entry = readU32() & 0xFFFFFFFFL;
            raf.skipBytes(4); // phoff
            this.e_shoff = readU32() & 0xFFFFFFFFL;
            raf.skipBytes(4); // flags
            raf.skipBytes(2);
            this.e_phentsize = readU16();
            this.e_phnum = readU16();
            this.e_shentsize = readU16();
            this.e_shnum = readU16();
            this.e_shstrndx = readU16();
        }
    }

    private int readU16() throws IOException {
        int b0 = raf.read(), b1 = raf.read();
        return (b1 << 8) | b0;
    }

    private long readU32() throws IOException {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            v |= ((long) raf.read()) << (8 * i);
        }
        return v;
    }

    private long readU64() throws IOException {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= ((long) raf.read()) << (8 * i);
        }
        return v;
    }

    private long readWord() throws IOException {
        return is64 ? readU64() : (readU32() & 0xFFFFFFFFL);
    }

    private long readAddr() throws IOException {
        return readWord();
    }

    public synchronized Section[] sections() throws IOException {
        if (sections != null) return sections;
        if (e_shoff == 0 || e_shnum == 0) {
            sections = new Section[0];
            return sections;
        }
        sections = new Section[e_shnum];
        for (int i = 0; i < e_shnum; i++) {
            raf.seek(e_shoff + (long) i * e_shentsize);
            Section s = new Section();
            s.index = i;
            s.name = null; // filled below
            long nameOff = readU32() & 0xFFFFFFFFL;
            s.type = (int) readU32();
            raf.skipBytes(is64 ? 8 : 4); // sh_flags
            s.addr = readAddr();
            s.offset = readWord();
            s.size = readWord();
            s.link = readU32() & 0xFFFFFFFFL;
            // info(4) + addralign(8 for ELF64 / 4 for ELF32) follow link.
            raf.skipBytes(is64 ? 12 : 8);
            s.entsize = readWord();
            sections[i] = s;
            sections[i].name = String.valueOf(nameOff); // temp
            sections[i].index = i;
            // stash name offset in a parallel trick: re-read below
        }
        // resolve names via shstrtab
        if (e_shstrndx < sections.length) {
            Section strsec = sections[e_shstrndx];
            shstrtab = new byte[(int) Math.min(strsec.size, 4 * 1024 * 1024)];
            raf.seek(strsec.offset);
            raf.readFully(shstrtab);
            for (Section s : sections) {
                try {
                    long off = Long.parseLong(s.name);
                    s.name = cstring(shstrtab, (int) off);
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            for (Section s : sections) s.name = "?";
        }
        return sections;
    }

    private static String cstring(byte[] data, int off) {
        if (off < 0 || off >= data.length) return "?";
        int end = off;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }

    public synchronized ProgramHeader[] segments() throws IOException {
        if (segments != null) return segments;
        segments = new ProgramHeader[e_phnum];
        for (int i = 0; i < e_phnum; i++) {
            raf.seek(e_phoff() + (long) i * e_phentsize);
            ProgramHeader ph = new ProgramHeader();
            ph.type = (int) readU32();
            if (is64) {
                raf.skipBytes(4); // flags
                ph.offset = readU32();
                ph.vaddr = readU64();
                raf.skipBytes(8); // paddr
                ph.filesz = readU64();
                ph.memsz = readU64();
            } else {
                ph.offset = readU32();
                ph.vaddr = readU32() & 0xFFFFFFFFL;
                raf.skipBytes(4); // paddr
                ph.filesz = readU32() & 0xFFFFFFFFL;
                ph.memsz = readU32() & 0xFFFFFFFFL;
            }
            segments[i] = ph;
        }
        return segments;
    }

    private long e_phoff;

    private long e_phoff() throws IOException {
        if (e_phoff == 0) {
            raf.seek(is64 ? 0x20 : 0x1C);
            e_phoff = is64 ? readU64() : (readU32() & 0xFFFFFFFFL);
        }
        return e_phoff;
    }

    /** Symbols from the named table (.symtab preferred) or .dynsym. */
    public synchronized List<Symbol> symbols(String sectionName) throws IOException {
        Section table = section(sectionName);
        if (table == null) return new ArrayList<>();
        Section strtab = sections.length > table.link ? sections[(int) table.link] : null;
        byte[] str = new byte[0];
        if (strtab != null) {
            str = new byte[(int) Math.min(strtab.size, 8 * 1024 * 1024)];
            raf.seek(strtab.offset);
            raf.readFully(str);
        }
        int count = (int) (table.size / Math.max(1, table.entsize));
        List<Symbol> out = new ArrayList<>(Math.min(count, 100000));
        for (int i = 0; i < count; i++) {
            raf.seek(table.offset + (long) i * table.entsize);
            Symbol sym = new Symbol();
            if (is64) {
                long nameOff = readU32() & 0xFFFFFFFFL;
                int info = raf.read();
                raf.skipBytes(1); // other
                sym.shndx = readU16();
                sym.value = readU64();
                sym.size = readU64();
                sym.type = info & 0xF;
                sym.bind = (info >> 4) & 0xF;
                sym.name = cstring(str, (int) nameOff);
            } else {
                long nameOff = readU32() & 0xFFFFFFFFL;
                sym.value = readU32() & 0xFFFFFFFFL;
                sym.size = readU32() & 0xFFFFFFFFL;
                int info = raf.read();
                raf.skipBytes(1); // other
                sym.shndx = readU16();
                sym.type = info & 0xF;
                sym.bind = (info >> 4) & 0xF;
                sym.name = cstring(str, (int) nameOff);
            }
            out.add(sym);
        }
        return out;
    }

    public synchronized Section section(String name) throws IOException {
        for (Section s : sections()) {
            if (name.equals(s.name)) return s;
        }
        return null;
    }

    public synchronized Map<String, String> dynamicInfo() throws IOException {
        Section dynamic = section(".dynamic");
        Section dynstr = section(".dynstr");
        Map<String, String> out = new LinkedHashMap<>();
        if (dynamic == null) return out;
        byte[] str = new byte[0];
        if (dynstr != null) {
            str = new byte[(int) Math.min(dynstr.size, 4 * 1024 * 1024)];
            raf.seek(dynstr.offset);
            raf.readFully(str);
        }
        int count = (int) (dynamic.size / (is64 ? 16 : 8));
        for (int i = 0; i < count; i++) {
            raf.seek(dynamic.offset + (long) i * (is64 ? 16 : 8));
            long tag = readWord();
            long value = readWord();
            if (tag == 0) break;
            switch ((int) tag) {
                case 14: // SONAME
                    out.put("soname", cstring(str, (int) value));
                    break;
                case 1: // NEEDED
                    String need = cstring(str, (int) value);
                    out.put("needed", out.containsKey("needed") ? out.get("needed") + ", " + need : need);
                    break;
                case 5: // STRTAB
                    out.put("strtab_addr", Long.toHexString(value));
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    /** Virtual address -> file offset, or -1 when not mapped. */
    public synchronized long addrToOffset(long vaddr) throws IOException {
        for (Section s : sections()) {
            if (s.type != 8 /* NOBITS */ && s.size > 0
                    && vaddr >= s.addr && vaddr < s.addr + s.size) {
                return s.offset + (vaddr - s.addr);
            }
        }
        for (ProgramHeader ph : segments()) {
            if (ph.type == 1 /* LOAD */ && ph.filesz > 0
                    && vaddr >= ph.vaddr && vaddr < ph.vaddr + ph.filesz) {
                return ph.offset + (vaddr - ph.vaddr);
            }
        }
        return -1;
    }

    public synchronized byte[] readBytes(long offset, int length) throws IOException {
        byte[] out = new byte[length];
        raf.seek(offset);
        int n = raf.read(out);
        if (n < 0) n = 0;
        return n == length ? out : Arrays.copyOf(out, n);
    }

    /** Reads a NUL-terminated C string at the given file offset (max 4 KiB). */
    public synchronized String readCString(long offset) throws IOException {
        byte[] buf = readBytes(offset, 4096);
        return cstring(buf, 0);
    }

    /** Extracts printable NUL-terminated strings (>= minLen) from the named section. */
    public synchronized List<String[]> strings(String sectionName, int minLen, int maxResults) throws IOException {
        Section sec = section(sectionName);
        List<String[]> out = new ArrayList<>();
        if (sec == null || sec.size == 0) return out;
        byte[] data = new byte[(int) Math.min(sec.size, 32 * 1024 * 1024)];
        raf.seek(sec.offset);
        int read = raf.read(data);
        if (read <= 0) return out;
        int start = -1;
        for (int i = 0; i < read && out.size() < maxResults; i++) {
            byte b = data[i];
            boolean printable = b >= 0x20 && b < 0x7F;
            if (printable) {
                if (start < 0) start = i;
            } else {
                if (start >= 0 && i - start >= minLen) {
                    out.add(new String[]{Long.toHexString(sec.addr + start),
                            new String(data, start, i - start, StandardCharsets.UTF_8)});
                }
                start = -1;
            }
        }
        return out;
    }

    /** In-place write at a file offset (used by the patching tools). */
    public synchronized void writeBytes(long offset, byte[] data) throws IOException {
        raf.seek(offset);
        raf.write(data);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
