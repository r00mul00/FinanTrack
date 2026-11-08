using System.Globalization;

namespace FinanTrack.Web.Models;

/// <summary>
/// Formato brasileiro definido explicitamente, sem depender dos dados de
/// globalização que o navegador baixa para cada idioma.
/// </summary>
public static class BrazilianCulture
{
    public static CultureInfo Instance { get; } = Create();

    private static CultureInfo Create()
    {
        var culture = (CultureInfo)CultureInfo.InvariantCulture.Clone();

        var number = culture.NumberFormat;
        number.CurrencySymbol = "R$";
        number.CurrencyDecimalSeparator = ",";
        number.CurrencyGroupSeparator = ".";
        number.NumberDecimalSeparator = ",";
        number.NumberGroupSeparator = ".";
        number.CurrencyPositivePattern = 2;
        number.CurrencyNegativePattern = 9;

        var date = culture.DateTimeFormat;
        date.ShortDatePattern = "dd/MM/yyyy";
        date.FirstDayOfWeek = DayOfWeek.Sunday;
        date.DayNames = ["domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado"];
        date.AbbreviatedDayNames = ["dom", "seg", "ter", "qua", "qui", "sex", "sáb"];
        date.ShortestDayNames = ["D", "S", "T", "Q", "Q", "S", "S"];
        date.MonthNames = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro", ""];
        date.AbbreviatedMonthNames = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez", ""];
        date.MonthGenitiveNames = date.MonthNames;
        date.AbbreviatedMonthGenitiveNames = date.AbbreviatedMonthNames;

        return culture;
    }
}
