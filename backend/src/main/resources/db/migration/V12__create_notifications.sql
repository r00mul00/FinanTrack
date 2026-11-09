CREATE TABLE notifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    dedupe_key VARCHAR(120) NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_notifications_type CHECK (type IN ('BUDGET_ALERT', 'BUDGET_EXCEEDED', 'DUE_DATE', 'INVOICE_DUE')),
    -- Garantia final, no próprio banco, de um único aviso por evento.
    CONSTRAINT uq_notifications_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user_read ON notifications (user_id, read_at, created_at DESC);
