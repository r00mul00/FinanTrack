package com.finantrack.account.service;

import com.finantrack.account.entity.Account;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida a conta escolhida pelo usuário em lançamentos, recorrências e pagamentos de fatura. */
@Component
public class AccountLookup {

    private final AccountRepository accounts;

    public AccountLookup(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Conta do usuário e não arquivada. Uma conta arquivada só é aceita se for
     * a que o registro já utilizava ({@code currentId}).
     */
    public Account usable(UUID id, UUID userId, UUID currentId) {
        Account account = accounts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
        if (account.isArchived() && !Objects.equals(currentId, account.getId())) {
            throw new BusinessRuleException("A conta selecionada está arquivada.");
        }
        return account;
    }
}
