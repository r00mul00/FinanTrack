package com.finantrack.notification.repository;

import com.finantrack.notification.entity.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    /** O conflito significa "já avisado": repetir o job nunca duplica nem falha. */
    @Transactional
    @Modifying
    @Query(value = """
            insert into notifications (id, user_id, type, title, message, dedupe_key, created_at, updated_at)
            values (:id, :userId, :type, :title, :message, :dedupeKey, :now, :now)
            on conflict (user_id, dedupe_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("type") String type,
            @Param("title") String title, @Param("message") String message, @Param("dedupeKey") String dedupeKey,
            @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
