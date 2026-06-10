-- fundamentals-service schema reference script
-- Run this ONCE manually against your PostgreSQL database before starting the service.
--
-- NOTE: This is a REFERENCE script only — Hibernate (ddl-auto=update) creates these
--       tables automatically on first startup. You only need to run this manually if
--       you want to pre-create the tables before the first deployment, or if you need
--       to add the indexes (Hibernate does not auto-create indexes).
--
-- Command (local):
--   psql -h localhost -U postgres -d watchlisttracker -f create_fundamentals_tables.sql
--
-- Command (AWS RDS):
--   psql -h <RDS_HOST> -U postgres -d watchlisttracker -f create_fundamentals_tables.sql
--
-- All statements use IF NOT EXISTS — safe to re-run without errors.
-- DO NOT run this if you want Hibernate to manage the schema automatically.

-- ─── quarterly_results ────────────────────────────────────────────────────────
-- Stores P&L data per company per quarter.
-- quarter format: Q1FY25, Q2FY25, Q3FY25, Q4FY25
-- Data accumulates — old quarters are never deleted.
-- Frontend API uses ORDER BY period_end_date DESC LIMIT 4.

CREATE TABLE IF NOT EXISTS quarterly_results (
    id                  BIGSERIAL       PRIMARY KEY,
    symbol              VARCHAR(20)     NOT NULL,
    quarter             VARCHAR(10)     NOT NULL,
    period_end_date     DATE,
    revenue             NUMERIC(20, 2),
    gross_profit        NUMERIC(20, 2),
    operating_profit    NUMERIC(20, 2),
    net_profit          NUMERIC(20, 2),
    eps                 NUMERIC(10, 4),
    revenue_growth_yoy  NUMERIC(8, 4),
    profit_growth_yoy   NUMERIC(8, 4),
    raw_data            TEXT,
    data_source         VARCHAR(20),
    fetched_at          TIMESTAMP,
    CONSTRAINT uq_quarterly_symbol_quarter UNIQUE (symbol, quarter)
);

CREATE INDEX IF NOT EXISTS idx_qr_symbol          ON quarterly_results (symbol);
CREATE INDEX IF NOT EXISTS idx_qr_period_end_date ON quarterly_results (period_end_date DESC);

-- ─── balance_sheets ───────────────────────────────────────────────────────────
-- Stores annual balance sheet per company per fiscal year.
-- fiscal_year format: FY2024, FY2025  (Indian fiscal: April–March)
-- Data accumulates — old years are never deleted.
-- Frontend API uses ORDER BY period_end_date DESC LIMIT 3.

CREATE TABLE IF NOT EXISTS balance_sheets (
    id                  BIGSERIAL       PRIMARY KEY,
    symbol              VARCHAR(20)     NOT NULL,
    fiscal_year         VARCHAR(10)     NOT NULL,
    period_end_date     DATE,
    total_assets        NUMERIC(20, 2),
    current_assets      NUMERIC(20, 2),
    cash_and_equivalents NUMERIC(20, 2),
    total_investments   NUMERIC(20, 2),
    fixed_assets        NUMERIC(20, 2),
    total_liabilities   NUMERIC(20, 2),
    current_liabilities NUMERIC(20, 2),
    total_debt          NUMERIC(20, 2),
    long_term_debt      NUMERIC(20, 2),
    shareholders_equity NUMERIC(20, 2),
    retained_earnings   NUMERIC(20, 2),
    share_capital       NUMERIC(20, 2),
    raw_data            TEXT,
    data_source         VARCHAR(20),
    fetched_at          TIMESTAMP,
    CONSTRAINT uq_balance_symbol_fiscal_year UNIQUE (symbol, fiscal_year)
);

CREATE INDEX IF NOT EXISTS idx_bs_symbol          ON balance_sheets (symbol);
CREATE INDEX IF NOT EXISTS idx_bs_period_end_date ON balance_sheets (period_end_date DESC);

-- ─── fundamentals_fetch_log ───────────────────────────────────────────────────
-- Audit log — one row per company per fetch attempt.
-- status: SUCCESS, FAILED, SKIPPED
-- fetch_type: QUARTERLY_RESULTS, BALANCE_SHEET

CREATE TABLE IF NOT EXISTS fundamentals_fetch_log (
    id             BIGSERIAL    PRIMARY KEY,
    symbol         VARCHAR(20)  NOT NULL,
    fetch_type     VARCHAR(30)  NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    error_message  TEXT,
    data_source    VARCHAR(20),
    fetched_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fetch_log_symbol     ON fundamentals_fetch_log (symbol);
CREATE INDEX IF NOT EXISTS idx_fetch_log_status     ON fundamentals_fetch_log (status, fetched_at DESC);
CREATE INDEX IF NOT EXISTS idx_fetch_log_fetch_type ON fundamentals_fetch_log (fetch_type, fetched_at DESC);
