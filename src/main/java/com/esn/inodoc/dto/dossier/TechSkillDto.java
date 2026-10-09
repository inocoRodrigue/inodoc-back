package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record TechSkillDto(
        UUID id,
        String category,
        String name) {
}
