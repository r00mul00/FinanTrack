using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class GoalApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/goals";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se a lista não puder ser carregada.</summary>
    public Task<List<GoalResponse>?> ListAsync() => Http.GetOrNullAsync<List<GoalResponse>>(Path);

    /// <summary>Retorna null se o histórico não puder ser carregado.</summary>
    public Task<PageResponse<GoalMovementResponse>?> GetMovementsAsync(Guid id) =>
        Http.GetOrNullAsync<PageResponse<GoalMovementResponse>>($"{Path}/{id}/movements?size=100");

    public Task<ApiResult> CreateAsync(GoalRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, GoalRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> MoveAsync(Guid id, GoalMovementType type, GoalMovementRequest request)
    {
        var action = type == GoalMovementType.DEPOSIT ? "deposits" : "withdrawals";
        return ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Path}/{id}/{action}", request));
    }

    public Task<ApiResult> CompleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/{id}/complete", null));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));
}
