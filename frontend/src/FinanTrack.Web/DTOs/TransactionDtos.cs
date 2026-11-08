using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record TransactionRequest(
    string Description,
    decimal Amount,
    TransactionType Type,
    TransactionStatus Status,
    Guid CategoryId,
    Guid AccountId,
    DateOnly TransactionDate,
    DateOnly? DueDate,
    string? Notes,
    long? Version);

public sealed record TransactionResponse(
    Guid Id,
    string Description,
    decimal Amount,
    TransactionType Type,
    TransactionKind Kind,
    TransactionStatus Status,
    Guid? CategoryId,
    string? CategoryName,
    string? CategoryIcon,
    string? CategoryColor,
    Guid? AccountId,
    string? AccountName,
    DateOnly TransactionDate,
    DateOnly? DueDate,
    string? Notes,
    long Version);

public sealed record PageResponse<T>(List<T> Content, int Page, int Size, long TotalElements, int TotalPages);
