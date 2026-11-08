using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class RecurringApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/recurring";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<List<RecurringResponse>?> ListAsync() => GetAsync<List<RecurringResponse>>(Path);

    public Task<List<DateOnly>?> GetUpcomingAsync(Guid id) => GetAsync<List<DateOnly>>($"{Path}/{id}/upcoming?count=12");

    public Task<ApiResult> CreateAsync(RecurringRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, RecurringRequest request) =>
        SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> PauseAsync(Guid id) => SendAsync(() => Http.PostAsync($"{Path}/{id}/pause", null));

    public Task<ApiResult> ResumeAsync(Guid id) => SendAsync(() => Http.PostAsync($"{Path}/{id}/resume", null));

    public Task<ApiResult> DeleteAsync(Guid id) => SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));

    private async Task<T?> GetAsync<T>(string url) where T : class
    {
        try
        {
            var response = await Http.GetAsync(url);
            return response.IsSuccessStatusCode ? await response.Content.ReadFromJsonAsync<T>() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

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
