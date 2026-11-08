using Microsoft.AspNetCore.Components.WebAssembly.Http;

namespace FinanTrack.Web.Authentication;

/// <summary>Faz o navegador enviar e aceitar o cookie HttpOnly do refresh token.</summary>
public sealed class IncludeCredentialsHandler : DelegatingHandler
{
    protected override Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        request.SetBrowserRequestCredentials(BrowserRequestCredentials.Include);
        return base.SendAsync(request, cancellationToken);
    }
}
