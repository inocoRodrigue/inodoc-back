package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record ExperienceProjectDto(
        UUID id,
        String name,
        String context,
        String team,
        String methodology,
        String tasks,
        String technologies) {
}
