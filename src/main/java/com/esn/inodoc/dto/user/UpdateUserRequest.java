package com.esn.inodoc.dto.user;

import com.esn.inodoc.domain.RoleName;

import java.util.Set;

public record UpdateUserRequest(
        String firstName,
        String lastName,
        Boolean enabled,
        Set<RoleName> roles) {
}
