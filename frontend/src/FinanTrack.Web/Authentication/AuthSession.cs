using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Authentication;

/// <summary>
/// Estado da sessão. O access token fica apenas em memória; ao recarregar a
/// página, a sessão é restaurada pelo cookie HttpOnly.
/// </summary>
public sealed class AuthSession(IHttpClientFactory httpClientFactory)
{
    private const int MaxRefreshAttempts = 4;
    private static readonly TimeSpan RetryDelay = TimeSpan.FromMilliseconds(500);

    private Task? _initialization;
    private Task<bool>? _refreshInProgress;

    public string? AccessToken { get; private set; }
    public UserResponse? CurrentUser { get; private set; }
    public event Action? Changed;

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Public);

    /// <summary>Tenta restaurar a sessão uma única vez, ao abrir o aplicativo.</summary>
    public Task InitializeAsync() => _initialization ??= RefreshAsync();

    /// <summary>Requisições simultâneas compartilham a mesma renovação.</summary>
    public Task<bool> RefreshAsync() => _refreshInProgress ??= RefreshOnceAsync();

    public async Task<ApiResult> LoginAsync(string email, string password)
    {
        try
        {
            var response = await Http.PostAsJsonAsync("api/v1/auth/login", new LoginRequest(email, password));
            if (!response.IsSuccessStatusCode)
            {
                return await ApiResult.FromResponseAsync(response);
            }
            var loaded = await ApplyTokenAsync(response);
            return loaded ? ApiResult.Ok() : ApiResult.NetworkFailure();
        }
        catch (HttpRequestException)
        {
            return ApiResult.NetworkFailure();
        }
    }

    /// <summary>A sessão local é encerrada mesmo que a API esteja inacessível.</summary>
    public async Task LogoutAsync()
    {
        try
        {
            await Http.PostAsync("api/v1/auth/logout", null);
        }
        catch (HttpRequestException)
        {
            // O cookie expira sozinho; os dados em memória são apagados a seguir.
        }
        Clear();
    }

    private async Task<bool> RefreshOnceAsync()
    {
        try
        {
            for (var attempt = 0; attempt < MaxRefreshAttempts; attempt++)
            {
                var response = await Http.PostAsync("api/v1/auth/refresh", null);
                if (response.StatusCode == HttpStatusCode.Conflict)
                {
                    await Task.Delay(RetryDelay); // Outra aba acabou de renovar.
                    continue;
                }
                if (response.IsSuccessStatusCode && await ApplyTokenAsync(response))
                {
                    return true;
                }
                break;
            }
        }
        catch (HttpRequestException)
        {
            // Trata como sessão indisponível.
        }
        finally
        {
            _refreshInProgress = null;
        }

        Clear();
        return false;
    }

    private async Task<bool> ApplyTokenAsync(HttpResponseMessage tokenResponse)
    {
        var token = await tokenResponse.Content.ReadFromJsonAsync<TokenResponse>();
        if (token is null)
        {
            return false;
        }

        using var request = new HttpRequestMessage(HttpMethod.Get, "api/v1/users/me");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token.AccessToken);
        var meResponse = await Http.SendAsync(request);
        if (!meResponse.IsSuccessStatusCode)
        {
            return false;
        }

        AccessToken = token.AccessToken;
        CurrentUser = await meResponse.Content.ReadFromJsonAsync<UserResponse>();
        Changed?.Invoke();
        return true;
    }

    private void Clear()
    {
        AccessToken = null;
        CurrentUser = null;
        Changed?.Invoke();
    }
}
