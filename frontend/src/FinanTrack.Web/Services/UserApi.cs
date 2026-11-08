using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class UserApi(IHttpClientFactory httpClientFactory)
{
    public async Task<UserResponse?> GetCurrentAsync()
    {
        try
        {
            var http = httpClientFactory.CreateClient(HttpClients.Api);
            var response = await http.GetAsync("api/v1/users/me");
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<UserResponse>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }
}
