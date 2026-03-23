-- V2: Add performance indexes for production query patterns

-- Index on last_updated for time-range and recency queries.
-- WHY: Dashboard/API queries sort by last_updated DESC (e.g., "show recently updated symbols").
-- Without this index every such query does a full table scan.
-- DESC matches the most common sort direction — Postgres can use it in both directions.
CREATE INDEX IF NOT EXISTS idx_news_last_updated ON company_news(last_updated DESC);
