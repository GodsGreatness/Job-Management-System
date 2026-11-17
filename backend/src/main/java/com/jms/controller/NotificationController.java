package com.jms.controller;

import com.jms.dto.AnalyticsDtos.NotificationResponse;
import com.jms.security.UserPrincipal;
import com.jms.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(notificationService.getUserNotifications(actor.getId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(actor.getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal UserPrincipal actor,
                                                         @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(actor.getId(), id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal actor) {
        notificationService.markAllAsRead(actor.getId());
        return ResponseEntity.noContent().build();
    }
}
