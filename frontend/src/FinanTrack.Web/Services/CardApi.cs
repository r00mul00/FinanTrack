using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class CardApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/cards";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<List<CardResponse>?> ListAsync() => GetAsync<List<CardResponse>>(Path);

    public Task<CardResponse?> GetAsync(Guid id) => GetAsync<CardResponse>($"{Path}/{id}");

    public Task<List<InvoiceResponse>?> ListInvoicesAsync(Guid id) =>
        GetAsync<List<InvoiceResponse>>($"{Path}/{id}/invoices");

    public Task<InvoiceDetailResponse?> GetInvoiceAsync(Guid id, Guid invoiceId) =>
        GetAsync<InvoiceDetailResponse>($"{Path}/{id}/invoices/{invoiceId}");

    public Task<PageResponse<PurchaseResponse>?> ListPurchasesAsync(Guid id) =>
        GetAsync<PageResponse<PurchaseResponse>>($"{Path}/{id}/purchases?size=100");

    public Task<ApiResult> CreateAsync(CardRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, CardRequest request) =>
        SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));

    public Task<ApiResult> CreatePurchaseAsync(Guid id, PurchaseRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync($"{Path}/{id}/purchases", request));

    public Task<ApiResult> CancelPurchaseAsync(Guid id, Guid purchaseId) =>
        SendAsync(() => Http.DeleteAsync($"{Path}/{id}/purchases/{purchaseId}"));

    public Task<ApiResult> PayAsync(Guid id, Guid invoiceId, PaymentRequest request) =>
        SendAsync(() => Http.PostAsJsonAsync($"{Path}/{id}/invoices/{invoiceId}/payments", request));

    private async Task<T?> GetAsync<T>(string url) where T : class
    {
        try
        {
            var response = await Http.GetAsync(url);
            return response.IsSuccessStatusCode ? await response.Content.ReadFromJsonAsync<T>() : null;
        }
        catch (HttpRequestException)
        {
            return null;
        }
    }

    private static async Task<ApiResult> SendAsync(Func<Task<HttpResponseMessage>> send)
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
