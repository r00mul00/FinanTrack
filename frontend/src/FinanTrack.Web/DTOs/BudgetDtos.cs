using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record BudgetRequest(Guid CategoryId, string Month, decimal LimitAmount);

public sealed record BudgetLimitRequest(decimal LimitAmount);

public sealed record BudgetResponse(
    Guid Id,
    Guid CategoryId,
    string CategoryName,
    string CategoryIcon,
    string CategoryColor,
    string Month,
    decimal LimitAmount,
    decimal UsedAmount,
    decimal RemainingAmount,
    decimal Percentage,
    BudgetState State);

public sealed record BudgetMonthResponse(string Month, decimal TotalLimit, decimal TotalUsed, List<BudgetResponse> Items);
