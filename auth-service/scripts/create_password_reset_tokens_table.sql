-- password_reset_tokens table — reference DDL for auth-service
--
-- Hibernate (ddl-auto=update) creates this table automatically on first startup.
-- Run this manually only if you want to pre-create the table or add the indexes
-- (Hibernate does not auto-create indexes).
--
-- All statements use IF NOT EXISTS — safe to re-run.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    email       VARCHAR(150)    NOT NULL,
    token_hash  VARCHAR(64)     UNIQUE NOT NULL,   -- SHA-256 hex of the raw token
    expires_at  TIMESTAMP       NOT NULL,          -- 15 minutes from creation
    used        BOOLEAN         NOT NULL DEFAULT false,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prt_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_email      ON password_reset_tokens (email);
