package com.finantrack.user.dto;

import com.finantrack.user.entity.Theme;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String currency, Theme theme) {
}
