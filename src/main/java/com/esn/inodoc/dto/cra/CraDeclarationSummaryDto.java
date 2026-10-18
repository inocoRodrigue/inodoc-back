package com.esn.inodoc.dto.cra;

import com.esn.inodoc.domain.cra.CraDeclarationStatus;

import java.util.UUID;

public record CraDeclarationSummaryDto(
        UUID id,
        UUID collaboratorId,
        String collaboratorName,
        String collaboratorEmail,
        int year,
        int month,
        CraDeclarationStatus status,
        double assignedDays) {
}