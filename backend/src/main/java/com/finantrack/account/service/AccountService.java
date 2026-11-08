package com.finantrack.account.service;

import com.finantrack.account.dto.AccountRequest;
import com.finantrack.account.dto.AccountResponse;
import com.finantrack.account.entity.Account;
import com.finantrack.account.mapper.AccountMapper;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final String DUPLICATE_NAME = "Já existe uma conta com este nome.";

    private final AccountRepository repository;
    private final AccountMapper mapper;

    public AccountService(AccountRepository repository, AccountMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return repository.findAllByUserIdOrderByNameAsc(userId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id, UUID userId) {
        return mapper.toResponse(find(id, userId));
    }

    @Transactional
    public AccountResponse create(AccountRequest request, UUID userId) {
        if (repository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        Account account = new Account(userId, request.name(), request.type(), request.initialBalance());
        return mapper.toResponse(saveChecked(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request, UUID userId) {
        Account account = find(id, userId);
        if (repository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name(), id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        account.update(request.name(), request.type(), request.initialBalance(), request.archived());
        return mapper.toResponse(saveChecked(account));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(find(id, userId));
    }

    /** Registro de outro usuário é tratado como inexistente: não revela que o ID existe. */
    private Account find(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
    }

    /** O índice único resolve o caso de duas requisições simultâneas com o mesmo nome. */
    private Account saveChecked(Account account) {
        try {
            return repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(DUPLICATE_NAME);
        }
    }
}
