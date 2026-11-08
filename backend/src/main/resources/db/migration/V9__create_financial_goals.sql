CREATE TABLE financial_goals (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name           VARCHAR(100)   NOT NULL,
    description    VARCHAR(500),
    target_amount  NUMERIC(14, 2) NOT NULL,
    initial_amount NUMERIC(14, 2) NOT NULL,
    current_amount NUMERIC(14, 2) NOT NULL,
    deadline       DATE           NOT NULL,
    status         VARCHAR(10)    NOT NULL,
    completed_at   TIMESTAMPTZ,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_goals_target CHECK (target_amount > 0),
    CONSTRAINT chk_goals_initial CHECK (initial_amount >= 0),
    CONSTRAINT chk_goals_current CHECK (current_amount >= 0),
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_goals_user ON financial_goals (user_id, status, deadline);

CREATE TABLE goal_movements (
    id         UUID           PRIMARY KEY,
    goal_id    UUID           NOT NULL REFERENCES financial_goals (id) ON DELETE RESTRICT,
    type       VARCHAR(10)    NOT NULL,
    amount     NUMERIC(14, 2) NOT NULL,
    note       VARCHAR(200),
    created_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_goal_movements_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_goal_movements_amount CHECK (amount > 0)
);

CREATE INDEX idx_goal_movements_goal_created ON goal_movements (goal_id, created_at DESC);
