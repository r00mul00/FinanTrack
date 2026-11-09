using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class ReportApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/reports";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se o relatório não puder ser gerado. O filtro já vem como query string.</summary>
    public async Task<ReportResponse?> GetAsync(string slug, string filter)
    {
        try
        {
            var response = await Http.GetAsync($"{Path}/{slug}?{filter}");
            return response.IsSuccessStatusCode ? await response.Content.ReadFromJsonAsync<ReportResponse>() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    /// <summary>Retorna null se o arquivo não puder ser gerado.</summary>
    public async Task<byte[]?> ExportAsync(string slug, string filter, string format)
    {
        try
        {
            var response = await Http.GetAsync($"{Path}/{slug}/export?format={format}&{filter}");
            return response.IsSuccessStatusCode ? await response.Content.ReadAsByteArrayAsync() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }
}
