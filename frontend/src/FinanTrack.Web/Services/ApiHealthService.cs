using System.Net.Http.Json;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public class ApiHealthService(HttpClient http)
{
    private sealed record HealthResponse(string? Status);

    public async Task<ApiHealth> GetAsync()
    {
        try
        {
            var response = await http.GetFromJsonAsync<HealthResponse>("actuator/health");
            return response?.Status == "UP" ? ApiHealth.Online : ApiHealth.Offline;
        }
        catch (HttpRequestException)
        {
            return ApiHealth.Offline;
        }
    }
}
