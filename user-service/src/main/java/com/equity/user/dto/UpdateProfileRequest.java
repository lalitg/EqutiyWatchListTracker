package com.equity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/users/me.
 *
 * Allows a logged-in user to update their display name, email, and/or phone
 * number.  At least `name` is required (it must not be left blank).
 * email and phoneNumber are optional — a null value leaves the existing
 * DB value unchanged (handled in UserService.updateProfile).
 */
public class UpdateProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @Size(max = 15, message = "Phone number must be at most 15 characters")
    private String phoneNumber;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
