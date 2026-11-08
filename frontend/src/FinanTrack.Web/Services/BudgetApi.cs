using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class BudgetApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/budgets";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se o mês não puder ser carregado.</summary>
    public async Task<BudgetMonthResponse?> GetMonthAsync(string month)
    {
        try
        {
            var response = await Http.GetAsync($"{Path}?month={month}");
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<BudgetMonthResponse>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public Task<ApiResult> CreateAsync(BudgetRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateLimitAsync(Guid id, decimal limit) =>
        SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", new BudgetLimitRequest(limit)));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));

    private static async Task<ApiResult> SendAsync(Func<Task<HttpResponseMessage>> send)
    {
        try
        {
            return await ApiResult.FromResponseAsync(await send());
        }
        catch (HttpRequestException)
        {
            return ApiResult.NetworkFailure();
        }
    }
}
