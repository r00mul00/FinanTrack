package com.finantrack.notification.service;

import com.finantrack.budget.entity.Budget;
import com.finantrack.budget.entity.BudgetState;
import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.card.service.CardLimitCalculator;
import com.finantrack.category.entity.Category;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.notification.entity.NotificationType;
import com.finantrack.notification.repository.NotificationRepository;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.repository.TransactionRepository.CategoryTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Regras de geração em docs/06. A chave de duplicidade garante um único aviso por evento. */
@Component
public class NotificationGenerator {

    static final int DUE_SOON_DAYS = 3;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final NotificationRepository notifications;
    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CreditCardInvoiceRepository invoices;
    private final CreditCardRepository cards;
    private final CardLimitCalculator limits;
    private final Clock clock;

    public NotificationGenerator(NotificationRepository notifications, BudgetRepository budgets,
            CategoryRepository categories, TransactionRepository transactions, CreditCardInvoiceRepository invoices,
            CreditCardRepository cards, CardLimitCalculator limits, Clock clock) {
        this.notifications = notifications;
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
        this.invoices = invoices;
        this.cards = cards;
        this.limits = limits;
        this.clock = clock;
    }

    public void generateBudgetAlerts(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        List<Budget> current = budgets.findAllByReferenceMonth(month.atDay(1));
        Map<UUID, String> names = categories.findAllById(current.stream().map(Budget::getCategoryId).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        current.stream().collect(Collectors.groupingBy(Budget::getUserId)).forEach((userId, userBudgets) -> {
            Map<UUID, BigDecimal> used = transactions
                    .sumCommittedByCategory(userId, month.atDay(1), month.atEndOfMonth()).stream()
                    .filter(item -> item.getCategoryId() != null)
                    .collect(Collectors.toMap(CategoryTotal::getCategoryId, CategoryTotal::getTotal));
            userBudgets.forEach(budget -> notifyBudget(budget, names.get(budget.getCategoryId()),
                    used.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO), month));
        });
    }

    /** Só despesas comuns: parcelas de cartão são avisadas pela fatura. */
    public void generateTransactionDueDates(LocalDate today) {
        for (Transaction item : transactions.findPendingExpensesDueBetween(today, today.plusDays(DUE_SOON_DAYS))) {
            insert(item.getUserId(), NotificationType.DUE_DATE, "Conta a vencer",
                    "%s vence em %s.".formatted(item.getDescription(), item.getDueDate().format(DATE)),
                    "due:%s:%s".formatted(item.getId(), item.getDueDate()));
        }
    }

    /** Faturas sem valor em aberto não geram aviso. */
    public void generateInvoiceDueDates(LocalDate today) {
        List<CreditCardInvoice> due = invoices.findAllByDueDateBetween(today, today.plusDays(DUE_SOON_DAYS));
        Map<UUID, BigDecimal> totals = limits.totalsOf(due.stream().map(CreditCardInvoice::getId).toList());
        Map<UUID, CreditCard> cardsById = cards.findAllById(due.stream().map(CreditCardInvoice::getCardId).toList())
                .stream().collect(Collectors.toMap(CreditCard::getId, Function.identity()));

        for (CreditCardInvoice invoice : due) {
            BigDecimal total = totals.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            if (total.compareTo(invoice.getPaidAmount()) > 0) {
                CreditCard card = cardsById.get(invoice.getCardId());
                insert(card.getUserId(), NotificationType.INVOICE_DUE, "Fatura a vencer",
                        "A fatura do cartão %s vence em %s.".formatted(card.getNickname(), invoice.getDueDate().format(DATE)),
                        "invoice:" + invoice.getId());
            }
        }
    }

    /** Quem excede sem passar pelo alerta recebe apenas o aviso de excedido. */
    private void notifyBudget(Budget budget, String category, BigDecimal used, YearMonth month) {
        BudgetState state = BudgetState.of(used, budget.getLimitAmount());
        if (state == BudgetState.EXCEEDED) {
            insert(budget.getUserId(), NotificationType.BUDGET_EXCEEDED, "Orçamento excedido",
                    "O orçamento de %s em %s foi excedido.".formatted(category, month.format(MONTH)),
                    "budget-exceeded:" + budget.getId());
        } else if (state == BudgetState.WARNING) {
            BigDecimal percent = used.multiply(HUNDRED).divide(budget.getLimitAmount(), 0, RoundingMode.DOWN);
            insert(budget.getUserId(), NotificationType.BUDGET_ALERT, "Orçamento em alerta",
                    "Você já usou %s%% do orçamento de %s em %s.".formatted(percent, category, month.format(MONTH)),
                    "budget-alert:" + budget.getId());
        }
    }

    private void insert(UUID userId, NotificationType type, String title, String message, String dedupeKey) {
        notifications.insertIfAbsent(UUID.randomUUID(), userId, type.name(), title, message, dedupeKey, clock.instant());
    }
}
