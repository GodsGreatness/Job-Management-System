package com.jms.service;

import com.jms.domain.enums.ApplicationStatus;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.enums.JobStatus;
import com.jms.domain.enums.Role;
import com.jms.domain.model.Application;
import com.jms.domain.model.JobPosting;
import com.jms.domain.model.User;
import com.jms.dto.ApplicationDtos.*;
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
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobPostingRepository jobRepository;
    private final UserRepository userRepository;
    private final BackgroundJobQueueService queueService;

    public ApplicationService(ApplicationRepository applicationRepository,
                              JobPostingRepository jobRepository,
                              UserRepository userRepository,
                              BackgroundJobQueueService queueService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
    }

    @Transactional
    public ApplicationResponse submitApplication(UserPrincipal actor, CreateApplicationRequest req) {
        JobPosting job = jobRepository.findById(req.jobId())
                .orElseThrow(() -> DomainException.notFound("Job posting not found: " + req.jobId()));
        if (job.getStatus() != JobStatus.OPEN) {
            throw DomainException.badRequest("Cannot apply to a job with status " + job.getStatus());
        }
        if (applicationRepository.existsByJobIdAndCandidateId(job.getId(), actor.getId())) {
            throw DomainException.conflict("You have already applied to this position");
        }

        User candidate = userRepository.findById(actor.getId())
                .orElseThrow(() -> DomainException.notFound("Candidate not found"));

        Application app = new Application();
        app.setJob(job);
        app.setCandidate(candidate);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setResumeText(req.resumeText().trim());
        app.setCoverLetter(req.coverLetter() != null ? req.coverLetter().trim() : null);

        Application saved = applicationRepository.save(app);

        // Enqueue asynchronous background processing pipeline
        queueService.enqueue(
                BackgroundJobType.PROCESS_RESUME,
                Map.of("applicationId", saved.getId()),
                9,
                "resume-score-app-" + saved.getId()
        );
        queueService.enqueue(
                BackgroundJobType.SEND_EMAIL,
                Map.of(
                        "to", candidate.getEmail(),
                        "subject", "Application Received: " + job.getTitle() + " at " + job.getCompany(),
                        "template", "APPLICATION_CONFIRMATION"
                ),
                6,
                "app-confirm-email-" + saved.getId()
        );
        queueService.enqueue(
                BackgroundJobType.SEND_NOTIFICATION,
                Map.of(
                        "recipientId", job.getEmployer().getId(),
                        "title", "New Application Received",
                        "message", candidate.getFullName() + " applied for " + job.getTitle(),
                        "category", "APPLICATION"
                ),
                7,
                "new-app-notif-" + saved.getId()
        );
        queueService.enqueue(
                BackgroundJobType.REFRESH_ANALYTICS,
                Map.of("reason", "application_submitted", "applicationId", saved.getId()),
                3,
                null
        );

        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse updateStatus(UserPrincipal actor, Long applicationId, UpdateApplicationStatusRequest req) {
        Application app = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> DomainException.notFound("Application not found: " + applicationId));

        if (actor.getRole() == Role.CANDIDATE) {
            if (!app.getCandidate().getId().equals(actor.getId())) {
                throw DomainException.forbidden("You can only modify your own applications");
            }
            if (req.status() != ApplicationStatus.WITHDRAWN) {
                throw DomainException.forbidden("Candidates can only withdraw applications");
            }
        } else if (actor.getRole() == Role.EMPLOYER) {
            if (!app.getJob().getEmployer().getId().equals(actor.getId())) {
                throw DomainException.forbidden("You can only manage applications for your own job postings");
            }
        }

        ApplicationStatus previous = app.getStatus();
        if (previous == ApplicationStatus.WITHDRAWN || previous == ApplicationStatus.HIRED) {
            if (previous != req.status()) {
                throw DomainException.badRequest("Cannot transition application out of terminal state " + previous);
            }
        }

        app.setStatus(req.status());
        if (req.recruiterNotes() != null) {
            app.setRecruiterNotes(req.recruiterNotes());
        }
        Application saved = applicationRepository.save(app);

        // Notify candidate on status update
        if (previous != req.status()) {
            queueService.enqueue(
                    BackgroundJobType.SEND_NOTIFICATION,
                    Map.of(
                            "recipientId", saved.getCandidate().getId(),
                            "title", "Application Status Updated: " + req.status().name(),
                            "message", "Your application for " + saved.getJob().getTitle()
                                    + " at " + saved.getJob().getCompany() + " moved from "
                                    + previous + " to " + req.status(),
                            "category", "STATUS_UPDATE"
                    ),
                    8,
                    null
            );
            queueService.enqueue(
                    BackgroundJobType.SEND_EMAIL,
                    Map.of(
                            "to", saved.getCandidate().getEmail(),
                            "subject", "Status Update for " + saved.getJob().getTitle() + ": " + req.status(),
                            "template", "STATUS_" + req.status().name()
                    ),
                    6,
                    null
            );
            queueService.enqueue(
                    BackgroundJobType.REFRESH_ANALYTICS,
                    Map.of("reason", "application_status_changed", "applicationId", saved.getId()),
                    3,
                    null
            );
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsForActor(UserPrincipal actor, Long jobId) {
        if (jobId != null) {
            JobPosting job = jobRepository.findById(jobId)
                    .orElseThrow(() -> DomainException.notFound("Job not found: " + jobId));
            if (actor.getRole() == Role.CANDIDATE) {
                throw DomainException.forbidden("Candidates cannot list all applicants for a job");
            }
            if (actor.getRole() == Role.EMPLOYER && !job.getEmployer().getId().equals(actor.getId())) {
                throw DomainException.forbidden("Access denied for this job's applications");
            }
            return applicationRepository.findByJobIdOrderByAtsMatchScoreDescAppliedAtDesc(jobId)
                    .stream().map(this::toResponse).toList();
        }

        return switch (actor.getRole()) {
            case CANDIDATE -> applicationRepository.findByCandidateIdOrderByAppliedAtDesc(actor.getId())
                    .stream().map(this::toResponse).toList();
            case EMPLOYER -> applicationRepository.findByJobEmployerIdOrderByAppliedAtDesc(actor.getId())
                    .stream().map(this::toResponse).toList();
            case RECRUITER, ADMIN -> applicationRepository.findAllByOrderByAppliedAtDesc()
                    .stream().map(this::toResponse).toList();
        };
    }

    private ApplicationResponse toResponse(Application app) {
        return new ApplicationResponse(
                app.getId(),
                app.getJob().getId(),
                app.getJob().getTitle(),
                app.getJob().getCompany(),
                app.getCandidate().getId(),
                app.getCandidate().getFullName(),
                app.getCandidate().getEmail(),
                app.getStatus(),
                app.getResumeText(),
                app.getCoverLetter(),
                app.getAtsMatchScore(),
                ResumeProcessingWorker.parseSkills(app.getMatchedSkills()),
                ResumeProcessingWorker.parseSkills(app.getMissingSkills()),
                app.getRecruiterNotes(),
                app.getAppliedAt(),
                app.getUpdatedAt()
        );
    }
}
