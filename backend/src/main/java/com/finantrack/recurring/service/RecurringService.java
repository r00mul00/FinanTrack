package com.finantrack.recurring.service;

import com.finantrack.account.entity.Account;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.recurring.dto.RecurringRequest;
import com.finantrack.recurring.dto.RecurringResponse;
import com.finantrack.recurring.entity.RecurringStatus;
import com.finantrack.recurring.entity.RecurringTransaction;
import com.finantrack.recurring.mapper.RecurringMapper;
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringService {

    private static final int MAX_UPCOMING = 24;

    private final RecurringTransactionRepository repository;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final RecurringOccurrenceWriter writer;
    private final RecurringMapper mapper;
    private final Clock clock;

    public RecurringService(RecurringTransactionRepository repository, CategoryRepository categories,
            AccountRepository accounts, RecurringOccurrenceWriter writer, RecurringMapper mapper, Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.accounts = accounts;
        this.writer = writer;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId) {
        List<RecurringTransaction> items = repository.findAllByUserIdOrderByDescriptionAsc(userId);
        Map<UUID, Category> byCategory = categories
                .findAllById(items.stream().map(RecurringTransaction::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<UUID, Account> byAccount = accounts
                .findAllById(items.stream().map(RecurringTransaction::getAccountId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        return items.stream()
                .map(item -> mapper.toResponse(item, byCategory.get(item.getCategoryId()), byAccount.get(item.getAccountId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringResponse get(UUID id, UUID userId) {
        return toResponse(find(id, userId));
    }

    /** Ciclos com data até hoje são gerados na hora, sem esperar o job da madrugada. */
    @Transactional
    public RecurringResponse create(RecurringRequest request, UUID userId) {
        requireValidPeriod(request);
        Reference reference = resolve(request, userId, null);
        RecurringTransaction item = repository.saveAndFlush(new RecurringTransaction(userId, request.description(),
                request.amount(), request.type(), request.categoryId(), request.accountId(), request.frequency(),
                request.startDate(), request.endDate()));
        writer.catchUp(item, today());
        repository.flush();
        return mapper.toResponse(item, reference.category(), reference.account());
    }

    /** Vale só para os próximos ciclos; os lançamentos já gerados não mudam. */
    @Transactional
    public RecurringResponse update(UUID id, RecurringRequest request, UUID userId) {
        RecurringTransaction item = findLocked(id, userId);
        if (request.version() != null && request.version() != item.getVersion()) {
            throw new ConflictException("Esta recorrência foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        if (item.getStatus() == RecurringStatus.FINISHED) {
            throw new BusinessRuleException("Esta recorrência já foi encerrada.");
        }
        if (!item.getStartDate().equals(request.startDate())) {
            throw new BusinessRuleException("A data de início não pode ser alterada.");
        }
        requireValidPeriod(request);
        Reference reference = resolve(request, userId, item);

        boolean frequencyChanged = item.getFrequency() != request.frequency();
        item.update(request.description(), request.amount(), request.type(), request.categoryId(), request.accountId(),
                request.frequency(), request.endDate());

        LocalDate next = item.getNextOccurrenceDate();
        if (frequencyChanged) {
            next = RecurrenceCalendar.firstOnOrAfter(item.getStartDate(), item.getFrequency(), next);
        }
        writer.applyCursor(item, next);
        repository.flush();
        return mapper.toResponse(item, reference.category(), reference.account());
    }

    @Transactional
    public RecurringResponse pause(UUID id, UUID userId) {
        RecurringTransaction item = findLocked(id, userId);
        if (item.getStatus() == RecurringStatus.FINISHED) {
            throw new BusinessRuleException("Esta recorrência já foi encerrada.");
        }
        item.pause();
        repository.flush();
        return toResponse(item);
    }

    /** Não gera retroativos: o período pausado fica sem lançamentos. */
    @Transactional
    public RecurringResponse resume(UUID id, UUID userId) {
        RecurringTransaction item = findLocked(id, userId);
        if (item.getStatus() == RecurringStatus.FINISHED) {
            throw new BusinessRuleException("Esta recorrência já foi encerrada.");
        }
        if (item.getStatus() == RecurringStatus.PAUSED) {
            item.activate();
            LocalDate from = item.getNextOccurrenceDate().isAfter(today()) ? item.getNextOccurrenceDate() : today();
            writer.applyCursor(item, RecurrenceCalendar.firstOnOrAfter(item.getStartDate(), item.getFrequency(), from));
            writer.catchUp(item, today());
            repository.flush();
        }
        return toResponse(item);
    }

    /** Os lançamentos já gerados permanecem, sem o vínculo com a recorrência. */
    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(findLocked(id, userId));
    }

    @Transactional(readOnly = true)
    public List<LocalDate> upcoming(UUID id, UUID userId, int count) {
        if (count < 1 || count > MAX_UPCOMING) {
            throw new InvalidParameterException("Informe entre 1 e 24 ocorrências.");
        }
        RecurringTransaction item = find(id, userId);
        List<LocalDate> dates = new ArrayList<>();
        if (item.getStatus() != RecurringStatus.ACTIVE) {
            return dates;
        }
        LocalDate next = item.getNextOccurrenceDate();
        while (next != null && dates.size() < count && (item.getEndDate() == null || !next.isAfter(item.getEndDate()))) {
            dates.add(next);
            next = RecurrenceCalendar.firstAfter(item.getStartDate(), item.getFrequency(), next);
        }
        return dates;
    }

    private record Reference(Category category, Account account) {
    }

    /** Conta ou categoria arquivada só é aceita se a recorrência já a utilizava. */
    private Reference resolve(RecurringRequest request, UUID userId, RecurringTransaction current) {
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        Account account = accounts.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
        if (!request.type().matches(category.getType())) {
            throw new BusinessRuleException("A categoria não corresponde ao tipo da recorrência.");
        }
        if (category.isArchived() && (current == null || !Objects.equals(current.getCategoryId(), category.getId()))) {
            throw new BusinessRuleException("A categoria selecionada está arquivada.");
        }
        if (account.isArchived() && (current == null || !Objects.equals(current.getAccountId(), account.getId()))) {
            throw new BusinessRuleException("A conta selecionada está arquivada.");
        }
        return new Reference(category, account);
    }

    private void requireValidPeriod(RecurringRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("A data de término não pode ser anterior à de início.");
        }
    }

    private RecurringResponse toResponse(RecurringTransaction item) {
        return mapper.toResponse(item, categories.findById(item.getCategoryId()).orElseThrow(),
                accounts.findById(item.getAccountId()).orElseThrow());
    }

    private RecurringTransaction find(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(this::notFound);
    }

    private RecurringTransaction findLocked(UUID id, UUID userId) {
        find(id, userId);
        return repository.findForUpdate(id).orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Recorrência não encontrada.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
