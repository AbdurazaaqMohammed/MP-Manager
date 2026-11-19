package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Configuration options for the Dual-Module Privacy Sanitizer bytecode engine.
 * Covers Module A (Telemetry & Hardware Identification) and Module B (AI & Assistant Nullification).
 */
public class PrivacyOptions implements Serializable {

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
        private boolean stripLogging = true;
        private boolean emptyTelephonyIds = true;
        private boolean neutralizeAdIds = true;
        private boolean maskDeveloperMode = true;
        private boolean sinkholeTelemetryDomains = true;
        private boolean neutralizeBackgroundWorkers = true;

        private boolean nullifyAiAssistants = true;
        private boolean forceAiExperimentControl = true;
        private boolean stubAiEligibilityManagers = true;
        private boolean neutralizeAiTabIngress = true;

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
            return new PrivacyOptions(
                    stripLogging, emptyTelephonyIds, neutralizeAdIds,
                    maskDeveloperMode, sinkholeTelemetryDomains, neutralizeBackgroundWorkers,
                    nullifyAiAssistants, forceAiExperimentControl, stubAiEligibilityManagers,
                    neutralizeAiTabIngress
            );
        }
    }
}
