package com.jms.config;

import com.jms.domain.enums.*;
import com.jms.domain.model.*;
import com.jms.repository.*;
import com.jms.worker.BackgroundJobQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Seeds realistic demo accounts, engineering job postings, applications, interviews,
 * and background worker jobs on initial boot so all 4 portals are immediately interactive.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final JobPostingRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobQueueService queueService;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;

    public DataSeeder(UserRepository userRepository,
                      JobPostingRepository jobRepository,
                      ApplicationRepository applicationRepository,
                      InterviewRepository interviewRepository,
                      BackgroundJobRepository backgroundJobRepository,
                      BackgroundJobQueueService queueService,
                      PasswordEncoder passwordEncoder,
                      @Value("${jms.seed.enabled:true}") boolean seedEnabled) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.backgroundJobRepository = backgroundJobRepository;
        this.queueService = queueService;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled || userRepository.count() > 0) {
            return;
        }

        String hash = passwordEncoder.encode("password123");

        User candidate = createUser("candidate@jms.dev", hash, "Kwame Mensah", Role.CANDIDATE,
                null, "Computer Engineering Student | Systems & Backend Developer",
                "Java, Spring Boot, C++, PostgreSQL, Redis, Docker, React, TypeScript");
        User candidate2 = createUser("ama.dev@jms.dev", hash, "Ama Boateng", Role.CANDIDATE,
                null, "Full-Stack Software Engineer",
                "React, TypeScript, Node.js, PostgreSQL, Docker, REST APIs");
        User employer = createUser("employer@jms.dev", hash, "Sarah Chen", Role.EMPLOYER,
                "HyperScale Systems", "VP of Platform Engineering", null);
        User recruiter = createUser("recruiter@jms.dev", hash, "David Osei", Role.RECRUITER,
                "TalentOps Global", "Lead Technical Recruiter", null);
        createUser("admin@jms.dev", hash, "System Administrator", Role.ADMIN,
                "JMS Platform Ops", "Infrastructure & Queue Operations", null);

        JobPosting job1 = createJob(employer,
                "Backend Systems Engineer (Java & Distributed Queues)",
                "HyperScale Systems", "Core Infrastructure", "Accra / Remote", true,
                EmploymentType.FULL_TIME, 95000, 135000,
                "Design and scale high-throughput asynchronous job pipelines, RESTful APIs, and fault-tolerant microservices.",
                "Java, Spring Boot, PostgreSQL, Redis, Docker, Concurrency");

        JobPosting job2 = createJob(employer,
                "Full-Stack Platform Engineer (React + Java)",
                "HyperScale Systems", "Product Engineering", "London / Hybrid", true,
                EmploymentType.FULL_TIME, 85000, 120000,
                "Own end-to-end features across our TypeScript/React portals and Spring Boot API services.",
                "React, TypeScript, Java, Spring Boot, PostgreSQL, REST APIs");

        JobPosting job3 = createJob(employer,
                "Systems & C++ Performance Engineer",
                "HyperScale Systems", "Runtime & Compilers", "New York, NY", false,
                EmploymentType.FULL_TIME, 125000, 165000,
                "Build low-latency scheduling engines, lock-free data structures, and empirical benchmarking suites.",
                "C++, Linux, Concurrency, Algorithms, Memory Management, Docker");

        createJob(employer,
                "Software Engineering Intern (Backend & Cloud)",
                "HyperScale Systems", "Cloud Platform", "Remote", true,
                EmploymentType.INTERNSHIP, 45000, 60000,
                "Collaborate with senior engineers on CI/CD pipelines, telemetry dashboards, and automated testing.",
                "Java, Git, SQL, Docker, Unit Testing");

        // Seed initial applications and kick off real background workers!
        Application app1 = new Application();
        app1.setJob(job1);
        app1.setCandidate(candidate);
        app1.setStatus(ApplicationStatus.SCREENING);
        app1.setResumeText("BSc Computer Engineering. Built C++17 CPU Process Scheduler, generic DSA Core library, and Java Spring Boot microservices using PostgreSQL, Redis, Docker, and multi-threaded concurrency.");
        app1.setCoverLetter("Excited to build resilient asynchronous worker pipelines and high-throughput Java backend systems.");
        app1 = applicationRepository.save(app1);

        Application app2 = new Application();
        app2.setJob(job2);
        app2.setCandidate(candidate2);
        app2.setStatus(ApplicationStatus.INTERVIEW);
        app2.setResumeText("3 years building full-stack web apps with React, TypeScript, REST APIs, PostgreSQL, and Docker containers.");
        app2.setCoverLetter("Passionate about developer experience and clean end-to-end product engineering.");
        app2 = applicationRepository.save(app2);

        // Seed an upcoming interview
        Interview interview = new Interview();
        interview.setApplication(app2);
        interview.setInterviewer(recruiter);
        interview.setScheduledAt(Instant.now().plus(2, ChronoUnit.DAYS));
        interview.setDurationMinutes(60);
        interview.setStage("Technical Architecture Round");
        interview.setMeetingLink("https://meet.jms.internal/tech-round-102");
        interview.setStatus(InterviewStatus.SCHEDULED);
        interviewRepository.save(interview);

        // Enqueue real background jobs to populate worker pool metrics
        queueService.enqueue(BackgroundJobType.PROCESS_RESUME, Map.of("applicationId", app1.getId()), 9, "seed-resume-" + app1.getId());
        queueService.enqueue(BackgroundJobType.PROCESS_RESUME, Map.of("applicationId", app2.getId()), 9, "seed-resume-" + app2.getId());
        queueService.enqueue(BackgroundJobType.SEND_EMAIL,
                Map.of("to", candidate.getEmail(), "subject", "Application Received: " + job1.getTitle(), "template", "APPLICATION_CONFIRMATION"),
                6, "seed-email-1");
        queueService.enqueue(BackgroundJobType.REFRESH_ANALYTICS, Map.of("reason", "initial_seed"), 4, "seed-analytics-1");

        // Seed one sample Dead-Letter Queue job so Admins can immediately test DLQ inspection & 1-click replay
        BackgroundJob dlqSample = new BackgroundJob();
        dlqSample.setType(BackgroundJobType.SEND_EMAIL);
        dlqSample.setStatus(BackgroundJobStatus.DEAD_LETTER);
        dlqSample.setPriority(7);
        dlqSample.setPayload("{\"to\":\"bounce-demo@external.invalid\",\"subject\":\"Offer Packet Dispatch\",\"template\":\"OFFER_LETTER\"}");
        dlqSample.setAttempts(3);
        dlqSample.setMaxAttempts(3);
        dlqSample.setNextRunAt(Instant.now());
        dlqSample.setCompletedAt(Instant.now());
        dlqSample.setExecutionDurationMs(42L);
        dlqSample.setLastError("SMTP 503 Service Unavailable after 3 exponential backoff attempts");
        backgroundJobRepository.save(dlqSample);

        log.info("Seeded demo accounts, 4 jobs, 2 applications, 1 interview, and background queue jobs.");
    }

    private User createUser(String email, String hash, String name, Role role,
                            String company, String headline, String skills) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setFullName(name);
        u.setRole(role);
        u.setCompanyName(company);
        u.setHeadline(headline);
        u.setSkills(skills);
        return userRepository.save(u);
    }

    private JobPosting createJob(User employer, String title, String company, String dept,
                                 String location, boolean remote, EmploymentType type,
                                 int minSalary, int maxSalary, String desc, String skills) {
        JobPosting job = new JobPosting();
        job.setEmployer(employer);
        job.setTitle(title);
        job.setCompany(company);
        job.setDepartment(dept);
        job.setLocation(location);
        job.setRemote(remote);
        job.setEmploymentType(type);
        job.setSalaryMin(minSalary);
        job.setSalaryMax(maxSalary);
        job.setDescription(desc);
        job.setRequiredSkills(skills);
        job.setStatus(JobStatus.OPEN);
        return jobRepository.save(job);
    }
}
