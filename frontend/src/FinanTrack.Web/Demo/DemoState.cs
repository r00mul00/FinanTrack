using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>
/// Tudo o que o modo demonstração guarda no navegador. Espelha as tabelas do backend
/// só no necessário para as telas; é serializado inteiro no localStorage.
/// </summary>
public sealed class DemoState
{
    public int SchemaVersion { get; set; }
    public DateOnly SeededOn { get; set; }
    public bool LoggedOut { get; set; }
    public long Clock { get; set; }

    public DemoUser User { get; set; } = new();
    public List<DemoAccount> Accounts { get; set; } = [];
    public List<DemoCategory> Categories { get; set; } = [];
    public List<DemoTransaction> Transactions { get; set; } = [];
    public List<DemoBudget> Budgets { get; set; } = [];
    public List<DemoGoal> Goals { get; set; } = [];
    public List<DemoGoalMovement> GoalMovements { get; set; } = [];
    public List<DemoCard> Cards { get; set; } = [];
    public List<DemoPurchase> Purchases { get; set; } = [];
    public List<DemoInvoice> Invoices { get; set; } = [];
    public List<DemoInstallment> Installments { get; set; } = [];
    public List<DemoRecurring> Recurrings { get; set; } = [];
    public List<DemoNotification> Notifications { get; set; } = [];
}

public sealed class DemoUser
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = "Visitante";
    public string Email { get; set; } = "demo@finantrack.dev";
    public string Currency { get; set; } = "BRL";
    public string Theme { get; set; } = "DARK";
    public bool NotifyBudget { get; set; } = true;
    public bool NotifyDueDates { get; set; } = true;
}

public sealed class DemoAccount
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = "";
    public AccountType Type { get; set; }
    public decimal InitialBalance { get; set; }
    public bool Archived { get; set; }
}

public sealed class DemoCategory
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = "";
    public CategoryType Type { get; set; }
    public string Icon { get; set; } = "";
    public string Color { get; set; } = "";
    public bool Archived { get; set; }
}

public sealed class DemoTransaction
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Description { get; set; } = "";
    public decimal Amount { get; set; }
    public TransactionType Type { get; set; }
    public TransactionKind Kind { get; set; }
    public TransactionStatus Status { get; set; }
    public Guid? CategoryId { get; set; }
    public Guid? AccountId { get; set; }
    public DateOnly TransactionDate { get; set; }
    public DateOnly? DueDate { get; set; }
    public string? Notes { get; set; }
    public Guid? RecurringId { get; set; }
    public DateOnly? OccurrenceDate { get; set; }
    public Guid? InvoiceId { get; set; }
    public long Version { get; set; }

    /// <summary>Ordem de criação, usada como desempate, como o createdAt do backend.</summary>
    public long CreatedAt { get; set; }
}

public sealed class DemoBudget
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid CategoryId { get; set; }
    public string Month { get; set; } = "";
    public decimal LimitAmount { get; set; }
}

public sealed class DemoGoal
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public decimal TargetAmount { get; set; }
    public decimal InitialAmount { get; set; }
    public decimal CurrentAmount { get; set; }
    public DateOnly Deadline { get; set; }
    public GoalStatus Status { get; set; }
    public long Version { get; set; }
}

public sealed class DemoGoalMovement
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid GoalId { get; set; }
    public GoalMovementType Type { get; set; }
    public decimal Amount { get; set; }
    public string? Note { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}

public sealed class DemoCard
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Nickname { get; set; } = "";
    public CardBrand Brand { get; set; }
    public string LastFour { get; set; } = "";
    public decimal CreditLimit { get; set; }
    public int ClosingDay { get; set; }
    public int DueDay { get; set; }
    public bool Archived { get; set; }
}

public sealed class DemoPurchase
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid CardId { get; set; }
    public Guid CategoryId { get; set; }
    public string Description { get; set; } = "";
    public decimal TotalAmount { get; set; }
    public int Installments { get; set; }
    public DateOnly PurchaseDate { get; set; }
    public bool Canceled { get; set; }
    public long CreatedAt { get; set; }
}

public sealed class DemoInvoice
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid CardId { get; set; }
    public string ReferenceMonth { get; set; } = "";
    public DateOnly ClosingDate { get; set; }
    public DateOnly DueDate { get; set; }
    public decimal PaidAmount { get; set; }
}

public sealed class DemoInstallment
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid PurchaseId { get; set; }
    public Guid InvoiceId { get; set; }
    public Guid TransactionId { get; set; }
    public int Number { get; set; }
    public decimal Amount { get; set; }
}

public sealed class DemoRecurring
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Description { get; set; } = "";
    public decimal Amount { get; set; }
    public TransactionType Type { get; set; }
    public Guid CategoryId { get; set; }
    public Guid AccountId { get; set; }
    public RecurrenceFrequency Frequency { get; set; }
    public DateOnly StartDate { get; set; }
    public DateOnly? EndDate { get; set; }
    public DateOnly? NextOccurrenceDate { get; set; }
    public RecurringStatus Status { get; set; }
    public long Version { get; set; }
}

public sealed class DemoNotification
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public NotificationType Type { get; set; }
    public string Title { get; set; } = "";
    public string Message { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? ReadAt { get; set; }
}

/// <summary>Erro de regra de negócio, devolvido ao frontend como ProblemDetail.</summary>
public sealed class DemoException(int status, string message) : Exception(message)
{
    public int Status { get; } = status;

    public static DemoException NotFound(string message) => new(404, message);
    public static DemoException Conflict(string message) => new(409, message);
    public static DemoException Rule(string message) => new(422, message);
    public static DemoException Invalid(string message) => new(400, message);
}
