package com.equity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/users/me/password.
 *
 * The user must supply their current password for verification before the
 * new password is accepted.  This prevents a stolen JWT from being used to
 * silently take over an account.
 *
 * Flow in UserService.changePassword:
 *   1. Load User by id from JWT.
 *   2. BCryptPasswordEncoder.matches(currentPassword, user.passwordHash).
 *   3. If mismatch → throw InvalidPasswordException (HTTP 400).
 *   4. Hash newPassword and save.
 */
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
