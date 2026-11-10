package com.finantrack.recurring.repository;

import com.finantrack.recurring.entity.RecurringTransaction;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    List<RecurringTransaction> findAllByUserIdOrderByDescriptionAsc(UUID userId);

    List<RecurringTransaction> findTop5ByUserIdAndDescriptionContainingIgnoreCaseOrderByDescriptionAsc(UUID userId, String term);

    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByAccountId(UUID accountId);

    long countByCategoryId(UUID categoryId);

    @Query("""
            select r.id from RecurringTransaction r
             where r.status = com.finantrack.recurring.entity.RecurringStatus.ACTIVE
               and r.nextOccurrenceDate <= :today
            """)
    List<UUID> findDueIds(@Param("today") LocalDate today);

    /** O lock impede que duas execuções do job processem a mesma recorrência ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id")
    Optional<RecurringTransaction> findForUpdate(@Param("id") UUID id);

    /** Mesmo lock do job, restrito ao dono: edição e job nunca trabalham sobre a mesma recorrência juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id and r.userId = :userId")
    Optional<RecurringTransaction> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("""
            update RecurringTransaction r
               set r.categoryId = :to, r.updatedAt = :now, r.version = r.version + 1
             where r.categoryId = :from
            """)
    int reassignCategory(@Param("from") UUID from, @Param("to") UUID to, @Param("now") Instant now);
}
