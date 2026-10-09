package com.esn.inodoc.dto.dossier;

import java.time.Instant;
import java.util.UUID;

/**
 * Overview of a dossier version (for lists).
 */
public record DossierSummaryDto(
        UUID id,
        String label,
        boolean master,
        String headline,
        Integer yearsOfExperience,
        String expertiseDomain,
        Instant updatedAt) {
}
