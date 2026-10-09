package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record TestResultDto(
        UUID id,
        String name,
        String score,
        String description,
        String link,
        String imageUrl) {
}
