package io.github.abdurazaaqmohammed.feature.apk.privacy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying extended configuration options, metrics,
 * and audit enums for the dual-module sanitizer engine.
 */
public class PrivacyOptionsStatsTest {

    @Test
    public void testDefaultPrivacyOptions() {
        PrivacyOptions options = new PrivacyOptions();

        // Module A defaults
        assertTrue("stripLogging should default to true", options.stripLogging);
        assertTrue("emptyTelephonyIds should default to true", options.emptyTelephonyIds);
        assertTrue("neutralizeAdIds should default to true", options.neutralizeAdIds);
        assertTrue("maskDeveloperMode should default to true", options.maskDeveloperMode);
        assertTrue("sinkholeTelemetryDomains should default to true", options.sinkholeTelemetryDomains);
        assertTrue("neutralizeBackgroundWorkers should default to true", options.neutralizeBackgroundWorkers);

        // Module B defaults
        assertTrue("nullifyAiAssistants should default to true", options.nullifyAiAssistants);
        assertTrue("forceAiExperimentControl should default to true", options.forceAiExperimentControl);
        assertTrue("stubAiEligibilityManagers should default to true", options.stubAiEligibilityManagers);
        assertTrue("neutralizeAiTabIngress should default to true", options.neutralizeAiTabIngress);
        assertTrue("neutraliseAiTabIngress alias should default to true", options.neutraliseAiTabIngress);
    }

    @Test
    public void testPrivacyOptionsAllAndNone() {
        PrivacyOptions all = PrivacyOptions.all();
        assertTrue(all.stripLogging);
        assertTrue(all.emptyTelephonyIds);
        assertTrue(all.neutralizeAdIds);
        assertTrue(all.maskDeveloperMode);
        assertTrue(all.sinkholeTelemetryDomains);
        assertTrue(all.neutralizeBackgroundWorkers);
        assertTrue(all.nullifyAiAssistants);
        assertTrue(all.forceAiExperimentControl);
        assertTrue(all.stubAiEligibilityManagers);
        assertTrue(all.neutralizeAiTabIngress);

        PrivacyOptions none = PrivacyOptions.none();
        assertFalse(none.stripLogging);
        assertFalse(none.emptyTelephonyIds);
        assertFalse(none.neutralizeAdIds);
        assertFalse(none.maskDeveloperMode);
        assertFalse(none.sinkholeTelemetryDomains);
        assertFalse(none.neutralizeBackgroundWorkers);
        assertFalse(none.nullifyAiAssistants);
        assertFalse(none.forceAiExperimentControl);
        assertFalse(none.stubAiEligibilityManagers);
        assertFalse(none.neutralizeAiTabIngress);
    }

    @Test
    public void testPrivacyOptionsBuilder() {
        PrivacyOptions options = new PrivacyOptions.Builder()
                .setStripLogging(false)
                .setEmptyTelephonyIds(true)
                .setNeutralizeAdIds(false)
                .setMaskDeveloperMode(true)
                .setSinkholeTelemetryDomains(false)
                .setNeutralizeBackgroundWorkers(true)
                .setNullifyAiAssistants(false)
                .setForceAiExperimentControl(true)
                .setStubAiEligibilityManagers(false)
                .setNeutralizeAiTabIngress(true)
                .build();

        assertFalse(options.stripLogging);
        assertTrue(options.emptyTelephonyIds);
        assertFalse(options.neutralizeAdIds);
        assertTrue(options.maskDeveloperMode);
        assertFalse(options.sinkholeTelemetryDomains);
        assertTrue(options.neutralizeBackgroundWorkers);
        assertFalse(options.nullifyAiAssistants);
        assertTrue(options.forceAiExperimentControl);
        assertFalse(options.stubAiEligibilityManagers);
        assertTrue(options.neutralizeAiTabIngress);
    }

    @Test
    public void testPrivacyOptionsSerialization() throws Exception {
        PrivacyOptions options = new PrivacyOptions.Builder()
                .setStripLogging(false)
                .setNullifyAiAssistants(false)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(options);
        }

        PrivacyOptions deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (PrivacyOptions) ois.readObject();
        }

        assertNotNull(deserialized);
        assertFalse(deserialized.stripLogging);
        assertFalse(deserialized.nullifyAiAssistants);
        assertTrue(deserialized.emptyTelephonyIds);
        assertTrue(deserialized.neutralizeBackgroundWorkers);
    }

    @Test
    public void testPrivacyStatsDefaultsAndTotalMutations() {
        PrivacyStats stats = new PrivacyStats();
        assertEquals(0, stats.logCallsStripped);
        assertEquals(0, stats.telephonyGettersBlanked);
        assertEquals(0, stats.adIdsNeutralized);
        assertEquals(0, stats.devModeChecksMasked);
        assertEquals(0, stats.telemetryDomainsSinkholed);
        assertEquals(0, stats.backgroundWorkersNeutralized);
        assertEquals(0, stats.aiFeatureGatesNeutralized);
        assertEquals(0, stats.aiEligibilityManagersStubbed);
        assertEquals(0, stats.aiTabIngressNeutralized);
        assertEquals(0, stats.aiEndpointsSinkholed);
        assertEquals(0, stats.deadCodeSkipped);
        assertEquals(0, stats.telemetryDeadCodeSkipped);
        assertEquals(0, stats.aiDeadCodeSkipped);
        assertEquals(0, stats.classesModified);
        assertEquals(0, stats.totalMutations());

        stats.logCallsStripped = 2;
        stats.telephonyGettersBlanked = 3;
        stats.adIdsNeutralized = 4;
        stats.devModeChecksMasked = 5;
        stats.telemetryDomainsSinkholed = 6;
        stats.backgroundWorkersNeutralized = 7;
        stats.aiFeatureGatesNeutralized = 8;
        stats.aiEligibilityManagersStubbed = 9;
        stats.aiTabIngressNeutralized = 10;
        stats.aiEndpointsSinkholed = 11;

        // deadCodeSkipped and classesModified must NOT be included in totalMutations
        stats.telemetryDeadCodeSkipped = 40;
        stats.aiDeadCodeSkipped = 59;
        stats.deadCodeSkipped = 99;
        stats.classesModified = 50;

        assertEquals(2 + 3 + 4 + 5 + 6 + 7, stats.telemetryTotalMutations());
        assertEquals(8 + 9 + 10 + 11, stats.aiTotalMutations());
        assertEquals(65, stats.totalMutations());
    }

    @Test
    public void testPrivacyStatsMerge() {
        PrivacyStats s1 = new PrivacyStats();
        s1.logCallsStripped = 1;
        s1.telephonyGettersBlanked = 2;
        s1.adIdsNeutralized = 3;
        s1.devModeChecksMasked = 4;
        s1.telemetryDomainsSinkholed = 5;
        s1.backgroundWorkersNeutralized = 6;
        s1.aiFeatureGatesNeutralized = 7;
        s1.aiEligibilityManagersStubbed = 8;
        s1.aiTabIngressNeutralized = 9;
        s1.aiEndpointsSinkholed = 10;
        s1.telemetryDeadCodeSkipped = 5;
        s1.aiDeadCodeSkipped = 6;
        s1.deadCodeSkipped = 11;
        s1.classesModified = 12;

        PrivacyStats s2 = new PrivacyStats();
        s2.logCallsStripped = 20;
        s2.telephonyGettersBlanked = 19;
        s2.adIdsNeutralized = 18;
        s2.devModeChecksMasked = 17;
        s2.telemetryDomainsSinkholed = 16;
        s2.backgroundWorkersNeutralized = 15;
        s2.aiFeatureGatesNeutralized = 14;
        s2.aiEligibilityManagersStubbed = 13;
        s2.aiTabIngressNeutralized = 12;
        s2.aiEndpointsSinkholed = 11;
        s2.telemetryDeadCodeSkipped = 4;
        s2.aiDeadCodeSkipped = 6;
        s2.deadCodeSkipped = 10;
        s2.classesModified = 9;

        s1.merge(s2);

        assertEquals(21, s1.logCallsStripped);
        assertEquals(21, s1.telephonyGettersBlanked);
        assertEquals(21, s1.adIdsNeutralized);
        assertEquals(21, s1.devModeChecksMasked);
        assertEquals(21, s1.telemetryDomainsSinkholed);
        assertEquals(21, s1.backgroundWorkersNeutralized);
        assertEquals(21, s1.aiFeatureGatesNeutralized);
        assertEquals(21, s1.aiEligibilityManagersStubbed);
        assertEquals(21, s1.aiTabIngressNeutralized);
        assertEquals(21, s1.aiEndpointsSinkholed);
        assertEquals(9, s1.telemetryDeadCodeSkipped);
        assertEquals(12, s1.aiDeadCodeSkipped);
        assertEquals(21, s1.deadCodeSkipped);
        assertEquals(21, s1.classesModified);

        // Merging null should be safe no-op
        s1.merge(null);
        assertEquals(21, s1.logCallsStripped);
    }

    @Test
    public void testPrivacyStatsSerialization() throws Exception {
        PrivacyStats stats = new PrivacyStats();
        stats.backgroundWorkersNeutralized = 5;
        stats.aiFeatureGatesNeutralized = 3;
        stats.deadCodeSkipped = 2;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(stats);
        }

        PrivacyStats deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (PrivacyStats) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals(5, deserialized.backgroundWorkersNeutralized);
        assertEquals(3, deserialized.aiFeatureGatesNeutralized);
        assertEquals(2, deserialized.deadCodeSkipped);
        assertEquals(8, deserialized.totalMutations());
    }

    @Test
    public void testModuleAuditResultEnumValues() {
        List<ModuleAuditResult.Status> statuses = Arrays.asList(ModuleAuditResult.Status.values());
        assertTrue(statuses.contains(ModuleAuditResult.Status.SUCCESS));
        assertTrue(statuses.contains(ModuleAuditResult.Status.NOT_DETECTED));
        assertTrue(statuses.contains(ModuleAuditResult.Status.WARNING_ZERO_MUTATIONS));
        assertTrue(statuses.contains(ModuleAuditResult.Status.DEAD_CODE_SKIPPED));
        assertEquals(4, statuses.size());
    }

    @Test
    public void testModuleAuditResultClass() {
        ModuleAuditResult result = new ModuleAuditResult(
                "Module B: AI Nullification",
                ModuleAuditResult.Status.SUCCESS,
                15,
                2,
                "Successfully neutralized AI components"
        );

        assertEquals("Module B: AI Nullification", result.moduleName);
        assertEquals(ModuleAuditResult.Status.SUCCESS, result.status);
        assertEquals(15, result.mutationsApplied);
        assertEquals(2, result.deadCodeSkipped);
        assertEquals("Successfully neutralized AI components", result.message);
        assertTrue(result.isSuccess());

        ModuleAuditResult notDetected = new ModuleAuditResult(
                "Module B: AI Nullification",
                ModuleAuditResult.Status.NOT_DETECTED
        );
        assertFalse(notDetected.isSuccess());
        assertEquals(ModuleAuditResult.Status.NOT_DETECTED, notDetected.status);
    }
}
