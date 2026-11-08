package com.finantrack.category.mapper;

import com.finantrack.category.dto.CategoryResponse;
import com.finantrack.category.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
