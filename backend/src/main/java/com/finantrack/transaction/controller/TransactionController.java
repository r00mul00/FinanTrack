package com.finantrack.transaction.controller;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.security.CurrentUser;
import com.finantrack.transaction.dto.TransactionFilter;
import com.finantrack.transaction.dto.TransactionRequest;
import com.finantrack.transaction.dto.TransactionResponse;
import com.finantrack.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "Lista as movimentações", description = "Paginada, com filtros opcionais (`search`, `type`, `status`, `categoryId`, `accountId`, `from`, `to`). `sort` aceita transactionDate, amount, description, status ou dueDate, com `,asc` ou `,desc`; `size` vai até 100.")
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @ModelAttribute TransactionFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort) {
        return service.list(currentUser.id(), filter, page, size, sort);
    }

    @Operation(summary = "Detalha uma movimentação")
    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @Operation(summary = "Cria uma movimentação", description = "A categoria precisa ser do mesmo tipo da movimentação. Receitas só contam nos totais quando efetivadas.")
    @ApiResponse(responseCode = "422", description = "Categoria de outro tipo, ou conta ou categoria arquivada.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera uma movimentação", description = "Envie `version` para detectar edição concorrente. Parcelas de cartão e pagamentos de fatura só mudam pela área de cartões.")
    @ApiResponse(responseCode = "409", description = "A movimentação foi alterada em outro lugar.")
    @ApiResponse(responseCode = "422", description = "Movimentação de cartão, ou referência inválida.")
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @Operation(summary = "Efetiva uma movimentação")
    @ApiResponse(responseCode = "422", description = "Movimentação cancelada ou de cartão.")
    @PostMapping("/{id}/complete")
    public TransactionResponse complete(@PathVariable UUID id) {
        return service.complete(id, currentUser.id());
    }

    @Operation(summary = "Exclui uma movimentação")
    @ApiResponse(responseCode = "422", description = "Movimentação de cartão.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }
}
