using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record GoalRequest(
    string Name,
    string? Description,
    decimal TargetAmount,
    decimal InitialAmount,
    DateOnly Deadline,
    long? Version);

public sealed record GoalMovementRequest(decimal Amount, string? Note);

public sealed record GoalResponse(
    Guid Id,
    string Name,
    string? Description,
    decimal TargetAmount,
    decimal InitialAmount,
    decimal CurrentAmount,
    decimal RemainingAmount,
    decimal Progress,
    DateOnly Deadline,
    bool Overdue,
    GoalStatus Status,
    long Version);

public sealed record GoalMovementResponse(Guid Id, GoalMovementType Type, decimal Amount, string? Note, DateTimeOffset CreatedAt);
