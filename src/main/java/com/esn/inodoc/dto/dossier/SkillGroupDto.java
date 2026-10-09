package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record SkillGroupDto(
        UUID id,
        String category,
        String content) {
}
