using Microsoft.Extensions.Configuration;

namespace FinanTrack.Web.Demo;

/// <summary>Ligado por "Demo:Enabled" no wwwroot/appsettings.json, só no build do GitHub Pages.</summary>
public static class DemoMode
{
    public static bool IsEnabled(IConfiguration configuration) =>
        bool.TryParse(configuration["Demo:Enabled"], out var enabled) && enabled;
}
