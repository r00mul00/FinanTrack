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
    public Task<List<AccountResponse>?> ListAsync() =>
        Http.GetOrNullAsync<List<AccountResponse>>(Path);

    public Task<ApiResult> CreateAsync(AccountRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, AccountRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));
}
