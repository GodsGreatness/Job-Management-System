package com.jms.service;

import com.jms.domain.enums.ApplicationStatus;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.enums.InterviewStatus;
import com.jms.domain.enums.Role;
import com.jms.domain.model.Application;
import com.jms.domain.model.Interview;
import com.jms.domain.model.User;
import com.jms.dto.InterviewDtos.*;
import com.jms.exception.DomainException;
import com.jms.repository.ApplicationRepository;
import com.jms.repository.InterviewRepository;
import com.jms.repository.UserRepository;
import com.jms.security.UserPrincipal;
import com.jms.worker.BackgroundJobQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final BackgroundJobQueueService queueService;

    public InterviewService(InterviewRepository interviewRepository,
                            ApplicationRepository applicationRepository,
                            UserRepository userRepository,
                            BackgroundJobQueueService queueService) {
        this.interviewRepository = interviewRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
    }

    @Transactional
    public InterviewResponse scheduleInterview(UserPrincipal actor, ScheduleInterviewRequest req) {
        Application app = applicationRepository.findDetailedById(req.applicationId())
                .orElseThrow(() -> DomainException.notFound("Application not found: " + req.applicationId()));

        if (actor.getRole() == Role.EMPLOYER && !app.getJob().getEmployer().getId().equals(actor.getId())) {
            throw DomainException.forbidden("You can only schedule interviews for your own job postings");
        }

        User interviewer = userRepository.findById(actor.getId())
                .orElseThrow(() -> DomainException.notFound("Interviewer not found"));

        Interview interview = new Interview();
        interview.setApplication(app);
        interview.setInterviewer(interviewer);
        interview.setScheduledAt(req.scheduledAt());
        interview.setDurationMinutes(req.durationMinutes());
        interview.setStage(req.stage().trim());
        interview.setMeetingLink(req.meetingLink() != null && !req.meetingLink().isBlank()
                ? req.meetingLink().trim()
                : "https://meet.jms.internal/room-" + app.getId());
        interview.setStatus(InterviewStatus.SCHEDULED);

        if (app.getStatus() == ApplicationStatus.SUBMITTED || app.getStatus() == ApplicationStatus.SCREENING) {
            app.setStatus(ApplicationStatus.INTERVIEW);
            applicationRepository.save(app);
        }

        Interview saved = interviewRepository.save(interview);

        queueService.enqueue(
                BackgroundJobType.SEND_EMAIL,
                Map.of(
                        "to", app.getCandidate().getEmail(),
                        "subject", "Interview Scheduled: " + req.stage() + " for " + app.getJob().getTitle(),
                        "template", "INTERVIEW_INVITE"
                ),
                9,
                "interview-email-" + saved.getId()
        );
        queueService.enqueue(
                BackgroundJobType.SEND_NOTIFICATION,
                Map.of(
                        "recipientId", app.getCandidate().getId(),
                        "title", "Interview Scheduled (" + req.stage() + ")",
                        "message", "Your " + req.stage() + " interview for " + app.getJob().getTitle()
                                + " is scheduled with " + interviewer.getFullName(),
                        "category", "INTERVIEW"
                ),
                9,
                "interview-notif-" + saved.getId()
        );
        queueService.enqueue(
                BackgroundJobType.REFRESH_ANALYTICS,
                Map.of("reason", "interview_scheduled", "interviewId", saved.getId()),
                3,
                null
        );

        return toResponse(saved);
    }

    @Transactional
    public InterviewResponse updateInterview(UserPrincipal actor, Long interviewId, UpdateInterviewRequest req) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> DomainException.notFound("Interview not found: " + interviewId));

        if (req.status() != null) interview.setStatus(req.status());
        if (req.feedback() != null) interview.setFeedback(req.feedback());
        if (req.rating() != null) interview.setRating(req.rating());

        Interview saved = interviewRepository.save(interview);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> getInterviews(UserPrincipal actor) {
        List<Interview> list = switch (actor.getRole()) {
            case CANDIDATE -> interviewRepository.findByApplicationCandidateIdOrderByScheduledAtAsc(actor.getId());
            case EMPLOYER -> interviewRepository.findByInterviewerIdOrderByScheduledAtAsc(actor.getId());
            case RECRUITER, ADMIN -> interviewRepository.findAllByOrderByScheduledAtDesc();
        };
        return list.stream().map(this::toResponse).toList();
    }

    private InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(
                i.getId(),
                i.getApplication().getId(),
                i.getApplication().getJob().getId(),
                i.getApplication().getJob().getTitle(),
                i.getApplication().getCandidate().getFullName(),
                i.getApplication().getCandidate().getEmail(),
                i.getInterviewer().getId(),
                i.getInterviewer().getFullName(),
                i.getScheduledAt(),
                i.getDurationMinutes(),
                i.getMeetingLink(),
                i.getStage(),
                i.getStatus(),
                i.getFeedback(),
                i.getRating(),
                i.getCreatedAt()
        );
    }
}
