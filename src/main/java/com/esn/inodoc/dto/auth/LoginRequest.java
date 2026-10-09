package com.esn.inodoc.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request to the single company managed by the application.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
