using System.Globalization;
using System.Text;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>Dashboard, relatórios, busca e notificações: só leituras sobre os dados locais.</summary>
public sealed partial class DemoStore
{
    private const int DailySeriesMaxDays = 62;
    private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;

    // ---------- Período (DashboardPeriod) ----------

    /// <summary>Sem datas, usa o mês corrente.</summary>
    private static (DateOnly From, DateOnly To) Resolve(DateOnly? from, DateOnly? to)
    {
        if (from is null && to is null)
        {
            var first = new DateOnly(Today.Year, Today.Month, 1);
            return (first, first.AddMonths(1).AddDays(-1));
        }
        if (from is null || to is null)
        {
            throw DemoException.Invalid("Informe as datas inicial e final.");
        }
        if (from > to)
        {
            throw DemoException.Invalid("A data inicial não pode ser posterior à data final.");
        }
        if (from.Value.AddYears(5) < to)
        {
            throw DemoException.Invalid("O período não pode ser maior que 5 anos.");
        }
        return (from.Value, to.Value);
    }

    /// <summary>Mês completo compara com o mês anterior; os demais, com o mesmo número de dias logo antes.</summary>
    private static (DateOnly From, DateOnly To) Previous(DateOnly from, DateOnly to)
    {
        var first = new DateOnly(from.Year, from.Month, 1);
        if (from == first && to == first.AddMonths(1).AddDays(-1))
        {
            var previous = first.AddMonths(-1);
            return (previous, first.AddDays(-1));
        }
        var days = Length(from, to);
        return (from.AddDays(-days), from.AddDays(-1));
    }

    private static int Length(DateOnly from, DateOnly to) => to.DayNumber - from.DayNumber + 1;

    // ---------- Dashboard ----------

    public DashboardSummaryResponse Summary(DateOnly? fromParam, DateOnly? toParam)
    {
        var (from, to) = Resolve(fromParam, toParam);
        var (previousFrom, previousTo) = Previous(from, to);
        var current = SumPeriod(from, to);
        var before = SumPeriod(previousFrom, previousTo);
        var result = current.Income - current.Expenses;
        var previousResult = before.Income - before.Expenses;
        var balance = State.Accounts.Where(a => !a.Archived).Sum(CurrentBalance);

        return new DashboardSummaryResponse(from, to, previousFrom, previousTo, balance,
            current.Income, current.Expenses, result, before.Income, before.Expenses, previousResult,
            Change(current.Income, before.Income), Change(current.Expenses, before.Expenses), Change(result, previousResult));
    }

    /// <summary>Usa o valor absoluto da base, pois o resultado anterior pode ser negativo.</summary>
    private static decimal? Change(decimal current, decimal previous) =>
        previous == 0 ? null : Math.Round((current - previous) / Math.Abs(previous) * 100m, 1, MidpointRounding.AwayFromZero);

    public IncomeVsExpenseResponse IncomeVsExpense(DateOnly? fromParam, DateOnly? toParam)
    {
        var (from, to) = Resolve(fromParam, toParam);
        var daily = Length(from, to) <= DailySeriesMaxDays;
        var points = (daily ? Days(from, to) : Months(from, to))
            .Select(b => new SeriesPoint(b.Start, b.Income, b.Expenses))
            .ToList();
        return new IncomeVsExpenseResponse(daily ? "DAY" : "MONTH", points);
    }

    public List<CategoryExpenseResponse> ExpensesByCategory(DateOnly? fromParam, DateOnly? toParam)
    {
        var (from, to) = Resolve(fromParam, toParam);
        var totals = CategoryTotals(from, to);
        var sum = totals.Sum(t => t.Total);
        return totals.Select(t => new CategoryExpenseResponse(t.Category.Id, t.Category.Name, t.Category.Icon,
            t.Category.Color, t.Total, Percent(t.Total, sum))).ToList();
    }

    private List<(DemoCategory Category, decimal Total)> CategoryTotals(DateOnly from, DateOnly to) =>
        InPeriod(from, to).Where(CountedExpense)
            .Where(t => t.CategoryId is not null)
            .GroupBy(t => t.CategoryId!.Value)
            .Select(g => (Category: FindCategory(g.Key), Total: g.Sum(t => t.Amount)))
            .OrderByDescending(x => x.Total)
            .ToList();

    private static decimal Percent(decimal part, decimal total) =>
        total == 0 ? 0 : Math.Round(part * 100m / total, 1, MidpointRounding.AwayFromZero);

    /// <summary>Contas a pagar e faturas, inclusive vencidas, nos próximos 30 dias; no máximo 8 itens.</summary>
    public List<UpcomingDueResponse> Upcoming()
    {
        var limit = Today.AddDays(30);
        var bills = State.Transactions
            .Where(t => t.Status == TransactionStatus.PENDING && t.Kind == TransactionKind.STANDARD
                && t.Type == TransactionType.EXPENSE && t.DueDate is not null && t.DueDate <= limit)
            .OrderBy(t => t.DueDate).Take(8)
            .Select(t => new UpcomingDueResponse("BILL", t.Id, null, t.Description, t.Amount, t.DueDate!.Value, t.DueDate < Today));

        var invoices = State.Invoices.Where(i => i.DueDate <= limit)
            .Select(i => (Invoice: i, Remaining: InvoiceTotal(i.Id) - i.PaidAmount))
            .Where(x => x.Remaining > 0)
            .Select(x => new UpcomingDueResponse("INVOICE", x.Invoice.Id, x.Invoice.CardId,
                "Fatura " + State.Cards.First(c => c.Id == x.Invoice.CardId).Nickname, x.Remaining, x.Invoice.DueDate,
                x.Invoice.DueDate < Today));

        return bills.Concat(invoices).OrderBy(i => i.DueDate).Take(8).ToList();
    }

    // ---------- Relatórios (ReportService) ----------

    private sealed record Bucket(DateOnly Start, string Label, decimal Income, decimal Expenses)
    {
        public decimal Result => Income - Expenses;
    }

    private List<Bucket> Days(DateOnly from, DateOnly to)
    {
        var byDay = InPeriod(from, to).GroupBy(t => t.TransactionDate).ToDictionary(g => g.Key, g => g.ToList());
        var buckets = new List<Bucket>();
        for (var day = from; day <= to; day = day.AddDays(1))
        {
            var items = byDay.GetValueOrDefault(day) ?? [];
            buckets.Add(new Bucket(day, Iso(day), items.Where(CountedIncome).Sum(t => t.Amount),
                items.Where(CountedExpense).Sum(t => t.Amount)));
        }
        return buckets;
    }

    private List<Bucket> Months(DateOnly from, DateOnly to)
    {
        var items = InPeriod(from, to).ToList();
        var buckets = new List<Bucket>();
        var last = new DateOnly(to.Year, to.Month, 1);
        for (var month = new DateOnly(from.Year, from.Month, 1); month <= last; month = month.AddMonths(1))
        {
            var inMonth = items.Where(t => t.TransactionDate.Year == month.Year && t.TransactionDate.Month == month.Month).ToList();
            buckets.Add(new Bucket(month, month.ToString("MM/yyyy", Invariant), inMonth.Where(CountedIncome).Sum(t => t.Amount),
                inMonth.Where(CountedExpense).Sum(t => t.Amount)));
        }
        return buckets;
    }

    public ReportResponse Report(string slug, IReadOnlyDictionary<string, string> query)
    {
        DateOnly? Date(string key) => query.TryGetValue(key, out var value) ? ParseIso(value) : null;

        switch (slug)
        {
            case "monthly":
                var month = query.TryGetValue("month", out var m)
                    ? DateOnly.ParseExact(m + "-01", "yyyy-MM-dd", Invariant)
                    : new DateOnly(Today.Year, Today.Month, 1);
                return Series("Relatório mensal", month, month.AddMonths(1).AddDays(-1), true);
            case "annual":
                var year = query.TryGetValue("year", out var y) ? int.Parse(y, Invariant) : Today.Year;
                return Series("Relatório anual", new DateOnly(year, 1, 1), new DateOnly(year, 12, 31), false);
        }

        var (from, to) = Resolve(Date("from"), Date("to"));
        return slug switch
        {
            "income-vs-expense" => Series("Receitas versus despesas", from, to, Length(from, to) <= DailySeriesMaxDays),
            "evolution" => Evolution(from, to),
            "by-period" => ByPeriod(from, to),
            "by-category" => ByCategory(from, to),
            "by-card" => ByCard(from, to),
            _ => throw DemoException.NotFound("Relatório não encontrado.")
        };
    }

    private static readonly ReportColumn IncomeColumn = new("Receitas", ColumnKind.MONEY);
    private static readonly ReportColumn ExpensesColumn = new("Despesas", ColumnKind.MONEY);
    private static readonly ReportColumn ResultColumn = new("Resultado", ColumnKind.MONEY);

    private ReportResponse Series(string title, DateOnly from, DateOnly to, bool daily)
    {
        var buckets = daily ? Days(from, to) : Months(from, to);
        var first = daily ? new ReportColumn("Data", ColumnKind.DATE) : new ReportColumn("Mês", ColumnKind.TEXT);
        var rows = buckets.Select(b => new List<string> { b.Label, Money(b.Income), Money(b.Expenses), Money(b.Result) }).ToList();
        return new ReportResponse(title, from, to, TotalsIndicators(from, to),
            [first, IncomeColumn, ExpensesColumn, ResultColumn], rows, SeriesTotals(buckets, false));
    }

    /// <summary>O acumulado parte de zero no início do período.</summary>
    private ReportResponse Evolution(DateOnly from, DateOnly to)
    {
        var buckets = Months(from, to);
        var rows = new List<List<string>>();
        var accumulated = 0m;
        foreach (var b in buckets)
        {
            accumulated += b.Result;
            rows.Add([b.Label, Money(b.Income), Money(b.Expenses), Money(b.Result), Money(accumulated)]);
        }
        return new ReportResponse("Evolução financeira", from, to, TotalsIndicators(from, to),
            [new ReportColumn("Mês", ColumnKind.TEXT), IncomeColumn, ExpensesColumn, ResultColumn,
             new ReportColumn("Acumulado", ColumnKind.MONEY)],
            rows, SeriesTotals(buckets, true));
    }

    private ReportResponse ByPeriod(DateOnly from, DateOnly to)
    {
        var items = InPeriod(from, to).Where(t => CountedIncome(t) || CountedExpense(t))
            .OrderBy(t => t.TransactionDate).ThenBy(t => t.CreatedAt).ToList();
        var rows = items.Select(t =>
        {
            var income = t.Type == TransactionType.INCOME;
            var category = t.CategoryId is { } id ? State.Categories.FirstOrDefault(c => c.Id == id)?.Name : null;
            return new List<string>
            {
                Iso(t.TransactionDate), t.Description, category ?? "", income ? Money(t.Amount) : "", income ? "" : Money(t.Amount)
            };
        }).ToList();
        var totalIncome = items.Where(t => t.Type == TransactionType.INCOME).Sum(t => t.Amount);
        var totalExpenses = items.Where(t => t.Type == TransactionType.EXPENSE).Sum(t => t.Amount);
        return new ReportResponse("Lançamentos do período", from, to, TotalsIndicators(from, to),
            [new ReportColumn("Data", ColumnKind.DATE), new ReportColumn("Descrição", ColumnKind.TEXT),
             new ReportColumn("Categoria", ColumnKind.TEXT), IncomeColumn, ExpensesColumn],
            rows, ["Total", "", "", Money(totalIncome), Money(totalExpenses)]);
    }

    private ReportResponse ByCategory(DateOnly from, DateOnly to)
    {
        var totals = CategoryTotals(from, to);
        var sum = totals.Sum(t => t.Total);
        var rows = totals.Select(t => new List<string>
        {
            t.Category.Name, Money(t.Total), Percent(t.Total, sum).ToString("0.0", Invariant)
        }).ToList();
        return new ReportResponse("Despesas por categoria", from, to, [new ReportIndicator("Total de despesas", sum)],
            [new ReportColumn("Categoria", ColumnKind.TEXT), new ReportColumn("Total", ColumnKind.MONEY),
             new ReportColumn("Percentual", ColumnKind.PERCENT)],
            rows, ["Total", Money(sum), rows.Count == 0 ? "0.0" : "100.0"]);
    }

    /// <summary>Pela data da parcela: uma compra parcelada se distribui pelos meses.</summary>
    private ReportResponse ByCard(DateOnly from, DateOnly to)
    {
        var byTransaction = State.Transactions.ToDictionary(t => t.Id);
        var totals = State.Installments
            .Select(i => (Installment: i, Purchase: PurchaseOf(i), Date: byTransaction[i.TransactionId].TransactionDate))
            .Where(x => !x.Purchase.Canceled && x.Date >= from && x.Date <= to)
            .GroupBy(x => x.Purchase.CardId)
            .Select(g => (Card: FindCard(g.Key), Purchases: g.Select(x => x.Purchase.Id).Distinct().Count(),
                Total: g.Sum(x => x.Installment.Amount)))
            .OrderByDescending(x => x.Total)
            .ToList();
        var sum = totals.Sum(t => t.Total);
        var rows = totals.Select(t => new List<string>
        {
            t.Card.Nickname, t.Purchases.ToString(Invariant), Money(t.Total)
        }).ToList();
        return new ReportResponse("Despesas por cartão", from, to, [new ReportIndicator("Total nos cartões", sum)],
            [new ReportColumn("Cartão", ColumnKind.TEXT), new ReportColumn("Compras", ColumnKind.INTEGER),
             new ReportColumn("Total", ColumnKind.MONEY)],
            rows, ["Total", "", Money(sum)]);
    }

    private List<ReportIndicator> TotalsIndicators(DateOnly from, DateOnly to)
    {
        var (income, expenses) = SumPeriod(from, to);
        return [new("Receitas", income), new("Despesas", expenses), new("Resultado", income - expenses)];
    }

    private static List<string> SeriesTotals(List<Bucket> buckets, bool withAccumulated)
    {
        var income = buckets.Sum(b => b.Income);
        var expenses = buckets.Sum(b => b.Expenses);
        var result = Money(income - expenses);
        return withAccumulated
            ? ["Total", Money(income), Money(expenses), result, result]
            : ["Total", Money(income), Money(expenses), result];
    }

    private static string Money(decimal value) =>
        Math.Round(value, 2, MidpointRounding.AwayFromZero).ToString("0.00", Invariant);

    private static string Iso(DateOnly date) => date.ToString("yyyy-MM-dd", Invariant);

    private static DateOnly ParseIso(string value) => DateOnly.ParseExact(value, "yyyy-MM-dd", Invariant);

    /// <summary>Mesmo formato do ReportExporter: ";" e vírgula decimal, com BOM para o Excel.</summary>
    public static byte[] ToCsv(ReportResponse report)
    {
        var csv = new StringBuilder();
        csv.Append(string.Join(';', report.Columns.Select(c => CsvText(c.Label)))).Append("\r\n");
        foreach (var row in report.Rows.Append(report.Totals))
        {
            csv.Append(string.Join(';', row.Select((value, i) => CsvValue(report.Columns[i].Kind, value)))).Append("\r\n");
        }
        return [.. Encoding.UTF8.GetPreamble(), .. Encoding.UTF8.GetBytes(csv.ToString())];
    }

    private static string CsvValue(ColumnKind kind, string value)
    {
        if (value.Length == 0)
        {
            return value;
        }
        return kind switch
        {
            ColumnKind.MONEY or ColumnKind.PERCENT => value.Replace('.', ','),
            ColumnKind.DATE when DateOnly.TryParseExact(value, "yyyy-MM-dd", Invariant, DateTimeStyles.None, out var date)
                => date.ToString("dd/MM/yyyy", Invariant),
            _ => CsvText(value)
        };
    }

    /// <summary>Aspas quando preciso e apóstrofo antes de fórmulas, para o Excel não executá-las.</summary>
    private static string CsvText(string value)
    {
        if (value.Length > 0 && "=+-@\t\r".Contains(value[0]))
        {
            value = "'" + value;
        }
        return value.IndexOfAny([';', '"', '\n', '\r']) >= 0 ? $"\"{value.Replace("\"", "\"\"")}\"" : value;
    }

    // ---------- Busca ----------

    /// <summary>Até cinco itens de cada tipo, a partir de dois caracteres.</summary>
    public List<SearchResult> Search(string? query)
    {
        var term = query?.Trim() ?? "";
        if (term.Length < 2)
        {
            return [];
        }
        bool Matches(string text) => text.Contains(term, StringComparison.CurrentCultureIgnoreCase);
        static string Br(DateOnly date) => date.ToString("dd/MM/yyyy", Invariant);

        var results = new List<SearchResult>();
        results.AddRange(State.Transactions.Where(t => Matches(t.Description)).OrderByDescending(t => t.TransactionDate).Take(5)
            .Select(t => new SearchResult(SearchResultType.TRANSACTION, t.Id, t.Description, Br(t.TransactionDate))));
        results.AddRange(State.Categories.Where(c => Matches(c.Name)).OrderBy(c => c.Name).Take(5)
            .Select(c => new SearchResult(SearchResultType.CATEGORY, c.Id, c.Name, c.Type == CategoryType.INCOME ? "Receita" : "Despesa")));
        results.AddRange(State.Goals.Where(g => Matches(g.Name)).OrderBy(g => g.Name).Take(5)
            .Select(g => new SearchResult(SearchResultType.GOAL, g.Id, g.Name, "Prazo: " + Br(g.Deadline))));
        results.AddRange(State.Cards.Where(c => Matches(c.Nickname)).OrderBy(c => c.Nickname).Take(5)
            .Select(c => new SearchResult(SearchResultType.CARD, c.Id, c.Nickname, "Final " + c.LastFour)));
        results.AddRange(State.Recurrings.Where(r => Matches(r.Description)).OrderBy(r => r.Description).Take(5)
            .Select(r => new SearchResult(SearchResultType.RECURRING, r.Id, r.Description,
                r.NextOccurrenceDate is { } next ? "Próxima: " + Br(next) : null)));
        return results;
    }

    // ---------- Notificações ----------

    public NotificationListResponse Notifications(int size)
    {
        var items = State.Notifications.OrderByDescending(n => n.CreatedAt)
            .Select(n => new NotificationResponse(n.Id, n.Type, n.Title, n.Message, n.CreatedAt, n.ReadAt));
        return new NotificationListResponse(State.Notifications.Count(n => n.ReadAt is null), Page(items, 0, size));
    }

    public void MarkRead(Guid id)
    {
        var notification = State.Notifications.FirstOrDefault(n => n.Id == id)
            ?? throw DemoException.NotFound("Notificação não encontrada.");
        notification.ReadAt ??= DateTimeOffset.Now;
        Save();
    }

    public void MarkAllRead()
    {
        State.Notifications.Where(n => n.ReadAt is null).ToList().ForEach(n => n.ReadAt = DateTimeOffset.Now);
        Save();
    }
}
