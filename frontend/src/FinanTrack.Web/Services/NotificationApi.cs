using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class NotificationApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/notifications";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<NotificationListResponse?> ListAsync() =>
        Http.GetOrNullAsync<NotificationListResponse>($"{Path}?size=20");

    public Task<ApiResult> MarkReadAsync(Guid id) => ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/{id}/read", null));

    public Task<ApiResult> MarkAllReadAsync() => ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/read-all", null));
}
