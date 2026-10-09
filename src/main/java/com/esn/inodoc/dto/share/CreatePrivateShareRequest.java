package com.esn.inodoc.dto.share;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePrivateShareRequest(@NotNull UUID businessManagerId) {
}