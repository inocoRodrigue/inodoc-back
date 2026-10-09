package com.esn.inodoc.dto.user;

import com.esn.inodoc.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull Set<RoleName> roles) {
}
