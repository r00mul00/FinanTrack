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
    public Task<BudgetMonthResponse?> GetMonthAsync(string month) =>
        Http.GetOrNullAsync<BudgetMonthResponse>($"{Path}?month={month}");

    public Task<ApiResult> CreateAsync(BudgetRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateLimitAsync(Guid id, decimal limit) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", new BudgetLimitRequest(limit)));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));
}
