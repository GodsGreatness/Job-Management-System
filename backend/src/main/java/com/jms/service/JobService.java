package com.jms.service;

import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.enums.EmploymentType;
import com.jms.domain.enums.JobStatus;
import com.jms.domain.enums.Role;
import com.jms.domain.model.JobPosting;
import com.jms.domain.model.User;
import com.jms.dto.JobDtos.*;
import com.jms.exception.DomainException;
import com.jms.repository.ApplicationRepository;
import com.jms.repository.JobPostingRepository;
import com.jms.repository.UserRepository;
import com.jms.security.UserPrincipal;
import com.jms.worker.BackgroundJobQueueService;
import com.jms.worker.ResumeProcessingWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class JobService {

    private final JobPostingRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final BackgroundJobQueueService queueService;

    public JobService(JobPostingRepository jobRepository,
                      ApplicationRepository applicationRepository,
                      UserRepository userRepository,
                      BackgroundJobQueueService queueService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> searchJobs(JobStatus status, Boolean remote, EmploymentType employmentType, String keyword) {
        return jobRepository.searchJobs(status, remote, employmentType, keyword)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getEmployerJobs(Long employerId) {
        return jobRepository.findByEmployerIdOrderByCreatedAtDesc(employerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(Long id) {
        JobPosting job = jobRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Job posting not found: " + id));
        return toResponse(job);
    }

    @Transactional
    public JobResponse createJob(UserPrincipal actor, CreateJobRequest req) {
        if (req.salaryMin() != null && req.salaryMax() != null && req.salaryMin() > req.salaryMax()) {
            throw DomainException.badRequest("salaryMin cannot exceed salaryMax");
        }
        User employer = userRepository.findById(actor.getId())
                .orElseThrow(() -> DomainException.notFound("Employer user not found"));

        JobPosting job = new JobPosting();
        job.setTitle(req.title().trim());
        job.setCompany(req.company().trim());
        job.setDepartment(req.department());
        job.setLocation(req.location().trim());
        job.setRemote(req.remote());
        job.setEmploymentType(req.employmentType());
        job.setSalaryMin(req.salaryMin());
        job.setSalaryMax(req.salaryMax());
        job.setDescription(req.description().trim());
        job.setRequiredSkills(req.requiredSkills().trim());
        job.setStatus(JobStatus.OPEN);
        job.setEmployer(employer);

        JobPosting saved = jobRepository.save(job);
        queueService.enqueue(BackgroundJobType.REFRESH_ANALYTICS, Map.of("reason", "job_created", "jobId", saved.getId()), 3, null);
        return toResponse(saved);
    }

    @Transactional
    public JobResponse updateJob(UserPrincipal actor, Long jobId, UpdateJobRequest req) {
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> DomainException.notFound("Job posting not found: " + jobId));
        enforceJobOwnershipOrPrivileged(actor, job);

        if (req.title() != null && !req.title().isBlank()) job.setTitle(req.title().trim());
        if (req.department() != null) job.setDepartment(req.department());
        if (req.location() != null && !req.location().isBlank()) job.setLocation(req.location().trim());
        if (req.remote() != null) job.setRemote(req.remote());
        if (req.employmentType() != null) job.setEmploymentType(req.employmentType());
        if (req.salaryMin() != null) job.setSalaryMin(req.salaryMin());
        if (req.salaryMax() != null) job.setSalaryMax(req.salaryMax());
        if (req.description() != null && !req.description().isBlank()) job.setDescription(req.description().trim());
        if (req.requiredSkills() != null && !req.requiredSkills().isBlank()) job.setRequiredSkills(req.requiredSkills().trim());
        if (req.status() != null) job.setStatus(req.status());

        JobPosting saved = jobRepository.save(job);
        queueService.enqueue(BackgroundJobType.REFRESH_ANALYTICS, Map.of("reason", "job_updated", "jobId", saved.getId()), 2, null);
        return toResponse(saved);
    }

    @Transactional
    public void deleteJob(UserPrincipal actor, Long jobId) {
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> DomainException.notFound("Job posting not found: " + jobId));
        enforceJobOwnershipOrPrivileged(actor, job);
        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);
    }

    private void enforceJobOwnershipOrPrivileged(UserPrincipal actor, JobPosting job) {
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.RECRUITER) {
            return;
        }
        if (!job.getEmployer().getId().equals(actor.getId())) {
            throw DomainException.forbidden("You can only modify job postings created by your organization");
        }
    }

    private JobResponse toResponse(JobPosting job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getDepartment(),
                job.getLocation(),
                job.isRemote(),
                job.getEmploymentType(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getDescription(),
                ResumeProcessingWorker.parseSkills(job.getRequiredSkills()),
                job.getStatus(),
                job.getEmployer().getId(),
                job.getEmployer().getFullName(),
                applicationRepository.countByJobId(job.getId()),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
