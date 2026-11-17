package com.jms.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.*;
import com.jms.domain.model.BackgroundJob;
import com.jms.dto.AnalyticsDtos.SystemAnalyticsResponse;
import com.jms.repository.ApplicationRepository;
import com.jms.repository.InterviewRepository;
import com.jms.repository.JobPostingRepository;
import com.jms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous Analytics Aggregation Worker.
 * Computes platform-wide hiring funnel metrics and caches snapshots in memory
 * (and Redis when active in production profile) to offload dashboard read queries.
 */
@Component
public class AnalyticsWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWorker.class);
    private static final String REDIS_CACHE_KEY = "jms:analytics:system_snapshot";

    private final UserRepository userRepository;
    private final JobPostingRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private final AtomicReference<SystemAnalyticsResponse> cachedSnapshot = new AtomicReference<>();

    public AnalyticsWorker(UserRepository userRepository,
                           JobPostingRepository jobRepository,
                           ApplicationRepository applicationRepository,
                           InterviewRepository interviewRepository,
                           ObjectMapper objectMapper,
                           ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public BackgroundJobType getType() {
        return BackgroundJobType.REFRESH_ANALYTICS;
    }

    @Override
    @Transactional(readOnly = true)
    public String handle(BackgroundJob job) throws Exception {
        if (job.getPayload() != null && !job.getPayload().isBlank()) {
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
            if (Boolean.TRUE.equals(payload.get("simulateFailure"))) {
                throw new IllegalStateException("Simulated analytics aggregation timeout");
            }
        }
        SystemAnalyticsResponse snapshot = computeAndCacheSnapshot();
        return "Refreshed analytics snapshot: " + snapshot.totalJobs() + " jobs, "
                + snapshot.totalApplications() + " applications, avgATS=" + snapshot.averageAtsScore() + "%";
    }

    @Transactional(readOnly = true)
    public SystemAnalyticsResponse computeAndCacheSnapshot() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), applicationRepository.countByStatus(status));
        }

        Double avgScore = applicationRepository.findAverageAtsScore();
        SystemAnalyticsResponse snapshot = new SystemAnalyticsResponse(
                userRepository.count(),
                userRepository.countByRole(Role.CANDIDATE),
                userRepository.countByRole(Role.EMPLOYER),
                userRepository.countByRole(Role.RECRUITER),
                jobRepository.count(),
                jobRepository.countByStatus(JobStatus.OPEN),
                applicationRepository.count(),
                interviewRepository.countByStatus(InterviewStatus.SCHEDULED),
                avgScore == null ? 0.0 : Math.round(avgScore * 10.0) / 10.0,
                byStatus,
                Instant.now()
        );

        cachedSnapshot.set(snapshot);
        writeToRedisIfAvailable(snapshot);
        return snapshot;
    }

    public SystemAnalyticsResponse getLatestSnapshot() {
        SystemAnalyticsResponse snap = cachedSnapshot.get();
        if (snap == null) {
            return computeAndCacheSnapshot();
        }
        return snap;
    }

    private void writeToRedisIfAvailable(SystemAnalyticsResponse snapshot) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(REDIS_CACHE_KEY, json, Duration.ofMinutes(10));
        } catch (Exception ex) {
            log.debug("Redis cache write skipped: {}", ex.getMessage());
        }
    }
}
