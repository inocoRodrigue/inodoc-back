package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record CertificationDto(
        UUID id,
        String name,
        String issuer,
        String year,
        String link,
        String imageUrl) {
}
