package com.jms.domain.model;

import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.enums.BackgroundJobType;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persistent background job record powering the asynchronous worker pool engine.
 * Supports priority ordering, delayed scheduling, exponential backoff retries,
 * optimistic locking for concurrent worker claiming, and Dead-Letter Queue (DLQ) replay.
 */
@Entity
@Table(name = "background_jobs", indexes = {
        @Index(name = "idx_bg_status_priority_next", columnList = "status, priority, nextRunAt"),
        @Index(name = "idx_bg_idempotency", columnList = "idempotencyKey", unique = true)
})
public class BackgroundJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BackgroundJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private BackgroundJobStatus status = BackgroundJobStatus.QUEUED;

    /** Higher number = higher priority (1=Low, 5=Normal, 10=Critical) */
    @Column(nullable = false)
    private int priority = 5;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(length = 160, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private int maxAttempts = 3;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(length = 100)
    private String lockedBy;

    private Instant startedAt;

    private Instant completedAt;

    private Long executionDurationMs;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.nextRunAt == null) {
            this.nextRunAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BackgroundJobType getType() { return type; }
    public void setType(BackgroundJobType type) { this.type = type; }

    public BackgroundJobStatus getStatus() { return status; }
    public void setStatus(BackgroundJobStatus status) { this.status = status; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
