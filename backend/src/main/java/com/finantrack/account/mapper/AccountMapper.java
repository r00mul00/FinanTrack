package com.finantrack.account.mapper;

import com.finantrack.account.dto.AccountResponse;
import com.finantrack.account.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    /** Enquanto não há transações, o saldo atual é o saldo inicial. */
    @Mapping(target = "currentBalance", source = "initialBalance")
    AccountResponse toResponse(Account account);
}
