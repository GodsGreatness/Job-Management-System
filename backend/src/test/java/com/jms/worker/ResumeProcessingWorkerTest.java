package com.jms.worker;

import com.jms.domain.enums.*;
import com.jms.domain.model.Application;
import com.jms.domain.model.JobPosting;
import com.jms.domain.model.User;
import com.jms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ResumeProcessingWorkerTest {

    @Autowired private UserRepository userRepository;
    @Autowired private JobPostingRepository jobRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BackgroundJobRepository backgroundJobRepository;
    @Autowired private BackgroundJobQueueService queueService;
    @Autowired private WorkerPoolEngine workerPoolEngine;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        backgroundJobRepository.deleteAll();
        applicationRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldScoreResumeSkillsAndFanOutHighMatchNotification() {
        User employer = new User();
        employer.setEmail("emp@score.dev");
        employer.setPasswordHash("hash");
        employer.setFullName("Hiring Lead");
        employer.setRole(Role.EMPLOYER);
        employer = userRepository.save(employer);

        User candidate = new User();
        candidate.setEmail("cand@score.dev");
        candidate.setPasswordHash("hash");
        candidate.setFullName("Ada Lovelace");
        candidate.setRole(Role.CANDIDATE);
        candidate = userRepository.save(candidate);

        JobPosting job = new JobPosting();
        job.setTitle("Systems Engineer");
        job.setCompany("CoreLabs");
        job.setLocation("Remote");
        job.setRemote(true);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setDescription("Build concurrent backends.");
        job.setRequiredSkills("Java, Spring Boot, C++, PostgreSQL, Docker");
        job.setEmployer(employer);
        job = jobRepository.save(job);

        Application app = new Application();
        app.setJob(job);
        app.setCandidate(candidate);
        app.setResumeText("Senior engineer experienced in Java, Spring Boot, C++, and PostgreSQL systems with high-throughput distributed worker pools.");
        app.setCoverLetter("Excited to contribute to CoreLabs.");
        app = applicationRepository.save(app);

        queueService.enqueue(BackgroundJobType.PROCESS_RESUME, Map.of("applicationId", app.getId()), 9, null);

        // Drain resume worker + follow-up notification worker
        workerPoolEngine.processDueJobsSynchronously(5);
        workerPoolEngine.processDueJobsSynchronously(5);

        Application scored = applicationRepository.findById(app.getId()).orElseThrow();
        assertThat(scored.getAtsMatchScore()).isNotNull().isGreaterThanOrEqualTo(75);
        assertThat(scored.getMatchedSkills()).contains("Java", "Spring Boot", "C++", "PostgreSQL");
        assertThat(scored.getMissingSkills()).contains("Docker");

        // Verify employer got high-match alert notification
        var employerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(employer.getId());
        assertThat(employerNotifs).isNotEmpty();
        assertThat(employerNotifs.get(0).getCategory()).isEqualTo("ATS_MATCH");
    }
}
