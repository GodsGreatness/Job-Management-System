package com.jms.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.Application;
import com.jms.domain.model.BackgroundJob;
import com.jms.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Asynchronous ATS Resume Processing & Skill Match Scoring Worker.
 * Parses candidate resume and cover letter text against a job posting's required skills,
 * computes a deterministic 0-100% ATS match score, persists matched/missing skills,
 * and triggers a high-priority recruiter alert when match score >= 75%.
 */
@Component
public class ResumeProcessingWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessingWorker.class);

    private final ApplicationRepository applicationRepository;
    private final BackgroundJobQueueService queueService;
    private final ObjectMapper objectMapper;

    public ResumeProcessingWorker(ApplicationRepository applicationRepository,
                                  BackgroundJobQueueService queueService,
                                  ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.queueService = queueService;
        this.objectMapper = objectMapper;
    }

    @Override
    public BackgroundJobType getType() {
        return BackgroundJobType.PROCESS_RESUME;
    }

    @Override
    @Transactional
    public String handle(BackgroundJob job) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
        if (Boolean.TRUE.equals(payload.get("simulateFailure"))) {
            throw new IllegalStateException("Simulated resume parser extraction error");
        }

        Number appIdNum = (Number) payload.get("applicationId");
        if (appIdNum == null) {
            throw new IllegalArgumentException("Missing required field: applicationId");
        }

        Application application = applicationRepository.findDetailedById(appIdNum.longValue())
                .orElseThrow(() -> new IllegalStateException("Application not found: " + appIdNum));

        List<String> requiredSkills = parseSkills(application.getJob().getRequiredSkills());
        String corpus = ((application.getResumeText() == null ? "" : application.getResumeText()) + " "
                + (application.getCoverLetter() == null ? "" : application.getCoverLetter())).toLowerCase(Locale.ROOT);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : requiredSkills) {
            if (containsSkill(corpus, skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int score;
        if (requiredSkills.isEmpty()) {
            score = 70;
        } else {
            double skillRatio = (double) matched.size() / requiredSkills.size();
            int baseScore = (int) Math.round(skillRatio * 90.0);
            int depthBonus = Math.min(10, corpus.length() / 15);
            score = Math.max(0, Math.min(100, baseScore + depthBonus));
        }

        application.setAtsMatchScore(score);
        application.setMatchedSkills(String.join(", ", matched));
        application.setMissingSkills(String.join(", ", missing));
        applicationRepository.save(application);

        // Fan-out notification to Employer if strong candidate match
        if (score >= 75) {
            queueService.enqueue(
                    BackgroundJobType.SEND_NOTIFICATION,
                    Map.of(
                            "recipientId", application.getJob().getEmployer().getId(),
                            "title", "High-Match Candidate (" + score + "%)",
                            "message", application.getCandidate().getFullName() + " scored " + score
                                    + "% ATS match for " + application.getJob().getTitle()
                                    + " (matched: " + String.join(", ", matched) + ")",
                            "category", "ATS_MATCH"
                    ),
                    8,
                    "high-match-notif-app-" + application.getId()
            );
        }

        log.info("[ResumeProcessingWorker] Scored applicationId={} score={}% matched={}/{}",
                application.getId(), score, matched.size(), requiredSkills.size());

        return "ATS Score=" + score + "% (" + matched.size() + "/" + requiredSkills.size() + " required skills matched)";
    }

    public static List<String> parseSkills(String rawSkills) {
        if (rawSkills == null || rawSkills.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawSkills.split("[,;\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private boolean containsSkill(String normalizedCorpus, String skill) {
        String normalizedSkill = skill.toLowerCase(Locale.ROOT).trim();
        if (normalizedSkill.isEmpty()) {
            return false;
        }
        // Handle symbols like C++, C#, Node.js cleanly using boundary-safe check
        String escaped = Pattern.quote(normalizedSkill);
        Pattern p = Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])");
        return p.matcher(normalizedCorpus).find() || normalizedCorpus.contains(normalizedSkill);
    }
}
