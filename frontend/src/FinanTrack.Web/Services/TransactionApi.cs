using System.Globalization;
using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class TransactionApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/transactions";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se a lista não puder ser carregada.</summary>
    public Task<PageResponse<TransactionResponse>?> ListAsync(
        TransactionFilter filter, int page, int size, string? sort, CancellationToken cancellationToken = default) =>
        Http.GetOrNullAsync<PageResponse<TransactionResponse>>(BuildUrl(filter, page, size, sort), cancellationToken);

    public Task<ApiResult> CreateAsync(TransactionRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, TransactionRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> CompleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.PostAsync($"{Path}/{id}/complete", null));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));

    private static string BuildUrl(TransactionFilter filter, int page, int size, string? sort)
    {
        var query = new List<string> { $"page={page}", $"size={size}" };
        Add(query, "sort", sort);
        Add(query, "search", string.IsNullOrWhiteSpace(filter.Search) ? null : filter.Search.Trim());
        Add(query, "type", filter.Type?.ToString());
        Add(query, "status", filter.Status?.ToString());
        Add(query, "categoryId", filter.CategoryId?.ToString());
        Add(query, "from", filter.From?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        Add(query, "to", filter.To?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        return $"{Path}?{string.Join('&', query)}";
    }

    private static void Add(List<string> query, string name, string? value)
    {
        if (value is not null)
        {
            query.Add($"{name}={Uri.EscapeDataString(value)}");
        }
    }
}
