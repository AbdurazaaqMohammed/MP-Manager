package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DexMergeUtil {

    private static final int MAX_SECTION_LIMIT = 64000;

    private DexMergeUtil() {
    }

    /**
     * Merges classes from multiple DEX files into one or more output DEX files,
     * automatically splitting when methods, fields, or types exceed the 64K limit.
     *
     * @param inputs List of source DEX files to merge.
     * @param baseOut The target output file (e.g. classes_merged.dex).
     * @param api The Android API level for DEX opcodes.
     * @return List of generated output DEX files.
     * @throws IOException If loading or writing DEX fails.
     */
    public static List<File> mergeDexFiles(List<File> inputs, File baseOut, int api) throws IOException {
        List<ClassDef> allClasses = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();

        for (File f : inputs) {
            // Pre-repair if damaged header
            try {
                DexRepairEngine.repairHeader(f);
            } catch (Exception ignored) {}

            DexBackedDexFile dex = DexFileFactory.loadDexFile(f, null);
            for (ClassDef c : dex.getClasses()) {
                if (seenTypes.add(c.getType())) {
                    allClasses.add(c);
                }
            }
        }

        List<File> generatedFiles = new ArrayList<>();
        File parentDir = baseOut.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }

        String baseName = baseOut.getName();
        String prefix = baseName.endsWith(".dex") ? baseName.substring(0, baseName.length() - 4) : baseName;

        Opcodes opcodes = Opcodes.forApi(api);
        DexPool currentPool = new DexPool(opcodes);
        int dexIndex = 1;

        for (ClassDef c : allClasses) {
            currentPool.internClass(DexDebugStripper.strip(c));

            int methodCount = currentPool.methodSection.getItemCount();
            int fieldCount = currentPool.fieldSection.getItemCount();
            int typeCount = currentPool.typeSection.getItemCount();

            if (methodCount >= MAX_SECTION_LIMIT || fieldCount >= MAX_SECTION_LIMIT || typeCount >= MAX_SECTION_LIMIT) {
                File currentOut = getOutputFile(parentDir, prefix, dexIndex);
                writePoolToFile(currentPool, currentOut);
                generatedFiles.add(currentOut);

                dexIndex++;
                currentPool = new DexPool(opcodes);
            }
        }

        if (currentPool.classSection.getItemCount() > 0 || generatedFiles.isEmpty()) {
            File currentOut = getOutputFile(parentDir, prefix, dexIndex);
            writePoolToFile(currentPool, currentOut);
            generatedFiles.add(currentOut);
        }

        return generatedFiles;
    }

    private static File getOutputFile(File parentDir, String prefix, int dexIndex) {
        String name = (dexIndex == 1) ? prefix + ".dex" : prefix + dexIndex + ".dex";
        return new File(parentDir, name);
    }

    private static void writePoolToFile(DexPool pool, File out) throws IOException {
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        byte[] data = Arrays.copyOf(store.getData(), store.getSize());
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
    }
}
