package com.finantrack.budget.service;

import com.finantrack.budget.dto.BudgetLimitRequest;
import com.finantrack.budget.dto.BudgetMonthResponse;
import com.finantrack.budget.dto.BudgetRequest;
import com.finantrack.budget.dto.BudgetResponse;
import com.finantrack.budget.entity.Budget;
import com.finantrack.budget.entity.BudgetState;
import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.repository.TransactionRepository.CategoryTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final String DUPLICATE = "Já existe um orçamento para esta categoria neste mês.";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetRepository repository;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Clock clock;

    public BudgetService(BudgetRepository repository, CategoryRepository categories,
            TransactionRepository transactions, Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Sem mês informado, usa o mês corrente. */
    @Transactional(readOnly = true)
    public BudgetMonthResponse listMonth(UUID userId, YearMonth requested) {
        YearMonth month = requested != null ? requested : YearMonth.from(LocalDate.now(clock));
        List<Budget> budgets = repository.findAllByUserIdAndReferenceMonth(userId, month.atDay(1));
        Map<UUID, BigDecimal> used = usedByCategory(userId, month);
        Map<UUID, Category> byId = categories
                .findAllById(budgets.stream().map(Budget::getCategoryId).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<BudgetResponse> items = budgets.stream()
                .map(budget -> toResponse(budget, byId.get(budget.getCategoryId()),
                        used.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(BudgetResponse::categoryName))
                .toList();

        return new BudgetMonthResponse(month.toString(),
                items.stream().map(BudgetResponse::limitAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(BudgetResponse::usedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items);
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request, UUID userId) {
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BusinessRuleException("Orçamentos só podem ser criados para categorias de despesa.");
        }
        if (category.isArchived()) {
            throw new BusinessRuleException("A categoria selecionada está arquivada.");
        }
        YearMonth month = YearMonth.parse(request.month());
        if (repository.existsByUserIdAndCategoryIdAndReferenceMonth(userId, category.getId(), month.atDay(1))) {
            throw new ConflictException(DUPLICATE);
        }

        Budget budget = saveChecked(new Budget(userId, category.getId(), month.atDay(1), request.limitAmount()));
        return toResponse(budget, category, usedByCategory(userId, month).getOrDefault(category.getId(), BigDecimal.ZERO));
    }

    /** Categoria e mês são fixos: para mudá-los, exclui-se e cria-se outro orçamento. */
    @Transactional
    public BudgetResponse updateLimit(UUID id, BudgetLimitRequest request, UUID userId) {
        Budget budget = find(id, userId);
        budget.changeLimit(request.limitAmount());
        repository.flush();

        Category category = categories.findById(budget.getCategoryId()).orElseThrow();
        YearMonth month = YearMonth.from(budget.getReferenceMonth());
        return toResponse(budget, category, usedByCategory(userId, month).getOrDefault(category.getId(), BigDecimal.ZERO));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(find(id, userId));
    }

    private Map<UUID, BigDecimal> usedByCategory(UUID userId, YearMonth month) {
        return transactions.sumCommittedByCategory(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(item -> item.getCategoryId() != null)
                .collect(Collectors.toMap(CategoryTotal::getCategoryId, CategoryTotal::getTotal));
    }

    /** O percentual é truncado para exibição: 79,996% nunca aparece como 80,00%. */
    private BudgetResponse toResponse(Budget budget, Category category, BigDecimal used) {
        BigDecimal percentage = used.multiply(HUNDRED).divide(budget.getLimitAmount(), 2, RoundingMode.DOWN);
        return new BudgetResponse(budget.getId(), category.getId(), category.getName(), category.getIcon(),
                category.getColor(), YearMonth.from(budget.getReferenceMonth()).toString(), budget.getLimitAmount(),
                used, budget.getLimitAmount().subtract(used), percentage, BudgetState.of(used, budget.getLimitAmount()));
    }

    private Budget find(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado."));
    }

    private Budget saveChecked(Budget budget) {
        try {
            return repository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(DUPLICATE);
        }
    }
}
