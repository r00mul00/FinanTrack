package com.finantrack.dashboard.service;

import com.finantrack.account.dto.AccountResponse;
import com.finantrack.account.service.AccountService;
import com.finantrack.category.entity.Category;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.dashboard.dto.CategoryExpenseResponse;
import com.finantrack.dashboard.dto.DashboardSummaryResponse;
import com.finantrack.dashboard.dto.IncomeVsExpenseResponse;
import com.finantrack.dashboard.dto.IncomeVsExpenseResponse.Granularity;
import com.finantrack.dashboard.dto.IncomeVsExpenseResponse.Point;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.repository.TransactionRepository.CategoryTotal;
import com.finantrack.transaction.repository.TransactionRepository.DailyTotals;
import com.finantrack.transaction.repository.TransactionRepository.PeriodTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int DAILY_SERIES_MAX_DAYS = 62;
    private static final int PERCENT_CALC_SCALE = 6;

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final AccountService accounts;
    private final Clock clock;

    public DashboardService(TransactionRepository transactions, CategoryRepository categories,
            AccountService accounts, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UUID userId, LocalDate from, LocalDate to) {
        DashboardPeriod period = resolve(from, to);
        DashboardPeriod previous = period.previous();

        PeriodTotals current = transactions.sumPeriod(userId, period.from(), period.to());
        PeriodTotals before = transactions.sumPeriod(userId, previous.from(), previous.to());
        BigDecimal result = current.getIncome().subtract(current.getExpenses());
        BigDecimal previousResult = before.getIncome().subtract(before.getExpenses());

        return new DashboardSummaryResponse(
                period.from(), period.to(), previous.from(), previous.to(),
                totalBalance(userId),
                current.getIncome(), current.getExpenses(), result,
                before.getIncome(), before.getExpenses(), previousResult,
                change(current.getIncome(), before.getIncome()),
                change(current.getExpenses(), before.getExpenses()),
                change(result, previousResult));
    }

    @Transactional(readOnly = true)
    public IncomeVsExpenseResponse incomeVsExpense(UUID userId, LocalDate from, LocalDate to) {
        DashboardPeriod period = resolve(from, to);
        Map<LocalDate, DailyTotals> byDay = transactions.sumByDay(userId, period.from(), period.to()).stream()
                .collect(Collectors.toMap(DailyTotals::getDate, Function.identity()));

        return period.lengthInDays() <= DAILY_SERIES_MAX_DAYS
                ? new IncomeVsExpenseResponse(Granularity.DAY, daily(period, byDay))
                : new IncomeVsExpenseResponse(Granularity.MONTH, monthly(period, byDay));
    }

    @Transactional(readOnly = true)
    public List<CategoryExpenseResponse> expensesByCategory(UUID userId, LocalDate from, LocalDate to) {
        DashboardPeriod period = resolve(from, to);
        List<CategoryTotal> totals = transactions.sumExpensesByCategory(userId, period.from(), period.to());
        BigDecimal sum = totals.stream().map(CategoryTotal::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, Category> byId = categories
                .findAllById(totals.stream().map(CategoryTotal::getCategoryId).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return totals.stream().map(item -> {
            Category category = byId.get(item.getCategoryId());
            return new CategoryExpenseResponse(item.getCategoryId(), category.getName(), category.getIcon(),
                    category.getColor(), item.getTotal(), percentage(item.getTotal(), sum));
        }).toList();
    }

    private DashboardPeriod resolve(LocalDate from, LocalDate to) {
        return DashboardPeriod.resolve(from, to, LocalDate.now(clock));
    }

    /** O saldo não depende do período: é a posição atual das contas ativas. */
    private BigDecimal totalBalance(UUID userId) {
        return accounts.list(userId).stream()
                .filter(account -> !account.archived())
                .map(AccountResponse::currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Dias sem movimento entram com zero, para o gráfico não pular datas. */
    private List<Point> daily(DashboardPeriod period, Map<LocalDate, DailyTotals> byDay) {
        List<Point> points = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            DailyTotals totals = byDay.get(day);
            points.add(totals == null
                    ? new Point(day, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new Point(day, totals.getIncome(), totals.getExpenses()));
        }
        return points;
    }

    private List<Point> monthly(DashboardPeriod period, Map<LocalDate, DailyTotals> byDay) {
        List<Point> points = new ArrayList<>();
        YearMonth last = YearMonth.from(period.to());
        for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(last); month = month.plusMonths(1)) {
            YearMonth current = month;
            List<DailyTotals> days = byDay.values().stream()
                    .filter(item -> YearMonth.from(item.getDate()).equals(current))
                    .toList();
            points.add(new Point(month.atDay(1),
                    days.stream().map(DailyTotals::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add),
                    days.stream().map(DailyTotals::getExpenses).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return points;
    }

    /** Usa o valor absoluto da base, pois o resultado anterior pode ser negativo. */
    private BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), PERCENT_CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal total) {
        return part.divide(total, PERCENT_CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
