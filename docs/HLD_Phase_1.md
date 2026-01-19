# Equity Watchlist Tracker – High Level Design (Phase 1)

## 1. Introduction
Equity Watchlist Tracker is an investing platform that allows users to maintain a personalized
equity watchlist enriched with company news, events, trend outlook, and sentiment analysis.

Phase 1 focuses on delivering a clean, scalable backend foundation with core user engagement
and intelligence features.

---

## 2. Goals of Phase 1
- Secure authentication and user management
- Personalized watchlist per user
- Centralized company intelligence
- Subscription-based feature access
- Scalable, modular architecture

---

## 3. MVP Scope (Must-have vs Recommended)
Must-have (Phase 1):
- OTP-based authentication and JWT issuance (email/phone)
- Watchlist CRUD (add/remove/list)
- View company details with latest news and basic sentiment
- Admin manual news ingestion API
- Basic subscription gating (Free vs Paid limits)
- Minimal observability (structured logs, basic metrics)

Recommended (not required for initial MVP):
- Payment gateway and automatic billing
- ML-driven sentiment and trend computation
- Push notifications and real-time updates
- Full mobile client

---

## 4. Non-goals (Phase 1)
- No payment gateway or billing automation
- No production ML sentiment models (manual/rule-based only)
- No push notification service
- No multi-region high-availability setup

---

## 5. High-Level Architecture
The system follows a modular monolith approach in Phase 1 and can evolve into
microservices as the platform scales. Modules are independently testable and marked
Must-have vs Recommended where appropriate.

---

## 6. Core Services (Phase 1)
### 6.1 Authentication Service (Must-have)
Responsibilities
- OTP-based login (Email / Phone)
- JWT & Refresh Token generation
- Token validation for downstream services

Characteristics
- Stateless
- Horizontally scalable

Security defaults (recommendations)
- OTP TTL: 5 minutes (configurable 5–10m)
- OTP rate limit: 5 requests/hour per identifier
- Access token TTL: 15 minutes
- Refresh token TTL: 7–30 days with rotation

---

### 6.2 User Service (Must-have)
Responsibilities
- User profile management
- Role management (Admin, Free, Paid)
- Subscription association (embedded in Phase 1)
- Account lifecycle management

Design note: Subscription is embedded within the User Service for Phase 1 to keep the MVP simple. Marked as Must-have. Extract to a dedicated Subscription Service (Recommended) when payment workflows, invoices, and renewals are introduced.

---

### 6.3 Watchlist Service (Must-have)
Responsibilities
- Manage user-to-company watchlist mapping
- Provide fast read access for dashboards
- Enforce subscription-based limits

Design notes
- Read-heavy, low-latency service — plan early for caching and denormalized reads
- Redis caching recommended for hot watchlists (TTL 5–30s)

---

### 6.4 Company News Service (Must-have)
Responsibilities
- Store company-specific news
- Normalize multiple news sources
- Provide paginated APIs

---

### 6.5 Company Event Service (Recommended)
Responsibilities
- Track earnings, dividends, AGMs, etc.
- Maintain event timelines per company
- Enable notifications in future phases

Phase 1 can offer minimal event support (Recommended) and expand later.

---

### 6.6 Sentiment Service (Must-have - basic)
Responsibilities
- Maintain sentiment indicators: Positive / Neutral / Negative

Phase 1 approach
- Rule-based or manually curated sentiment labels (Must-have basic)

Future
- AI/ML-driven sentiment analysis (Recommended)

---

### 6.7 Trend Service (Recommended)
Responsibilities
- Store short-term, mid-term, and long-term trends
- Provide directional outlook (Bullish / Neutral / Bearish)

Phase 1 approach
- Offline or manually computed trends (Recommended)

---

## 7. MVP API Surface (Must-have)
Provide a minimal set of endpoints so frontend and backend work can parallelize:
- POST /v1/auth/otp/request
- POST /v1/auth/otp/verify
- POST /v1/auth/token/refresh
- GET /v1/users/me
- GET /v1/users/me/watchlist
- POST /v1/users/me/watchlist
- DELETE /v1/users/me/watchlist/{company_id}
- GET /v1/companies/{id}
- GET /v1/companies/{id}/news
- POST /v1/admin/news  (admin-only manual ingestion)

Recommendation: Use OpenAPI / Swagger for contract-first development.

---

## 8. Minimal Data Model / Schema (Logical)
Suggested tables and key columns (logical view):
- users (id, email, phone, role, created_at, last_seen)
- subscriptions (id, user_id, plan, status, start_at, end_at) -- embedded for Phase 1
- companies (id, ticker, name, sector, market_cap)
- watchlists (id, user_id, company_id, added_at)
- news (id, company_id, title, source, url, published_at, content, ingested_at)
- events (id, company_id, type, date, details)
- sentiment (id, company_id, source, sentiment_label, score, evaluated_at)
- trends (id, company_id, horizon, direction, computed_at)

Indexing recommendations (read-heavy):
- watchlists(user_id, company_id) unique index
- news(company_id, published_at DESC)
- sentiment(company_id, evaluated_at DESC)

---

## 9. Caching & Read Optimization (Must-have - basic)
- Redis for hot watchlists and aggregated dashboard queries.
- Cache TTLs: 5–30 seconds for watchlist views, 30–300s for news feeds depending on freshness needs.
- Invalidation: invalidate on watchlist update and on new company/news ingestion for affected keys.

---

## 10. Background Jobs & Ingestion (Must-have)
- Use a lightweight job queue (e.g., Redis queues, RQ, Sidekiq, Celery) for:
  - News ingestion
  - Periodic sentiment/trend recomputation
  - Email/notification tasks (future)
- Workers should be horizontally scalable and idempotent.

---

## 11. Security & Anti-abuse (Must-have)
- Rate limits per endpoint (especially auth) — default OTP limit: 5/hr per identifier; login attempts: 10/hr per IP.
- Token revocation list or rotation support for refresh tokens.
- Secrets management: store keys in vault or environment with rotation policy.

---

## 12. Observability & Ops (Must-have)
- Structured logs (JSON), correlation IDs, and basic distributed tracing.
- Metrics: request count, latency (p50/p90/p99), error rates, job queue lengths.
- Backups: nightly DB backups and restore runbook.
- Error tracking: Sentry or equivalent.

---

## 13. CI/CD & Deployment (Must-have)
- GitHub Actions for linting, unit tests, and image build.
- Build Docker images and push to registry.
- Staging auto-deploy; manual promotion to production for MVP.
- Simple deployment targets: single VM or managed container service (ECS/Fargate/GKE) depending on team familiarity.

---

## 14. Testing (Must-have)
- Unit tests for modules.
- Integration tests against a CI/test DB.
- Contract tests for APIs (OpenAPI + contract tests between frontend/backends).

---

## 15. Phase 1 Backlog & Sprint Plan (Suggested 4–5 sprints)
Sprint 1: Auth (OTP, JWT), User CRUD, basic CI
Sprint 2: Watchlist CRUD, DB models, API contracts
Sprint 3: Company news ingestion (manual admin API), basic sentiment
Sprint 4: Observability, caching, background jobs
Sprint 5: Hardening, testing, staging and production deploy

---

## 16. Metrics to Track (KPIs)
- Signups/day
- Daily Active Users (DAU)
- Watchlists created
- API error rate (5xx)
- Avg API latency (p95)

---

## 17. Summary
This HLD now separates Must-have vs Recommended items, provides a minimal API and data model for the frontend to work against, and adds operational guidance for a shippable Phase 1 MVP. Mark sections that are Recommended vs Must-have to help prioritize.