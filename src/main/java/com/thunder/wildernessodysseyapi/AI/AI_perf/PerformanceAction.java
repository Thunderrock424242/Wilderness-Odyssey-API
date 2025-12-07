package com.thunder.wildernessodysseyapi.AI.AI_perf;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a concrete, human-approvable performance action derived from AI advice.
 */
public class PerformanceAction {
    private final String id;
    private final String subsystem;
    private final String summary;
    private final String detail;
    private final int durationSeconds;
    private final int severity;
    private PerformanceActionStatus status;
    private final Instant createdAt;
    private final String rollbackOfId;

    public PerformanceAction(String id, String subsystem, String summary, String detail, int durationSeconds,
                             int severity) {
        this(id, subsystem, summary, detail, durationSeconds, severity,
                PerformanceActionStatus.PENDING, Instant.now(), null);
    }

    public PerformanceAction(String id, String subsystem, String summary, String detail, int durationSeconds,
                             int severity, PerformanceActionStatus status, Instant createdAt, String rollbackOfId) {
        this.id = Objects.requireNonNull(id, "id");
        this.subsystem = Objects.requireNonNull(subsystem, "subsystem");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.durationSeconds = durationSeconds;
        this.severity = severity;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.rollbackOfId = rollbackOfId;
    }

    public String getId() {
        return id;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetail() {
        return detail;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getSeverity() {
        return severity;
    }

    public PerformanceActionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRollback() {
        return rollbackOfId != null;
    }

    public Optional<String> getRollbackOfId() {
        return Optional.ofNullable(rollbackOfId);
    }

    public void markApproved() {
        status = PerformanceActionStatus.APPROVED;
    }

    public void markApplied() {
        status = PerformanceActionStatus.APPLIED;
    }

    public void markRejected() {
        status = PerformanceActionStatus.REJECTED;
    }

    public void markExpired() {
        status = PerformanceActionStatus.EXPIRED;
    }
}
