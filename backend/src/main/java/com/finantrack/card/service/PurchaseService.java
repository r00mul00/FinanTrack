package com.finantrack.card.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.card.dto.PurchaseRequest;
import com.finantrack.card.dto.PurchaseResponse;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.entity.CreditCardInstallment;
import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.entity.CreditCardPurchase;
import com.finantrack.card.repository.CreditCardInstallmentRepository;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
import com.finantrack.card.repository.CreditCardPurchaseRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CreditCardRepository cards;
    private final CreditCardPurchaseRepository purchases;
    private final CreditCardInvoiceRepository invoices;
    private final CreditCardInstallmentRepository installments;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CardLimitCalculator limits;
    private final Clock clock;
    private final AuditRecorder audit;

    public PurchaseService(CreditCardRepository cards, CreditCardPurchaseRepository purchases,
            CreditCardInvoiceRepository invoices, CreditCardInstallmentRepository installments,
            CategoryRepository categories, TransactionRepository transactions, CardLimitCalculator limits,
            Clock clock, AuditRecorder audit) {
        this.audit = audit;
        this.cards = cards;
        this.purchases = purchases;
        this.invoices = invoices;
        this.installments = installments;
        this.categories = categories;
        this.transactions = transactions;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> list(UUID cardId, UUID userId, int page, int size) {
        CreditCard card = cards.findByIdAndUserId(cardId, userId).orElseThrow(this::cardNotFound);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("Paginação inválida.");
        }
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CreditCardPurchase> result = purchases.findAllByCardId(card.getId(), PageRequest.of(page, size, newestFirst));
        return PageResponse.of(result, result.getContent().stream().map(this::toResponse).toList());
    }

    /** Compra, faturas, parcelas e movimentações são gravadas juntas ou nenhuma delas. */
    @Transactional
    public PurchaseResponse create(UUID cardId, PurchaseRequest request, UUID userId) {
        CreditCard card = cards.findForUpdate(cardId, userId).orElseThrow(this::cardNotFound);
        if (card.isArchived()) {
            throw new BusinessRuleException("Este cartão está arquivado.");
        }
        Category category = expenseCategory(request.categoryId(), userId);

        List<BigDecimal> amounts = InstallmentSplitter.split(request.totalAmount(), request.installments());
        if (amounts.isEmpty()) {
            throw new BusinessRuleException("O valor é pequeno demais para este número de parcelas.");
        }
        BigDecimal available = card.getCreditLimit().subtract(limits.usedByCard(card.getId()));
        if (request.totalAmount().compareTo(available) > 0) {
            throw new BusinessRuleException("A compra ultrapassa o limite disponível do cartão.");
        }

        CreditCardPurchase purchase = purchases.save(new CreditCardPurchase(card.getId(), category.getId(),
                request.description(), request.totalAmount(), request.installments(), request.purchaseDate()));

        YearMonth firstMonth = InvoiceCalendar.referenceMonthOf(request.purchaseDate(), card.getClosingDay());
        for (int i = 0; i < amounts.size(); i++) {
            int number = i + 1;
            CreditCardInvoice invoice = invoiceFor(card, firstMonth.plusMonths(i));
            String description = amounts.size() == 1
                    ? request.description()
                    : "%s (%d/%d)".formatted(request.description(), number, amounts.size());
            Transaction transaction = transactions.save(Transaction.cardInstallment(userId, description, amounts.get(i),
                    category.getId(), request.purchaseDate().plusMonths(i)));
            installments.save(new CreditCardInstallment(purchase.getId(), invoice.getId(), transaction.getId(), number,
                    amounts.get(i)));
        }
        transactions.flush();
        audit.record(userId, AuditAction.CREATE, AuditEntityType.CARD_PURCHASE, purchase.getId(), null, snapshot(purchase));
        return toResponse(purchase);
    }

    /** Parcela em fatura com pagamento registrado impede o cancelamento. */
    @Transactional
    public void cancel(UUID cardId, UUID purchaseId, UUID userId) {
        CreditCard card = cards.findForUpdate(cardId, userId).orElseThrow(this::cardNotFound);
        CreditCardPurchase purchase = purchases.findByIdAndCardId(purchaseId, card.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Compra não encontrada."));
        if (purchase.getCanceledAt() != null) {
            return;
        }

        List<UUID> invoiceIds = installments.findAllByPurchaseId(purchaseId).stream()
                .map(CreditCardInstallment::getInvoiceId)
                .toList();
        boolean paid = invoices.findAllById(invoiceIds).stream().anyMatch(item -> item.getPaidAmount().signum() > 0);
        if (paid) {
            throw new BusinessRuleException("A compra tem parcela em fatura com pagamento registrado.");
        }

        purchase.cancel(clock.instant());
        purchases.flush();
        transactions.cancelInstallmentsOfPurchase(purchaseId, clock.instant());
        audit.record(userId, AuditAction.CANCEL, AuditEntityType.CARD_PURCHASE, purchaseId, snapshot(purchase), null);
    }

    private Map<String, Object> snapshot(CreditCardPurchase purchase) {
        return Map.of("cardId", purchase.getCardId(), "description", purchase.getDescription(),
                "totalAmount", purchase.getTotalAmount(), "installments", purchase.getInstallmentsCount(),
                "categoryId", purchase.getCategoryId(), "purchaseDate", purchase.getPurchaseDate());
    }

    private CreditCardInvoice invoiceFor(CreditCard card, YearMonth month) {
        return invoices.findByCardIdAndReferenceMonth(card.getId(), month.atDay(1))
                .orElseGet(() -> invoices.saveAndFlush(new CreditCardInvoice(card.getId(), month.atDay(1),
                        InvoiceCalendar.closingDate(month, card.getClosingDay()),
                        InvoiceCalendar.dueDate(month, card.getClosingDay(), card.getDueDay()))));
    }

    private Category expenseCategory(UUID categoryId, UUID userId) {
        Category category = categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BusinessRuleException("Compras só podem usar categorias de despesa.");
        }
        if (category.isArchived()) {
            throw new BusinessRuleException("A categoria selecionada está arquivada.");
        }
        return category;
    }

    private ResourceNotFoundException cardNotFound() {
        return new ResourceNotFoundException("Cartão não encontrado.");
    }

    private PurchaseResponse toResponse(CreditCardPurchase purchase) {
        return new PurchaseResponse(purchase.getId(), purchase.getDescription(), purchase.getTotalAmount(),
                purchase.getInstallmentsCount(), purchase.getCategoryId(), purchase.getPurchaseDate(),
                purchase.getCanceledAt() != null);
    }
}
