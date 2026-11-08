package com.finantrack.goal.repository;

import com.finantrack.goal.entity.GoalMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalMovementRepository extends JpaRepository<GoalMovement, UUID> {

    Page<GoalMovement> findAllByGoalId(UUID goalId, Pageable pageable);

    @Modifying
    @Query("delete from GoalMovement m where m.goalId = :goalId")
    int deleteAllByGoal(@Param("goalId") UUID goalId);
}
