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
    public Task<List<CategoryResponse>?> ListAsync() => GetAsync<List<CategoryResponse>>(Path);

    /// <summary>Retorna null se não for possível consultar o uso.</summary>
    public async Task<long?> GetUsageAsync(Guid id) =>
        (await GetAsync<CategoryUsageResponse>($"{Path}/{id}/usage"))?.References;

    public Task<ApiResult> CreateAsync(CategoryRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, CategoryRequest request) =>
        SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> DeleteAsync(Guid id, Guid? reassignTo = null) =>
        SendAsync(() => Http.DeleteAsync(reassignTo is null ? $"{Path}/{id}" : $"{Path}/{id}?reassignTo={reassignTo}"));

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
