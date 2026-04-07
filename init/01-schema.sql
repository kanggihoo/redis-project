CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL PRIMARY KEY,
    owner_name  VARCHAR(100) NOT NULL,
    balance     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS store_transactions (
    store_id   BIGINT    PRIMARY KEY,
    tx_count   BIGINT    NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO accounts (owner_name, balance) VALUES
    ('Alice',   1000000),
    ('Bob',     2000000),
    ('Charlie',  500000),
    ('Diana',   3000000),
    ('Eve',      750000),
    ('Frank',  1500000),
    ('Grace',   800000),
    ('Heidi',  2500000),
    ('Ivan',    600000),
    ('Judy',   1200000);
