CREATE TABLE budgets (
    id           UUID           PRIMARY KEY,
    user_id      UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    category_id  UUID           NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    month        DATE           NOT NULL,
    limit_amount NUMERIC(14, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_budgets_month_first_day CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT chk_budgets_limit CHECK (limit_amount > 0),
    CONSTRAINT uq_budgets_user_category_month UNIQUE (user_id, category_id, month)
);

CREATE INDEX idx_budgets_category ON budgets (category_id);
