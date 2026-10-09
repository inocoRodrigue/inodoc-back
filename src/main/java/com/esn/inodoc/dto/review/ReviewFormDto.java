package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ReviewFormDto(
        UUID id,
        String title,
        String description,
        JsonNode schema,
        Instant createdAt,
        Instant updatedAt) {
}
