package com.esn.inodoc.dto.cra;

import com.esn.inodoc.domain.cra.CraDeclarationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CraDeclarationDto(
        UUID id,
        UUID collaboratorId,
        String collaboratorName,
        String collaboratorEmail,
        int year,
        int month,
        CraDeclarationStatus status,
        Instant declaredAt,
        Instant validatedAt,
        String validatedByName,
        List<CraEntryDto> days,
        String rejectionReason) {
}