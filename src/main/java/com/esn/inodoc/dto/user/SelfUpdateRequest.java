package com.esn.inodoc.dto.user;

import jakarta.validation.constraints.Size;

/**
 * Update of one's own account information ("My account" page).
 */
public record SelfUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 10000) String signature) {
}
