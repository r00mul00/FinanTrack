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

    interface CardExpense {
        UUID getCardId();

        long getPurchases();

        BigDecimal getTotal();
    }

    List<CreditCardInstallment> findAllByPurchaseId(UUID purchaseId);

    /** Pela data da parcela, como nas despesas: uma compra parcelada se distribui pelos meses. */
    @Query("""
            select p.cardId as cardId, count(distinct p.id) as purchases, sum(i.amount) as total
              from CreditCardInstallment i
              join CreditCardPurchase p on p.id = i.purchaseId
              join Transaction t on t.id = i.transactionId
             where t.userId = :userId and t.transactionDate between :from and :to and p.canceledAt is null
             group by p.cardId
            """)
    List<CardExpense> sumByCard(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

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
