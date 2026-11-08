CREATE TABLE user_tokens (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    type        VARCHAR(30)   NOT NULL,
    token_hash  CHAR(64)      NOT NULL UNIQUE,
    new_email   VARCHAR(254),
    expires_at  TIMESTAMPTZ   NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_user_tokens_type CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'))
);

CREATE INDEX idx_user_tokens_user_type ON user_tokens (user_id, type);
