using System.Globalization;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

/// <summary>Todas as leituras retornam null se os dados não puderem ser carregados.</summary>
public sealed class DashboardApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/dashboard";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<DashboardSummaryResponse?> GetSummaryAsync(DateOnly from, DateOnly to) =>
        GetAsync<DashboardSummaryResponse>("summary", from, to);

    public Task<IncomeVsExpenseResponse?> GetIncomeVsExpenseAsync(DateOnly from, DateOnly to) =>
        GetAsync<IncomeVsExpenseResponse>("income-vs-expense", from, to);

    public Task<List<CategoryExpenseResponse>?> GetExpensesByCategoryAsync(DateOnly from, DateOnly to) =>
        GetAsync<List<CategoryExpenseResponse>>("expenses-by-category", from, to);

    public Task<List<UpcomingDueResponse>?> GetUpcomingAsync() =>
        Http.GetOrNullAsync<List<UpcomingDueResponse>>($"{Path}/upcoming");

    private Task<T?> GetAsync<T>(string resource, DateOnly from, DateOnly to) where T : class =>
        Http.GetOrNullAsync<T>($"{Path}/{resource}?from={Iso(from)}&to={Iso(to)}");

    private static string Iso(DateOnly date) => date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
}
