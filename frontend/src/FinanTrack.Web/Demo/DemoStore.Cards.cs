using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>Cartões, compras parceladas, faturas e pagamentos (InvoiceService e PurchaseService).</summary>
public sealed partial class DemoStore
{
    public List<CardResponse> ListCards() =>
        State.Cards.OrderBy(c => c.Nickname, StringComparer.CurrentCultureIgnoreCase).Select(ToResponse).ToList();

    public CardResponse GetCard(Guid id) => ToResponse(FindCard(id));

    public CardResponse CreateCard(CardRequest request)
    {
        var card = new DemoCard();
        Apply(card, request);
        State.Cards.Add(card);
        Save();
        return ToResponse(card);
    }

    public CardResponse UpdateCard(Guid id, CardRequest request)
    {
        var card = FindCard(id);
        Apply(card, request);
        Save();
        return ToResponse(card);
    }

    public void DeleteCard(Guid id)
    {
        var card = FindCard(id);
        if (State.Purchases.Any(p => p.CardId == id))
        {
            throw DemoException.Conflict("Este cartão possui compras. Arquive-o para manter o histórico.");
        }
        State.Invoices.RemoveAll(i => i.CardId == id);
        State.Cards.Remove(card);
        Save();
    }

    private static void Apply(DemoCard card, CardRequest request)
    {
        card.Nickname = request.Nickname.Trim();
        card.Brand = request.Brand;
        card.LastFour = request.LastFour;
        card.CreditLimit = request.CreditLimit;
        card.ClosingDay = request.ClosingDay;
        card.DueDay = request.DueDay;
        card.Archived = request.Archived;
    }

    private DemoCard FindCard(Guid id) =>
        State.Cards.FirstOrDefault(c => c.Id == id) ?? throw DemoException.NotFound("Cartão não encontrado.");

    private CardResponse ToResponse(DemoCard card)
    {
        var used = UsedLimit(card.Id);
        return new CardResponse(card.Id, card.Nickname, card.Brand, card.LastFour, card.CreditLimit, used,
            card.CreditLimit - used, card.ClosingDay, card.DueDay, card.Archived);
    }

    /// <summary>Soma do que falta pagar em cada fatura, inclusive das parcelas futuras.</summary>
    private decimal UsedLimit(Guid cardId) =>
        State.Invoices.Where(i => i.CardId == cardId).Sum(i => Math.Max(InvoiceTotal(i.Id) - i.PaidAmount, 0));

    /// <summary>Compras canceladas não fazem parte do total da fatura.</summary>
    private decimal InvoiceTotal(Guid invoiceId) =>
        State.Installments.Where(i => i.InvoiceId == invoiceId && !PurchaseOf(i).Canceled).Sum(i => i.Amount);

    private DemoPurchase PurchaseOf(DemoInstallment installment) =>
        State.Purchases.First(p => p.Id == installment.PurchaseId);

    // ---------- Faturas ----------

    public List<InvoiceResponse> ListInvoices(Guid cardId)
    {
        FindCard(cardId);
        return State.Invoices.Where(i => i.CardId == cardId)
            .OrderByDescending(i => i.ReferenceMonth, StringComparer.Ordinal)
            .Select(ToResponse)
            .ToList();
    }

    public InvoiceDetailResponse InvoiceDetail(Guid cardId, Guid invoiceId)
    {
        var invoice = FindInvoice(FindCard(cardId), invoiceId);
        var lines = State.Installments.Where(i => i.InvoiceId == invoice.Id)
            .Select(i => (Installment: i, Purchase: PurchaseOf(i)))
            .Where(x => !x.Purchase.Canceled)
            .OrderBy(x => x.Purchase.PurchaseDate).ThenBy(x => x.Purchase.CreatedAt)
            .Select(x => new InvoiceLine(x.Purchase.Id, x.Purchase.Description, x.Installment.Number,
                x.Purchase.Installments, x.Installment.Amount, x.Purchase.PurchaseDate))
            .ToList();
        return new InvoiceDetailResponse(ToResponse(invoice), lines);
    }

    /// <summary>Aceita pagamento parcial. Ao quitar, as parcelas da fatura passam a efetivadas.</summary>
    public InvoiceResponse PayInvoice(Guid cardId, Guid invoiceId, PaymentRequest request)
    {
        var card = FindCard(cardId);
        var invoice = FindInvoice(card, invoiceId);
        var account = UsableAccount(request.AccountId, null);

        var remaining = InvoiceTotal(invoice.Id) - invoice.PaidAmount;
        if (request.Amount > remaining)
        {
            throw DemoException.Rule("O pagamento é maior que o valor restante da fatura.");
        }

        invoice.PaidAmount += request.Amount;
        var month = ParseIso(invoice.ReferenceMonth + "-01");
        State.Transactions.Add(new DemoTransaction
        {
            Description = $"Pagamento da fatura {card.Nickname} {month.Month:00}/{month.Year}",
            Amount = request.Amount,
            Type = TransactionType.EXPENSE,
            Kind = TransactionKind.INVOICE_PAYMENT,
            Status = TransactionStatus.COMPLETED,
            AccountId = account.Id,
            InvoiceId = invoice.Id,
            TransactionDate = request.PaymentDate,
            CreatedAt = Tick()
        });

        if (request.Amount == remaining)
        {
            var transactionIds = State.Installments.Where(i => i.InvoiceId == invoice.Id).Select(i => i.TransactionId).ToHashSet();
            foreach (var transaction in State.Transactions.Where(t => transactionIds.Contains(t.Id)
                         && t.Status == TransactionStatus.PENDING))
            {
                transaction.Status = TransactionStatus.COMPLETED;
                transaction.Version++;
            }
        }
        Save();
        return ToResponse(invoice);
    }

    private DemoInvoice FindInvoice(DemoCard card, Guid invoiceId) =>
        State.Invoices.FirstOrDefault(i => i.Id == invoiceId && i.CardId == card.Id)
        ?? throw DemoException.NotFound("Fatura não encontrada.");

    private InvoiceResponse ToResponse(DemoInvoice invoice)
    {
        var total = InvoiceTotal(invoice.Id);
        var remaining = Math.Max(total - invoice.PaidAmount, 0);
        return new InvoiceResponse(invoice.Id, invoice.ReferenceMonth, invoice.ClosingDate, invoice.DueDate, total,
            invoice.PaidAmount, remaining, StatusOf(invoice, total, remaining));
    }

    /// <summary>Estado derivado, nesta ordem, conforme docs/06.</summary>
    private static InvoiceStatus StatusOf(DemoInvoice invoice, decimal total, decimal remaining)
    {
        if (total > 0 && remaining == 0)
        {
            return InvoiceStatus.PAID;
        }
        if (Today > invoice.DueDate && remaining > 0)
        {
            return InvoiceStatus.OVERDUE;
        }
        return Today > invoice.ClosingDate ? InvoiceStatus.CLOSED : InvoiceStatus.OPEN;
    }

    // ---------- Compras ----------

    public PageResponse<PurchaseResponse> ListPurchases(Guid cardId, int size)
    {
        FindCard(cardId);
        return Page(State.Purchases.Where(p => p.CardId == cardId)
            .OrderByDescending(p => p.PurchaseDate).ThenByDescending(p => p.CreatedAt)
            .Select(p => new PurchaseResponse(p.Id, p.Description, p.TotalAmount, p.Installments, p.CategoryId,
                p.PurchaseDate, p.Canceled)), 0, size);
    }

    /// <summary>Compra, faturas, parcelas e movimentações são criadas juntas.</summary>
    public void CreatePurchase(Guid cardId, PurchaseRequest request)
    {
        var card = FindCard(cardId);
        if (card.Archived)
        {
            throw DemoException.Rule("Este cartão está arquivado.");
        }
        UsableCategory(request.CategoryId, CategoryType.EXPENSE, "Compras só podem usar categorias de despesa.", null);

        var amounts = Split(request.TotalAmount, request.Installments);
        if (amounts.Count == 0)
        {
            throw DemoException.Rule("O valor é pequeno demais para este número de parcelas.");
        }
        if (request.TotalAmount > card.CreditLimit - UsedLimit(card.Id))
        {
            throw DemoException.Rule("A compra ultrapassa o limite disponível do cartão.");
        }

        var purchase = new DemoPurchase
        {
            CardId = card.Id,
            CategoryId = request.CategoryId,
            Description = request.Description.Trim(),
            TotalAmount = request.TotalAmount,
            Installments = request.Installments,
            PurchaseDate = request.PurchaseDate,
            CreatedAt = Tick()
        };
        State.Purchases.Add(purchase);

        var firstMonth = ReferenceMonthOf(request.PurchaseDate, card.ClosingDay);
        for (var i = 0; i < amounts.Count; i++)
        {
            var invoice = InvoiceFor(card, firstMonth.AddMonths(i));
            var transaction = new DemoTransaction
            {
                Description = amounts.Count == 1 ? purchase.Description : $"{purchase.Description} ({i + 1}/{amounts.Count})",
                Amount = amounts[i],
                Type = TransactionType.EXPENSE,
                Kind = TransactionKind.CARD_INSTALLMENT,
                Status = TransactionStatus.PENDING,
                CategoryId = purchase.CategoryId,
                TransactionDate = request.PurchaseDate.AddMonths(i),
                CreatedAt = Tick()
            };
            State.Transactions.Add(transaction);
            State.Installments.Add(new DemoInstallment
            {
                PurchaseId = purchase.Id, InvoiceId = invoice.Id, TransactionId = transaction.Id, Number = i + 1, Amount = amounts[i]
            });
        }
        Save();
    }

    /// <summary>Parcela em fatura com pagamento registrado impede o cancelamento.</summary>
    public void CancelPurchase(Guid cardId, Guid purchaseId)
    {
        var card = FindCard(cardId);
        var purchase = State.Purchases.FirstOrDefault(p => p.Id == purchaseId && p.CardId == card.Id)
            ?? throw DemoException.NotFound("Compra não encontrada.");
        if (purchase.Canceled)
        {
            return;
        }

        var installments = State.Installments.Where(i => i.PurchaseId == purchaseId).ToList();
        var invoiceIds = installments.Select(i => i.InvoiceId).ToHashSet();
        if (State.Invoices.Any(i => invoiceIds.Contains(i.Id) && i.PaidAmount > 0))
        {
            throw DemoException.Rule("A compra tem parcela em fatura com pagamento registrado.");
        }

        purchase.Canceled = true;
        var transactionIds = installments.Select(i => i.TransactionId).ToHashSet();
        foreach (var transaction in State.Transactions.Where(t => transactionIds.Contains(t.Id)))
        {
            transaction.Status = TransactionStatus.CANCELED;
            transaction.Version++;
        }
        Save();
    }

    private DemoInvoice InvoiceFor(DemoCard card, DateOnly month)
    {
        var key = month.ToString("yyyy-MM", System.Globalization.CultureInfo.InvariantCulture);
        var invoice = State.Invoices.FirstOrDefault(i => i.CardId == card.Id && i.ReferenceMonth == key);
        if (invoice is null)
        {
            invoice = new DemoInvoice
            {
                CardId = card.Id,
                ReferenceMonth = key,
                ClosingDate = DayOf(month, card.ClosingDay),
                DueDate = DayOf(card.DueDay > card.ClosingDay ? month : month.AddMonths(1), card.DueDay)
            };
            State.Invoices.Add(invoice);
        }
        return invoice;
    }

    /// <summary>Compra até o fechamento, inclusive, entra na fatura do mês; depois, na seguinte.</summary>
    private static DateOnly ReferenceMonthOf(DateOnly purchaseDate, int closingDay)
    {
        var month = new DateOnly(purchaseDate.Year, purchaseDate.Month, 1);
        return purchaseDate > DayOf(month, closingDay) ? month.AddMonths(1) : month;
    }

    /// <summary>Dia inexistente no mês usa o último dia.</summary>
    private static DateOnly DayOf(DateOnly month, int day) =>
        new(month.Year, month.Month, Math.Min(day, DateTime.DaysInMonth(month.Year, month.Month)));

    /// <summary>Divide em centavos exatos; a diferença do arredondamento vai para a primeira parcela.</summary>
    private static List<decimal> Split(decimal total, int count)
    {
        var regular = Math.Floor(total / count * 100m) / 100m;
        if (regular == 0)
        {
            return [];
        }
        var result = new List<decimal> { total - regular * (count - 1) };
        result.AddRange(Enumerable.Repeat(regular, count - 1));
        return result;
    }
}
