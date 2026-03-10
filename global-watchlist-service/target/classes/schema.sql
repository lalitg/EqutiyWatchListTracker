CREATE TABLE IF NOT EXISTS global_watchlist (
    company_id  BIGINT PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL,
    added_at    TIMESTAMP DEFAULT NOW()
);
