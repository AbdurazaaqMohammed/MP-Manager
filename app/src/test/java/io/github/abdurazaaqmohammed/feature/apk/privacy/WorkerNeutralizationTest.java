package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests verifying Task 4: WorkManager Telemetry Worker Bytecode Nullification (Module A).
 */
public class WorkerNeutralizationTest {

    private static final String ANALYTICS_WORKER_SMALI =
            """
            .class public Lcom/example/AnalyticsWorker;
            .super Landroidx/work/Worker;
            .source "AnalyticsWorker.java"

            .method public constructor <init>(Landroid/content/Context;Landroidx/work/WorkerParameters;)V
                .locals 2
                invoke-direct {p0, p1, p2}, Landroidx/work/Worker;-><init>(Landroid/content/Context;Landroidx/work/WorkerParameters;)V
                const-string v0, "InitTag"
                const-string v1, "Analytics init"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 3
                const-string v0, "AnalyticsWorker"
                const-string v1, "Flushing telemetry buffer..."
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->retry()Landroidx/work/ListenableWorker$Result;
                move-result-object v2
                return-object v2
            .end method

            .method public flushEvents()V
                .locals 2
                const-string v0, "https://app-measurement.com/a"
                const-string v1, "Sending events"
                invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method
            """;

    private static final String DEAD_WORKER_SMALI =
            """
            .class public Lcom/example/DeadTelemetryWorker;
            .super Landroidx/work/Worker;
            .source "DeadTelemetryWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->failure()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    private static final String NORMAL_WORKER_SMALI =
            """
            .class public Lcom/example/DatabaseSyncWorker;
            .super Landroidx/work/Worker;
            .source "DatabaseSyncWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->retry()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    private static final String CUJO_WORKER_SMALI =
            """
            .class public Lcom/xfinity/cujo/CujoWorker;
            .super Landroidx/work/ListenableWorker;
            .source "CujoWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/ListenableWorker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->retry()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    private static final String OTM_WORKER_SMALI =
            """
            .class public Lcom/xfinity/otm/OtmWorker;
            .super Landroidx/work/Worker;
            .source "OtmWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->retry()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    private static final String CALLER_SMALI =
            """
            .class public Lcom/example/Caller;
            .super Ljava/lang/Object;
            .source "Caller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static schedule()V
                .locals 1
                const-class v0, Lcom/example/AnalyticsWorker;
                return-void
            .end method
            """;

    private static final String NATIVE_WORKER_SMALI =
            """
            .class public Lcom/example/NativeTelemetryWorker;
            .super Landroidx/work/Worker;
            .source "NativeTelemetryWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public native doWork()Landroidx/work/ListenableWorker$Result;
            .end method
            """;

    private static final String ABSTRACT_WORKER_SMALI =
            """
            .class public abstract Lcom/example/AbstractTelemetryWorker;
            .super Landroidx/work/Worker;
            .source "AbstractTelemetryWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public abstract doWork()Landroidx/work/ListenableWorker$Result;
            .end method
            """;

    private File assembleSmali(String... smaliFiles) throws Exception {
        File dir = Files.createTempDirectory("worker_test").toFile();
        int idx = 0;
        for (String smali : smaliFiles) {
            File smaliFile = new File(dir, "TestClass" + (idx++) + ".smali");
            try (FileWriter w = new FileWriter(smaliFile)) {
                w.write(smali);
            }
        }
        File outDex = new File(dir, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue(Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    private ClassDef findClass(DexBackedDexFile dex, String descriptor) {
        for (ClassDef cd : dex.getClasses()) {
            if (descriptor.equals(cd.getType())) {
                return cd;
            }
        }
        return null;
    }

    @Test
    public void testActiveWorkerNeutralization() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef workerDef = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(workerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(workerDef, options, stats);

        // Verify stats
        assertEquals(1, stats.backgroundWorkersNeutralized);
        assertTrue(stats.classesModified > 0);

        // Find doWork() in sanitized class
        Method doWorkMethod = null;
        for (Method m : sanitized.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                doWorkMethod = m;
                break;
            }
        }
        assertNotNull(doWorkMethod);

        // Verify implementation has registers >= 1
        assertNotNull(doWorkMethod.getImplementation());
        assertTrue(doWorkMethod.getImplementation().getRegisterCount() >= 1);

        // Verify exact instructions: invoke-static success() -> move-result-object v0 -> return-object v0
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction inst : doWorkMethod.getImplementation().getInstructions()) {
            instructions.add(inst);
        }
        assertEquals(3, instructions.size());

        // Instruction 0: invoke-static {} Landroidx/work/ListenableWorker$Result;->success()Landroidx/work/ListenableWorker$Result;
        assertEquals(Opcode.INVOKE_STATIC, instructions.get(0).getOpcode());
        assertTrue(instructions.get(0) instanceof ReferenceInstruction);
        Object ref = ((ReferenceInstruction) instructions.get(0)).getReference();
        assertTrue(ref instanceof MethodReference);
        MethodReference mr = (MethodReference) ref;
        assertEquals("Landroidx/work/ListenableWorker$Result;", mr.getDefiningClass());
        assertEquals("success", mr.getName());
        assertEquals("Landroidx/work/ListenableWorker$Result;", mr.getReturnType());

        // Instruction 1: move-result-object v0
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions.get(1).getOpcode());
        assertTrue(instructions.get(1) instanceof OneRegisterInstruction);
        assertEquals(0, ((OneRegisterInstruction) instructions.get(1)).getRegisterA());

        // Instruction 2: return-object v0
        assertEquals(Opcode.RETURN_OBJECT, instructions.get(2).getOpcode());
        assertTrue(instructions.get(2) instanceof OneRegisterInstruction);
        assertEquals(0, ((OneRegisterInstruction) instructions.get(2)).getRegisterA());
    }

    @Test
    public void testDeadWorkerSkippedWithReachabilityIndex() throws Exception {
        File dexFile = assembleSmali(DEAD_WORKER_SMALI, CALLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        DexReachabilityIndex reachabilityIndex = DexReachabilityIndex.buildFromDexFiles(dex);

        ClassDef deadWorker = findClass(dex, "Lcom/example/DeadTelemetryWorker;");
        assertNotNull(deadWorker);
        assertFalse(reachabilityIndex.isClassReferenced(deadWorker.getType()));

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(deadWorker, options, stats, reachabilityIndex);

        // Verify deadCodeSkipped incremented and backgroundWorkersNeutralized is 0
        assertEquals(1, stats.deadCodeSkipped);
        assertEquals(0, stats.backgroundWorkersNeutralized);

        // Verify doWork() was NOT rewritten
        Method doWorkMethod = null;
        for (Method m : sanitized.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                doWorkMethod = m;
                break;
            }
        }
        assertNotNull(doWorkMethod);
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction inst : doWorkMethod.getImplementation().getInstructions()) {
            instructions.add(inst);
        }
        // Original has failure(), not success()
        boolean hasSuccessCall = false;
        for (Instruction inst : instructions) {
            if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference && "success".equals(((MethodReference) ref).getName())) {
                    hasSuccessCall = true;
                }
            }
        }
        assertFalse("Dead worker doWork() should not have been mutated to success()", hasSuccessCall);
    }

    @Test
    public void testActiveWorkerReferencedWithReachabilityIndex() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI, CALLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        DexReachabilityIndex reachabilityIndex = DexReachabilityIndex.buildFromDexFiles(dex);

        ClassDef activeWorker = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(activeWorker);
        assertTrue(reachabilityIndex.isClassReferenced(activeWorker.getType()));

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(activeWorker, options, stats, reachabilityIndex);

        assertEquals(1, stats.backgroundWorkersNeutralized);
        assertEquals(0, stats.deadCodeSkipped);

        // Verify doWork() rewritten
        Method doWorkMethod = null;
        for (Method m : sanitized.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                doWorkMethod = m;
                break;
            }
        }
        assertNotNull(doWorkMethod);
        assertEquals(3, ((List<?>) doWorkMethod.getImplementation().getInstructions()).size());
    }

    @Test
    public void testNonTelemetryWorkerNotNeutralized() throws Exception {
        File dexFile = assembleSmali(NORMAL_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef normalWorker = findClass(dex, "Lcom/example/DatabaseSyncWorker;");
        assertNotNull(normalWorker);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(normalWorker, options, stats);

        assertEquals(0, stats.backgroundWorkersNeutralized);
        assertEquals(0, stats.deadCodeSkipped);

        // Verify doWork() still has original retry()
        Method doWorkMethod = null;
        for (Method m : sanitized.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                doWorkMethod = m;
                break;
            }
        }
        assertNotNull(doWorkMethod);
        boolean hasRetry = false;
        for (Instruction inst : doWorkMethod.getImplementation().getInstructions()) {
            if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference && "retry".equals(((MethodReference) ref).getName())) {
                    hasRetry = true;
                }
            }
        }
        assertTrue("Non-telemetry worker should keep original doWork", hasRetry);
    }

    @Test
    public void testOptionDisabledSkipsWorkerNeutralization() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef workerDef = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(workerDef);

        PrivacyOptions options = PrivacyOptions.all();
        options.neutralizeBackgroundWorkers = false;
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(workerDef, options, stats);

        assertEquals(0, stats.backgroundWorkersNeutralized);
    }

    @Test
    public void testNonWorkerMethodsStillSanitizedInWorkerClass() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef workerDef = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(workerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(workerDef, options, stats);

        // Background worker neutralized
        assertEquals(1, stats.backgroundWorkersNeutralized);
        // Logging stripped from constructor and/or flushEvents
        assertTrue("Expected logCallsStripped > 0, got " + stats.logCallsStripped, stats.logCallsStripped > 0);
        // flushEvents had app-measurement.com URL sinkholed
        assertTrue("Expected adIdsNeutralized or telemetryDomainsSinkholed > 0",
                stats.adIdsNeutralized > 0 || stats.telemetryDomainsSinkholed > 0);
    }

    @Test
    public void testCujoAndOtmWorkerNeutralization() throws Exception {
        File dexFile = assembleSmali(CUJO_WORKER_SMALI, OTM_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        ClassDef cujo = findClass(dex, "Lcom/xfinity/cujo/CujoWorker;");
        assertNotNull(cujo);
        PrivacyStats cujoStats = new PrivacyStats();
        PrivacyBytecodeEngine.sanitizeClass(cujo, PrivacyOptions.all(), cujoStats);
        assertEquals(1, cujoStats.backgroundWorkersNeutralized);

        ClassDef otm = findClass(dex, "Lcom/xfinity/otm/OtmWorker;");
        assertNotNull(otm);
        PrivacyStats otmStats = new PrivacyStats();
        PrivacyBytecodeEngine.sanitizeClass(otm, PrivacyOptions.all(), otmStats);
        assertEquals(1, otmStats.backgroundWorkersNeutralized);
    }

    @Test
    public void testNeutralizeWorkerClassDirectMethod() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef workerDef = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(workerDef);

        PrivacyStats stats = new PrivacyStats();
        MutableMethodImplementation mutable = new MutableMethodImplementation(2);
        MutableMethodImplementation result = PrivacyBytecodeEngine.neutralizeWorkerClass(workerDef, mutable, stats);

        assertNotNull(result);
        assertEquals(1, stats.backgroundWorkersNeutralized);
        assertEquals(3, ((List<?>) result.getInstructions()).size());
        assertTrue(result.getRegisterCount() >= 1);
    }

    @Test
    public void testSanitizedWorkerDexPoolAssembly() throws Exception {
        File dexFile = assembleSmali(ANALYTICS_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef workerDef = findClass(dex, "Lcom/example/AnalyticsWorker;");
        assertNotNull(workerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(workerDef, options, stats);
        assertNotNull(sanitized);

        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);

        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue("Written DEX size should be positive", store.getSize() > 0);

        // Verify re-parsing the written DEX bytes produces valid ClassDef and instructions
        DexBackedDexFile parsed = new DexBackedDexFile(Opcodes.getDefault(), store.getData());
        ClassDef reloadedClass = findClass(parsed, "Lcom/example/AnalyticsWorker;");
        assertNotNull(reloadedClass);

        Method reloadedDoWork = null;
        for (Method m : reloadedClass.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                reloadedDoWork = m;
                break;
            }
        }
        assertNotNull(reloadedDoWork);
        assertNotNull(reloadedDoWork.getImplementation());
        List<Instruction> reloadedInstructions = new ArrayList<>();
        for (Instruction inst : reloadedDoWork.getImplementation().getInstructions()) {
            reloadedInstructions.add(inst);
        }
        assertEquals(3, reloadedInstructions.size());
    }

    @Test
    public void testNativeAndAbstractWorkerFlagsMaskedInDexPoolAssembly() throws Exception {
        File dexFile = assembleSmali(NATIVE_WORKER_SMALI, ABSTRACT_WORKER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        ClassDef nativeWorker = findClass(dex, "Lcom/example/NativeTelemetryWorker;");
        assertNotNull(nativeWorker);
        ClassDef abstractWorker = findClass(dex, "Lcom/example/AbstractTelemetryWorker;");
        assertNotNull(abstractWorker);

        PrivacyStats nativeStats = new PrivacyStats();
        ClassDef sanitizedNative = PrivacyBytecodeEngine.sanitizeClass(nativeWorker, PrivacyOptions.all(), nativeStats);
        assertEquals(1, nativeStats.backgroundWorkersNeutralized);

        PrivacyStats abstractStats = new PrivacyStats();
        ClassDef sanitizedAbstract = PrivacyBytecodeEngine.sanitizeClass(abstractWorker, PrivacyOptions.all(), abstractStats);
        assertEquals(1, abstractStats.backgroundWorkersNeutralized);

        // Verify neither NATIVE nor ABSTRACT flags remain on doWork
        for (Method m : sanitizedNative.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                assertEquals(0, m.getAccessFlags() & (AccessFlags.NATIVE.getValue() | AccessFlags.ABSTRACT.getValue()));
                assertNotNull(m.getImplementation());
            }
        }
        for (Method m : sanitizedAbstract.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) {
                assertEquals(0, m.getAccessFlags() & (AccessFlags.NATIVE.getValue() | AccessFlags.ABSTRACT.getValue()));
                assertNotNull(m.getImplementation());
            }
        }

        // Verify writing to DexPool produces valid DEX with zero assembler / verifier errors
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitizedNative);
        pool.internClass(sanitizedAbstract);

        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);

        DexBackedDexFile parsed = new DexBackedDexFile(Opcodes.getDefault(), store.getData());
        assertNotNull(findClass(parsed, "Lcom/example/NativeTelemetryWorker;"));
        assertNotNull(findClass(parsed, "Lcom/example/AbstractTelemetryWorker;"));
    }
}
