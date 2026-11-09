package com.finantrack.report.service;

import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardInstallmentRepository;
import com.finantrack.card.repository.CreditCardInstallmentRepository.CardExpense;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.dashboard.service.DashboardPeriod;
import com.finantrack.report.dto.ReportFilter;
import com.finantrack.report.dto.ReportResponse;
import com.finantrack.report.dto.ReportResponse.Column;
import com.finantrack.report.dto.ReportResponse.ColumnKind;
import com.finantrack.report.dto.ReportResponse.Indicator;
import com.finantrack.report.dto.ReportType;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionType;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.repository.TransactionRepository.CategoryTotal;
import com.finantrack.transaction.repository.TransactionRepository.DailyTotals;
import com.finantrack.transaction.repository.TransactionRepository.PeriodTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Usa as mesmas consultas do Dashboard: as regras de docs/06 ficam em um só lugar. */
@Service
public class ReportService {

    private static final int MAX_ROWS = 5000;
    private static final int DAILY_SERIES_MAX_DAYS = 62;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Column INCOME = new Column("Receitas", ColumnKind.MONEY);
    private static final Column EXPENSES = new Column("Despesas", ColumnKind.MONEY);
    private static final Column RESULT = new Column("Resultado", ColumnKind.MONEY);

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CreditCardRepository cards;
    private final CreditCardInstallmentRepository installments;
    private final Clock clock;

    public ReportService(TransactionRepository transactions, CategoryRepository categories, CreditCardRepository cards,
            CreditCardInstallmentRepository installments, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.cards = cards;
        this.installments = installments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportResponse generate(UUID userId, ReportType type, ReportFilter filter) {
        return switch (type) {
            case MONTHLY -> {
                YearMonth month = month(filter.month());
                yield series(userId, "Relatório mensal", month.atDay(1), month.atEndOfMonth(), true);
            }
            case ANNUAL -> {
                int year = filter.year() != null ? filter.year() : LocalDate.now(clock).getYear();
                yield series(userId, "Relatório anual", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), false);
            }
            case INCOME_VS_EXPENSE -> {
                DashboardPeriod period = period(filter);
                yield series(userId, "Receitas versus despesas", period.from(), period.to(),
                        period.lengthInDays() <= DAILY_SERIES_MAX_DAYS);
            }
            case EVOLUTION -> evolution(userId, period(filter));
            case BY_PERIOD -> byPeriod(userId, period(filter));
            case BY_CATEGORY -> byCategory(userId, period(filter));
            case BY_CARD -> byCard(userId, period(filter));
        };
    }

    private record Bucket(String label, BigDecimal income, BigDecimal expenses) {

        BigDecimal result() {
            return income.subtract(expenses);
        }
    }

    private ReportResponse series(UUID userId, String title, LocalDate from, LocalDate to, boolean daily) {
        List<Bucket> buckets = daily ? days(userId, from, to) : months(userId, from, to);
        Column first = daily ? new Column("Data", ColumnKind.DATE) : new Column("Mês", ColumnKind.TEXT);
        List<List<String>> rows = buckets.stream()
                .map(item -> List.of(item.label(), money(item.income()), money(item.expenses()), money(item.result())))
                .toList();
        return new ReportResponse(title, from, to, totalsIndicators(userId, from, to),
                List.of(first, INCOME, EXPENSES, RESULT), rows, seriesTotals(buckets, false));
    }

    /** O acumulado parte de zero no início do período. */
    private ReportResponse evolution(UUID userId, DashboardPeriod period) {
        List<Bucket> buckets = months(userId, period.from(), period.to());
        List<List<String>> rows = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (Bucket item : buckets) {
            accumulated = accumulated.add(item.result());
            rows.add(List.of(item.label(), money(item.income()), money(item.expenses()), money(item.result()),
                    money(accumulated)));
        }
        return new ReportResponse("Evolução financeira", period.from(), period.to(),
                totalsIndicators(userId, period.from(), period.to()),
                List.of(new Column("Mês", ColumnKind.TEXT), INCOME, EXPENSES, RESULT,
                        new Column("Acumulado", ColumnKind.MONEY)),
                rows, seriesTotals(buckets, true));
    }

    private ReportResponse byPeriod(UUID userId, DashboardPeriod period) {
        List<Transaction> items = transactions.findCountedInPeriod(userId, period.from(), period.to(),
                PageRequest.of(0, MAX_ROWS + 1));
        if (items.size() > MAX_ROWS) {
            throw new BusinessRuleException("O período tem lançamentos demais para o relatório. Escolha um período menor.");
        }
        Map<UUID, String> names = categories
                .findAllById(items.stream().map(Transaction::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        List<List<String>> rows = new ArrayList<>();
        for (Transaction item : items) {
            boolean isIncome = item.getType() == TransactionType.INCOME;
            income = isIncome ? income.add(item.getAmount()) : income;
            expenses = isIncome ? expenses : expenses.add(item.getAmount());
            rows.add(List.of(item.getTransactionDate().toString(), item.getDescription(),
                    names.getOrDefault(item.getCategoryId(), ""), isIncome ? money(item.getAmount()) : "",
                    isIncome ? "" : money(item.getAmount())));
        }
        return new ReportResponse("Lançamentos do período", period.from(), period.to(),
                totalsIndicators(userId, period.from(), period.to()),
                List.of(new Column("Data", ColumnKind.DATE), new Column("Descrição", ColumnKind.TEXT),
                        new Column("Categoria", ColumnKind.TEXT), INCOME, EXPENSES),
                rows, List.of("Total", "", "", money(income), money(expenses)));
    }

    private ReportResponse byCategory(UUID userId, DashboardPeriod period) {
        List<CategoryTotal> totals = transactions.sumExpensesByCategory(userId, period.from(), period.to());
        BigDecimal sum = totals.stream().map(CategoryTotal::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, String> names = categories.findAllById(totals.stream().map(CategoryTotal::getCategoryId).toList())
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));

        List<List<String>> rows = totals.stream()
                .map(item -> List.of(names.getOrDefault(item.getCategoryId(), ""), money(item.getTotal()),
                        item.getTotal().multiply(HUNDRED).divide(sum, 1, RoundingMode.HALF_UP).toPlainString()))
                .toList();
        return new ReportResponse("Despesas por categoria", period.from(), period.to(),
                List.of(new Indicator("Total de despesas", sum)),
                List.of(new Column("Categoria", ColumnKind.TEXT), new Column("Total", ColumnKind.MONEY),
                        new Column("Percentual", ColumnKind.PERCENT)),
                rows, List.of("Total", money(sum), rows.isEmpty() ? "0.0" : "100.0"));
    }

    private ReportResponse byCard(UUID userId, DashboardPeriod period) {
        List<CardExpense> totals = installments.sumByCard(userId, period.from(), period.to());
        Map<UUID, CreditCard> byId = cards.findAllById(totals.stream().map(CardExpense::getCardId).toList()).stream()
                .collect(Collectors.toMap(CreditCard::getId, Function.identity()));
        BigDecimal sum = totals.stream().map(CardExpense::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<List<String>> rows = totals.stream()
                .sorted((a, b) -> b.getTotal().compareTo(a.getTotal()))
                .map(item -> List.of(byId.get(item.getCardId()).getNickname(), Long.toString(item.getPurchases()),
                        money(item.getTotal())))
                .toList();
        return new ReportResponse("Despesas por cartão", period.from(), period.to(),
                List.of(new Indicator("Total nos cartões", sum)),
                List.of(new Column("Cartão", ColumnKind.TEXT), new Column("Compras", ColumnKind.INTEGER),
                        new Column("Total", ColumnKind.MONEY)),
                rows, List.of("Total", "", money(sum)));
    }

    private List<Indicator> totalsIndicators(UUID userId, LocalDate from, LocalDate to) {
        PeriodTotals totals = transactions.sumPeriod(userId, from, to);
        return List.of(new Indicator("Receitas", totals.getIncome()), new Indicator("Despesas", totals.getExpenses()),
                new Indicator("Resultado", totals.getIncome().subtract(totals.getExpenses())));
    }

    private List<String> seriesTotals(List<Bucket> buckets, boolean withAccumulated) {
        BigDecimal income = buckets.stream().map(Bucket::income).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = buckets.stream().map(Bucket::expenses).reduce(BigDecimal.ZERO, BigDecimal::add);
        String result = money(income.subtract(expenses));
        return withAccumulated
                ? List.of("Total", money(income), money(expenses), result, result)
                : List.of("Total", money(income), money(expenses), result);
    }

    private List<Bucket> days(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyTotals> byDay = byDay(userId, from, to);
        List<Bucket> buckets = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DailyTotals totals = byDay.get(day);
            buckets.add(totals == null
                    ? new Bucket(day.toString(), BigDecimal.ZERO, BigDecimal.ZERO)
                    : new Bucket(day.toString(), totals.getIncome(), totals.getExpenses()));
        }
        return buckets;
    }

    private List<Bucket> months(UUID userId, LocalDate from, LocalDate to) {
        Map<YearMonth, List<DailyTotals>> byMonth = byDay(userId, from, to).values().stream()
                .collect(Collectors.groupingBy(item -> YearMonth.from(item.getDate())));
        List<Bucket> buckets = new ArrayList<>();
        YearMonth last = YearMonth.from(to);
        for (YearMonth month = YearMonth.from(from); !month.isAfter(last); month = month.plusMonths(1)) {
            List<DailyTotals> days = byMonth.getOrDefault(month, List.of());
            buckets.add(new Bucket(month.format(MONTH_LABEL),
                    days.stream().map(DailyTotals::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add),
                    days.stream().map(DailyTotals::getExpenses).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return buckets;
    }

    private Map<LocalDate, DailyTotals> byDay(UUID userId, LocalDate from, LocalDate to) {
        return transactions.sumByDay(userId, from, to).stream()
                .collect(Collectors.toMap(DailyTotals::getDate, Function.identity()));
    }

    private DashboardPeriod period(ReportFilter filter) {
        return DashboardPeriod.resolve(filter.from(), filter.to(), LocalDate.now(clock));
    }

    private YearMonth month(String value) {
        if (value == null || value.isBlank()) {
            return YearMonth.from(LocalDate.now(clock));
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw new InvalidParameterException("Informe o mês no formato AAAA-MM.");
        }
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
