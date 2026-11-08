package com.finantrack.transaction.service;

import com.finantrack.common.exception.InvalidParameterException;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Só aceita campos conhecidos: o cliente nunca escolhe colunas livremente. */
final class TransactionPaging {

    private static final int MAX_SIZE = 100;

    private static final Map<String, String> SORTABLE = Map.of(
            "transactionDate", "transactionDate",
            "amount", "amount",
            "description", "description",
            "status", "status",
            "dueDate", "dueDate");

    private TransactionPaging() {
    }

    static Pageable of(int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidParameterException("A página não pode ser negativa.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidParameterException("O tamanho da página deve estar entre 1 e 100.");
        }
        return PageRequest.of(page, size, parse(sort));
    }

    /** O desempate por criação mantém a paginação estável entre páginas. */
    private static Sort parse(String sort) {
        String[] parts = sort.split(",");
        String property = SORTABLE.get(parts[0].trim());
        if (property == null || parts.length > 2) {
            throw new InvalidParameterException("Ordenação inválida.");
        }
        return Sort.by(direction(parts), property)
                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Sort.Direction direction(String[] parts) {
        if (parts.length == 1) {
            return Sort.Direction.ASC;
        }
        return switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidParameterException("Ordenação inválida.");
        };
    }
}
