package com.jms.controller;

import com.jms.dto.ApplicationDtos.*;
import com.jms.security.UserPrincipal;
import com.jms.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApplicationResponse> submitApplication(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.submitApplication(actor, request));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) Long jobId) {
        return ResponseEntity.ok(applicationService.getApplicationsForActor(actor, jobId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(actor, id, request));
    }
}
