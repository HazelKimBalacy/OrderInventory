package edu.cit.balacy.notification;

import java.time.Instant;

public record NotificationDTO(
        Long notificationId,
        NotificationType type,
        String message,
        Instant createdAt
) {
    static NotificationDTO from(Notification n) {
        return new NotificationDTO(
                n.getNotificationId(), n.getType(), n.getMessage(), n.getCreatedAt());
    }
}
