package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit report produced by PrivacyScannerEngine containing threat taxonomy and risk scores.
 */
public class PrivacyAuditReport implements Serializable {

    public enum RiskLevel {
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }

    public int overallScore = 0; // 0 to 100
    public int hardwareScore = 0;
    public int surveillanceScore = 0;
    public int trackerScore = 0;
    public int logLeakScore = 0;

    public int totalClassesScanned = 0;
    public int invasiveClassesCount = 0;

    public final List<DetectedThreat> threats = new ArrayList<>();

    // ==========================================
    // Multi-DEX Sanitization & Audit Results
    // ==========================================
    public ModuleAuditResult telemetryModuleResult = new ModuleAuditResult("Module A: Telemetry", ModuleAuditResult.Status.NOT_DETECTED);
    public ModuleAuditResult aiModuleResult = new ModuleAuditResult("Module B: AI & Assistants", ModuleAuditResult.Status.NOT_DETECTED);
    public PrivacyStats sanitizationStats = new PrivacyStats();
    public ModuleAuditResult.Status overallSanitizationStatus = ModuleAuditResult.Status.NOT_DETECTED;

    public boolean isSanitizationSuccessful() {
        return overallSanitizationStatus == ModuleAuditResult.Status.SUCCESS;
    }

    public ModuleAuditResult.Status getOverallSanitizationStatus() {
        return overallSanitizationStatus;
    }

    public PrivacyStats getSanitizationStats() {
        return sanitizationStats;
    }

    public ModuleAuditResult getTelemetryModuleResult() {
        return telemetryModuleResult;
    }

    public ModuleAuditResult getAiModuleResult() {
        return aiModuleResult;
    }

    public RiskLevel getRiskLevel() {
        if (overallScore >= 75) return RiskLevel.CRITICAL;
        if (overallScore >= 50) return RiskLevel.HIGH;
        if (overallScore >= 25) return RiskLevel.MODERATE;
        return RiskLevel.LOW;
    }

    public void addThreat(DetectedThreat threat) {
        for (DetectedThreat existing : threats) {
            if (existing.category == threat.category
                    && existing.targetClass.equals(threat.targetClass)
                    && existing.targetMethod.equals(threat.targetMethod)
                    && existing.title.equals(threat.title)) {
                existing.occurrences += threat.occurrences;
                return;
            }
        }
        threats.add(threat);
    }

    public void computeScores() {
        int hwCount = 0;
        int survCount = 0;
        int trackCount = 0;
        int logCount = 0;

        for (DetectedThreat t : threats) {
            switch (t.category) {
                case HARDWARE_FINGERPRINT -> hwCount += t.occurrences;
                case SURVEILLANCE -> survCount += t.occurrences;
                case TRACKER_SDK -> trackCount += t.occurrences;
                case LOG_LEAK -> logCount += t.occurrences;
            }
        }

        hardwareScore = Math.min(100, hwCount * 25);
        surveillanceScore = Math.min(100, survCount * 20);
        trackerScore = Math.min(100, trackCount * 15);
        logLeakScore = Math.min(100, logCount * 2);

        // Weighted overall score
        overallScore = (int) Math.round(
                (hardwareScore * 0.35) +
                (trackerScore * 0.30) +
                (surveillanceScore * 0.25) +
                (logLeakScore * 0.10)
        );
    }
}
