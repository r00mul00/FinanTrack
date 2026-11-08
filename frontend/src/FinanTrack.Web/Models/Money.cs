using System.Globalization;

namespace FinanTrack.Web.Models;

public static class Money
{
    public const decimal MaxValue = 999_999_999_999.99m;

    public static CultureInfo Culture => BrazilianCulture.Instance;

    public static string Format(decimal value) => value.ToString("C2", Culture);
}
