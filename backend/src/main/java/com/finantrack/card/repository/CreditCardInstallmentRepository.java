package com.finantrack.card.repository;

import com.finantrack.card.entity.CreditCardInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditCardInstallmentRepository extends JpaRepository<CreditCardInstallment, UUID> {

    interface InvoiceTotal {
        UUID getInvoiceId();

        BigDecimal getTotal();
    }

    interface InvoiceLine {
        UUID getPurchaseId();

        String getDescription();

        int getNumber();

        int getInstallmentsCount();

        BigDecimal getAmount();

        LocalDate getPurchaseDate();
    }

    List<CreditCardInstallment> findAllByPurchaseId(UUID purchaseId);

    /** Compras canceladas não fazem parte do total da fatura. */
    @Query("""
            select i.invoiceId as invoiceId, sum(i.amount) as total
              from CreditCardInstallment i join CreditCardPurchase p on p.id = i.purchaseId
             where i.invoiceId in :invoiceIds and p.canceledAt is null
             group by i.invoiceId
            """)
    List<InvoiceTotal> sumByInvoices(@Param("invoiceIds") Collection<UUID> invoiceIds);

    @Query("""
            select i.purchaseId as purchaseId, p.description as description, i.number as number,
                   p.installmentsCount as installmentsCount, i.amount as amount, p.purchaseDate as purchaseDate
              from CreditCardInstallment i join CreditCardPurchase p on p.id = i.purchaseId
             where i.invoiceId = :invoiceId and p.canceledAt is null
             order by p.purchaseDate desc, p.description asc
            """)
    List<InvoiceLine> findLines(@Param("invoiceId") UUID invoiceId);
}
