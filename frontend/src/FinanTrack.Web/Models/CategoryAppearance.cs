using MudBlazor;

namespace FinanTrack.Web.Models;

/// <summary>A API guarda só a chave do ícone; o desenho fica no frontend.</summary>
public static class CategoryAppearance
{
    public const string DefaultColor = "#7C4DFF";
    public const string DefaultIcon = "Category";

    public static IReadOnlyDictionary<string, string> Icons { get; } = new Dictionary<string, string>
    {
        ["Category"] = MudBlazor.Icons.Material.Rounded.Category,
        ["Restaurant"] = MudBlazor.Icons.Material.Rounded.Restaurant,
        ["LocalGroceryStore"] = MudBlazor.Icons.Material.Rounded.LocalGroceryStore,
        ["Home"] = MudBlazor.Icons.Material.Rounded.Home,
        ["DirectionsCar"] = MudBlazor.Icons.Material.Rounded.DirectionsCar,
        ["LocalGasStation"] = MudBlazor.Icons.Material.Rounded.LocalGasStation,
        ["LocalHospital"] = MudBlazor.Icons.Material.Rounded.LocalHospital,
        ["FitnessCenter"] = MudBlazor.Icons.Material.Rounded.FitnessCenter,
        ["School"] = MudBlazor.Icons.Material.Rounded.School,
        ["SportsEsports"] = MudBlazor.Icons.Material.Rounded.SportsEsports,
        ["Flight"] = MudBlazor.Icons.Material.Rounded.Flight,
        ["ShoppingBag"] = MudBlazor.Icons.Material.Rounded.ShoppingBag,
        ["Checkroom"] = MudBlazor.Icons.Material.Rounded.Checkroom,
        ["Pets"] = MudBlazor.Icons.Material.Rounded.Pets,
        ["ChildCare"] = MudBlazor.Icons.Material.Rounded.ChildCare,
        ["CardGiftcard"] = MudBlazor.Icons.Material.Rounded.CardGiftcard,
        ["Receipt"] = MudBlazor.Icons.Material.Rounded.Receipt,
        ["Wifi"] = MudBlazor.Icons.Material.Rounded.Wifi,
        ["PhoneAndroid"] = MudBlazor.Icons.Material.Rounded.PhoneAndroid,
        ["MoreHoriz"] = MudBlazor.Icons.Material.Rounded.MoreHoriz,
        ["Payments"] = MudBlazor.Icons.Material.Rounded.Payments,
        ["Work"] = MudBlazor.Icons.Material.Rounded.Work,
        ["TrendingUp"] = MudBlazor.Icons.Material.Rounded.TrendingUp,
        ["Savings"] = MudBlazor.Icons.Material.Rounded.Savings,
        ["AttachMoney"] = MudBlazor.Icons.Material.Rounded.AttachMoney
    };

    public static IReadOnlyList<string> Colors { get; } =
    [
        "#7C4DFF", "#B14DDB", "#F06292", "#FF647C", "#FF8A65", "#FFB547",
        "#81C784", "#20C997", "#4DB6AC", "#4FC3F7", "#90A4AE", "#A6A8BC"
    ];

    public static string IconFor(string key) =>
        Icons.TryGetValue(key, out var icon) ? icon : Icons[DefaultIcon];
}
