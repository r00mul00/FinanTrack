using System.Text.Json.Serialization;

namespace FinanTrack.Web.Models;

[JsonConverter(typeof(JsonStringEnumConverter<CategoryType>))]
public enum CategoryType
{
    EXPENSE,
    INCOME
}

public static class CategoryTypeLabels
{
    public static string Label(this CategoryType type) => type == CategoryType.EXPENSE ? "Despesa" : "Receita";
}
