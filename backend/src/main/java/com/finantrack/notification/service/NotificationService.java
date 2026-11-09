package com.finantrack.notification.service;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.notification.dto.NotificationListResponse;
import com.finantrack.notification.dto.NotificationResponse;
import com.finantrack.notification.entity.Notification;
import com.finantrack.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        Page<Notification> result = unreadOnly
                ? repository.findAllByUserIdAndReadAtIsNull(userId, pageable)
                : repository.findAllByUserId(userId, pageable);
        return new NotificationListResponse(repository.countByUserIdAndReadAtIsNull(userId),
                PageResponse.of(result, result.getContent().stream().map(this::toResponse).toList()));
    }

    @Transactional
    public NotificationResponse markRead(UUID id, UUID userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada."));
        notification.markRead(clock.instant());
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, clock.instant());
    }

    private NotificationResponse toResponse(Notification item) {
        return new NotificationResponse(item.getId(), item.getType(), item.getTitle(), item.getMessage(),
                item.getCreatedAt(), item.getReadAt());
    }
}
