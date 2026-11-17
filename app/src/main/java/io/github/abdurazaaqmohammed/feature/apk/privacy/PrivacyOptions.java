package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Configuration options for the Privacy Sanitizer bytecode engine.
 */
public class PrivacyOptions implements Serializable {

    /** Strip all android.util.Log calls (v, d, i, w, e, wtf, println). */
    public boolean stripLogging = true;

    /** Blank TelephonyManager identifiers (IMEI, Device ID, SIM serial, Phone number). */
    public boolean emptyTelephonyIds = true;

    /** Neutralize Google Advertising ID (GAID) and blank Settings.Secure android_id. */
    public boolean neutralizeAdIds = true;

    /** Mask Developer Options and ADB debugging checks to return 0 / disabled. */
    public boolean maskDeveloperMode = true;

    public PrivacyOptions() {
    }

    public PrivacyOptions(boolean stripLogging, boolean emptyTelephonyIds,
                          boolean neutralizeAdIds, boolean maskDeveloperMode) {
        this.stripLogging = stripLogging;
        this.emptyTelephonyIds = emptyTelephonyIds;
        this.neutralizeAdIds = neutralizeAdIds;
        this.maskDeveloperMode = maskDeveloperMode;
    }

    public static PrivacyOptions all() {
        return new PrivacyOptions(true, true, true, true);
    }
}
