package com.finantrack.card.service;

import com.finantrack.account.entity.Account;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.card.dto.InvoiceDetailResponse;
import com.finantrack.card.dto.InvoiceResponse;
import com.finantrack.card.dto.PaymentRequest;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.entity.InvoiceStatus;
import com.finantrack.card.repository.CreditCardInstallmentRepository;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final CreditCardRepository cards;
    private final CreditCardInvoiceRepository invoices;
    private final CreditCardInstallmentRepository installments;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CardLimitCalculator limits;
    private final Clock clock;
    private final AuditRecorder audit;

    public InvoiceService(CreditCardRepository cards, CreditCardInvoiceRepository invoices,
            CreditCardInstallmentRepository installments, AccountRepository accounts,
            TransactionRepository transactions, CardLimitCalculator limits, Clock clock, AuditRecorder audit) {
        this.audit = audit;
        this.cards = cards;
        this.invoices = invoices;
        this.installments = installments;
        this.accounts = accounts;
        this.transactions = transactions;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(UUID cardId, UUID userId) {
        CreditCard card = findCard(cardId, userId);
        List<CreditCardInvoice> all = invoices.findAllByCardIdOrderByReferenceMonthDesc(card.getId());
        Map<UUID, BigDecimal> totals = limits.totalsOf(all.stream().map(CreditCardInvoice::getId).toList());
        return all.stream()
                .map(invoice -> toResponse(invoice, totals.getOrDefault(invoice.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse detail(UUID cardId, UUID invoiceId, UUID userId) {
        CreditCardInvoice invoice = findInvoice(findCard(cardId, userId), invoiceId);
        List<InvoiceDetailResponse.Line> lines = installments.findLines(invoice.getId()).stream()
                .map(line -> new InvoiceDetailResponse.Line(line.getPurchaseId(), line.getDescription(), line.getNumber(),
                        line.getInstallmentsCount(), line.getAmount(), line.getPurchaseDate()))
                .toList();
        return new InvoiceDetailResponse(toResponse(invoice, totalOf(invoice)), lines);
    }

    /** Aceita pagamento parcial. Ao quitar, as parcelas da fatura passam a efetivadas. */
    @Transactional
    public InvoiceResponse pay(UUID cardId, UUID invoiceId, PaymentRequest request, UUID userId) {
        CreditCard card = cards.findForUpdate(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado."));
        CreditCardInvoice invoice = findInvoice(card, invoiceId);
        Account account = accounts.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
        if (account.isArchived()) {
            throw new BusinessRuleException("A conta selecionada está arquivada.");
        }

        BigDecimal total = totalOf(invoice);
        BigDecimal remaining = total.subtract(invoice.getPaidAmount());
        if (request.amount().compareTo(remaining) > 0) {
            throw new BusinessRuleException("O pagamento é maior que o valor restante da fatura.");
        }

        invoice.registerPayment(request.amount());
        Transaction payment = transactions.save(Transaction.invoicePayment(userId, description(card, invoice),
                request.amount(), account.getId(), invoice.getId(), request.paymentDate()));
        transactions.flush();
        audit.record(userId, AuditAction.CREATE, AuditEntityType.INVOICE_PAYMENT, payment.getId(), null,
                Map.of("cardId", card.getId(), "invoiceId", invoice.getId(), "accountId", account.getId(),
                        "amount", request.amount(), "paymentDate", request.paymentDate()));

        if (request.amount().compareTo(remaining) == 0) {
            transactions.completeInstallmentsOfInvoice(invoice.getId(), clock.instant());
        }
        return toResponse(invoice, total);
    }

    private String description(CreditCard card, CreditCardInvoice invoice) {
        YearMonth month = YearMonth.from(invoice.getReferenceMonth());
        return "Pagamento da fatura %s %02d/%d".formatted(card.getNickname(), month.getMonthValue(), month.getYear());
    }

    private BigDecimal totalOf(CreditCardInvoice invoice) {
        return limits.totalsOf(List.of(invoice.getId())).getOrDefault(invoice.getId(), BigDecimal.ZERO);
    }

    private CreditCard findCard(UUID cardId, UUID userId) {
        return cards.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado."));
    }

    private CreditCardInvoice findInvoice(CreditCard card, UUID invoiceId) {
        return invoices.findByIdAndCardId(invoiceId, card.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada."));
    }

    private InvoiceResponse toResponse(CreditCardInvoice invoice, BigDecimal total) {
        BigDecimal remaining = total.subtract(invoice.getPaidAmount()).max(BigDecimal.ZERO);
        return new InvoiceResponse(invoice.getId(), YearMonth.from(invoice.getReferenceMonth()).toString(),
                invoice.getClosingDate(), invoice.getDueDate(), total, invoice.getPaidAmount(), remaining,
                statusOf(invoice, total, remaining));
    }

    /** Estado derivado, nesta ordem, conforme docs/06. */
    private InvoiceStatus statusOf(CreditCardInvoice invoice, BigDecimal total, BigDecimal remaining) {
        LocalDate today = LocalDate.now(clock);
        if (total.signum() > 0 && remaining.signum() == 0) {
            return InvoiceStatus.PAID;
        }
        if (today.isAfter(invoice.getDueDate()) && remaining.signum() > 0) {
            return InvoiceStatus.OVERDUE;
        }
        return today.isAfter(invoice.getClosingDate()) ? InvoiceStatus.CLOSED : InvoiceStatus.OPEN;
    }
}
