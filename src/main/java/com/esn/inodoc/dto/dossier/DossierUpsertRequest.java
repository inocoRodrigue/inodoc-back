package com.esn.inodoc.dto.dossier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.util.List;

/**
 * Payload to create / update a dossier version.
 * The collections, if provided, entirely replace the existing ones.
 *
 * Validation rules:
 * - label: required, 1-200 characters
 * - yearsOfExperience: optional, 0-99 if provided
 * - summary: optional, max 5000 characters
 */
public record DossierUpsertRequest(
        @NotBlank(message = "Dossier label is required")
        @Size(min = 1, max = 200, message = "Label must be between 1 and 200 characters")
        String label,

        Boolean master,

        String headline,

        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 99, message = "Years of experience cannot exceed 99")
        Integer yearsOfExperience,

        String expertiseDomain,

        @Size(max = 5000, message = "Summary cannot exceed 5000 characters")
        String summary,

        List<SkillGroupDto> skillGroups,

        List<ExperienceCompanyDto> experiences,

        List<CertificationDto> certifications,

        List<EducationDto> educations,

        List<LanguageDto> languages,

        List<ReferenceDto> references,

        List<TestResultDto> testResults) {
}
