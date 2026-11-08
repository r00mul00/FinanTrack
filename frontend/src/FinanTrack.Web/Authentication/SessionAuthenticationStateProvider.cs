using System.Security.Claims;
using Microsoft.AspNetCore.Components.Authorization;

namespace FinanTrack.Web.Authentication;

public sealed class SessionAuthenticationStateProvider : AuthenticationStateProvider
{
    private readonly AuthSession _session;

    public SessionAuthenticationStateProvider(AuthSession session)
    {
        _session = session;
        _session.Changed += () => NotifyAuthenticationStateChanged(Task.FromResult(BuildState()));
    }

    public override async Task<AuthenticationState> GetAuthenticationStateAsync()
    {
        await _session.InitializeAsync();
        return BuildState();
    }

    private AuthenticationState BuildState()
    {
        var user = _session.CurrentUser;
        if (user is null)
        {
            return new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()));
        }

        var identity = new ClaimsIdentity(
        [
            new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim(ClaimTypes.Name, user.Name),
            new Claim(ClaimTypes.Email, user.Email)
        ], authenticationType: "jwt");
        return new AuthenticationState(new ClaimsPrincipal(identity));
    }
}
