package com.jms.worker;

import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import com.jms.repository.BackgroundJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkerPoolEngineTest {

    @Autowired
    private BackgroundJobQueueService queueService;

    @Autowired
    private WorkerPoolEngine workerPoolEngine;

    @Autowired
    private BackgroundJobRepository jobRepository;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    @Test
    void shouldClaimJobsInStrictPriorityOrder() {
        BackgroundJob low = queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", "low@test.dev", "subject", "Low"), 2, null);
        BackgroundJob high = queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", "high@test.dev", "subject", "High"), 10, null);
        BackgroundJob med = queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", "med@test.dev", "subject", "Medium"), 5, null);

        List<BackgroundJob> claimed = queueService.claimDueJobs(3, "test-worker");
        assertThat(claimed).extracting(BackgroundJob::getId)
                .containsExactly(high.getId(), med.getId(), low.getId());
    }

    @Test
    void shouldEnforceIdempotencyKeyDeduplication() {
        BackgroundJob first = queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", "user@test.dev", "subject", "Welcome"), 5, "unique-welcome-42");
        BackgroundJob second = queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", "user@test.dev", "subject", "Welcome"), 5, "unique-welcome-42");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRetryWithExponentialBackoffThenMoveToDeadLetterAndAllowReplay() {
        BackgroundJob failing = queueService.enqueue(
                BackgroundJobType.SEND_EMAIL,
                Map.of("to", "fail@test.dev", "subject", "Retry Test", "simulateFailure", true),
                8,
                null
        );

        // Attempt 1 -> RETRYING with backoff
        assertThat(workerPoolEngine.processDueJobsSynchronously(5)).isEqualTo(1);
        BackgroundJob afterAttempt1 = jobRepository.findById(failing.getId()).orElseThrow();
        assertThat(afterAttempt1.getStatus()).isEqualTo(BackgroundJobStatus.RETRYING);
        assertThat(afterAttempt1.getAttempts()).isEqualTo(1);
        assertThat(afterAttempt1.getNextRunAt()).isAfter(Instant.now().minusSeconds(1));

        // Fast-forward nextRunAt for Attempt 2
        afterAttempt1.setNextRunAt(Instant.now().minusSeconds(1));
        jobRepository.save(afterAttempt1);
        assertThat(workerPoolEngine.processDueJobsSynchronously(5)).isEqualTo(1);
        BackgroundJob afterAttempt2 = jobRepository.findById(failing.getId()).orElseThrow();
        assertThat(afterAttempt2.getStatus()).isEqualTo(BackgroundJobStatus.RETRYING);
        assertThat(afterAttempt2.getAttempts()).isEqualTo(2);

        // Fast-forward nextRunAt for Attempt 3 -> DEAD_LETTER
        afterAttempt2.setNextRunAt(Instant.now().minusSeconds(1));
        jobRepository.save(afterAttempt2);
        assertThat(workerPoolEngine.processDueJobsSynchronously(5)).isEqualTo(1);
        BackgroundJob afterAttempt3 = jobRepository.findById(failing.getId()).orElseThrow();
        assertThat(afterAttempt3.getStatus()).isEqualTo(BackgroundJobStatus.DEAD_LETTER);
        assertThat(afterAttempt3.getAttempts()).isEqualTo(3);
        assertThat(afterAttempt3.getLastError()).contains("Simulated SMTP gateway timeout");

        // Fix payload and replay from DLQ -> COMPLETED
        afterAttempt3.setPayload("{\"to\":\"recovered@test.dev\",\"subject\":\"Recovered\",\"simulateFailure\":false}");
        jobRepository.save(afterAttempt3);

        var replayed = queueService.replayDeadLetterJob(afterAttempt3.getId());
        assertThat(replayed.status()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(replayed.attempts()).isZero();

        assertThat(workerPoolEngine.processDueJobsSynchronously(5)).isEqualTo(1);
        BackgroundJob finalState = jobRepository.findById(failing.getId()).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(BackgroundJobStatus.COMPLETED);
        assertThat(finalState.getResultSummary()).contains("recovered@test.dev");
    }
}
