using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class UserApi(IHttpClientFactory httpClientFactory)
{
    private const string Me = "api/v1/users/me";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<UserResponse?> GetCurrentAsync() =>
        Http.GetOrNullAsync<UserResponse>(Me);

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
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Me}/password", new ChangePasswordRequest(currentPassword, newPassword)));

    public Task<ApiResult> RequestEmailChangeAsync(string newEmail, string currentPassword) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Me}/email-change", new EmailChangeRequest(newEmail, currentPassword)));

    public Task<ApiResult> ConfirmEmailChangeAsync(string token) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Me}/email-change/confirm", new TokenRequest(token)));

    public Task<ApiResult> RequestDeletionAsync(string currentPassword) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Me}/deletion-request", new DeletionRequest(currentPassword)));

    public Task<PreferencesDto?> GetPreferencesAsync() =>
        Http.GetOrNullAsync<PreferencesDto>($"{Me}/preferences");

    public Task<ApiResult> UpdatePreferencesAsync(PreferencesDto preferences) =>
        ApiCalls.SendAsync(() => Http.PatchAsJsonAsync($"{Me}/preferences", preferences));

    public Task<byte[]?> ExportAsync() =>
        Http.GetBytesOrNullAsync($"{Me}/export");
}
