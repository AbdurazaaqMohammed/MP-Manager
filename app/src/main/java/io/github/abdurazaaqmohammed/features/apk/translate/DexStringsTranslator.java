package io.github.abdurazaaqmohammed.features.apk.translate;

import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali.Smali;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.FastDexPatch;

/**
 * The dex half of translation mode: reads string literals out of one or more dex files and writes
 * edited ones back.
 *
 * <p>Deliberately shaped like {@link ApkStringsTranslator} so the screen above it cannot tell the
 * difference. Where the arsc side resolves a config qualifier and edits a resource table, this
 * resolves a list of dex files and edits literals; the engines, the format guard, the filtering and
 * the staging are shared.
 *
 * <p>Reading walks the dex structure directly - field initialisers, {@code const-string}
 * instructions and annotation values - rather than going through the DEX editor ClassTree. That class is
 * built for showing a class tree in an editor and costs a working directory plus a full
 * disassembly; this screen never shows a class, it only needs a flat set of literals.
 *
 * <p>Writing is a smali round trip: disassemble, substitute inside quoted literals, reassemble.
 * A dex string is usually interned in the constant pool and shared, so it cannot be edited in
 * place; that is also why this is slower than the arsc path.
 */
public class DexStringsTranslator {

    /** smali target level. Reading accepts any dex version; this only governs what we emit. */
    private static final int DEFAULT_API = 35;

    /** One dex file, together with the APK entry it came from. */
    public static final class DexEntry {
        public final String entryName;
        public final File file;

        DexEntry(String entryName, File file) {
            this.entryName = entryName;
            this.file = file;
        }
    }

    private final List<DexEntry> entries;

    private DexStringsTranslator(List<DexEntry> entries) {
        this.entries = entries;
    }

    public List<DexEntry> entries() {
        return entries;
    }

    /** Entry names as they appear in the APK, for the header line. */
    public String entrySummary() {
        if (entries.isEmpty()) return "";
        if (entries.size() == 1) return entries.get(0).entryName;
        return entries.get(0).entryName + " +" + (entries.size() - 1);
    }

    public static DexStringsTranslator open(List<String> paths) throws IOException {
        List<DexEntry> entries = new ArrayList<>();
        for (String path : paths) {
            File f = new File(path);
            if (f.isFile()) entries.add(new DexEntry(f.getName(), f));
        }
        if (entries.isEmpty()) throw new IOException("No readable dex files");
        return new DexStringsTranslator(entries);
    }

    /**
     * Every distinct string literal across the selected dex files.
     *
     * <p>Call off the main thread: a large multidex APK has tens of thousands of these and the
     * walk is per-class.
     */
    public List<String> literals() throws IOException {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (DexEntry entry : entries) {
            DexBackedDexFile dex = openDex(entry.file);
            for (ClassDef c : dex.getClasses()) {
                collect(c, seen);
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    private DexBackedDexFile openDex(File file) throws IOException {
        return DexFileFactory.loadDexFile(file, Opcodes.forApi(DEFAULT_API));
    }

    /** Field initialisers, const-string instructions and annotation values, in that order. */
    private void collect(ClassDef c, Map<String, Boolean> seen) {
        try {
            for (Field f : c.getFields()) {
                EncodedValue v = f.getInitialValue();
                if (v instanceof StringEncodedValue) {
                    String s = ((StringEncodedValue) v).getValue();
                    if (s != null && !s.isEmpty()) seen.putIfAbsent(s, Boolean.TRUE);
                }
            }
            for (Method m : c.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                for (Instruction inst : impl.getInstructions()) {
                    if (!(inst instanceof ReferenceInstruction)) continue;
                    Reference ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String s = ((StringReference) ref).getString();
                        if (s != null && !s.isEmpty()) seen.putIfAbsent(s, Boolean.TRUE);
                    }
                }
            }
        } catch (Throwable ignored) {
            // One class that will not decode must not cost the APK its whole string list.
        }
    }

    /**
     * Writes the staged translations back into every selected dex file.
     *
     * <p>Each dex is disassembled once and all replacements applied in that single pass. The
     * existing single-string helper takes one find/replace pair, so calling it per entry would
     * re-disassemble and reassemble the file once per string - thousands of full round trips on a
     * multidex APK.
     *
     * @param replacements original literal to translated value; a null or unchanged value is skipped
     * @return the number of distinct literals changed
     */
    public int apply(Map<String, String> replacements) throws IOException {
        if (replacements == null || replacements.isEmpty()) return 0;
        int changed = 0;
        for (DexEntry entry : entries) {
            changed += patchDex(entry, replacements);
        }
        // The archive itself is not touched here. The edited files live side by side in one
        // directory, and ApkResultHandler already knows to add every *.dex in that directory to
        // the APK - which is exactly right for a multidex translation, and would be wrong if this
        // class injected them one at a time.
        return changed;
    }

    private int patchDex(DexEntry entry, Map<String, String> replacements) throws IOException {
        File tmpRoot = Files.createTempDirectory("dextrans").toFile();
        try {
            DexBackedDexFile dex = openDex(entry.file);
            Set<String> descriptors = new LinkedHashSet<>();
            for (ClassDef c : dex.getClasses()) descriptors.add(c.getType());

            File smaliDir = new File(tmpRoot, "smali");
            //noinspection ResultOfMethodCallIgnored
            smaliDir.mkdirs();
            BaksmaliOptions options = FastDexPatch.defaultBaksmaliOptions();
            FastDexPatch.disassembleClasses(dex, descriptors, smaliDir, options, null);

            Map<String, String> applied = new LinkedHashMap<>();
            List<File> smaliFiles = new ArrayList<>();
            collectSmali(smaliDir, smaliFiles);
            Collections.sort(smaliFiles);
            for (File smali : smaliFiles) {
                String text = new String(Files.readAllBytes(smali.toPath()), StandardCharsets.UTF_8);
                String rebuilt = substitute(text, replacements, applied);
                if (!rebuilt.equals(text)) {
                    Files.write(smali.toPath(), rebuilt.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (applied.isEmpty()) return 0;

            SmaliOptions smaliOptions = new SmaliOptions();
            File rebuilt = new File(tmpRoot, "out.dex");
            smaliOptions.outputDexFile = rebuilt.getPath();
            smaliOptions.jobs = 1;
            smaliOptions.apiLevel = DEFAULT_API;
            if (!Smali.assemble(smaliOptions, smaliDir.getPath())) {
                throw new IOException("Failed to assemble " + entry.entryName);
            }
            // Replace only once the new dex exists, so a failed assemble leaves the extracted file
            // untouched and a retry starts from the same state.
            Files.copy(rebuilt.toPath(), entry.file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return applied.size();
        } finally {
            deleteRecursive(tmpRoot);
        }
    }

    /** Matches a quoted smali literal, honouring escapes. */
    private static final Pattern QUOTED = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    /**
     * Replaces whole literals, never substrings.
     *
     * <p>Whole-literal matching is the point: a substring rule would rewrite {@code "Save"} inside
     * {@code "Save the file"} and change code that only meant part of it.
     */
    private static String substitute(String text, Map<String, String> replacements,
                                     Map<String, String> applied) {
        Matcher m = QUOTED.matcher(text);
        StringBuffer out = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            String literal = m.group(1);
            String replacement = replacements.get(literal);
            String chosen;
            if (replacement == null || replacement.isEmpty() || replacement.equals(literal)) {
                chosen = m.group(0);
            } else {
                chosen = "\"" + escapeForSmali(replacement) + "\"";
                applied.put(literal, replacement);
                changed = true;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(chosen));
        }
        m.appendTail(out);
        return changed ? out.toString() : text;
    }

    /** A literal going into smali cannot carry a raw newline or an unescaped quote. */
    private static String escapeForSmali(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void collectSmali(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectSmali(f, out);
            else if (f.getName().endsWith(".smali")) out.add(f);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}