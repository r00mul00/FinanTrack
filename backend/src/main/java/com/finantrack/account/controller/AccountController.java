package com.finantrack.account.controller;

import com.finantrack.account.dto.AccountRequest;
import com.finantrack.account.dto.AccountResponse;
import com.finantrack.account.service.AccountService;
import com.finantrack.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Contas")
public class AccountController {

    private final AccountService service;
    private final CurrentUser currentUser;

    public AccountController(AccountService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Lista as contas", description = "Com o saldo atual: saldo inicial mais as movimentações efetivadas.")
    @GetMapping
    public List<AccountResponse> list() {
        return service.list(currentUser.id());
    }

    @Operation(summary = "Detalha uma conta")
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @Operation(summary = "Cria uma conta")
    @ApiResponse(responseCode = "409", description = "Já existe uma conta com este nome.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera uma conta", description = "Arquivar mantém o histórico e impede novos lançamentos.")
    @ApiResponse(responseCode = "409", description = "Já existe uma conta com este nome.")
    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui uma conta", description = "Só é possível sem movimentações nem recorrências; caso contrário, arquive.")
    @ApiResponse(responseCode = "409", description = "A conta tem movimentações ou recorrências.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }
}
