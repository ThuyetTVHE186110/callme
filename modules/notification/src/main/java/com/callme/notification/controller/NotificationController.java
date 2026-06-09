package com.callme.notification.controller;

import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.notification.dto.NotificationResponse;
import com.callme.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{recipientId}")
    public ApiResponse<List<NotificationResponse>> listFor(@PathVariable UUID recipientId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(notificationService.listFor(recipientId, account));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID notificationId, @AuthenticationPrincipal AuthenticatedAccount account) {
        notificationService.markRead(notificationId, account);
        return ApiResponse.ok(null);
    }
}
