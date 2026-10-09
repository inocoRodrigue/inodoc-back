package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ReferenceDocumentDto(
        UUID id,
        String title,
        String description,
        String category,
        JsonNode content,
        Instant createdAt,
        Instant updatedAt) {
}
