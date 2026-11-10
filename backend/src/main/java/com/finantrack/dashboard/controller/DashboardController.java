package com.finantrack.dashboard.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.dashboard.dto.CategoryExpenseResponse;
import com.finantrack.dashboard.dto.DashboardSummaryResponse;
import com.finantrack.dashboard.dto.IncomeVsExpenseResponse;
import com.finantrack.dashboard.dto.UpcomingDueResponse;
import com.finantrack.dashboard.service.DashboardService;
import com.finantrack.dashboard.service.UpcomingDueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sem "from" e "to", todas as rotas consideram o mês corrente. */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService service;
    private final CurrentUser currentUser;

    private final UpcomingDueService upcomingDue;

    public DashboardController(DashboardService service, CurrentUser currentUser, UpcomingDueService upcomingDue) {
        this.service = service;
        this.currentUser = currentUser;
        this.upcomingDue = upcomingDue;
    }

    /** Relativo a hoje: não usa o período selecionado. */
    @Operation(summary = "Próximos vencimentos", description = "Contas a pagar pendentes e faturas em aberto, inclusive vencidas, nos próximos 30 dias (até 8 itens).")
    @GetMapping("/upcoming")
    public List<UpcomingDueResponse> upcoming() {
        return upcomingDue.upcoming(currentUser.id());
    }

    @Operation(summary = "Indicadores do período", description = "Saldo atual, receitas, despesas e resultado, com a variação sobre o período anterior. Sem datas, usa o mês corrente.")
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(currentUser.id(), from, to);
    }

    @Operation(summary = "Receitas e despesas no tempo", description = "Série diária para até 62 dias; mensal para períodos maiores.")
    @GetMapping("/income-vs-expense")
    public IncomeVsExpenseResponse incomeVsExpense(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.incomeVsExpense(currentUser.id(), from, to);
    }

    @Operation(summary = "Despesas por categoria", description = "Com o percentual de cada uma no total.")
    @GetMapping("/expenses-by-category")
    public List<CategoryExpenseResponse> expensesByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.expensesByCategory(currentUser.id(), from, to);
    }
}
