package com.esn.inodoc.dto.dossier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete representation of a competence dossier version.
 */
public record DossierDto(
        UUID id,
        UUID profileId,
        String label,
        boolean master,
        String headline,
        Integer yearsOfExperience,
        String expertiseDomain,
        String summary,
        String documentLanguage,
        List<SkillGroupDto> skillGroups,
        List<ExperienceCompanyDto> experiences,
        List<CertificationDto> certifications,
        List<EducationDto> educations,
        List<LanguageDto> languages,
        List<ReferenceDto> references,
        List<TestResultDto> testResults,
        Instant createdAt,
        Instant updatedAt) {
}
