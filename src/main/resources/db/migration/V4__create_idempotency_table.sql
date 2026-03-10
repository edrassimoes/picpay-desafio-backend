CREATE TABLE idempotency_keys (
    key        VARCHAR(255) PRIMARY KEY,
    response   TEXT,
    created_at TIMESTAMP NOT NULL
);