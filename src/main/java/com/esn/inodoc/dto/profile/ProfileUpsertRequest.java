package com.esn.inodoc.dto.profile;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.ProfileStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Payload to create / update a person's identity (without dossier content).
 *
 * Validation rules:
 * - firstName, lastName: required, 1-100 characters
 * - email: optional, but must be valid if provided
 * - dailyRate: optional, minimum 0 if provided
 */
public record ProfileUpsertRequest(
        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Email(message = "Email must be valid")
        String email,

        String phone,

        String address,

        String location,

        AvailabilityStatus availabilityStatus,

        LocalDate availableFrom,

        ProfileStatus status,

        @Min(value = 0, message = "Daily rate cannot be negative")
        Integer dailyRate) {
}
