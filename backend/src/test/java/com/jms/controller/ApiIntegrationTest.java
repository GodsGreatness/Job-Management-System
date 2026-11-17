package com.jms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.ApplicationStatus;
import com.jms.domain.enums.EmploymentType;
import com.jms.domain.enums.Role;
import com.jms.dto.ApplicationDtos.CreateApplicationRequest;
import com.jms.dto.ApplicationDtos.UpdateApplicationStatusRequest;
import com.jms.dto.AuthDtos.RegisterRequest;
import com.jms.dto.InterviewDtos.ScheduleInterviewRequest;
import com.jms.dto.JobDtos.CreateJobRequest;
import com.jms.repository.*;
import com.jms.worker.WorkerPoolEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkerPoolEngine workerPoolEngine;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BackgroundJobRepository backgroundJobRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private JobPostingRepository jobRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        interviewRepository.deleteAll();
        notificationRepository.deleteAll();
        backgroundJobRepository.deleteAll();
        applicationRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullEndToEndHiringWorkflowWithRbacAndAsyncWorkers() throws Exception {
        // 1. Register Employer, Candidate, Recruiter, Admin
        String employerToken = registerAndGetToken("emp@it.dev", "Sarah Employer", Role.EMPLOYER);
        String candidateToken = registerAndGetToken("cand@it.dev", "Kofi Candidate", Role.CANDIDATE);
        String recruiterToken = registerAndGetToken("rec@it.dev", "David Recruiter", Role.RECRUITER);
        String adminToken = registerAndGetToken("admin@it.dev", "Root Admin", Role.ADMIN);

        // 2. RBAC check: Candidate cannot post jobs
        CreateJobRequest jobReq = new CreateJobRequest(
                "Java Distributed Systems Engineer", "ScaleCorp", "Infra", "Remote", true,
                EmploymentType.FULL_TIME, 100000, 140000,
                "Design fault-tolerant distributed workers.",
                "Java, Spring Boot, PostgreSQL, Redis, Docker"
        );

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobReq)))
                .andExpect(status().isForbidden());

        // 3. Employer creates job
        String jobJson = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + employerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Java Distributed Systems Engineer"))
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(jobJson).get("id").asLong();

        // 4. Candidate applies
        CreateApplicationRequest applyReq = new CreateApplicationRequest(
                jobId,
                "Built Java Spring Boot APIs backed by PostgreSQL, Redis, and Docker worker pools.",
                "Ready to ship production systems."
        );

        String appJson = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        long appId = objectMapper.readTree(appJson).get("id").asLong();

        // Duplicate application must return 409 Conflict
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isConflict());

        // 5. Run async workers deterministically
        workerPoolEngine.processDueJobsSynchronously(20);
        workerPoolEngine.processDueJobsSynchronously(20);

        // Verify ATS score populated on Candidate's application list
        String candAppsJson = mockMvc.perform(get("/api/applications")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode scoredApp = objectMapper.readTree(candAppsJson).get(0);
        assertThat(scoredApp.get("atsMatchScore").asInt()).isGreaterThanOrEqualTo(80);

        // 6. Recruiter schedules interview -> auto-transitions status to INTERVIEW
        ScheduleInterviewRequest intReq = new ScheduleInterviewRequest(
                appId, Instant.now().plus(1, ChronoUnit.DAYS), 45, "Technical Deep Dive", "https://meet.jms/1"
        );
        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("Technical Deep Dive"));

        // 7. Recruiter advances application to OFFER
        mockMvc.perform(patch("/api/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateApplicationStatusRequest(ApplicationStatus.OFFER, "Strong systems round"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER"));

        // 8. Admin inspects queue metrics & system analytics
        workerPoolEngine.processDueJobsSynchronously(20);
        mockMvc.perform(get("/api/admin/queue/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/analytics/overview?live=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(1))
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.scheduledInterviews").value(1));
    }

    private String registerAndGetToken(String email, String name, Role role) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "password123", name, role, "TestOrg", "Engineer", "Java");
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
