package com.finantrack.notification.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A criação passa por NotificationRepository.insertIfAbsent, que respeita a chave de duplicidade. */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 120)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 120)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    /** Ler de novo mantém a data da primeira leitura. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
