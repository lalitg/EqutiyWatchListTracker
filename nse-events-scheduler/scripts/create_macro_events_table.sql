-- NSE Events Scheduler — macro_events reference schema
-- Hibernate (ddl-auto=update) creates this table AND indexes automatically on first startup.
-- Run this manually ONLY if you need to recreate the table from scratch.

CREATE TABLE IF NOT EXISTS macro_events (
    id           BIGSERIAL PRIMARY KEY,
    category     VARCHAR(100)  NOT NULL,   -- BUDGET, ELECTION, RBI_MPC, US_CPI, US_NFP, MUHURAT_TRADING, FOMC
    event_name   VARCHAR(500)  NOT NULL,
    event_date   DATE          NOT NULL,
    event_time   VARCHAR(50),              -- e.g. '10:00 AM IST', 'TBA'
    description  TEXT,
    country      VARCHAR(10),              -- IN or US
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_macro_events_category ON macro_events(category);
CREATE INDEX IF NOT EXISTS idx_macro_events_date     ON macro_events(event_date);

-- ── Useful queries ────────────────────────────────────────────────────────────

-- All upcoming events
-- SELECT * FROM macro_events WHERE event_date >= CURRENT_DATE ORDER BY event_date;

-- Upcoming RBI MPC dates
-- SELECT * FROM macro_events WHERE category = 'RBI_MPC' AND event_date >= CURRENT_DATE ORDER BY event_date;

-- All US events
-- SELECT * FROM macro_events WHERE country = 'US' ORDER BY event_date;

-- Update Muhurat Trading time when announced
-- UPDATE macro_events SET event_time = '6:15 PM IST', last_updated = NOW()
-- WHERE category = 'MUHURAT_TRADING' AND event_date = '2025-10-20';

-- Add a new election
-- INSERT INTO macro_events (category, event_name, event_date, country, last_updated)
-- VALUES ('ELECTION', 'Goa Assembly Election Results 2027', '2027-03-10', 'IN', NOW());

-- Re-seed from scratch (seeder inserts only when count = 0)
-- DELETE FROM macro_events;
-- Then restart the service — seeder will re-insert all hardcoded dates.
