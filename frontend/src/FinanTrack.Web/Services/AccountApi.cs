using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class AccountApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/accounts";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se a lista não puder ser carregada.</summary>
    public async Task<List<AccountResponse>?> ListAsync()
    {
        try
        {
            var response = await Http.GetAsync(Path);
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<List<AccountResponse>>()
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public Task<ApiResult> CreateAsync(AccountRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, AccountRequest request) =>
        SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

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
