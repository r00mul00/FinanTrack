package com.finantrack.category.dto;

import com.finantrack.category.entity.CategoryType;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryType type, String icon, String color, boolean archived) {
}
