namespace FinanTrack.Web.State;

public enum PeriodPreset
{
    ThisMonth,
    LastMonth,
    LastThreeMonths,
    ThisYear,
    Custom
}

/// <summary>Período escolhido pelo usuário, compartilhado pelos componentes que dependem dele.</summary>
public sealed class PeriodState
{
    public PeriodState() => Select(PeriodPreset.ThisMonth);

    public PeriodPreset Preset { get; private set; }
    public DateOnly From { get; private set; }
    public DateOnly To { get; private set; }

    public event Action? Changed;

    public void Select(PeriodPreset preset)
    {
        var today = DateOnly.FromDateTime(DateTime.Today);
        var monthStart = new DateOnly(today.Year, today.Month, 1);
        (From, To) = preset switch
        {
            PeriodPreset.LastMonth => (monthStart.AddMonths(-1), monthStart.AddDays(-1)),
            PeriodPreset.LastThreeMonths => (monthStart.AddMonths(-2), monthStart.AddMonths(1).AddDays(-1)),
            PeriodPreset.ThisYear => (new DateOnly(today.Year, 1, 1), new DateOnly(today.Year, 12, 31)),
            _ => (monthStart, monthStart.AddMonths(1).AddDays(-1))
        };
        Preset = preset;
        Changed?.Invoke();
    }

    public void SelectCustom(DateOnly from, DateOnly to)
    {
        (From, To, Preset) = (from, to, PeriodPreset.Custom);
        Changed?.Invoke();
    }
}
