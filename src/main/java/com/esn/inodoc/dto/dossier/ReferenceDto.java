package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record ReferenceDto(
        UUID id,
        String name,
        String email,
        String phone,
        String description,
        String link) {
}
