using FinanTrack.Web.Models;

namespace FinanTrack.Web.DTOs;

public sealed record ReportIndicator(string Label, decimal Value);

public sealed record ReportColumn(string Label, ColumnKind Kind);

public sealed record ReportResponse(
    string Title,
    DateOnly From,
    DateOnly To,
    List<ReportIndicator> Indicators,
    List<ReportColumn> Columns,
    List<List<string>> Rows,
    List<string> Totals);
