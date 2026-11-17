package com.jms.dto;

import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.enums.BackgroundJobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record SystemAnalyticsResponse(
            long totalUsers,
            long totalCandidates,
            long totalEmployers,
            long totalRecruiters,
            long totalJobs,
            long openJobs,
            long totalApplications,
            long scheduledInterviews,
            Double averageAtsScore,
            Map<String, Long> applicationsByStatus,
            Instant computedAt
    ) {}

    public record QueueMetricsResponse(
            int workerPoolSize,
            int activeWorkers,
            long queuedCount,
            long processingCount,
            long retryingCount,
            long completedCount,
            long deadLetterCount,
            Double averageExecutionMs,
            Map<String, Long> jobsByType,
            Instant snapshotAt
    ) {}

    public record BackgroundJobResponse(
            Long id,
            BackgroundJobType type,
            BackgroundJobStatus status,
            int priority,
            String payload,
            String idempotencyKey,
            int attempts,
            int maxAttempts,
            Instant nextRunAt,
            String lockedBy,
            Long executionDurationMs,
            String lastError,
            String resultSummary,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record TriggerJobRequest(
            @NotNull BackgroundJobType type,
            @Min(1) @Max(10) int priority,
            @NotNull String payload,
            boolean simulateFailure
    ) {}

    public record NotificationResponse(
            Long id,
            String title,
            String message,
            String category,
            boolean read,
            Instant createdAt
    ) {}
}
