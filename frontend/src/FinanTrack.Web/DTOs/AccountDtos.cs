using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record AccountRequest(string Name, AccountType Type, decimal InitialBalance, bool Archived);

public sealed record AccountResponse(
    Guid Id,
    string Name,
    AccountType Type,
    decimal InitialBalance,
    decimal CurrentBalance,
    bool Archived);
