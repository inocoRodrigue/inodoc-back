package com.esn.inodoc.dto.share;

import com.esn.inodoc.domain.ShareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request to create a shared link for a competence dossier.
 *
 * Validation rules:
 * - dossierId: required
 * - type: required (ENUM)
 * - expiresInDays: optional, 1-365 days if provided
 * - password: optional, 8-128 characters if provided (empty string = no password)
 * - maxOpens: optional, 1-10000 if provided
 * - email: optional, but if provided must be valid
 */
public record CreateShareRequest(
        @NotNull(message = "Dossier ID is required")
        UUID dossierId,

        @NotNull(message = "Share type is required")
        ShareType type,

        @Min(value = 1, message = "Expiration must be at least 1 day")
        @Max(value = 365, message = "Expiration cannot exceed 365 days")
        Integer expiresInDays,

        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @Min(value = 1, message = "Max opens must be at least 1")
        @Max(value = 10000, message = "Max opens cannot exceed 10000")
        Integer maxOpens,

        @Valid
        ShareEmailRequest email) {
}
