using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class SearchApi(IHttpClientFactory httpClientFactory)
{
    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null em caso de falha; o cancelamento fica a cargo de quem chama.</summary>
    public async Task<List<SearchResult>?> SearchAsync(string query, CancellationToken cancellationToken)
    {
        try
        {
            var response = await Http.GetAsync($"api/v1/search?q={Uri.EscapeDataString(query)}", cancellationToken);
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<List<SearchResult>>(cancellationToken)
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }
}
