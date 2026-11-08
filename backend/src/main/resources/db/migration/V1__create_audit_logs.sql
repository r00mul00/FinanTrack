-- Trilha de auditoria. user_id não tem FK para sobreviver à exclusão da conta.
CREATE TABLE audit_logs (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   UUID         NOT NULL,
    changes     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs (user_id, created_at DESC);
