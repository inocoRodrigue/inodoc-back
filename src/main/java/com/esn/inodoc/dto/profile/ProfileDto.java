package com.esn.inodoc.dto.profile;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.dto.dossier.DossierSummaryDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Representation of a person with the list of their dossier versions.
 */
public record ProfileDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String location,
        AvailabilityStatus availabilityStatus,
        LocalDate availableFrom,
        ProfileStatus status,
        Integer dailyRate,
        List<DossierSummaryDto> dossiers,
        UUID accountUserId,
        String accountEmail,
        Instant createdAt,
        Instant updatedAt,
        String imageUrl) {
}
