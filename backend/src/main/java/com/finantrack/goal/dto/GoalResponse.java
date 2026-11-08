package com.finantrack.goal.dto;

import com.finantrack.goal.entity.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String name,
        String description,
        BigDecimal targetAmount,
        BigDecimal initialAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        BigDecimal progress,
        LocalDate deadline,
        boolean overdue,
        GoalStatus status,
        long version) {
}
