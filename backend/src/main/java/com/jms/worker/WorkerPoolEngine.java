package com.jms.worker;

import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import com.jms.dto.AnalyticsDtos.QueueMetricsResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded Background Job Worker Pool Engine.
 * Dispatches queued jobs in priority order across a bounded thread pool,
 * enforces exponential backoff on failures, routes exhausted jobs to the DLQ,
 * and tracks live worker utilization telemetry.
 */
@Component
public class WorkerPoolEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkerPoolEngine.class);

    private final BackgroundJobQueueService queueService;
    private final Map<BackgroundJobType, JobHandler> handlers = new EnumMap<>(BackgroundJobType.class);
    private final ExecutorService executor;
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicInteger threadSequence = new AtomicInteger(1);

    private final boolean enabled;
    private final int poolSize;
    private final long baseBackoffMs;

    public WorkerPoolEngine(
            BackgroundJobQueueService queueService,
            List<JobHandler> handlerList,
            @Value("${jms.worker.enabled:true}") boolean enabled,
            @Value("${jms.worker.pool-size:4}") int poolSize,
            @Value("${jms.worker.base-backoff-ms:1500}") long baseBackoffMs) {
        this.queueService = queueService;
        this.enabled = enabled;
        this.poolSize = Math.max(1, poolSize);
        this.baseBackoffMs = Math.max(100, baseBackoffMs);

        for (JobHandler handler : handlerList) {
            this.handlers.put(handler.getType(), handler);
        }

        this.executor = Executors.newFixedThreadPool(this.poolSize, runnable -> {
            Thread t = new Thread(runnable, "jms-worker-" + threadSequence.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        log.info("WorkerPoolEngine initialized with poolSize={}, handlers={}, enabled={}",
                this.poolSize, this.handlers.keySet(), this.enabled);
    }

    @Scheduled(fixedDelayString = "${jms.worker.poll-interval-ms:1000}")
    public void pollAndDispatch() {
        if (!enabled) {
            return;
        }
        int availableSlots = poolSize - activeWorkers.get();
        if (availableSlots <= 0) {
            return;
        }

        List<BackgroundJob> claimed = queueService.claimDueJobs(availableSlots, "dispatcher-pool");
        for (BackgroundJob job : claimed) {
            activeWorkers.incrementAndGet();
            executor.submit(() -> {
                try {
                    executeJob(job);
                } finally {
                    activeWorkers.decrementAndGet();
                }
            });
        }
    }

    /**
     * Deterministic synchronous drain used by automated tests and manual admin flushes.
     */
    public int processDueJobsSynchronously(int maxJobs) {
        List<BackgroundJob> claimed = queueService.claimDueJobs(maxJobs, "sync-runner");
        for (BackgroundJob job : claimed) {
            executeJob(job);
        }
        return claimed.size();
    }

    private void executeJob(BackgroundJob job) {
        long startNanos = System.nanoTime();
        String workerName = Thread.currentThread().getName();
        try {
            JobHandler handler = handlers.get(job.getType());
            if (handler == null) {
                throw new IllegalStateException("No JobHandler registered for type: " + job.getType());
            }
            String summary = handler.handle(job);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            queueService.markCompleted(job.getId(), durationMs, summary);
            log.info("[{}] Completed job id={} type={} in {}ms: {}",
                    workerName, job.getId(), job.getType(), durationMs, summary);
        } catch (Exception ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            queueService.markFailedOrRetry(job.getId(), durationMs, msg, baseBackoffMs);
        }
    }

    public QueueMetricsResponse getLiveMetrics() {
        return queueService.getMetrics(poolSize, activeWorkers.get());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WorkerPoolEngine gracefully...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
