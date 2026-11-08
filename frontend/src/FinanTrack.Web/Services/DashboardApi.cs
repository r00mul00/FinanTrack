using System.Globalization;
using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class DashboardApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/dashboard";

    public Task<DashboardSummaryResponse?> GetSummaryAsync(DateOnly from, DateOnly to) =>
        GetAsync<DashboardSummaryResponse>("summary", from, to);

    public Task<IncomeVsExpenseResponse?> GetIncomeVsExpenseAsync(DateOnly from, DateOnly to) =>
        GetAsync<IncomeVsExpenseResponse>("income-vs-expense", from, to);

    public Task<List<CategoryExpenseResponse>?> GetExpensesByCategoryAsync(DateOnly from, DateOnly to) =>
        GetAsync<List<CategoryExpenseResponse>>("expenses-by-category", from, to);

    /// <summary>Retorna null se os dados não puderem ser carregados.</summary>
    private async Task<T?> GetAsync<T>(string resource, DateOnly from, DateOnly to) where T : class
    {
        try
        {
            var http = httpClientFactory.CreateClient(HttpClients.Api);
            var response = await http.GetAsync($"{Path}/{resource}?from={Iso(from)}&to={Iso(to)}");
            return response.IsSuccessStatusCode ? await response.Content.ReadFromJsonAsync<T>() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    private static string Iso(DateOnly date) => date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
}
