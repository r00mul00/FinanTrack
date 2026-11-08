namespace FinanTrack.Web.DTOs;

public sealed record DashboardSummaryResponse(
    DateOnly From,
    DateOnly To,
    DateOnly PreviousFrom,
    DateOnly PreviousTo,
    decimal TotalBalance,
    decimal Income,
    decimal Expenses,
    decimal Result,
    decimal PreviousIncome,
    decimal PreviousExpenses,
    decimal PreviousResult,
    decimal? IncomeChange,
    decimal? ExpensesChange,
    decimal? ResultChange);

public sealed record SeriesPoint(DateOnly Date, decimal Income, decimal Expenses);

public sealed record IncomeVsExpenseResponse(string Granularity, List<SeriesPoint> Points);

public sealed record CategoryExpenseResponse(
    Guid CategoryId,
    string Name,
    string Icon,
    string Color,
    decimal Amount,
    decimal Percentage);
