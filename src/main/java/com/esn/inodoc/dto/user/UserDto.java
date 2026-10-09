package com.esn.inodoc.dto.user;

import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Set<String> roles,
        UUID profileId,
        String signature,
        String imageUrl) {
}
