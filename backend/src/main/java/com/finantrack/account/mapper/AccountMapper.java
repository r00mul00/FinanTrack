package com.finantrack.account.mapper;

import com.finantrack.account.dto.AccountResponse;
import com.finantrack.account.entity.Account;
import java.math.BigDecimal;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account, BigDecimal currentBalance);
}
