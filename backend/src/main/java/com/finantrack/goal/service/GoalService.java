package com.finantrack.goal.service;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.goal.dto.GoalMovementRequest;
import com.finantrack.goal.dto.GoalMovementResponse;
import com.finantrack.goal.dto.GoalRequest;
import com.finantrack.goal.dto.GoalResponse;
import com.finantrack.goal.entity.FinancialGoal;
import com.finantrack.goal.entity.GoalMovement;
import com.finantrack.goal.entity.GoalMovementType;
import com.finantrack.goal.entity.GoalStatus;
import com.finantrack.goal.mapper.GoalMapper;
import com.finantrack.goal.repository.FinancialGoalRepository;
import com.finantrack.goal.repository.GoalMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final int MAX_PAGE_SIZE = 100;

    private final FinancialGoalRepository goals;
    private final GoalMovementRepository movements;
    private final GoalMapper mapper;
    private final Clock clock;

    public GoalService(FinancialGoalRepository goals, GoalMovementRepository movements, GoalMapper mapper,
            Clock clock) {
        this.goals = goals;
        this.movements = movements;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        return goals.findAllByUserIdOrderByStatusAscDeadlineAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(UUID id, UUID userId) {
        return toResponse(find(id, userId));
    }

    @Transactional
    public GoalResponse create(GoalRequest request, UUID userId) {
        if (request.deadline().isBefore(today())) {
            throw new BusinessRuleException("O prazo não pode estar no passado.");
        }
        FinancialGoal goal = new FinancialGoal(userId, request.name(), request.description(), request.targetAmount(),
                request.initialAmount(), request.deadline());
        return toResponse(goals.saveAndFlush(goal));
    }

    @Transactional
    public GoalResponse update(UUID id, GoalRequest request, UUID userId) {
        FinancialGoal goal = findLocked(id, userId);
        if (request.version() != null && request.version() != goal.getVersion()) {
            throw new ConflictException("Esta meta foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        BigDecimal newCurrent = goal.getCurrentAmount().add(request.initialAmount().subtract(goal.getInitialAmount()));
        if (newCurrent.signum() < 0) {
            throw new BusinessRuleException("O novo valor inicial deixaria o saldo da meta negativo.");
        }
        requireWithinLimit(newCurrent);

        goal.update(request.name(), request.description(), request.targetAmount(), request.initialAmount(),
                request.deadline());
        goals.flush();
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse deposit(UUID id, GoalMovementRequest request, UUID userId) {
        FinancialGoal goal = findActiveLocked(id, userId);
        requireWithinLimit(goal.getCurrentAmount().add(request.amount()));

        goal.deposit(request.amount());
        return record(goal, GoalMovementType.DEPOSIT, request);
    }

    @Transactional
    public GoalResponse withdraw(UUID id, GoalMovementRequest request, UUID userId) {
        FinancialGoal goal = findActiveLocked(id, userId);
        if (request.amount().compareTo(goal.getCurrentAmount()) > 0) {
            throw new BusinessRuleException("A retirada é maior que o saldo disponível na meta.");
        }

        goal.withdraw(request.amount());
        return record(goal, GoalMovementType.WITHDRAWAL, request);
    }

    /** Concluir de novo não altera nada: a operação é idempotente. */
    @Transactional
    public GoalResponse complete(UUID id, UUID userId) {
        FinancialGoal goal = findLocked(id, userId);
        if (goal.getStatus() == GoalStatus.ACTIVE) {
            goal.complete(clock.instant());
            goals.flush();
        }
        return toResponse(goal);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        FinancialGoal goal = findLocked(id, userId);
        movements.deleteAllByGoal(goal.getId());
        goals.delete(goal);
    }

    @Transactional(readOnly = true)
    public PageResponse<GoalMovementResponse> movements(UUID id, UUID userId, int page, int size) {
        FinancialGoal goal = find(id, userId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("Paginação inválida.");
        }
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
        Page<GoalMovement> result = movements.findAllByGoalId(goal.getId(), PageRequest.of(page, size, newestFirst));
        return PageResponse.of(result, result.getContent().stream().map(mapper::toResponse).toList());
    }

    private GoalResponse record(FinancialGoal goal, GoalMovementType type, GoalMovementRequest request) {
        movements.save(new GoalMovement(goal.getId(), type, request.amount(), request.note(), clock.instant()));
        goals.flush();
        return toResponse(goal);
    }

    private void requireWithinLimit(BigDecimal value) {
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("O saldo da meta ultrapassaria o valor máximo permitido.");
        }
    }

    private FinancialGoal find(UUID id, UUID userId) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(this::notFound);
    }

    private FinancialGoal findLocked(UUID id, UUID userId) {
        return goals.findForUpdate(id, userId).orElseThrow(this::notFound);
    }

    private FinancialGoal findActiveLocked(UUID id, UUID userId) {
        FinancialGoal goal = findLocked(id, userId);
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            throw new BusinessRuleException("Esta meta já foi concluída e não aceita movimentações.");
        }
        return goal;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Meta não encontrada.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** O progresso é truncado e limitado a 100%: quem guardou além do alvo não vê mais que 100. */
    private GoalResponse toResponse(FinancialGoal goal) {
        BigDecimal progress = goal.getCurrentAmount().multiply(HUNDRED)
                .divide(goal.getTargetAmount(), 2, RoundingMode.DOWN)
                .min(HUNDRED);
        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount()).max(BigDecimal.ZERO);
        boolean overdue = goal.getStatus() == GoalStatus.ACTIVE && goal.getDeadline().isBefore(today());

        return new GoalResponse(goal.getId(), goal.getName(), goal.getDescription(), goal.getTargetAmount(),
                goal.getInitialAmount(), goal.getCurrentAmount(), remaining, progress, goal.getDeadline(), overdue,
                goal.getStatus(), goal.getVersion());
    }
}
