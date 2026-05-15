-- NSE News Scheduler — reference schema
-- Hibernate (ddl-auto=update) creates this table automatically on first startup.
-- Run this manually ONLY if you need to recreate the table from scratch.

CREATE TABLE IF NOT EXISTS company_news (
    id           BIGSERIAL PRIMARY KEY,
    keyword      VARCHAR(255) UNIQUE NOT NULL,
    sentiments   VARCHAR(255),
    news         JSONB,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookup by keyword (company symbol)
-- Hibernate does not create indexes automatically — run this once manually if needed.
CREATE INDEX IF NOT EXISTS idx_news_keyword ON company_news(keyword);
