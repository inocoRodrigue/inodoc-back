package com.esn.inodoc.dto.user;

import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.User;

import java.util.stream.Collectors;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getName).map(Enum::name).collect(Collectors.toSet()),
                user.getProfile() != null ? user.getProfile().getId() : null,
                user.canUseMailSignature() ? user.getSignature() : null,
                user.getImageUrl());
    }
}
