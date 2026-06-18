package com.callme.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(force = true)
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String message;

    /** The trip or booking this notification relates to — allows the mobile app to navigate directly to the right screen. Null for non-trip notifications (e.g. VERIFICATION_EXPIRED). */
    private UUID referenceId;

    private boolean read;

    private Instant createdAt;

    public Notification(UUID recipientId, NotificationType type, String message) {
        this(recipientId, type, message, null);
    }

    public Notification(UUID recipientId, NotificationType type, String message, UUID referenceId) {
        this.recipientId = recipientId;
        this.type = type;
        this.message = message;
        this.referenceId = referenceId;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        this.read = true;
    }
}
