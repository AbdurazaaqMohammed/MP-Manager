package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests verifying Task 3: Streaming Multi-DEX Reachability Discovery Engine.
 */
public class DexReachabilityIndexTest {

    private static final String CALLER_SMALI =
            """
            .class public Ltest/Caller;
            .super Ljava/lang/Object;
            .source "Caller.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static invokeActive()V
                .locals 1
                invoke-static {}, Ltest/Service;->activeCall()V
                sget v0, Ltest/Service;->activeField:I
                return-void
            .end method
            """;

    private static final String SERVICE_SMALI =
            """
            .class public Ltest/Service;
            .super Ljava/lang/Object;
            .source "Service.java"

            .field public static activeField:I
            .field public static deadField:I

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static activeCall()V
                .locals 0
                return-void
            .end method

            .method public static deadCall()V
                .locals 0
                return-void
            .end method
            """;

    private static final String CHAIN_CALLER_SMALI =
            """
            .class public Ltest/ChainCaller;
            .super Ljava/lang/Object;
            .source "ChainCaller.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static startChain()V
                .locals 0
                invoke-static {}, Ltest/Intermediary;->forward()V
                return-void
            .end method
            """;

    private static final String INTERMEDIARY_SMALI =
            """
            .class public Ltest/Intermediary;
            .super Ljava/lang/Object;
            .source "Intermediary.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static forward()V
                .locals 0
                invoke-static {}, Ltest/Service;->activeCall()V
                return-void
            .end method
            """;

    private File assembleSmali(String className, String smaliCode) throws Exception {
        File dir = Files.createTempDirectory("reachability_test_" + className).toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, className + ".smali"))) {
            w.write(smaliCode);
        }
        File outDex = new File(dir, className + ".dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue(Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    private DexBackedDexFile dexCaller;
    private DexBackedDexFile dexService;

    @Before
    public void setUp() throws Exception {
        File callerDexFile = assembleSmali("Caller", CALLER_SMALI);
        File serviceDexFile = assembleSmali("Service", SERVICE_SMALI);
        dexCaller = DexFileFactory.loadDexFile(callerDexFile, Opcodes.getDefault());
        dexService = DexFileFactory.loadDexFile(serviceDexFile, Opcodes.getDefault());
    }

    @Test
    public void testActiveAndDeadMethodReachabilityMultiDex() {
        DexReachabilityIndex index = DexReachabilityIndex.buildFromDexFiles(Arrays.asList(dexCaller, dexService));

        // activeCall() is invoked by Caller in classes.dex -> reachability: true
        assertTrue("activeCall() should be reported as invoked",
                index.isMethodInvoked("Ltest/Service;", "activeCall", "V"));

        // deadCall() is defined in Service in classes2.dex but never called -> reachability: false
        assertFalse("deadCall() should NOT be reported as invoked",
                index.isMethodInvoked("Ltest/Service;", "deadCall", "V"));

        // activeField is read via sget in Caller -> reachability: true
        assertTrue("activeField should be reported as referenced",
                index.isFieldReferenced("Ltest/Service;", "activeField"));

        // deadField is defined but never accessed -> reachability: false
        assertFalse("deadField should NOT be reported as referenced",
                index.isFieldReferenced("Ltest/Service;", "deadField"));

        // Ltest/Service; is referenced by Caller -> true
        assertTrue("Ltest/Service; should be reported as referenced",
                index.isClassReferenced("Ltest/Service;"));

        // Non-existent class is never referenced -> false
        assertFalse("Ltest/NonExistent; should NOT be referenced",
                index.isClassReferenced("Ltest/NonExistent;"));

        // Total indexed references must be positive
        assertTrue("Total indexed references should be > 0",
                index.getTotalIndexedReferences() > 0);
    }

    @Test
    public void testMethodReferenceOverload() {
        DexReachabilityIndex index = DexReachabilityIndex.buildFromDexFiles(Arrays.asList(dexCaller, dexService));

        Method activeMethod = null;
        Method deadMethod = null;
        for (ClassDef cd : dexService.getClasses()) {
            if ("Ltest/Service;".equals(cd.getType())) {
                for (Method m : cd.getMethods()) {
                    if ("activeCall".equals(m.getName())) activeMethod = m;
                    if ("deadCall".equals(m.getName())) deadMethod = m;
                }
            }
        }

        assertNotNull("activeMethod must be found in Service ClassDef", activeMethod);
        assertNotNull("deadMethod must be found in Service ClassDef", deadMethod);

        assertTrue("MethodReference overload should detect activeCall", index.isMethodInvoked(activeMethod));
        assertFalse("MethodReference overload should report false for deadCall", index.isMethodInvoked(deadMethod));
    }

    @Test
    public void testFlexibleFormattingAndFqcn() {
        DexReachabilityIndex index = DexReachabilityIndex.buildFromDexFiles(Arrays.asList(dexCaller, dexService));

        // Java FQCN format: test.Service, void
        assertTrue("Java FQCN with test.Service and void should match",
                index.isMethodInvoked("test.Service", "activeCall", "void"));

        // Slash format: test/Service
        assertTrue("Slash format test/Service should match",
                index.isMethodInvoked("test/Service", "activeCall", "V"));

        // Without return type
        assertTrue("Method invocation without return type should match",
                index.isMethodInvoked("Ltest/Service;", "activeCall"));

        // Full descriptor query
        assertTrue("Full descriptor should match",
                index.isMethodInvoked("Ltest/Service;->activeCall()V"));
        assertFalse("Full descriptor for uninvoked method should not match",
                index.isMethodInvoked("Ltest/Service;->deadCall()V"));
    }

    @Test
    public void testStreamingThreeDexCrossReferences() throws Exception {
        File chainCallerDexFile = assembleSmali("ChainCaller", CHAIN_CALLER_SMALI);
        File intermediaryDexFile = assembleSmali("Intermediary", INTERMEDIARY_SMALI);
        DexBackedDexFile dexChainCaller = DexFileFactory.loadDexFile(chainCallerDexFile, Opcodes.getDefault());
        DexBackedDexFile dexIntermediary = DexFileFactory.loadDexFile(intermediaryDexFile, Opcodes.getDefault());

        // Stream 3 DEX files (ChainCaller -> Intermediary -> Service)
        DexReachabilityIndex index = DexReachabilityIndex.buildFromDexFiles(
                Arrays.asList(dexChainCaller, dexIntermediary, dexService));

        assertTrue(index.isMethodInvoked("Ltest/Intermediary;", "forward", "V"));
        assertTrue(index.isMethodInvoked("Ltest/Service;", "activeCall", "V"));
        assertTrue(index.isClassReferenced("Ltest/Intermediary;"));
        assertTrue(index.isClassReferenced("Ltest/Service;"));
        assertFalse(index.isMethodInvoked("Ltest/Service;", "deadCall", "V"));
        assertTrue(index.getTotalIndexedReferences() >= 3);
    }

    @Test
    public void testNullAndEmptySafety() {
        DexReachabilityIndex empty = DexReachabilityIndex.buildFromDexFiles((Iterable<DexBackedDexFile>) null);
        assertEquals(0, empty.getTotalIndexedReferences());
        assertFalse(empty.isMethodInvoked("Ltest/Service;", "activeCall", "V"));
        assertFalse(empty.isFieldReferenced("Ltest/Service;", "activeField"));
        assertFalse(empty.isClassReferenced("Ltest/Service;"));

        DexReachabilityIndex emptyList = DexReachabilityIndex.buildFromDexFiles(Collections.emptyList());
        assertEquals(0, emptyList.getTotalIndexedReferences());
        assertFalse(emptyList.isMethodInvoked(null, "activeCall", "V"));
        assertFalse(emptyList.isMethodInvoked("Ltest/Service;", null, "V"));
        assertFalse(emptyList.isFieldReferenced(null, "field"));
        assertFalse(emptyList.isFieldReferenced("Ltest/Service;", null));
        assertFalse(emptyList.isClassReferenced(null));
        assertFalse(emptyList.isClassReferenced(""));
    }

    @Test
    public void testDirectIndexDexFileMethod() {
        DexReachabilityIndex index = new DexReachabilityIndex();
        assertEquals(0, index.getTotalIndexedReferences());

        index.indexDexFile(dexCaller);
        assertTrue("After caller indexed, activeCall is recognized",
                index.isMethodInvoked("Ltest/Service;", "activeCall", "V"));
        assertFalse(index.isMethodInvoked("Ltest/Service;", "deadCall", "V"));

        int countBefore = index.getTotalIndexedReferences();
        index.indexDexFile(dexService);
        int countAfter = index.getTotalIndexedReferences();
        assertTrue(countAfter >= countBefore);
    }
}
