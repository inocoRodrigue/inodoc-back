package com.esn.inodoc.dto.dossier;

import java.util.UUID;

public record LanguageDto(
        UUID id,
        String language,
        String level) {
}
