package io.github.abdurazaaqmohammed.feature.apk.privacy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying SanitizationMode enum values, title/description strings,
 * standard vs aggressive factory defaults in PrivacyOptions, builder integration,
 * and serialization round-trip.
 */
public class SanitizationModeTest {

    @Test
    public void testSanitizationModeEnumConstantsAndDescriptions() {
        assertEquals("Standard Mode", SanitizationMode.STANDARD.title);
        assertEquals(
                "Generally guarantees app remains stable, but may miss some targets in highly obfuscated or encrypted apps",
                SanitizationMode.STANDARD.description
        );
        assertEquals("Standard Mode", SanitizationMode.STANDARD.getTitle());
        assertEquals(
                "Generally guarantees app remains stable, but may miss some targets in highly obfuscated or encrypted apps",
                SanitizationMode.STANDARD.getDescription()
        );

        assertEquals("Aggressive Mode", SanitizationMode.AGGRESSIVE.title);
        assertEquals(
                "Generally will kill all telemetry, but might also kill your app. If you try this and your app doesn't launch, try standard mode",
                SanitizationMode.AGGRESSIVE.description
        );
        assertEquals("Aggressive Mode", SanitizationMode.AGGRESSIVE.getTitle());
        assertEquals(
                "Generally will kill all telemetry, but might also kill your app. If you try this and your app doesn't launch, try standard mode",
                SanitizationMode.AGGRESSIVE.getDescription()
        );
    }

    @Test
    public void testPrivacyOptionsStandardDefaults() {
        PrivacyOptions standard = PrivacyOptions.standard();

        assertEquals(SanitizationMode.STANDARD, standard.mode);
        assertFalse("blankAndroidId must be false in standard mode", standard.blankAndroidId);
        assertFalse("blankHardwareSerial must be false in standard mode", standard.blankHardwareSerial);
        assertFalse("blankWifiMac must be false in standard mode", standard.blankWifiMac);
        assertFalse("suppressPeriodicWorkEnqueue must be false in standard mode", standard.suppressPeriodicWorkEnqueue);
        assertFalse("deepStructuralAnalysis must be false in standard mode", standard.deepStructuralAnalysis);

        assertTrue("stripLogging must be true in standard mode", standard.stripLogging);
        assertTrue("emptyTelephonyIds must be true in standard mode", standard.emptyTelephonyIds);
        assertTrue("neutralizeAdIds must be true in standard mode", standard.neutralizeAdIds);
        assertTrue("sinkholeTelemetryDomains must be true in standard mode", standard.sinkholeTelemetryDomains);
        assertTrue("neutralizeBackgroundWorkers must be true in standard mode", standard.neutralizeBackgroundWorkers);
        assertTrue("nullifyAiAssistants must be true in standard mode", standard.nullifyAiAssistants);

        // Check additional standard flags
        assertTrue(standard.maskDeveloperMode);
        assertTrue(standard.forceAiExperimentControl);
        assertTrue(standard.stubAiEligibilityManagers);
        assertTrue(standard.neutralizeAiTabIngress);
    }

    @Test
    public void testPrivacyOptionsAggressiveDefaults() {
        PrivacyOptions aggressive = PrivacyOptions.aggressive();

        assertEquals(SanitizationMode.AGGRESSIVE, aggressive.mode);
        assertTrue("blankAndroidId must be true in aggressive mode", aggressive.blankAndroidId);
        assertTrue("blankHardwareSerial must be true in aggressive mode", aggressive.blankHardwareSerial);
        assertTrue("blankWifiMac must be true in aggressive mode", aggressive.blankWifiMac);
        assertTrue("suppressPeriodicWorkEnqueue must be true in aggressive mode", aggressive.suppressPeriodicWorkEnqueue);
        assertTrue("deepStructuralAnalysis must be true in aggressive mode", aggressive.deepStructuralAnalysis);

        assertTrue("stripLogging must be true in aggressive mode", aggressive.stripLogging);
        assertTrue("emptyTelephonyIds must be true in aggressive mode", aggressive.emptyTelephonyIds);
        assertTrue("neutralizeAdIds must be true in aggressive mode", aggressive.neutralizeAdIds);
        assertTrue("sinkholeTelemetryDomains must be true in aggressive mode", aggressive.sinkholeTelemetryDomains);
        assertTrue("neutralizeBackgroundWorkers must be true in aggressive mode", aggressive.neutralizeBackgroundWorkers);
        assertTrue("nullifyAiAssistants must be true in aggressive mode", aggressive.nullifyAiAssistants);
        assertTrue("maskDeveloperMode must be true in aggressive mode", aggressive.maskDeveloperMode);
        assertTrue("forceAiExperimentControl must be true in aggressive mode", aggressive.forceAiExperimentControl);
        assertTrue("stubAiEligibilityManagers must be true in aggressive mode", aggressive.stubAiEligibilityManagers);
        assertTrue("neutralizeAiTabIngress must be true in aggressive mode", aggressive.neutralizeAiTabIngress);
        assertTrue("neutraliseAiTabIngress alias must be true in aggressive mode", aggressive.neutraliseAiTabIngress);
    }

    @Test
    public void testPrivacyOptionsBuilderIntegration() {
        PrivacyOptions options = new PrivacyOptions.Builder()
                .setMode(SanitizationMode.AGGRESSIVE)
                .setBlankAndroidId(true)
                .setBlankHardwareSerial(true)
                .setBlankWifiMac(true)
                .setSuppressPeriodicWorkEnqueue(true)
                .setDeepStructuralAnalysis(true)
                .setStripLogging(false)
                .setEmptyTelephonyIds(true)
                .setNeutralizeAdIds(true)
                .setSinkholeTelemetryDomains(true)
                .setNeutralizeBackgroundWorkers(true)
                .setNullifyAiAssistants(false)
                .build();

        assertEquals(SanitizationMode.AGGRESSIVE, options.mode);
        assertTrue(options.blankAndroidId);
        assertTrue(options.blankHardwareSerial);
        assertTrue(options.blankWifiMac);
        assertTrue(options.suppressPeriodicWorkEnqueue);
        assertTrue(options.deepStructuralAnalysis);
        assertFalse(options.stripLogging);
        assertTrue(options.emptyTelephonyIds);
        assertTrue(options.neutralizeAdIds);
        assertTrue(options.sinkholeTelemetryDomains);
        assertTrue(options.neutralizeBackgroundWorkers);
        assertFalse(options.nullifyAiAssistants);
    }

    @Test
    public void testSerializationRoundTrip() throws Exception {
        PrivacyOptions aggressive = PrivacyOptions.aggressive();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(aggressive);
        }

        PrivacyOptions deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (PrivacyOptions) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals(SanitizationMode.AGGRESSIVE, deserialized.mode);
        assertTrue(deserialized.blankAndroidId);
        assertTrue(deserialized.blankHardwareSerial);
        assertTrue(deserialized.blankWifiMac);
        assertTrue(deserialized.suppressPeriodicWorkEnqueue);
        assertTrue(deserialized.deepStructuralAnalysis);
        assertTrue(deserialized.stripLogging);
        assertTrue(deserialized.neutralizeBackgroundWorkers);
        assertTrue(deserialized.nullifyAiAssistants);

        // Also verify standard mode serialization round-trip
        PrivacyOptions standard = PrivacyOptions.standard();
        ByteArrayOutputStream baosStandard = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baosStandard)) {
            oos.writeObject(standard);
        }

        PrivacyOptions deserializedStandard;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baosStandard.toByteArray()))) {
            deserializedStandard = (PrivacyOptions) ois.readObject();
        }

        assertNotNull(deserializedStandard);
        assertEquals(SanitizationMode.STANDARD, deserializedStandard.mode);
        assertFalse(deserializedStandard.blankAndroidId);
        assertFalse(deserializedStandard.blankHardwareSerial);
        assertFalse(deserializedStandard.blankWifiMac);
        assertFalse(deserializedStandard.suppressPeriodicWorkEnqueue);
        assertFalse(deserializedStandard.deepStructuralAnalysis);
        assertTrue(deserializedStandard.stripLogging);
    }

    @Test
    public void testThreatUncheckedOptionFiltering() {
        // Aggressive mode: when hardware fingerprint threats unchecked, all HW IDs disabled
        PrivacyOptions aggressiveOptions = PrivacyOptions.aggressive();
        // simulate unchecking HARDWARE_FINGERPRINT
        aggressiveOptions.emptyTelephonyIds = false;
        aggressiveOptions.neutralizeAdIds = false;
        aggressiveOptions.blankAndroidId = false;
        aggressiveOptions.blankHardwareSerial = false;
        aggressiveOptions.blankWifiMac = false;

        assertFalse(aggressiveOptions.emptyTelephonyIds);
        assertFalse(aggressiveOptions.neutralizeAdIds);
        assertFalse(aggressiveOptions.blankAndroidId);
        assertFalse(aggressiveOptions.blankHardwareSerial);
        assertFalse(aggressiveOptions.blankWifiMac);
        // Tracker SDK and Log still enabled
        assertTrue(aggressiveOptions.stripLogging);
        assertTrue(aggressiveOptions.sinkholeTelemetryDomains);
        assertTrue(aggressiveOptions.neutralizeBackgroundWorkers);

        // Standard mode: when log leaks unchecked
        PrivacyOptions standardOptions = PrivacyOptions.standard();
        standardOptions.stripLogging = false;
        assertFalse(standardOptions.stripLogging);
        assertTrue(standardOptions.emptyTelephonyIds);
        assertTrue(standardOptions.neutralizeAdIds);
    }
}
