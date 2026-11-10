using System.Net.Http.Json;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

/// <summary>
/// Chamadas comuns aos clientes da API. Leituras devolvem null em qualquer falha;
/// escritas devolvem um <see cref="ApiResult"/> com a mensagem a exibir.
/// </summary>
internal static class ApiCalls
{
    /// <summary>Retorna null se a resposta não for de sucesso ou o servidor não responder.</summary>
    public static async Task<T?> GetOrNullAsync<T>(this HttpClient http, string url,
        CancellationToken cancellationToken = default) where T : class
    {
        try
        {
            var response = await http.GetAsync(url, cancellationToken);
            return response.IsSuccessStatusCode
                ? await response.Content.ReadFromJsonAsync<T>(cancellationToken)
                : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    /// <summary>Retorna null se o arquivo não puder ser baixado.</summary>
    public static async Task<byte[]?> GetBytesOrNullAsync(this HttpClient http, string url)
    {
        try
        {
            var response = await http.GetAsync(url);
            return response.IsSuccessStatusCode ? await response.Content.ReadAsByteArrayAsync() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    public static async Task<ApiResult> SendAsync(Func<Task<HttpResponseMessage>> send)
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
