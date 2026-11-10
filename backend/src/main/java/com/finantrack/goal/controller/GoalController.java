package com.finantrack.goal.controller;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.security.CurrentUser;
import com.finantrack.goal.dto.GoalMovementRequest;
import com.finantrack.goal.dto.GoalMovementResponse;
import com.finantrack.goal.dto.GoalRequest;
import com.finantrack.goal.dto.GoalResponse;
import com.finantrack.goal.service.GoalService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Metas")
public class GoalController {

    private final GoalService service;
    private final CurrentUser currentUser;

    public GoalController(GoalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Lista as metas", description = "Ativas primeiro, pelo prazo mais próximo.")
    @GetMapping
    public List<GoalResponse> list() {
        return service.list(currentUser.id());
    }

    @Operation(summary = "Detalha uma meta")
    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @Operation(summary = "Cria uma meta")
    @ApiResponse(responseCode = "422", description = "Prazo no passado.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera uma meta", description = "Mudar o valor inicial ajusta o saldo pela diferença. Envie `version` para detectar edição concorrente.")
    @ApiResponse(responseCode = "409", description = "A meta foi alterada em outro lugar.")
    @ApiResponse(responseCode = "422", description = "O novo valor inicial deixaria o saldo negativo.")
    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui uma meta", description = "Apaga também o histórico de movimentações.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }

    @Operation(summary = "Registra um aporte")
    @ApiResponse(responseCode = "422", description = "Meta concluída ou saldo acima do máximo.")
    @PostMapping("/{id}/deposits")
    public GoalResponse deposit(@PathVariable UUID id, @Valid @RequestBody GoalMovementRequest request) {
        return service.deposit(id, request, currentUser.id());
    }

    @Operation(summary = "Registra uma retirada")
    @ApiResponse(responseCode = "422", description = "Meta concluída ou retirada maior que o saldo.")
    @PostMapping("/{id}/withdrawals")
    public GoalResponse withdraw(@PathVariable UUID id, @Valid @RequestBody GoalMovementRequest request) {
        return service.withdraw(id, request, currentUser.id());
    }

    @Operation(summary = "Conclui a meta", description = "Idempotente. Depois de concluída, a meta não aceita movimentações.")
    @PostMapping("/{id}/complete")
    public GoalResponse complete(@PathVariable UUID id) {
        return service.complete(id, currentUser.id());
    }

    @Operation(summary = "Histórico de movimentações", description = "Paginado, do mais recente para o mais antigo.")
    @GetMapping("/{id}/movements")
    public PageResponse<GoalMovementResponse> movements(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.movements(id, currentUser.id(), page, size);
    }
}
