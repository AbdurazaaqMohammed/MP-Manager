package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;

/**
 * Represents a privacy or tracking threat identified during DEX/APK static analysis.
 */
public class DetectedThreat implements Serializable {

    public enum Category {
        HARDWARE_FINGERPRINT,
        SURVEILLANCE,
        TRACKER_SDK,
        LOG_LEAK
    }

    public enum ExcisionType {
        NOP_INVOKE,
        BLANK_RETURN_STRING,
        FORCE_BOOLEAN_TRUE,
        FORCE_INT_ZERO,
        MANIFEST_STRIP
    }

    public final Category category;
    public final String title;
    public final String description;
    public final String targetClass;
    public final String targetMethod;
    public final ExcisionType excisionType;
    public final boolean requiresRoot;
    public boolean selectedForExcision = true;
    public int occurrences = 1;

    public DetectedThreat(Category category, String title, String description,
                          String targetClass, String targetMethod,
                          ExcisionType excisionType, boolean requiresRoot) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.excisionType = excisionType;
        this.requiresRoot = requiresRoot;
    }

    public String getRootBadge() {
        return requiresRoot ? "[Requires Root]" : "[Non-Root]";
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s::%s) - %d occurrences",
                getRootBadge(), title, targetClass, targetMethod, occurrences);
    }
}
