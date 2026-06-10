package com.equity.user.repository;

import com.equity.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the User entity.
 *
 * Spring auto-generates all SQL at startup — no boilerplate needed.
 * Each method name is parsed by Spring Data into a WHERE clause automatically.
 *
 * Why these specific methods?
 * - findByUsername     → auth-service calls the internal /validate endpoint with
 *                        a username; also used on login.
 * - findByEmail        → future: "forgot password" flow looks up by email.
 * - existsByUsername   → registration guard: reject duplicate usernames fast.
 * - existsByEmail      → registration guard: reject duplicate emails fast.
 * - existsByPhoneNumber→ registration guard: reject duplicate phone numbers.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Find a user by their unique login handle. */
    Optional<User> findByUsername(String username);

    /** Find a user by their email address (nullable field). */
    Optional<User> findByEmail(String email);

    /** Returns true if a user with this username already exists. */
    boolean existsByUsername(String username);

    /** Returns true if a user with this email already exists. */
    boolean existsByEmail(String email);

    /** Returns true if a user with this phone number already exists. */
    boolean existsByPhoneNumber(String phoneNumber);
}
