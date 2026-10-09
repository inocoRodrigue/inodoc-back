package com.esn.inodoc.dto.profile;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.ProfileStatus;

import java.util.UUID;

/**
 * Overview of a person for lists/search.
 * The business fields (headline, experience, domain) come from the master version.
 */
public record ProfileSummaryDto(
        UUID id,
        String firstName,
        String lastName,
        String headline,
        String location,
        ProfileStatus status,
        AvailabilityStatus availabilityStatus,
        Integer yearsOfExperience,
        String expertiseDomain,
        int dossierCount) {
}
