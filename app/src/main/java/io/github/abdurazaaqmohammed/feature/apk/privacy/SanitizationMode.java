package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Sanitization modes defining the aggressiveness and risk profile
 * for the privacy bytecode engine.
 */
public enum SanitizationMode implements Serializable {
    STANDARD(
            "Standard Mode",
            "Generally guarantees app remains stable, but may miss some targets in highly obfuscated or encrypted apps"
    ),
    AGGRESSIVE(
            "Aggressive Mode",
            "Generally will kill all telemetry, but might also kill your app. If you try this and your app doesn't launch, try standard mode"
    );

    public final String title;
    public final String description;

    SanitizationMode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
