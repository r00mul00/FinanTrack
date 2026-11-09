package com.finantrack.budget.repository;

import com.finantrack.budget.entity.Budget;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByUserIdAndReferenceMonth(UUID userId, LocalDate referenceMonth);

    List<Budget> findAllByReferenceMonth(LocalDate referenceMonth);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndCategoryIdAndReferenceMonth(UUID userId, UUID categoryId, LocalDate referenceMonth);

    long countByCategoryId(UUID categoryId);

    /** Remove os orçamentos que colidiriam com um já existente na categoria de destino. */
    @Modifying
    @Query("""
            delete from Budget b
             where b.categoryId = :from
               and exists (select 1 from Budget o where o.categoryId = :to and o.referenceMonth = b.referenceMonth)
            """)
    int deleteConflicting(@Param("from") UUID from, @Param("to") UUID to);

    @Modifying
    @Query("update Budget b set b.categoryId = :to, b.updatedAt = :now where b.categoryId = :from")
    int reassignCategory(@Param("from") UUID from, @Param("to") UUID to, @Param("now") Instant now);
}
