using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class RecurringApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/recurring";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<List<RecurringResponse>?> ListAsync() => Http.GetOrNullAsync<List<RecurringResponse>>(Path);

    public Task<List<DateOnly>?> GetUpcomingAsync(Guid id) => Http.GetOrNullAsync<List<DateOnly>>($"{Path}/{id}/upcoming?count=12");

    public Task<ApiResult> CreateAsync(RecurringRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, RecurringRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> PauseAsync(Guid id) => ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/{id}/pause", null));

    public Task<ApiResult> ResumeAsync(Guid id) => ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/{id}/resume", null));

    public Task<ApiResult> DeleteAsync(Guid id) => ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));
}
