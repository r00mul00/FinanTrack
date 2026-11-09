package com.finantrack.search.service;

import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.goal.repository.FinancialGoalRepository;
import com.finantrack.recurring.entity.RecurringTransaction;
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import com.finantrack.search.dto.SearchResult;
import com.finantrack.search.dto.SearchResultType;
import com.finantrack.transaction.repository.TransactionRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cada consulta filtra pelo usuário autenticado e traz no máximo cinco itens de cada tipo. */
@Service
public class SearchService {

    private static final int MIN_LENGTH = 2;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final FinancialGoalRepository goals;
    private final CreditCardRepository cards;
    private final RecurringTransactionRepository recurring;

    public SearchService(TransactionRepository transactions, CategoryRepository categories,
            FinancialGoalRepository goals, CreditCardRepository cards, RecurringTransactionRepository recurring) {
        this.transactions = transactions;
        this.categories = categories;
        this.goals = goals;
        this.cards = cards;
        this.recurring = recurring;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(UUID userId, String query) {
        String term = query == null ? "" : query.trim();
        if (term.length() < MIN_LENGTH) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        transactions.findTop5ByUserIdAndDescriptionContainingIgnoreCaseOrderByTransactionDateDesc(userId, term)
                .forEach(item -> results.add(new SearchResult(SearchResultType.TRANSACTION, item.getId(),
                        item.getDescription(), item.getTransactionDate().format(DATE))));
        categories.findTop5ByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(userId, term)
                .forEach(item -> results.add(new SearchResult(SearchResultType.CATEGORY, item.getId(), item.getName(),
                        item.getType() == CategoryType.INCOME ? "Receita" : "Despesa")));
        goals.findTop5ByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(userId, term)
                .forEach(item -> results.add(new SearchResult(SearchResultType.GOAL, item.getId(), item.getName(),
                        "Prazo: " + item.getDeadline().format(DATE))));
        cards.findTop5ByUserIdAndNicknameContainingIgnoreCaseOrderByNicknameAsc(userId, term)
                .forEach(item -> results.add(new SearchResult(SearchResultType.CARD, item.getId(), item.getNickname(),
                        "Final " + item.getLastFour())));
        recurring.findTop5ByUserIdAndDescriptionContainingIgnoreCaseOrderByDescriptionAsc(userId, term)
                .forEach(item -> results.add(new SearchResult(SearchResultType.RECURRING, item.getId(),
                        item.getDescription(), nextOccurrence(item))));
        return results;
    }

    private String nextOccurrence(RecurringTransaction item) {
        return item.getNextOccurrenceDate() == null ? null : "Próxima: " + item.getNextOccurrenceDate().format(DATE);
    }
}
