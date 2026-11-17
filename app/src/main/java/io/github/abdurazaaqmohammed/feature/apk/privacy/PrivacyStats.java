package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Mutation statistics recorded during privacy sanitization passes.
 */
public class PrivacyStats implements Serializable {

    public int logCallsStripped = 0;
    public int telephonyGettersBlanked = 0;
    public int adIdsNeutralized = 0;
    public int devModeChecksMasked = 0;
    public int classesModified = 0;

    public int totalMutations() {
        return logCallsStripped + telephonyGettersBlanked + adIdsNeutralized + devModeChecksMasked;
    }

    public void merge(PrivacyStats other) {
        if (other == null) return;
        this.logCallsStripped += other.logCallsStripped;
        this.telephonyGettersBlanked += other.telephonyGettersBlanked;
        this.adIdsNeutralized += other.adIdsNeutralized;
        this.devModeChecksMasked += other.devModeChecksMasked;
        this.classesModified += other.classesModified;
    }

    @Override
    public String toString() {
        return String.format(
                "PrivacyStats[total=%d (logs=%d, telephony=%d, adIds=%d, devMode=%d), classesModified=%d]",
                totalMutations(), logCallsStripped, telephonyGettersBlanked,
                adIdsNeutralized, devModeChecksMasked, classesModified
        );
    }
}
