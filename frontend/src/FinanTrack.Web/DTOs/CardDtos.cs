using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record CardRequest(
    string Nickname,
    CardBrand Brand,
    string LastFour,
    decimal CreditLimit,
    int ClosingDay,
    int DueDay,
    bool Archived);

public sealed record CardResponse(
    Guid Id,
    string Nickname,
    CardBrand Brand,
    string LastFour,
    decimal CreditLimit,
    decimal UsedLimit,
    decimal AvailableLimit,
    int ClosingDay,
    int DueDay,
    bool Archived);

public sealed record PurchaseRequest(
    string Description,
    decimal TotalAmount,
    int Installments,
    Guid CategoryId,
    DateOnly PurchaseDate);

public sealed record PurchaseResponse(
    Guid Id,
    string Description,
    decimal TotalAmount,
    int Installments,
    Guid CategoryId,
    DateOnly PurchaseDate,
    bool Canceled);

public sealed record InvoiceResponse(
    Guid Id,
    string ReferenceMonth,
    DateOnly ClosingDate,
    DateOnly DueDate,
    decimal TotalAmount,
    decimal PaidAmount,
    decimal RemainingAmount,
    InvoiceStatus Status);

public sealed record InvoiceLine(Guid PurchaseId, string Description, int Number, int Installments, decimal Amount, DateOnly PurchaseDate);

public sealed record InvoiceDetailResponse(InvoiceResponse Invoice, List<InvoiceLine> Lines);

public sealed record PaymentRequest(Guid AccountId, decimal Amount, DateOnly PaymentDate);
