using FinanTrack.Web;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.Services;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using MudBlazor.Services;

var builder = WebAssemblyHostBuilder.CreateDefault(args);
builder.RootComponents.Add<App>("#app");
builder.RootComponents.Add<HeadOutlet>("head::after");

// Em produção, frontend e API ficam na mesma origem; em desenvolvimento, vem do appsettings.
var apiBaseAddress = new Uri(builder.Configuration["Api:BaseUrl"] ?? builder.HostEnvironment.BaseAddress);

// Singleton: no WebAssembly existe um por aba, e os handlers do IHttpClientFactory
// rodam em outro escopo de DI; assim todos enxergam a mesma sessão.
builder.Services.AddSingleton<AuthSession>();
builder.Services.AddSingleton<AuthenticationStateProvider, SessionAuthenticationStateProvider>();
builder.Services.AddAuthorizationCore();
builder.Services.AddCascadingAuthenticationState();

builder.Services.AddTransient<IncludeCredentialsHandler>();
builder.Services.AddTransient<AuthorizationHandler>();

// Rotas /auth: enviam o cookie e não usam Bearer.
builder.Services.AddHttpClient(HttpClients.Public, client => client.BaseAddress = apiBaseAddress)
    .AddHttpMessageHandler<IncludeCredentialsHandler>();

// Demais rotas: Bearer e renovação automática da sessão.
builder.Services.AddHttpClient(HttpClients.Api, client => client.BaseAddress = apiBaseAddress)
    .AddHttpMessageHandler<AuthorizationHandler>();

builder.Services.AddHttpClient<ApiHealthService>(client => client.BaseAddress = apiBaseAddress);
builder.Services.AddScoped<AuthApi>();
builder.Services.AddScoped<UserApi>();
builder.Services.AddScoped<AccountApi>();
builder.Services.AddMudServices();

await builder.Build().RunAsync();
