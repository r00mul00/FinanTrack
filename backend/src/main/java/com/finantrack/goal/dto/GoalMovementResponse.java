package com.finantrack.goal.dto;

import com.finantrack.goal.entity.GoalMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GoalMovementResponse(UUID id, GoalMovementType type, BigDecimal amount, String note, Instant createdAt) {
}
