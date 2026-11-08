package com.finantrack.transaction.mapper;

import com.finantrack.account.entity.Account;
import com.finantrack.category.entity.Category;
import com.finantrack.transaction.dto.TransactionResponse;
import com.finantrack.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "id", source = "transaction.id")
    @Mapping(target = "type", source = "transaction.type")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categoryIcon", source = "category.icon")
    @Mapping(target = "categoryColor", source = "category.color")
    @Mapping(target = "accountName", source = "account.name")
    TransactionResponse toResponse(Transaction transaction, Category category, Account account);
}
