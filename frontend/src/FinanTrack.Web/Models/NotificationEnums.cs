using System.Text.Json.Serialization;
using MudBlazor;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<NotificationType>))]
public enum NotificationType
{
    BUDGET_ALERT,
    BUDGET_EXCEEDED,
    DUE_DATE,
    INVOICE_DUE
}

public static class NotificationDisplay
{
    public static string IconOf(NotificationType type) => type switch
    {
        NotificationType.BUDGET_ALERT => Icons.Material.Rounded.WarningAmber,
        NotificationType.BUDGET_EXCEEDED => Icons.Material.Rounded.ErrorOutline,
        NotificationType.INVOICE_DUE => Icons.Material.Rounded.CreditCard,
        _ => Icons.Material.Rounded.Event
    };

    public static Color ColorOf(NotificationType type) => type switch
    {
        NotificationType.BUDGET_ALERT => Color.Warning,
        NotificationType.BUDGET_EXCEEDED => Color.Error,
        _ => Color.Primary
    };
}
