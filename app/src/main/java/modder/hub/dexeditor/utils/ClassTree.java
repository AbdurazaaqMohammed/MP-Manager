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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import modder.hub.dexeditor.activity.DexEditorActivity;
import modder.hub.dexeditor.model.TreeNode;
import modder.hub.dexeditor.smali.SharedSmaliUtils;
import modder.hub.dexeditor.smali.SmaliHelper;

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
    // Concurrent so the hot edit path (saveClassDef / saveAllDexFiles) needs no monitor, and so a
    // background auto-save pass can read them while the user keeps editing.
    private final Map<String, HashSet<String>> editedClassMap = new ConcurrentHashMap<>();
    private final Map<String, String> pendingSmaliMap = new ConcurrentHashMap<>();
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
    byte[] input;

    private Map<String, HashSet<String>> deletedClassJson = new ConcurrentHashMap<>();
    private final Map<String, String> typeToDexMap = new HashMap<>();
    private final Map<String, DexBackedDexFile> dexFileByName = new HashMap<>();
    // Concurrent so the hot edit path (saveClassDef / saveAllDexFiles) needs no monitor.
    private final ConcurrentMap<String, Integer> classDefIndex = new ConcurrentHashMap<>(1 << 14);

    // =====================================================================
    // Background auto-save (pipelined dex compilation)
    //
    // Every modification bumps the touched dex's generation (before any state is mutated,
    // under editLock). A background pass compiles dirty dexes into session-scoped autosave
    // files; the foreground save applies any autosave whose generation still matches - a file
    // copy instead of a multi-second recompile - and compiles the rest exactly as before.
    //
    // A generation mismatch means the dex changed while that autosave was being produced, so
    // the autosave is discarded and the dex recompiled: an edit made during a background save
    // can never be silently lost. Autosave files live under the session workDir, which is
    // deleted when the editor exits, so nothing accumulates on the user's storage and an
    // exit-without-saving leaves the original dexes untouched.
    // =====================================================================

    /**
     * Orders state mutations (bump generation, update pendingSmaliMap / classMap / classDefList)
     * against a compiling pass's generation snapshot. Writers hold it briefly; a compiling pass
     * takes its snapshot under it and then works lock-free, re-checking the generation before
     * using its result.
     */
    private final Object editLock = new Object();

    /** Only one save pass (background or foreground) may run at a time. */
    private final Object saveCoordinator = new Object();

    /** Set while the user is waiting on the real save; a background pass yields between dexes. */
    private volatile boolean foregroundWaiting = false;

    /** Current generation of each dex file; bumped (under editLock) before any state mutation. */
    private final ConcurrentHashMap<String, AtomicLong> dexGenerations = new ConcurrentHashMap<>();

    /** Generation of each dex as last persisted - to the real file (foreground) or an autosave. */
    private final ConcurrentHashMap<String, Long> persistedGenerations = new ConcurrentHashMap<>();

    /** A completed background compilation of one dex, usable by the foreground save if still current. */
    private static final class AutosavedDex {
        final long generation;
        final File file;
        AutosavedDex(long generation, File file) {
            this.generation = generation;
            this.file = file;
        }
    }

    private final ConcurrentHashMap<String, AutosavedDex> autosavedDexes = new ConcurrentHashMap<>();

    private volatile boolean autoSaveEnabled = false;

    /** Debounce: coalesces bursts of edits (typing, batch replace) into one background pass. */
    private static final long AUTO_SAVE_DEBOUNCE_MS = 1500;

    private final ScheduledExecutorService autoSaveExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dex-autosave");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean autoSaveScheduled = new AtomicBoolean(false);


    /**
     * Byte-budgeted LRU cache of disassembled classes.
     *
     * <p>The previous implementation was a {@link ConcurrentHashMap} that called {@code clear()}
     * the moment it reached {@code SMALI_CACHE_MAX} entries. That is the worst possible policy for
     * a full-dex search or browse: after 256 classes it throws away <em>everything</em>, including
     * the entries it is actively re-reading, so classes get disassembled over and over. An LRU keeps
     * the hot working set instead of flushing it wholesale.
     *
     * <p>The budget is measured in characters (the real cost of a cached smali text on ART, where a
     * char is two bytes) rather than entries, so a device with a large heap can afford to keep the
     * disassembly of a whole dex resident - which makes a repeat search a pure scan - while a small
     * heap degrades gracefully to the hottest subset instead of OOM-ing. Evicting or clearing only
     * changes speed, never results.
     *
     * <p>Thread-safe: search fans out over a pool of workers that all read through this cache.
     */
    private long smaliCacheChars = 0;

    private final Map<String, String> pureSmaliCache =
            Collections.synchronizedMap(new LinkedHashMap<String, String>(256, 0.75f, true) {
                private void addCost(String key, String value, int sign) {
                    smaliCacheChars += sign * (key.length() + value.length() + 64L);
                }

                @Override
                public String put(String key, String value) {
                    String prev = super.put(key, value);
                    if (prev == null) {
                        addCost(key, value, 1);
                    } else {
                        smaliCacheChars += value.length() - prev.length();
                    }
                    return prev;
                }

                @Override
                public String remove(Object key) {
                    String prev = super.remove(key);
                    if (prev != null && key instanceof String) {
                        addCost((String) key, prev, -1);
                    }
                    return prev;
                }

                @Override
                public void clear() {
                    super.clear();
                    smaliCacheChars = 0;
                }

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    if (smaliCacheChars > smaliCacheBudget()) {
                        smaliCacheChars -= eldest.getKey().length() + eldest.getValue().length() + 64L;
                        return true;
                    }
                    return false;
                }
            });

    /**
     * Character budget for {@link #pureSmaliCache}. Scales with the heap (the dex editor runs with
     * {@code largeHeap}), leaving the majority of the heap for the dex buffers, class definitions
     * and open editors.
     */
    private static long smaliCacheBudget() {
        long maxBytes = Runtime.getRuntime().maxMemory();
        long budgetBytes = Math.min(256L * 1024 * 1024, Math.max(8L * 1024 * 1024, maxBytes / 3));
        return budgetBytes / 2; // one char of smali text costs 2 bytes on ART
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

    private volatile CompilationOptions compilationOptions = new CompilationOptions();

    public void setCompilationOptions(CompilationOptions options) {
        CompilationOptions old = this.compilationOptions;
        this.compilationOptions = options;
        if (optionsChanged(old, options)) {
            // The bytes a save would produce just changed for every dex, not just edited ones
            // (e.g. removing debug info rewrites every class), so every dex becomes dirty.
            synchronized (editLock) {
                for (String fileName : dexClassMap.keySet()) {
                    dexGen(fileName).incrementAndGet();
                }
            }
            scheduleAutoSave();
        }
    }

    private static boolean optionsChanged(CompilationOptions a, CompilationOptions b) {
        return a == null || b == null
                || a.removeAllDebug != b.removeAllDebug
                || a.removeDebugSource != b.removeDebugSource
                || a.removeDebugLine != b.removeDebugLine
                || a.removeDebugParam != b.removeDebugParam
                || a.removeDebugPrologue != b.removeDebugPrologue
                || a.removeDebugLocal != b.removeDebugLocal
                || !a.dexVersion.equals(b.dexVersion);
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

        synchronized (editLock) {
            removeClassesLocked(classNames);
        }
        scheduleAutoSave();
    }

    private void removeClassesLocked(List<String> classNames) {

        List<String> folderPrefixes = new ArrayList<>();
        Set<String> individualClasses = new HashSet<>();

        for (String name : classNames) {
            if (name.endsWith("/")) {
                folderPrefixes.add(name);
            } else {
                individualClasses.add(name);
            }
        }
        // Go through pureSmaliCache.remove (not keySet().removeAll/removeIf) so the cache's
        // byte bookkeeping sees every eviction; the keySet views bypass the overridden remove.
        for (String name : individualClasses) {
            pureSmaliCache.remove(name);
        }
        for (String prefix : folderPrefixes) {
            for (String key : new ArrayList<>(pureSmaliCache.keySet())) {
                if (key.startsWith(prefix)) {
                    pureSmaliCache.remove(key);
                }
            }
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
            dexGen(fileName).incrementAndGet();
            HashSet<String> deleted = deletedClassJson.get(fileName);
            if (deleted == null) {
                deleted = new HashSet<>();
                deletedClassJson.put(fileName, deleted);
            }
            synchronized (deleted) {
                deleted.add(type);
            }
        }
    }

    // save smali according to the class and its content
    // it's just a mapping way to preserve  smali data in memeory, but it's not a good way it may cause self kill app like situation and mt manager never do like this
    public void saveSmali(String type, String smali) {
        synchronized (editLock) {
            // The generation is bumped before the mutation: a compiling pass that snapshot the
            // old generation is guaranteed to see the bump and discard its result.
            bumpDexForType(type);
            pendingSmaliMap.put(type, smali);
            pureSmaliCache.remove(type);
            recordEditedClass(type);
        }
        DexEditorActivity.isChanged = true;
        DexEditorActivity.isSaved = false;
    }

    // save class def to the main node o the dex and record the changes classes
    public void saveClassDef(ClassDef classDef) {
        String type = classDef.getType().substring(1, classDef.getType().length() - 1);
        synchronized (editLock) {
            // Bump before mutating, so a pass that is compiling this dex from an earlier
            // generation will discard its (now stale) result instead of overwriting this save.
            bumpDexForType(type);
            pendingSmaliMap.remove(type);
            pureSmaliCache.remove(type);

            classMap.put(type, classDef);

            String rawType = "L" + type + ";";
            Integer index = classDefIndex.get(rawType);
            if (index != null && index >= 0 && index < classDefList.size()) {
                classDefList.set(index, classDef);
            } else {
                synchronized (classDefList) {
                    boolean found = false;
                    for (int i = 0; i < classDefList.size(); i++) {
                        ClassDef existingDef = classDefList.get(i);
                        String existingType = existingDef.getType();
                        if (existingType.equals(rawType)) {
                            classDefList.set(i, classDef);
                            classDefIndex.put(rawType, i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        classDefIndex.put(rawType, classDefList.size());
                        classDefList.add(classDef);
                    }
                }
            }

            recordEditedClass(type);
        }
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
        return cached.equals(SmaliHelper.stripSmaliHeader(smaliWithHeader));
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
        if (smali != null) {
            smali = SmaliHelper.stripSmaliHeader(smali);
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
        String rawType = "L" + type + ";";
        String fileName = typeToDexMap.get(rawType);
        if (fileName == null) {
            if (!dexClassMap.isEmpty()) {
                fileName = dexClassMap.keySet().iterator().next();
            } else {
                fileName = "classes.dex";
            }
            typeToDexMap.put(rawType, fileName);
            List<String> list = dexClassMap.get(fileName);
            if (list == null) {
                list = new ArrayList<>();
                dexClassMap.put(fileName, list);
            }
            if (!list.contains(rawType)) {
                list.add(rawType);
            }
        }
        HashSet<String> edited = editedClassMap.get(fileName);
        if (edited == null) {
            edited = new HashSet<>();
            editedClassMap.put(fileName, edited);
        }
        edited.add(type);
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
    /** Raised when the user cancelled a save; never surfaced as a build failure. */
    public static class SaveCancelledException extends RuntimeException {
        public SaveCancelledException() {
            super("CANCELLED");
        }
    }

    /**
     * Keeps the original throwable as the cause. Wrapping an {@link Error}
     * (OutOfMemoryError, StackOverflowError) into a plain Exception is what let
     * those escape the save thread and take the process down.
     */
    private static Exception asException(Throwable t) {
        if (t instanceof Exception) return (Exception) t;
        return new Exception(t);
    }

    @SuppressLint("SdCardPath")
    public void saveAllDexFiles(DexSaveProgress dexSaveProgress) throws Exception {
        if (dexClassMap == null || dexClassMap.isEmpty()) return;
        // Ask any running background pass to yield after the dex it is currently writing, then
        // take the coordinator so only one pass is ever saving at a time.
        foregroundWaiting = true;
        try {
            dexSaveProgress.onMessage("Finishing background save...");
            synchronized (saveCoordinator) {
                saveAllDexFilesLocked(dexSaveProgress, false);
            }
        } finally {
            foregroundWaiting = false;
        }
    }

    /** Progress listener for background passes: no UI, cannot cancel. */
    private static final DexSaveProgress SILENT_PROGRESS = new DexSaveProgress() {
        @Override public void onProgress(int progress, int total) { }
        @Override public void onMessage(String name) { }
        @Override public void onTitle(String title) { }
    };

    /** Runs one background pass: compiles dirty dexes into autosave files. Never touches UI state. */
    private void runAutoSavePass() {
        if (!autoSaveEnabled || foregroundWaiting) return;
        try {
            synchronized (saveCoordinator) {
                if (!autoSaveEnabled || foregroundWaiting) return;
                saveAllDexFilesLocked(SILENT_PROGRESS, true);
            }
        } catch (Throwable t) {
            // A failed background pass costs nothing: the foreground save recompiles the dex.
            t.printStackTrace();
        }
    }

    private AtomicLong dexGen(String fileName) {
        return dexGenerations.computeIfAbsent(fileName, k -> new AtomicLong());
    }

    /** Bumps the generation of the dex owning {@code type} and schedules a background save. */
    private void bumpDexForType(String type) {
        String fileName = typeToDexMap.get("L" + type + ";");
        if (fileName != null) {
            dexGen(fileName).incrementAndGet();
            scheduleAutoSave();
        }
    }

    /** Enables or disables pipelined background compilation. Enabling catches up immediately. */
    public void setAutoSaveEnabled(boolean enabled) {
        autoSaveEnabled = enabled;
        if (enabled) scheduleAutoSave(0L);
    }

    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }

    /** Debounced request for a background pass; bursts of edits coalesce into one pass. */
    public void scheduleAutoSave() {
        scheduleAutoSave(AUTO_SAVE_DEBOUNCE_MS);
    }

    private void scheduleAutoSave(long delayMs) {
        if (!autoSaveEnabled) return;
        if (autoSaveScheduled.getAndSet(true)) return;
        try {
            autoSaveExecutor.schedule(() -> {
                autoSaveScheduled.set(false);
                runAutoSavePass();
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            autoSaveScheduled.set(false);
        }
    }

    private File autoSaveDir() {
        File dir = new File(workDir, "autosave");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void saveAllDexFilesLocked(DexSaveProgress dexSaveProgress, boolean background) throws Exception {
        final long saveStart = System.nanoTime();
        long compileNanos = 0L;
        long writeFileNanos = 0L;

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

            // A background pass yields as soon as the user asks for the real save.
            if (background && foregroundWaiting) break;

            long gen = dexGen(fileName).get();
            // persistedGenerations tracks what is in the REAL dex file; an autosave file is
            // only a candidate the foreground save applies - it never counts as persisted.
            long realGen = persistedGenerations.getOrDefault(fileName, 0L);

            if (background) {
                AutosavedDex savedSoFar = autosavedDexes.get(fileName);
                long savedGen = savedSoFar != null ? savedSoFar.generation : 0L;
                if (gen <= savedGen || gen <= realGen) {
                    // Already compiled to an autosave, or already in the real file.
                    current++;
                    continue;
                }
            } else {
                if (gen <= realGen) {
                    current++;
                    continue;
                }
                dexSaveProgress.onTitle(fileName + " (" + current + "/" + total + ")");

                // Fast path: this exact state was already compiled in the background - apply
                // it with a file copy instead of recompiling for seconds.
                AutosavedDex saved = autosavedDexes.get(fileName);
                if (saved != null && saved.generation == dexGen(fileName).get()) {
                    boolean applied = false;
                    try {
                        applyAutosavedDex(fileName, saved, dexSaveProgress);
                        applied = true;
                    } catch (SaveCancelledException cancel) {
                        throw cancel;
                    } catch (Exception applyFailed) {
                        // The autosave file is dispensable; fall through to a real compile.
                        applyFailed.printStackTrace();
                        autosavedDexes.remove(fileName);
                    }
                    if (applied && dexGen(fileName).get() != saved.generation) {
                        // An edit landing mid-apply invalidates the applied bytes: recompile.
                        compileAndWriteDex(fileName, entry.getValue(), dexSaveProgress, false,
                                dexGen(fileName).get(), executor, numThreads, opcodes,
                                finalTargetDexVersion, forceCompileAll);
                    } else if (!applied) {
                        compileAndWriteDex(fileName, entry.getValue(), dexSaveProgress, false,
                                dexGen(fileName).get(), executor, numThreads, opcodes,
                                finalTargetDexVersion, forceCompileAll);
                    }
                    current++;
                    continue;
                }
            }

            compileAndWriteDex(fileName, entry.getValue(), dexSaveProgress, background,
                    gen, executor, numThreads, opcodes, finalTargetDexVersion, forceCompileAll);
            if (!background && dexGen(fileName).get() != gen) {
                // An edit landed on this dex while it was being compiled: redo it once so the
                // bytes on disk are guaranteed to include everything the user changed.
                compileAndWriteDex(fileName, entry.getValue(), dexSaveProgress, false,
                        dexGen(fileName).get(), executor, numThreads, opcodes,
                        finalTargetDexVersion, forceCompileAll);
            }
            current++;
            }
        } finally {
            executor.shutdownNow();
        }

        if (!background) {
            // The dexes on disk now already contain every pending edit and deletion, so stop
            // reporting those dex files as "touched". Without this, every subsequent save would
            // recompile each dex all over again even when nothing changed since the last save.
            editedClassMap.clear();

            DexEditorActivity.isChanged = false;
            DexEditorActivity.isSaved = true;
        }

        dexSaveProgress.onFinished((System.nanoTime() - saveStart) / 1_000_000L,
                compileNanos / 1_000_000L, writeFileNanos / 1_000_000L);
    }

    /**
     * Applies a completed background compilation to the real dex file: backup dance, byte write,
     * rollback on failure - the same commit sequence a foreground compile performs.
     */
    private void applyAutosavedDex(String fileName, AutosavedDex saved, DexSaveProgress dexSaveProgress) throws Exception {
        dexSaveProgress.onMessage("Applying saved " + fileName + "...");
        byte[] bytes = read(saved.file.getAbsolutePath());
        commitDexBytes(fileName, bytes, bytes.length);
        persistedGenerations.put(fileName, saved.generation);
        autosavedDexes.remove(fileName);
        try {
            saved.file.delete();
        } catch (Exception ignored) {
        }
    }

    /**
     * The commit sequence shared by the compile and apply paths: back the current file up once
     * ({@code .bak}), write the new bytes, and restore the backup if the write fails partway.
     */
    @SuppressLint("SdCardPath")
    private void commitDexBytes(String fileName, byte[] buf, int size) throws Exception {
        boolean committed = false;
        File backupOfThisDex = null;
        File outFile = null;
        try {
            String outputDir = paths != null && !paths.isEmpty() && paths.get(0) != null
                    ? new File(paths.get(0)).getParent()
                    : "/sdcard";
            outFile = new File(outputDir, fileName);
            File bakFile = new File(outFile.getAbsolutePath() + ".bak");
            backupOfThisDex = bakFile.exists() ? null : bakFile;

            if (outFile.exists() && !bakFile.exists()) {
                // Instant backup: rename the inode instead of byte-copying the whole
                // file and then deleting the original. Same end state (previous bytes
                // at .bak, new bytes at outFile) for ~free in the same directory.
                // Falls back to the old copy+delete when rename is unsupported.
                if (bakFile.exists()) bakFile.delete();
                if (!outFile.renameTo(bakFile)) {
                    FileUtil.copyFile(outFile.getAbsolutePath(), bakFile.getAbsolutePath());
                    outFile.delete();
                }
            }

            try (FileOutputStream out = new FileOutputStream(outFile)) {
                out.write(buf, 0, size);
                out.flush();
            }
            // Nothing may throw after this point: the dex on disk is now the new one.
            committed = true;
        } catch (final Throwable e) {
            e.printStackTrace();
            if (e instanceof Exception) throw (Exception) e;
            throw new Exception(e);
        } finally {
            if (!committed && backupOfThisDex != null && backupOfThisDex.exists() && outFile != null) {
                // The old bytes were renamed aside before writing, so a failure here would
                // otherwise leave a truncated or missing dex. Put the backup back.
                try {
                    if (outFile.exists() && !outFile.delete()) outFile.deleteOnExit();
                    if (!backupOfThisDex.renameTo(outFile)) {
                        FileUtil.copyFile(backupOfThisDex.getAbsolutePath(), outFile.getAbsolutePath());
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Compiles one dex and writes it - to the real file (foreground) or to a session autosave
     * file (background). The compile body is unchanged from the original saveAllDexFiles loop;
     * only the write target and the post-processing guard are new.
     *
     * @param genSnapshot the dex's generation when this pass decided to save it
     */
    private void compileAndWriteDex(String fileName, List<String> classNames,
                                    DexSaveProgress dexSaveProgress, boolean background,
                                    long genSnapshot, ExecutorService executor, int numThreads,
                                    Opcodes opcodes, int finalTargetDexVersion,
                                    boolean forceCompileAll) throws Exception {
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
                        if (forceCompileAll) {
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
                    synchronized (threadException) {
                        if (threadException[0] == null) threadException[0] = asException(t);
                    }
                }
                return null;
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                // A cancelled save is not a compile failure; record it as such
                // so the caller can report it without an error dialog.
                Thread.currentThread().interrupt();
                synchronized (threadException) {
                    if (threadException[0] == null) threadException[0] = new SaveCancelledException();
                }
                break;
            } catch (Exception e) {
                Throwable cause = e;
                if (e instanceof ExecutionException && e.getCause() != null) {
                    cause = e.getCause();
                }
                synchronized (threadException) {
                    if (threadException[0] == null) threadException[0] = asException(cause);
                }
                break;
            }
            if (threadException[0] != null) break;
        }
        if (threadException[0] != null) {
            throw threadException[0];
        }

        // Post the assembled definitions back into the live state. A background pass must not
        // post if the dex changed while it was compiling - that would overwrite a newer in-memory
        // edit with the older bytes it compiled from. The generation check under editLock makes
        // "still current" and "post" atomic with respect to new edits. A foreground pass posts
        // unconditionally, exactly as it always did.
        if (!background || dexGen(fileName).get() == genSnapshot) {
            synchronized (editLock) {
                if (background && dexGen(fileName).get() != genSnapshot) {
                    // Lost the race between the check and the lock: leave the state alone.
                } else if (!assembledDefs.isEmpty()) {
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
                            pureSmaliCache.put(typeKey, SmaliHelper.stripSmaliHeader(pending));
                        }
                    }
                }
            }
        }

        dexSaveProgress.onMessage("Writing file...");

        // Size the scratch buffer from the original dex when we know it, and otherwise
        // from a per-class estimate. MemoryDataStore grows by copying in fixed steps, so
        // starting too small means repeatedly copying the whole buffer. The 512 B/class
        // guess badly under-shoots for any dex with real code.
        DexBackedDexFile original = dexFileByName.get(fileName);
        int estimate = original != null && original.getFileSize() > 0
                ? (int) Math.min(Integer.MAX_VALUE - 64L, (long) original.getFileSize() * 12L / 10L + (1 << 20))
                : classCount * 1024;
        MemoryDataStore memoryDataStore = new MemoryDataStore(Math.max(1 << 16, estimate));
        byte[] buf;
        int size;
        try {
            // Write the live buffer slice directly. Anything that copies it
            // first (a byte[] clone, a buffered stream, a channel transfer)
            // only pays for a second full-dex memory pass before the bytes
            // even reach the disk.
            dexBuilder.writeTo(memoryDataStore);
            buf = memoryDataStore.getBuffer();
            size = memoryDataStore.getSize();
        } catch (OutOfMemoryError tooBig) {
            // The preallocated buffer is one contiguous array sized from the
            // original dex, which a low-RAM device cannot always hold. Drop
            // it before retrying so the collector can actually reclaim it,
            // then let the store grow in fixed steps instead of failing the
            // whole save.
            memoryDataStore = null;
            System.gc();
            memoryDataStore = new MemoryDataStore(1 << 16);
            dexBuilder.writeTo(memoryDataStore);
            buf = memoryDataStore.getBuffer();
            size = memoryDataStore.getSize();
        }

        if (background) {
            File target = new File(autoSaveDir(), fileName);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(buf, 0, size);
                out.flush();
            }
            if (dexGen(fileName).get() == genSnapshot) {
                autosavedDexes.put(fileName, new AutosavedDex(genSnapshot, target));
            } else {
                // Compiled from a state the user has already moved past: keep it out of the way.
                target.delete();
            }
            return;
        }

        commitDexBytes(fileName, buf, size);
        persistedGenerations.put(fileName, dexGen(fileName).get());
        autosavedDexes.remove(fileName);
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
        try {
            autoSaveExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
        foregroundWaiting = false;
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
        // Pending text and dirty flags outlive a session otherwise, so the next
        // load would re-apply edits from the previous one on save.
        pendingSmaliMap.clear();
        editedClassMap.clear();
        deletedClassJson.clear();
        classDefList.clear();
        
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

        /**
         * Called once, on the saving thread, after every dex file has been written.
         *
         * @param totalMs     wall-clock time of the whole save/compile/write run
         * @param compileMs   time spent assembling and interning classes
         * @param writeFileMs time spent serializing dexes and writing them to disk
         */
        default void onFinished(long totalMs, long compileMs, long writeFileMs) {
        }
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