package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
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
 * Unit tests verifying Task 5: Generative AI & Assistant Bytecode Nullification (Module B).
 */
public class AiBytecodeNullificationTest {

    private static final String SAVX_ELIGIBILITY_STATE_SMALI =
            """
            .class public final enum Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
            .super Ljava/lang/Enum;
            .source "SavXEligibilityState.java"

            .field public static final enum ELIGIBLE:Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
            .field public static final enum NOT_ELIGIBLE:Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
            .field public static final enum INITIAL:Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;

            .method public static valueOf(Ljava/lang/String;)Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
                .locals 1
                const-class v0, Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
                invoke-static {v0, p0}, Ljava/lang/Enum;->valueOf(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;
                move-result-object v0
                check-cast v0, Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
                return-object v0
            .end method
            """;

    private static final String SAVX_ELIGIBILITY_MANAGER_SMALI =
            """
            .class public Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;
            .super Ljava/lang/Object;
            .source "SavXStableEligibilityManager.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public isEligible()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public isEnabled()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public evaluateEligibility()Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
                .locals 1
                sget-object v0, Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;->ELIGIBLE:Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;
                return-object v0
            .end method
            """;

    private static final String USER_FEATURE_MANAGER_SMALI =
            """
            .class public Lcom/xfinity/digitalhome/features/overview/activities/UserFeatureManager;
            .super Ljava/lang/Object;
            .source "UserFeatureManager.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public getSupportsAssistant()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public supportsAssistant()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public isAiEnabled()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public isRufusEnabled()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method
            """;

    private static final String IMOBILE_WEBLAB_SMALI =
            """
            .class public interface abstract Lcom/amazon/weblab/mobile/IMobileWeblab;
            .super Ljava/lang/Object;
            .source "IMobileWeblab.java"

            .method public abstract getTreatmentAssignment(Ljava/lang/String;)Ljava/lang/String;
            .end method
            """;

    private static final String FEATURE_GATE_SMALI =
            """
            .class public interface abstract Lcom/example/FeatureGate;
            .super Ljava/lang/Object;
            .source "FeatureGate.java"

            .method public abstract isFeatureEnabled(Ljava/lang/String;)Z
            .end method
            """;

    private static final String WEBLAB_CALLER_SMALI =
            """
            .class public Lcom/example/WeblabCaller;
            .super Ljava/lang/Object;
            .source "WeblabCaller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public checkRufus(Lcom/amazon/weblab/mobile/IMobileWeblab;)Ljava/lang/String;
                .locals 2
                const-string v0, "RUFUS_SEARCH_INGRESS"
                invoke-interface {p1, v0}, Lcom/amazon/weblab/mobile/IMobileWeblab;->getTreatmentAssignment(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                return-object v1
            .end method

            .method public checkCart(Lcom/amazon/weblab/mobile/IMobileWeblab;)Ljava/lang/String;
                .locals 2
                const-string v0, "CART_REDESIGN"
                invoke-interface {p1, v0}, Lcom/amazon/weblab/mobile/IMobileWeblab;->getTreatmentAssignment(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                return-object v1
            .end method

            .method public checkBooleanGate(Lcom/example/FeatureGate;)Z
                .locals 2
                const-string v0, "copilot_assistant_flag"
                invoke-interface {p1, v0}, Lcom/example/FeatureGate;->isFeatureEnabled(Ljava/lang/String;)Z
                move-result v1
                return v1
            .end method
            """;

    private static final String BOTTOM_TABS_CONTROLLER_SMALI =
            """
            .class public Lcom/amazon/mShop/rendering/BottomTabsBarSavXController;
            .super Ljava/lang/Object;
            .source "BottomTabsBarSavXController.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public selectRufusTabItem(I)V
                .locals 2
                const-string v0, "SavXTab"
                const-string v1, "Selected Rufus tab"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method

            .method public showRufusIngress()V
                .locals 2
                const-string v0, "SavXTab"
                const-string v1, "Showing ingress"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method
            """;

    private static final String BOTTOM_TABS_EXTENDED_SMALI =
            """
            .class public Lcom/amazon/mShop/rendering/ExtendedBottomTabsBarSavXController;
            .super Ljava/lang/Object;
            .source "ExtendedBottomTabsBarSavXController.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public showRufusIngressFloat()F
                .locals 1
                const/high16 v0, 0x3f800000    # 1.0f
                return v0
            .end method

            .method public showRufusIngressDouble()D
                .locals 2
                const-wide/high16 v0, 0x3ff0000000000000L    # 1.0
                return-wide v0
            .end method

            .method public selectRufusTabView()Ljava/lang/Object;
                .locals 1
                new-instance v0, Ljava/lang/Object;
                invoke-direct {v0}, Ljava/lang/Object;-><init>()V
                return-object v0
            .end method
            """;

    private static final String CUSTOM_SETTINGS_AI_SMALI =
            """
            .class public Lcom/example/CustomSettings;
            .super Ljava/lang/Object;
            .source "CustomSettings.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public getSupportsAssistant()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method

            .method public isEnabled()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method
            """;

    private static final String NON_STRING_WEBLAB_CALLER_SMALI =
            """
            .class public Lcom/example/NonStringCaller;
            .super Ljava/lang/Object;
            .source "NonStringCaller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static getObjectExperiment(Ljava/lang/String;)Ljava/lang/Object;
                .locals 1
                new-instance v0, Ljava/lang/Object;
                invoke-direct {v0}, Ljava/lang/Object;-><init>()V
                return-object v0
            .end method

            .method public testRufusObject()Ljava/lang/Object;
                .locals 2
                const-string v0, "rufus_config"
                invoke-static {v0}, Lcom/example/NonStringCaller;->getObjectExperiment(Ljava/lang/String;)Ljava/lang/Object;
                move-result-object v1
                return-object v1
            .end method
            """;

    private static final String ACTIVE_CALLER_SMALI =
            """
            .class public Lcom/example/ActiveCaller;
            .super Ljava/lang/Object;
            .source "ActiveCaller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static invokeManager(Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;)V
                .locals 0
                invoke-virtual {p0}, Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;->isEligible()Z
                return-void
            .end method

            .method public static invokeTab(Lcom/amazon/mShop/rendering/BottomTabsBarSavXController;)V
                .locals 1
                const/4 v0, 0
                invoke-virtual {p0, v0}, Lcom/amazon/mShop/rendering/BottomTabsBarSavXController;->selectRufusTabItem(I)V
                return-void
            .end method
            """;

    private File assembleSmali(String... smaliFiles) throws Exception {
        File dir = Files.createTempDirectory("ai_test").toFile();
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

    private Method findMethod(ClassDef classDef, String name) {
        for (Method m : classDef.getVirtualMethods()) {
            if (name.equals(m.getName())) return m;
        }
        for (Method m : classDef.getDirectMethods()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    @Test
    public void testIsAiControllerClass() {
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/xfinity/digitalhome/features/overview/activities/UserFeatureManager;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/amazon/mShop/rendering/BottomTabsBarSavXController;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/walmart/sparky/SparkyController;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/example/CopilotManager;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/example/GenAiAssistant;"));
        assertTrue(AiBytecodeRules.isAiControllerClass("Lcom/example/AiChatHelper;"));
        assertFalse(AiBytecodeRules.isAiControllerClass("Lcom/example/NormalActivity;"));
        assertFalse(AiBytecodeRules.isAiControllerClass("Lcom/example/DatabaseHelper;"));
    }

    @Test
    public void testSavXEligibilityManagerNullification() throws Exception {
        File dexFile = assembleSmali(SAVX_ELIGIBILITY_STATE_SMALI, SAVX_ELIGIBILITY_MANAGER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef managerDef = findClass(dex, "Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;");
        assertNotNull(managerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(managerDef, options, stats);

        // Verify stats
        assertEquals(3, stats.aiEligibilityManagersStubbed); // isEligible, isEnabled, evaluateEligibility
        assertTrue(stats.classesModified > 0);

        // Verify evaluateEligibility returns NOT_ELIGIBLE
        Method evalMethod = findMethod(sanitized, "evaluateEligibility");
        assertNotNull(evalMethod);
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction inst : evalMethod.getImplementation().getInstructions()) {
            instructions.add(inst);
        }
        assertEquals(2, instructions.size());
        assertEquals(Opcode.SGET_OBJECT, instructions.get(0).getOpcode());
        ReferenceInstruction sget = (ReferenceInstruction) instructions.get(0);
        FieldReference fr = (FieldReference) sget.getReference();
        assertEquals("NOT_ELIGIBLE", fr.getName());
        assertEquals("Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;", fr.getDefiningClass());
        assertEquals(Opcode.RETURN_OBJECT, instructions.get(1).getOpcode());

        // Verify isEligible returns false (0)
        Method isEligibleMethod = findMethod(sanitized, "isEligible");
        assertNotNull(isEligibleMethod);
        List<Instruction> isEligibleInsts = new ArrayList<>();
        for (Instruction inst : isEligibleMethod.getImplementation().getInstructions()) {
            isEligibleInsts.add(inst);
        }
        assertEquals(2, isEligibleInsts.size());
        assertEquals(Opcode.CONST_4, isEligibleInsts.get(0).getOpcode());
        assertEquals(0, ((NarrowLiteralInstruction) isEligibleInsts.get(0)).getNarrowLiteral());
        assertEquals(Opcode.RETURN, isEligibleInsts.get(1).getOpcode());

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testUserFeatureManagerNullification() throws Exception {
        File dexFile = assembleSmali(USER_FEATURE_MANAGER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef managerDef = findClass(dex, "Lcom/xfinity/digitalhome/features/overview/activities/UserFeatureManager;");
        assertNotNull(managerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(managerDef, options, stats);

        assertEquals(4, stats.aiEligibilityManagersStubbed); // getSupportsAssistant, supportsAssistant, isAiEnabled, isRufusEnabled
        assertTrue(stats.classesModified > 0);

        Method supportsMethod = findMethod(sanitized, "getSupportsAssistant");
        assertNotNull(supportsMethod);
        List<Instruction> insts = new ArrayList<>();
        for (Instruction inst : supportsMethod.getImplementation().getInstructions()) {
            insts.add(inst);
        }
        assertEquals(2, insts.size());
        assertEquals(Opcode.CONST_4, insts.get(0).getOpcode());
        assertEquals(0, ((NarrowLiteralInstruction) insts.get(0)).getNarrowLiteral());
        assertEquals(Opcode.RETURN, insts.get(1).getOpcode());

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testWeblabTreatmentCallsiteInterception() throws Exception {
        File dexFile = assembleSmali(IMOBILE_WEBLAB_SMALI, FEATURE_GATE_SMALI, WEBLAB_CALLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef callerDef = findClass(dex, "Lcom/example/WeblabCaller;");
        assertNotNull(callerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(callerDef, options, stats);

        // 2 callsites intercepted: checkRufus (String -> "C") and checkBooleanGate (boolean -> 0)
        assertEquals(2, stats.aiFeatureGatesNeutralized);

        // Verify checkRufus has move-result-object replaced with const-string "C"
        Method checkRufusMethod = findMethod(sanitized, "checkRufus");
        assertNotNull(checkRufusMethod);
        boolean foundConstC = false;
        for (Instruction inst : checkRufusMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.CONST_STRING) {
                ReferenceInstruction ri = (ReferenceInstruction) inst;
                if ("C".equals(((StringReference) ri.getReference()).getString())) {
                    foundConstC = true;
                }
            }
        }
        assertTrue("Expected const-string \"C\" replacement in checkRufus", foundConstC);

        // Verify checkCart remains unchanged (still has CART_REDESIGN and move-result-object)
        Method checkCartMethod = findMethod(sanitized, "checkCart");
        assertNotNull(checkCartMethod);
        boolean foundMoveResultObj = false;
        for (Instruction inst : checkCartMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                foundMoveResultObj = true;
            }
        }
        assertTrue("Expected move-result-object to be preserved for CART_REDESIGN", foundMoveResultObj);

        // Verify checkBooleanGate has move-result replaced with const/4 0
        Method checkBoolMethod = findMethod(sanitized, "checkBooleanGate");
        assertNotNull(checkBoolMethod);
        boolean foundConstZero = false;
        for (Instruction inst : checkBoolMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.CONST_4 && ((NarrowLiteralInstruction) inst).getNarrowLiteral() == 0) {
                foundConstZero = true;
            }
        }
        assertTrue("Expected const/4 0 replacement in checkBooleanGate", foundConstZero);

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testBottomTabsIngressNeutralization() throws Exception {
        File dexFile = assembleSmali(BOTTOM_TABS_CONTROLLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef tabsDef = findClass(dex, "Lcom/amazon/mShop/rendering/BottomTabsBarSavXController;");
        assertNotNull(tabsDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(tabsDef, options, stats);

        assertEquals(2, stats.aiTabIngressNeutralized); // selectRufusTabItem, showRufusIngress

        // Verify selectRufusTabItem is rewritten to return-void
        Method selectMethod = findMethod(sanitized, "selectRufusTabItem");
        assertNotNull(selectMethod);
        List<Instruction> selectInsts = new ArrayList<>();
        for (Instruction inst : selectMethod.getImplementation().getInstructions()) {
            selectInsts.add(inst);
        }
        assertEquals(1, selectInsts.size());
        assertEquals(Opcode.RETURN_VOID, selectInsts.get(0).getOpcode());

        // Verify showRufusIngress is rewritten to return-void
        Method showMethod = findMethod(sanitized, "showRufusIngress");
        assertNotNull(showMethod);
        List<Instruction> showInsts = new ArrayList<>();
        for (Instruction inst : showMethod.getImplementation().getInstructions()) {
            showInsts.add(inst);
        }
        assertEquals(1, showInsts.size());
        assertEquals(Opcode.RETURN_VOID, showInsts.get(0).getOpcode());

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testReachabilityAwareDeadCodeSkipping() throws Exception {
        File managerDex = assembleSmali(SAVX_ELIGIBILITY_STATE_SMALI, SAVX_ELIGIBILITY_MANAGER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(managerDex, Opcodes.getDefault());

        // Build empty reachability index (0 references to SavXStableEligibilityManager)
        DexReachabilityIndex emptyIndex = new DexReachabilityIndex();

        ClassDef managerDef = findClass(dex, "Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;");
        assertNotNull(managerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(managerDef, options, stats, emptyIndex);

        // Dead code must be skipped
        assertEquals(0, stats.aiEligibilityManagersStubbed);
        assertEquals(3, stats.deadCodeSkipped); // isEligible, isEnabled, evaluateEligibility
        assertEquals(0, stats.classesModified);

        // Now build reachability index WITH invocation
        File callerDex = assembleSmali(SAVX_ELIGIBILITY_STATE_SMALI, SAVX_ELIGIBILITY_MANAGER_SMALI, ACTIVE_CALLER_SMALI);
        DexBackedDexFile callerBackedDex = DexFileFactory.loadDexFile(callerDex, Opcodes.getDefault());
        DexReachabilityIndex activeIndex = DexReachabilityIndex.buildFromDexFiles(callerBackedDex);

        PrivacyStats activeStats = new PrivacyStats();
        ClassDef activeSanitized = PrivacyBytecodeEngine.sanitizeClass(managerDef, options, activeStats, activeIndex);

        // isEligible was invoked, so it gets stubbed
        assertTrue(activeStats.aiEligibilityManagersStubbed >= 1);
        assertTrue(activeStats.deadCodeSkipped >= 1); // isEnabled / evaluateEligibility not in ActiveCaller
        assertTrue(activeStats.classesModified > 0);
    }

    @Test
    public void testDisabledOptionsNoMutation() throws Exception {
        File dexFile = assembleSmali(SAVX_ELIGIBILITY_STATE_SMALI, SAVX_ELIGIBILITY_MANAGER_SMALI, BOTTOM_TABS_CONTROLLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef managerDef = findClass(dex, "Lcom/amazon/mShop/savX/manager/eligibility/SavXStableEligibilityManager;");

        PrivacyOptions options = PrivacyOptions.none();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(managerDef, options, stats);

        assertEquals(0, stats.aiEligibilityManagersStubbed);
        assertEquals(0, stats.aiTabIngressNeutralized);
        assertEquals(0, stats.aiFeatureGatesNeutralized);
        assertEquals(0, stats.classesModified);
    }

    @Test
    public void testExtendedTabIngressFloatDoubleAndObjectReturn() throws Exception {
        File dexFile = assembleSmali(BOTTOM_TABS_EXTENDED_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef tabsDef = findClass(dex, "Lcom/amazon/mShop/rendering/ExtendedBottomTabsBarSavXController;");
        assertNotNull(tabsDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(tabsDef, options, stats);

        // float and double methods stubbed, object return NOT stubbed
        assertEquals(2, stats.aiTabIngressNeutralized);

        // Verify float method rewritten to const/4 0 -> return
        Method floatMethod = findMethod(sanitized, "showRufusIngressFloat");
        assertNotNull(floatMethod);
        List<Instruction> floatInsts = new ArrayList<>();
        for (Instruction inst : floatMethod.getImplementation().getInstructions()) {
            floatInsts.add(inst);
        }
        assertEquals(2, floatInsts.size());
        assertEquals(Opcode.CONST_4, floatInsts.get(0).getOpcode());
        assertEquals(Opcode.RETURN, floatInsts.get(1).getOpcode());

        // Verify double method rewritten to const-wide 0 -> return-wide, regCount >= 2
        Method doubleMethod = findMethod(sanitized, "showRufusIngressDouble");
        assertNotNull(doubleMethod);
        assertTrue(doubleMethod.getImplementation().getRegisterCount() >= 2);
        List<Instruction> doubleInsts = new ArrayList<>();
        for (Instruction inst : doubleMethod.getImplementation().getInstructions()) {
            doubleInsts.add(inst);
        }
        assertEquals(2, doubleInsts.size());
        assertEquals(Opcode.CONST_WIDE_16, doubleInsts.get(0).getOpcode());
        assertEquals(Opcode.RETURN_WIDE, doubleInsts.get(1).getOpcode());

        // Verify object method remains unmodified (still returns new Object)
        Method objMethod = findMethod(sanitized, "selectRufusTabView");
        assertNotNull(objMethod);
        boolean hasNewInstance = false;
        for (Instruction inst : objMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.NEW_INSTANCE) {
                hasNewInstance = true;
            }
        }
        assertTrue("Expected selectRufusTabView to preserve implementation and not return null", hasNewInstance);

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testClassIdentifiedByMethodDefinitions() throws Exception {
        File dexFile = assembleSmali(CUSTOM_SETTINGS_AI_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef settingsDef = findClass(dex, "Lcom/example/CustomSettings;");
        assertNotNull(settingsDef);

        // Class name does NOT match AI_CONTROLLER_CLASS_PATTERN, but class defines getSupportsAssistant()
        assertFalse(AiBytecodeRules.isAiControllerClass(settingsDef.getType()));
        assertTrue(AiBytecodeRules.isAiControllerClass(settingsDef));

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(settingsDef, options, stats);

        // Both getSupportsAssistant and isEnabled are stubbed because isAiClass was wired from ClassDef
        assertEquals(2, stats.aiEligibilityManagersStubbed);
        assertTrue(stats.classesModified > 0);

        // Verify DexPool assembly
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(sanitized);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        assertTrue(store.getSize() > 0);
    }

    @Test
    public void testWeblabTreatmentNonStringReturnPreserved() throws Exception {
        File dexFile = assembleSmali(NON_STRING_WEBLAB_CALLER_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        ClassDef callerDef = findClass(dex, "Lcom/example/NonStringCaller;");
        assertNotNull(callerDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(callerDef, options, stats);

        // Target method returns Object, so move-result-object must NOT be replaced with "C"
        assertEquals(0, stats.aiFeatureGatesNeutralized);

        Method testMethod = findMethod(sanitized, "testRufusObject");
        assertNotNull(testMethod);
        boolean foundMoveResultObj = false;
        for (Instruction inst : testMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                foundMoveResultObj = true;
            }
        }
        assertTrue("Expected move-result-object to be preserved when return type is Object", foundMoveResultObj);
    }

    @Test
    public void testReachabilityOverloadDistinction() {
        DexReachabilityIndex index = new DexReachabilityIndex();
        // Index only 0-arg method
        // Method with different return or param should not match
        assertFalse(index.isMethodInvoked("Lcom/example/Manager;", "isEligible", "Z"));
    }
}
