package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Mutation statistics recorded during privacy sanitization passes across
 * Module A (Telemetry & Identification) and Module B (AI & Assistant Nullification).
 */
public class PrivacyStats implements Serializable {

    // ==========================================
    // Module A: Telemetry & Hardware Fingerprinting
    // ==========================================
    public int logCallsStripped = 0;
    public int telephonyGettersBlanked = 0;
    public int adIdsNeutralized = 0;
    public int devModeChecksMasked = 0;
    public int telemetryDomainsSinkholed = 0;
    public int backgroundWorkersNeutralized = 0;
    public int hardwareIdentifiersBlanked = 0;
    public int periodicWorkEnqueueSuppressed = 0;
    public int trackerClinitSanitized = 0;

    // ==========================================
    // Module B: Generative AI & Assistant Nullification
    // ==========================================
    public int aiFeatureGatesNeutralized = 0;
    public int aiEligibilityManagersStubbed = 0;
    public int aiTabIngressNeutralized = 0;
    public int aiEndpointsSinkholed = 0;

    // ==========================================
    // Audit & Structural Metrics
    // ==========================================
    public int deadCodeSkipped = 0;
    public int telemetryDeadCodeSkipped = 0;
    public int aiDeadCodeSkipped = 0;
    public int classesModified = 0;

    /**
     * Total count of mutations applied across Module A (Telemetry) only.
     */
    public int telemetryTotalMutations() {
        return logCallsStripped + telephonyGettersBlanked + adIdsNeutralized +
                devModeChecksMasked + telemetryDomainsSinkholed + backgroundWorkersNeutralized +
                hardwareIdentifiersBlanked + periodicWorkEnqueueSuppressed + trackerClinitSanitized;
    }

    /**
     * Total count of mutations applied across Module B (AI & Assistants) only.
     */
    public int aiTotalMutations() {
        return aiFeatureGatesNeutralized + aiEligibilityManagersStubbed +
                aiTabIngressNeutralized + aiEndpointsSinkholed;
    }

    /**
     * Total count of active bytecode mutations performed across all modules.
     * Dead-code skipped candidates and classes-modified counters are audit metrics
     * and are not counted as active mutations.
     */
    public int totalMutations() {
        return telemetryTotalMutations() + aiTotalMutations();
    }

    /**
     * Accumulates stats from another pass or DEX file into this instance.
     */
    public void merge(PrivacyStats other) {
        if (other == null) return;
        this.logCallsStripped += other.logCallsStripped;
        this.telephonyGettersBlanked += other.telephonyGettersBlanked;
        this.adIdsNeutralized += other.adIdsNeutralized;
        this.devModeChecksMasked += other.devModeChecksMasked;
        this.telemetryDomainsSinkholed += other.telemetryDomainsSinkholed;
        this.backgroundWorkersNeutralized += other.backgroundWorkersNeutralized;
        this.hardwareIdentifiersBlanked += other.hardwareIdentifiersBlanked;
        this.periodicWorkEnqueueSuppressed += other.periodicWorkEnqueueSuppressed;
        this.trackerClinitSanitized += other.trackerClinitSanitized;

        this.aiFeatureGatesNeutralized += other.aiFeatureGatesNeutralized;
        this.aiEligibilityManagersStubbed += other.aiEligibilityManagersStubbed;
        this.aiTabIngressNeutralized += other.aiTabIngressNeutralized;
        this.aiEndpointsSinkholed += other.aiEndpointsSinkholed;

        this.telemetryDeadCodeSkipped += other.telemetryDeadCodeSkipped;
        this.aiDeadCodeSkipped += other.aiDeadCodeSkipped;
        int totalDead = this.telemetryDeadCodeSkipped + this.aiDeadCodeSkipped;
        this.deadCodeSkipped = totalDead > 0 ? totalDead : (this.deadCodeSkipped + other.deadCodeSkipped);
        this.classesModified += other.classesModified;
    }

    @Override
    public String toString() {
        return String.format(
                "PrivacyStats[total=%d (telemetry=%d, ai=%d, deadCodeSkipped=%d), classesModified=%d]",
                totalMutations(), telemetryTotalMutations(), aiTotalMutations(),
                deadCodeSkipped, classesModified
        );
    }
}
