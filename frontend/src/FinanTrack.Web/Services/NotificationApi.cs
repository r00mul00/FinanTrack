using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class NotificationApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/notifications";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public async Task<NotificationListResponse?> ListAsync()
    {
        try
        {
            var response = await Http.GetAsync($"{Path}?size=20");
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<NotificationListResponse>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public Task<ApiResult> MarkReadAsync(Guid id) => SendAsync(() => Http.PostAsync($"{Path}/{id}/read", null));

    public Task<ApiResult> MarkAllReadAsync() => SendAsync(() => Http.PostAsync($"{Path}/read-all", null));

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
