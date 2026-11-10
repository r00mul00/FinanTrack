package com.finantrack.budget.controller;

import com.finantrack.budget.dto.BudgetLimitRequest;
import com.finantrack.budget.dto.BudgetMonthResponse;
import com.finantrack.budget.dto.BudgetRequest;
import com.finantrack.budget.dto.BudgetResponse;
import com.finantrack.budget.service.BudgetService;
import com.finantrack.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/budgets")
@Tag(name = "Orçamentos")
public class BudgetController {

    private final BudgetService service;
    private final CurrentUser currentUser;

    public BudgetController(BudgetService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Orçamentos do mês", description = "Sem `month` (AAAA-MM), usa o mês corrente. O uso soma as despesas comprometidas, inclusive pendentes; o estado passa a WARNING em 80% e a EXCEEDED acima de 100%.")
    @GetMapping
    public BudgetMonthResponse list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.listMonth(currentUser.id(), month);
    }

    @Operation(summary = "Cria um orçamento", description = "Um por categoria de despesa e mês.")
    @ApiResponse(responseCode = "409", description = "Já existe orçamento para esta categoria no mês.")
    @ApiResponse(responseCode = "422", description = "Categoria de receita ou arquivada.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@Valid @RequestBody BudgetRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera o limite", description = "Categoria e mês são fixos; para mudá-los, exclua e crie outro.")
    @PutMapping("/{id}")
    public BudgetResponse updateLimit(@PathVariable UUID id, @Valid @RequestBody BudgetLimitRequest request) {
        return service.updateLimit(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui um orçamento")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }
}
