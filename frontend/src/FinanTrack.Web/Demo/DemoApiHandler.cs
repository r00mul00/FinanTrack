using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>
/// Substitui a rede no modo demonstração: atende as rotas /api/v1 com o <see cref="DemoStore"/>.
/// Os serviços e as páginas do frontend não sabem que a API não existe.
/// </summary>
public sealed class DemoApiHandler(DemoStore store) : HttpMessageHandler
{
    private const string ApiPrefix = "api/v1/";
    private const string Unavailable = "Esta ação não está disponível na demonstração.";
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        var uri = request.RequestUri!;
        var path = uri.AbsolutePath;
        var start = path.IndexOf(ApiPrefix, StringComparison.Ordinal);
        if (start < 0)
        {
            return Problem(404, "Recurso não encontrado.");
        }

        var segments = path[(start + ApiPrefix.Length)..].Trim('/').Split('/');
        var query = ParseQuery(uri.Query);
        var body = request.Content is null ? null : await request.Content.ReadAsByteArrayAsync(cancellationToken);

        try
        {
            if (segments[0] != "auth" && !store.IsLoggedIn)
            {
                return Problem(401, "Sessão expirada.");
            }
            return Route(request.Method, segments, query, new Body(body));
        }
        catch (DemoException ex)
        {
            return Problem(ex.Status, ex.Message);
        }
        catch (Exception ex) when (ex is FormatException or ArgumentException or JsonException)
        {
            return Problem(400, "Requisição inválida.");
        }
    }

    private HttpResponseMessage Route(HttpMethod method, string[] s, Dictionary<string, string> q, Body body)
    {
        var get = method == HttpMethod.Get;
        var post = method == HttpMethod.Post;
        var put = method == HttpMethod.Put;
        var patch = method == HttpMethod.Patch;
        var delete = method == HttpMethod.Delete;

        return (s[0], s.Length) switch
        {
            // Autenticação: qualquer credencial entra na conta de demonstração.
            ("auth", 2) when s[1] == "login" => Ok(store.Login()),
            ("auth", 2) when s[1] == "refresh" => store.Refresh() is { } token ? Ok(token) : Problem(401, "Sessão expirada."),
            ("auth", 2) when s[1] == "logout" => Do(store.Logout),
            ("auth", 2) => NoContent(),

            // Perfil e preferências.
            ("users", 2) when get => Ok(store.CurrentUser()),
            ("users", 2) when patch => Ok(store.Rename(body.As<UpdateProfileRequest>())),
            ("users", 3) when s[2] == "preferences" && get => Ok(store.Preferences()),
            ("users", 3) when s[2] == "preferences" && patch => Do(() => store.UpdatePreferences(body.As<PreferencesDto>())),
            ("users", 3) when s[2] == "export" => File(JsonSerializer.SerializeToUtf8Bytes(store.Export(), Json), "application/json"),
            ("users", _) => Problem(422, Unavailable),

            // Contas e categorias.
            ("accounts", 1) when get => Ok(store.ListAccounts()),
            ("accounts", 1) when post => Created(store.CreateAccount(body.As<AccountRequest>())),
            ("accounts", 2) when put => Ok(store.UpdateAccount(Id(s[1]), body.As<AccountRequest>())),
            ("accounts", 2) when delete => Do(() => store.DeleteAccount(Id(s[1]))),

            ("categories", 1) when get => Ok(store.ListCategories()),
            ("categories", 1) when post => Created(store.CreateCategory(body.As<CategoryRequest>())),
            ("categories", 2) when put => Ok(store.UpdateCategory(Id(s[1]), body.As<CategoryRequest>())),
            ("categories", 2) when delete => Do(() => store.DeleteCategory(Id(s[1]), q.TryGetValue("reassignTo", out var to) ? Id(to) : null)),
            ("categories", 3) when s[2] == "usage" => Ok(new CategoryUsageResponse(store.CategoryUsage(Id(s[1])))),

            // Movimentações e orçamentos.
            ("transactions", 1) when get => Ok(store.ListTransactions(q)),
            ("transactions", 1) when post => Created(store.CreateTransaction(body.As<TransactionRequest>())),
            ("transactions", 2) when put => Ok(store.UpdateTransaction(Id(s[1]), body.As<TransactionRequest>())),
            ("transactions", 2) when delete => Do(() => store.DeleteTransaction(Id(s[1]))),
            ("transactions", 3) when s[2] == "complete" => Ok(store.CompleteTransaction(Id(s[1]))),

            ("budgets", 1) when get => Ok(store.BudgetMonth(q.GetValueOrDefault("month"))),
            ("budgets", 1) when post => Created(store.CreateBudget(body.As<BudgetRequest>())),
            ("budgets", 2) when put => Ok(store.UpdateBudget(Id(s[1]), body.As<BudgetLimitRequest>())),
            ("budgets", 2) when delete => Do(() => store.DeleteBudget(Id(s[1]))),

            // Metas.
            ("goals", 1) when get => Ok(store.ListGoals()),
            ("goals", 1) when post => Created(store.CreateGoal(body.As<GoalRequest>())),
            ("goals", 2) when put => Ok(store.UpdateGoal(Id(s[1]), body.As<GoalRequest>())),
            ("goals", 2) when delete => Do(() => store.DeleteGoal(Id(s[1]))),
            ("goals", 3) when s[2] == "movements" => Ok(store.GoalMovements(Id(s[1]), Size(q))),
            ("goals", 3) when s[2] == "deposits" => Ok(store.MoveGoal(Id(s[1]), GoalMovementType.DEPOSIT, body.As<GoalMovementRequest>())),
            ("goals", 3) when s[2] == "withdrawals" => Ok(store.MoveGoal(Id(s[1]), GoalMovementType.WITHDRAWAL, body.As<GoalMovementRequest>())),
            ("goals", 3) when s[2] == "complete" => Ok(store.CompleteGoal(Id(s[1]))),

            // Cartões, compras e faturas.
            ("cards", 1) when get => Ok(store.ListCards()),
            ("cards", 1) when post => Created(store.CreateCard(body.As<CardRequest>())),
            ("cards", 2) when get => Ok(store.GetCard(Id(s[1]))),
            ("cards", 2) when put => Ok(store.UpdateCard(Id(s[1]), body.As<CardRequest>())),
            ("cards", 2) when delete => Do(() => store.DeleteCard(Id(s[1]))),
            ("cards", 3) when s[2] == "invoices" => Ok(store.ListInvoices(Id(s[1]))),
            ("cards", 3) when s[2] == "purchases" && get => Ok(store.ListPurchases(Id(s[1]), Size(q))),
            ("cards", 3) when s[2] == "purchases" && post => Do(() => store.CreatePurchase(Id(s[1]), body.As<PurchaseRequest>()), 201),
            ("cards", 4) when s[2] == "invoices" => Ok(store.InvoiceDetail(Id(s[1]), Id(s[3]))),
            ("cards", 4) when s[2] == "purchases" && delete => Do(() => store.CancelPurchase(Id(s[1]), Id(s[3]))),
            ("cards", 5) when s[4] == "payments" => Ok(store.PayInvoice(Id(s[1]), Id(s[3]), body.As<PaymentRequest>())),

            // Recorrências.
            ("recurring", 1) when get => Ok(store.ListRecurring()),
            ("recurring", 1) when post => Created(store.CreateRecurring(body.As<RecurringRequest>())),
            ("recurring", 2) when put => Ok(store.UpdateRecurring(Id(s[1]), body.As<RecurringRequest>())),
            ("recurring", 2) when delete => Do(() => store.DeleteRecurring(Id(s[1]))),
            ("recurring", 3) when s[2] == "upcoming" => Ok(store.UpcomingOccurrences(Id(s[1]), Count(q))),
            ("recurring", 3) when s[2] == "pause" => Ok(store.PauseRecurring(Id(s[1]))),
            ("recurring", 3) when s[2] == "resume" => Ok(store.ResumeRecurring(Id(s[1]))),

            // Leituras agregadas.
            ("dashboard", 2) when s[1] == "summary" => Ok(store.Summary(Date(q, "from"), Date(q, "to"))),
            ("dashboard", 2) when s[1] == "income-vs-expense" => Ok(store.IncomeVsExpense(Date(q, "from"), Date(q, "to"))),
            ("dashboard", 2) when s[1] == "expenses-by-category" => Ok(store.ExpensesByCategory(Date(q, "from"), Date(q, "to"))),
            ("dashboard", 2) when s[1] == "upcoming" => Ok(store.Upcoming()),

            ("reports", 2) => Ok(store.Report(s[1], q)),
            ("reports", 3) when s[2] == "export" => Export(s[1], q),

            ("search", 1) => Ok(store.Search(q.GetValueOrDefault("q"))),

            ("notifications", 1) => Ok(store.Notifications(Size(q))),
            ("notifications", 2) when s[1] == "read-all" => Do(store.MarkAllRead),
            ("notifications", 3) when s[2] == "read" => Do(() => store.MarkRead(Id(s[1]))),

            _ => Problem(404, "Recurso não encontrado.")
        };
    }

    /// <summary>O PDF depende do backend; o CSV é gerado aqui, no mesmo formato.</summary>
    private HttpResponseMessage Export(string slug, Dictionary<string, string> query) =>
        query.GetValueOrDefault("format") == "csv"
            ? File(DemoStore.ToCsv(store.Report(slug, query)), "text/csv")
            : Problem(422, "Na demonstração, a exportação está disponível só em CSV.");

    private sealed class Body(byte[]? content)
    {
        public T As<T>() => JsonSerializer.Deserialize<T>(content ?? [], Json) ?? throw new JsonException();
    }

    private static Guid Id(string value) => Guid.Parse(value);

    private static int Size(Dictionary<string, string> query) => int.Parse(query.GetValueOrDefault("size", "20"));

    private static int Count(Dictionary<string, string> query) => int.Parse(query.GetValueOrDefault("count", "12"));

    private static DateOnly? Date(Dictionary<string, string> query, string key) =>
        query.TryGetValue(key, out var value)
            ? DateOnly.ParseExact(value, "yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture)
            : null;

    private static Dictionary<string, string> ParseQuery(string query)
    {
        var result = new Dictionary<string, string>();
        foreach (var pair in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var parts = pair.Split('=', 2);
            result[Uri.UnescapeDataString(parts[0])] = parts.Length > 1 ? Uri.UnescapeDataString(parts[1].Replace('+', ' ')) : "";
        }
        return result;
    }

    private static HttpResponseMessage Ok<T>(T value) => Json200(value, HttpStatusCode.OK);

    private static HttpResponseMessage Created<T>(T value) => Json200(value, HttpStatusCode.Created);

    private static HttpResponseMessage Json200<T>(T value, HttpStatusCode status) =>
        new(status) { Content = JsonContent.Create(value, options: Json) };

    private static HttpResponseMessage NoContent() => new(HttpStatusCode.NoContent);

    private static HttpResponseMessage Do(Action action, int status = 204)
    {
        action();
        return new HttpResponseMessage((HttpStatusCode)status);
    }

    private static HttpResponseMessage File(byte[] content, string contentType)
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(content) };
        response.Content.Headers.ContentType = new(contentType);
        return response;
    }

    private static HttpResponseMessage Problem(int status, string detail) =>
        new((HttpStatusCode)status) { Content = JsonContent.Create(new ProblemResponse(detail, null, null), options: Json) };
}
