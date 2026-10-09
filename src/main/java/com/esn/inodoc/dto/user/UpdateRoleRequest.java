package com.esn.inodoc.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleRequest(@NotBlank String role) {
}
