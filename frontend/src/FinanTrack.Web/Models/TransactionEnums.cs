using System.Text.Json.Serialization;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<TransactionType>))]
public enum TransactionType
{
    EXPENSE,
    INCOME
}

[JsonConverter(typeof(JsonStringEnumConverter<TransactionStatus>))]
public enum TransactionStatus
{
    PENDING,
    COMPLETED,
    CANCELED
}

[JsonConverter(typeof(JsonStringEnumConverter<TransactionKind>))]
public enum TransactionKind
{
    STANDARD,
    CARD_INSTALLMENT,
    INVOICE_PAYMENT
}

public static class TransactionLabels
{
    public static string Label(this TransactionType type) => type == TransactionType.EXPENSE ? "Despesa" : "Receita";

    public static string Label(this TransactionStatus status) => status switch
    {
        TransactionStatus.PENDING => "Pendente",
        TransactionStatus.COMPLETED => "Efetivada",
        _ => "Cancelada"
    };

    public static CategoryType ToCategoryType(this TransactionType type) =>
        type == TransactionType.EXPENSE ? CategoryType.EXPENSE : CategoryType.INCOME;
}
