CREATE TABLE accounts (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name            VARCHAR(80)    NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    initial_balance NUMERIC(14, 2) NOT NULL,
    archived        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_accounts_type CHECK (type IN ('CHECKING', 'SAVINGS', 'WALLET', 'INVESTMENT', 'OTHER')),
    CONSTRAINT chk_accounts_initial_balance CHECK (initial_balance >= 0)
);

CREATE UNIQUE INDEX uq_accounts_user_name ON accounts (user_id, lower(name));
