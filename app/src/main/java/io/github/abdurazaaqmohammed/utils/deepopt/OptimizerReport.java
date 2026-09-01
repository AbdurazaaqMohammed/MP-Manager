package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.apk.APKLogger;

/**
 * Counters collected during a deep optimization run. Used for logging and for the
 * idempotency assertion (a second run on the output must show zero removals).
 */
public class OptimizerReport {

    public int classesTotal;
    public int classesRemoved;
    public int methodsRemoved;
    public int fieldsRemoved;
    public int entriesRemoved;
    public int filesRemoved;
    public int passesUsed;
    public boolean fixedPoint;
    public boolean reflectionMode;
    public boolean allResourcesKept;
    public boolean conservativeLock;

    private final APKLogger logger;

    public OptimizerReport(APKLogger logger) {
        this.logger = logger;
    }

    public void log(String message) {
        if (logger != null) logger.logMessage(message);
        else System.out.println(message);
    }

    public void logSummary() {
        log("Deep optimization report:");
        log("  Classes: kept " + (classesTotal - classesRemoved) + "/" + classesTotal + ", removed " + classesRemoved);
        log("  Methods removed: " + methodsRemoved);
        log("  Fields removed: " + fieldsRemoved);
        log("  Resources: " + entriesRemoved + " entries, " + filesRemoved + " files removed"
                + (allResourcesKept ? " (all-keep mode)" : ""));
        log("  Empty-method cascade passes: " + passesUsed);
        log("  Reflection mode: " + reflectionMode + ", fixed point: " + fixedPoint);
    }

    public boolean hasRemovals() {
        return classesRemoved > 0 || methodsRemoved > 0 || fieldsRemoved > 0
                || entriesRemoved > 0 || filesRemoved > 0;
    }
}