-- auth-service schema script
-- Run this once manually against your PostgreSQL database before starting the service.
--
-- Command (local):
--   psql -h localhost -U postgres -d watchlisttracker -f create_refresh_tokens_table.sql
--
-- Command (AWS RDS):
--   psql -h <RDS_HOST> -U postgres -d watchlisttracker -f create_refresh_tokens_table.sql
--
-- NOTE: The users table is owned by user-service.
--       auth-service only owns the refresh_tokens table.
--       user_id here references users.id logically but NOT as a DB foreign key
--       (no FK across microservices — services are independently deployable).

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,

    -- Which user this token belongs to (logical reference to users.id)
    user_id     BIGINT          NOT NULL,

    -- SHA-256 hash of the actual refresh token string.
    -- The raw token is only ever sent to the client once and never stored plain.
    token_hash  VARCHAR(64)     UNIQUE NOT NULL,

    -- Token expiry: 30 days from creation
    expires_at  TIMESTAMP       NOT NULL,

    -- Set to true on logout OR when rotated (a new token replaces this one)
    revoked     BOOLEAN         NOT NULL DEFAULT false,

    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index for fast lookup by token_hash on every /refresh and /logout call
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Index for fast lookup by user_id (e.g. revoking all tokens for a user)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- How to Run the Jar
-- java -DDB_PASSWORD=yourPassword -jar target/auth-service-0.0.1-SNAPSHOT.jar