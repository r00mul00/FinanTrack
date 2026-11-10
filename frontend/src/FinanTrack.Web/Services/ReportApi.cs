using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Services;

public sealed class ReportApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/reports";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    /// <summary>Retorna null se o relatório não puder ser gerado. O filtro já vem como query string.</summary>
    public Task<ReportResponse?> GetAsync(string slug, string filter) =>
        Http.GetOrNullAsync<ReportResponse>($"{Path}/{slug}?{filter}");

    /// <summary>Retorna null se o arquivo não puder ser gerado.</summary>
    public Task<byte[]?> ExportAsync(string slug, string filter, string format) =>
        Http.GetBytesOrNullAsync($"{Path}/{slug}/export?format={format}&{filter}");
}
