using System.Text.Json.Serialization;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<AccountType>))]
public enum AccountType
{
    CHECKING,
    SAVINGS,
    WALLET,
    INVESTMENT,
    OTHER
}

public static class AccountTypeLabels
{
    public static string Label(this AccountType type) => type switch
    {
        AccountType.CHECKING => "Conta corrente",
        AccountType.SAVINGS => "Poupança",
        AccountType.WALLET => "Carteira",
        AccountType.INVESTMENT => "Investimento",
        _ => "Outra"
    };
}
