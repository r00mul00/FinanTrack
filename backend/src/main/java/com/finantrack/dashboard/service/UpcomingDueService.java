package com.finantrack.dashboard.service;

import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.card.service.CardLimitCalculator;
import com.finantrack.dashboard.dto.UpcomingDueResponse;
import com.finantrack.dashboard.dto.UpcomingDueResponse.Type;
import com.finantrack.transaction.entity.TransactionKind;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import com.finantrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras em docs/06: inclui os itens vencidos e não depende do período selecionado. */
@Service
public class UpcomingDueService {

    private static final int WINDOW_DAYS = 30;
    private static final int MAX_ITEMS = 8;

    private final TransactionRepository transactions;
    private final CreditCardRepository cards;
    private final CreditCardInvoiceRepository invoices;
    private final CardLimitCalculator limits;
    private final Clock clock;

    public UpcomingDueService(TransactionRepository transactions, CreditCardRepository cards,
            CreditCardInvoiceRepository invoices, CardLimitCalculator limits, Clock clock) {
        this.transactions = transactions;
        this.cards = cards;
        this.invoices = invoices;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UpcomingDueResponse> upcoming(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.plusDays(WINDOW_DAYS);

        List<UpcomingDueResponse> items = new ArrayList<>();
        transactions.findTop8ByUserIdAndStatusAndKindAndTypeAndDueDateLessThanEqualOrderByDueDateAsc(userId,
                        TransactionStatus.PENDING, TransactionKind.STANDARD, TransactionType.EXPENSE, limit)
                .forEach(item -> items.add(new UpcomingDueResponse(Type.BILL, item.getId(), null, item.getDescription(),
                        item.getAmount(), item.getDueDate(), item.getDueDate().isBefore(today))));
        items.addAll(openInvoices(userId, limit, today));

        return items.stream()
                .sorted(Comparator.comparing(UpcomingDueResponse::dueDate))
                .limit(MAX_ITEMS)
                .toList();
    }

    private List<UpcomingDueResponse> openInvoices(UUID userId, LocalDate limit, LocalDate today) {
        Map<UUID, CreditCard> userCards = cards.findAllByUserIdOrderByNicknameAsc(userId).stream()
                .collect(Collectors.toMap(CreditCard::getId, Function.identity()));
        List<CreditCardInvoice> due = invoices.findAllByCardIdInAndDueDateLessThanEqual(userCards.keySet(), limit);
        Map<UUID, BigDecimal> totals = limits.totalsOf(due.stream().map(CreditCardInvoice::getId).toList());

        List<UpcomingDueResponse> items = new ArrayList<>();
        for (CreditCardInvoice invoice : due) {
            BigDecimal remaining = totals.getOrDefault(invoice.getId(), BigDecimal.ZERO).subtract(invoice.getPaidAmount());
            if (remaining.signum() > 0) {
                items.add(new UpcomingDueResponse(Type.INVOICE, invoice.getId(), invoice.getCardId(),
                        "Fatura " + userCards.get(invoice.getCardId()).getNickname(), remaining, invoice.getDueDate(),
                        invoice.getDueDate().isBefore(today)));
            }
        }
        return items;
    }
}
