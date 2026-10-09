package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record EducationDto(
        UUID id,
        String school,
        String degree,
        String year,
        String link,
        String imageUrl) {
}
