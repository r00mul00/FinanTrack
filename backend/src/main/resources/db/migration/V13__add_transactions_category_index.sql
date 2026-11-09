-- Contagem de uso, reatribuição e a checagem da chave estrangeira ao excluir uma
-- categoria filtram só por category_id. O índice (user_id, category_id, ...) não
-- atende esse filtro: com 200 mil transações a consulta lia a tabela inteira.
CREATE INDEX idx_transactions_category ON transactions (category_id);
