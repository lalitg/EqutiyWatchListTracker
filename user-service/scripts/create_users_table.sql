-- user-service schema script
-- Run this once manually against your PostgreSQL database before starting the service.
--
-- Command:
--   psql -h <host> -U <username> -d <dbname> -f create_users_table.sql
--
-- Example (local):
--   psql -h localhost -U postgres -d watchlisttracker -f create_users_table.sql

CREATE TABLE IF NOT EXISTS users (
    id               BIGSERIAL    PRIMARY KEY,

    -- Login identity
    username         VARCHAR(50)  UNIQUE NOT NULL,
    name             VARCHAR(100) NOT NULL,
    email            VARCHAR(150) UNIQUE,
    phone_number     VARCHAR(15)  UNIQUE,

    -- BCrypt hash (always 60 chars for $2a$ format)
    password_hash    VARCHAR(60)  NOT NULL,

    -- Role: CLIENT (normal user) or ADMIN
    user_type        VARCHAR(10)  NOT NULL DEFAULT 'CLIENT',

    -- Lifecycle: ACTIVE, BLOCKED, or DELETED (soft-delete)
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',

    -- Investor profile — nullable until user fills them in
    investment_years   VARCHAR(20),
    investment_amount  VARCHAR(20),

    -- Auto-computed from investment_years + investment_amount score matrix
    -- BEGINNER | INTERMEDIATE | ADVANCED | PRO
    investor_category  VARCHAR(15) NOT NULL DEFAULT 'BEGINNER',

    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for fast admin queries by status and category
CREATE INDEX IF NOT EXISTS idx_users_status            ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_investor_category ON users (investor_category);


-- How to run the jar
-- java -DDB_PASSWORD=yourpassword -jar target/user-service-0.0.1-SNAPSHOT.jar