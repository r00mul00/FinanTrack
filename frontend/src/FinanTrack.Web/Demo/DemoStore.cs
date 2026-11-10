using System.Text.Json;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;
using Microsoft.JSInterop;

namespace FinanTrack.Web.Demo;

/// <summary>
/// "Backend" do modo demonstração: guarda os dados no localStorage e aplica as
/// mesmas regras de docs/06 que o backend Java. Cada área fica em um arquivo parcial.
/// </summary>
public sealed partial class DemoStore(IJSRuntime js)
{
    /// <summary>Mudar este número descarta os dados salvos e recria os de exemplo.</summary>
    private const int SchemaVersion = 1;
    private const string StorageKey = "finantrack-demo";

    /// <summary>Dados com mais de 30 dias são recriados, para o demo sempre parecer atual.</summary>
    private const int MaxDataAgeDays = 30;

    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private DemoState? _state;
    private bool _detached;

    private DemoState State => _state ??= Load();

    private static DateOnly Today => DateOnly.FromDateTime(DateTime.Now);

    /// <summary>Opera sobre um estado em memória, sem gravar: usado para montar os dados de exemplo.</summary>
    internal static DemoStore Detached(DemoState state) => new(null!) { _state = state, _detached = true };

    /// <summary>Apaga tudo e recria os dados de exemplo, mantendo o visitante conectado.</summary>
    public void Reset()
    {
        _state = DemoSeed.Create(Today, SchemaVersion);
        Save();
    }

    private void Save()
    {
        if (!_detached)
        {
            Storage.InvokeVoid("localStorage.setItem", StorageKey, JsonSerializer.Serialize(State, Json));
        }
    }

    private IJSInProcessRuntime Storage => (IJSInProcessRuntime)js;

    private DemoState Load()
    {
        DemoState? stored = null;
        try
        {
            var json = Storage.Invoke<string?>("localStorage.getItem", StorageKey);
            stored = json is null ? null : JsonSerializer.Deserialize<DemoState>(json, Json);
        }
        catch (JsonException)
        {
            // Dados corrompidos: recria abaixo.
        }

        if (stored is null || stored.SchemaVersion != SchemaVersion || stored.SeededOn.AddDays(MaxDataAgeDays) < Today)
        {
            stored = DemoSeed.Create(Today, SchemaVersion);
        }

        // O equivalente ao job da madrugada: gera os lançamentos recorrentes vencidos.
        _state = stored;
        foreach (var item in stored.Recurrings)
        {
            CatchUp(item, Today);
        }
        Save();
        return stored;
    }

    private long Tick() => ++State.Clock;

    // ---------- Consultas comuns ----------

    private DemoCategory FindCategory(Guid id) =>
        State.Categories.FirstOrDefault(item => item.Id == id) ?? throw DemoException.NotFound("Categoria não encontrada.");

    private DemoAccount FindAccount(Guid id) =>
        State.Accounts.FirstOrDefault(item => item.Id == id) ?? throw DemoException.NotFound("Conta não encontrada.");

    /// <summary>Arquivada só é aceita se o registro já a utilizava, como no CategoryLookup.</summary>
    private DemoCategory UsableCategory(Guid id, CategoryType expected, string mismatch, Guid? currentId)
    {
        var category = FindCategory(id);
        if (category.Type != expected)
        {
            throw DemoException.Rule(mismatch);
        }
        if (category.Archived && category.Id != currentId)
        {
            throw DemoException.Rule("A categoria selecionada está arquivada.");
        }
        return category;
    }

    private DemoAccount UsableAccount(Guid id, Guid? currentId)
    {
        var account = FindAccount(id);
        if (account.Archived && account.Id != currentId)
        {
            throw DemoException.Rule("A conta selecionada está arquivada.");
        }
        return account;
    }

    // ---------- Regras de contagem de docs/06 ----------

    /// <summary>Receitas contam só depois de recebidas.</summary>
    private static bool CountedIncome(DemoTransaction t) =>
        t.Type == TransactionType.INCOME && t.Status == TransactionStatus.COMPLETED;

    /// <summary>A despesa de cartão acontece na compra; o pagamento da fatura nunca conta.</summary>
    private static bool CountedExpense(DemoTransaction t) =>
        t.Type == TransactionType.EXPENSE
        && ((t.Kind == TransactionKind.STANDARD && t.Status == TransactionStatus.COMPLETED)
            || (t.Kind == TransactionKind.CARD_INSTALLMENT && t.Status != TransactionStatus.CANCELED));

    private static decimal Signed(DemoTransaction t) => t.Type == TransactionType.INCOME ? t.Amount : -t.Amount;

    private IEnumerable<DemoTransaction> InPeriod(DateOnly from, DateOnly to) =>
        State.Transactions.Where(t => t.TransactionDate >= from && t.TransactionDate <= to);

    private (decimal Income, decimal Expenses) SumPeriod(DateOnly from, DateOnly to)
    {
        var items = InPeriod(from, to).ToList();
        return (items.Where(CountedIncome).Sum(t => t.Amount), items.Where(CountedExpense).Sum(t => t.Amount));
    }

    private decimal CurrentBalance(DemoAccount account) =>
        account.InitialBalance + State.Transactions
            .Where(t => t.AccountId == account.Id && t.Status == TransactionStatus.COMPLETED)
            .Sum(Signed);

    // ---------- Conversão para os DTOs do frontend ----------

    private UserResponse ToUser() =>
        new(State.User.Id, State.User.Name, State.User.Email, State.User.Currency, State.User.Theme);

    private AccountResponse ToResponse(DemoAccount a) =>
        new(a.Id, a.Name, a.Type, a.InitialBalance, CurrentBalance(a), a.Archived);

    private static CategoryResponse ToResponse(DemoCategory c) => new(c.Id, c.Name, c.Type, c.Icon, c.Color, c.Archived);

    private TransactionResponse ToResponse(DemoTransaction t)
    {
        var category = t.CategoryId is { } categoryId ? State.Categories.FirstOrDefault(c => c.Id == categoryId) : null;
        var account = t.AccountId is { } accountId ? State.Accounts.FirstOrDefault(a => a.Id == accountId) : null;
        return new TransactionResponse(t.Id, t.Description, t.Amount, t.Type, t.Kind, t.Status, category?.Id,
            category?.Name, category?.Icon, category?.Color, account?.Id, account?.Name, t.TransactionDate, t.DueDate,
            t.Notes, t.RecurringId is not null, t.Version);
    }

    private static PageResponse<T> Page<T>(IEnumerable<T> source, int page, int size)
    {
        var all = source.ToList();
        var totalPages = (int)Math.Ceiling(all.Count / (double)size);
        return new PageResponse<T>(all.Skip(page * size).Take(size).ToList(), page, size, all.Count, totalPages);
    }
}
