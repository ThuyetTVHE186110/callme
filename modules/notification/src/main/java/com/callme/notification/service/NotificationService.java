package com.callme.notification.service;

import com.callme.common.security.AuthenticatedAccount;
import com.callme.notification.dto.NotificationResponse;
import com.callme.notification.entity.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    UUID notify(UUID recipientId, NotificationType type, String message);

    /** Overload that attaches a referenceId (tripId or bookingId) for deep-linking in the mobile app. */
    UUID notify(UUID recipientId, NotificationType type, String message, UUID referenceId);

    List<NotificationResponse> listFor(UUID recipientId, AuthenticatedAccount requester);

    void markRead(UUID notificationId, AuthenticatedAccount requester);
}
