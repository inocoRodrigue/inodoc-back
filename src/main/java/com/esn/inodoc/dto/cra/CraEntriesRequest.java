package com.esn.inodoc.dto.cra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CraEntriesRequest(@NotNull List<@Valid CraDayAssignmentRequest> days) {
}