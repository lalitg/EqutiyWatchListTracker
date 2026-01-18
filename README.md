📈 Equity Watchlist Tracker

Equity Watchlist Tracker is a modern investing platform that helps users track equities in a personal watchlist with real-time news, upcoming events, trend outlook, and market sentiment — all in one place.

The goal is to give long-term investors and active learners context, not just prices.

🚀 Key Features
⭐ Watchlist-First Design

Create a personalized equity watchlist

Fast access to tracked companies

Optimized for daily usage

📰 Company Intelligence

Latest company-specific news

Upcoming corporate events (results, dividends, AGMs)

Market trend and outlook (short / mid / long term)

Sentiment indicators (positive / neutral / negative)

👤 User & Subscription Management

OTP-based login (Email / Phone)

Free and Paid user tiers

Subscription plans:

Monthly

3 Months

6 Months

1 Year

🔐 Secure Authentication

JWT-based authentication

Role-based access (Admin / Free User / Paid User)

SSO-ready architecture (future)

⚡ Scalable Architecture

Modular service design

PostgreSQL as source of truth

Redis-ready for high-performance watchlist access

Microservice-friendly, monolith-first approach

🧠 Why Equity Watchlist Tracker?

Most investing apps focus on prices and trades.
Equity Watchlist Tracker focuses on context and insight:

What’s happening with the company? Why is the trend changing? What events are coming next?

🏗️ System Architecture (High Level)
Client (Web / Mobile)
        ↓
API Gateway
        ↓
Auth Service
User Service
Watchlist Service
Company Data Service
        ↓
PostgreSQL / Cache

🧩 Core Services
Auth Service

Email / Phone OTP login

JWT & Refresh token management

User Service

User profiles

Roles & subscriptions

Free vs Paid feature control

Watchlist Service

User ↔ Company mapping

Fast read-optimized access

Redis-cache ready

Company Data Service

Company master data

News

Events

Trend & sentiment

🗄️ Tech Stack
Layer	Technology
Backend	Java, Spring Boot
Database	PostgreSQL
Cache	Redis (planned)
Auth	JWT, OTP
Frontend	React / Angular (planned)
Mobile	Android (planned)
Deployment	Docker, Kubernetes (future)
🔐 Security & Access Control

OTP-based authentication

JWT for API security

Role-based authorization

Subscription-based feature access

📌 Roadmap
Phase 1 (MVP)

User login (OTP)

Watchlist management

Company news, events, trend, sentiment

Phase 2

Alerts & notifications

Advanced sentiment analysis

UI enhancements

Redis caching

Phase 3

Portfolio module

AI-based insights

Recommendations

Mobile app

🛠️ Getting Started (Backend)
git clone https://github.com/<your-username>/equity-watchlist-tracker.git
cd equity-watchlist-tracker


Setup instructions will be added as development progresses.

🤝 Contribution

Contributions, ideas, and feedback are welcome.
This project is built with clean architecture, scalability, and long-term growth in mind.

📄 License

This project is licensed under the MIT License.

👤 Author

Lalit Gera
Software Engineer | Investing & System Design Enthusiast