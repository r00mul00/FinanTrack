CREATE TABLE credit_cards (
    id           UUID           PRIMARY KEY,
    user_id      UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    nickname     VARCHAR(60)    NOT NULL,
    brand        VARCHAR(20)    NOT NULL,
    last_four    CHAR(4)        NOT NULL,
    credit_limit NUMERIC(14, 2) NOT NULL,
    closing_day  SMALLINT       NOT NULL,
    due_day      SMALLINT       NOT NULL,
    archived     BOOLEAN        NOT NULL DEFAULT FALSE,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_cards_brand CHECK (brand IN ('VISA', 'MASTERCARD', 'ELO', 'AMEX', 'HIPERCARD', 'OTHER')),
    CONSTRAINT chk_cards_last_four CHECK (last_four ~ '^[0-9]{4}$'),
    CONSTRAINT chk_cards_limit CHECK (credit_limit > 0),
    CONSTRAINT chk_cards_closing_day CHECK (closing_day BETWEEN 1 AND 31),
    CONSTRAINT chk_cards_due_day CHECK (due_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_cards_user ON credit_cards (user_id);

CREATE TABLE credit_card_invoices (
    id              UUID           PRIMARY KEY,
    card_id         UUID           NOT NULL REFERENCES credit_cards (id) ON DELETE RESTRICT,
    reference_month DATE           NOT NULL,
    closing_date    DATE           NOT NULL,
    due_date        DATE           NOT NULL,
    paid_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_invoices_month_first_day CHECK (EXTRACT(DAY FROM reference_month) = 1),
    CONSTRAINT chk_invoices_paid CHECK (paid_amount >= 0),
    CONSTRAINT uq_invoices_card_month UNIQUE (card_id, reference_month)
);

CREATE TABLE credit_card_purchases (
    id                 UUID           PRIMARY KEY,
    card_id            UUID           NOT NULL REFERENCES credit_cards (id) ON DELETE RESTRICT,
    category_id        UUID           NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    description        VARCHAR(180)   NOT NULL,
    total_amount       NUMERIC(14, 2) NOT NULL,
    installments_count SMALLINT       NOT NULL,
    purchase_date      DATE           NOT NULL,
    canceled_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_purchases_total CHECK (total_amount > 0),
    CONSTRAINT chk_purchases_installments CHECK (installments_count BETWEEN 1 AND 48)
);

CREATE INDEX idx_purchases_card_date ON credit_card_purchases (card_id, purchase_date DESC);
CREATE INDEX idx_purchases_category ON credit_card_purchases (category_id);

CREATE TABLE credit_card_installments (
    id             UUID           PRIMARY KEY,
    purchase_id    UUID           NOT NULL REFERENCES credit_card_purchases (id) ON DELETE RESTRICT,
    invoice_id     UUID           NOT NULL REFERENCES credit_card_invoices (id) ON DELETE RESTRICT,
    transaction_id UUID           NOT NULL UNIQUE REFERENCES transactions (id) ON DELETE RESTRICT,
    number         SMALLINT       NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_installments_number CHECK (number >= 1),
    CONSTRAINT chk_installments_amount CHECK (amount > 0),
    CONSTRAINT uq_installments_purchase_number UNIQUE (purchase_id, number)
);

CREATE INDEX idx_installments_invoice ON credit_card_installments (invoice_id);

ALTER TABLE transactions ADD COLUMN invoice_id UUID REFERENCES credit_card_invoices (id) ON DELETE RESTRICT;
CREATE INDEX idx_transactions_invoice ON transactions (invoice_id);
