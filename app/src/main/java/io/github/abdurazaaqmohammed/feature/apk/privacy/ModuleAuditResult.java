package io.github.abdurazaaqmohammed.feature.apk.privacy;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result and status classification for a privacy/AI nullification module pass.
 */
public class ModuleAuditResult implements Serializable {

    public enum Status {
        SUCCESS,
        NOT_DETECTED,
        WARNING_ZERO_MUTATIONS,
        DEAD_CODE_SKIPPED
    }

    public String moduleName;
    public Status status;
    public int mutationsApplied;
    public int deadCodeSkipped;
    public String message;

    public ModuleAuditResult() {
        this.status = Status.NOT_DETECTED;
        this.moduleName = "";
        this.mutationsApplied = 0;
        this.deadCodeSkipped = 0;
        this.message = "";
    }

    public ModuleAuditResult(String moduleName, Status status) {
        this(moduleName, status, 0, 0, "");
    }

    public ModuleAuditResult(String moduleName, Status status, int mutationsApplied) {
        this(moduleName, status, mutationsApplied, 0, "");
    }

    public ModuleAuditResult(String moduleName, Status status, int mutationsApplied, int deadCodeSkipped, String message) {
        this.moduleName = moduleName != null ? moduleName : "";
        this.status = status != null ? status : Status.NOT_DETECTED;
        this.mutationsApplied = mutationsApplied;
        this.deadCodeSkipped = deadCodeSkipped;
        this.message = message != null ? message : "";
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleAuditResult that = (ModuleAuditResult) o;
        return mutationsApplied == that.mutationsApplied &&
                deadCodeSkipped == that.deadCodeSkipped &&
                Objects.equals(moduleName, that.moduleName) &&
                status == that.status &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, status, mutationsApplied, deadCodeSkipped, message);
    }

    @Override
    public String toString() {
        return String.format(
                "ModuleAuditResult[module='%s', status=%s, mutations=%d, deadCodeSkipped=%d, message='%s']",
                moduleName, status, mutationsApplied, deadCodeSkipped, message
        );
    }
}
