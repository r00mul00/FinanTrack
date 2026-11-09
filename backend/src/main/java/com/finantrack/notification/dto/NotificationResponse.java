package com.finantrack.notification.dto;

import com.finantrack.notification.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String message, Instant createdAt,
        Instant readAt) {
}
