package com.jms.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import com.jms.domain.model.Notification;
import com.jms.domain.model.User;
import com.jms.repository.NotificationRepository;
import com.jms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Asynchronous Application Notification Fan-Out Worker.
 * Persists structured in-app notifications for candidates, employers, and recruiters.
 */
@Component
public class ApplicationNotificationWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationNotificationWorker.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ApplicationNotificationWorker(NotificationRepository notificationRepository,
                                         UserRepository userRepository,
                                         ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public BackgroundJobType getType() {
        return BackgroundJobType.SEND_NOTIFICATION;
    }

    @Override
    @Transactional
    public String handle(BackgroundJob job) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
        if (Boolean.TRUE.equals(payload.get("simulateFailure"))) {
            throw new IllegalStateException("Simulated notification delivery broker failure");
        }

        Number recipientIdNum = (Number) payload.get("recipientId");
        if (recipientIdNum == null) {
            throw new IllegalArgumentException("Missing required field: recipientId");
        }

        User recipient = userRepository.findById(recipientIdNum.longValue())
                .orElseThrow(() -> new IllegalStateException("Recipient user not found: " + recipientIdNum));

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(String.valueOf(payload.getOrDefault("title", "System Update")));
        notification.setMessage(String.valueOf(payload.getOrDefault("message", "")));
        notification.setCategory(String.valueOf(payload.getOrDefault("category", "GENERAL")));
        notificationRepository.save(notification);

        log.info("[ApplicationNotificationWorker] Saved notification for userId={} title=\"{}\"",
                recipient.getId(), notification.getTitle());
        return "Notification persisted for " + recipient.getEmail();
    }
}
