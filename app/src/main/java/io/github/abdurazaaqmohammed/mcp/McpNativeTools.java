package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.utils.nativelib.ElfParser;
import io.github.abdurazaaqmohammed.utils.nativelib.InstructionDecoder;

/**
 * MCP native-library tools (MT Manager mt_apk_native_* parity): ELF
 * inspection, symbol/string/section listings, ARM64/ARM32 disassembly,
 * address mapping, cross-reference scanning, in-place string/byte patching
 * and a lightweight call/branch analysis. Pure Java — no Capstone or other
 * native dependency; unhandled encodings fall back to raw word lines.
 *
 * Libraries come from an open APK session ({@code session} + {@code lib}
 * entry name like {@code lib/arm64-v8a/libfoo.so} or just {@code libfoo.so}),
 * or directly from a file path. Patches write back into the session APK so
 * they flow through {@code mp_apk_build} like every other edit.
 */
public class McpNativeTools {

    private final McpToolContext ctx;

    public McpNativeTools(McpToolContext ctx) {
        this.ctx = ctx;
    }

    private static final class OpenLib {
        final ElfParser elf;
        final File file;
        final String entryName;   // APK entry when opened from a session, else null
        final File sessionApk;    // session working APK for write-back, else null

        OpenLib(ElfParser elf, File file, String entryName, File sessionApk) {
            this.elf = elf;
            this.file = file;
            this.entryName = entryName;
            this.sessionApk = sessionApk;
        }
    }

    /** Resolves {session, lib} or {path} into an extracted temp ELF. */
    private OpenLib openLib(JsonObject args) throws McpJson.McpToolException {
        String sessionArg = McpJson.str(args, "session", null);
        if (sessionArg != null && !sessionArg.isEmpty()) {
            McpApkSessions.Session session = ctx.apkSessions.get(sessionArg);
            String libArg = McpJson.reqStr(args, "lib");
            String entry = findLibEntry(session.workApk, libArg);
            if (entry == null) {
                throw new McpJson.McpToolException("Library '" + libArg + "' not found in the session APK"
                        + " (list candidates with mp_apk_list_entries filter=lib/)");
            }
            File temp = extractEntry(session.workApk, entry);
            return open(temp, entry, session.workApk);
        }
        String pathArg = McpJson.str(args, "path", null);
        if (pathArg == null) {
            throw new McpJson.McpToolException("Provide session+lib or path");
        }
        File lib = ctx.policy.resolve(McpPathPolicy.normalizePath(pathArg, ctx.policy.getHome()));
        if (!lib.isFile()) {
            throw new McpJson.McpToolException("File not found: " + lib);
        }
        return open(lib, lib.getName(), null);
    }

    private OpenLib open(File file, String entryName, File sessionApk) throws McpJson.McpToolException {
        try {
            ElfParser elf = new ElfParser(new RandomAccessFile(file, "rw"));
            return new OpenLib(elf, file, entryName, sessionApk);
        } catch (IOException e) {
            throw new McpJson.McpToolException("Cannot parse ELF: " + e.getMessage());
        }
    }

    static String findLibEntry(File apk, String libArg) {
        try {
            List<String> names = new ArrayList<>();
            try (ZipFile zf = new ZipFile(apk)) {
                for (FileHeader h : zf.getFileHeaders()) {
                    String name = h.getFileName();
                    if (name.startsWith("lib/") && name.endsWith(".so") && !h.isDirectory()) {
                        names.add(name);
                    }
                }
            }
            if (libArg.contains("/")) {
                for (String name : names) {
                    if (name.equals(libArg) || name.endsWith("/" + libArg)) return name;
                }
            }
            for (String name : names) {
                if (name.endsWith("/" + libArg)) return name;
            }
            for (String name : names) {
                if (name.contains(libArg)) return name;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private File extractEntry(File apk, String entry) throws McpJson.McpToolException {
        try {
            File temp = File.createTempFile("mcp_so_", ".so", ctx.context.getCacheDir());
            try (ZipFile zf = new ZipFile(apk);
                 InputStream in = zf.getInputStream(zf.getFileHeader(entry));
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return temp;
        } catch (Exception e) {
            throw new McpJson.McpToolException("Failed to extract " + entry + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // inspect
    // ------------------------------------------------------------------

    public JsonObject inspect(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("class", elf.is64 ? "ELF64" : "ELF32");
            result.addProperty("machine", ElfParser.machineName(elf.machine));
            result.addProperty("machine_id", elf.machine);
            result.addProperty("entry", "0x" + Long.toHexString(elf.entry));
            result.addProperty("disassembler_supported", InstructionDecoder.supports(elf.machine));
            ElfParser.Section[] sections = elf.sections();
            result.addProperty("sections", sections.length);
            ElfParser.ProgramHeader[] segments = elf.segments();
            int load = 0;
            for (ElfParser.ProgramHeader ph : segments) {
                if (ph.type == 1) load++;
            }
            result.addProperty("segments", segments.length);
            result.addProperty("loadable_segments", load);
            Map<String, String> dyn = elf.dynamicInfo();
            if (dyn.containsKey("soname")) result.addProperty("soname", dyn.get("soname"));
            if (dyn.containsKey("needed")) {
                JsonArray needed = new JsonArray();
                for (String need : dyn.get("needed").split(", ")) {
                    needed.add(need);
                }
                result.add("needed_libs", needed);
            }
            List<ElfParser.Symbol> symtab = elf.symbols(".symtab");
            List<ElfParser.Symbol> dynsym = elf.symbols(".dynsym");
            result.addProperty("symtab_symbols", symtab.size());
            result.addProperty("dynsym_symbols", dynsym.size());
            ElfParser.Section text = elf.section(".text");
            if (text != null) {
                result.addProperty("text_addr", "0x" + Long.toHexString(text.addr));
                result.addProperty("text_size", text.size);
            }
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    // ------------------------------------------------------------------
    // list_items
    // ------------------------------------------------------------------

    public JsonObject listItems(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        String kind = McpJson.str(args, "kind", "symbols");
        String filter = McpJson.str(args, "filter", null);
        int max = Math.max(1, Math.min(2000, McpJson.intVal(args, "max_results", 200)));
        try {
            ElfParser elf = lib.elf;
            JsonArray items = new JsonArray();
            int total = 0;
            switch (kind) {
                case "sections": {
                    for (ElfParser.Section s : elf.sections()) {
                        total++;
                        if (items.size() < max && match(s.name, filter)) {
                            JsonObject obj = new JsonObject();
                            obj.addProperty("name", s.name);
                            obj.addProperty("type", s.type);
                            obj.addProperty("addr", "0x" + Long.toHexString(s.addr));
                            obj.addProperty("size", s.size);
                            items.add(obj);
                        }
                    }
                    break;
                }
                case "segments": {
                    for (ElfParser.ProgramHeader ph : elf.segments()) {
                        total++;
                        if (items.size() < max) {
                            JsonObject obj = new JsonObject();
                            obj.addProperty("type", ph.type == 1 ? "LOAD" : "0x" + Integer.toHexString(ph.type));
                            obj.addProperty("vaddr", "0x" + Long.toHexString(ph.vaddr));
                            obj.addProperty("offset", ph.offset);
                            obj.addProperty("filesz", ph.filesz);
                            items.add(obj);
                        }
                    }
                    break;
                }
                case "dynsym":
                case "imports": {
                    List<ElfParser.Symbol> symbols = elf.symbols(".dynsym");
                    for (ElfParser.Symbol sym : symbols) {
                        if ("imports".equals(kind) && sym.shndx != 0) continue;
                        total++;
                        if (items.size() < max && match(sym.name, filter)) {
                            items.add(symbolJson(sym));
                        }
                    }
                    break;
                }
                case "strings": {
                    int minLen = Math.max(4, McpJson.intVal(args, "min_length", 6));
                    List<String[]> strings = elf.strings(".rodata", minLen, 20000);
                    for (String[] str : strings) {
                        if (match(str[1], filter)) {
                            total++;
                            if (items.size() < max) {
                                JsonObject obj = new JsonObject();
                                obj.addProperty("addr", "0x" + str[0]);
                                obj.addProperty("value", str[1]);
                                items.add(obj);
                            }
                        }
                    }
                    break;
                }
                default: { // symbols (.symtab, falls back to .dynsym)
                    List<ElfParser.Symbol> symbols = elf.symbols(".symtab");
                    if (symbols.isEmpty()) symbols = elf.symbols(".dynsym");
                    for (ElfParser.Symbol sym : symbols) {
                        total++;
                        if (items.size() < max && match(sym.name, filter)) {
                            items.add(symbolJson(sym));
                        }
                    }
                    break;
                }
            }
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("kind", kind);
            result.addProperty("total", total);
            result.addProperty("returned", items.size());
            result.add("items", items);
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    private static JsonObject symbolJson(ElfParser.Symbol sym) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", sym.name);
        obj.addProperty("addr", "0x" + Long.toHexString(sym.value));
        obj.addProperty("size", sym.size);
        obj.addProperty("type", sym.typeName());
        obj.addProperty("bind", sym.bindName());
        return obj;
    }

    private static boolean match(String value, String filter) {
        return filter == null || filter.isEmpty() || value.contains(filter);
    }

    // ------------------------------------------------------------------
    // disassemble
    // ------------------------------------------------------------------

    public JsonObject disassemble(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            int machine = elf.machine;
            if (!InstructionDecoder.supports(machine)) {
                throw new McpJson.McpToolException("Disassembly of " + ElfParser.machineName(machine)
                        + " is not supported by the built-in decoder (ELF inspection and symbols still work)");
            }
            List<ElfParser.Symbol> all = elf.symbols(".symtab");
            if (all.isEmpty()) all = elf.symbols(".dynsym");
            Map<Long, String> byAddr = new LinkedHashMap<>();
            for (ElfParser.Symbol sym : all) {
                if (sym.value != 0 && !sym.name.isEmpty()) byAddr.putIfAbsent(sym.value, sym.name);
            }
            boolean thumb = McpJson.bool(args, "thumb", machine == 0x28);
            long startAddr;
            String startName;
            String symbolArg = McpJson.str(args, "symbol", null);
            String addrArg = McpJson.str(args, "address", null);
            if (symbolArg != null && !symbolArg.isEmpty()) {
                Long found = findSymbol(all, symbolArg);
                if (found == null) throw new McpJson.McpToolException("Symbol not found: " + symbolArg);
                startName = symbolArg;
                startAddr = machine == 0x28 && thumb ? (found & ~1L) : found;
            } else if (addrArg != null && !addrArg.isEmpty()) {
                startAddr = parseAddr(addrArg);
                startName = byAddr.containsKey(startAddr) ? byAddr.get(startAddr) : null;
            } else {
                throw new McpJson.McpToolException("Provide symbol or address");
            }
            int count = Math.max(1, Math.min(2000, McpJson.intVal(args, "count", 64)));
            long sizeHint = McpJson.longVal(args, "max_bytes", count * 16L);

            long offset = elf.addrToOffset(startAddr);
            if (offset < 0) throw new McpJson.McpToolException("Address 0x" + Long.toHexString(startAddr) + " is not mapped");
            byte[] code = elf.readBytes(offset, (int) Math.min(sizeHint, 512 * 1024));
            List<InstructionDecoder.Insn> insns = InstructionDecoder.decode(machine, code, startAddr,
                    code.length, count);
            JsonArray list = new JsonArray();
            for (InstructionDecoder.Insn insn : insns) {
                JsonObject obj = new JsonObject();
                obj.addProperty("addr", "0x" + Long.toHexString(insn.address));
                obj.addProperty("size", insn.size);
                String text = insn.text;
                String comment = byAddr.containsKey(insn.address) ? byAddr.get(insn.address) : null;
                if (text.startsWith("bl\t") || text.startsWith("b\t") || text.startsWith("b.w")) {
                    String target = text.substring(text.indexOf('\t') + 1).trim();
                    try {
                        String targetName = byAddr.get(Long.parseLong(target.substring(2), 16));
                        if (targetName != null) text += "  ; " + targetName;
                    } catch (Exception ignored) {
                    }
                }
                if (comment != null) text += "  ; " + comment;
                obj.addProperty("text", text);
                list.add(obj);
            }
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("start", "0x" + Long.toHexString(startAddr));
            if (startName != null) result.addProperty("symbol", startName);
            result.addProperty("arch", ElfParser.machineName(machine) + (machine == 0x28 && thumb ? " (thumb)" : ""));
            result.addProperty("decoded", list.size());
            result.add("instructions", list);
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    static Long findSymbol(List<ElfParser.Symbol> symbols, String name) {
        for (ElfParser.Symbol sym : symbols) {
            if (sym.name.equals(name)) return sym.value;
        }
        for (ElfParser.Symbol sym : symbols) {
            if (sym.name.endsWith(name)) return sym.value;
        }
        return null;
    }

    private static long parseAddr(String value) throws McpJson.McpToolException {
        try {
            String v = value.trim().toLowerCase();
            if (v.startsWith("0x")) v = v.substring(2);
            return Long.parseLong(v, 16);
        } catch (Exception e) {
            throw new McpJson.McpToolException("Invalid address: " + value);
        }
    }

    // ------------------------------------------------------------------
    // map_address
    // ------------------------------------------------------------------

    public JsonObject mapAddress(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            long addr = parseAddr(McpJson.reqStr(args, "address"));
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("address", "0x" + Long.toHexString(addr));
            long offset = elf.addrToOffset(addr);
            result.addProperty("file_offset", offset >= 0 ? offset : null);
            for (ElfParser.Section s : elf.sections()) {
                if (s.size > 0 && addr >= s.addr && addr < s.addr + s.size) {
                    result.addProperty("section", s.name);
                    result.addProperty("section_offset", addr - s.addr);
                    break;
                }
            }
            List<ElfParser.Symbol> all = elf.symbols(".symtab");
            if (all.isEmpty()) all = elf.symbols(".dynsym");
            ElfParser.Symbol prev = null;
            ElfParser.Symbol next = null;
            for (ElfParser.Symbol sym : all) {
                if (sym.value <= addr && (prev == null || sym.value > prev.value)) prev = sym;
                if (sym.value > addr && (next == null || sym.value < next.value)) next = sym;
            }
            if (prev != null) {
                result.addProperty("containing_symbol", prev.name);
                result.addProperty("symbol_addr", "0x" + Long.toHexString(prev.value));
                result.addProperty("symbol_offset", addr - prev.value);
            }
            if (next != null) result.addProperty("next_symbol", next.name + " @ 0x" + Long.toHexString(next.value));
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    // ------------------------------------------------------------------
    // xref
    // ------------------------------------------------------------------

    public JsonObject xref(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            int machine = elf.machine;
            String symbolArg = McpJson.str(args, "symbol", null);
            String addrArg = McpJson.str(args, "address", null);
            long target;
            if (symbolArg != null && !symbolArg.isEmpty()) {
                List<ElfParser.Symbol> symbols = elf.symbols(".symtab");
                if (symbols.isEmpty()) symbols = elf.symbols(".dynsym");
                Long found = findSymbol(symbols, symbolArg);
                if (found == null) throw new McpJson.McpToolException("Symbol not found: " + symbolArg);
                target = found & ~1L;
            } else if (addrArg != null && !addrArg.isEmpty()) {
                target = parseAddr(addrArg);
            } else {
                throw new McpJson.McpToolException("Provide symbol or address");
            }
            int max = Math.max(1, Math.min(500, McpJson.intVal(args, "max_results", 50)));
            ElfParser.Section text = elf.section(".text");
            if (text == null || text.size == 0) {
                throw new McpJson.McpToolException("No .text section");
            }
            byte[] code = elf.readBytes(text.offset, (int) Math.min(text.size, 16 * 1024 * 1024));
            JsonArray hits = new JsonArray();
            int total = 0;
            if (machine == 0xB7) {
                // AArch64: BL to the target, or ADRP+ADD pairs resolving to it.
                for (int i = 0; i + 3 < code.length; i += 4) {
                    long pc = text.addr + i;
                    int op = InstructionDecoder.u32pub(code, i);
                    if ((op >>> 26) == 0b100101) { // BL
                        long dest = pc + (InstructionDecoder.sextpub(op & 0x3FFFFFF, 26) << 2);
                        if (dest == target) {
                            total++;
                            addHit(hits, pc, "bl -> 0x" + Long.toHexString(target), max);
                        }
                    } else if ((op & 0x9F000000) == 0x90000000) { // ADRP
                        long immlo = (op >> 29) & 0x3;
                        long immhi = (op >> 5) & 0x7FFFF;
                        long page = ((pc & ~0xFFFL) + (InstructionDecoder.sextpub(immhi << 2 | immlo, 21) << 12));
                        int rd = op & 0x1F;
                        // scan following 8 instructions for ADD rd, rd, #imm12
                        for (int j = i + 4; j < Math.min(i + 36, code.length - 3); j += 4) {
                            int op2 = InstructionDecoder.u32pub(code, j);
                            if ((op2 & 0xFF800000) == 0x91000000
                                    && (op2 & 0x1F) == rd && ((op2 >> 5) & 0x1F) == rd) {
                                long imm12 = (op2 >> 10) & 0xFFF;
                                if (page + imm12 == target) {
                                    total++;
                                    addHit(hits, pc, "adrp+add -> 0x" + Long.toHexString(target), max);
                                }
                                break;
                            }
                        }
                    }
                }
            } else if (machine == 0x28) {
                // ARM32: BL to the target.
                for (int i = 0; i + 3 < code.length; i += 4) {
                    long pc = text.addr + i;
                    int op = InstructionDecoder.u32pub(code, i);
                    if ((op & 0x0F000000) == 0x0B000000) {
                        long dest = pc + 8 + (InstructionDecoder.sextpub(op & 0xFFFFFF, 24) << 2);
                        if (dest == target) {
                            total++;
                            addHit(hits, pc, "bl -> 0x" + Long.toHexString(target), max);
                        }
                    }
                }
            } else {
                throw new McpJson.McpToolException("xref scan supports AArch64 and ARM libraries only");
            }
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("target", "0x" + Long.toHexString(target));
            result.addProperty("total", total);
            result.addProperty("returned", hits.size());
            result.add("references", hits);
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    private static void addHit(JsonArray hits, long pc, String kind, int max) {
        if (hits.size() >= max) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("addr", "0x" + Long.toHexString(pc));
        obj.addProperty("kind", kind);
        hits.add(obj);
    }

    // ------------------------------------------------------------------
    // control flow (lightweight linear analysis of one function)
    // ------------------------------------------------------------------

    public JsonObject controlFlow(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            int machine = elf.machine;
            if (!InstructionDecoder.supports(machine)) {
                throw new McpJson.McpToolException("Control-flow analysis supports AArch64/ARM libraries only");
            }
            List<ElfParser.Symbol> all = elf.symbols(".symtab");
            if (all.isEmpty()) all = elf.symbols(".dynsym");
            String symbolArg = McpJson.reqStr(args, "symbol");
            Long start = findSymbol(all, symbolArg);
            if (start == null) throw new McpJson.McpToolException("Symbol not found: " + symbolArg);
            boolean thumb = McpJson.bool(args, "thumb", machine == 0x28);
            long base = machine == 0x28 && thumb ? (start & ~1L) : start;
            long size = 256;
            for (ElfParser.Symbol sym : all) {
                if (sym.value == base && sym.size > 0) {
                    size = sym.size;
                    break;
                }
            }
            long nearest = Long.MAX_VALUE;
            for (ElfParser.Symbol sym : all) {
                if (sym.value > base && sym.value - base < nearest) nearest = sym.value - base;
            }
            if (nearest != Long.MAX_VALUE) size = Math.min(size, nearest);
            long offset = elf.addrToOffset(base);
            if (offset < 0) throw new McpJson.McpToolException("Symbol address not mapped");
            byte[] code = elf.readBytes(offset, (int) Math.min(size, 512 * 1024));
            List<InstructionDecoder.Insn> insns = InstructionDecoder.decode(machine, code, base, code.length, 5000);

            Map<Long, String> byAddr = new LinkedHashMap<>();
            for (ElfParser.Symbol sym : all) {
                if (sym.value != 0 && !sym.name.isEmpty()) byAddr.putIfAbsent(sym.value, sym.name);
            }
            JsonArray calls = new JsonArray();
            JsonArray branches = new JsonArray();
            for (InstructionDecoder.Insn insn : insns) {
                String text = insn.text;
                if (text.startsWith("bl\t") || text.startsWith("blx\t")) {
                    String target = text.substring(text.indexOf('\t') + 1).trim();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("addr", "0x" + Long.toHexString(insn.address));
                    obj.addProperty("target", target);
                    try {
                        String name = byAddr.get(Long.parseLong(target.substring(2), 16));
                        if (name != null) obj.addProperty("target_symbol", name);
                    } catch (Exception ignored) {
                    }
                    calls.add(obj);
                } else if (text.startsWith("b\t") || text.startsWith("b.")
                        || text.startsWith("cbz") || text.startsWith("cbnz")
                        || text.startsWith("tbz") || text.startsWith("tbnz") || text.startsWith("b.w")) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("addr", "0x" + Long.toHexString(insn.address));
                    obj.addProperty("insn", text);
                    branches.add(obj);
                }
            }
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("symbol", symbolArg);
            result.addProperty("start", "0x" + Long.toHexString(base));
            result.addProperty("size", size);
            result.addProperty("instructions", insns.size());
            result.addProperty("calls", calls.size());
            result.addProperty("branches", branches.size());
            result.add("call_sites", calls);
            result.add("branch_sites", branches);
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("ELF read failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    // ------------------------------------------------------------------
    // patching
    // ------------------------------------------------------------------

    public JsonObject patchString(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            String oldStr = McpJson.reqStr(args, "old");
            String newStr = McpJson.reqStr(args, "new");
            if (newStr.length() > oldStr.length()) {
                throw new McpJson.McpToolException("new (" + newStr.length() + " chars) must not be longer than old ("
                        + oldStr.length() + " chars); in-place patching cannot grow the file");
            }
            String sectionName = McpJson.str(args, "section", ".rodata");
            ElfParser.Section sec = elf.section(sectionName);
            if (sec == null) throw new McpJson.McpToolException("Section not found: " + sectionName);
            byte[] data = elf.readBytes(sec.offset, (int) Math.min(sec.size, 32 * 1024 * 1024));
            byte[] oldBytes = oldStr.getBytes(StandardCharsets.UTF_8);
            byte[] newBytes = newStr.getBytes(StandardCharsets.UTF_8);
            int hits = 0;
            long firstAddr = -1;
            for (int i = 0; i + oldBytes.length + 1 <= data.length; i++) {
                boolean eq = data[i + oldBytes.length] == 0; // must be NUL-terminated
                if (eq) {
                    for (int j = 0; j < oldBytes.length; j++) {
                        if (data[i + j] != oldBytes[j]) {
                            eq = false;
                            break;
                        }
                    }
                }
                if (eq) {
                    hits++;
                    if (firstAddr < 0) firstAddr = sec.addr + i;
                    for (int j = 0; j < newBytes.length; j++) data[i + j] = newBytes[j];
                    for (int j = newBytes.length; j <= oldBytes.length; j++) data[i + j] = 0;
                }
            }
            if (hits == 0) {
                throw new McpJson.McpToolException("String not found in " + sectionName + ": " + oldStr);
            }
            elf.writeBytes(sec.offset, data);
            writeBack(lib);
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("section", sectionName);
            result.addProperty("replaced", hits);
            result.addProperty("first_addr", "0x" + Long.toHexString(firstAddr));
            result.addProperty("old", oldStr);
            result.addProperty("new", newStr);
            result.addProperty("written_back", lib.entryName);
            result.addProperty("warning", "Patched in the working copy. Call mp_apk_build to produce a signed APK.");
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("Patch failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    public JsonObject patchBytes(JsonObject args) throws McpJson.McpToolException {
        OpenLib lib = openLib(args);
        try {
            ElfParser elf = lib.elf;
            long addr = parseAddr(McpJson.reqStr(args, "address"));
            String hex = McpJson.reqStr(args, "hex").replaceAll("[^0-9a-fA-F]", "");
            if (hex.isEmpty() || hex.length() % 2 != 0) {
                throw new McpJson.McpToolException("hex must be a valid even-length hex string");
            }
            byte[] patch = new byte[hex.length() / 2];
            for (int i = 0; i < patch.length; i++) {
                patch[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            long offset = elf.addrToOffset(addr);
            if (offset < 0) throw new McpJson.McpToolException("Address 0x" + Long.toHexString(addr) + " is not mapped");
            byte[] original = elf.readBytes(offset, patch.length);
            elf.writeBytes(offset, patch);
            writeBack(lib);
            JsonObject result = new JsonObject();
            result.addProperty("lib", lib.entryName != null ? lib.entryName : lib.file.getPath());
            result.addProperty("address", "0x" + Long.toHexString(addr));
            result.addProperty("file_offset", offset);
            result.addProperty("old_hex", hexOf(original));
            result.addProperty("new_hex", hexOf(patch));
            result.addProperty("written_back", lib.entryName);
            result.addProperty("warning", "Patched in the working copy. Call mp_apk_build to produce a signed APK.");
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("Patch failed: " + e.getMessage());
        } finally {
            closeQuietly(lib);
        }
    }

    private static String hexOf(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Re-inserts the patched temp file into the owning session APK. */
    private void writeBack(OpenLib lib) throws McpJson.McpToolException {
        if (lib.entryName == null || lib.sessionApk == null) return;
        try {
            byte[] bytes = new byte[(int) lib.file.length()];
            try (FileInputStream in = new FileInputStream(lib.file)) {
                int pos = 0;
                while (pos < bytes.length) {
                    int read = in.read(bytes, pos, bytes.length - pos);
                    if (read < 0) break;
                    pos += read;
                }
            }
            McpApkTools.replaceApkEntry(lib.sessionApk, lib.entryName, bytes);
        } catch (IOException e) {
            throw new McpJson.McpToolException("Write-back into APK failed: " + e.getMessage());
        }
    }

    private static void closeQuietly(OpenLib lib) {
        try {
            lib.elf.close();
        } catch (IOException ignored) {
        }
        lib.file.delete(); // temp extraction (null entryName => file stays)
    }
}
