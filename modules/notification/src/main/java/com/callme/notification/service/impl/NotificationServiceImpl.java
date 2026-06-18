package com.callme.notification.service.impl;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.notification.dto.NotificationResponse;
import com.callme.notification.entity.Notification;
import com.callme.notification.entity.NotificationType;
import com.callme.notification.repository.NotificationRepository;
import com.callme.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public UUID notify(UUID recipientId, NotificationType type, String message) {
        return notify(recipientId, type, message, null);
    }

    @Override
    public UUID notify(UUID recipientId, NotificationType type, String message, UUID referenceId) {
        return notificationRepository.save(new Notification(recipientId, type, message, referenceId)).getId();
    }

    @Override
    public List<NotificationResponse> listFor(UUID recipientId, AuthenticatedAccount requester) {
        requireRecipient(recipientId, requester);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getRecipientId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt(), n.getReferenceId()))
                .toList();
    }

    @Override
    public void markRead(UUID notificationId, AuthenticatedAccount requester) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông báo: " + notificationId));
        requireRecipient(notification.getRecipientId(), requester);
        notification.markRead();
        notificationRepository.save(notification);
    }

    private void requireRecipient(UUID recipientId, AuthenticatedAccount requester) {
        if (!requester.isAdmin() && !requester.ownsProfile(recipientId)) {
            throw new ForbiddenException("Bạn chỉ có thể xem thông báo của chính mình");
        }
    }
}
