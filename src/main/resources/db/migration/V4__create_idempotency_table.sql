CREATE TABLE idempotency_keys (
    key        VARCHAR(255) PRIMARY KEY,
    status     VARCHAR(50)  NOT NULL,
    response   TEXT,
    created_at TIMESTAMP    NOT NULL
);