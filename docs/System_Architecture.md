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
|  | Sentiment Module        |   |
|  +-------------------------+   |
|  | Trend Module            |   |
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
- Viewing company intelligence
- Subscription upgrade flows

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
(Details as in HLD; modules are same. Keep Subscription embedded in User Module for Phase 1.)

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
This System Architecture document now adds operational guidance, caching and queue strategies, security hardening, observability/SLO recommendations, and clear upgrade paths to microservices. It complements the HLD by providing the engineering-facing details required for implementation and operations.