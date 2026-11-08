namespace FinanTrack.Web.Models;

public sealed record TransactionFilter(
    string? Search,
    TransactionType? Type,
    TransactionStatus? Status,
    Guid? CategoryId,
    DateOnly? From,
    DateOnly? To);
