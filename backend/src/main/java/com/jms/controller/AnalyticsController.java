package com.jms.controller;

import com.jms.domain.enums.BackgroundJobType;
import com.jms.dto.AnalyticsDtos.BackgroundJobResponse;
import com.jms.dto.AnalyticsDtos.SystemAnalyticsResponse;
import com.jms.worker.AnalyticsWorker;
import com.jms.worker.BackgroundJobQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsWorker analyticsWorker;
    private final BackgroundJobQueueService queueService;

    public AnalyticsController(AnalyticsWorker analyticsWorker,
                               BackgroundJobQueueService queueService) {
        this.analyticsWorker = analyticsWorker;
        this.queueService = queueService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<SystemAnalyticsResponse> getOverview(@RequestParam(defaultValue = "false") boolean live) {
        SystemAnalyticsResponse response = live
                ? analyticsWorker.computeAndCacheSnapshot()
                : analyticsWorker.getLatestSnapshot();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ResponseEntity<BackgroundJobResponse> enqueueRefresh() {
        var job = queueService.enqueue(BackgroundJobType.REFRESH_ANALYTICS, Map.of("reason", "manual_refresh"), 7, null);
        return ResponseEntity.accepted().body(queueService.toResponse(job));
    }
}
