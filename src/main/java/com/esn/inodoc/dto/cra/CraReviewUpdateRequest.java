package com.esn.inodoc.dto.cra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CraReviewUpdateRequest(
        @NotNull List<@NotNull @Valid CraDayAssignmentRequest> days,
        UUID mailAccountId) {
}