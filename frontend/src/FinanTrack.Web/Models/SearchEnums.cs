using System.Text.Json.Serialization;
using MudBlazor;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<SearchResultType>))]
public enum SearchResultType
{
    TRANSACTION,
    CATEGORY,
    GOAL,
    CARD,
    RECURRING
}

public static class SearchDisplay
{
    public static string Label(this SearchResultType type) => type switch
    {
        SearchResultType.TRANSACTION => "Transação",
        SearchResultType.CATEGORY => "Categoria",
        SearchResultType.GOAL => "Meta",
        SearchResultType.CARD => "Cartão",
        _ => "Recorrência"
    };

    public static string Icon(this SearchResultType type) => type switch
    {
        SearchResultType.TRANSACTION => Icons.Material.Rounded.SwapHoriz,
        SearchResultType.CATEGORY => Icons.Material.Rounded.Category,
        SearchResultType.GOAL => Icons.Material.Rounded.Flag,
        SearchResultType.CARD => Icons.Material.Rounded.CreditCard,
        _ => Icons.Material.Rounded.Autorenew
    };
}
