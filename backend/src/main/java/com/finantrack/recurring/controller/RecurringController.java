package com.finantrack.recurring.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.recurring.dto.RecurringRequest;
import com.finantrack.recurring.dto.RecurringResponse;
import com.finantrack.recurring.service.RecurringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/recurring")
@Tag(name = "Contas recorrentes")
public class RecurringController {

    private final RecurringService service;
    private final CurrentUser currentUser;

    public RecurringController(RecurringService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Lista as recorrências")
    @GetMapping
    public List<RecurringResponse> list() {
        return service.list(currentUser.id());
    }

    @Operation(summary = "Detalha uma recorrência")
    @GetMapping("/{id}")
    public RecurringResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @Operation(summary = "Cria uma recorrência", description = "Os ciclos com data até hoje são gerados na hora, como lançamentos pendentes; os seguintes, pelo job diário.")
    @ApiResponse(responseCode = "422", description = "Término antes do início, ou referência inválida.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse create(@Valid @RequestBody RecurringRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera uma recorrência", description = "Vale para os próximos ciclos. A data de início não muda. Envie `version` para detectar edição concorrente.")
    @ApiResponse(responseCode = "409", description = "A recorrência foi alterada em outro lugar.")
    @ApiResponse(responseCode = "422", description = "Recorrência encerrada ou data de início alterada.")
    @PutMapping("/{id}")
    public RecurringResponse update(@PathVariable UUID id, @Valid @RequestBody RecurringRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui uma recorrência", description = "Os lançamentos já gerados permanecem.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }

    @Operation(summary = "Pausa uma recorrência")
    @ApiResponse(responseCode = "422", description = "Recorrência encerrada.")
    @PostMapping("/{id}/pause")
    public RecurringResponse pause(@PathVariable UUID id) {
        return service.pause(id, currentUser.id());
    }

    @Operation(summary = "Retoma uma recorrência", description = "Não gera os ciclos do período pausado.")
    @ApiResponse(responseCode = "422", description = "Recorrência encerrada.")
    @PostMapping("/{id}/resume")
    public RecurringResponse resume(@PathVariable UUID id) {
        return service.resume(id, currentUser.id());
    }

    @Operation(summary = "Próximas ocorrências", description = "Datas dos próximos ciclos (`count` de 1 a 24).")
    @GetMapping("/{id}/upcoming")
    public List<LocalDate> upcoming(@PathVariable UUID id, @RequestParam(defaultValue = "6") int count) {
        return service.upcoming(id, currentUser.id(), count);
    }
}
