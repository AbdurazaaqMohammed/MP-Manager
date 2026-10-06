/*
 * Dex-Editor-Android an Advanced Dex Editor for Android
 * Copyright 2024-26, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */

package modder.hub.dexeditor.utils;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.DebugItemType;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.smali2.Smali;
import com.android.tools.smali.dexlib2.util.DexUtil;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import modder.hub.dexeditor.activity.DexEditorActivity;
import modder.hub.dexeditor.model.TreeNode;
import modder.hub.dexeditor.smali.SharedSmaliUtils;

public class ClassTree {
	
	/*
	Author @developer-krushna
	Orginally replicate from Flying-Yu AE Manager on github
	*/
    /*
     * There are some major advancement and enhancement are made by me. From loading of multi dex to
     faster batch class deletion and even really  fatser dex compilatin .

     * There is so many usefull tricks for advancing your smali assembly and disaembly knowledge
     * Here I have made significant improvement in loading/ compiling/ editing of dexes
     */


    private String DELETED_CLASSES_JSON;
    private String EDITED_CLASSES_JSON;
    private final String workDir;
    private final Map<String, HashSet<String>> editedClassMap = new HashMap<>();
    private final Map<String, String> pendingSmaliMap = new HashMap<>();
    public Tree tree;
    public HashMap<String, ClassDef> classMap;
    public List<DexBackedDexFile> dexFiles;
    public DexBackedDexFile dexFile;
    public String Path;
    public ClassDef curClassDef;
    public int dep;
    public Stack<String> path;
    public String curFile;
    public final List<ClassDef> classDefList = new ArrayList<>();
    public final List<String> paths;
    public int dexVersion;
    final Map<String, List<String>> dexClassMap = new LinkedHashMap<>();
    byte[] data;
    byte[] input;

    private Map<String, HashSet<String>> deletedClassJson = new HashMap<>();
    private final Map<String, String> typeToDexMap = new HashMap<>();
    private final Map<String, DexBackedDexFile> dexFileByName = new HashMap<>();
    // Concurrent so the hot edit path (saveClassDef / saveAllDexFiles) needs no monitor.
    private final ConcurrentMap<String, Integer> classDefIndex = new ConcurrentHashMap<>(1 << 14);

    /**
     * Bounded LRU cache of disassembled classes.
     *
     * <p>The previous implementation was a {@link ConcurrentHashMap} that called {@code clear()}
     * the moment it reached {@code SMALI_CACHE_MAX} entries. That is the worst possible policy for
     * a full-dex search or browse: after 256 classes it throws away <em>everything</em>, including
     * the entries it is actively re-reading, so classes get disassembled over and over. An LRU keeps
     * the hot working set instead of flushing it wholesale.
     *
     * <p>Thread-safe: search fans out over a pool of workers that all read through this cache.
     */
    private final Map<String, String> pureSmaliCache =
            Collections.synchronizedMap(new LinkedHashMap<String, String>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > smaliCacheMax();
                }
            });

    /** Cap scales with the heap so a large device can cache more disassembly and a small one cannot OOM. */
    private static int smaliCacheMax() {
        long maxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        return (int) Math.max(256L, Math.min(4096L, maxMb / 8L));
    }

    private static final Set<String> activeWorkDirs = new HashSet<>();

    public static synchronized void claimWorkDir(String workDir) {
        if (workDir != null) activeWorkDirs.add(workDir);
    }

    public static synchronized void releaseWorkDir(String workDir) {
        if (workDir != null) activeWorkDirs.remove(workDir);
    }

    public static synchronized boolean isWorkDirClaimed(String workDir) {
        return workDir != null && activeWorkDirs.contains(workDir);
    }

    public static class CompilationOptions {
        public String dexVersion = "Keep the same";
        public boolean removeAllDebug = false;
        public boolean removeDebugSource = false;
        public boolean removeDebugLine = false;
        public boolean removeDebugParam = false;
        public boolean removeDebugPrologue = false;
        public boolean removeDebugLocal = false;
    }

    private CompilationOptions compilationOptions = new CompilationOptions();

    public void setCompilationOptions(CompilationOptions options) {
        this.compilationOptions = options;
    }

    public ClassTree(List<String> mPaths, String cacheDir) throws Exception {
        this.paths = mPaths;
        this.workDir = cacheDir;
        initPaths();
        initMultiDex();
        loadDeletedClasses();
    }

    private void initPaths() {
        File dir = new File(workDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        DELETED_CLASSES_JSON = new File(dir, "deletedclasses.json").getAbsolutePath();
        EDITED_CLASSES_JSON = new File(dir, "editedclasses.json").getAbsolutePath();
    }

    @Deprecated
    private void initDex() throws Exception {
        byte[] read = read(this.Path);
        this.input = read;
        int verifyDexHeader = DexUtil.verifyDexHeader(read, 0);
        this.dexVersion = verifyDexHeader;
        dexFile = DexBackedDexFile.fromInputStream(Opcodes.forDexVersion(verifyDexHeader), new ByteArrayInputStream(input));
        classDefList.addAll(dexFile.getClasses());
        initClassMap();
    }

    private void initMultiDex() throws Exception {
        dexFiles = new ArrayList<>();
        classDefList.clear();
        typeToDexMap.clear();
        dexClassMap.clear();
        dexFileByName.clear();

        final int n = paths.size();
        List<DexLoadResult> results;
        if (n == 1) {
            results = Collections.singletonList(loadSingleDex(paths.get(0)));
        } else {
            int numThreads = Math.min(n, Math.max(1, Runtime.getRuntime().availableProcessors()));
            ExecutorService pool = Executors.newFixedThreadPool(numThreads);
            List<Future<DexLoadResult>> futures = new ArrayList<>(n);
            try {
                for (String path : paths) {
                    futures.add(pool.submit(() -> loadSingleDex(path)));
                }
                results = new ArrayList<>(n);
                for (Future<DexLoadResult> f : futures) {
                    try {
                        results.add(f.get());
                    } catch (Exception e) {
                        Throwable c = e.getCause() != null ? e.getCause() : e;
                        for (Future<DexLoadResult> rest : futures) rest.cancel(true);
                        if (c instanceof Exception) throw (Exception) c;
                        throw new Exception(c);
                    }
                }
            } finally {
                pool.shutdown();
            }
        }

        // Merge once, single threaded. Each dex was parsed into its own private maps by
        // loadSingleDex(), so no locking is needed here and no lock is taken per class.
        int totalClasses = 0;
        for (DexLoadResult result : results) totalClasses += result.defs.size();
        ((ArrayList<ClassDef>) classDefList).ensureCapacity(totalClasses);

        for (DexLoadResult result : results) {
            if (result.dexVersion > this.dexVersion) this.dexVersion = result.dexVersion;
            dexFiles.add(result.dexFile);
            dexFileByName.put(result.fileName, result.dexFile);
            dexClassMap.put(result.fileName, result.classNames);
            classDefList.addAll(result.defs);
            typeToDexMap.putAll(result.typeToDex);
        }

        // Size the map for the real class count. The old sizing (paths.size() * 4096) forced a
        // long chain of HashMap rehashes for any dex holding more than a few thousand classes.
        classMap = new HashMap<>((int) (totalClasses / 0.75f) + 16);
        for (DexLoadResult result : results) classMap.putAll(result.classMap);

        classDefIndex.clear();
        int index = 0;
        for (ClassDef classDef : classDefList) {
            classDefIndex.put(classDef.getType(), index++);
        }
        // initClassMap() is now integrated into the merge above.
        // saveAllClassesJson() was removed: it Gson-serialised every class name to a multi-megabyte
        // JSON file on each open and nothing ever read that file back.
    }

    /** Per-dex parse result. Each dex is built independently so no shared collection is touched
     *  until the single-threaded merge in {@link #initMultiDex()}. */
    private static final class DexLoadResult {
        final DexBackedDexFile dexFile;
        final String fileName;
        final int dexVersion;
        final List<ClassDef> defs;
        final List<String> classNames;
        final Map<String, ClassDef> classMap;
        final Map<String, String> typeToDex;

        DexLoadResult(DexBackedDexFile dexFile, String fileName, int dexVersion,
                      List<ClassDef> defs, List<String> classNames,
                      Map<String, ClassDef> classMap, Map<String, String> typeToDex) {
            this.dexFile = dexFile;
            this.fileName = fileName;
            this.dexVersion = dexVersion;
            this.defs = defs;
            this.classNames = classNames;
            this.classMap = classMap;
            this.typeToDex = typeToDex;
        }
    }

    private DexLoadResult loadSingleDex(String path) throws Exception {
        byte[] buf = read(path);
        int verifyDexHeader = DexUtil.verifyDexHeader(buf, 0);

        // Build the dex straight from the buffer we already hold. DexBackedDexFile.fromInputStream()
        // copies the whole stream into a second byte[], doubling peak memory and the load time.
        // The 3-arg constructor is the public form of (opcodes, buf, offset, verifyMagic=false),
        // so it behaves identically to fromInputStream() minus the redundant copy.
        DexBackedDexFile file = new DexBackedDexFile(Opcodes.forDexVersion(verifyDexHeader), buf, 0);

        Set<? extends ClassDef> classes = file.getClasses();
        int classCount = classes.size();
        List<ClassDef> defs = new ArrayList<>(classCount);
        List<String> classNames = new ArrayList<>(classCount);
        int capacity = (int) (classCount / 0.75f) + 16;
        Map<String, ClassDef> localClassMap = new HashMap<>(capacity);
        Map<String, String> localTypeToDex = new HashMap<>(capacity);

        // Same test as the old isClassDeleted(), but resolved once per dex instead of a
        // "L" + type + ";" concatenation plus a typeToDexMap lookup for every single class.
        // NOTE: preserved as-is. loadDeletedClasses() runs *after* initMultiDex() in the
        // constructor, so deletedClassJson is still empty here and this lookup never matches -
        // exactly like the isClassDeleted() call it replaced. Re-ordering those two calls would
        // make reopened sessions hide deleted classes from the tree; that is a behaviour change,
        // so it is deliberately left for a separate decision.
        String fileName = new File(path).getName();
        HashSet<String> deleted = deletedClassJson.get(fileName);

        for (ClassDef classDef : classes) {
            String type = classDef.getType();
            defs.add(classDef);
            classNames.add(type);
            localTypeToDex.put(type, fileName);
            String typeName = type.substring(1, type.length() - 1);
            if (deleted == null || !deleted.contains(typeName)) {
                localClassMap.put(typeName, classDef);
            }
        }
        return new DexLoadResult(file, fileName, verifyDexHeader,
                defs, classNames, localClassMap, localTypeToDex);
    }

    // loading the deleted classes from JSON list
    private void loadDeletedClasses() {
        try {
            File file = new File(DELETED_CLASSES_JSON);
            if (file.exists()) {
                String json = new String(read(DELETED_CLASSES_JSON));
                Map<String, List<String>> loaded = new Gson().fromJson(json, new TypeToken<Map<String, List<String>>>() {}.getType());
                deletedClassJson.clear();
                for (Entry<String, List<String>> entry : loaded.entrySet()) {
                    deletedClassJson.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            } else {
                deletedClassJson = new HashMap<>();
            }
        } catch (Exception e) {
            deletedClassJson = new HashMap<>();
        }
    }

    // saving deleted classes as JSON so that will be excluded during the dex compilation
    private void saveDeletedClasses() {
        try {
            File file = new File(DELETED_CLASSES_JSON);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (file.exists()) {
                file.delete();
            }

            Gson gson = new GsonBuilder().create();
            // Convert HashSet to List for JSON
            Map<String, List<String>> toSave = new HashMap<>();
            for (Entry<String, HashSet<String>> entry : deletedClassJson.entrySet()) {
                toSave.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            String json = gson.toJson(toSave);
            FileWriter writer = new FileWriter(DELETED_CLASSES_JSON);
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initClassMap() {
        if (classMap == null) classMap = new HashMap<>();
        else classMap.clear();

        for (ClassDef classDef : classDefList) {
            String type = classDef.getType();
            type = type.substring(1, type.length() - 1);
            if (!isClassDeleted(type)) {
                classMap.put(type, classDef);
            }
        }
        tree = new Tree(classMap);
    }

    // checking if the class is beigh deleted usefull in case of searching
    private boolean isClassDeleted(String type) {
        String fileName = typeToDexMap.get("L" + type + ";");
        if (fileName != null) {
            HashSet<String> deleted = deletedClassJson.get(fileName);
            return deleted != null && deleted.contains(type);
        }
        return false;
    }

    // removal of batch or single classes from tree node
    public void removeClasses(List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) return;

        List<String> folderPrefixes = new ArrayList<>();
        Set<String> individualClasses = new HashSet<>();

        for (String name : classNames) {
            if (name.endsWith("/")) {
                folderPrefixes.add(name);
            } else {
                individualClasses.add(name);
            }
        }
        pureSmaliCache.keySet().removeAll(individualClasses);
        for (String prefix : folderPrefixes) {
            pureSmaliCache.keySet().removeIf(key -> key.startsWith(prefix));
        }

        // Optimization: Use a single pass over the map when folders are involved (O(N))
        if (!folderPrefixes.isEmpty()) {
            Iterator<Map.Entry<String, ClassDef>> it = classMap.entrySet().iterator();
            while (it.hasNext()) {
                String className = it.next().getKey();
                if (shouldRemove(className, individualClasses, folderPrefixes)) {
                    recordRemovedClass(className);
                    it.remove();
                    pendingSmaliMap.remove(className);
                    unrecordEditedClass(className);
                }
            }

            // Cleanup for classes that might only exist in edited maps
            for (HashSet<String> set : editedClassMap.values()) {
                Iterator<String> setIt = set.iterator();
                while (setIt.hasNext()) {
                    if (shouldRemove(setIt.next(), individualClasses, folderPrefixes)) {
                        setIt.remove();
                    }
                }
            }

            Iterator<String> pendingIt = pendingSmaliMap.keySet().iterator();
            while (pendingIt.hasNext()) {
                if (shouldRemove(pendingIt.next(), individualClasses, folderPrefixes)) {
                    pendingIt.remove();
                }
            }
        } else {
            // High-performance path for individual class removals (O(1) lookups)
            for (String name : individualClasses) {
                if (classMap.remove(name) != null) {
                    recordRemovedClass(name);
                }
                pendingSmaliMap.remove(name);
                unrecordEditedClass(name);
            }
        }

        tree = new Tree(classMap);
        saveDeletedClasses();
    }

    private boolean shouldRemove(String className, Set<String> individualClasses, List<String> folderPrefixes) {
        if (individualClasses.contains(className)) return true;
        for (String prefix : folderPrefixes) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    // remove single class only
    public void removeClass(String className) {
        removeClasses(ImmutableList.of(className));
    }

    // the remove classes will be recorded during batch and preserve in json
    private void recordRemovedClass(String type) {
        String fileName = typeToDexMap.get("L" + type + ";");
        if (fileName != null) {
            HashSet<String> deleted = deletedClassJson.get(fileName);
            if (deleted == null) {
                deleted = new HashSet<>();
                deletedClassJson.put(fileName, deleted);
            }
            deleted.add(type);
        }
    }

    // save smali according to the class and its content
    // it's just a mapping way to preserve  smali data in memeory, but it's not a good way it may cause self kill app like situation and mt manager never do like this
    public void saveSmali(String type, String smali) {
        pendingSmaliMap.put(type, smali);
        pureSmaliCache.remove(type);
        recordEditedClass(type);
        DexEditorActivity.isChanged = true;
        DexEditorActivity.isSaved = false;
    }

    // save class def to the main node o the dex and record the changes classes
    public void saveClassDef(ClassDef classDef) {
        String type = classDef.getType().substring(1, classDef.getType().length() - 1);
        pendingSmaliMap.remove(type);
        pureSmaliCache.remove(type);

        classMap.put(type, classDef);

        String rawType = "L" + type + ";";
        Integer index = classDefIndex.get(rawType);
        if (index != null) {
            classDefList.set(index, classDef);
        } else {
            synchronized (classDefList) {
                for (int i = 0; i < classDefList.size(); i++) {
                    ClassDef existingDef = classDefList.get(i);
                    String existingType = existingDef.getType();
                    if (existingType.equals(rawType)) {
                        classDefList.set(i, classDef);
                        classDefIndex.put(rawType, i);
                        break;
                    }
                }
            }
        }

        recordEditedClass(type);
        DexEditorActivity.isChanged = true;
        DexEditorActivity.isSaved = false;
    }

    // save class def without invalidating caches when the code text did not change
    public void saveClassDefIfChanged(ClassDef classDef, String code) {
        String type = classDef.getType().substring(1, classDef.getType().length() - 1);
        if (isSameAsCachedSmali(type, code)) {
            return;
        }
        saveClassDef(classDef);
    }

    public boolean isSameAsCachedSmali(String typeKey, String smaliWithHeader) {
        String cached = pureSmaliCache.get(typeKey);
        if (cached == null || pendingSmaliMap.containsKey(typeKey)) return false;
        String body = smaliWithHeader;
        if (body.trim().startsWith("# ")) {
            body = body.replaceFirst("(?s)^#.*?\\n\\n", "");
        }
        return cached.equals(body);
    }

    public String getSmaliByType(ClassDef classDef) throws Exception {
        String type = classDef.getType();
        String typeKey = type.substring(1, type.length() - 1);
        String smali;
        
        // Check if we have unsaved smali in memory
        // MEMEORY WORKS are really not good especially for android. MT Manager never do this it save all as files and retrive them from
        // files only and there will be IO exception and even background kill
        if (pendingSmaliMap.containsKey(typeKey)) {
            smali = pendingSmaliMap.get(typeKey);
        } else {
            smali = getPureSmaliCached(classDef, typeKey);
        }

        // We strip any existing header to avoid "# classes.dex" appearing multiple times
        // This usually happens during search and replace operations
        if (smali != null && smali.trim().startsWith("# ")) {
            smali = smali.replaceFirst("(?s)^#.*?\\n\\n", "");
        }

        String dexFileName = findDexFileNameForClass(classDef);
        return "# " + dexFileName + "\n\n" + smali;
    }

    public String getPureSmaliCached(ClassDef classDef, String typeKey) throws Exception {
        String cached = pureSmaliCache.get(typeKey);
        if (cached != null) return cached;
        String fresh = getPureSmaliFromClassDef(classDef);
        // The cache evicts itself: pureSmaliCache is an access-ordered LinkedHashMap that drops
        // the least-recently-used entry once it exceeds smaliCacheMax(). No clear(), no manual
        // size bookkeeping, and the classes actually being re-read stay resident.
        pureSmaliCache.put(typeKey, fresh);
        return fresh;
    }

    public void invalidateSmaliCache(String typeKey) {
        if (typeKey == null) return;
        pureSmaliCache.remove(typeKey);
    }

    // This returns the smali code without any informative headers
    public String getPureSmaliFromClassDef(ClassDef classDef) throws Exception {
        StringWriter stringWriter = new StringWriter(16 * 1024);
        BaksmaliWriter baksmaliWriter = new BaksmaliWriter(stringWriter);
        new ClassDefinition(SharedSmaliUtils.OPTIONS, classDef).writeTo(baksmaliWriter);
        baksmaliWriter.close();
        return stringWriter.toString();
    }

    public String findDexFileNameForClass(ClassDef classDef) {
        String fileName = typeToDexMap.get(classDef.getType());
        if (fileName != null) {
            return fileName;
        }
        return "unknown.dex";
    }

    // record the edited classes
    private void recordEditedClass(String type) {
        String fileName = typeToDexMap.get("L" + type + ";");
        if (fileName != null) {
            HashSet<String> edited = editedClassMap.get(fileName);
            if (edited == null) {
                edited = new HashSet<>();
                editedClassMap.put(fileName, edited);
            }
            edited.add(type);
        }
    }

    private void unrecordEditedClass(String type) {
        String fileName = typeToDexMap.get("L" + type + ";");
        if (fileName != null) {
            HashSet<String> edited = editedClassMap.get(fileName);
            if (edited != null) {
                edited.remove(type);
            }
        }
    }

    // save all loaded dexes
    @SuppressLint("SdCardPath")
    public void saveAllDexFiles(DexSaveProgress dexSaveProgress) throws Exception {
        if (dexClassMap == null || dexClassMap.isEmpty()) return;

        int total = dexClassMap.size();

        int targetDexVersion = this.dexVersion;
        if (!compilationOptions.dexVersion.equals("Keep the same")) {
            try {
                targetDexVersion = Integer.parseInt(compilationOptions.dexVersion);
            } catch (Exception ignored) {}
        }
        final int finalTargetDexVersion = targetDexVersion;

        final Opcodes opcodes = Opcodes.forDexVersion(targetDexVersion);

        boolean forceCompileAll = compilationOptions.removeAllDebug || compilationOptions.removeDebugSource || 
                                 compilationOptions.removeDebugLine || compilationOptions.removeDebugParam || 
                                 compilationOptions.removeDebugPrologue || compilationOptions.removeDebugLocal ||
                                 !compilationOptions.dexVersion.equals("Keep the same");

        int numThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            int current = 1;
            for (Entry<String, List<String>> entry : dexClassMap.entrySet()) {
            String fileName = entry.getKey();
            dexSaveProgress.onTitle(fileName + " (" + current + "/" + total + ")");

            boolean isTouched = forceCompileAll || deletedClassJson.containsKey(fileName) || editedClassMap.containsKey(fileName);
            if (!isTouched) {
                current++;
                continue;
            }

            List<String> classNames = entry.getValue();
            int classCount = classNames.size();
            AtomicInteger processed = new AtomicInteger(0);
            final Exception[] threadException = {null};

            DexBuilder dexBuilder = new DexBuilder(opcodes);
            dexBuilder.setIgnoreMethodAndFieldError(true);

            final Map<String, ClassDef> assembledDefs = new ConcurrentHashMap<>();
            HashSet<String> deleted = deletedClassJson.get(fileName);
            final HashSet<String> deletedSet = deleted;

            // Chunk the work instead of submitting one Future per class.
            // A 30k-class dex used to allocate 30k FutureTask objects plus 30k queue entries and
            // 30k ThreadPoolExecutor queue offers, all to do ~30k tiny intern() calls. Handing each
            // worker a contiguous slice collapses that to a handful of tasks while keeping every
            // worker busy and the per-class work identical.
            final int chunkSize = Math.max(64, (classCount + (numThreads * 8) - 1) / (numThreads * 8));
            List<Future<?>> futures = new ArrayList<>((classCount + chunkSize - 1) / chunkSize);
            for (int start = 0; start < classCount; start += chunkSize) {
                final int from = start;
                final int to = Math.min(start + chunkSize, classCount);
                futures.add(executor.submit(() -> {
                    try {
                        for (int idx = from; idx < to; idx++) {
                            if (threadException[0] != null) return null;
                            final String rawType = classNames.get(idx);
                            final String type = rawType.substring(1, rawType.length() - 1);

                            if (deletedSet != null && deletedSet.contains(type)) {
                                int p = processed.incrementAndGet();
                                if (p % 100 == 0 || p == classCount) {
                                    dexSaveProgress.onProgress(p, classCount);
                                }
                                continue;
                            }

                            final ClassDef defToIntern;
                            String pending = pendingSmaliMap.get(type);
                            if (pending != null) {
                                if (processed.get() % 50 == 0) {
                                    dexSaveProgress.onMessage("Assembling " + type + "...");
                                }
                                defToIntern = Smali.assemble(pending, SharedSmaliUtils.ASSEMBLE_OPTIONS, finalTargetDexVersion);
                                assembledDefs.put(type, defToIntern);
                            } else {
                                defToIntern = classMap.get(type);
                            }

                            if (defToIntern == null) {
                                int p = processed.incrementAndGet();
                                if (p % 100 == 0 || p == classCount) {
                                    dexSaveProgress.onProgress(p, classCount);
                                }
                                continue;
                            }

                            ClassDef strippedDef = defToIntern;
                            if (compilationOptions.removeAllDebug || compilationOptions.removeDebugSource ||
                                compilationOptions.removeDebugLine || compilationOptions.removeDebugParam ||
                                compilationOptions.removeDebugPrologue || compilationOptions.removeDebugLocal) {
                                strippedDef = new DebugInfoStripper(defToIntern, compilationOptions);
                            }

                            dexBuilder.internClassDef(strippedDef);

                            int p = processed.incrementAndGet();
                            if (p % 200 == 0 || p == classCount) {
                                dexSaveProgress.onMessage("Compiling...");
                            }
                            if (p % 100 == 0 || p == classCount) {
                                dexSaveProgress.onProgress(p, classCount);
                            }
                        }
                    } catch (final Throwable t) {
                        Exception e = t instanceof Exception ? (Exception) t : new Exception(t);
                        synchronized (threadException) {
                            if (threadException[0] == null) threadException[0] = e;
                        }
                    }
                    return null;
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                    if (threadException[0] != null) break;
                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof ExecutionException && e.getCause() != null) {
                        cause = e.getCause();
                    }
                    synchronized (threadException) {
                        if (threadException[0] == null) threadException[0] = cause instanceof Exception ? (Exception) cause : new Exception(cause);
                    }
                    break;
                }
            }
            if (threadException[0] != null) {
                throw threadException[0];
            }

            if (!assembledDefs.isEmpty()) {
                classMap.putAll(assembledDefs);
                for (Map.Entry<String, ClassDef> e : assembledDefs.entrySet()) {
                    String typeKey = e.getKey();
                    String rawType = "L" + typeKey + ";";
                    Integer index = classDefIndex.get(rawType);
                    if (index != null) {
                        classDefList.set(index, e.getValue());
                    }
                    String pending = pendingSmaliMap.remove(typeKey);
                    if (pending != null) {
                        pureSmaliCache.put(typeKey, pending.trim().startsWith("# ") ? pending.replaceFirst("(?s)^#.*?\\n\\n", "") : pending);
                    }
                }
            }

            dexSaveProgress.onMessage("Writing file...");
            try {
                // Size the scratch buffer from the original dex when we know it, and otherwise
                // from a per-class estimate. MemoryDataStore grows by copying in fixed steps, so
                // starting too small means repeatedly copying the whole buffer. The 512 B/class
                // guess badly under-shoots for any dex with real code.
                DexBackedDexFile original = dexFileByName.get(fileName);
                int estimate = original != null && original.getFileSize() > 0
                        ? (int) Math.min(Integer.MAX_VALUE - 64L, (long) original.getFileSize() * 12L / 10L + (1 << 20))
                        : classCount * 1024;
                MemoryDataStore memoryDataStore = new MemoryDataStore(Math.max(1 << 16, estimate));
                dexBuilder.writeTo(memoryDataStore);
                byte[] result = Arrays.copyOf(memoryDataStore.getBuffer(), memoryDataStore.getSize());

                String outputDir;
                if (paths != null && !paths.isEmpty()) {
                    outputDir = new File(paths.get(0)).getParent();
                } else {
                    outputDir = "/sdcard";
                }
                File outFile = new File(outputDir, fileName);
                File bakFile = new File(outFile.getAbsolutePath() + ".bak");

                if (outFile.exists()) {
                    FileUtil.copyFile(outFile.getAbsolutePath(), bakFile.getAbsolutePath());
                    outFile.delete();
                }

                FileOutputStream fos = new FileOutputStream(outFile);
                try {
                    fos.write(result);
                    fos.flush();
                } finally {
                    fos.close();
                }
                data = result;
            } catch (Exception e) {
                e.printStackTrace();
            }

            current++;
            }
        } finally {
            executor.shutdownNow();
        }

        // The dexes on disk now already contain every pending edit and deletion, so stop
        // reporting those dex files as "touched". Without this, every subsequent save would
        // recompile each dex all over again even when nothing changed since the last save.
        editedClassMap.clear();

        DexEditorActivity.isChanged = false;
        DexEditorActivity.isSaved = true;
    }

    public List<TreeNode> buildFullTree() {
        Map<String, TreeNode> allNodes = new HashMap<>();
        List<TreeNode> roots = new ArrayList<>();

        List<String> sortedKeys = new ArrayList<>(classMap.keySet());
        Collections.sort(sortedKeys);

        for (String type : sortedKeys) {
            String[] parts = type.split("/");
            TreeNode parent = null;
            String pathStr = "";

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (pathStr.isEmpty()) {
                    pathStr = part;
                } else {
                    pathStr = pathStr + "/" + part;
                }
                boolean isLast = (i == parts.length - 1);

                TreeNode node = allNodes.get(pathStr);
                if (node == null) {
                    node = new TreeNode(part, pathStr, i, !isLast);
                    allNodes.put(pathStr, node);
                    if (parent == null) {
                        roots.add(node);
                    } else {
                        parent.addChild(node);
                    }
                } else {
                    if (!isLast) {
                        node.setDirectory(true);
                    }
                }
                parent = node;
            }
        }
        sortNodes(roots);
        compactTree(roots);
        return roots;
    }

    public Map<String, String> getPendingSmaliMap() {
        return pendingSmaliMap;
    }

    public List<TreeNode> buildEditedFullTree() {
        Map<String, TreeNode> allNodes = new HashMap<>();
        List<TreeNode> roots = new ArrayList<>();

        List<String> editedClasses = new ArrayList<>();
        for (HashSet<String> classes : editedClassMap.values()) {
            editedClasses.addAll(classes);
        }
        Collections.sort(editedClasses);

        for (String type : editedClasses) {
            String[] parts = type.split("/");
            TreeNode parent = null;
            String pathStr = "";

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                pathStr = pathStr.isEmpty() ? part : pathStr + "/" + part;
                boolean isLast = (i == parts.length - 1);

                TreeNode node = allNodes.get(pathStr);
                if (node == null) {
                    node = new TreeNode(part, pathStr, i, !isLast);
                    allNodes.put(pathStr, node);
                    if (parent == null) {
                        roots.add(node);
                    } else {
                        parent.addChild(node);
                    }
                } else {
                    if (!isLast) {
                        node.setDirectory(true);
                    }
                }
                parent = node;
            }
        }
        sortNodes(roots);
        compactTree(roots);
        return roots;
    }

    private void sortNodes(List<TreeNode> nodes) {
        Collections.sort(nodes, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (TreeNode node : nodes) {
            if (!node.getChildren().isEmpty()) {
                sortNodes(node.getChildren());
            }
        }
    }

    private void compactTree(List<TreeNode> nodes) {
        for (TreeNode node : nodes) {
            if (node.isDirectory()) {
                List<TreeNode> children = node.getChildren();
                while (children.size() == 1 && children.get(0).isDirectory()) {
                    TreeNode singleChild = children.get(0);
                    node.setName(node.getName() + "." + singleChild.getName());
                    node.setFullName(singleChild.getFullName());
                    node.setChildren(singleChild.getChildren());
                    children = node.getChildren();
                }
                compactTree(children);
            }
        }
    }

    public void clearAll() {
        if (classMap != null) classMap.clear();
        classMap = null;
        path = null;
        dexFile = null;
        curClassDef = null;
        tree = null;
        curFile = null;
        pureSmaliCache.clear();
        classDefIndex.clear();
        dexFileByName.clear();
        
        // Clean up cache directory (never while a load is still using it)
        if (workDir != null && !isWorkDirClaimed(workDir)) {
            deleteRecursive(new File(workDir));
        }
        
        System.gc();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles())) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    public byte[] read(String fileName) throws IOException {
        FileInputStream fis = new FileInputStream(fileName);
        try {
            long len = fis.getChannel().size();
            byte[] out = new byte[(int) len];
            int off = 0;
            while (off < len) {
                int r = fis.read(out, off, (int) (len - off));
                if (r < 0) break;
                off += r;
            }
            if (off == len) return out;
            return Arrays.copyOf(out, off);
        } finally {
            fis.close();
        }
    }

    public void saveFile(byte[] bfile, String filePath) throws Exception {
        File file = new File(filePath);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        bos.write(bfile);
        bos.close();
    }

    public int getOpenedDexVersion() {
        return this.dexVersion;
    }

    public List<String> getAllStrings() {
        HashSet<String> allStrings = new HashSet<>();
        
        List<ClassDef> defs;
        synchronized (classMap) {
            defs = new ArrayList<>(classMap.values());
        }
        
        int numThreads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        try {
            final int chunk = Math.max(1, (defs.size() + numThreads - 1) / numThreads);
            List<Future<?>> futures = new ArrayList<>();
            for (int start = 0; start < defs.size(); start += chunk) {
                final List<ClassDef> part = defs.subList(start, Math.min(start + chunk, defs.size()));
                futures.add(pool.submit(() -> {
                    HashSet<String> local = new HashSet<>();
                    collectStringsFromClasses(part, local);
                    synchronized (allStrings) {
                        allStrings.addAll(local);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        
        List<String> result = new ArrayList<>(allStrings);
        Collections.sort(result);
        return result;
    }

    private void collectStringsFromClasses(List<ClassDef> classDefs, Set<String> allStrings) {
        for (ClassDef classDef : classDefs) {
            for (Field field : classDef.getFields()) {
                EncodedValue initialValue = field.getInitialValue();
                if (initialValue instanceof StringEncodedValue) {
                    allStrings.add(((StringEncodedValue) initialValue).getValue());
                }
            }
            
            for (Method method : classDef.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl != null) {
                    for (Instruction inst : impl.getInstructions()) {
                        if (inst instanceof ReferenceInstruction) {
                            Reference ref = ((ReferenceInstruction) inst).getReference();
                            if (ref instanceof StringReference) {
                                allStrings.add(((StringReference) ref).getString());
                            }
                        }
                    }
                }
            }
            
            collectStringsFromAnnotations(classDef.getAnnotations(), allStrings);
        }
    }

    private void collectStringsFromAnnotations(Set<? extends Annotation> annotations, Set<String> allStrings) {
        if (annotations == null) return;
        for (Annotation annotation : annotations) {
            for (AnnotationElement element : annotation.getElements()) {
                collectStringsFromEncodedValue(element.getValue(), allStrings);
            }
        }
    }

    private void collectStringsFromEncodedValue(EncodedValue value, Set<String> allStrings) {
        if (value instanceof StringEncodedValue) {
            allStrings.add(((StringEncodedValue) value).getValue());
        } else if (value instanceof AnnotationEncodedValue) {
            for (AnnotationElement element : ((AnnotationEncodedValue) value).getElements()) {
                collectStringsFromEncodedValue(element.getValue(), allStrings);
            }
        } else if (value instanceof ArrayEncodedValue) {
            for (EncodedValue subValue : ((ArrayEncodedValue) value).getValue()) {
                collectStringsFromEncodedValue(subValue, allStrings);
            }
        }
    }

    public interface DexSaveProgress {
        void onProgress(int progress, int total);

        void onMessage(String name);

        void onTitle(String title);
    }

    public class Tree {
        private final List<Map<String, String>> node;
        private final Comparator<String> sortByType = (a, b) -> {
            if (isDirectory(a) && !isDirectory(b)) return -1;
            if (!isDirectory(a) && isDirectory(b)) return 1;
            return a.toLowerCase().compareTo(b.toLowerCase());
        };

        @SuppressLint("SuspiciousIndentation")
        public Tree(HashMap<String, ClassDef> classMap) {
            if (path == null) {
                path = new Stack<>();
                dep = 0;
            }
            Set<String> names = classMap.keySet();
            node = new ArrayList<>();

            for (String name : names) {
                String[] token = name.split("/");
                String tmp = "";
                for (int i = 0; i < token.length; i++) {
                    String value = token[i];
                    if (i >= node.size()) node.add(new HashMap<>());
                    Map<String, String> map = node.get(i);
                    if (classMap.containsKey(tmp + value) && i + 1 == token.length)
                        map.put(tmp + value, tmp);
                    else
                        map.put(tmp + value + "/", tmp);
                    tmp += value + "/";
                }
            }
        }

        public ArrayList<String> list(String parent) {
            ArrayList<String> str = new ArrayList<>();
            while (dep >= 0 && node.size() > 0) {
                Map<String, String> map = node.get(dep);
                if (map != null) {
                    for (String key : map.keySet()) {
                        if (parent.equals(map.get(key))) {
                            int index = key.endsWith("/") ? key.lastIndexOf("/", key.length() - 2) : key.lastIndexOf("/");
                            str.add(index != -1 ? key.substring(index + 1) : key);
                        }
                    }
                    break;
                }
                pop();
            }
            Collections.sort(str, sortByType);
            return str;
        }

        public ArrayList<String> list() {
            return list(getCurPath());
        }

        private void push(String name) {
            dep++;
            path.push(name);
        }

        private String pop() {
            if (dep > 0) {
                dep--;
                return path.pop();
            }
            return null;
        }

        public String getCurPath() {
            return join(path, "/");
        }

        public boolean isDirectory(String name) {
            return name.endsWith("/");
        }

        private String join(Stack<String> stack, String d) {
            StringBuilder sb = new StringBuilder();
            for (String s : stack) sb.append(s);
            return sb.toString();
        }
    }

    // helper classe for debug info Striping
    private class DebugInfoStripper implements ClassDef {
        private final ClassDef delegate;
        private final CompilationOptions options;

        public DebugInfoStripper(ClassDef delegate, CompilationOptions options) {
            this.delegate = delegate;
            this.options = options;
        }

        @Override @Nonnull public String getType() { return delegate.getType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override @Nullable public String getSuperclass() { return delegate.getSuperclass(); }
        @Override @Nonnull public List<String> getInterfaces() { return delegate.getInterfaces(); }
        @Override @Nullable public String getSourceFile() { return options.removeDebugSource || options.removeAllDebug ? null : delegate.getSourceFile(); }
        @Override @Nonnull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @Nonnull public Iterable<? extends Field> getStaticFields() { return delegate.getStaticFields(); }
        @Override @Nonnull public Iterable<? extends Field> getInstanceFields() { return delegate.getInstanceFields(); }
        @Override @Nonnull public Iterable<? extends Field> getFields() { return delegate.getFields(); }

        @Override @Nonnull public Iterable<? extends Method> getDirectMethods() {
            return wrapMethods(delegate.getDirectMethods());
        }

        @Override @Nonnull public Iterable<? extends Method> getVirtualMethods() {
            return wrapMethods(delegate.getVirtualMethods());
        }

        @Override @Nonnull public Iterable<? extends Method> getMethods() {
            return wrapMethods(delegate.getMethods());
        }

        @Override public int compareTo(@Nonnull CharSequence o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
        @Override public int length() { return delegate.length(); }
        @Override public char charAt(int index) { return delegate.charAt(index); }
        @NonNull
        @Override public CharSequence subSequence(int start, int end) { return delegate.subSequence(start, end); }
        @Override @Nonnull public String toString() { return delegate.toString(); }

        private Iterable<? extends Method> wrapMethods(Iterable<? extends Method> methods) {
            List<Method> wrapped = new ArrayList<>();
            for (Method method : methods) {
                wrapped.add(new MethodStripper(method, options));
            }
            return wrapped;
        }
    }

    // Method stripper
    private class MethodStripper implements Method {
        private final Method delegate;
        private final CompilationOptions options;

        public MethodStripper(Method delegate, CompilationOptions options) {
            this.delegate = delegate;
            this.options = options;
        }

        @Override @Nonnull public String getDefiningClass() { return delegate.getDefiningClass(); }
        @Override @Nonnull public String getName() { return delegate.getName(); }
        @Override @Nonnull public List<? extends MethodParameter> getParameters() {
            if (options.removeDebugParam || options.removeAllDebug) {
                List<MethodParameter> params = new ArrayList<>();
                for (MethodParameter p : delegate.getParameters()) {
                    params.add(new ImmutableMethodParameter(p.getType(), null, null));
                }
                return params;
            }
            return delegate.getParameters();
        }
        @Override @Nonnull public List<? extends CharSequence> getParameterTypes() { return delegate.getParameterTypes(); }
        @Override @Nonnull public String getReturnType() { return delegate.getReturnType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override @Nonnull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @Nonnull public Set<HiddenApiRestriction> getHiddenApiRestrictions() { return delegate.getHiddenApiRestrictions(); }
        @Override @Nullable public MethodImplementation getImplementation() {
            MethodImplementation impl = delegate.getImplementation();
            if (impl == null) return null;
            return new MethodImplementationStripper(impl, options);
        }
        @Override public int compareTo(@Nonnull MethodReference o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
    }

    // method implemention stripper
    private static class MethodImplementationStripper implements MethodImplementation {
        private final MethodImplementation delegate;
        private final CompilationOptions options;

        public MethodImplementationStripper(MethodImplementation delegate, CompilationOptions options) {
            this.delegate = delegate;
            this.options = options;
        }

        @Override public int getRegisterCount() { return delegate.getRegisterCount(); }
        @NonNull
        @Override public Iterable<? extends Instruction> getInstructions() { return delegate.getInstructions(); }
        @NonNull
        @Override public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks() { return delegate.getTryBlocks(); }

        @NonNull
        @Override public Iterable<? extends DebugItem> getDebugItems() {
            if (options.removeAllDebug) return new ArrayList<>();
            List<DebugItem> filtered = new ArrayList<>();
            for (DebugItem item : delegate.getDebugItems()) {
                boolean remove = false;
                switch (item.getDebugItemType()) {
                    case DebugItemType.SET_SOURCE_FILE:
                        if (options.removeDebugSource) remove = true;
                        break;
                    case DebugItemType.LINE_NUMBER:
                        if (options.removeDebugLine) remove = true;
                        break;
                    case DebugItemType.PROLOGUE_END:
                        if (options.removeDebugPrologue) remove = true;
                        break;
                    case DebugItemType.EPILOGUE_BEGIN:
                        // Prologue option usually covers epilogue too in some tools, or we can map it
                        if (options.removeDebugPrologue) remove = true;
                        break;
                    case DebugItemType.START_LOCAL:
                    case DebugItemType.END_LOCAL:
                    case DebugItemType.RESTART_LOCAL:
                    case DebugItemType.START_LOCAL_EXTENDED:
                        if (options.removeDebugLocal) remove = true;
                        break;
                }
                if (!remove) filtered.add(item);
            }
            // Param debug info is usually handled via method parameters if we want to strip names,
            // but in debug_info_item they are also present.
            // dexlib2 doesn't easily expose the parameter names list from debug_info_item here.
            return filtered;
        }
    }

}
