using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>Metas (GoalService) e contas recorrentes (RecurringService e RecurringOccurrenceWriter).</summary>
public sealed partial class DemoStore
{
    private const decimal MaxGoalAmount = 999_999_999_999.99m;

    // ---------- Metas ----------

    /// <summary>Ativas primeiro, depois pelo prazo mais próximo.</summary>
    public List<GoalResponse> ListGoals() =>
        State.Goals.OrderBy(g => g.Status).ThenBy(g => g.Deadline).Select(ToResponse).ToList();

    public GoalResponse CreateGoal(GoalRequest request)
    {
        if (request.Deadline < Today)
        {
            throw DemoException.Rule("O prazo não pode estar no passado.");
        }
        var goal = new DemoGoal
        {
            Name = request.Name.Trim(),
            Description = request.Description,
            TargetAmount = request.TargetAmount,
            InitialAmount = request.InitialAmount,
            CurrentAmount = request.InitialAmount,
            Deadline = request.Deadline,
            Status = GoalStatus.ACTIVE
        };
        State.Goals.Add(goal);
        Save();
        return ToResponse(goal);
    }

    public GoalResponse UpdateGoal(Guid id, GoalRequest request)
    {
        var goal = FindGoal(id);
        if (request.Version is { } version && version != goal.Version)
        {
            throw DemoException.Conflict("Esta meta foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        var newCurrent = goal.CurrentAmount + (request.InitialAmount - goal.InitialAmount);
        if (newCurrent < 0)
        {
            throw DemoException.Rule("O novo valor inicial deixaria o saldo da meta negativo.");
        }
        RequireWithinLimit(newCurrent);

        goal.Name = request.Name.Trim();
        goal.Description = request.Description;
        goal.TargetAmount = request.TargetAmount;
        goal.InitialAmount = request.InitialAmount;
        goal.CurrentAmount = newCurrent;
        goal.Deadline = request.Deadline;
        goal.Version++;
        Save();
        return ToResponse(goal);
    }

    public GoalResponse MoveGoal(Guid id, GoalMovementType type, GoalMovementRequest request)
    {
        var goal = FindGoal(id);
        if (goal.Status == GoalStatus.COMPLETED)
        {
            throw DemoException.Rule("Esta meta já foi concluída e não aceita movimentações.");
        }
        if (type == GoalMovementType.DEPOSIT)
        {
            RequireWithinLimit(goal.CurrentAmount + request.Amount);
            goal.CurrentAmount += request.Amount;
        }
        else
        {
            if (request.Amount > goal.CurrentAmount)
            {
                throw DemoException.Rule("A retirada é maior que o saldo disponível na meta.");
            }
            goal.CurrentAmount -= request.Amount;
        }
        goal.Version++;
        State.GoalMovements.Add(new DemoGoalMovement
        {
            GoalId = goal.Id, Type = type, Amount = request.Amount, Note = request.Note, CreatedAt = DateTimeOffset.Now
        });
        Save();
        return ToResponse(goal);
    }

    /// <summary>Concluir de novo não altera nada.</summary>
    public GoalResponse CompleteGoal(Guid id)
    {
        var goal = FindGoal(id);
        if (goal.Status == GoalStatus.ACTIVE)
        {
            goal.Status = GoalStatus.COMPLETED;
            goal.Version++;
            Save();
        }
        return ToResponse(goal);
    }

    public void DeleteGoal(Guid id)
    {
        var goal = FindGoal(id);
        State.GoalMovements.RemoveAll(m => m.GoalId == id);
        State.Goals.Remove(goal);
        Save();
    }

    public PageResponse<GoalMovementResponse> GoalMovements(Guid id, int size)
    {
        FindGoal(id);
        return Page(State.GoalMovements.Where(m => m.GoalId == id)
            .OrderByDescending(m => m.CreatedAt)
            .Select(m => new GoalMovementResponse(m.Id, m.Type, m.Amount, m.Note, m.CreatedAt)), 0, size);
    }

    private DemoGoal FindGoal(Guid id) =>
        State.Goals.FirstOrDefault(g => g.Id == id) ?? throw DemoException.NotFound("Meta não encontrada.");

    private static void RequireWithinLimit(decimal value)
    {
        if (value > MaxGoalAmount)
        {
            throw DemoException.Rule("O saldo da meta ultrapassaria o valor máximo permitido.");
        }
    }

    /// <summary>O progresso é truncado e limitado a 100%.</summary>
    private static GoalResponse ToResponse(DemoGoal goal)
    {
        var progress = Math.Min(Math.Floor(goal.CurrentAmount * 100m / goal.TargetAmount * 100m) / 100m, 100m);
        var remaining = Math.Max(goal.TargetAmount - goal.CurrentAmount, 0);
        var overdue = goal.Status == GoalStatus.ACTIVE && goal.Deadline < Today;
        return new GoalResponse(goal.Id, goal.Name, goal.Description, goal.TargetAmount, goal.InitialAmount,
            goal.CurrentAmount, remaining, progress, goal.Deadline, overdue, goal.Status, goal.Version);
    }

    // ---------- Recorrências ----------

    public List<RecurringResponse> ListRecurring() =>
        State.Recurrings.OrderBy(r => r.Description, StringComparer.CurrentCultureIgnoreCase).Select(ToResponse).ToList();

    /// <summary>Ciclos com data até hoje são gerados na hora.</summary>
    public RecurringResponse CreateRecurring(RecurringRequest request)
    {
        RequireValidPeriod(request);
        ResolveReferences(request, null);
        var item = new DemoRecurring { StartDate = request.StartDate, NextOccurrenceDate = request.StartDate, Status = RecurringStatus.ACTIVE };
        Apply(item, request);
        State.Recurrings.Add(item);
        CatchUp(item, Today);
        Save();
        return ToResponse(item);
    }

    /// <summary>Vale só para os próximos ciclos; os lançamentos já gerados não mudam.</summary>
    public RecurringResponse UpdateRecurring(Guid id, RecurringRequest request)
    {
        var item = FindRecurring(id);
        if (request.Version is { } version && version != item.Version)
        {
            throw DemoException.Conflict("Esta recorrência foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        RequireNotFinished(item);
        if (item.StartDate != request.StartDate)
        {
            throw DemoException.Rule("A data de início não pode ser alterada.");
        }
        RequireValidPeriod(request);
        ResolveReferences(request, item);

        var frequencyChanged = item.Frequency != request.Frequency;
        Apply(item, request);
        var next = item.NextOccurrenceDate;
        if (frequencyChanged && next is not null)
        {
            next = FirstOnOrAfter(item.StartDate, item.Frequency, next.Value);
        }
        ApplyCursor(item, next);
        item.Version++;
        Save();
        return ToResponse(item);
    }

    public RecurringResponse PauseRecurring(Guid id)
    {
        var item = FindRecurring(id);
        RequireNotFinished(item);
        item.Status = RecurringStatus.PAUSED;
        item.Version++;
        Save();
        return ToResponse(item);
    }

    /// <summary>Não gera retroativos: o período pausado fica sem lançamentos.</summary>
    public RecurringResponse ResumeRecurring(Guid id)
    {
        var item = FindRecurring(id);
        RequireNotFinished(item);
        if (item.Status == RecurringStatus.PAUSED)
        {
            item.Status = RecurringStatus.ACTIVE;
            var from = item.NextOccurrenceDate > Today ? item.NextOccurrenceDate.Value : Today;
            ApplyCursor(item, FirstOnOrAfter(item.StartDate, item.Frequency, from));
            CatchUp(item, Today);
            item.Version++;
            Save();
        }
        return ToResponse(item);
    }

    /// <summary>Os lançamentos já gerados permanecem, sem o vínculo com a recorrência.</summary>
    public void DeleteRecurring(Guid id)
    {
        var item = FindRecurring(id);
        State.Transactions.Where(t => t.RecurringId == id).ToList().ForEach(t => t.RecurringId = null);
        State.Recurrings.Remove(item);
        Save();
    }

    public List<DateOnly> UpcomingOccurrences(Guid id, int count)
    {
        var item = FindRecurring(id);
        var dates = new List<DateOnly>();
        if (item.Status != RecurringStatus.ACTIVE)
        {
            return dates;
        }
        var next = item.NextOccurrenceDate;
        while (next is not null && dates.Count < count && (item.EndDate is null || next <= item.EndDate))
        {
            dates.Add(next.Value);
            next = FirstOnOrAfter(item.StartDate, item.Frequency, next.Value.AddDays(1));
        }
        return dates;
    }

    /// <summary>Gera os lançamentos pendentes de cada ciclo até hoje.</summary>
    private void CatchUp(DemoRecurring item, DateOnly today)
    {
        if (item.Status != RecurringStatus.ACTIVE)
        {
            return;
        }
        var next = item.NextOccurrenceDate;
        while (next is not null && next <= today && WithinPeriod(item, next.Value))
        {
            var date = next.Value;
            if (!State.Transactions.Any(t => t.RecurringId == item.Id && t.OccurrenceDate == date))
            {
                State.Transactions.Add(new DemoTransaction
                {
                    Description = item.Description,
                    Amount = item.Amount,
                    Type = item.Type,
                    Kind = TransactionKind.STANDARD,
                    Status = TransactionStatus.PENDING,
                    CategoryId = item.CategoryId,
                    AccountId = item.AccountId,
                    TransactionDate = date,
                    DueDate = date,
                    RecurringId = item.Id,
                    OccurrenceDate = date,
                    CreatedAt = Tick()
                });
            }
            next = FirstOnOrAfter(item.StartDate, item.Frequency, date.AddDays(1));
        }
        ApplyCursor(item, next);
    }

    /// <summary>Sem próximo ciclo dentro do período, a recorrência se encerra.</summary>
    private static void ApplyCursor(DemoRecurring item, DateOnly? next)
    {
        if (next is null || !WithinPeriod(item, next.Value))
        {
            item.Status = RecurringStatus.FINISHED;
            item.NextOccurrenceDate = null;
        }
        else
        {
            item.NextOccurrenceDate = next;
        }
    }

    private static bool WithinPeriod(DemoRecurring item, DateOnly date) => item.EndDate is null || date <= item.EndDate;

    /// <summary>Cada ciclo parte do início: dia 31 vira 28/02 e volta a 31/03.</summary>
    private static DateOnly Occurrence(DateOnly start, RecurrenceFrequency frequency, int index) => frequency switch
    {
        RecurrenceFrequency.WEEKLY => start.AddDays(7 * index),
        RecurrenceFrequency.BIWEEKLY => start.AddDays(14 * index),
        RecurrenceFrequency.QUARTERLY => start.AddMonths(3 * index),
        RecurrenceFrequency.YEARLY => start.AddYears(index),
        _ => start.AddMonths(index)
    };

    private static DateOnly FirstOnOrAfter(DateOnly start, RecurrenceFrequency frequency, DateOnly date)
    {
        var index = 0;
        var candidate = start;
        while (candidate < date)
        {
            candidate = Occurrence(start, frequency, ++index);
        }
        return candidate;
    }

    private void ResolveReferences(RecurringRequest request, DemoRecurring? current)
    {
        UsableCategory(request.CategoryId, request.Type.ToCategoryType(),
            "A categoria não corresponde ao tipo da recorrência.", current?.CategoryId);
        UsableAccount(request.AccountId, current?.AccountId);
    }

    private static void Apply(DemoRecurring item, RecurringRequest request)
    {
        item.Description = request.Description.Trim();
        item.Amount = request.Amount;
        item.Type = request.Type;
        item.CategoryId = request.CategoryId;
        item.AccountId = request.AccountId;
        item.Frequency = request.Frequency;
        item.EndDate = request.EndDate;
    }

    private static void RequireValidPeriod(RecurringRequest request)
    {
        if (request.EndDate < request.StartDate)
        {
            throw DemoException.Rule("A data de término não pode ser anterior à de início.");
        }
    }

    private static void RequireNotFinished(DemoRecurring item)
    {
        if (item.Status == RecurringStatus.FINISHED)
        {
            throw DemoException.Rule("Esta recorrência já foi encerrada.");
        }
    }

    private DemoRecurring FindRecurring(Guid id) =>
        State.Recurrings.FirstOrDefault(r => r.Id == id) ?? throw DemoException.NotFound("Recorrência não encontrada.");

    private RecurringResponse ToResponse(DemoRecurring r)
    {
        var category = FindCategory(r.CategoryId);
        var account = FindAccount(r.AccountId);
        return new RecurringResponse(r.Id, r.Description, r.Amount, r.Type, category.Id, category.Name, category.Icon,
            category.Color, account.Id, account.Name, r.Frequency, r.StartDate, r.EndDate, r.NextOccurrenceDate,
            r.Status, r.Version);
    }
}
