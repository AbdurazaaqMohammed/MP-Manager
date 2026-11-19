package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/**
 * Unit tests verifying Stage 4: Domain Sinkholing and Native Library Auditing.
 */
public class DomainSinkholeTest {

    private static final String TELEMETRY_ENDPOINTS_SMALI =
            """
            .class public Ltest/TelemetryClient;
            .super Ljava/lang/Object;
            .source "TelemetryClient.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public getEndpoints()Ljava/lang/String;
                .locals 2
                const-string v0, "https://t.appsflyer.com/api/v4/event"
                const-string v1, "https://api2.branch.io/v1/open"
                return-object v0
            .end method
            """;

    private File assembleSmali(String smaliCode) throws Exception {
        File dir = Files.createTempDirectory("sinkhole_test").toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, "TelemetryClient.smali"))) {
            w.write(smaliCode);
        }
        File outDex = new File(dir, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue(Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    @Test
    public void testIsTelemetryEndpoint() {
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://app-measurement.com/a"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://t.appsflyer.com/api/v4/event"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://api2.branch.io/v1/open"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://app.adjust.com/event"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://telemetry.mycorp.com/collect"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://siftscience.com/api"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://browser-http-intake.logs.datadoghq.com/v1/input"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://sdk.iad-01.braze.com/api/v3/data"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://events.launchdarkly.com/mobile"));
        assertTrue(DomainSinkholer.isTelemetryEndpoint("https://control.kochava.com/track/json"));

        assertFalse(DomainSinkholer.isTelemetryEndpoint("https://www.google.com/search"));
        assertFalse(DomainSinkholer.isTelemetryEndpoint("https://api.github.com/repos"));
        assertFalse(DomainSinkholer.isTelemetryEndpoint(""));
        assertFalse(DomainSinkholer.isTelemetryEndpoint(null));
    }

    @Test
    public void testIsAiEndpoint() {
        assertTrue(DomainSinkholer.isAiEndpoint("https://andistreaminghub.amazon.com/v1/stream"));
        assertTrue(DomainSinkholer.isAiEndpoint("https://aiq.xa.comcast.net/assistant/query"));
        assertTrue(DomainSinkholer.isAiEndpoint("https://api.openai.com/v1/chat/completions"));
        assertTrue(DomainSinkholer.isAiEndpoint("https://anthropic.com/v1/messages"));
        assertTrue(DomainSinkholer.isAiEndpoint("https://generativelanguage.googleapis.com/v1beta/models"));
        assertTrue(DomainSinkholer.isAiEndpoint("andistreaminghub.amazon.com"));
        assertTrue(DomainSinkholer.isAiEndpoint("aiq.xa.comcast.net"));

        assertFalse(DomainSinkholer.isAiEndpoint("https://www.google.com/search"));
        assertFalse(DomainSinkholer.isAiEndpoint("https://api.github.com/repos"));
        assertFalse(DomainSinkholer.isAiEndpoint(""));
        assertFalse(DomainSinkholer.isAiEndpoint(null));
    }

    @Test
    public void testIsAiOrTelemetryEndpoint() {
        assertTrue(DomainSinkholer.isAiOrTelemetryEndpoint("https://andistreaminghub.amazon.com/v1/stream"));
        assertTrue(DomainSinkholer.isAiOrTelemetryEndpoint("https://aiq.xa.comcast.net/assistant/query"));
        assertTrue(DomainSinkholer.isAiOrTelemetryEndpoint("https://siftscience.com/api"));
        assertTrue(DomainSinkholer.isAiOrTelemetryEndpoint("https://events.launchdarkly.com/mobile"));

        assertFalse(DomainSinkholer.isAiOrTelemetryEndpoint("https://example.com/api"));
        assertFalse(DomainSinkholer.isAiOrTelemetryEndpoint("https://www.google.com/search"));
    }

    @Test
    public void testSinkholeMethod() throws Exception {
        File dexFile = assembleSmali(TELEMETRY_ENDPOINTS_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        ClassDef classDef = null;
        for (ClassDef cd : dex.getClasses()) {
            if ("Ltest/TelemetryClient;".equals(cd.getType())) {
                classDef = cd;
                break;
            }
        }
        assertNotNull(classDef);

        Method getEndpointsMethod = null;
        for (Method m : classDef.getVirtualMethods()) {
            if ("getEndpoints".equals(m.getName())) {
                getEndpointsMethod = m;
                break;
            }
        }
        assertNotNull(getEndpointsMethod);

        PrivacyBytecodeEngine.MethodTransformationResult result = DomainSinkholer.sinkholeMethod(getEndpointsMethod);
        assertTrue(result.modified);

        // Verify that strings were sinkholed to 127.0.0.1:0
        int sinkholeMatches = 0;
        for (Instruction inst : result.method.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && DomainSinkholer.SINKHOLE_DESTINATION.equals(((StringReference) ref).getString())) {
                    sinkholeMatches++;
                }
            }
        }
        assertEquals(2, sinkholeMatches);
    }

    private static final String MIXED_ENDPOINTS_SMALI =
            """
            .class public Ltest/MixedEndpointsClient;
            .super Ljava/lang/Object;
            .source "MixedEndpointsClient.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public getEndpoints()V
                .locals 3
                const-string v0, "https://andistreaminghub.amazon.com/v1/stream"
                const-string v1, "https://events.launchdarkly.com/mobile"
                const-string v2, "https://example.com/valid/api"
                return-void
            .end method
            """;

    @Test
    public void testSinkholeMethodWithAiAndTelemetry() throws Exception {
        File dexFile = assembleSmali(MIXED_ENDPOINTS_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        ClassDef classDef = null;
        for (ClassDef cd : dex.getClasses()) {
            if ("Ltest/MixedEndpointsClient;".equals(cd.getType())) {
                classDef = cd;
                break;
            }
        }
        assertNotNull(classDef);

        Method getEndpointsMethod = null;
        for (Method m : classDef.getVirtualMethods()) {
            if ("getEndpoints".equals(m.getName())) {
                getEndpointsMethod = m;
                break;
            }
        }
        assertNotNull(getEndpointsMethod);

        // Case 1: Both enabled (default)
        PrivacyStats stats1 = new PrivacyStats();
        PrivacyOptions options1 = PrivacyOptions.all();
        PrivacyBytecodeEngine.MethodTransformationResult result1 =
                DomainSinkholer.sinkholeMethod(getEndpointsMethod, stats1, options1);
        assertTrue(result1.modified);
        assertEquals(1, stats1.aiEndpointsSinkholed);
        assertEquals(1, stats1.telemetryDomainsSinkholed);

        int sinkholeCount = 0;
        int nonSinkholeCount = 0;
        for (Instruction inst : result1.method.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference) {
                    String str = ((StringReference) ref).getString();
                    if (DomainSinkholer.SINKHOLE_DESTINATION.equals(str)) {
                        sinkholeCount++;
                    } else if ("https://example.com/valid/api".equals(str)) {
                        nonSinkholeCount++;
                    }
                }
            }
        }
        assertEquals(2, sinkholeCount);
        assertEquals(1, nonSinkholeCount);

        // Case 2: AI disabled
        PrivacyStats stats2 = new PrivacyStats();
        PrivacyOptions options2 = PrivacyOptions.builder().setNullifyAiAssistants(false).build();
        PrivacyBytecodeEngine.MethodTransformationResult result2 =
                DomainSinkholer.sinkholeMethod(getEndpointsMethod, stats2, options2);
        assertTrue(result2.modified);
        assertEquals(0, stats2.aiEndpointsSinkholed);
        assertEquals(1, stats2.telemetryDomainsSinkholed);

        // Case 3: Telemetry disabled
        PrivacyStats stats3 = new PrivacyStats();
        PrivacyOptions options3 = PrivacyOptions.builder().setSinkholeTelemetryDomains(false).build();
        PrivacyBytecodeEngine.MethodTransformationResult result3 =
                DomainSinkholer.sinkholeMethod(getEndpointsMethod, stats3, options3);
        assertTrue(result3.modified);
        assertEquals(1, stats3.aiEndpointsSinkholed);
        assertEquals(0, stats3.telemetryDomainsSinkholed);
    }
}
