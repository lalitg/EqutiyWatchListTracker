# Equity Watchlist Tracker – System Architecture Design

## 1. Architecture Overview

Equity Watchlist Tracker follows a **modular monolith architecture** in Phase 1, designed to
scale smoothly into microservices as user traffic and feature complexity grow.

The architecture prioritizes:
- Clear service boundaries
- Read-heavy performance (watchlists)
- Security and subscription-based access
- Future extensibility

---

## 2. Architectural Style

**Phase 1:** Modular Monolith  
**Future:** Microservices-ready

Why modular monolith?
- Faster development for MVP
- Easier debugging and deployment
- Clear logical separation without network overhead

Each module is independently testable and can later be extracted as a microservice.

---

## 3. High-Level System Architecture

+---------------------+
| Web / Mobile App |
+----------+----------+
|
v
+---------------------+
| API Gateway |
| (Routing, Security) |
+----------+----------+
|
v
+-------------------------------+
| Backend Application |
| (Modular Monolith) |
| |
| +-------------------------+ |
| | Authentication Module | |
| +-------------------------+ |
| | User & Subscription | |
| | Module | |
| +-------------------------+ |
| | Watchlist Module | |
| +-------------------------+ |
| | Company News Module | |
| +-------------------------+ |
| | Company Events Module | |
| +-------------------------+ |
| | Sentiment Module | |
| +-------------------------+ |
| | Trend Module | |
| +-------------------------+ |
+---------------+---------------+
|
v
+--------------------------------+
| PostgreSQL Database |
+--------------------------------+


---

## 4. Client Layer

### Responsibilities
- User login & OTP verification
- Watchlist management
- Viewing company intelligence
- Subscription upgrade flows

### Supported Clients
- Web (Phase 1)
- Mobile (future)

---

## 5. API Gateway Layer

### Responsibilities
- Request routing
- JWT validation
- Rate limiting (future)
- Centralized security policies

**Phase 1 Note:**  
Can be implemented as a lightweight gateway or integrated into the backend using filters.

---

## 6. Backend Application (Core Modules)

### 6.1 Authentication Module
- OTP-based login (Email / Phone)
- JWT & Refresh Token issuance
- Stateless design

---

### 6.2 User & Subscription Module
- User profiles
- Roles: Admin, Free, Paid
- Subscription plans:
  - Monthly
  - 3 Months
  - 6 Months
  - 1 Year
- Subscription expiry & access control

**Design Decision**
> Subscription is embedded within User Module in Phase 1  
> Extracted later as a dedicated service if payments & renewals grow

---

### 6.3 Watchlist Module
- User ↔ Company mapping
- Enforces subscription-based limits
- Optimized for fast reads

**Future Optimization**
- Redis caching
- Read replicas

---

### 6.4 Company News Module
- Stores latest company news
- Supports multiple sources
- Paginated APIs

---

### 6.5 Company Events Module
- Earnings, dividends, AGMs
- Time-based queries
- Notification hooks (future)

---

### 6.6 Sentiment Module
- Sentiment per company
- Values: Positive / Neutral / Negative

**Phase 1**
- Rule-based or manual input

**Future**
- AI/ML sentiment analysis

---

### 6.7 Trend Module
- Short / Mid / Long-term trend outlook
- Directional indicators (Bullish / Neutral / Bearish)

---

## 7. Data Layer Architecture

### Primary Database
- **PostgreSQL**

### Logical Data Separation
- Users & Subscriptions
- Watchlists
- Company Master
- News
- Events
- Sentiment
- Trends

**Future Enhancements**
- Redis for caching
- Read replicas for scaling
- Partitioning for large datasets

---

## 8. Security Architecture

- OTP-based authentication
- JWT-based authorization
- Role-based access control
- Subscription-based feature gating
- API-level validation

---

## 9. Scalability & Evolution Path

### Phase 1
- Single deployable backend
- Modular codebase
- Single PostgreSQL instance

### Phase 2
- Redis caching
- Notification service
- Background jobs

### Phase 3
- Microservices extraction
- Dedicated Subscription Service
- AI-driven insights
- Mobile apps

---

## 10. Architecture Principles Followed

- Separation of concerns
- Domain-driven module boundaries
- Read optimization
- Security by design
- Scale only when needed

---

## 11. Summary

This system architecture provides a strong, production-ready foundation for
Equity Watchlist Tracker, enabling rapid MVP delivery while keeping the door
open for enterprise-grade scalability.

The design balances **simplicity today** with **flexibility tomorrow**.

