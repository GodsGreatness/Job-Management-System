package com.jms.controller;

import com.jms.domain.enums.EmploymentType;
import com.jms.domain.enums.JobStatus;
import com.jms.dto.JobDtos.*;
import com.jms.security.UserPrincipal;
import com.jms.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> searchJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(jobService.searchJobs(status, remote, employmentType, keyword));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<List<JobResponse>> getMyJobs(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(jobService.getEmployerJobs(actor.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<JobResponse> createJob(@AuthenticationPrincipal UserPrincipal actor,
                                                 @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(actor, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<JobResponse> updateJob(@AuthenticationPrincipal UserPrincipal actor,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody UpdateJobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(actor, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<Void> closeJob(@AuthenticationPrincipal UserPrincipal actor,
                                         @PathVariable Long id) {
        jobService.deleteJob(actor, id);
        return ResponseEntity.noContent().build();
    }
}
