package com.jms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.model.BackgroundJob;
import com.jms.dto.AnalyticsDtos.*;
import com.jms.dto.AuthDtos.UserSummary;
import com.jms.repository.UserRepository;
import com.jms.service.AuthService;
import com.jms.worker.BackgroundJobQueueService;
import com.jms.worker.WorkerPoolEngine;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminQueueController {

    private final BackgroundJobQueueService queueService;
    private final WorkerPoolEngine workerPoolEngine;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminQueueController(BackgroundJobQueueService queueService,
                                WorkerPoolEngine workerPoolEngine,
                                UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.queueService = queueService;
        this.workerPoolEngine = workerPoolEngine;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/queue/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<QueueMetricsResponse> getQueueMetrics() {
        return ResponseEntity.ok(workerPoolEngine.getLiveMetrics());
    }

    @GetMapping("/queue/jobs")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<List<BackgroundJobResponse>> listQueueJobs(
            @RequestParam(required = false) BackgroundJobStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queueService.listJobs(status, limit));
    }

    @PostMapping("/queue/jobs/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackgroundJobResponse> triggerJob(@Valid @RequestBody TriggerJobRequest req) throws Exception {
        String payload = req.payload();
        if (req.simulateFailure()) {
            ObjectNode node = (payload == null || payload.isBlank())
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(payload);
            node.put("simulateFailure", true);
            payload = objectMapper.writeValueAsString(node);
        }
        BackgroundJob job = queueService.enqueueRaw(req.type(), payload, req.priority(), null, 3);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queueService.toResponse(job));
    }

    @PostMapping("/queue/jobs/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackgroundJobResponse> replayDeadLetterJob(@PathVariable Long id) {
        return ResponseEntity.ok(queueService.replayDeadLetterJob(id));
    }

    @PostMapping("/queue/flush")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> flushQueue() {
        workerPoolEngine.pollAndDispatch();
        return ResponseEntity.ok(Map.of("status", "dispatched", "metrics", workerPoolEngine.getLiveMetrics()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummary>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(AuthService::toSummary).toList());
    }
}
