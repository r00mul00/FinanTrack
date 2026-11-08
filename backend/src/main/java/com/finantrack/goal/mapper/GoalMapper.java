package com.finantrack.goal.mapper;

import com.finantrack.goal.dto.GoalMovementResponse;
import com.finantrack.goal.entity.GoalMovement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GoalMapper {

    GoalMovementResponse toResponse(GoalMovement movement);
}
