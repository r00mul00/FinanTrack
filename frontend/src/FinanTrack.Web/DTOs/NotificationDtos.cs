using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record NotificationResponse(
    Guid Id,
    NotificationType Type,
    string Title,
    string Message,
    DateTimeOffset CreatedAt,
    DateTimeOffset? ReadAt);

public sealed record NotificationListResponse(long UnreadCount, PageResponse<NotificationResponse> Notifications);
