CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name       VARCHAR(60)  NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    icon       VARCHAR(60)  NOT NULL,
    color      CHAR(7)      NOT NULL,
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_categories_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_categories_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX uq_categories_user_type_name ON categories (user_id, type, lower(name));

-- Usuários criados antes desta versão recebem as mesmas categorias padrão de
-- CategoryService.DEFAULTS, que atende os novos cadastros.
INSERT INTO categories (id, user_id, name, type, icon, color, created_at, updated_at)
SELECT gen_random_uuid(), u.id, d.name, d.type, d.icon, d.color, now(), now()
FROM users u
CROSS JOIN (VALUES
    ('Alimentação',   'EXPENSE', 'Restaurant',    '#FF8A65'),
    ('Moradia',       'EXPENSE', 'Home',          '#7C4DFF'),
    ('Transporte',    'EXPENSE', 'DirectionsCar', '#4FC3F7'),
    ('Saúde',         'EXPENSE', 'LocalHospital', '#FF647C'),
    ('Educação',      'EXPENSE', 'School',        '#FFB547'),
    ('Lazer',         'EXPENSE', 'SportsEsports', '#B14DDB'),
    ('Compras',       'EXPENSE', 'ShoppingBag',   '#F06292'),
    ('Contas',        'EXPENSE', 'Receipt',       '#90A4AE'),
    ('Outros',        'EXPENSE', 'MoreHoriz',     '#A6A8BC'),
    ('Salário',       'INCOME',  'Payments',      '#20C997'),
    ('Freelance',     'INCOME',  'Work',          '#4DB6AC'),
    ('Investimentos', 'INCOME',  'TrendingUp',    '#81C784'),
    ('Outros',        'INCOME',  'AttachMoney',   '#A6A8BC')
) AS d (name, type, icon, color);
