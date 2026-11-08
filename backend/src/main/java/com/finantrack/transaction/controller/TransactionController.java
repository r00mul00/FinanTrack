package com.finantrack.transaction.controller;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.security.CurrentUser;
import com.finantrack.transaction.dto.TransactionFilter;
import com.finantrack.transaction.dto.TransactionRequest;
import com.finantrack.transaction.dto.TransactionResponse;
import com.finantrack.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transações")
public class TransactionController {

    private final TransactionService service;
    private final CurrentUser currentUser;

    public TransactionController(TransactionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @ModelAttribute TransactionFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort) {
        return service.list(currentUser.id(), filter, page, size, sort);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
        return service.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @PostMapping("/{id}/complete")
    public TransactionResponse complete(@PathVariable UUID id) {
        return service.complete(id, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }
}
