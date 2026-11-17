package com.jms.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Asynchronous transactional email dispatch worker.
 * Supports simulated SMTP failure via payload flag {@code simulateFailure: true}
 * to demonstrate exponential backoff retries and Dead-Letter Queue routing.
 */
@Component
public class EmailWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);
    private final ObjectMapper objectMapper;

    public EmailWorker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BackgroundJobType getType() {
        return BackgroundJobType.SEND_EMAIL;
    }

    @Override
    public String handle(BackgroundJob job) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
        boolean simulateFailure = Boolean.TRUE.equals(payload.get("simulateFailure"));
        if (simulateFailure) {
            throw new IllegalStateException("Simulated SMTP gateway timeout (503 Service Unavailable)");
        }

        String to = String.valueOf(payload.getOrDefault("to", "unknown@example.com"));
        String subject = String.valueOf(payload.getOrDefault("subject", "JMS Notification"));
        String template = String.valueOf(payload.getOrDefault("template", "GENERIC"));

        log.info("[EmailWorker] Dispatched email template={} to={} subject=\"{}\"", template, to, subject);
        return "Email delivered to " + to + " [" + template + "]";
    }
}
