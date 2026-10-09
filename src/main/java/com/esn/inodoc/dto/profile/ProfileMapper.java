package com.esn.inodoc.dto.profile;

import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.dto.dossier.DossierMapper;

import java.util.Optional;

/**
 * Conversion between the Profile entity and its DTOs.
 */
public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static ProfileDto toDto(Profile p) {
        return toDto(p, null);
    }

    public static ProfileDto toDto(Profile p, com.esn.inodoc.domain.User account) {
        return new ProfileDto(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getEmail(),
                p.getPhone(),
                p.getAddress(),
                p.getLocation(),
                p.getAvailabilityStatus(),
                p.getAvailableFrom(),
                p.getStatus(),
                p.getDailyRate(),
                p.getDossiers().stream().map(DossierMapper::toSummary).toList(),
                account == null ? null : account.getId(),
                account == null ? null : account.getEmail(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getImageUrl());
    }

    public static ProfileSummaryDto toSummary(Profile p) {
        Optional<CompetenceDossier> master = p.masterDossier();
        return new ProfileSummaryDto(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                master.map(CompetenceDossier::getHeadline).orElse(null),
                p.getLocation(),
                p.getStatus(),
                p.getAvailabilityStatus(),
                master.map(CompetenceDossier::getYearsOfExperience).orElse(null),
                master.map(CompetenceDossier::getExpertiseDomain).orElse(null),
                p.getDossiers().size());
    }

    public static void apply(Profile p, ProfileUpsertRequest r) {
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setEmail(r.email());
        p.setPhone(r.phone());
        p.setAddress(r.address());
        p.setLocation(r.location());
        if (r.availabilityStatus() != null) {
            p.setAvailabilityStatus(r.availabilityStatus());
        }
        p.setAvailableFrom(r.availableFrom());
        p.setDailyRate(r.dailyRate());
    }
}
