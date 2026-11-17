package com.jms.controller;

import com.jms.dto.InterviewDtos.*;
import com.jms.security.UserPrincipal;
import com.jms.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<InterviewResponse> scheduleInterview(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody ScheduleInterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.scheduleInterview(actor, request));
    }

    @GetMapping
    public ResponseEntity<List<InterviewResponse>> getInterviews(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(interviewService.getInterviews(actor));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<InterviewResponse> updateInterview(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody UpdateInterviewRequest request) {
        return ResponseEntity.ok(interviewService.updateInterview(actor, id, request));
    }
}
