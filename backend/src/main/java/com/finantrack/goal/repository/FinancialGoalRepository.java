package com.finantrack.goal.repository;

import com.finantrack.goal.entity.FinancialGoal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, UUID> {

    /** Metas ativas primeiro, depois pelo prazo mais próximo. */
    List<FinancialGoal> findAllByUserIdOrderByStatusAscDeadlineAsc(UUID userId);

    List<FinancialGoal> findTop5ByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(UUID userId, String term);

    Optional<FinancialGoal> findByIdAndUserId(UUID id, UUID userId);

    /** O lock serializa movimentações simultâneas: cada uma enxerga o saldo já atualizado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from FinancialGoal g where g.id = :id and g.userId = :userId")
    Optional<FinancialGoal> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
