package com.finantrack.recurring.mapper;

import com.finantrack.account.entity.Account;
import com.finantrack.category.entity.Category;
import com.finantrack.recurring.dto.RecurringResponse;
import com.finantrack.recurring.entity.RecurringTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RecurringMapper {

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "type", source = "item.type")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categoryIcon", source = "category.icon")
    @Mapping(target = "categoryColor", source = "category.color")
    @Mapping(target = "accountName", source = "account.name")
    RecurringResponse toResponse(RecurringTransaction item, Category category, Account account);
}
