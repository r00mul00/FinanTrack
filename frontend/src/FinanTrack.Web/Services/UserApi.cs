using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class UserApi(IHttpClientFactory httpClientFactory)
{
    private const string Me = "api/v1/users/me";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public async Task<UserResponse?> GetCurrentAsync()
    {
        try
        {
            var response = await Http.GetAsync(Me);
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<UserResponse>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public async Task<(ApiResult Result, UserResponse? User)> RenameAsync(string name)
    {
        try
        {
            var response = await Http.PatchAsJsonAsync(Me, new UpdateProfileRequest(name));
            var result = await ApiResult.FromResponseAsync(response);
            return (result, result.Success ? await response.Content.ReadFromJsonAsync<UserResponse>() : null);
        }
        catch (HttpRequestException)
        {
            return (ApiResult.NetworkFailure(), null);
        }
    }

    public Task<ApiResult> ChangePasswordAsync(string currentPassword, string newPassword) =>
        SendAsync(http => http.PutAsJsonAsync($"{Me}/password", new ChangePasswordRequest(currentPassword, newPassword)));

    public Task<ApiResult> RequestEmailChangeAsync(string newEmail, string currentPassword) =>
        SendAsync(http => http.PostAsJsonAsync($"{Me}/email-change", new EmailChangeRequest(newEmail, currentPassword)));

    public Task<ApiResult> ConfirmEmailChangeAsync(string token) =>
        SendAsync(http => http.PostAsJsonAsync($"{Me}/email-change/confirm", new TokenRequest(token)));

    public Task<ApiResult> RequestDeletionAsync(string currentPassword) =>
        SendAsync(http => http.PostAsJsonAsync($"{Me}/deletion-request", new DeletionRequest(currentPassword)));

    public async Task<PreferencesDto?> GetPreferencesAsync()
    {
        try
        {
            var response = await Http.GetAsync($"{Me}/preferences");
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<PreferencesDto>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public Task<ApiResult> UpdatePreferencesAsync(PreferencesDto preferences) =>
        SendAsync(http => http.PatchAsJsonAsync($"{Me}/preferences", preferences));

    public async Task<byte[]?> ExportAsync()
    {
        try
        {
            var response = await Http.GetAsync($"{Me}/export");
            return response.IsSuccessStatusCode ? await response.Content.ReadAsByteArrayAsync() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    private async Task<ApiResult> SendAsync(Func<HttpClient, Task<HttpResponseMessage>> request)
    {
        try
        {
            return await ApiResult.FromResponseAsync(await request(Http));
        }
        catch (HttpRequestException)
        {
            return ApiResult.NetworkFailure();
        }
    }
}
