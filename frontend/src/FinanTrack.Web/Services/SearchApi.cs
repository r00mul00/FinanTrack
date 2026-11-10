using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class SearchApi(IHttpClientFactory httpClientFactory)
{
    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null em caso de falha; o cancelamento fica a cargo de quem chama.</summary>
    public Task<List<SearchResult>?> SearchAsync(string query, CancellationToken cancellationToken) =>
        Http.GetOrNullAsync<List<SearchResult>>($"api/v1/search?q={Uri.EscapeDataString(query)}", cancellationToken);
}
