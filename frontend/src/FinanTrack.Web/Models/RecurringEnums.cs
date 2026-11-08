using System.Text.Json.Serialization;
using MudBlazor;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<RecurrenceFrequency>))]
public enum RecurrenceFrequency
{
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

[JsonConverter(typeof(JsonStringEnumConverter<RecurringStatus>))]
public enum RecurringStatus
{
    ACTIVE,
    PAUSED,
    FINISHED
}

public static class RecurringDisplay
{
    public static string Label(this RecurrenceFrequency frequency) => frequency switch
    {
        RecurrenceFrequency.WEEKLY => "Semanal",
        RecurrenceFrequency.BIWEEKLY => "Quinzenal",
        RecurrenceFrequency.QUARTERLY => "Trimestral",
        RecurrenceFrequency.YEARLY => "Anual",
        _ => "Mensal"
    };

    public static string Label(this RecurringStatus status) => status switch
    {
        RecurringStatus.PAUSED => "Pausada",
        RecurringStatus.FINISHED => "Encerrada",
        _ => "Ativa"
    };

    public static Color Color(this RecurringStatus status) => status switch
    {
        RecurringStatus.PAUSED => MudBlazor.Color.Warning,
        RecurringStatus.FINISHED => MudBlazor.Color.Default,
        _ => MudBlazor.Color.Success
    };

    public static string Icon(this RecurringStatus status) => status switch
    {
        RecurringStatus.PAUSED => Icons.Material.Rounded.PauseCircleOutline,
        RecurringStatus.FINISHED => Icons.Material.Rounded.DoNotDisturbOn,
        _ => Icons.Material.Rounded.CheckCircleOutline
    };
}
