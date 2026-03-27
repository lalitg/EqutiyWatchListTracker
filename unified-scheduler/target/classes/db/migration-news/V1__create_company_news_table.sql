-- V1: Create company_news table
-- news column stores JSONB array of { date, summary, link } only
-- seqId is NOT stored here — maintained in-memory via sliding window

DROP TABLE IF EXISTS company_news;

CREATE TABLE company_news (
    id           BIGSERIAL PRIMARY KEY,
    keyword      VARCHAR(255) UNIQUE NOT NULL,
    sentiments   VARCHAR(255),
    news         JSONB,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_news_keyword ON company_news(keyword);
