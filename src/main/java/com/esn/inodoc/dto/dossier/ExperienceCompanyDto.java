package com.esn.inodoc.dto.dossier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExperienceCompanyDto(
        UUID id,
        String company,
        LocalDate startDate,
        LocalDate endDate,
        String roleTitle,
        String context,
        List<ExperienceProjectDto> projects) {
}
