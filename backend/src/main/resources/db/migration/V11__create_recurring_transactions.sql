CREATE TABLE recurring_transactions (
    id                   UUID           PRIMARY KEY,
    user_id              UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    description          VARCHAR(200)   NOT NULL,
    amount               NUMERIC(14, 2) NOT NULL,
    type                 VARCHAR(10)    NOT NULL,
    category_id          UUID           NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    account_id           UUID           NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    frequency            VARCHAR(12)    NOT NULL,
    start_date           DATE           NOT NULL,
    end_date             DATE,
    next_occurrence_date DATE,
    status               VARCHAR(10)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_recurring_amount CHECK (amount > 0),
    CONSTRAINT chk_recurring_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_recurring_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT chk_recurring_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FINISHED')),
    CONSTRAINT chk_recurring_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_user ON recurring_transactions (user_id);
CREATE INDEX idx_recurring_category ON recurring_transactions (category_id);
CREATE INDEX idx_recurring_account ON recurring_transactions (account_id);
CREATE INDEX idx_recurring_due ON recurring_transactions (next_occurrence_date) WHERE status = 'ACTIVE';

-- Excluir a recorrência mantém os lançamentos já gerados, sem o vínculo.
ALTER TABLE transactions
    ADD COLUMN recurring_id UUID REFERENCES recurring_transactions (id) ON DELETE SET NULL,
    ADD COLUMN occurrence_date DATE;

-- Garantia final, no próprio banco, de um único lançamento por ciclo.
CREATE UNIQUE INDEX uq_transactions_recurring_occurrence
    ON transactions (recurring_id, occurrence_date) WHERE recurring_id IS NOT NULL;
