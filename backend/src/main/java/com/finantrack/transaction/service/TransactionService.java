package com.finantrack.transaction.service;

import com.finantrack.account.entity.Account;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.transaction.dto.TransactionFilter;
import com.finantrack.transaction.dto.TransactionRequest;
import com.finantrack.transaction.dto.TransactionResponse;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionKind;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.mapper.TransactionMapper;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.repository.TransactionSpecifications;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper mapper;

    public TransactionService(TransactionRepository repository, AccountRepository accountRepository,
            CategoryRepository categoryRepository, TransactionMapper mapper) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, TransactionFilter filter, int page, int size,
            String sort) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidParameterException("A data inicial não pode ser posterior à data final.");
        }
        Page<Transaction> result = repository.findAll(
                TransactionSpecifications.matching(userId, filter), TransactionPaging.of(page, size, sort));
        return PageResponse.of(result, toResponses(result.getContent()));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id, UUID userId) {
        return toResponse(find(id, userId));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request, UUID userId) {
        Reference reference = resolve(request, userId, null);
        Transaction transaction = repository.saveAndFlush(new Transaction(userId, details(request)));
        return mapper.toResponse(transaction, reference.category(), reference.account());
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request, UUID userId) {
        Transaction transaction = findEditable(id, userId);
        if (request.version() != null && request.version() != transaction.getVersion()) {
            throw new ConflictException("Esta movimentação foi alterada em outro lugar. Recarregue e tente novamente.");
        }
        Reference reference = resolve(request, userId, transaction);
        transaction.update(details(request));
        repository.flush();
        return mapper.toResponse(transaction, reference.category(), reference.account());
    }

    @Transactional
    public TransactionResponse complete(UUID id, UUID userId) {
        Transaction transaction = findEditable(id, userId);
        if (transaction.getStatus() == TransactionStatus.CANCELED) {
            throw new BusinessRuleException("Uma movimentação cancelada não pode ser efetivada.");
        }
        transaction.complete();
        repository.flush();
        return toResponse(transaction);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(findEditable(id, userId));
    }

    private record Reference(Category category, Account account) {
    }

    /** Conta ou categoria arquivada só é aceita se a movimentação já a utilizava. */
    private Reference resolve(TransactionRequest request, UUID userId, Transaction current) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));

        if (!request.type().matches(category.getType())) {
            throw new BusinessRuleException("A categoria não corresponde ao tipo da movimentação.");
        }
        boolean sameCategory = current != null && Objects.equals(current.getCategoryId(), category.getId());
        if (category.isArchived() && !sameCategory) {
            throw new BusinessRuleException("A categoria selecionada está arquivada.");
        }
        boolean sameAccount = current != null && Objects.equals(current.getAccountId(), account.getId());
        if (account.isArchived() && !sameAccount) {
            throw new BusinessRuleException("A conta selecionada está arquivada.");
        }
        return new Reference(category, account);
    }

    private Transaction.Details details(TransactionRequest request) {
        return new Transaction.Details(request.description(), request.amount(), request.type(), request.status(),
                request.categoryId(), request.accountId(), request.transactionDate(), request.dueDate(),
                request.notes());
    }

    /** Parcelas de cartão e pagamentos de fatura só mudam pelos fluxos de cartão. */
    private Transaction findEditable(UUID id, UUID userId) {
        Transaction transaction = find(id, userId);
        if (transaction.getKind() != TransactionKind.STANDARD) {
            throw new BusinessRuleException("Esta movimentação só pode ser alterada pela área de cartões.");
        }
        return transaction;
    }

    private Transaction find(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada."));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return toResponses(List.of(transaction)).getFirst();
    }

    /** Carrega categorias e contas em lote, evitando uma consulta por linha. */
    private List<TransactionResponse> toResponses(List<Transaction> transactions) {
        Map<UUID, Category> categories = categoryRepository.findAllById(transactions.stream()
                        .map(Transaction::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<UUID, Account> accounts = accountRepository.findAllById(transactions.stream()
                        .map(Transaction::getAccountId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Account::getId, Function.identity()));

        return transactions.stream()
                .map(item -> mapper.toResponse(item, categories.get(item.getCategoryId()),
                        accounts.get(item.getAccountId())))
                .toList();
    }
}
