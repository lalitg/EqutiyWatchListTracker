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

## 3. High-Level Architecture

The system follows a **modular monolith** approach in Phase 1 and can evolve into
microservices as the platform scales.


---

## 4. Core Services (Phase 1)

### 4.1 Authentication Service
**Responsibilities**
- OTP-based login (Email / Phone)
- JWT & Refresh Token generation
- Token validation for downstream services

**Characteristics**
- Stateless
- Horizontally scalable

---

### 4.2 User Service
**Responsibilities**
- User profile management
- Role management (Admin, Free, Paid)
- Subscription association
- Account lifecycle management

---

### 4.3 Subscription Management (Design Decision)

**Phase 1 Approach**
- Subscription management is implemented **inside User Service**

**Why not a separate service yet?**
- Keeps MVP simple
- Avoids unnecessary network calls
- Subscription is tightly coupled with user lifecycle

**Future Evolution**
- Extract into a dedicated `Subscription Service` when:
  - Payment gateway is introduced
  - Renewals, invoicing, and refunds are required

---

### 4.4 Watchlist Service
**Responsibilities**
- Manage user-to-company watchlist mapping
- Provide fast read access for dashboards
- Enforce subscription-based limits

**Design Notes**
- Read-heavy service
- Redis caching planned for future phases

---

### 4.5 Company News Service
**Responsibilities**
- Store company-specific news
- Normalize multiple news sources
- Provide paginated APIs

---

### 4.6 Company Event Service
**Responsibilities**
- Track earnings, dividends, AGMs, etc.
- Maintain event timelines per company
- Enable notifications in future phases

---

### 4.7 Sentiment Service
**Responsibilities**
- Maintain sentiment indicators:
  - Positive
  - Neutral
  - Negative

**Phase 1**
- Rule-based or manually curated

**Future**
- AI/ML-driven sentiment analysis

---

### 4.8 Trend Service
**Responsibilities**
- Store short-term, mid-term, and long-term trends
- Provide directional outlook (Bullish / Neutral / Bearish)

**Phase 1**
- Offline or manually computed trends

---

## 5. Data Management
**Primary Database**
- PostgreSQL

**Logical Separation**
- Users & Subscriptions
- Watchlists
- Company master data
- News, Events, Sentiment, Trends

---

## 6. Security & Access Control
- JWT-based authentication
- Role-based authorization
- Subscription-based feature gating
- API-level access control

---

## 7. Scalability Strategy

**Phase 1**
- Modular monolith
- Single database

**Future Scaling**
- Redis for watchlist caching
- Independent microservices for company intelligence
- API Gateway with rate limiting

---

## 8. Summary
Phase 1 HLD establishes a strong, scalable foundation for Equity Watchlist Tracker,
balancing simplicity with future extensibility and production-readiness.

