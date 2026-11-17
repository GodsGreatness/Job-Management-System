package com.jms.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import com.jms.dto.AnalyticsDtos.BackgroundJobResponse;
import com.jms.dto.AnalyticsDtos.QueueMetricsResponse;
import com.jms.exception.DomainException;
import com.jms.repository.BackgroundJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Transactional persistence manager for the priority job queue, optimistic job claiming,
 * exponential backoff retry scheduling, and Dead-Letter Queue (DLQ) replay.
 */
@Service
public class BackgroundJobQueueService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobQueueService.class);

    private final BackgroundJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final int defaultMaxAttempts;

    public BackgroundJobQueueService(
            BackgroundJobRepository jobRepository,
            ObjectMapper objectMapper,
            @Value("${jms.worker.default-max-attempts:3}") int defaultMaxAttempts) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    @Transactional
    public BackgroundJob enqueue(BackgroundJobType type,
                                 Map<String, Object> payload,
                                 int priority,
                                 String idempotencyKey) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return enqueueRaw(type, json, priority, idempotencyKey, defaultMaxAttempts);
        } catch (JsonProcessingException e) {
            throw DomainException.badRequest("Failed to serialize job payload: " + e.getMessage());
        }
    }

    @Transactional
    public BackgroundJob enqueueRaw(BackgroundJobType type,
                                    String rawJsonPayload,
                                    int priority,
                                    String idempotencyKey,
                                    int maxAttempts) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<BackgroundJob> existing = jobRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.debug("Skipping duplicate enqueue for idempotencyKey={}", idempotencyKey);
                return existing.get();
            }
        }

        BackgroundJob job = new BackgroundJob();
        job.setType(type);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setPriority(Math.max(1, Math.min(10, priority)));
        job.setPayload(rawJsonPayload);
        job.setIdempotencyKey((idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey);
        job.setMaxAttempts(Math.max(1, maxAttempts));
        job.setNextRunAt(Instant.now());

        BackgroundJob saved = jobRepository.save(job);
        log.info("Enqueued background job id={} type={} priority={}", saved.getId(), type, saved.getPriority());
        return saved;
    }

    /**
     * Atomically claims up to {@code maxBatch} due jobs in strict priority-then-FIFO order.
     */
    @Transactional
    public List<BackgroundJob> claimDueJobs(int maxBatch, String workerNodeId) {
        if (maxBatch <= 0) {
            return List.of();
        }
        List<BackgroundJob> candidates = jobRepository.findDueJobs(
                List.of(BackgroundJobStatus.QUEUED, BackgroundJobStatus.RETRYING),
                Instant.now(),
                PageRequest.of(0, maxBatch));

        List<BackgroundJob> claimed = new ArrayList<>();
        for (BackgroundJob job : candidates) {
            try {
                job.setStatus(BackgroundJobStatus.PROCESSING);
                job.setLockedBy(workerNodeId);
                job.setStartedAt(Instant.now());
                job.setAttempts(job.getAttempts() + 1);
                claimed.add(jobRepository.saveAndFlush(job));
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.debug("Job id={} claimed concurrently by another worker", job.getId());
            }
        }
        return claimed;
    }

    @Transactional
    public void markCompleted(Long jobId, long durationMs, String resultSummary) {
        BackgroundJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(BackgroundJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setExecutionDurationMs(durationMs);
        job.setResultSummary(resultSummary);
        job.setLastError(null);
        job.setLockedBy(null);
        jobRepository.save(job);
    }

    @Transactional
    public void markFailedOrRetry(Long jobId, long durationMs, String errorMessage, long baseBackoffMs) {
        BackgroundJob job = jobRepository.findById(jobId).orElseThrow();
        job.setExecutionDurationMs(durationMs);
        job.setLastError(truncate(errorMessage, 2000));
        job.setLockedBy(null);

        if (job.getAttempts() >= job.getMaxAttempts()) {
            job.setStatus(BackgroundJobStatus.DEAD_LETTER);
            job.setCompletedAt(Instant.now());
            log.warn("Job id={} type={} exhausted {} attempts -> moved to DEAD_LETTER queue. Error: {}",
                    job.getId(), job.getType(), job.getMaxAttempts(), errorMessage);
        } else {
            long exponent = Math.max(0, job.getAttempts() - 1);
            long delayMs = baseBackoffMs * (1L << Math.min(exponent, 10));
            job.setStatus(BackgroundJobStatus.RETRYING);
            job.setNextRunAt(Instant.now().plusMillis(delayMs));
            log.warn("Job id={} type={} failed attempt {}/{} -> retrying in {}ms. Error: {}",
                    job.getId(), job.getType(), job.getAttempts(), job.getMaxAttempts(), delayMs, errorMessage);
        }
        jobRepository.save(job);
    }

    @Transactional
    public BackgroundJobResponse replayDeadLetterJob(Long jobId) {
        BackgroundJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> DomainException.notFound("Background job not found: " + jobId));
        if (job.getStatus() != BackgroundJobStatus.DEAD_LETTER) {
            throw DomainException.badRequest("Only DEAD_LETTER jobs can be replayed (current status: " + job.getStatus() + ")");
        }
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setAttempts(0);
        job.setLastError(null);
        job.setCompletedAt(null);
        job.setLockedBy(null);
        job.setNextRunAt(Instant.now());
        BackgroundJob saved = jobRepository.save(job);
        log.info("Replayed DEAD_LETTER job id={} type={}", saved.getId(), saved.getType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BackgroundJobResponse> listJobs(BackgroundJobStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<BackgroundJob> jobs = (status == null)
                ? jobRepository.findAllByOrderByCreatedAtDesc(page)
                : jobRepository.findByStatusOrderByCreatedAtDesc(status, page);
        return jobs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QueueMetricsResponse getMetrics(int poolSize, int activeWorkers) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (BackgroundJobType type : BackgroundJobType.values()) {
            byType.put(type.name(), jobRepository.countByType(type));
        }
        Double avgMs = jobRepository.findAverageExecutionDurationMs();
        return new QueueMetricsResponse(
                poolSize,
                activeWorkers,
                jobRepository.countByStatus(BackgroundJobStatus.QUEUED),
                jobRepository.countByStatus(BackgroundJobStatus.PROCESSING),
                jobRepository.countByStatus(BackgroundJobStatus.RETRYING),
                jobRepository.countByStatus(BackgroundJobStatus.COMPLETED),
                jobRepository.countByStatus(BackgroundJobStatus.DEAD_LETTER),
                avgMs == null ? 0.0 : Math.round(avgMs * 100.0) / 100.0,
                byType,
                Instant.now()
        );
    }

    public BackgroundJobResponse toResponse(BackgroundJob job) {
        return new BackgroundJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getPriority(),
                job.getPayload(),
                job.getIdempotencyKey(),
                job.getAttempts(),
                job.getMaxAttempts(),
                job.getNextRunAt(),
                job.getLockedBy(),
                job.getExecutionDurationMs(),
                job.getLastError(),
                job.getResultSummary(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
