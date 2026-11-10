using System.Net.Http.Json;
using FinanTrack.Web.Authentication;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Services;

public sealed class CardApi(IHttpClientFactory httpClientFactory)
{
    private const string Path = "api/v1/cards";

    private HttpClient Http => httpClientFactory.CreateClient(HttpClients.Api);

    public Task<List<CardResponse>?> ListAsync() => Http.GetOrNullAsync<List<CardResponse>>(Path);

    public Task<CardResponse?> GetAsync(Guid id) => Http.GetOrNullAsync<CardResponse>($"{Path}/{id}");

    public Task<List<InvoiceResponse>?> ListInvoicesAsync(Guid id) =>
        Http.GetOrNullAsync<List<InvoiceResponse>>($"{Path}/{id}/invoices");

    public Task<InvoiceDetailResponse?> GetInvoiceAsync(Guid id, Guid invoiceId) =>
        Http.GetOrNullAsync<InvoiceDetailResponse>($"{Path}/{id}/invoices/{invoiceId}");

    public Task<PageResponse<PurchaseResponse>?> ListPurchasesAsync(Guid id) =>
        Http.GetOrNullAsync<PageResponse<PurchaseResponse>>($"{Path}/{id}/purchases?size=100");

    public Task<ApiResult> CreateAsync(CardRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync(Path, request));

    public Task<ApiResult> UpdateAsync(Guid id, CardRequest request) =>
        ApiCalls.SendAsync(() => Http.PutAsJsonAsync($"{Path}/{id}", request));

    public Task<ApiResult> DeleteAsync(Guid id) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}"));

    public Task<ApiResult> CreatePurchaseAsync(Guid id, PurchaseRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Path}/{id}/purchases", request));

    public Task<ApiResult> CancelPurchaseAsync(Guid id, Guid purchaseId) =>
        ApiCalls.SendAsync(() => Http.DeleteAsync($"{Path}/{id}/purchases/{purchaseId}"));

    public Task<ApiResult> PayAsync(Guid id, Guid invoiceId, PaymentRequest request) =>
        ApiCalls.SendAsync(() => Http.PostAsJsonAsync($"{Path}/{id}/invoices/{invoiceId}/payments", request));
}
