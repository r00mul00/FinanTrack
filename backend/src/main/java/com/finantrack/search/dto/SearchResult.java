package com.finantrack.search.dto;

import java.util.UUID;

/** O detalhe é opcional: uma recorrência encerrada, por exemplo, não tem próxima data. */
public record SearchResult(SearchResultType type, UUID id, String title, String detail) {
}
