package com.esn.inodoc.dto.career;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record CareerTemplateDto(
        UUID id,
        String title,
        String description,
        JsonNode entrySchema,
        Instant updatedAt) {
}
