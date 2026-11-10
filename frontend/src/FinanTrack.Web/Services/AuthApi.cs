using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class AuthApi(IHttpClientFactory httpClientFactory)
{
    public Task<ApiResult> RegisterAsync(RegisterRequest request) => PostAsync("register", request);

    public Task<ApiResult> VerifyEmailAsync(string token) => PostAsync("verify-email", new TokenRequest(token));

    public Task<ApiResult> ResendVerificationAsync(string email) =>
        PostAsync("resend-verification", new EmailRequest(email));

    public Task<ApiResult> ForgotPasswordAsync(string email) => PostAsync("forgot-password", new EmailRequest(email));

    public Task<ApiResult> ResetPasswordAsync(ResetPasswordRequest request) => PostAsync("reset-password", request);

    private Task<ApiResult> PostAsync<T>(string path, T body) =>
        ApiCalls.SendAsync(() => httpClientFactory.CreateClient(HttpClients.Public)
            .PostAsJsonAsync($"api/v1/auth/{path}", body));
}
