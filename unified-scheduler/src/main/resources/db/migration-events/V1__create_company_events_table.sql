-- V1: Create company_events table for NSE Events Scheduler
-- Stores both upcoming and past events fetched from NSE event calendar API
-- Flyway runs this once on first startup and never again

DROP TABLE IF EXISTS company_events;

CREATE TABLE company_events (
    id           BIGSERIAL PRIMARY KEY,
    keyword      VARCHAR(255) UNIQUE NOT NULL,
    events       JSONB,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_keyword ON company_events(keyword);
