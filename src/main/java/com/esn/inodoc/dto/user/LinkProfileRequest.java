package com.esn.inodoc.dto.user;

import java.util.UUID;

/**
 * Association of a user account with an internal profile.
 * A null profileId unlinks the account from its profile.
 */
public record LinkProfileRequest(UUID profileId) {
}
