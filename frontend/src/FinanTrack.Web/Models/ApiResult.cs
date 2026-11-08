using System.Net.Http.Json;
using System.Text.Json;
using FinanTrack.Web.DTOs;

namespace FinanTrack.Web.Models;

public sealed record ApiResult(bool Success, string? Message = null, string? Code = null)
{
    private const string GenericError = "Não foi possível concluir a operação. Tente novamente.";

    public const string EmailNotVerified = "EMAIL_NOT_VERIFIED";

    public static ApiResult Ok() => new(true);

    public static ApiResult NetworkFailure() =>
        new(false, "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.");

    public static async Task<ApiResult> FromResponseAsync(HttpResponseMessage response)
    {
        if (response.IsSuccessStatusCode)
        {
            return Ok();
        }
        try
        {
            var problem = await response.Content.ReadFromJsonAsync<ProblemResponse>();
            var fieldError = problem?.Errors?.Values.FirstOrDefault();
            return new ApiResult(false, fieldError ?? problem?.Detail ?? GenericError, problem?.Code);
        }
        catch (JsonException)
        {
            return new ApiResult(false, GenericError);
        }
    }
}
