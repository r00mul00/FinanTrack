CREATE TABLE refresh_tokens (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    family_id   UUID          NOT NULL,
    token_hash  CHAR(64)      NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ   NOT NULL,
    rotated_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
