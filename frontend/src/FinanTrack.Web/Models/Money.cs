using System.Globalization;

namespace FinanTrack.Web.Models;

/// <summary>A moeda escolhida altera apenas a exibição: os valores não são convertidos.</summary>
public static class Money
{
    public const decimal MaxValue = 999_999_999_999.99m;
    public const string DefaultCurrency = "BRL";

    public static readonly IReadOnlyList<(string Code, string Label)> Currencies =
    [
        ("BRL", "Real (R$)"),
        ("USD", "Dólar americano (US$)"),
        ("EUR", "Euro (€)"),
        ("GBP", "Libra esterlina (£)")
    ];

    private static readonly Dictionary<string, string> Symbols = new()
    {
        ["BRL"] = "R$",
        ["USD"] = "US$",
        ["EUR"] = "€",
        ["GBP"] = "£"
    };

    public static CultureInfo Culture { get; private set; } = BrazilianCulture.Instance;

    public static string Symbol => Culture.NumberFormat.CurrencySymbol;

    public static void UseCurrency(string? code)
    {
        var symbol = Symbols.GetValueOrDefault(code ?? DefaultCurrency, Symbols[DefaultCurrency]);
        var culture = (CultureInfo)BrazilianCulture.Instance.Clone();
        culture.NumberFormat.CurrencySymbol = symbol;
        Culture = culture;
    }

    public static string Format(decimal value) => value.ToString("C2", Culture);
}
