using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record RecurringRequest(
    string Description,
    decimal Amount,
    TransactionType Type,
    Guid CategoryId,
    Guid AccountId,
    RecurrenceFrequency Frequency,
    DateOnly StartDate,
    DateOnly? EndDate,
    long? Version);

public sealed record RecurringResponse(
    Guid Id,
    string Description,
    decimal Amount,
    TransactionType Type,
    Guid CategoryId,
    string CategoryName,
    string CategoryIcon,
    string CategoryColor,
    Guid AccountId,
    string AccountName,
    RecurrenceFrequency Frequency,
    DateOnly StartDate,
    DateOnly? EndDate,
    DateOnly? NextOccurrenceDate,
    RecurringStatus Status,
    long Version);
