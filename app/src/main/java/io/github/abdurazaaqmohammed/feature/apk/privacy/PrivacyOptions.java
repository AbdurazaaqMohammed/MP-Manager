package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Configuration options for the Dual-Module Privacy Sanitizer bytecode engine.
 * Covers Module A (Telemetry & Hardware Identification) and Module B (AI & Assistant Nullification),
 * with support for Standard (Safe) and Aggressive (Deep) sanitization modes.
 */
public class PrivacyOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Active sanitization mode (STANDARD or AGGRESSIVE). */
    public SanitizationMode mode = SanitizationMode.STANDARD;

    // ==========================================
    // Module A: Telemetry & Hardware Fingerprinting
    // ==========================================

    /** Strip all android.util.Log calls (v, d, i, w, e, wtf, println). */
    public boolean stripLogging = true;

    /** Blank TelephonyManager identifiers (IMEI, Device ID, SIM serial, Phone number). */
    public boolean emptyTelephonyIds = true;

    /** Neutralize Google Advertising ID (GAID) and blank Settings.Secure android_id. */
    public boolean neutralizeAdIds = true;

    /** Mask Developer Options and ADB debugging checks to return 0 / disabled. */
    public boolean maskDeveloperMode = true;

    /** Sinkhole known telemetry, analytics, and tracking URLs to 127.0.0.1:0. */
    public boolean sinkholeTelemetryDomains = true;

    /** Neutralize WorkManager telemetry workers to return Result.success() immediately. */
    public boolean neutralizeBackgroundWorkers = true;

    // ==========================================
    // Aggressive Mode Extensions (Hardware & WorkManager)
    // ==========================================

    /** Blank Settings.Secure android_id callsites to return empty string (""). */
    public boolean blankAndroidId = false;

    /** Blank Build.SERIAL and Build.getSerial() to return empty string (""). */
    public boolean blankHardwareSerial = false;

    /** Blank WifiInfo.getMacAddress() callsites to return dummy MAC address. */
    public boolean blankWifiMac = false;

    /** Suppress PeriodicWorkRequest / enqueuePeriodicWork bytecode callsites. */
    public boolean suppressPeriodicWorkEnqueue = false;

    /** Neutralize pure tracker static initializers (<clinit>). */
    public boolean nullifyTrackerClinit = false;

    /** Perform deep structural dataflow analysis across all classes. */
    public boolean deepStructuralAnalysis = false;

    // ==========================================
    // Module B: Generative AI & Assistant Nullification
    // ==========================================

    /** Nullify GenAI assistant components, state managers, and controllers. */
    public boolean nullifyAiAssistants = true;

    /** Force experiment weblab treatments (IMobileWeblab / LaunchDarkly) to Control ("C") or false. */
    public boolean forceAiExperimentControl = true;

    /** Stub AI eligibility manager getters and evaluation methods to false / NOT_ELIGIBLE. */
    public boolean stubAiEligibilityManagers = true;

    /** Neutralize UI tab items and activity ingress points for AI assistants. */
    public boolean neutralizeAiTabIngress = true;

    /** Alias for neutralizeAiTabIngress for alternate spelling compatibility. */
    public boolean neutraliseAiTabIngress = true;

    public PrivacyOptions() {
    }

    /**
     * Legacy 5-parameter constructor for backwards compatibility.
     */
    public PrivacyOptions(boolean stripLogging, boolean emptyTelephonyIds,
                          boolean neutralizeAdIds, boolean maskDeveloperMode,
                          boolean sinkholeTelemetryDomains) {
        this.stripLogging = stripLogging;
        this.emptyTelephonyIds = emptyTelephonyIds;
        this.neutralizeAdIds = neutralizeAdIds;
        this.maskDeveloperMode = maskDeveloperMode;
        this.sinkholeTelemetryDomains = sinkholeTelemetryDomains;
    }

    /**
     * Full-parameter constructor initializing both Module A and Module B options.
     */
    public PrivacyOptions(boolean stripLogging, boolean emptyTelephonyIds,
                          boolean neutralizeAdIds, boolean maskDeveloperMode,
                          boolean sinkholeTelemetryDomains, boolean neutralizeBackgroundWorkers,
                          boolean nullifyAiAssistants, boolean forceAiExperimentControl,
                          boolean stubAiEligibilityManagers, boolean neutralizeAiTabIngress) {
        this.stripLogging = stripLogging;
        this.emptyTelephonyIds = emptyTelephonyIds;
        this.neutralizeAdIds = neutralizeAdIds;
        this.maskDeveloperMode = maskDeveloperMode;
        this.sinkholeTelemetryDomains = sinkholeTelemetryDomains;
        this.neutralizeBackgroundWorkers = neutralizeBackgroundWorkers;
        this.nullifyAiAssistants = nullifyAiAssistants;
        this.forceAiExperimentControl = forceAiExperimentControl;
        this.stubAiEligibilityManagers = stubAiEligibilityManagers;
        this.neutralizeAiTabIngress = neutralizeAiTabIngress;
        this.neutraliseAiTabIngress = neutralizeAiTabIngress;
    }

    /**
     * Returns a standard mode configuration with safe defaults guaranteeing app stability.
     */
    public static PrivacyOptions standard() {
        PrivacyOptions options = new PrivacyOptions();
        options.mode = SanitizationMode.STANDARD;
        options.blankAndroidId = false;
        options.blankHardwareSerial = false;
        options.blankWifiMac = false;
        options.suppressPeriodicWorkEnqueue = false;
        options.deepStructuralAnalysis = false;
        options.stripLogging = true;
        options.emptyTelephonyIds = true;
        options.neutralizeAdIds = true;
        options.maskDeveloperMode = true;
        options.sinkholeTelemetryDomains = true;
        options.neutralizeBackgroundWorkers = true;
        options.nullifyAiAssistants = true;
        options.forceAiExperimentControl = true;
        options.stubAiEligibilityManagers = true;
        options.neutralizeAiTabIngress = true;
        options.neutraliseAiTabIngress = true;
        return options;
    }

    /**
     * Returns an aggressive mode configuration with zero-tolerance telemetry and deep analysis.
     */
    public static PrivacyOptions aggressive() {
        PrivacyOptions options = new PrivacyOptions();
        options.mode = SanitizationMode.AGGRESSIVE;
        options.blankAndroidId = true;
        options.blankHardwareSerial = true;
        options.blankWifiMac = true;
        options.suppressPeriodicWorkEnqueue = true;
        options.nullifyTrackerClinit = true;
        options.deepStructuralAnalysis = true;
        options.stripLogging = true;
        options.emptyTelephonyIds = true;
        options.neutralizeAdIds = true;
        options.maskDeveloperMode = true;
        options.sinkholeTelemetryDomains = true;
        options.neutralizeBackgroundWorkers = true;
        options.nullifyAiAssistants = true;
        options.forceAiExperimentControl = true;
        options.stubAiEligibilityManagers = true;
        options.neutralizeAiTabIngress = true;
        options.neutraliseAiTabIngress = true;
        return options;
    }

    public static PrivacyOptions all() {
        return new PrivacyOptions(true, true, true, true, true, true, true, true, true, true);
    }

    public static PrivacyOptions none() {
        return new PrivacyOptions(false, false, false, false, false, false, false, false, false, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for assembling granular PrivacyOptions.
     */
    public static class Builder {
        private SanitizationMode mode = SanitizationMode.STANDARD;
        private boolean stripLogging = true;
        private boolean emptyTelephonyIds = true;
        private boolean neutralizeAdIds = true;
        private boolean maskDeveloperMode = true;
        private boolean sinkholeTelemetryDomains = true;
        private boolean neutralizeBackgroundWorkers = true;

        private boolean blankAndroidId = false;
        private boolean blankHardwareSerial = false;
        private boolean blankWifiMac = false;
        private boolean suppressPeriodicWorkEnqueue = false;
        private boolean nullifyTrackerClinit = false;
        private boolean deepStructuralAnalysis = false;

        private boolean nullifyAiAssistants = true;
        private boolean forceAiExperimentControl = true;
        private boolean stubAiEligibilityManagers = true;
        private boolean neutralizeAiTabIngress = true;

        public Builder setMode(SanitizationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder setBlankAndroidId(boolean blankAndroidId) {
            this.blankAndroidId = blankAndroidId;
            return this;
        }

        public Builder setBlankHardwareSerial(boolean blankHardwareSerial) {
            this.blankHardwareSerial = blankHardwareSerial;
            return this;
        }

        public Builder setBlankWifiMac(boolean blankWifiMac) {
            this.blankWifiMac = blankWifiMac;
            return this;
        }

        public Builder setSuppressPeriodicWorkEnqueue(boolean suppressPeriodicWorkEnqueue) {
            this.suppressPeriodicWorkEnqueue = suppressPeriodicWorkEnqueue;
            return this;
        }

        public Builder setNullifyTrackerClinit(boolean nullifyTrackerClinit) {
            this.nullifyTrackerClinit = nullifyTrackerClinit;
            return this;
        }

        public Builder setDeepStructuralAnalysis(boolean deepStructuralAnalysis) {
            this.deepStructuralAnalysis = deepStructuralAnalysis;
            return this;
        }

        public Builder setStripLogging(boolean stripLogging) {
            this.stripLogging = stripLogging;
            return this;
        }

        public Builder setEmptyTelephonyIds(boolean emptyTelephonyIds) {
            this.emptyTelephonyIds = emptyTelephonyIds;
            return this;
        }

        public Builder setNeutralizeAdIds(boolean neutralizeAdIds) {
            this.neutralizeAdIds = neutralizeAdIds;
            return this;
        }

        public Builder setMaskDeveloperMode(boolean maskDeveloperMode) {
            this.maskDeveloperMode = maskDeveloperMode;
            return this;
        }

        public Builder setSinkholeTelemetryDomains(boolean sinkholeTelemetryDomains) {
            this.sinkholeTelemetryDomains = sinkholeTelemetryDomains;
            return this;
        }

        public Builder setNeutralizeBackgroundWorkers(boolean neutralizeBackgroundWorkers) {
            this.neutralizeBackgroundWorkers = neutralizeBackgroundWorkers;
            return this;
        }

        public Builder setNullifyAiAssistants(boolean nullifyAiAssistants) {
            this.nullifyAiAssistants = nullifyAiAssistants;
            return this;
        }

        public Builder setForceAiExperimentControl(boolean forceAiExperimentControl) {
            this.forceAiExperimentControl = forceAiExperimentControl;
            return this;
        }

        public Builder setStubAiEligibilityManagers(boolean stubAiEligibilityManagers) {
            this.stubAiEligibilityManagers = stubAiEligibilityManagers;
            return this;
        }

        public Builder setNeutralizeAiTabIngress(boolean neutralizeAiTabIngress) {
            this.neutralizeAiTabIngress = neutralizeAiTabIngress;
            return this;
        }

        public Builder setNeutraliseAiTabIngress(boolean neutraliseAiTabIngress) {
            this.neutralizeAiTabIngress = neutraliseAiTabIngress;
            return this;
        }

        public PrivacyOptions build() {
            PrivacyOptions options = new PrivacyOptions(
                    stripLogging, emptyTelephonyIds, neutralizeAdIds,
                    maskDeveloperMode, sinkholeTelemetryDomains, neutralizeBackgroundWorkers,
                    nullifyAiAssistants, forceAiExperimentControl, stubAiEligibilityManagers,
                    neutralizeAiTabIngress
            );
            options.mode = mode;
            options.blankAndroidId = blankAndroidId;
            options.blankHardwareSerial = blankHardwareSerial;
            options.blankWifiMac = blankWifiMac;
            options.suppressPeriodicWorkEnqueue = suppressPeriodicWorkEnqueue;
            options.nullifyTrackerClinit = nullifyTrackerClinit;
            options.deepStructuralAnalysis = deepStructuralAnalysis;
            return options;
        }
    }
}
