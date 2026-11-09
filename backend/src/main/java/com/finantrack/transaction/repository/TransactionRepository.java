package com.finantrack.transaction.repository;

import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionKind;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** Movimentação realizada de uma conta. */
    interface AccountMovement {
        UUID getAccountId();

        BigDecimal getTotal();
    }

    String SIGNED_AMOUNT = """
            case when t.type = com.finantrack.transaction.entity.TransactionType.INCOME
                 then t.amount else -t.amount end
            """;

    String REALIZED = "t.status = com.finantrack.transaction.entity.TransactionStatus.COMPLETED";

    /** Regra de docs/06: receitas contam só depois de recebidas. */
    String COUNTED_INCOME = """
            (t.type = com.finantrack.transaction.entity.TransactionType.INCOME
             and t.status = com.finantrack.transaction.entity.TransactionStatus.COMPLETED)
            """;

    /**
     * Regra de docs/06: a despesa de cartão acontece na compra; o pagamento da
     * fatura (INVOICE_PAYMENT) nunca conta, para não somar a compra duas vezes.
     */
    String COUNTED_EXPENSE = """
            (t.type = com.finantrack.transaction.entity.TransactionType.EXPENSE and (
                (t.kind = com.finantrack.transaction.entity.TransactionKind.STANDARD
                 and t.status = com.finantrack.transaction.entity.TransactionStatus.COMPLETED)
                or (t.kind = com.finantrack.transaction.entity.TransactionKind.CARD_INSTALLMENT
                    and t.status <> com.finantrack.transaction.entity.TransactionStatus.CANCELED)))
            """;

    interface PeriodTotals {
        BigDecimal getIncome();

        BigDecimal getExpenses();
    }

    interface DailyTotals extends PeriodTotals {
        LocalDate getDate();
    }

    interface CategoryTotal {
        UUID getCategoryId();

        BigDecimal getTotal();
    }

    @Query("select coalesce(sum(case when " + COUNTED_INCOME + " then t.amount else 0BD end), 0BD) as income,"
            + " coalesce(sum(case when " + COUNTED_EXPENSE + " then t.amount else 0BD end), 0BD) as expenses"
            + " from Transaction t"
            + " where t.userId = :userId and t.transactionDate between :from and :to")
    PeriodTotals sumPeriod(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t.transactionDate as date,"
            + " sum(case when " + COUNTED_INCOME + " then t.amount else 0BD end) as income,"
            + " sum(case when " + COUNTED_EXPENSE + " then t.amount else 0BD end) as expenses"
            + " from Transaction t"
            + " where t.userId = :userId and t.transactionDate between :from and :to"
            + " group by t.transactionDate")
    List<DailyTotals> sumByDay(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t.categoryId as categoryId, sum(t.amount) as total from Transaction t"
            + " where t.userId = :userId and t.transactionDate between :from and :to and " + COUNTED_EXPENSE
            + " group by t.categoryId order by sum(t.amount) desc")
    List<CategoryTotal> sumExpensesByCategory(@Param("userId") UUID userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Ao quitar a fatura, as parcelas pendentes dela passam a efetivadas. */
    @Modifying
    @Query("""
            update Transaction t
               set t.status = com.finantrack.transaction.entity.TransactionStatus.COMPLETED,
                   t.updatedAt = :now, t.version = t.version + 1
             where t.status = com.finantrack.transaction.entity.TransactionStatus.PENDING
               and t.id in (select i.transactionId from CreditCardInstallment i where i.invoiceId = :invoiceId)
            """)
    int completeInstallmentsOfInvoice(@Param("invoiceId") UUID invoiceId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Transaction t
               set t.status = com.finantrack.transaction.entity.TransactionStatus.CANCELED,
                   t.updatedAt = :now, t.version = t.version + 1
             where t.id in (select i.transactionId from CreditCardInstallment i where i.purchaseId = :purchaseId)
            """)
    int cancelInstallmentsOfPurchase(@Param("purchaseId") UUID purchaseId, @Param("now") Instant now);

    /** Regra de docs/06: o orçamento mede o comprometido, por isso inclui pendentes. */
    @Query("select t.categoryId as categoryId, sum(t.amount) as total from Transaction t"
            + " where t.userId = :userId and t.transactionDate between :from and :to"
            + " and t.type = com.finantrack.transaction.entity.TransactionType.EXPENSE"
            + " and t.status <> com.finantrack.transaction.entity.TransactionStatus.CANCELED"
            + " and t.kind <> com.finantrack.transaction.entity.TransactionKind.INVOICE_PAYMENT"
            + " group by t.categoryId")
    List<CategoryTotal> sumCommittedByCategory(@Param("userId") UUID userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select t from Transaction t"
            + " where t.status = com.finantrack.transaction.entity.TransactionStatus.PENDING"
            + " and t.kind = com.finantrack.transaction.entity.TransactionKind.STANDARD"
            + " and t.type = com.finantrack.transaction.entity.TransactionType.EXPENSE"
            + " and t.dueDate between :from and :to")
    List<Transaction> findPendingExpensesDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Transaction> findTop5ByUserIdAndDescriptionContainingIgnoreCaseOrderByTransactionDateDesc(UUID userId, String term);

    List<Transaction> findTop8ByUserIdAndStatusAndKindAndTypeAndDueDateLessThanEqualOrderByDueDateAsc(UUID userId,
            TransactionStatus status, TransactionKind kind, TransactionType type, LocalDate limit);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByAccountId(UUID accountId);

    @Query("select count(t) > 0 from Transaction t where t.recurringId = :id and t.occurrenceDate = :date")
    boolean existsOccurrence(@Param("id") UUID recurringId, @Param("date") LocalDate occurrenceDate);

    long countByCategoryId(UUID categoryId);

    @Query("select t.accountId as accountId, sum(" + SIGNED_AMOUNT + ") as total from Transaction t"
            + " where t.userId = :userId and t.accountId is not null and " + REALIZED + " group by t.accountId")
    List<AccountMovement> sumRealizedByAccount(@Param("userId") UUID userId);

    @Query("select coalesce(sum(" + SIGNED_AMOUNT + "), 0) from Transaction t"
            + " where t.accountId = :accountId and " + REALIZED)
    BigDecimal sumRealized(@Param("accountId") UUID accountId);

    @Modifying
    @Query("""
            update Transaction t
               set t.categoryId = :to, t.updatedAt = :now, t.version = t.version + 1
             where t.categoryId = :from
            """)
    int reassignCategory(@Param("from") UUID from, @Param("to") UUID to, @Param("now") Instant now);
}
