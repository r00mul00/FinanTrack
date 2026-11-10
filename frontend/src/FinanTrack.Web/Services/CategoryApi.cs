using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class CategoryApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/categories";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se a lista não puder ser carregada.</summary>
    public Task<List<CategoryResponse>?> ListAsync() => Http.GetOrNullAsync<List<CategoryResponse>>(Path);

    /// <summary>Retorna null se não for possível consultar o uso.</summary>
    public async Task<long?> GetUsageAsync(Guid id) =>
        (await Http.GetOrNullAsync<CategoryUsageResponse>($"{Path}/{id}/usage"))?.References;

    public Task<ApiResult> CreateAsync(CategoryRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, CategoryRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> DeleteAsync(Guid id, Guid? reassignTo = null) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync(reassignTo is null ? $"{Path}/{id}" : $"{Path}/{id}?reassignTo={reassignTo}"));
}
