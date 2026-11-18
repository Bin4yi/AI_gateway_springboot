-- One row per tenant. Postgres is the truth; Redis is the fast gate.
CREATE TABLE budgets (
    tenant_id   VARCHAR(64) PRIMARY KEY,
    total_limit BIGINT      NOT NULL CHECK (total_limit >= 0),
    used        BIGINT      NOT NULL DEFAULT 0 CHECK (used >= 0),
    reserved    BIGINT      NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version     BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking (@Version)
    -- Last line of defence: the database itself refuses an overspend
    CONSTRAINT budgets_never_overspent CHECK (used + reserved <= total_limit)
);

-- The ledger of every reservation. Rebuilds Redis after a crash.
CREATE TABLE reservations (
    id               UUID         PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL REFERENCES budgets (tenant_id),
    action_id        VARCHAR(64)  NOT NULL,
    estimated_amount BIGINT       NOT NULL CHECK (estimated_amount > 0),
    actual_cost      BIGINT,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('HELD', 'COMMITTED', 'REFUNDED', 'EXPIRED')),
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Cleanup job: find HELD reservations that are past expires_at
CREATE INDEX idx_reservations_status_expires ON reservations (status, expires_at);
-- Rebuild Redis: sum HELD / COMMITTED per tenant
CREATE INDEX idx_reservations_tenant_status ON reservations (tenant_id, status);
-- One reservation per action, so a retried action cannot reserve twice
CREATE UNIQUE INDEX uq_reservations_action ON reservations (action_id);

-- Kafka readers skip an event they already handled
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Saved answer for a repeated Idempotency-Key
CREATE TABLE idempotency_keys (
    key           VARCHAR(128) PRIMARY KEY,
    response_json JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
