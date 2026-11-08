using System.Text.Json.Serialization;
using MudBlazor;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<BudgetState>))]
public enum BudgetState
{
    NORMAL,
    WARNING,
    EXCEEDED
}

public static class BudgetStateDisplay
{
    public static string Label(this BudgetState state) => state switch
    {
        BudgetState.WARNING => "Alerta",
        BudgetState.EXCEEDED => "Excedido",
        _ => "Normal"
    };

    public static Color Color(this BudgetState state) => state switch
    {
        BudgetState.WARNING => MudBlazor.Color.Warning,
        BudgetState.EXCEEDED => MudBlazor.Color.Error,
        _ => MudBlazor.Color.Success
    };

    public static string Icon(this BudgetState state) => state switch
    {
        BudgetState.WARNING => Icons.Material.Rounded.WarningAmber,
        BudgetState.EXCEEDED => Icons.Material.Rounded.ErrorOutline,
        _ => Icons.Material.Rounded.CheckCircleOutline
    };
}
