CREATE TABLE users (
    id                    UUID          PRIMARY KEY,
    name                  VARCHAR(120)  NOT NULL,
    email                 VARCHAR(254)  NOT NULL,
    password_hash         VARCHAR(100)  NOT NULL,
    email_verified_at     TIMESTAMPTZ,
    currency              CHAR(3)       NOT NULL DEFAULT 'BRL',
    theme                 VARCHAR(10)   NOT NULL DEFAULT 'DARK',
    notify_budget         BOOLEAN       NOT NULL DEFAULT TRUE,
    notify_due_dates      BOOLEAN       NOT NULL DEFAULT TRUE,
    deletion_requested_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_users_theme CHECK (theme IN ('DARK', 'LIGHT'))
);

CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
