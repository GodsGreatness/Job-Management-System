package com.jms.dto;

import com.jms.domain.enums.EmploymentType;
import com.jms.domain.enums.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

public final class JobDtos {

    private JobDtos() {}

    public record CreateJobRequest(
            @NotBlank String title,
            @NotBlank String company,
            String department,
            @NotBlank String location,
            boolean remote,
            @NotNull EmploymentType employmentType,
            @PositiveOrZero Integer salaryMin,
            @PositiveOrZero Integer salaryMax,
            @NotBlank String description,
            @NotBlank String requiredSkills
    ) {}

    public record UpdateJobRequest(
            String title,
            String department,
            String location,
            Boolean remote,
            EmploymentType employmentType,
            Integer salaryMin,
            Integer salaryMax,
            String description,
            String requiredSkills,
            JobStatus status
    ) {}

    public record JobResponse(
            Long id,
            String title,
            String company,
            String department,
            String location,
            boolean remote,
            EmploymentType employmentType,
            Integer salaryMin,
            Integer salaryMax,
            String description,
            List<String> requiredSkills,
            JobStatus status,
            Long employerId,
            String employerName,
            long applicantCount,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
