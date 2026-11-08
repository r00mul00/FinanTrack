using System.Globalization;

namespace FinanTrack.Web.Models;

/// <summary>
/// Formato brasileiro definido explicitamente, sem depender dos dados de
/// globalização que o navegador baixa para cada idioma.
/// </summary>
public static class Money
{
    public const decimal MaxValue = 999_999_999_999.99m;

    public static CultureInfo Culture { get; } = CreateCulture();

    public static string Format(decimal value) => value.ToString("C2", Culture);

    private static CultureInfo CreateCulture()
    {
        var culture = (CultureInfo)CultureInfo.InvariantCulture.Clone();
        var format = culture.NumberFormat;
        format.CurrencySymbol = "R$";
        format.CurrencyDecimalSeparator = ",";
        format.CurrencyGroupSeparator = ".";
        format.NumberDecimalSeparator = ",";
        format.NumberGroupSeparator = ".";
        format.CurrencyPositivePattern = 2;
        format.CurrencyNegativePattern = 9;
        return culture;
    }
}
