package com.finantrack.user.mapper;

import com.finantrack.user.dto.UserResponse;
import com.finantrack.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
