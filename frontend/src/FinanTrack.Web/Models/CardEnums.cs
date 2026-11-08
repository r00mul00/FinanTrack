using System.Text.Json.Serialization;
using MudBlazor;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<CardBrand>))]
public enum CardBrand
{
    VISA,
    MASTERCARD,
    ELO,
    AMEX,
    HIPERCARD,
    OTHER
}

[JsonConverter(typeof(JsonStringEnumConverter<InvoiceStatus>))]
public enum InvoiceStatus
{
    OPEN,
    CLOSED,
    OVERDUE,
    PAID
}

public static class CardDisplay
{
    public static string Label(this CardBrand brand) => brand switch
    {
        CardBrand.VISA => "Visa",
        CardBrand.MASTERCARD => "Mastercard",
        CardBrand.ELO => "Elo",
        CardBrand.AMEX => "American Express",
        CardBrand.HIPERCARD => "Hipercard",
        _ => "Outra"
    };

    public static string Label(this InvoiceStatus status) => status switch
    {
        InvoiceStatus.OPEN => "Aberta",
        InvoiceStatus.CLOSED => "Fechada",
        InvoiceStatus.OVERDUE => "Vencida",
        _ => "Paga"
    };

    public static Color Color(this InvoiceStatus status) => status switch
    {
        InvoiceStatus.OVERDUE => MudBlazor.Color.Error,
        InvoiceStatus.PAID => MudBlazor.Color.Success,
        InvoiceStatus.CLOSED => MudBlazor.Color.Warning,
        _ => MudBlazor.Color.Default
    };

    public static string Icon(this InvoiceStatus status) => status switch
    {
        InvoiceStatus.OVERDUE => Icons.Material.Rounded.ErrorOutline,
        InvoiceStatus.PAID => Icons.Material.Rounded.CheckCircleOutline,
        InvoiceStatus.CLOSED => Icons.Material.Rounded.Lock,
        _ => Icons.Material.Rounded.Schedule
    };
}
