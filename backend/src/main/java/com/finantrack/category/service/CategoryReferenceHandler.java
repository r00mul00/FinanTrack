package com.finantrack.category.service;

import java.util.UUID;

/**
 * Implementado por cada módulo que aponta para categorias (transações,
 * orçamentos, recorrências...). Assim a exclusão não precisa conhecê-los.
 */
public interface CategoryReferenceHandler {

    long countReferences(UUID categoryId);

    /** Move os vínculos, dentro da transação da exclusão. */
    void reassign(UUID fromCategoryId, UUID toCategoryId);
}
