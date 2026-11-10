using FinanTrack.Web.DTOs;
using FinanTrack.Web.Models;

namespace FinanTrack.Web.Demo;

/// <summary>
/// Dados fictícios de seis meses, sempre relativos à data de hoje. São criados pelas
/// mesmas operações do <see cref="DemoStore"/>, então obedecem às regras do sistema.
/// </summary>
internal static class DemoSeed
{
    private static readonly (string Name, CategoryType Type, string Icon, string Color)[] DefaultCategories =
    [
        ("Alimentação", CategoryType.EXPENSE, "Restaurant", "#FF8A65"),
        ("Moradia", CategoryType.EXPENSE, "Home", "#7C4DFF"),
        ("Transporte", CategoryType.EXPENSE, "DirectionsCar", "#4FC3F7"),
        ("Saúde", CategoryType.EXPENSE, "LocalHospital", "#FF647C"),
        ("Educação", CategoryType.EXPENSE, "School", "#FFB547"),
        ("Lazer", CategoryType.EXPENSE, "SportsEsports", "#B14DDB"),
        ("Compras", CategoryType.EXPENSE, "ShoppingBag", "#F06292"),
        ("Contas", CategoryType.EXPENSE, "Receipt", "#90A4AE"),
        ("Outros", CategoryType.EXPENSE, "MoreHoriz", "#A6A8BC"),
        ("Salário", CategoryType.INCOME, "Payments", "#20C997"),
        ("Freelance", CategoryType.INCOME, "Work", "#4DB6AC"),
        ("Investimentos", CategoryType.INCOME, "TrendingUp", "#81C784"),
        ("Outros", CategoryType.INCOME, "AttachMoney", "#A6A8BC")
    ];

    public static DemoState Create(DateOnly today, int schemaVersion)
    {
        var state = new DemoState { SchemaVersion = schemaVersion, SeededOn = today };
        var builder = new Builder(state, today);
        builder.Build();
        return state;
    }

    private sealed class Builder(DemoState state, DateOnly today)
    {
        private readonly Random _random = new(2026);
        private readonly DateOnly _thisMonth = new(today.Year, today.Month, 1);
        private DemoStore _store = null!;

        public void Build()
        {
            _store = DemoStore.Detached(state);

            foreach (var (name, type, icon, color) in DefaultCategories)
            {
                state.Categories.Add(new DemoCategory { Name = name, Type = type, Icon = icon, Color = color });
            }

            var checking = Account("Nubank", AccountType.CHECKING, 3200);
            var wallet = Account("Carteira", AccountType.WALLET, 180);
            Account("Poupança", AccountType.SAVINGS, 8500);
            Account("Tesouro Selic", AccountType.INVESTMENT, 15000);

            Recurring("Salário", 6800, TransactionType.INCOME, "Salário", checking, 5);
            Recurring("Aluguel", 1950, TransactionType.EXPENSE, "Moradia", checking, 10);
            Recurring("Internet fibra", 119.90m, TransactionType.EXPENSE, "Contas", checking, 15);
            Recurring("Academia", 109.90m, TransactionType.EXPENSE, "Saúde", checking, 8);
            Recurring("Curso de inglês", 289, TransactionType.EXPENSE, "Educação", checking, 20);
            CompletePastOccurrences();

            VariableTransactions(checking, wallet);
            Cards(checking);
            Goals();
            Budgets();
            Notifications();
        }

        private Guid Category(string name, CategoryType type = CategoryType.EXPENSE) =>
            state.Categories.First(c => c.Name == name && c.Type == type).Id;

        private Guid Account(string name, AccountType type, decimal initial) =>
            _store.CreateAccount(new AccountRequest(name, type, initial, false)).Id;

        private void Recurring(string description, decimal amount, TransactionType type, string category, Guid account, int day)
        {
            var categoryType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
            var start = Day(_thisMonth.AddMonths(-5), day);
            _store.CreateRecurring(new RecurringRequest(description, amount, type, Category(category, categoryType), account,
                RecurrenceFrequency.MONTHLY, start, null, null));
        }

        /// <summary>O visitante encontra o histórico pago; só o que vence a partir de hoje fica pendente.</summary>
        private void CompletePastOccurrences()
        {
            foreach (var t in state.Transactions.Where(t => t.TransactionDate < today))
            {
                t.Status = TransactionStatus.COMPLETED;
            }
        }

        private void VariableTransactions(Guid checking, Guid wallet)
        {
            for (var offset = -5; offset <= 0; offset++)
            {
                var month = _thisMonth.AddMonths(offset);
                Expense("Supermercado", Amount(380, 620), "Alimentação", checking, Day(month, 3));
                Expense("Supermercado", Amount(250, 480), "Alimentação", checking, Day(month, 17));
                Expense("Feira", Amount(60, 110), "Alimentação", wallet, Day(month, 12));
                Expense("Restaurante", Amount(90, 180), "Alimentação", checking, Day(month, 22));
                Expense("Combustível", Amount(210, 290), "Transporte", checking, Day(month, 9));
                Expense("Aplicativo de transporte", Amount(45, 95), "Transporte", checking, Day(month, 25));
                Expense("Conta de luz", Amount(160, 240), "Contas", checking, Day(month, 18));
                Expense("Cinema e pipoca", Amount(70, 120), "Lazer", wallet, Day(month, 14));
                Expense("Farmácia", Amount(40, 130), "Saúde", checking, Day(month, 27));
                if (offset % 2 == 0)
                {
                    Income("Projeto freelance", Amount(1200, 2400), "Freelance", checking, Day(month, 21));
                }
                Income("Rendimento da poupança", Amount(55, 75), "Investimentos", checking, Day(month, 28));
            }

            // Uma conta vencida e não paga, para o painel mostrar o alerta.
            var overdue = today.AddDays(-3);
            _store.CreateTransaction(new TransactionRequest("IPVA (parcela 3/3)", 412.35m, TransactionType.EXPENSE,
                TransactionStatus.PENDING, Category("Transporte"), checking, overdue, overdue, null, null));
        }

        /// <summary>Datas futuras ficam pendentes, com vencimento, como contas a pagar.</summary>
        private void Expense(string description, decimal amount, string category, Guid account, DateOnly date)
        {
            var past = date < today;
            _store.CreateTransaction(new TransactionRequest(description, amount, TransactionType.EXPENSE,
                past ? TransactionStatus.COMPLETED : TransactionStatus.PENDING, Category(category), account, date,
                past ? null : date, null, null));
        }

        private void Income(string description, decimal amount, string category, Guid account, DateOnly date)
        {
            if (date >= today)
            {
                return;
            }
            _store.CreateTransaction(new TransactionRequest(description, amount, TransactionType.INCOME,
                TransactionStatus.COMPLETED, Category(category, CategoryType.INCOME), account, date, null, null, null));
        }

        private void Cards(Guid checking)
        {
            var nubank = _store.CreateCard(new CardRequest("Roxinho", CardBrand.MASTERCARD, "4821", 9000, 3, 10, false)).Id;
            var inter = _store.CreateCard(new CardRequest("Inter Gold", CardBrand.VISA, "1934", 5000, 25, 5, false)).Id;

            Purchase(nubank, "Notebook", 4799, 10, "Compras", _thisMonth.AddMonths(-4).AddDays(11));
            Purchase(nubank, "Passagens aéreas", 1380, 3, "Lazer", _thisMonth.AddMonths(-2).AddDays(6));
            Purchase(inter, "Tênis de corrida", 549.90m, 2, "Compras", _thisMonth.AddMonths(-1).AddDays(15));
            for (var offset = -5; offset <= 0; offset++)
            {
                var month = _thisMonth.AddMonths(offset);
                Purchase(nubank, "Streaming de vídeo", 55.90m, 1, "Lazer", Day(month, 2));
                Purchase(inter, "Delivery", Amount(70, 140), 1, "Alimentação", Day(month, 19));
                Purchase(nubank, "Loja online", Amount(120, 320), 1, "Compras", Day(month, 23));
            }

            // Faturas já vencidas foram pagas integralmente na data do vencimento.
            foreach (var invoice in state.Invoices.Where(i => i.DueDate < today).ToList())
            {
                var remaining = _store.InvoiceDetail(invoice.CardId, invoice.Id).Invoice.RemainingAmount;
                if (remaining > 0)
                {
                    _store.PayInvoice(invoice.CardId, invoice.Id, new PaymentRequest(checking, remaining, invoice.DueDate));
                }
            }
        }

        private void Purchase(Guid card, string description, decimal amount, int installments, string category, DateOnly date)
        {
            if (date <= today)
            {
                _store.CreatePurchase(card, new PurchaseRequest(description, amount, installments, Category(category), date));
            }
        }

        private void Goals()
        {
            var emergency = _store.CreateGoal(new GoalRequest("Reserva de emergência", "Seis meses de custo de vida.",
                30000, 9000, today.AddMonths(14), null)).Id;
            var trip = _store.CreateGoal(new GoalRequest("Viagem para o Chile", "Férias de julho, com a família.",
                12000, 1500, today.AddMonths(8), null)).Id;
            var bike = _store.CreateGoal(new GoalRequest("Bicicleta nova", null, 3500, 500, today.AddMonths(3), null)).Id;

            Movements(emergency, [800, 800, 1200, 800, 650]);
            Movements(trip, [600, 750, 900]);
            Movements(bike, [500, 700, 600]);
            _store.MoveGoal(trip, GoalMovementType.WITHDRAWAL, new GoalMovementRequest(300, "Reserva do hotel"));
            _store.MoveGoal(bike, GoalMovementType.DEPOSIT, new GoalMovementRequest(1200, "Venda da bicicleta antiga"));
            _store.CompleteGoal(bike);
        }

        /// <summary>Um aporte por mês, com data retroativa para o histórico fazer sentido.</summary>
        private void Movements(Guid goal, decimal[] amounts)
        {
            for (var i = 0; i < amounts.Length; i++)
            {
                _store.MoveGoal(goal, GoalMovementType.DEPOSIT, new GoalMovementRequest(amounts[i], "Aporte mensal"));
                state.GoalMovements[^1].CreatedAt = new DateTimeOffset(
                    Day(_thisMonth.AddMonths(i - amounts.Length), 6).ToDateTime(new TimeOnly(9, 30)));
            }
        }

        private void Budgets()
        {
            foreach (var month in new[] { _thisMonth.AddMonths(-1), _thisMonth })
            {
                var key = month.ToString("yyyy-MM", System.Globalization.CultureInfo.InvariantCulture);
                _store.CreateBudget(new BudgetRequest(Category("Alimentação"), key, 1500));
                _store.CreateBudget(new BudgetRequest(Category("Transporte"), key, 450));
                _store.CreateBudget(new BudgetRequest(Category("Lazer"), key, 400));
                _store.CreateBudget(new BudgetRequest(Category("Compras"), key, 900));
            }
        }

        /// <summary>As mesmas mensagens que o job de notificações geraria hoje.</summary>
        private void Notifications()
        {
            var created = DateTimeOffset.Now.AddHours(-2);
            foreach (var budget in _store.BudgetMonth(null).Items.Where(b => b.State != BudgetState.NORMAL))
            {
                var exceeded = budget.State == BudgetState.EXCEEDED;
                state.Notifications.Add(new DemoNotification
                {
                    Type = exceeded ? NotificationType.BUDGET_EXCEEDED : NotificationType.BUDGET_ALERT,
                    Title = exceeded ? $"Orçamento de {budget.CategoryName} excedido" : $"Orçamento de {budget.CategoryName} em alerta",
                    Message = $"Você já usou {budget.Percentage:0}% do limite deste mês.",
                    CreatedAt = created
                });
            }
            foreach (var due in _store.Upcoming().Where(d => d.DueDate <= today.AddDays(5)))
            {
                var invoice = due.Type == "INVOICE";
                state.Notifications.Add(new DemoNotification
                {
                    Type = invoice ? NotificationType.INVOICE_DUE : NotificationType.DUE_DATE,
                    Title = due.Overdue ? $"{due.Description} venceu" : $"{due.Description} vence em breve",
                    Message = $"Vencimento em {due.DueDate:dd/MM/yyyy}.",
                    CreatedAt = created.AddMinutes(-5)
                });
            }
            state.Notifications.Add(new DemoNotification
            {
                Type = NotificationType.DUE_DATE,
                Title = "Bem-vindo ao FinanTrack",
                Message = "Esta é uma demonstração: os dados ficam só no seu navegador.",
                CreatedAt = created.AddDays(-1),
                ReadAt = created
            });
        }

        private decimal Amount(int min, int max) => Math.Round(min + (decimal)_random.NextDouble() * (max - min), 2);

        private static DateOnly Day(DateOnly month, int day) =>
            new(month.Year, month.Month, Math.Min(day, DateTime.DaysInMonth(month.Year, month.Month)));
    }
}
