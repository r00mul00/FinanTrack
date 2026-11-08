package com.finantrack.card.service;

import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.repository.CreditCardInstallmentRepository;
import com.finantrack.card.repository.CreditCardInstallmentRepository.InvoiceTotal;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Limite em uso: soma do que falta pagar em cada fatura, inclusive das parcelas futuras. */
@Component
public class CardLimitCalculator {

    private final CreditCardInvoiceRepository invoices;
    private final CreditCardInstallmentRepository installments;

    public CardLimitCalculator(CreditCardInvoiceRepository invoices, CreditCardInstallmentRepository installments) {
        this.invoices = invoices;
        this.installments = installments;
    }

    public Map<UUID, BigDecimal> usedByCard(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        List<CreditCardInvoice> all = invoices.findAllByCardIdIn(cardIds);
        Map<UUID, BigDecimal> totals = totalsOf(all.stream().map(CreditCardInvoice::getId).toList());

        return all.stream().collect(Collectors.toMap(CreditCardInvoice::getCardId,
                invoice -> totals.getOrDefault(invoice.getId(), BigDecimal.ZERO)
                        .subtract(invoice.getPaidAmount())
                        .max(BigDecimal.ZERO),
                BigDecimal::add));
    }

    public BigDecimal usedByCard(UUID cardId) {
        return usedByCard(List.of(cardId)).getOrDefault(cardId, BigDecimal.ZERO);
    }

    public Map<UUID, BigDecimal> totalsOf(Collection<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return installments.sumByInvoices(invoiceIds).stream()
                .collect(Collectors.toMap(InvoiceTotal::getInvoiceId, InvoiceTotal::getTotal));
    }
}
