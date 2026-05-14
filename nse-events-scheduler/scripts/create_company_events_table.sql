-- NSE Events Scheduler — reference schema
-- Hibernate (ddl-auto=update) creates this table automatically on first startup.
-- Run this manually only if you need to recreate the table from scratch.

CREATE TABLE IF NOT EXISTS company_events (
    id           BIGSERIAL PRIMARY KEY,
    keyword      VARCHAR(255) UNIQUE NOT NULL,
    events       JSONB,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookup by symbol (keyword)
-- Hibernate does not create indexes automatically — run this once manually if needed.
CREATE INDEX IF NOT EXISTS idx_events_keyword ON company_events(keyword);
