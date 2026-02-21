# Equity Watchlist Tracker – System Architecture Design

## 1. Architecture Overview
Equity Watchlist Tracker follows a modular monolith architecture in Phase 1, designed to
scale smoothly into microservices as user traffic and feature complexity grow.

The architecture prioritizes:
- Clear service boundaries
- Read-heavy performance (watchlists)
- Security and subscription-based access
- Future extensibility

Sections below distinguish Must-have (Phase 1) items vs Recommended (future) items.

---

## 2. Architectural Style
Must-have (Phase 1): Modular Monolith  
Recommended (Future): Microservices-ready

Why modular monolith?
- Faster development for MVP
- Easier debugging and deployment
- Clear logical separation without early network overhead

Each module is independently testable and can later be extracted as a microservice.

---

## 3. High-Level System Architecture

+---------------------+
| Web / Mobile App    |
+----------+----------+
|
v
+---------------------+
| API Gateway         |
| (Routing, Security) |
+----------+----------+
|
v
+-------------------------------+
| Backend Application           |
| (Modular Monolith)           |
|  +-------------------------+   |
|  | Authentication Module   |   |
|  +-------------------------+   |
|  | User & Subscription     |   |
|  | Module                  |   |
|  +-------------------------+   |
|  | Watchlist Module        |   |
|  +-------------------------+   |
|  | Company News Module     |   |
|  +-------------------------+   |
|  | Company Events Module   |   |
|  +-------------------------+   |
|  | Company Coming Events   |   |
|  | Module                  |   |
|  +-------------------------+   |
|  | Company Sentiment       |   |
|  | Module                  |   |
|  +-------------------------+   |
|  | Company Trend Module    |   |
|  +-------------------------+   |
+-------------------------------+
|
v
+--------------------------------+
| PostgreSQL Database             |
+--------------------------------+

---

## 4. Client Layer
Responsibilities
- User login & OTP verification
- Watchlist management
- Viewing company intelligence (news, trends, sentiment, coming events)
- Subscription upgrade flows
- Event notifications and calendar views

Supported Clients
- Web (Must-have)
- Mobile (Recommended for later phases)

---

## 5. API Gateway Layer (Options & Responsibilities)
Must-have options for Phase 1:
- Lightweight external gateway (e.g., API Gateway, Nginx) OR
- Middleware within the backend app (e.g., routing filters)

Responsibilities (Must-have):
- Request routing and basic auth validation (JWT)
- Centralized security policies
- Enforce rate limits for auth endpoints
- TLS termination (if external)

Recommended (future):
- Dedicated gateway with WAF, advanced rate limiting, and observability integrations

---

## 6. Caching Placement & Strategy
- Redis placement: dedicated cache cluster or managed Redis instance (Recommended).
- Use Redis for: hot watchlists, aggregated dashboard queries, and as a lightweight job queue (phase-dependent).
- Distinguish cache vs queue usage; prefer a separate queue backend for production-grade jobs.
- Cache TTLs: 5–30s for watchlist view; 30–300s for news feed; use short TTLs for freshness-sensitive data.

## 6.1 Authentication Module
- OTP-based login (Email / Phone)
- JWT & Refresh Token issuance
- Stateless design
- OTP-based auth flows, JWT issuance, refresh tokens and revocation logic.

Suggestions / Non-destructive additions:
- Rate limiting & anti-abuse: per-IP and per-user OTP attempt limits, exponential backoff, temporary account blocks.
- MFA/2FA: support optional second factor (TOTP) for paid users or admins.
- Device management: maintain list of trusted devices / sessions and allow revocation.
- Audit logging for login attempts, token issuance and revocations.
- Internal auth service API: well-documented endpoints for token introspection and session management (useful if later extracted to microservice).

### 6.2 User & Subscription Module
- User profiles
- Roles: Admin, Free, Paid
- Subscription plans:
  - Monthly
  - 3 Months
  - 6 Months
  - 1 Year
- Subscription expiry & access control

Suggestions / Non-destructive additions:
- Entitlement checks middleware: central helper to validate feature access based on active subscription and role.
- Billing/webhook handling: idempotent processors for payment provider webhooks (Stripe/PayPal/etc.).
- Grace period policies and trial handling for new users.
- Subscription change audit trail (who changed plan, timestamps, invoice ids).
- Admin console APIs for manual adjustments and debugging user entitlements.

---

## 7. Background Workers & Queue
Must-have:
- Lightweight worker pool for news ingestion and periodic sentiment/trend recomputation.
- Use Redis-backed queues or a managed queue service depending on scale.
- Workers must be idempotent and have retry/backoff strategies.

Recommended:
- Separate queue for high-priority tasks; monitor queue length and worker latency.

---

## 8. Backend Application (Core Modules)
Core modules for Phase 1 (Modular Monolith):

- Authentication Module
  - OTP-based auth flows, JWT issuance, refresh tokens and revocation logic.
  - Token lifetime policies: short-lived access tokens, refresh token rotation on use.
  - Token revocation store (redis) for immediate revocations where required.
  - Endpoints: /auth/otp/request, /auth/otp/verify, /auth/token/refresh, /auth/logout

- User & Subscription Module
  - User profiles, subscription plans, billing hooks, entitlement checks.
  - Roles & permissions mapping for feature gating.
  - Endpoints: /users, /users/{id}/subscriptions, /users/{id}/entitlements

- Watchlist Module
  - CRUD for watchlists, membership management, caching for fast reads.
  - Watchlist items store minimal company identifiers (ticker, exchange) and metadata for display.
  - Support bulk operations (add/remove many tickers) and export/import (CSV).
  - Read-optimized endpoints and queries; use Redis for hot entries and paginated results.
  - Webhook/event hooks when watchlists change (for cross-device sync & notifications).

- Company News Module
  - Ingest news, store metadata, expose search/filter APIs (future full-text via search index).
  - News deduplication: fingerprinting articles by canonical URL + hash of content.
  - News metadata: provider, published_at, relevance_score, tickers mentioned, sentiment snapshot.
  - Endpoints: /companies/{ticker}/news, /news/search
  - Recommended: full-text search via Elastic/OpenSearch for advanced filtering.

- Company Events Module
  - Canonical store of corporate events (earnings, dividends, filings); historical and published event records.
  - Normalize provider feeds into canonical event schema (type, scheduled_at, status, source).
  - Endpoints: /companies/{ticker}/events, /events/calendar

- Company Coming Events Module
  - Focused on upcoming/near-term events and calendar integration:
    - Aggregates and deduplicates upcoming event notices from news, filings, and provider feeds.
    - Provides APIs for upcoming-event lists, calendar export (iCal), user reminders, and notification hooks.
    - Supports subscription-based event alerts and entitlement checks.
    - Worker-driven refresh to ensure freshness and push notifications for imminent events.
  - Additional points:
    - Event prioritization rules (earnings > dividends > filings) for notification batching.
    - Allow user-level snooze and custom reminder offsets (e.g., 1h, 24h).

- Company Sentiment Module
  - Periodic sentiment scoring from news/social sources, time-series storage for trends.
  - Data model: {ticker, source, timestamp, sentiment_score, confidence}
  - Batch recomputation jobs and online incremental updates from new articles.
  - Endpoints: /companies/{ticker}/sentiment, /sentiment/history
  - Suggestions: store both raw signals and aggregated, explainable metrics (positive/negative counts).

- Company Trend Module
  - Computation and storage of trend signals (price momentum, volume, derived indicators).
  - Inputs: price bars, trade volumes, sentiment signals, news bursts.
  - Provide both raw indicators (SMA, EMA, RSI) and combined trend signals used by UI.
  - Endpoints: /companies/{ticker}/trends, /trends/summary

Keep Subscription embedded in User Module for Phase 1; plan to extract as separate service when billing/scale reasons arise.

---

## 9. Data Layer & Scaling
Primary DB: PostgreSQL (Must-have)

Scaling recommendations:
- Read replicas for read-heavy workloads (Recommended)
- Logical partitioning (sharding) or table partitioning for very large tables (Recommended)
- Use search index (Elasticsearch/OpenSearch) for full-text news search (Future)

Backup & Restore (Must-have):
- Automated nightly backups, point-in-time recovery where possible, and tested restore runbooks.

---

## 10. Security Architecture
Must-have:
- OTP-based authentication with rate limits and anti-abuse rules.
- JWT-based authorization with short-lived access tokens and refresh tokens.
- Token revocation strategy (maintain revocation list or rotate refresh tokens on use).
- Secrets management and least-privilege DB credentials.

Recommended:
- Webhook signing for downstream integrations.
- WAF and DDOS protection when exposing public APIs.

---

## 11. Observability, SLOs & SLIs
Observability (Must-have):
- Structured logs with correlation IDs.
- Metrics collection: request rates, latencies (p50/p90/p99), error rates, job queue lengths.
- Tracing (sampled) for requests that trigger background jobs.
- Error reporting (Sentry or similar).

Example SLOs (Recommended for Phase 1):
- API availability: 99.9% (monthly)
- 95th percentile API latency: < 500ms
- Job processing success rate: 99%

Define SLIs that map to these SLOs: success rate, p95 latency, queue backlog.

---

## 12. Deployment Options & CI/CD
MVP options (Must-have):
- Build Docker images via GitHub Actions.
- Deploy to a single VM or managed container service (ECS/Fargate/GKE) for MVP.
- Staging auto-deploy; manual production promotion.

Migration to k8s (Recommended):
- Prepare k8s manifests and production-grade observability before migration.
- Use helm charts or kustomize for environment-specific configs.

---

## 13. Microservice Extraction Triggers
When to extract a module to its own service (Recommended triggers):
- CPU/Memory resource growth > 2x baseline for a single module
- Distinct scaling needs (e.g., watchlists require much larger read capacity than auth)
- Team ownership boundaries and release cadences diverge
- Increased operational complexity (deploy frequency, incident rate)

---

## 14. API Versioning & Contract
- Adopt OpenAPI and prefix endpoints with /v1/ for the initial contract.
- Maintain backward compatibility; use semver for API changes.
- Add automated contract tests in CI to prevent breaking changes.

---

## 15. Summary
This System Architecture document now adds operational guidance, caching and queue strategies, security hardening, observability/SLO recommendations, and clear upgrade paths to microservices. It complements the module-level responsibilities listed above for the Authentication, Watchlist, User & Subscription, Company News, Company Events, Company Coming Events, Company Sentiment, and Company Trend modules.