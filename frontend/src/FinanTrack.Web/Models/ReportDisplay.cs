using System.Globalization;
using System.Text.Json.Serialization;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<ColumnKind>))]
public enum ColumnKind
{
    TEXT,
    DATE,
    MONEY,
    PERCENT,
    INTEGER
}

/// <summary>Filtro exibido para cada relatório; os cálculos ficam todos no backend.</summary>
public enum ReportFilterKind
{
    Month,
    Year,
    Period
}

public sealed record ReportOption(string Slug, string Label, ReportFilterKind Filter);

public static class ReportDisplay
{
    public const string IncomeVsExpense = "income-vs-expense";

    public static readonly IReadOnlyList<ReportOption> Options =
    [
        new("monthly", "Mensal", ReportFilterKind.Month),
        new("annual", "Anual", ReportFilterKind.Year),
        new("by-category", "Por categoria", ReportFilterKind.Period),
        new("by-period", "Por período", ReportFilterKind.Period),
        new(IncomeVsExpense, "Receitas versus despesas", ReportFilterKind.Period),
        new("evolution", "Evolução financeira", ReportFilterKind.Period),
        new("by-card", "Despesas por cartão", ReportFilterKind.Period)
    ];

    public static bool IsNumeric(ColumnKind kind) => kind is ColumnKind.MONEY or ColumnKind.PERCENT or ColumnKind.INTEGER;

    /// <summary>A linha de total traz texto mesmo em colunas de data, por isso o valor é mantido quando não é data.</summary>
    public static string Format(ColumnKind kind, string value)
    {
        if (value.Length == 0)
        {
            return value;
        }
        return kind switch
        {
            ColumnKind.MONEY => Money.Format(decimal.Parse(value, CultureInfo.InvariantCulture)),
            ColumnKind.PERCENT => decimal.Parse(value, CultureInfo.InvariantCulture).ToString("0.0", BrazilianCulture.Instance) + "%",
            ColumnKind.DATE when DateOnly.TryParseExact(value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var date)
                => date.ToString("dd/MM/yyyy", CultureInfo.InvariantCulture),
            _ => value
        };
    }
}
