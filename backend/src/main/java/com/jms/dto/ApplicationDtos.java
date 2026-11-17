package com.jms.dto;

import com.jms.domain.enums.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public record CreateApplicationRequest(
            @NotNull Long jobId,
            @NotBlank String resumeText,
            String coverLetter
    ) {}

    public record UpdateApplicationStatusRequest(
            @NotNull ApplicationStatus status,
            String recruiterNotes
    ) {}

    public record ApplicationResponse(
            Long id,
            Long jobId,
            String jobTitle,
            String company,
            Long candidateId,
            String candidateName,
            String candidateEmail,
            ApplicationStatus status,
            String resumeText,
            String coverLetter,
            Integer atsMatchScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            String recruiterNotes,
            Instant appliedAt,
            Instant updatedAt
    ) {}
}
