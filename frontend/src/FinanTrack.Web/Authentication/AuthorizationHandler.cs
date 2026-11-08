using System.Net;
using System.Net.Http.Headers;

namespace FinanTrack.Web.Authentication;

/// <summary>Envia o Bearer e, em caso de 401, renova a sessão e repete a requisição uma vez.</summary>
public sealed class AuthorizationHandler(AuthSession session) : DelegatingHandler
{
    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        var body = request.Content is null ? null : await request.Content.ReadAsByteArrayAsync(cancellationToken);
        var contentType = request.Content?.Headers.ContentType;

        Attach(request);
        var response = await base.SendAsync(request, cancellationToken);
        if (response.StatusCode != HttpStatusCode.Unauthorized || !await session.RefreshAsync())
        {
            return response;
        }

        response.Dispose();
        return await base.SendAsync(Clone(request, body, contentType), cancellationToken);
    }

    private void Attach(HttpRequestMessage request)
    {
        request.Headers.Authorization = session.AccessToken is null
            ? null
            : new AuthenticationHeaderValue("Bearer", session.AccessToken);
    }

    private HttpRequestMessage Clone(HttpRequestMessage original, byte[]? body, MediaTypeHeaderValue? contentType)
    {
        var clone = new HttpRequestMessage(original.Method, original.RequestUri);
        foreach (var header in original.Headers)
        {
            clone.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }
        if (body is not null)
        {
            clone.Content = new ByteArrayContent(body);
            clone.Content.Headers.ContentType = contentType;
        }
        Attach(clone);
        return clone;
    }
}
