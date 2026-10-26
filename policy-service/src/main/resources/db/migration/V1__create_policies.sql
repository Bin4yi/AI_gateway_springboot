CREATE TABLE policies (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  VARCHAR(64)  NOT NULL,
    agent_id   VARCHAR(64)  NOT NULL,
    tool       VARCHAR(128) NOT NULL,
    effect     VARCHAR(8)   NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    conditions JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- /policies/check looks up rules by tenant + agent + tool
CREATE INDEX idx_policies_lookup ON policies (tenant_id, agent_id, tool);
