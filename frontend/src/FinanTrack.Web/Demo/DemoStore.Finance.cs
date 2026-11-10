using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>Sessão, perfil, contas, categorias, movimentações e orçamentos.</summary>
public sealed partial class DemoStore
{
    // ---------- Sessão e perfil ----------

    /// <summary>Qualquer e-mail e senha entram: a conta de demonstração é a mesma para todos.</summary>
    public TokenResponse Login()
    {
        State.LoggedOut = false;
        Save();
        return Token();
    }

    /// <summary>Quem não saiu explicitamente continua conectado, inclusive na primeira visita.</summary>
    public TokenResponse? Refresh() => State.LoggedOut ? null : Token();

    public void Logout()
    {
        State.LoggedOut = true;
        Save();
    }

    private static TokenResponse Token() => new("demo", "Bearer", 900);

    public bool IsLoggedIn => !State.LoggedOut;

    public UserResponse CurrentUser() => ToUser();

    public UserResponse Rename(UpdateProfileRequest request)
    {
        State.User.Name = request.Name.Trim();
        Save();
        return ToUser();
    }

    public PreferencesDto Preferences() =>
        new(State.User.Currency, State.User.Theme, State.User.NotifyBudget, State.User.NotifyDueDates);

    public void UpdatePreferences(PreferencesDto request)
    {
        State.User.Currency = request.Currency;
        State.User.Theme = request.Theme;
        State.User.NotifyBudget = request.NotifyBudget;
        State.User.NotifyDueDates = request.NotifyDueDates;
        Save();
    }

    /// <summary>Mesmo conteúdo da exportação real (LGPD), gerado a partir dos dados locais.</summary>
    public object Export() => new
    {
        user = ToUser(),
        preferences = Preferences(),
        accounts = ListAccounts(),
        categories = ListCategories(),
        transactions = State.Transactions.OrderBy(t => t.TransactionDate).Select(ToResponse).ToList(),
        budgets = State.Budgets.Select(b => BudgetResponse(b)).ToList(),
        goals = ListGoals(),
        cards = ListCards(),
        recurring = ListRecurring()
    };

    // ---------- Contas ----------

    public List<AccountResponse> ListAccounts() =>
        State.Accounts.OrderBy(a => a.Name, StringComparer.CurrentCultureIgnoreCase).Select(ToResponse).ToList();

    public AccountResponse CreateAccount(AccountRequest request)
    {
        RequireUniqueAccountName(request.Name, null);
        var account = new DemoAccount { Name = request.Name.Trim(), Type = request.Type, InitialBalance = request.InitialBalance };
        State.Accounts.Add(account);
        Save();
        return ToResponse(account);
    }

    public AccountResponse UpdateAccount(Guid id, AccountRequest request)
    {
        var account = FindAccount(id);
        RequireUniqueAccountName(request.Name, id);
        account.Name = request.Name.Trim();
        account.Type = request.Type;
        account.InitialBalance = request.InitialBalance;
        account.Archived = request.Archived;
        Save();
        return ToResponse(account);
    }

    public void DeleteAccount(Guid id)
    {
        var account = FindAccount(id);
        if (State.Transactions.Any(t => t.AccountId == id))
        {
            throw DemoException.Conflict("Esta conta possui movimentações. Arquive-a para manter o histórico.");
        }
        if (State.Recurrings.Any(r => r.AccountId == id))
        {
            throw DemoException.Conflict("Esta conta é usada por contas recorrentes. Altere-as antes de excluir.");
        }
        State.Accounts.Remove(account);
        Save();
    }

    private void RequireUniqueAccountName(string name, Guid? exceptId)
    {
        if (State.Accounts.Any(a => a.Id != exceptId && string.Equals(a.Name, name.Trim(), StringComparison.OrdinalIgnoreCase)))
        {
            throw DemoException.Conflict("Já existe uma conta com este nome.");
        }
    }

    // ---------- Categorias ----------

    public List<CategoryResponse> ListCategories() =>
        State.Categories.OrderBy(c => c.Name, StringComparer.CurrentCultureIgnoreCase).Select(ToResponse).ToList();

    public CategoryResponse CreateCategory(CategoryRequest request)
    {
        RequireUniqueCategoryName(request.Name, request.Type, null);
        var category = new DemoCategory
        {
            Name = request.Name.Trim(), Type = request.Type, Icon = request.Icon, Color = request.Color
        };
        State.Categories.Add(category);
        Save();
        return ToResponse(category);
    }

    public CategoryResponse UpdateCategory(Guid id, CategoryRequest request)
    {
        var category = FindCategory(id);
        if (category.Type != request.Type)
        {
            throw DemoException.Rule("O tipo da categoria não pode ser alterado.");
        }
        RequireUniqueCategoryName(request.Name, request.Type, id);
        category.Name = request.Name.Trim();
        category.Icon = request.Icon;
        category.Color = request.Color;
        category.Archived = request.Archived;
        Save();
        return ToResponse(category);
    }

    /// <summary>Soma os vínculos de movimentações, orçamentos, compras e recorrências.</summary>
    public long CategoryUsage(Guid id)
    {
        FindCategory(id);
        return State.Transactions.Count(t => t.CategoryId == id)
            + State.Budgets.Count(b => b.CategoryId == id)
            + State.Purchases.Count(p => p.CategoryId == id)
            + State.Recurrings.Count(r => r.CategoryId == id);
    }

    public void DeleteCategory(Guid id, Guid? reassignTo)
    {
        var category = FindCategory(id);
        if (CategoryUsage(id) > 0)
        {
            if (reassignTo is null)
            {
                throw DemoException.Conflict("Esta categoria está em uso. Escolha outra para receber os registros.");
            }
            var target = FindCategory(reassignTo.Value);
            if (target.Id == id)
            {
                throw DemoException.Rule("Escolha uma categoria diferente da que será excluída.");
            }
            if (target.Type != category.Type)
            {
                throw DemoException.Rule("A categoria de destino precisa ser do mesmo tipo.");
            }
            if (target.Archived)
            {
                throw DemoException.Rule("A categoria de destino está arquivada.");
            }
            Reassign(id, target.Id);
        }
        State.Categories.Remove(category);
        Save();
    }

    /// <summary>Orçamento que já existe no destino para o mesmo mês prevalece sobre o da categoria excluída.</summary>
    private void Reassign(Guid from, Guid to)
    {
        State.Transactions.Where(t => t.CategoryId == from).ToList().ForEach(t => { t.CategoryId = to; t.Version++; });
        State.Purchases.Where(p => p.CategoryId == from).ToList().ForEach(p => p.CategoryId = to);
        State.Recurrings.Where(r => r.CategoryId == from).ToList().ForEach(r => r.CategoryId = to);
        foreach (var budget in State.Budgets.Where(b => b.CategoryId == from).ToList())
        {
            if (State.Budgets.Any(b => b.CategoryId == to && b.Month == budget.Month))
            {
                State.Budgets.Remove(budget);
            }
            else
            {
                budget.CategoryId = to;
            }
        }
    }

    private void RequireUniqueCategoryName(string name, CategoryType type, Guid? exceptId)
    {
        if (State.Categories.Any(c => c.Id != exceptId && c.Type == type
                && string.Equals(c.Name, name.Trim(), StringComparison.OrdinalIgnoreCase)))
        {
            throw DemoException.Conflict("Já existe uma categoria com este nome.");
        }
    }

    // ---------- Movimentações ----------

    public PageResponse<TransactionResponse> ListTransactions(IReadOnlyDictionary<string, string> query)
    {
        var page = int.Parse(query.GetValueOrDefault("page", "0"));
        var size = int.Parse(query.GetValueOrDefault("size", "20"));
        IEnumerable<DemoTransaction> items = State.Transactions;

        if (query.TryGetValue("search", out var search) && !string.IsNullOrWhiteSpace(search))
        {
            items = items.Where(t => t.Description.Contains(search.Trim(), StringComparison.OrdinalIgnoreCase));
        }
        if (query.TryGetValue("type", out var type))
        {
            items = items.Where(t => t.Type == Enum.Parse<TransactionType>(type));
        }
        if (query.TryGetValue("status", out var status))
        {
            items = items.Where(t => t.Status == Enum.Parse<TransactionStatus>(status));
        }
        if (query.TryGetValue("categoryId", out var categoryId))
        {
            items = items.Where(t => t.CategoryId == Guid.Parse(categoryId));
        }
        var from = query.TryGetValue("from", out var fromText) ? ParseIso(fromText) : (DateOnly?)null;
        var to = query.TryGetValue("to", out var toText) ? ParseIso(toText) : (DateOnly?)null;
        if (from > to)
        {
            throw DemoException.Invalid("A data inicial não pode ser posterior à data final.");
        }
        if (from is not null)
        {
            items = items.Where(t => t.TransactionDate >= from);
        }
        if (to is not null)
        {
            items = items.Where(t => t.TransactionDate <= to);
        }

        return Page(Sort(items, query.GetValueOrDefault("sort")).Select(ToResponse), page, size);
    }

    /// <summary>Mesmos campos do TransactionPaging; o desempate é pela criação mais recente.</summary>
    private static IEnumerable<DemoTransaction> Sort(IEnumerable<DemoTransaction> items, string? sort)
    {
        var parts = (sort ?? "transactionDate,desc").Split(',');
        var descending = parts.Length > 1 && parts[1].Trim().Equals("desc", StringComparison.OrdinalIgnoreCase);
        Func<DemoTransaction, IComparable?> key = parts[0].Trim() switch
        {
            "amount" => t => t.Amount,
            "description" => t => t.Description.ToLowerInvariant(),
            "status" => t => t.Status.ToString(),
            "dueDate" => t => t.DueDate,
            _ => t => t.TransactionDate
        };
        var ordered = descending ? items.OrderByDescending(key) : items.OrderBy(key);
        return ordered.ThenByDescending(t => t.CreatedAt);
    }

    public TransactionResponse CreateTransaction(TransactionRequest request)
    {
        ResolveReferences(request, null);
        var transaction = new DemoTransaction { CreatedAt = Tick(), Kind = TransactionKind.STANDARD };
        Apply(transaction, request);
        State.Transactions.Add(transaction);
        Save();
        return ToResponse(transaction);
    }

    public TransactionResponse UpdateTransaction(Guid id, TransactionRequest request)
    {
        var transaction = FindEditable(id);
        if (request.Version is { } version && version != transaction.Version)
        {
            throw DemoException.Conflict("Esta movimentação foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        ResolveReferences(request, transaction);
        Apply(transaction, request);
        transaction.Version++;
        Save();
        return ToResponse(transaction);
    }

    public TransactionResponse CompleteTransaction(Guid id)
    {
        var transaction = FindEditable(id);
        if (transaction.Status == TransactionStatus.CANCELED)
        {
            throw DemoException.Rule("Uma movimentação cancelada não pode ser efetivada.");
        }
        transaction.Status = TransactionStatus.COMPLETED;
        transaction.Version++;
        Save();
        return ToResponse(transaction);
    }

    public void DeleteTransaction(Guid id)
    {
        State.Transactions.Remove(FindEditable(id));
        Save();
    }

    private void ResolveReferences(TransactionRequest request, DemoTransaction? current)
    {
        UsableCategory(request.CategoryId, request.Type.ToCategoryType(),
            "A categoria não corresponde ao tipo da movimentação.", current?.CategoryId);
        UsableAccount(request.AccountId, current?.AccountId);
    }

    private static void Apply(DemoTransaction transaction, TransactionRequest request)
    {
        transaction.Description = request.Description.Trim();
        transaction.Amount = request.Amount;
        transaction.Type = request.Type;
        transaction.Status = request.Status;
        transaction.CategoryId = request.CategoryId;
        transaction.AccountId = request.AccountId;
        transaction.TransactionDate = request.TransactionDate;
        transaction.DueDate = request.DueDate;
        transaction.Notes = string.IsNullOrWhiteSpace(request.Notes) ? null : request.Notes.Trim();
    }

    /// <summary>Parcelas de cartão e pagamentos de fatura só mudam pelos fluxos de cartão.</summary>
    private DemoTransaction FindEditable(Guid id)
    {
        var transaction = State.Transactions.FirstOrDefault(t => t.Id == id)
            ?? throw DemoException.NotFound("Movimentação não encontrada.");
        if (transaction.Kind != TransactionKind.STANDARD)
        {
            throw DemoException.Rule("Esta movimentação só pode ser alterada pela área de cartões.");
        }
        return transaction;
    }

    // ---------- Orçamentos ----------

    public BudgetMonthResponse BudgetMonth(string? month)
    {
        var reference = string.IsNullOrEmpty(month) ? Today.ToString("yyyy-MM", Invariant) : month;
        var items = State.Budgets.Where(b => b.Month == reference)
            .Select(b => BudgetResponse(b))
            .OrderBy(b => b.CategoryName, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
        return new BudgetMonthResponse(reference, items.Sum(b => b.LimitAmount), items.Sum(b => b.UsedAmount), items);
    }

    public BudgetResponse CreateBudget(BudgetRequest request)
    {
        UsableCategory(request.CategoryId, CategoryType.EXPENSE,
            "Orçamentos só podem ser criados para categorias de despesa.", null);
        if (State.Budgets.Any(b => b.CategoryId == request.CategoryId && b.Month == request.Month))
        {
            throw DemoException.Conflict("Já existe um orçamento para esta categoria neste mês.");
        }
        var budget = new DemoBudget { CategoryId = request.CategoryId, Month = request.Month, LimitAmount = request.LimitAmount };
        State.Budgets.Add(budget);
        Save();
        return BudgetResponse(budget);
    }

    public BudgetResponse UpdateBudget(Guid id, BudgetLimitRequest request)
    {
        var budget = FindBudget(id);
        budget.LimitAmount = request.LimitAmount;
        Save();
        return BudgetResponse(budget);
    }

    public void DeleteBudget(Guid id)
    {
        State.Budgets.Remove(FindBudget(id));
        Save();
    }

    private DemoBudget FindBudget(Guid id) =>
        State.Budgets.FirstOrDefault(b => b.Id == id) ?? throw DemoException.NotFound("Orçamento não encontrado.");

    /// <summary>O orçamento mede o comprometido: inclui pendentes e exclui pagamentos de fatura.</summary>
    private decimal CommittedIn(Guid categoryId, string month)
    {
        var first = ParseIso(month + "-01");
        var last = first.AddMonths(1).AddDays(-1);
        return InPeriod(first, last)
            .Where(t => t.CategoryId == categoryId && t.Type == TransactionType.EXPENSE
                && t.Status != TransactionStatus.CANCELED && t.Kind != TransactionKind.INVOICE_PAYMENT)
            .Sum(t => t.Amount);
    }

    private BudgetResponse BudgetResponse(DemoBudget budget)
    {
        var category = FindCategory(budget.CategoryId);
        var used = CommittedIn(budget.CategoryId, budget.Month);
        var percentage = Math.Floor(used * 100m / budget.LimitAmount * 100m) / 100m;
        return new BudgetResponse(budget.Id, category.Id, category.Name, category.Icon, category.Color, budget.Month,
            budget.LimitAmount, used, budget.LimitAmount - used, percentage, StateOf(used, budget.LimitAmount));
    }

    /// <summary>Sem arredondar: 79,99% é normal e 100,01% excedeu.</summary>
    private static BudgetState StateOf(decimal used, decimal limit) =>
        used > limit ? BudgetState.EXCEEDED : used * 100 >= limit * 80 ? BudgetState.WARNING : BudgetState.NORMAL;
}
