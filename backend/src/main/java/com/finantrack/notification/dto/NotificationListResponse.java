package com.finantrack.notification.dto;

import com.finantrack.common.dto.PageResponse;

/** O contador acompanha a lista para o sino não precisar de uma segunda chamada. */
public record NotificationListResponse(long unreadCount, PageResponse<NotificationResponse> notifications) {
}
