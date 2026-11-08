package com.finantrack.transaction.repository;

import com.finantrack.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
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

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByAccountId(UUID accountId);

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
