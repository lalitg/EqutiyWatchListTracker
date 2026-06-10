-- payment-service schema script
-- Run this ONCE manually before starting the service for the first time.
--
-- Command (local):
--   psql -h localhost -U postgres -d watchlisttracker -f create_payment_tables.sql
--
-- Command (AWS RDS):
--   psql -h <RDS_HOST> -U postgres -d watchlisttracker -f create_payment_tables.sql
--
-- Tables owned by payment-service:
--   plans, coupons, coupon_usages, subscriptions, payments
--
-- No FK constraints to users table (owned by user-service).
-- Microservices are independently deployable — cross-service FKs are avoided.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. plans
--    Stores all subscription plans. NOT hardcoded in Java.
--    Marketing team manages these rows directly (or via future admin API).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS plans (
    id                BIGSERIAL       PRIMARY KEY,

    -- Internal code name e.g. MONTHLY_499
    name              VARCHAR(50)     NOT NULL UNIQUE,

    -- Display name shown to users e.g. "Pro Monthly"
    display_name      VARCHAR(100)    NOT NULL,

    -- Marketing description shown on pricing page
    description       TEXT,

    -- Price in paise to avoid floating-point issues (Rs.499 = 49900)
    price_paise       INTEGER         NOT NULL CHECK (price_paise >= 0),

    -- MONTHLY or ANNUAL
    billing_cycle     VARCHAR(10)     NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),

    -- How many days this plan covers (30 for monthly, 365 for annual)
    duration_days     INTEGER         NOT NULL CHECK (duration_days > 0),

    -- Razorpay Plan ID created via Razorpay Plans API.
    -- Filled automatically at service startup via PlanService.seedPlanToRazorpay().
    -- Required to create Razorpay Subscriptions.
    razorpay_plan_id  VARCHAR(100),

    -- Set to false to stop selling this plan (existing subs are unaffected)
    is_active         BOOLEAN         NOT NULL DEFAULT true,

    created_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Seed: the current plan decided by the team
INSERT INTO plans (name, display_name, description, price_paise, billing_cycle, duration_days)
VALUES (
    'MONTHLY_499',
    'Pro Monthly',
    'Full access to all Equity Watchlist Tracker features for 30 days',
    49900,
    'MONTHLY',
    30
) ON CONFLICT (name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. coupons
--    Discount codes created by the marketing team.
--    Supports FLAT (e.g. Rs.100 off) and PERCENTAGE (e.g. 50% off) discounts.
--    Coupons apply to FIRST PAYMENT ONLY — renewals always charge full price.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
    id                       BIGSERIAL    PRIMARY KEY,

    -- The code the user types: LAUNCH50, SAVE100, FRIEND20
    code                     VARCHAR(50)  NOT NULL UNIQUE,

    -- Internal note for marketing team
    description              VARCHAR(255),

    -- FLAT: discount_value is amount in paise (10000 = Rs.100 off)
    -- PERCENTAGE: discount_value is 0-100 (50 = 50% off)
    discount_type            VARCHAR(10)  NOT NULL CHECK (discount_type IN ('FLAT', 'PERCENTAGE')),
    discount_value           INTEGER      NOT NULL CHECK (discount_value > 0),

    -- For PERCENTAGE type: cap the maximum discount (e.g. 50% off but max Rs.200)
    -- NULL means no cap
    max_discount_paise       INTEGER,

    -- Minimum order value required to apply this coupon
    min_order_paise          INTEGER      NOT NULL DEFAULT 0,

    -- NULL = valid on all plans; set to a plan id to restrict to one plan
    applicable_plan_id       BIGINT       REFERENCES plans(id),

    -- NULL = unlimited total uses; set to limit total redemptions across all users
    max_redemptions          INTEGER,

    -- How many times a single user can use this coupon (usually 1)
    max_redemptions_per_user INTEGER      NOT NULL DEFAULT 1,

    -- Validity window
    valid_from               TIMESTAMP    NOT NULL DEFAULT NOW(),
    valid_until              TIMESTAMP,        -- NULL = never expires

    -- Set to false to immediately disable a coupon
    is_active                BOOLEAN      NOT NULL DEFAULT true,

    created_at               TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. coupon_usages
--    Tracks every coupon redemption.
--    Used to enforce max_redemptions and max_redemptions_per_user.
--    A row is inserted ONLY after payment is confirmed (status=PAID).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupon_usages (
    id           BIGSERIAL    PRIMARY KEY,
    coupon_id    BIGINT       NOT NULL REFERENCES coupons(id),
    -- Logical reference to users.id in user-service (no FK across services)
    user_id      BIGINT       NOT NULL,
    payment_id   BIGINT,      -- filled after payment record is created
    used_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_coupon_usages_coupon_id ON coupon_usages (coupon_id);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_user_id   ON coupon_usages (user_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. subscriptions
--    One row per user. Tracks their current active plan and billing period.
--    razorpay_subscription_id is essential — without it we cannot cancel
--    the recurring charge on Razorpay's end.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id                        BIGSERIAL    PRIMARY KEY,

    -- Logical reference to users.id in user-service (no FK)
    user_id                   BIGINT       NOT NULL UNIQUE,

    plan_id                   BIGINT       NOT NULL REFERENCES plans(id),

    -- PENDING   : Razorpay subscription created, first payment not yet done
    -- ACTIVE    : Subscription is live, user has access
    -- HALTED    : Razorpay failed to charge after all retries
    -- CANCELLED : User cancelled; access ended at period end
    -- EXPIRED   : Period ended naturally (for lifetime/non-recurring plans)
    status                    VARCHAR(15)  NOT NULL CHECK (status IN ('PENDING','ACTIVE','HALTED','CANCELLED','EXPIRED')),

    -- Current billing window
    current_period_start      TIMESTAMP,
    current_period_end        TIMESTAMP,

    -- If true: subscription ends at current_period_end, not renewed
    cancel_at_period_end      BOOLEAN      NOT NULL DEFAULT false,

    -- Razorpay subscription ID: "sub_YYYYYY"
    -- Used to: cancel via Razorpay API, match webhook events
    razorpay_subscription_id  VARCHAR(100) UNIQUE,

    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id            ON subscriptions (user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_razorpay_sub_id    ON subscriptions (razorpay_subscription_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. payments
--    Full audit trail of every payment — first payments and auto-renewals.
--    Stores original price, discount, and final charged amount separately.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payments (
    id                        BIGSERIAL    PRIMARY KEY,

    -- Logical reference to users.id in user-service (no FK)
    user_id                   BIGINT       NOT NULL,

    plan_id                   BIGINT       NOT NULL REFERENCES plans(id),

    -- NULL if no coupon was used
    coupon_id                 BIGINT       REFERENCES coupons(id),

    -- FIRST_PAYMENT: user subscribing for the first time (coupon may apply)
    -- RENEWAL      : monthly auto-charge by Razorpay (no coupon, full plan price)
    payment_type              VARCHAR(15)  NOT NULL CHECK (payment_type IN ('FIRST_PAYMENT','RENEWAL')),

    -- Price breakdown for full audit trail
    original_amount_paise     INTEGER      NOT NULL,   -- plan price before any discount
    discount_paise            INTEGER      NOT NULL DEFAULT 0,
    final_amount_paise        INTEGER      NOT NULL,   -- amount actually charged

    currency                  VARCHAR(5)   NOT NULL DEFAULT 'INR',

    -- CREATED  : Razorpay subscription created, awaiting first payment
    -- PAID     : Payment confirmed (via /verify or subscription.charged webhook)
    -- FAILED   : Payment failed
    -- REFUNDED : Payment refunded
    status                    VARCHAR(15)  NOT NULL CHECK (status IN ('CREATED','PAID','FAILED','REFUNDED')),

    -- Filled when Razorpay processes the payment
    razorpay_payment_id       VARCHAR(100) UNIQUE,
    razorpay_signature        VARCHAR(255),
    failure_reason            VARCHAR(255),

    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_user_id          ON payments (user_id);
CREATE INDEX IF NOT EXISTS idx_payments_razorpay_pay_id  ON payments (razorpay_payment_id);


-- How to run the jar
-- java -DDB_PASSWORD=postgres -jar target\payment-service-0.0.1-SNAPSHOT.jar