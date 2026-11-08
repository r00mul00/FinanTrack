-- invoice_id, recurring_id e occurrence_date entram na Fase 6, com as tabelas a que apontam.
CREATE TABLE transactions (
    id               UUID           PRIMARY KEY,
    user_id          UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    description      VARCHAR(200)   NOT NULL,
    amount           NUMERIC(14, 2) NOT NULL,
    type             VARCHAR(10)    NOT NULL,
    kind             VARCHAR(20)    NOT NULL DEFAULT 'STANDARD',
    status           VARCHAR(10)    NOT NULL,
    category_id      UUID           REFERENCES categories (id) ON DELETE RESTRICT,
    account_id       UUID           REFERENCES accounts (id) ON DELETE RESTRICT,
    transaction_date DATE           NOT NULL,
    due_date         DATE,
    notes            VARCHAR(1000),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELED')),
    CONSTRAINT chk_transactions_kind CHECK (
        (kind = 'STANDARD' AND account_id IS NOT NULL AND category_id IS NOT NULL)
        OR (kind = 'CARD_INSTALLMENT' AND account_id IS NULL AND category_id IS NOT NULL AND type = 'EXPENSE')
        OR (kind = 'INVOICE_PAYMENT' AND account_id IS NOT NULL AND category_id IS NULL AND type = 'EXPENSE')
    )
);

CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date DESC);
CREATE INDEX idx_transactions_user_category_date ON transactions (user_id, category_id, transaction_date);
CREATE INDEX idx_transactions_user_status_due ON transactions (user_id, status, due_date);
CREATE INDEX idx_transactions_account_status ON transactions (account_id, status);
