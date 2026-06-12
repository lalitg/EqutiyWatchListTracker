package com.equity.user.enums;

/**
 * Represents the role of a registered user on the platform.
 *
 * CLIENT — a normal investor using the watchlist and market features.
 * ADMIN  — a platform operator who can manage users and view admin dashboards.
 *
 * Stored as a VARCHAR(10) string in the DB column `user_type` (via @Enumerated(STRING)).
 * Used by auth-service to embed the role inside the JWT so that downstream
 * services (e.g. watchlist-service) can gate features without a DB call.
 */
public enum UserType {
    CLIENT,
    ADMIN
}
