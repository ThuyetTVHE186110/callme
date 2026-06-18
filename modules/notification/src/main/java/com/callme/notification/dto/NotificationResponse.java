package com.callme.notification.dto;

import com.callme.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID recipientId, NotificationType type,
                                    String message, boolean read, Instant createdAt,
                                    UUID referenceId) {
}
