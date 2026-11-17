package com.jms.dto;

import com.jms.domain.enums.InterviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class InterviewDtos {

    private InterviewDtos() {}

    public record ScheduleInterviewRequest(
            @NotNull Long applicationId,
            @NotNull Instant scheduledAt,
            @Min(15) @Max(240) int durationMinutes,
            @NotBlank String stage,
            String meetingLink
    ) {}

    public record UpdateInterviewRequest(
            InterviewStatus status,
            String feedback,
            @Min(1) @Max(5) Integer rating
    ) {}

    public record InterviewResponse(
            Long id,
            Long applicationId,
            Long jobId,
            String jobTitle,
            String candidateName,
            String candidateEmail,
            Long interviewerId,
            String interviewerName,
            Instant scheduledAt,
            int durationMinutes,
            String meetingLink,
            String stage,
            InterviewStatus status,
            String feedback,
            Integer rating,
            Instant createdAt
    ) {}
}
