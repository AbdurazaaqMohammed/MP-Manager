package modder.hub.dexeditor.utils;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.smali2.Smali;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import modder.hub.dexeditor.model.TreeNode;
import modder.hub.dexeditor.smali.SharedSmaliUtils;

public final class DexBatchExporter {
    public static final int FORMAT_SMALI = 0;
    public static final int FORMAT_DEX = 1;
    public static final int FORMAT_JAR = 2;

    private DexBatchExporter() {
    }

    public static List<String> resolveTypes(ClassTree tree, List<TreeNode> nodes) {
        Set<String> types = new LinkedHashSet<>();
        if (tree == null || tree.classMap == null || nodes == null) return new ArrayList<>(types);
        for (TreeNode node : nodes) {
            if (node == null) continue;
            String full = node.getFullName();
            if (full == null) continue;
            if (node.isDirectory()) {
                String prefix = full.endsWith("/") ? full : full + "/";
                for (String key : tree.classMap.keySet()) {
                    if (key != null && key.startsWith(prefix)) types.add(key);
                }
            } else {
                types.add(full);
            }
        }
        return new ArrayList<>(types);
    }

    public static Map<String, ClassDef> resolveDefs(ClassTree tree, List<String> types, int dexVersion) throws Exception {
        Map<String, ClassDef> out = new LinkedHashMap<>();
        Map<String, String> pending = tree.getPendingSmaliMap();
        for (String type : types) {
            String smali = pending != null ? pending.get(type) : null;
            ClassDef def;
            if (smali != null) {
                try {
                    def = Smali.assemble(smali, SharedSmaliUtils.ASSEMBLE_OPTIONS, dexVersion);
                } catch (Exception e) {
                    throw new Exception(type + ": " + e.getMessage(), e);
                }
            } else {
                def = tree.classMap.get(type);
                if (def == null) throw new Exception("Class not found: " + type);
            }
            out.put(type, def);
        }
        return out;
    }

    public static int exportSmali(ClassTree tree, Map<String, ClassDef> defs, File destDir) throws Exception {
        Map<String, String> pending = tree.getPendingSmaliMap();
        int n = 0;
        for (Map.Entry<String, ClassDef> e : defs.entrySet()) {
            String smali = pending != null ? pending.get(e.getKey()) : null;
            if (smali == null) smali = tree.getPureSmaliCached(e.getValue(), e.getKey());
            if (smali == null) throw new Exception("No smali for: " + e.getKey());
            File out = new File(destDir, e.getKey() + ".smali");
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(smali.getBytes(StandardCharsets.UTF_8));
            }
            n++;
        }
        return n;
    }

    public static File exportDex(Map<String, ClassDef> defs, int dexVersion, File destDir, String baseName) throws Exception {
        DexBuilder builder = new DexBuilder(Opcodes.forDexVersion(dexVersion));
        builder.setIgnoreMethodAndFieldError(true);
        for (ClassDef def : defs.values()) builder.internClassDef(def);
        MemoryDataStore store = new MemoryDataStore(Math.max(1 << 16, defs.size() * 1024));
        builder.writeTo(store);
        File out = unusedFile(destDir, baseName + ".dex");
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(store.getBuffer(), 0, store.getSize());
        }
        return out;
    }

    public static File exportJar(Map<String, ClassDef> defs, int dexVersion, File cacheDir, File destDir, String baseName) throws Exception {
        File tmpDex = File.createTempFile("export", ".dex", cacheDir);
        try {
            DexBuilder builder = new DexBuilder(Opcodes.forDexVersion(dexVersion));
            builder.setIgnoreMethodAndFieldError(true);
            for (ClassDef def : defs.values()) builder.internClassDef(def);
            builder.writeTo(new FileDataStore(tmpDex));
            JadxArgs args = new JadxArgs();
            args.setSkipResources(true);
            args.setShowInconsistentCode(true);
            args.setInputFiles(ImmutableList.of(tmpDex));
            JadxDecompiler decompiler = new JadxDecompiler(args);
            try {
                decompiler.load();
                File out = unusedFile(destDir, baseName + "-sources.jar");
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
                    ZipEntry man = new ZipEntry("META-INF/MANIFEST.MF");
                    zos.putNextEntry(man);
                    zos.write("Manifest-Version: 1.0\nCreated-By: MP Manager\n".getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    for (JavaClass cls : decompiler.getClasses()) {
                        String name = cls.getFullName();
                        if (name == null || name.isEmpty()) continue;
                        try {
                            cls.decompile();
                        } catch (Exception decompileError) {
                            continue;
                        }
                        String code = cls.getCode();
                        if (code == null) continue;
                        ZipEntry ze = new ZipEntry(name.replace('.', '/') + ".java");
                        zos.putNextEntry(ze);
                        zos.write(code.getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
                return out;
            } finally {
                try {
                    decompiler.close();
                } catch (Exception ignored) {
                }
            }
        } finally {
            try {
                tmpDex.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private static File unusedFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        do {
            f = new File(dir, base + " (" + i + ")" + ext);
            i++;
        } while (f.exists());
        return f;
    }
}
