package com.finantrack.dashboard.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.dashboard.dto.CategoryExpenseResponse;
import com.finantrack.dashboard.dto.DashboardSummaryResponse;
import com.finantrack.dashboard.dto.IncomeVsExpenseResponse;
import com.finantrack.dashboard.service.DashboardService;
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

    public DashboardController(DashboardService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(currentUser.id(), from, to);
    }

    @GetMapping("/income-vs-expense")
    public IncomeVsExpenseResponse incomeVsExpense(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.incomeVsExpense(currentUser.id(), from, to);
    }

    @GetMapping("/expenses-by-category")
    public List<CategoryExpenseResponse> expensesByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.expensesByCategory(currentUser.id(), from, to);
    }
}
